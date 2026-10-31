package dev.edgewave;

/** Legacy quiet response with broad smoothing. */
final class ClassicWaveProfile {
  private static final double[] SPEEDS = {.72, -.49, 1.03};

  static float sampleLayer(float[] bands, float along, float seconds, int layer) {
    float position = Math.max(0, Math.min(1, along)) * (bands.length - 1), energy = 0, weight = 0;
    for (int j = -4; j <= 4; j++) {
      int index = Math.max(0, Math.min(bands.length - 1, Math.round(position) + j));
      float w = (float) Math.exp(-j * j / 7.0);
      energy += bands[index] * w;
      weight += w;
    }
    double phase = along * Math.PI * (3.2 + layer * .7) + seconds * SPEEDS[layer] + layer * 1.9;
    float crest = (float) (.5 + .5 * Math.sin(phase));
    return Math.max(0, Math.min(1, .08f + layer * .22f + energy / weight * (.26f + .27f * crest)));
  }

  static float samplePrepared(float[] data, float along, float seconds, int layer) {
    double phase = along * Math.PI * (3.2 + layer * .7) + seconds * SPEEDS[layer] + layer * 1.9;
    float crest = (float) (.5 + .5 * Math.sin(phase));
    return Math.max(
        0,
        Math.min(1, .08f + layer * .22f + WaveSmoothing.at(data, along) * (.26f + .27f * crest)));
  }
}
