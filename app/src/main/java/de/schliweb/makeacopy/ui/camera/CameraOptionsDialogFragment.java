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
 * Simple options dialog for Camera that only exposes a single setting: Skip OCR (export only).
 */
public class CameraOptionsDialogFragment extends DialogFragment {

    public static final String REQUEST_KEY = "camera_options";
    public static final String BUNDLE_SKIP_OCR = "skip_ocr";
    public static final String BUNDLE_ANALYSIS_ENABLED = "analysis_enabled";

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
        CheckBox cbAnalysis = view.findViewById(R.id.dialog_checkbox_analysis_enabled);

        SharedPreferences prefs = ctx.getSharedPreferences("export_options", Context.MODE_PRIVATE);
        boolean skipOcr = prefs.getBoolean("skip_ocr", false);
        boolean analysisEnabled = prefs.getBoolean("analysis_enabled", false);
        cbSkip.setChecked(skipOcr);
        if (cbAnalysis != null) cbAnalysis.setChecked(analysisEnabled);

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(R.string.btn_options)
                .setView(view)
                .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(R.string.confirm, (d, w) -> {
                    boolean skip = cbSkip.isChecked();
                    boolean analysis = cbAnalysis != null && cbAnalysis.isChecked();

                    // Persist and keep legacy/new flags in sync
                    prefs.edit()
                            .putBoolean("skip_ocr", skip)
                            .putBoolean("include_ocr", !skip)
                            .putBoolean("analysis_enabled", analysis)
                            .apply();

                    Bundle result = new Bundle();
                    result.putBoolean(BUNDLE_SKIP_OCR, skip);
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
