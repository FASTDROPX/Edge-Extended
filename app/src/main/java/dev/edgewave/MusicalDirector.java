package dev.edgewave;

/** Sound roles choose gesture families; no random effect lottery. */
final class MusicalDirector {
  static int choose(TrackPatterns.Event e, int enabled, boolean combos) {
    if (e.type == TrackPatterns.Type.DROP) {
      if (!e.bottomAccent && e.strength < .75f)
        return first(enabled & ((1 << 8) | (1 << 9) | (1 << 3)));
      int result = enabled & ~((1 << 1) | (1 << 4));
      if (!e.bottomAccent) result &= ~(1 << 5);
      int upper = first(result & ((1 << 8) | (1 << 9)));
      if (upper == 0) upper = result & 1;
      result = (result & ~((1 << 8) | (1 << 9) | 1)) | upper;
      if (!combos) return e.bottomAccent && (result & (1 << 5)) != 0 ? 1 << 5 : first(result);
      return result;
    }
    enabled &= ~(1 << 5);
    if (enabled == 0) return 0;
    int[] family =
        switch (e.type) {
          case ROLL -> new int[] {0, 2, 3};
          case SNARE -> new int[] {0, 2, 3};
          case HAT -> new int[] {2, 3};
          case KICK -> new int[] {4, 3, 6, 2};
          case GLIDE -> new int[] {9, 1, 8};
          case TONAL -> new int[] {8, 9, 1};
          default -> new int[] {3, 2, 7};
        };
    int primary = -1;
    for (int kind : family)
      if ((enabled & (1 << kind)) != 0) {
        primary = kind;
        break;
      }
    if (primary < 0) return 0;
    int result = 1 << primary;
    if (combos && e.type == TrackPatterns.Type.SNARE && (enabled & (1 << 2)) != 0) result |= 1 << 2;

    return result;
  }

  private static int first(int mask) {
    return mask & -mask;
  }

  static String describe(TrackPatterns.Event e) {
    return switch (e.type) {
      case KICK -> "Низкая атака";
      case SNARE -> "Шумовая атака";
      case HAT -> "Высокочастотная атака";
      case TONAL -> "Тональная атака";
      case GLIDE -> "Спектральное скольжение";
      case ROLL ->
          (e.buildup ? "Регулярная дробь · возможное нарастание · ×" : "Повторяющаяся дробь · ×")
              + e.subdivision;
      case DROP -> "Возврат плотного спектра после затишья";
      default -> "Спектральный акцент";
    };
  }
}
