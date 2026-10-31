package dev.edgewave;

import java.util.Random;

/** Any eligible pair can be used, except the two competing side shapes. */
final class ComboPlanner {
  static boolean upper(int kind) {
    return kind == 0 || kind == 8 || kind == 9;
  }

  static boolean conflicts(int a, int b) {
    return (a == 1 && b == 4) || (a == 4 && b == 1) || (upper(a) && upper(b) && a != b);
  }

  static boolean compatible(int a, int b) {
    return a != b && !conflicts(a, b);
  }

  static int partner(int primary, int mask, boolean enabled, Random random) {
    if (!enabled || random.nextFloat() >= .45f) return -1;
    int candidates = 0;
    for (int i = 0; i < EffectsEngine.COUNT; i++)
      if ((mask & (1 << i)) != 0 && compatible(primary, i)) candidates |= 1 << i;
    if (candidates == 0) return -1;
    int skip = random.nextInt(Integer.bitCount(candidates));
    for (int i = 0; i < EffectsEngine.COUNT; i++)
      if ((candidates & (1 << i)) != 0 && skip-- == 0) return i;
    return -1;
  }
}
