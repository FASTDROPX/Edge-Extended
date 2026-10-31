package dev.edgewave;

import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.WindowManager;

final class OverlayWindows {
  static WindowManager.LayoutParams create(int width, int height) {
    // Android DisplayPolicy clamps NOT_TOUCHABLE alert windows to opacity 0.8.
    // Keep the small effect window touchable; NOT_TOUCH_MODAL lets outside touches through.
    WindowManager.LayoutParams params =
        new WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
    params.gravity = Gravity.TOP | Gravity.LEFT;
    params.layoutInDisplayCutoutMode =
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
    params.alpha = 1f;
    if (android.os.Build.VERSION.SDK_INT >= 30) params.setFitInsetsTypes(0);
    return params;
  }

  static WindowManager.LayoutParams scene(boolean trusted) {
    WindowManager.LayoutParams p = create(-1, -1);
    p.type =
        trusted
            ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
    p.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
    p.alpha = trusted ? 1f : .8f;
    return p;
  }
}
