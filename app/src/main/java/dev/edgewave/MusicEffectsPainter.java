package dev.edgewave;

import android.graphics.*;

/** Smoothed copies of the actual wave: no independent oscillators or terminal blobs. */
final class MusicEffectsPainter {
  private static final class Brush {
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Path path = new Path();
    final float[] smooth = new float[48], stringShape = new float[48];
  }

  private static final ThreadLocal<Brush> BRUSH = ThreadLocal.withInitial(Brush::new);

  static float wave(EffectsEngine.Fx f, float x, int width, float origin) {
    if (f.shape == null || f.shape.length < 2) return origin;
    return WaveSmoothing.at(f.shape, x / Math.max(1, width));
  }

  static float exponentialOut(float t) {
    if (t <= 0) return 0;
    if (t >= 1) return 1;
    return 1 - (float) Math.pow(2, -10 * t);
  }

  static float duetHeight(EffectsEngine.Fx f, int side) {
    return 12 + (f.duetReverse ? 1 - side : side) * 5;
  }

  static float duetHead(float t) {
    return Math.min(1, 2 * exponentialOut(t));
  }

  static float duetTail(float t) {
    return Math.max(0, 2 * exponentialOut(t) - 1);
  }

  static float presence(float t) {
    float enter = smooth(t / .14f), exit = 1 - smooth((t - .64f) / .36f);
    return enter * exit;
  }

  private static float smooth(float value) {
    value = Math.max(0, Math.min(1, value));
    return value * value * (3 - 2 * value);
  }

  static void smoothed(float[] shape, float[] output, float origin) {
    if (shape == null || shape.length != 48) {
      java.util.Arrays.fill(output, origin);
      return;
    }
    WaveSmoothing.prepare(shape, output, true);
  }

  static void trace(
      Path path,
      float[] shape,
      int w,
      float from,
      float to,
      float offset,
      float presence,
      int style,
      float travel,
      float time,
      boolean transition) {
    path.reset();
    boolean started = false;
    float px = 0, py = 0;
    for (int i = 0; i <= 40; i++) {
      float x = from + (to - from) * i / 40f,
          y = WaveSmoothing.at(shape, x / Math.max(1, w)) + offset;
      if (transition) y = TransitionShape.y(style, x / Math.max(1, w), y, travel, presence, time);
      if (y <= .1f) {
        if (started) path.lineTo(px, py);
        started = false;
        continue;
      }
      if (!started) {
        path.moveTo(x, y);
        started = true;
      } else path.quadTo(px, py, (px + x) / 2, (py + y) / 2);
      px = x;
      py = y;
    }
    if (started) path.lineTo(px, py);
  }

  static void draw(Canvas c, EffectsEngine.Fx f, int w, int h, float d, float origin, long now) {
    float t = f.t(now);
    if (t >= 1) return;
    Brush brush = BRUSH.get();
    Paint paint = brush.paint;
    Path path = brush.path;
    smoothed(f.shape, brush.smooth, origin);
    paint.setColor(Color.BLACK);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeCap(Paint.Cap.BUTT);
    float p = f.kind == 8 ? (float) Math.pow(1 - t, 1.15f) : 1;
    if (p <= 0) return;
    float smoothingClearance = 0;
    if (f.shape != null)
      for (int i = 0; i < 48; i++)
        smoothingClearance = Math.max(smoothingClearance, f.shape[i] - brush.smooth[i]);
    if (f.kind == 8) {
      for (int string = 0; string < 3; string++) {
        float reveal = StringMotion.reveal(f, string, t);
        if (reveal <= 0) continue;
        float local = StringMotion.local(f, string, t);
        paint.setAlpha((int) (255 * Math.pow(1 - local, 1.15) * (3 - string) / 3));
        paint.setStrokeWidth((.7f + string * .12f) * d);
        float offset =
            (11 + string * 3.5f) * d
                + 15
                + f.clearance
                + smoothingClearance
                + 3 * d * EffectsPainter.ease(t)
                + StringMotion.separation(f, string, t);
        for (int i = 0; i < 48; i++)
          brush.stringShape[i] = brush.smooth[i] + StringMotion.bend(f, i / 47f, string, t) * d;
        float from = StringMotion.rightToLeft(f) ? w : 0,
            to = StringMotion.rightToLeft(f) ? w * (1 - reveal) : w * reveal;
        trace(path, brush.stringShape, w, from, to, offset, 1, 0, 0, t, false);
        c.drawPath(path, paint);
      }
    } else if (f.kind == 9) {
      float head = w * .5f * duetHead(t), tail = w * .5f * duetTail(t);
      float width = (1.1f + f.strength * .9f) * d;
      for (int side = 0; side < 2; side++) {
        paint.setStrokeWidth(width);
        paint.setAlpha(255);
        float from = side == 0 ? tail : w - tail, to = side == 0 ? head : w - head;
        trace(
            path,
            brush.smooth,
            w,
            from,
            to,
            duetHeight(f, side) * d + smoothingClearance,
            1,
            f.exitStyle,
            origin + 64 * d,
            t,
            false);
        c.drawPath(path, paint);
      }
    }
  }
}
