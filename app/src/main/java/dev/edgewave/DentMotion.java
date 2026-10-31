package dev.edgewave;

/** Geometry-only easing; never fades or times out while held. */
final class DentMotion {
  private float from;
  private long changed;
  private boolean held;

  void press(long now) {
    if (held) return;
    from = value(now);
    changed = now;
    held = true;
  }

  void release(long now) {
    if (!held) return;
    from = value(now);
    changed = now;
    held = false;
  }

  float value(long now) {
    float t = Math.max(0, Math.min(1, (now - changed) / (held ? 160f : 240f)));
    float eased = 1 - (float) Math.pow(1 - t, 3);
    return held ? from + (1 - from) * eased : from * (1 - eased);
  }

  boolean finished(long now) {
    return !held && value(now) <= 0;
  }
}
