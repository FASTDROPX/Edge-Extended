package dev.edgewave;

import android.graphics.Path;

/** A quiet three-layer edge echo; shallow travel and the same smooth curves as the waves. */
final class ContourEcho {
  static float progress(float time, int layer) {
    return Math.max(0, Math.min(1, (time - layer * .08f) / (1 - layer * .08f)));
  }

  static float inset(float progress, int layer, float strength, float density) {
    float inverse = 1 - progress;
    return (1 + (8 + strength * 6) * (1 - inverse * inverse) + layer * 3.5f) * density;
  }

  static int alpha(float progress, int layer) {
    if (progress <= 0 || progress >= 1) return 0;
    return Math.round(
        (layer == 0 ? 132 : layer == 1 ? 84 : 48)
            * (float) Math.pow(Math.sin(Math.PI * progress), .85));
  }

  static void trace(Path path, int w, int h, float d, float progress, int layer, float strength) {
    float inset = inset(progress, layer, strength, d);
    float left = inset, top = inset, right = w - inset, bottom = h - inset;
    if (right - left < 4 || bottom - top < 4) {
      path.reset();
      return;
    }
    float radius = Math.min(26 * d, Math.min(right - left, bottom - top) * .18f);
    float ripple =
        (.7f + strength)
            * d
            * (float) Math.sin(progress * Math.PI)
            * (.7f + .3f * (float) Math.sin(progress * 5 + layer));
    path.reset();
    path.moveTo(left + radius, top);
    path.cubicTo(w * .38f, top + ripple, w * .62f, top - ripple, right - radius, top);
    path.quadTo(right, top, right, top + radius);
    path.cubicTo(right - ripple, h * .38f, right + ripple, h * .62f, right, bottom - radius);
    path.quadTo(right, bottom, right - radius, bottom);
    path.cubicTo(w * .62f, bottom - ripple, w * .38f, bottom + ripple, left + radius, bottom);
    path.quadTo(left, bottom, left, bottom - radius);
    path.cubicTo(left + ripple, h * .62f, left - ripple, h * .38f, left, top + radius);
    path.quadTo(left, top, left + radius, top);
    path.close();
  }
}
