package dev.edgewave;

import java.util.*;

/** One accent per eligible onset, with rests, variety and long cooldowns for big gestures. */
final class EffectScheduler {
  private final long[] last = new long[EffectsEngine.COUNT];
  private int previous = -1, cursor;
  private long lastAny = -10000;
  private final Random random = new Random(712);

  EffectScheduler() {
    reset();
  }

  void reset() {
    Arrays.fill(last, -100000);
    previous = -1;
    cursor = 0;
    lastAny = -10000;
    random.setSeed(712);
  }

  int available(int mask, BeatTracker.Beat beat, float bpm) {
    float period = bpm > 0 ? 60000 / bpm : 500;
    int result = 0;
    for (int i = 0; i < EffectsEngine.COUNT; i++) {
      if ((mask & (1 << i)) == 0) continue;
      boolean big = i == 1 || i == 7;
      if (big && !beat.epic) continue;
      long cooldown = big ? (long) Math.max(4500, period * 8) : (long) Math.max(400, period * 1.2f);
      if (beat.time - last[i] >= cooldown) result |= 1 << i;
    }
    return result;
  }

  void markPartner(int kind, long time) {
    if (kind >= 0) last[kind] = time;
  }

  int choose(int mask, BeatTracker.Beat beat) {
    return choose(mask, beat, 0);
  }

  int choose(int mask, BeatTracker.Beat beat, float bpm) {
    float period = bpm > 0 ? 60000 / bpm : 500;
    if (mask == 0 || beat.time - lastAny < Math.max(170, Math.min(420, period * .55f))) return -1;
    boolean strong = beat.epic;
    if (!strong && beat.index % 4 == 0 && Integer.bitCount(mask) > 2) return -1;
    ArrayList<Integer> options = new ArrayList<>();
    for (int i = 0; i < EffectsEngine.COUNT; i++) {
      if ((mask & (1 << i)) == 0) continue;
      boolean big = i == 1 || i == 7;
      if (big && !strong) continue;
      long cooldown = big ? (long) Math.max(4500, period * 8) : (long) Math.max(400, period * 1.2f);
      if (beat.time - last[i] < cooldown) continue;
      if (i != previous || Integer.bitCount(mask) == 1) options.add(i);
    }
    if (options.isEmpty()) return -1;
    if (strong) {
      ArrayList<Integer> big = new ArrayList<>();
      for (int i : options) if (i == 1 || i == 7) big.add(i);
      if (!big.isEmpty()) options = big;
    }
    int index = options.get((cursor++ + random.nextInt(options.size())) % options.size());
    previous = index;
    last[index] = beat.time;
    lastAny = beat.time;
    return index;
  }
}
