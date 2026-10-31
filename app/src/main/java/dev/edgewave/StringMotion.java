package dev.edgewave;

/** Fixed random choices per gesture; no random work or allocations during drawing. */
final class StringMotion {
  static boolean rightToLeft(EffectsEngine.Fx f) {
    return f.seed < .5f;
  }

  static int rank(EffectsEngine.Fx f, int line) {
    return f.seed2 < .5f ? line : 2 - line;
  }

  static int mode(EffectsEngine.Fx f) {
    return Math.min(5, (int) (f.seed2 * 6)) % 3;
  }

  static float local(EffectsEngine.Fx f, int line, float t) {
    return Math.max(
        0, (t * f.duration - rank(f, line) * 45) / Math.max(1, f.duration - rank(f, line) * 45));
  }

  static float reveal(EffectsEngine.Fx f, int line, float t) {
    float elapsed = t * f.duration - rank(f, line) * 45;
    float p = Math.max(0, Math.min(1, elapsed / 300));
    return p * p * (3 - 2 * p);
  }

  static float separation(EffectsEngine.Fx f, int line, float t) {
    float p = t * t * (3 - 2 * t), a = (8 + 7 * f.seed) * p;
    switch (mode(f)) {
      case 0:
        return (line - 1) * a;
      case 1:
        return line == 0 ? 0 : line == 1 ? a * .6f : a;
      default:
        return line == 0 ? -a : line == 1 ? -a * .6f : 0;
    }
  }

  static float bend(EffectsEngine.Fx f, float x, int line, float t) {
    return (float)
            (Math.sin(Math.PI * x)
                * Math.sin(x * Math.PI * 2 + t * 2.2 + f.seed * 6.28 + line * .45)
                * Math.sin(Math.PI * t))
        * 1.3f;
  }
}
