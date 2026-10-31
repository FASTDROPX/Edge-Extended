package dev.edgewave;

/** A bounded consecutive rhythm, not a counter accumulated across unrelated noises. */
final class RollContext {
  long last = -10000, until = -10000;
  float interval;
  int hits;

  void observe(long now) {
    long gap = now - last;
    if (interval > 0 && gap < interval * .45f) return;
    int steps = interval > 0 ? Math.max(1, Math.min(3, Math.round(gap / interval))) : 1;
    float sample = gap / (float) steps;
    boolean regular =
        gap >= 40
            && gap <= 650
            && (interval == 0
                ? gap <= 350
                : Math.abs(sample - interval) <= Math.max(30, interval * .3f));
    if (regular) {
      hits++;
      interval = interval == 0 ? sample : interval * .4f + sample * .6f;
    } else {
      hits = 1;
      interval = 0;
      until = -10000;
    }
    last = now;
    if (hits >= 4) until = now + 1200;
  }

  boolean release(long now, boolean subBassAttack, boolean broadAttack) {
    return hits >= 4
        && now <= until
        && now - last >= 60
        && now - last <= 1200
        && subBassAttack
        && broadAttack;
  }

  boolean active(long now) {
    return hits >= 4 && now <= until;
  }

  void reset() {
    last = until = -10000;
    interval = 0;
    hits = 0;
  }
}
