package dev.edgewave;

/** More classic movement without raising its rest position; live response stays reduced. */
final class HybridWaveProfile {
  static final float CLASSIC_WEIGHT = 1.25f, REST_WEIGHT = .8f, LIVE_WEIGHT = .7f;

  static float samplePrepared(
      float[] classic,
      float[] live,
      float along,
      float slowTime,
      float liveTime,
      int layer,
      float beat) {
    float rest = .08f + layer * .22f;
    float classicValue = ClassicWaveProfile.samplePrepared(classic, along, slowTime, layer);
    float background = rest * REST_WEIGHT + (classicValue - rest) * CLASSIC_WEIGHT;
    float liveRest = .025f + layer * .17f;
    float reaction =
        Math.max(0, WaveProfile.samplePrepared(live, along, liveTime, layer, beat) - liveRest);
    return Math.max(0, Math.min(1.55f, background + reaction * LIVE_WEIGHT));
  }
}
