package dev.edgewave;

import android.graphics.*;
import java.util.*;

final class EffectsPainter {
  private static final class Brush {
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Path path = new Path();
    final RectF rect = new RectF();
    final float[] point = new float[4];
  }

  private static final ThreadLocal<Brush> BRUSH = ThreadLocal.withInitial(Brush::new);

  static final class Part {
    final EffectsEngine.Fx fx;
    final int index;
    final RectF bounds;

    Part(EffectsEngine.Fx f, int i, RectF b) {
      fx = f;
      index = i;
      bounds = b;
    }

    long key() {
      return fx.id * 8 + index;
    }
  }

  static float ease(float t) {
    return 1 - (float) Math.pow(1 - t, 3);
  }

  static float bottomPulse(float t) {
    if (t <= 0 || t >= 1) return 0;
    float rise = Math.min(1, t / .025f), fall = Math.max(0, (t - .025f) / .975f);
    return .65f * (rise * rise * (3 - 2 * rise)) * (1 - fall * fall * (3 - 2 * fall));
  }

  static float pulse(float t) {
    return (1 - (float) Math.exp(-t * 35)) * (float) Math.exp(-t * 5);
  }

  static float[] particle(EffectsEngine.Fx f, int w, int h, float d, float origin, long now) {
    return particleInto(f, w, h, d, origin, now, new float[4]);
  }

  static float[] particleInto(
      EffectsEngine.Fx f, int w, int h, float d, float origin, long now, float[] output) {
    float t = f.t(now), e = ease(t);
    float x, y;
    if (f.kind == 3) {
      x = (f.side == 0 ? 0 : w) + (f.side == 0 ? 1 : -1) * (18 + f.seed2 * 28) * d * e;
      y = h * (.14f + f.seed * .7f) + (f.seed2 - .5f) * 24 * d * e;
    } else {
      x = w * (.06f + .88f * f.seed) + (f.seed2 - .5f) * 22 * d * e;
      float start = f.kind == 6 ? h : origin;
      y = start + (f.kind == 6 ? -1 : 1) * (14 + 24 * f.seed2) * d * e;
    }
    float spin = f.seed * 6.28f + 8 * e,
        radius = 2.5f * d * (1 - (float) Math.exp(-t * 8)) * (1 - t);
    x += Math.cos(spin) * radius;
    y += Math.sin(spin) * radius;
    output[0] = x;
    output[1] = y;
    output[2] = (2 + f.seed2 * 2) * d;
    output[3] = 180 * e + f.seed * 90;
    return output;
  }

  static Path trace(int w, int h, float d, float origin, boolean right, float t, float strength) {
    Path base = new Path();
    float x = 2 * d, r = 22 * d;
    base.moveTo(x, Math.min(origin, h * .15f));
    base.lineTo(x, h - r);
    base.quadTo(x, h - x, r, h - x);
    base.lineTo(w / 2f, h - x);
    if (right) {
      Matrix mirror = new Matrix();
      mirror.setScale(-1, 1);
      mirror.postTranslate(w, 0);
      base.transform(mirror);
    }
    PathMeasure measure = new PathMeasure(base, false);
    float length = measure.getLength(),
        head = length * ease(t),
        tail = Math.max(0, head - length * (.12f + strength * .11f));
    Path result = new Path();
    measure.getSegment(tail, head, result, true);
    return result;
  }

  static List<Part> parts(EffectsEngine.Fx f, int w, int h, float d, float origin, long now) {
    ArrayList<Part> out = new ArrayList<>();
    float t = f.t(now);
    if (t >= 1) return out;
    float pad = 3 * d;
    if (f.kind == 2 || f.kind == 3 || f.kind == 6) {
      float[] a = particle(f, w, h, d, origin, now);
      float r = a[2] * 1.5f + 1;
      add(out, f, 0, new RectF(a[0] - r, a[1] - r, a[0] + r, a[1] + r), w, h);
    } else if (f.kind == 0) {
      float lo = origin, hi = origin;
      for (float v : f.shape) {
        lo = Math.min(lo, v);
        hi = Math.max(hi, v);
      }
      float dy = 18 * d * ease(t);
      add(out, f, 0, new RectF(0, lo + dy - pad, w, hi + dy + pad), w, h);
    } else if (f.kind == 1) {
      add(out, f, 0, new RectF(0, 0, 8 * d, h), w, h);
      add(out, f, 1, new RectF(w - 8 * d, 0, w, h), w, h);
    } else if (f.kind == 4) {
      float depth = (4 + f.strength * 9) * 1.5f * d * pulse(t) + 1;
      for (int side = 0; side < 2; side++)
        if (f.side == 2 || f.side == side)
          add(
              out,
              f,
              side,
              new RectF(side == 0 ? 0 : w - depth, 0, side == 0 ? depth : w, h),
              w,
              h);
    } else if (f.kind == 5) {
      float depth = (24 + f.strength * 28) * d * 1.8f + 1;
      add(out, f, 0, new RectF(0, h - depth, w, h), w, h);
    } else if (f.kind == 7) {
      float edge = Math.min(28 * d, w * .25f), corner = Math.min(58 * d, h * .25f);
      add(out, f, 0, new RectF(0, 0, w, corner), w, h);
      add(out, f, 1, new RectF(0, h - corner, w, h), w, h);
      add(out, f, 2, new RectF(0, corner, edge, h - corner), w, h);
      add(out, f, 3, new RectF(w - edge, corner, w, h - corner), w, h);
    }

    if (f.kind >= 8) {
      float max = origin;
      if (f.shape != null) for (float value : f.shape) max = Math.max(max, value);
      add(
          out,
          f,
          0,
          new RectF(0, 0, w, Math.min(h, max + (f.kind == 8 ? 80 : 48) * d + f.clearance)),
          w,
          h);
    }
    return out;
  }

