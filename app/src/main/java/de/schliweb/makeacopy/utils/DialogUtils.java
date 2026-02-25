package de.schliweb.makeacopy.utils;

import android.content.Context;
import android.content.res.Configuration;
import lombok.experimental.UtilityClass;

/** Small dialog-related helpers to reduce UI code duplication. */
@UtilityClass
public final class DialogUtils {

  /**
   * In dark mode, some AlertDialog button colors can be low contrast depending on theme. This
   * method adjusts button text colors to white to improve readability. Safe to call on dialog's
   * onShow.
   */
  public static void improveAlertDialogButtonContrastForNight(
      androidx.appcompat.app.AlertDialog dialog, Context ctx) {
    if (dialog == null || ctx == null) return;
    try {
      int nightModeFlags =
          ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
      if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
        try {
          int white = androidx.core.content.ContextCompat.getColor(ctx, android.R.color.white);
          if (dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE) != null) {
            dialog
                .getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setTextColor(white);
          }
          if (dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE) != null) {
            dialog
                .getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(white);
          }
          if (dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL) != null) {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setTextColor(white);
          }
        } catch (Exception ignored) {
          // Best-effort; failure is non-critical
        }
      }
    } catch (Throwable ignored) {
      // Best-effort; failure is non-critical
    }
  }
}
