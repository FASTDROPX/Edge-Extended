package dev.edgewave;

final class EdgeSweep {
  static boolean upward(float seed, int side) {
    int mode = Math.min(3, (int) (seed * 4));
    return mode == 1 || (mode == 2 && side == 1) || (mode == 3 && side == 0);
  }

  static float halfLength(float height) {
    return height * .31f;
  }

  static float center(float height, float t, boolean up) {
    float length = halfLength(height);
    t = Math.max(0, Math.min(1, t));
    float inverse = 1 - t, e = 1 - inverse * inverse * inverse;
    float center = -length + (height + length * 2) * e;
    return up ? height - center : center;
  }

  static float depth(float strength, float density) {
    return (4 + strength * 7) * density * .5f;
  }
}
