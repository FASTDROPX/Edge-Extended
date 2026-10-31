package dev.edgewave;

final class TransitionShape {
  static float y(int style, float x, float full, float travel, float presence, float time) {
    float p = Math.max(0, Math.min(1, presence));
    if (p == 0) return 0;
    if (p == 1) return full;
    switch (style) {
      case 1:
        return full * (float) Math.pow(p, 1.65);
      case 2:
        return full * smooth((p * 1.35f - Math.abs(2 * x - 1)) / .35f);
      case 3:
        return full * smooth((p * 1.35f - x) / .35f);
      case 4:
        return Math.max(
            0,
            full * p
                - travel * (1 - p) * (.32f + .68f * Math.abs(2 * x - 1))
                + travel * .06f * p * (1 - p) * (float) Math.sin(x * 12 + time * 2));
      default:
        return Math.max(0, full - travel * (1 - p));
    }
  }

  private static float smooth(float v) {
    v = Math.max(0, Math.min(1, v));
    return v * v * (3 - 2 * v);
  }
}
