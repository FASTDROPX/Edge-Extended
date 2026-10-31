package dev.edgewave;

/** Independent broad-wave synthesis: frequency order is preserved in every contour. */
final class WaveProfile {
  private static final double[] SPEEDS = {2.1, -1.55, 2.8};

  static float samplePrepared(float[] bands, float along, float seconds, int layer, float beat) {
    return contour(WaveSmoothing.at(bands, along), along, seconds, layer, beat);
  }

  static float sampleLayer(float[] bands, float along, float seconds, int layer, float beat) {
    float position = Math.max(0, Math.min(1, along)) * (bands.length - 1), energy = 0, weight = 0;
    for (int j = -2; j <= 2; j++) {
      int index = Math.max(0, Math.min(bands.length - 1, Math.round(position) + j));
      float w = (float) Math.exp(-j * j / 2.2);
      energy += bands[index] * w;
      weight += w;
    }
    return contour(energy / weight, along, seconds, layer, beat);
  }

  private static float contour(float energy, float along, float seconds, int layer, float beat) {
    double phase = along * Math.PI * (3.2 + layer * .7) + seconds * SPEEDS[layer] + layer * 1.9;
    float crest = (float) (.5 + .5 * Math.sin(phase));
    float pulse = beat * .24f * (float) Math.exp(-along * 3);
    float height = .025f + layer * .17f + energy * (.40f + .56f * crest) + pulse;
    return Math.max(0, Math.min(1.35f, height));
  }

  static boolean shouldShow(boolean audible, boolean muted) {
    return audible && !muted;
  }
}
