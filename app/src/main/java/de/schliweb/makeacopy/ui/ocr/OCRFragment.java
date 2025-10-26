package de.schliweb.makeacopy.ui.ocr;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import de.schliweb.makeacopy.R;
import de.schliweb.makeacopy.databinding.FragmentOcrBinding;
import de.schliweb.makeacopy.ui.crop.CropViewModel;
import de.schliweb.makeacopy.utils.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

/**
 * OCRFragment handles the Optical Character Recognition (OCR) functionality within the application.
 * This fragment manages UI and orchestration; the actual OCR work is done on a dedicated single-thread executor
 * with a fresh TessBaseAPI instance per job to ensure thread-safety.
 * <p>
 * Flow: Crop -> (optional) User Rotation -> OCR -> Export
 */
public class OCRFragment extends Fragment {
    private static final String TAG = "OCRFragment";
    // Early-exit threshold: if the first rotation attempt (extra=0) reaches this mean confidence,
    // we skip trying further 90° rotations to save time. Adjust if needed.
    private static final int OCR_EARLY_EXIT_MEAN_CONF_THRESHOLD = 85;

    private FragmentOcrBinding binding;
    private OCRViewModel ocrViewModel;
    private CropViewModel cropViewModel;

    // Track last observed image to decide when to reset OCR state
    private Bitmap lastObservedBitmap;

    // Language helper for listing/availability checks (no long-lived TessBaseAPI instance)
    private OCRHelper langHelper;

    // Concurrency: serialize OCR jobs, 1 job ↔ 1 TessBaseAPI instance
    private final ExecutorService ocrExecutor = Executors.newSingleThreadExecutor();
    private volatile Future<?> runningOcr = null;
    private final AtomicBoolean ocrCancelled = new AtomicBoolean(false);

    // SAF launcher for manual traineddata import
    private ActivityResultLauncher<Intent> openTraineddataLauncher;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ocrViewModel = new ViewModelProvider(requireActivity()).get(OCRViewModel.class);
        cropViewModel = new ViewModelProvider(requireActivity()).get(CropViewModel.class);
        binding = FragmentOcrBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // If OCR was opened directly (skipping Crop), ensure we have a bitmap in CropViewModel
        try {
            if (cropViewModel.getImageBitmap().getValue() == null) {
                de.schliweb.makeacopy.ui.camera.CameraViewModel camVm = new ViewModelProvider(requireActivity()).get(de.schliweb.makeacopy.ui.camera.CameraViewModel.class);
                String path = camVm.getImagePath() != null ? camVm.getImagePath().getValue() : null;
                android.net.Uri uri = camVm.getImageUri() != null ? camVm.getImageUri().getValue() : null;
                android.graphics.Bitmap bmp = de.schliweb.makeacopy.utils.ImageLoader.decode(requireContext(), path, uri);
                if (bmp != null) {
                    cropViewModel.setImageBitmap(bmp);
                }
            }
        } catch (Throwable ignore) {
        }

        // On first entry, if we have an image, clear any stale OCR results and remember this image
        try {
            Bitmap cur = cropViewModel.getImageBitmap().getValue();
            if (cur != null) {
                lastObservedBitmap = cur;
                ocrViewModel.resetForNewImage();
            }
        } catch (Throwable ignore) {
        }

        // Language helper (no initTesseract() here!)
        langHelper = new OCRHelper(requireContext().getApplicationContext());

