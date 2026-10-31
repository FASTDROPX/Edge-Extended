package dev.edgewave;

import android.os.Build;
import android.util.DisplayMetrics;
import android.view.*;

final class ScreenGeometry {
  static int bottom(
      int viewHeight, int viewTop, int displayHeight, int navInset, boolean physical, int offset) {
    return Math.max(
        1, Math.min(viewHeight, displayHeight - (physical ? 0 : navInset) - viewTop - offset));
  }

  static int localBottom(View view, WindowInsets insets, boolean physical, int offset) {
    int h = view.getHeight();
    if (h <= 0) return 1;
    DisplayMetrics metrics = new DisplayMetrics();
    Display display = view.getDisplay();
    if (display == null) return Math.max(1, h - offset);
    display.getRealMetrics(metrics);
    int inset = 0;
    if (insets != null)
      inset =
          Build.VERSION.SDK_INT >= 30
              ? insets.getInsets(WindowInsets.Type.navigationBars()).bottom
              : insets.getSystemWindowInsetBottom();
    if (inset == 0 && !physical) {
      try {
        if (Build.VERSION.SDK_INT >= 30) {
          WindowManager manager =
              (WindowManager)
                  view.getContext().getSystemService(android.content.Context.WINDOW_SERVICE);
          inset =
              manager
                  .getCurrentWindowMetrics()
                  .getWindowInsets()
                  .getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars())
                  .bottom;
        } else {
          DisplayMetrics usable = new DisplayMetrics();
          display.getMetrics(usable);
          inset = Math.max(0, metrics.heightPixels - usable.heightPixels);
        }
      } catch (RuntimeException ignored) {
      }
    }
    int[] location = new int[2];
    view.getLocationOnScreen(location);
    return bottom(h, location[1], metrics.heightPixels, inset, physical, offset);
  }
}
