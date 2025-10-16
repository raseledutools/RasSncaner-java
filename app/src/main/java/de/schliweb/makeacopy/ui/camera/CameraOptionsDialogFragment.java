package de.schliweb.makeacopy.ui.camera;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import de.schliweb.makeacopy.R;

/**
 * A dialog fragment that provides options for configuring camera settings such as OCR skipping,
 * cropping, and analysis enabling. This fragment saves user choices persistently and communicates
 * results back to the parent fragment.
 * <p>
 * Constants:
 * - REQUEST_KEY: The key used for returning results via FragmentResult API.
 * - BUNDLE_SKIP_OCR: The bundle key representing whether OCR skipping is enabled.
 * - BUNDLE_ANALYSIS_ENABLED: The bundle key representing the state of analysis enablement.
 * - BUNDLE_SKIP_CROPPING: The bundle key representing whether cropping is skipped.
 * <p>
 * Methods:
 * - show(FragmentManager fm): Static utility method to display the dialog fragment.
 * - onCreateDialog(Bundle savedInstanceState): Inflates and initializes the dialog's UI, attaches
 * functionality for saving choices, and improves button appearance for night mode, if applicable.
 */
public class CameraOptionsDialogFragment extends DialogFragment {

    public static final String REQUEST_KEY = "camera_options";
    public static final String BUNDLE_SKIP_OCR = "skip_ocr";
    public static final String BUNDLE_ANALYSIS_ENABLED = "analysis_enabled";
    public static final String BUNDLE_SKIP_CROPPING = "skip_cropping";

    public static void show(@NonNull FragmentManager fm) {
        new CameraOptionsDialogFragment().show(fm, "CameraOptionsDialogFragment");
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        LayoutInflater inflater = LayoutInflater.from(ctx);
        View view = inflater.inflate(R.layout.dialog_camera_options, null);

        CheckBox cbSkip = view.findViewById(R.id.dialog_checkbox_skip_ocr);
        CheckBox cbSkipCropping = view.findViewById(R.id.dialog_checkbox_skip_cropping);
        CheckBox cbAnalysis = view.findViewById(R.id.dialog_checkbox_analysis_enabled);

        SharedPreferences prefs = ctx.getSharedPreferences("export_options", Context.MODE_PRIVATE);
        boolean skipOcr = prefs.getBoolean("skip_ocr", false);
        boolean skipPerspective = prefs.getBoolean("skip_cropping", false);
        boolean analysisEnabled = prefs.getBoolean("analysis_enabled", false);
        cbSkip.setChecked(skipOcr);
        if (cbSkipCropping != null) cbSkipCropping.setChecked(skipPerspective);
        if (cbAnalysis != null) cbAnalysis.setChecked(analysisEnabled);

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(R.string.btn_options)
                .setView(view)
                .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(R.string.confirm, (d, w) -> {
                    boolean skip = cbSkip.isChecked();
                    boolean skipCropping = cbSkipCropping != null && cbSkipCropping.isChecked();
                    boolean analysis = cbAnalysis != null && cbAnalysis.isChecked();

                    // Persist and keep legacy/new flags in sync
                    prefs.edit()
                            .putBoolean("skip_ocr", skip)
                            .putBoolean("include_ocr", !skip)
                            .putBoolean("skip_cropping", skipCropping)
                            .putBoolean("analysis_enabled", analysis)
                            .apply();

                    Bundle result = new Bundle();
                    result.putBoolean(BUNDLE_SKIP_OCR, skip);
                    result.putBoolean(BUNDLE_SKIP_CROPPING, skipCropping);
                    result.putBoolean(BUNDLE_ANALYSIS_ENABLED, analysis);
                    getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
                })
                .create();

        // Improve button contrast for night mode if utility exists (best-effort)
        dialog.setOnShowListener(dlg -> {
            try {
                de.schliweb.makeacopy.utils.DialogUtils.improveAlertDialogButtonContrastForNight(dialog, ctx);
            } catch (Throwable ignore) {
            }
        });

        return dialog;
    }
}
