package dev.edgewave;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

final class DentView extends View {
  final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  final Path path = new Path();
  final boolean right;
  final float depth, length, center;
  final DentMotion motion = new DentMotion();
  boolean hardwareHeld, removalPosted;
  Runnable finished;

  DentView(Context context, boolean right, float depth, float length, float center) {
    super(context);
    this.right = right;
    this.depth = depth;
    this.length = length;
    this.center = center;
    paint.setColor(Color.BLACK);
    motion.press(SystemClock.uptimeMillis());
  }

  void press() {
    hardwareHeld = true;
    motion.press(SystemClock.uptimeMillis());
    invalidate();
  }

  void release() {
    hardwareHeld = false;
    motion.release(SystemClock.uptimeMillis());
    invalidate();
  }

  protected void onDraw(Canvas canvas) {
    long now = SystemClock.uptimeMillis();
    float amount = motion.value(now);
    if (motion.finished(now)) {
      if (finished != null && !removalPosted) {
        removalPosted = true;
        post(
            () -> {
              removalPosted = false;
              if (motion.finished(SystemClock.uptimeMillis()) && finished != null) finished.run();
            });
      }
      return;
    }
    canvas.save();
    if (right) {
      canvas.translate(getWidth(), 0);
      canvas.scale(-1, 1);
    }
    DentShape.trace(path, depth * amount, length, center);
    canvas.drawPath(path, paint);
    canvas.restore();
    postInvalidateOnAnimation();
  }
}