  private static void add(List<Part> out, EffectsEngine.Fx f, int index, RectF b, int w, int h) {
    if (b.intersect(0, 0, w, h) && b.width() > .4f && b.height() > .4f)
      out.add(new Part(f, index, b));
  }

  static void draw(Canvas c, EffectsEngine.Fx f, int w, int h, float d, float origin, long now) {
    if (f.kind >= 8) {
      MusicEffectsPainter.draw(c, f, w, h, d, origin, now);
      return;
    }
    float t = f.t(now);
    if (t >= 1) return;
    Brush brush = BRUSH.get();
    Paint paint = brush.paint;
    paint.setStyle(Paint.Style.FILL);
    paint.setStrokeCap(Paint.Cap.BUTT);
    paint.setColor(Color.BLACK);
    paint.setAlpha((int) (255 * Math.pow(1 - t, 1.2)));
    Path path = brush.path;
    path.reset();
    if (f.kind == 2 || f.kind == 3 || f.kind == 6) {
      float[] a = particleInto(f, w, h, d, origin, now, brush.point);
      c.save();
      c.translate(a[0], a[1]);
      c.rotate(a[3]);
      c.drawRect(-a[2] / 2, -a[2] / 2, a[2] / 2, a[2] / 2, paint);
      c.restore();
    } else if (f.kind == 0) {
      float dy = 18 * d * ease(t);
      MusicEffectsPainter.trace(path, f.shape, w, 0, w, dy, 1, 0, 0, t, false);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(1.5f * d);
      c.drawPath(path, paint);
    } else if (f.kind == 1) {
      paint.setAlpha(255);
      float depth = EdgeSweep.depth(f.strength, d), length = EdgeSweep.halfLength(h);
      c.save();
      c.clipRect(0, 0, w, h);
      for (int side = 0; side < 2; side++) {
        float center = EdgeSweep.center(h, t, EdgeSweep.upward(f.seed, side));
        c.save();
        if (side == 1) {
          c.translate(w, 0);
          c.scale(-1, 1);
        }
        DentShape.trace(path, depth, length, center);
        c.drawPath(path, paint);
        c.restore();
      }
      c.restore();
    } else if (f.kind == 4) {
      paint.setAlpha(255);
      float depth = (4 + f.strength * 9) * 1.5f * d * pulse(t);
      for (int side = 0; side < 2; side++)
        if (f.side == 2 || f.side == side) {
          c.save();
          if (side == 1) {
            c.translate(w, 0);
            c.scale(-1, 1);
          }
          DentShape.trace(path, depth, h / 2f, h / 2f);
          c.drawPath(path, paint);
          c.restore();
        }
    } else if (f.kind == 5) {
      for (int layer = 2; layer >= 0; layer--) {
        float local = Math.max(0, (t - layer * .045f) / (1 - layer * .045f)),
            depth = (24 + f.strength * 28) * d / 1.2f * bottomPulse(local) * (1 + layer * .3f);
        paint.setAlpha(255);
        paint.setStyle(layer == 0 ? Paint.Style.FILL : Paint.Style.STROKE);
        paint.setStrokeWidth(2 * d);
        path.reset();
        if (layer == 0) path.moveTo(0, h);
        for (int i = 0; i <= 48; i++) {
          float x = i / 48f,
              envelope = (float) Math.pow(Math.sin(Math.PI * x), .48),
              ripple = 1 + .065f * (float) Math.cos(x * Math.PI * 6 - local * 5 + layer * .8f);
          float y = h - depth * envelope * ripple;
          if (i == 0) {
            if (layer == 0) path.lineTo(0, y);
            else path.moveTo(0, y);
          } else path.lineTo(w * x, y);
        }
        if (layer == 0) {
          path.lineTo(w, h);
          path.close();
        }
        c.drawPath(path, paint);
      }
    } else if (f.kind == 7) {
      paint.setStyle(Paint.Style.STROKE);
      for (int layer = 2; layer >= 0; layer--) {
        float p = ContourEcho.progress(t, layer);
        int alpha = ContourEcho.alpha(p, layer);
        if (alpha == 0) continue;
        paint.setAlpha(alpha);
        paint.setStrokeWidth((.95f - .4f * p) * d);
        ContourEcho.trace(path, w, h, d, p, layer, f.strength);
        c.drawPath(path, paint);
      }
    }
  }

  static void scene(
      Canvas c, EffectsEngine engine, int w, int h, float density, float origin, long now) {
    int layer =
        engine.shutdownAt >= 0
            ? c.saveLayerAlpha(0, 0, w, h, (int) (255 * engine.visibility(now)))
            : c.save();
    c.clipRect(0, 0, w, h);
    for (int i = 0; i < engine.items.size(); i++)
      draw(c, engine.items.get(i), w, h, density, origin, now);
    c.restoreToCount(layer);
  }
}