        // Init SAF launcher for manual .traineddata import
        openTraineddataLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        Uri uri = data.getData();
                        boolean ok = uri != null && OcrModelManager.importFromUri(requireContext(), uri);
                        UIUtils.showToast(requireContext(), ok ? getString(R.string.ocr_import_success) : getString(R.string.ocr_import_failed), Toast.LENGTH_SHORT);
                        if (ok) refreshLanguageSpinner();
                    }
                }
        );

        // State observer
        ocrViewModel.getState().observe(getViewLifecycleOwner(), state -> {
            boolean canProceed = state.imageProcessed() && !state.processing();
            binding.buttonProcess.setEnabled(canProceed);
            binding.buttonProcess.setText(R.string.next);

            binding.textOcr.setText(state.processing()
                    ? getString(R.string.processing_image)
                    : (state.imageProcessed()
                    ? getString(R.string.ocr_processing_complete_tap_the_button_to_proceed_to_export)
                    : getString(R.string.no_image_processed_crop_an_image_first)));

            binding.ocrResultText.setText((state.ocrText() == null || state.ocrText().isEmpty())
                    ? getString(R.string.ocr_results_will_appear_here)
                    : state.ocrText());

            // Enable review button only when OCR finished and we have words (and feature enabled)
            if (!FeatureFlags.isOcrReviewEnabled()) {
                // When feature is disabled, hide the review button completely
                binding.buttonOcrReview.setVisibility(View.GONE);
            } else {
                boolean hasWords = state.words() != null && !state.words().isEmpty();
                boolean enableReview = state.imageProcessed() && !state.processing() && hasWords;
                binding.buttonOcrReview.setEnabled(enableReview);
                binding.buttonOcrReview.setAlpha(enableReview ? 1f : 0.4f);
                binding.buttonOcrReview.setVisibility(View.VISIBLE);
            }

            // Proceed to Export
            binding.buttonProcess.setOnClickListener(v ->
                    Navigation.findNavController(requireView()).navigate(R.id.navigation_export));
        });

        // Error events
        ocrViewModel.getErrorEvents().observe(getViewLifecycleOwner(), ev -> {
            if (ev == null) return;
            String msg = ev.getContentIfNotHandled();
            if (msg != null) UIUtils.showToast(requireContext(), "OCR failed: " + msg, Toast.LENGTH_LONG);
        });

        // When image changes in Crop VM, reset OCR state if it's a different image than last time
        cropViewModel.getImageBitmap().observe(getViewLifecycleOwner(), bitmap -> {
            if (bitmap != null) {
                if (bitmap != lastObservedBitmap) {
                    lastObservedBitmap = bitmap;
                    ocrViewModel.resetForNewImage();
                }
            }
        });

        // Insets (status bar)
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            int topInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            ViewGroup.MarginLayoutParams textParams =
                    (ViewGroup.MarginLayoutParams) binding.textOcr.getLayoutParams();
            textParams.topMargin = (int) (8 * getResources().getDisplayMetrics().density) + topInset;
            binding.textOcr.setLayoutParams(textParams);
            return insets;
        });

        // Bottom inset for button container
        UIUtils.adjustMarginForSystemInsets(binding.buttonContainer, 12);
        ViewCompat.setOnApplyWindowInsetsListener(binding.buttonContainer, (v, insets) -> {
            UIUtils.adjustMarginForSystemInsets(binding.buttonContainer, 12);
            return insets;
        });

        // Back navigates to Crop reliably: try to pop back stack, otherwise navigate explicitly
        binding.buttonBack.setOnClickListener(v -> {
            try {
                // Prevent immediate auto-forward from Crop by resetting cropped state and restoring original
                try {
                    cropViewModel.setImageCropped(false);
                    cropViewModel.setUserRotationDegrees(0);

                    Bitmap orig = cropViewModel.getOriginalImageBitmap().getValue();
                    if (orig != null) cropViewModel.setImageBitmap(orig);
                } catch (Throwable ignoreSet) {
                }
                androidx.navigation.NavController nav = Navigation.findNavController(requireView());
                boolean popped = nav.popBackStack();
                if (!popped) {
                    nav.navigate(R.id.navigation_crop);
                }
            } catch (Throwable ignore) {
                try {
                    Navigation.findNavController(requireView()).navigate(R.id.navigation_crop);
                } catch (Throwable ignored2) {
                }
            }
        });

        // Also handle system back (gesture/hardware) the same way
        OnBackPressedCallback backCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                try {
                    // Prevent immediate auto-forward from Crop by resetting cropped state and restoring original
                    try {
                        cropViewModel.setImageCropped(false);
                        cropViewModel.setUserRotationDegrees(0);

                        Bitmap orig = cropViewModel.getOriginalImageBitmap().getValue();
                        if (orig != null) cropViewModel.setImageBitmap(orig);
                    } catch (Throwable ignoreSet) {
                    }
                    androidx.navigation.NavController nav = Navigation.findNavController(requireView());
                    boolean popped = nav.popBackStack();
                    if (!popped) {
                        nav.navigate(R.id.navigation_crop);
                    }
                } catch (Throwable ignore) {
                    try {
                        Navigation.findNavController(requireView()).navigate(R.id.navigation_crop);
                    } catch (Throwable ignored2) {
                    }
                }
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backCallback);

        // OCR options (settings) icon above the button bar
        binding.buttonOcrOptions.setOnClickListener(v -> showOcrOptionsDialog());
        // OCR Review icon (optional, feature-flagged)
        if (!FeatureFlags.isOcrReviewEnabled()) {
            binding.buttonOcrReview.setVisibility(View.GONE);
        } else {
            binding.buttonOcrReview.setVisibility(View.VISIBLE);
            binding.buttonOcrReview.setOnClickListener(v -> {
                // Prefer loading last edits from autosave if present; otherwise fall back to current OCR state
                de.schliweb.makeacopy.ui.ocr.review.OcrReviewViewModel rv = new ViewModelProvider(requireActivity()).get(de.schliweb.makeacopy.ui.ocr.review.OcrReviewViewModel.class);
                java.io.File autosave = null;
                try {
                    String id = SessionIds.getOrCreateCurrentScanId(requireContext().getApplicationContext());
                    rv.setTargetScanId(id);
                    java.io.File dir = new java.io.File(requireContext().getFilesDir(), "scans/" + id);
                    autosave = new java.io.File(dir, "page.ocr.json");
                } catch (Throwable ignore) {
                    try {
                        autosave = new java.io.File(requireContext().getFilesDir(), "review_autosave.json");
                    } catch (Throwable ignore2) {
                    }
                }
                boolean loaded = false;
                if (autosave != null && autosave.exists()) {
                    try {
                        rv.load(autosave);
                        loaded = true;
                    } catch (Throwable ignore) {
                    }
                }
                if (!loaded) {
                    // Build OcrDoc from current OCR state and pass to Review VM as fallback
                    OCRViewModel.OcrUiState s = ocrViewModel.getState().getValue();
                    de.schliweb.makeacopy.ui.ocr.review.model.OcrDoc doc = de.schliweb.makeacopy.ui.ocr.review.model.OcrDocMapper.fromState(s);
                    rv.setDoc(doc);
                }
                Navigation.findNavController(requireView()).navigate(R.id.navigation_review);
            });
        }

        // Language selection
        setupLanguageSpinner();

        return root;
    }

    /**
     * Language spinner now only updates ViewModel language and UI.
     * We do NOT touch any long-lived TessBaseAPI here.
     */
    private static final String PREFS_NAME = "export_options";
    private static final String PREF_KEY_OCR_LANG = "ocr_language";

    private void setupLanguageSpinner() {
        AutoCompleteTextView dropdown = binding.languageSpinner;
        String[] codes = getAvailableLanguages();
        String[] displayNames = mapCodesToDisplayNames(codes);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, displayNames);
        dropdown.setAdapter(adapter);

        // Determine preferred language: saved preference (if available and installed) else system default
        String systemLang = OCRUtils.mapSystemLanguageToTesseract(java.util.Locale.getDefault().getLanguage());
        android.content.SharedPreferences sp = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);
        String savedCodeTmp = null;
        try {
            savedCodeTmp = sp.getString(PREF_KEY_OCR_LANG, null);
        } catch (Throwable ignore) {
        }
        final String savedCode = savedCodeTmp;

        // Pick target code
        String targetCodeTmp;
        if (savedCode != null && IntStream.range(0, codes.length).anyMatch(i -> codes[i].equals(savedCode)) && isLanguageAvailableSafe(savedCode)) {
            targetCodeTmp = savedCode;
        } else {
            targetCodeTmp = systemLang;
            final String sysLangFinal = systemLang;
            boolean systemInList = IntStream.range(0, codes.length).anyMatch(i -> codes[i].equals(sysLangFinal));
            if (!systemInList) {
                targetCodeTmp = codes.length > 0 ? codes[0] : systemLang;
            }
        }
        final String targetCode = targetCodeTmp;
        // Find index for target
        final int targetPos = IntStream.range(0, codes.length)
                .filter(i -> codes[i].equals(targetCode)).findFirst().orElse(0);

        // Apply selection without triggering listeners and update VM
        dropdown.setText(displayNames[targetPos], false);
        ocrViewModel.setLanguage(targetCode);

        // Initial auto-run logic (only if not already processed)
        Bitmap bitmap = cropViewModel.getImageBitmap().getValue();
        de.schliweb.makeacopy.ui.ocr.OCRViewModel.OcrUiState st0 = ocrViewModel.getState().getValue();
        boolean alreadyProcessed0 = (st0 != null && st0.imageProcessed());
        if (bitmap != null && !alreadyProcessed0) {
            // Do not auto-run if there is no image; otherwise run once
            // Note: performOCR() checks executor state and image again
            // Keeping behavior consistent with previous firstSelection logic
            performOCR();
        }

        final int defaultIndex = targetPos;
        dropdown.setOnItemClickListener((parent, view, pos, id) -> {
            String selectedCode = codes[pos];

            if (!isLanguageAvailableSafe(selectedCode)) {
                UIUtils.showToast(requireContext(), "Language " + displayNames[pos] + " not available.", Toast.LENGTH_LONG);
                // Revert to default without firing listener
                dropdown.setText(displayNames[defaultIndex], false);
                return;
            }

            String prevLang = null;
            try {
                prevLang = ocrViewModel.getLanguage().getValue();
            } catch (Throwable ignore) {
            }
            ocrViewModel.setLanguage(selectedCode);
            // Persist selected language like other settings
            try {
                android.content.SharedPreferences sp2 = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);
                sp2.edit().putString(PREF_KEY_OCR_LANG, selectedCode).apply();
            } catch (Throwable ignore) {
            }

            de.schliweb.makeacopy.ui.ocr.OCRViewModel.OcrUiState st = ocrViewModel.getState().getValue();
            boolean processed = (st != null && st.imageProcessed());
            boolean changed = !Objects.equals(prevLang, selectedCode);
            if (processed && changed) {
                // Allow re-run with new language
                binding.buttonProcess.setText(R.string.btn_process);
                binding.buttonProcess.setOnClickListener(v -> performOCR());
            } else if (processed) {
                // Keep "Next" leading to Export
                binding.buttonProcess.setText(R.string.next);
                binding.buttonProcess.setOnClickListener(v ->
                        Navigation.findNavController(requireView()).navigate(R.id.navigation_export));
            }
        });
    }

    private boolean isLanguageAvailableSafe(String lang) {
        try {
            return langHelper != null && langHelper.isLanguageAvailable(lang);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Get available languages without keeping a long-lived TessBaseAPI.
     */
    private String[] getAvailableLanguages() {
        try {
            if (langHelper != null) {
                String[] langs = langHelper.getAvailableLanguages();
                if (langs != null && langs.length > 0) return langs;
            }
        } catch (Throwable ignore) {
        }
        // Fallback includes Chinese (Simplified and Traditional) so users on zh locales can select them when asset listing fails
        return OCRUtils.getLanguages();
    }

    private String[] mapCodesToDisplayNames(String[] codes) {
        String[] out = new String[codes.length];
        for (int i = 0; i < codes.length; i++) {
            out[i] = codeToDisplayName(codes[i]);
        }
        return out;
    }

    private String codeToDisplayName(String code) {
        // Map common Tesseract 3-letter codes to 2-letter BCP-47 where possible, for localization
        String two;
        switch (code) {
            case "eng":
                two = "en";
                break;
            case "deu":
                two = "de";
                break;
            case "fra":
                two = "fr";
                break;
            case "ita":
                two = "it";
                break;
            case "spa":
                two = "es";
                break;
            case "por":
                two = "pt";
                break;
            case "nld":
                two = "nl";
                break;
            case "pol":
                two = "pl";
                break;
            case "ces":
                two = "cs";
                break;
            case "slk":
                two = "sk";
                break;
            case "hun":
                two = "hu";
                break;
            case "ron":
                two = "ro";
                break;
            case "dan":
                two = "da";
                break;
            case "nor":
                two = "no";
                break;
            case "swe":
                two = "sv";
                break;
            case "rus":
                two = "ru";
                break;
            case "tha":
                two = "th";
                break;
            case "chi_sim":
                return "Chinese (Simplified)";
            case "chi_tra":
                return "Chinese (Traditional)";
            default:
                // Fallback: try first two letters
                if (code != null && code.length() >= 2) {
                    two = code.substring(0, 2);
                } else {
                    two = "en";
                }
        }
        try {
            java.util.Locale loc = java.util.Locale.forLanguageTag(two);
            return loc.getDisplayLanguage(java.util.Locale.getDefault());
        } catch (Throwable ignore) {
            return code;
        }
    }

    /**
     * Refresh the language spinner after importing new models.
     */
    private void refreshLanguageSpinner() {
        try {
            // Recreate helper to see any new files (not strictly necessary)
            langHelper = new OCRHelper(requireContext().getApplicationContext());
            setupLanguageSpinner();
        } catch (Throwable t) {
            Log.w(TAG, "Failed to refresh language spinner", t);
        }
    }

    /**
     * Open a small dialog with OCR model actions.
     */
    private void showOcrOptionsDialog() {
        CharSequence[] items = new CharSequence[]{
                getString(R.string.ocr_import_manual),
                getString(R.string.ocr_discover_packs)
        };
        AlertDialog dlg = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.ocr_models_manage)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        // Manual import via SAF
                        openTraineddataLauncher.launch(OcrModelManager.createOpenTraineddataIntent());
                    } else if (which == 1) {
                        showDiscoverPacksDialog();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        dlg.setOnShowListener(d -> DialogUtils.improveAlertDialogButtonContrastForNight(dlg, requireContext()));
        dlg.show();
    }

    private void showDiscoverPacksDialog() {
        List<String> pkgs = OcrModelManager.discoverAddonPackages(requireContext());
        if (pkgs == null || pkgs.isEmpty()) {
            UIUtils.showToast(requireContext(), getString(R.string.ocr_no_packs_found), Toast.LENGTH_SHORT);
            return;
        }
        CharSequence[] items = pkgs.toArray(new CharSequence[0]);
        AlertDialog dlg = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.ocr_choose_pack)
                .setItems(items, (d, idx) -> showModelsInPackDialog(pkgs.get(idx)))
                .setNegativeButton(R.string.cancel, null)
                .create();

        dlg.setOnShowListener(e -> DialogUtils.improveAlertDialogButtonContrastForNight(dlg, requireContext()));
        dlg.show();
    }

    private void showModelsInPackDialog(String pkg) {
        List<String> files = OcrModelManager.listTrainedDataInPackage(requireContext(), pkg);
        if (files == null || files.isEmpty()) {
            UIUtils.showToast(requireContext(), getString(R.string.ocr_no_models_in_pack), Toast.LENGTH_SHORT);
            return;
        }
        CharSequence[] items = files.toArray(new CharSequence[0]);
        AlertDialog dlg = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.ocr_choose_model)
                .setItems(items, (d, idx) -> {
                    String filename = files.get(idx);
                    boolean ok = OcrModelManager.importFromPackage(requireContext(), pkg, filename);
                    UIUtils.showToast(requireContext(), ok ? getString(R.string.ocr_import_success) : getString(R.string.ocr_import_failed), Toast.LENGTH_SHORT);
                    if (ok) refreshLanguageSpinner();
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        dlg.setOnShowListener(e -> DialogUtils.improveAlertDialogButtonContrastForNight(dlg, requireContext()));
        dlg.show();
    }

    /**
     * Executes OCR in a single-thread executor with a fresh TessBaseAPI per job.
     * Rotation handling: capture-rotation compensation, then user rotation (after crop, before OCR).
     * No write-back to CropViewModel from OCR thread.
     */
    private void performOCR() {
        if (ocrExecutor.isShutdown()) {
            UIUtils.showToast(requireContext(), "Screen is closing, cannot start OCR", Toast.LENGTH_SHORT);
            Log.w(TAG, "performOCR: Executor is already shut down; aborting");
            return;
        }

        Bitmap imageBitmap = cropViewModel.getImageBitmap().getValue();
        Boolean croppedFlagAtStart = cropViewModel.isImageCropped().getValue();
        Integer userRotAtStart = cropViewModel.getUserRotationDegrees().getValue();
        Integer capRotAtStart = cropViewModel.getCaptureRotationDegrees().getValue();
        Log.d(TAG, "performOCR: start on thread=" + Thread.currentThread().getName()
                + ", imageBitmap=" + (imageBitmap == null ? "null" : (imageBitmap.getWidth() + "x" + imageBitmap.getHeight()))
                + ", isImageCropped=" + croppedFlagAtStart
                + ", userDeg=" + userRotAtStart);
        if (imageBitmap == null) {
            UIUtils.showToast(requireContext(), "No image to process", Toast.LENGTH_SHORT);
            Log.w(TAG, "performOCR: No image present in CropViewModel");
            return;
        }

        // Prevent parallel runs
        if (runningOcr != null && !runningOcr.isDone()) {
            UIUtils.showToast(requireContext(), "OCR already running", Toast.LENGTH_SHORT);
            Log.w(TAG, "performOCR: A previous OCR task is still running; ignoring new request");
            return;
        }

        ocrViewModel.startProcessing();
        ocrCancelled.set(false);

        try {
            runningOcr = ocrExecutor.submit(() -> {
                final String LP = "[OCR_LOG] ";
                long t0 = System.nanoTime();
                OCRHelper localHelper = null;
                try {
                    Log.d(TAG, LP + "BG thread=" + Thread.currentThread().getName());
                    // Prepare bitmap (orientation corrections)
                    Log.d(TAG, LP + "Preparing image for OCR - orientation handling");
                    Bitmap src = imageBitmap;

                    // Note: We ignore capture/EXIF rotation here because the app is locked to portrait
                    // (AndroidManifest: android:screenOrientation="portrait") and handles
                    // configChanges for orientation/screenSize/screenLayout. In this setup,
                    // getCaptureRotationDegrees() is effectively always 0 and evaluating it adds no value.
                    // If orientation handling changes in the future, restore compensation here if needed.
                    Bitmap srcNoCaptureRotation = src; // alias for clarity
                    src = srcNoCaptureRotation;

                    // Apply user-requested rotation (after crop, before OCR)
                    int userDeg = 0;
                    Integer ur = cropViewModel.getUserRotationDegrees().getValue();
                    if (ur != null) userDeg = ((ur % 360) + 360) % 360;
                    if (userDeg != 0) {
                        src = rotateBitmap(src, userDeg);
                    }

                    // We will try OCR in 90° steps (0, 90, 180, 270) to guard against wrong user rotation
                    // Start with current src (already including userDeg). For each attempt, rotate extra and pick best by meanConfidence, then by text length.

                    if (ocrCancelled.get()) {
                        Log.w(TAG, LP + "Cancelled before OCR init");
                        postError("Cancelled");
                        return;
                    }

                    // Fresh Tesseract per job
                    localHelper = new OCRHelper(requireContext().getApplicationContext());

                    String lang = ocrViewModel.getLanguage().getValue();
                    if (lang == null || lang.isEmpty()) lang = "eng";
                    Log.d(TAG, LP + "Language requested=" + lang);

                    try {
                        // Ensure language is set BEFORE init so Tesseract loads the correct traineddata
                        localHelper.setLanguage(lang);
                    } catch (Throwable t) {
                        Log.e(TAG, LP + "Failed to set language " + lang, t);
                    }

                    long tInit0 = System.nanoTime();
                    boolean initOk = false;
                    try {
                        initOk = localHelper.initTesseract();
                    } catch (Throwable t) {
                        Log.e(TAG, LP + "initTesseract threw", t);
                    }
                    Log.d(TAG, LP + "Tesseract init ok=" + initOk + ", took=" + ((System.nanoTime() - tInit0) / 1_000_000L) + "ms");
                    if (!initOk) {
                        postError("Engine not initialized");
                        return;
                    }

                    // Try OCR for rotations 0, 90, 180, 270 and keep best result
                    int[] extraRots = new int[]{0, 90, 180, 270};
                    OCRHelper.OcrResultWords bestResult = null;
                    OCRViewModel.OcrTransform bestTx = null;
                    int bestRot = 0;

                    for (int extra : extraRots) {
                        if (ocrCancelled.get()) {
                            Log.w(TAG, LP + "Cancelled before OCR run (extraRot=" + extra + ")");
                            postError("Cancelled");
                            return;
                        }

                        Bitmap rotated = (extra == 0) ? src : rotateBitmap(src, extra);

                        Log.d(TAG, "performOCR: Pre-scaling image to A4 dimensions before OCR (extraRot=" + extra + ")");
                        Bitmap scaledBitmap = ImageScaler.scaleToA4(rotated);
                        // Optional robustness: feed immutable ARGB_8888 copy to Tesseract
                        Bitmap inputForOcr = scaledBitmap.copy(Bitmap.Config.ARGB_8888, /*mutable*/ false);

                        // Build transform for this attempt
                        OCRViewModel.OcrTransform tx = new OCRViewModel.OcrTransform(
                                rotated.getWidth(), rotated.getHeight(),
                                scaledBitmap.getWidth(), scaledBitmap.getHeight(),
                                scaledBitmap.getWidth() / (float) rotated.getWidth(),
                                scaledBitmap.getHeight() / (float) rotated.getHeight(),
                                0, 0
                        );
                        Log.d(TAG, LP + "Transform: src=" + tx.srcW() + "x" + tx.srcH() + ", dst=" + tx.dstW() + "x" + tx.dstH() + ", sx=" + tx.scaleX() + ", sy=" + tx.scaleY());

                        // Run OCR
                        OCRHelper.OcrResultWords r = localHelper.runOcrWithWords(inputForOcr);

                        if (ocrCancelled.get()) {
                            Log.w(TAG, LP + "Cancelled after OCR run (extraRot=" + extra + ")");
                            postError("Cancelled");
                            return;
                        }

                        // Early-exit: if the first attempt (extra=0) is already strong enough, skip other rotations
                        if (extra == 0) {
                            int mc0 = (r.meanConfidence != null ? r.meanConfidence : 0);
                            if (mc0 >= OCR_EARLY_EXIT_MEAN_CONF_THRESHOLD) {
                                bestResult = r;
                                bestTx = tx;
                                bestRot = 0;
                                Log.d(TAG, LP + "Early-exit: meanConf=" + mc0 + " >= " + OCR_EARLY_EXIT_MEAN_CONF_THRESHOLD + ", skipping further rotations");
                                break;
                            }
                        }

                        // Choose best by mean confidence, then by text length as tiebreaker
                        boolean take;
                        if (bestResult == null) {
                            take = true;
                        } else {
                            float mc = (r.meanConfidence != null ? r.meanConfidence : 0f);
                            float bestMc = (bestResult.meanConfidence != null ? bestResult.meanConfidence : 0f);
                            if (mc > bestMc + 0.01f) { // small epsilon
                                take = true;
                            } else if (Math.abs(mc - bestMc) <= 0.01f) {
                                int len = (r.text != null ? r.text.length() : 0);
                                int bestLen = (bestResult.text != null ? bestResult.text.length() : 0);
                                take = len > bestLen;
                            } else {
                                take = false;
                            }
                        }

                        if (take) {
                            bestResult = r;
                            bestTx = tx;
                            bestRot = extra;
                        }
                    }

                    if (bestResult == null || bestTx == null) {
                        postError("OCR failed (no result)");
                        return;
                    }

                    // Push transform of best attempt to VM on UI thread
                    OCRViewModel.OcrTransform finalTx = bestTx;
                    runOnUiThreadSafe(() -> ocrViewModel.setTransform(finalTx));

                    long durMs = (System.nanoTime() - t0) / 1_000_000L;
                    String finalText = (bestResult.text == null || bestResult.text.trim().isEmpty())
                            ? getString(R.string.ocr_results_will_appear_here)
                            : bestResult.text;
                    List<RecognizedWord> words = (bestResult.words != null) ? bestResult.words : new ArrayList<>();

                    int appliedExtraRot = bestRot; // for logging only
                    Integer bestMeanConf = bestResult.meanConfidence;
                    Log.d(TAG, LP + "Best rotation extra=" + appliedExtraRot + "°, meanConf=" + bestMeanConf + ", textLen=" + (bestResult.text == null ? 0 : bestResult.text.length()));

                    final Integer meanConfFinal = bestMeanConf;
                    runOnUiThreadSafe(() -> {
                        ocrViewModel.setWords(words);
                        ocrViewModel.finishSuccess(finalText, words, durMs, meanConfFinal, finalTx);
                    });

                } catch (Throwable e) {
                    Log.e(TAG, "performOCR: Unexpected error", e);
                    postError(e.getMessage() != null ? e.getMessage() : e.toString());
                } finally {
                    // Release Tesseract in the same thread that used it
                    try {
                        if (localHelper != null) localHelper.shutdown();
                        Log.d(TAG, LP + "Tesseract shutdown complete");
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ex) {
            UIUtils.showToast(requireContext(), "OCR service is shutting down", Toast.LENGTH_SHORT);
            Log.w(TAG, "performOCR: RejectedExecutionException (executor shutting down)", ex);
            ocrViewModel.finishError("Executor shutdown");
        }
    }

    private void postError(String msg) {
        runOnUiThreadSafe(() -> {
            ocrViewModel.finishError(msg);
        });
    }

    private void runOnUiThreadSafe(Runnable r) {
        if (!isAdded()) return;
        try {
            requireActivity().runOnUiThread(() -> {
                if (!isAdded() || binding == null) return;
                r.run();
            });
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Signal cancel; do NOT forcibly interrupt the running job (avoid tearing down Tesseract mid-call)
        ocrCancelled.set(true);

        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Fragment is going away for good: now it's safe to shut down the executor
        ocrExecutor.shutdown();
    }

    /**
     * Rotates the given Bitmap by the specified degree in a clockwise direction.
     * If the degrees are a multiple of 360, the original Bitmap is returned unchanged.
     *
     * @param src       The Bitmap to be rotated. Must not be null.
     * @param degreesCW The number of degrees to rotate the Bitmap clockwise.
     *                  Values outside the range [0, 360) will be normalized.
     * @return A new rotated Bitmap object, or the original Bitmap if no rotation is applied.
     */
    private static Bitmap rotateBitmap(Bitmap src, int degreesCW) {
        int deg = ((degreesCW % 360) + 360) % 360;
        if (deg == 0) return src;
        Matrix m = new Matrix();
        m.postRotate(deg);
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
    }
}
