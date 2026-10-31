package dev.edgewave;

import java.util.Arrays;

/**
 * Causal spectral-pattern analysis. Labels describe acoustic evidence, not isolated instruments.
 */
final class TrackPatterns {
  enum Type {
    KICK,
    SNARE,
    HAT,
    TONAL,
    GLIDE,
    ACCENT,
    ROLL,
    DROP
  }

  static final class Event {
    final long time;
    final Type type;
    final int voice, subdivision;
    final float strength, similarity, centroid;
    final boolean bottomAccent, buildup;

    Event(
        long time,
        Type type,
        int voice,
        int subdivision,
        float strength,
        float similarity,
        float centroid) {
      this(
          time, type, voice, subdivision, strength, similarity, centroid, type == Type.DROP, false);
    }

    Event(
        long time,
        Type type,
        int voice,
        int subdivision,
        float strength,
        float similarity,
        float centroid,
        boolean bottomAccent,
        boolean buildup) {
      this.time = time;
      this.type = type;
      this.voice = voice;
      this.subdivision = subdivision;
      this.strength = strength;
      this.similarity = similarity;
      this.centroid = centroid;
      this.bottomAccent = bottomAccent;
      this.buildup = buildup;
    }
  }

  private final float[] previous = new float[48],
      shape = new float[48],
      baseline = new float[48],
      attackShape = new float[48];
  private final float[][] templates = new float[12][48];
  private final long[] voiceTime = new long[12];
  private final float[] voiceInterval = new float[12];
  private final int[] repeats = new int[12];
  private float noiseMean = .0002f, noiseDeviation = .0002f, previousNoiseFlux;
  private boolean noiseArmed = true;
  private long lastPercussion = -10000;
  final RollContext rollContext = new RollContext();
  private long quietDuration;
  private long candidateAt = -10000, lastBottomAt = -10000;
  private float candidateEnergy, candidateSub;
  private long lowHitAt = -10000;
  private boolean heardLow;
  private long noiseAt = -10000;
  private float noiseInterval;
  private int voices;
  private long lastFrame = -1,
      lastOnset = -10000,
      lastGlide = -10000,
      lastDrop = -10000,
      quietSince = -1,
      quietUntil = -10000,
      sustainSince = -1;
  private float previousEnergy,
      fluxMean = .001f,
      fluxDeviation = .001f,
      previousCentroid,
      glideTravel,
      glideShift,
      slowEnergy = .008f;
  private int previousDensity;

  synchronized Event analyze(float[] bands, long now, float bpm) {
    if (lastFrame >= 0 && (now < lastFrame || now - lastFrame > 2000)) reset();
    float dt = lastFrame < 0 ? .02f : Math.max(.001f, Math.min(.2f, (now - lastFrame) / 1000f));
    lastFrame = now;
    float total = 0, squares = 0, peak = 0, centroid = 0, low = 0, high = 0, logs = 0, flux = 0;
    int density = 0;
    for (int i = 0; i < 48; i++) {
      float v = Math.max(0, bands[i]);
      total += v;
      squares += v * v;
      peak = Math.max(peak, v);
      centroid += v * i;
      if (i < 16) low += v;
      if (i >= 25) high += v;
      logs += (float) Math.log(v + .000001f);
      flux += Math.max(0, v - previous[i]) / 48;
    }
    float energy = total / 48;
    centroid = total > 0 ? centroid / total : 0;
    float norm = (float) Math.sqrt(squares);
    for (int i = 0; i < 48; i++) {
      shape[i] = norm > 0 ? Math.max(0, bands[i]) / norm : 0;
      if (bands[i] > peak * .15f && bands[i] > .001f) density++;
    }
    float quietLevel = Math.max(.0006f, slowEnergy * .16f);
    boolean quiet = energy < quietLevel;
    boolean sparse =
        energy < Math.max(.0006f, slowEnergy * .4f)
            || (low / Math.max(total, .0001f) < .15f && density < 20);
    if (sparse) {
      if (quietSince < 0) quietSince = now;
      if (now - quietSince >= 450) quietUntil = now;
    } else {
      if (quietSince >= 0) quietDuration = now - quietSince;
      quietSince = -1;
    }
    float attack = (energy - previousEnergy) / Math.max(.001f, previousEnergy), novelty = 0;
    float prevNorm = 0;
    for (float v : previous) prevNorm += v * v;
    prevNorm = (float) Math.sqrt(prevNorm);
    if (prevNorm > 0)
      for (int i = 0; i < 48; i++) novelty += Math.max(0, shape[i] - previous[i] / prevNorm) / 7;
    float noiseFlux = 0, noisePeak = 0, noiseNow = 0, noiseBefore = 0;
    int noiseBins = 0;
    for (int i = 21; i < 46; i++) {
      float delta = Math.max(0, bands[i] - previous[i]);
      noiseNow += bands[i] / 25;
      noiseBefore += previous[i] / 25;
      noiseFlux += delta / 25;
      noisePeak = Math.max(noisePeak, delta);
    }
    for (int i = 21; i < 46; i++)
      if (bands[i] - previous[i] > Math.max(.0004f, noisePeak * .12f)) noiseBins++;
    float noiseThreshold = Math.max(.00025f, noiseMean * .7f + noiseDeviation * .7f);
    if (noiseFlux < noiseThreshold * .55f) noiseArmed = true;
    boolean percussion =
        (noiseNow - noiseBefore) > Math.max(.0002f, noiseBefore * .12f)
            && noisePeak / Math.max(.00001f, noiseFlux * 25) < .22f
            && noiseArmed
            && noiseBins >= 6
            && noiseFlux > noiseThreshold
            && now - lastPercussion >= 40;
    float threshold = Math.max(.0007f, fluxMean + fluxDeviation * 1.5f);
    boolean onset =
        percussion
            || energy > .001f
                && now - lastOnset >= 35
                && flux > Math.max(.00025f, threshold * .55f)
                && (attack > .10f || novelty > .055f);
    float tonality = total > 0 ? peak / total : 0,
        flatness = energy > 0 ? (float) Math.exp(logs / 48) / energy : 0;
    Event event = null;
    if (onset) {
      float attackSum = 0,
          attackPeak = 0,
          attackLow = 0,
          attackHigh = 0,
          attackCenter = 0,
          attackNorm = 0;
      for (int i = 0; i < 48; i++) {
        float v = Math.max(0, bands[i] - previous[i]) / (float) Math.sqrt(.002f + baseline[i]);
        v *= i < 16 ? .35f : 1;
        attackShape[i] = v;
        attackSum += v;
        attackNorm += v * v;
        attackPeak = Math.max(attackPeak, v);
        attackCenter += v * i;
        if (i < 16) attackLow += v;
        if (i >= 25) attackHigh += v;
      }
      attackNorm = (float) Math.sqrt(attackNorm);
      for (int i = 0; i < 48; i++) shape[i] = attackNorm > 0 ? attackShape[i] / attackNorm : 0;
      int voice = -1;
      float similarity = 0;
      for (int v = 0; v < voices; v++) {
        float dot = 0;
        for (int i = 0; i < 48; i++) dot += templates[v][i] * shape[i];
        if (dot > similarity) {
          similarity = dot;
          voice = v;
        }
      }
      if (voice < 0 || similarity < .8f) {
        voice = voices < 12 ? voices++ : oldestVoice();
        System.arraycopy(shape, 0, templates[voice], 0, 48);
        voiceTime[voice] = -10000;
        voiceInterval[voice] = 0;
        repeats[voice] = 0;
      }
      long interval = now - voiceTime[voice];
      float period = bpm > 0 ? 60000 / bpm : 500;
      boolean rapid = interval >= 35 && interval <= Math.max(240, period * .65f);
      boolean regular =
          voiceInterval[voice] == 0
              || Math.abs(interval - voiceInterval[voice])
                  < Math.max(35, voiceInterval[voice] * .28f);
      repeats[voice] = rapid && regular && similarity > .82f ? repeats[voice] + 1 : 0;
      if (rapid) voiceInterval[voice] = interval;
      else voiceInterval[voice] = 0;
      voiceTime[voice] = now;
      float attackTone = attackPeak / Math.max(.00001f, attackSum),
          attackPosition = attackCenter / Math.max(.00001f, attackSum);
      Type type =
          attackLow / Math.max(attackSum, .00001f) > .68f && attackPosition < 15
              ? Type.KICK
              : attackTone > .12f
                  ? Type.TONAL
                  : attackPosition > 35
                      ? Type.HAT
                      : attackHigh / Math.max(attackSum, .00001f) > .4f ? Type.SNARE : Type.ACCENT;
      if (percussion) {
        type = Type.SNARE;
        noiseArmed = false;
        lastPercussion = now;
      }
      if (repeats[voice] >= 1 && (type == Type.SNARE || type == Type.HAT || type == Type.ACCENT))
        type = Type.ROLL;
      if (type == Type.SNARE || type == Type.HAT || type == Type.ACCENT || type == Type.ROLL) {
        long noiseGap = now - noiseAt;
        if (noiseGap >= 35
            && noiseGap <= 240
            && (noiseInterval == 0
                || Math.abs(noiseGap - noiseInterval) < Math.max(40, noiseInterval * .4f))) {
          type = Type.ROLL;
          noiseInterval = noiseGap;
        } else noiseInterval = 0;
        noiseAt = now;
      }

      boolean sliding =
          type == Type.TONAL
              && previousEnergy > .002f
              && attack < .8f
              && Math.abs(centroid - previousCentroid) < 1.5f
              && now - lastOnset < 350;
      if (sliding) type = Type.GLIDE;
      // A return of broad low+high content after a sustained sparse/quiet passage, not a loudness
      // peak alone.
      int freshDensity = 0;
      for (float v : attackShape) if (v > attackPeak * .08f) freshDensity++;
      boolean lowReturn =
          heardLow
              && now - lowHitAt > 1800
              && attackLow / Math.max(.00001f, attackSum) > .25f
              && attackHigh / Math.max(.00001f, attackSum) > .08f
              && energy > slowEnergy * 1.35f
              && freshDensity >= 10;
      boolean drop =
          now - quietUntil < 600
              && now - lastDrop > 3500
              && freshDensity >= 10
              && low / Math.max(total, .0001f) > .12f
              && high / Math.max(total, .0001f) > .05f
              && (density > previousDensity + 3 || energy > slowEnergy * 1.8f);
      float sub = 0, oldSub = 0;
      for (int i = 0; i < 9; i++) {
        sub += bands[i];
        oldSub += previous[i];
      }
      boolean subAttack = sub - oldSub > Math.max(.005f, oldSub * .35f);
      boolean buildRelease =
          rollContext.release(
              now,
              subAttack && sub / Math.max(.00001f, total) > .35f,
              freshDensity >= 10 && energy > slowEnergy * 1.1f);
      boolean quietRelease =
          quietDuration >= 800
              && now - quietUntil < 250
              && subAttack
              && freshDensity >= 14
              && energy > slowEnergy * 2
              && attackHigh / Math.max(.00001f, attackSum) > .12f;
      boolean bottomAccent = buildRelease || quietRelease;
      if ((drop || lowReturn || buildRelease || quietRelease) && now - lastDrop > 5000) {
        type = Type.DROP;
        lastDrop = now;
        quietUntil = -10000;
      }
      if (type == Type.KICK) {
        lowHitAt = now;
        heardLow = true;
      }
      int division = rapid ? Math.max(1, Math.min(16, Math.round(period / interval))) : 1;
      float strength =
          Math.max(
              .25f, Math.min(1, .35f + novelty * .8f + flux / Math.max(threshold, .001f) * .12f));
      if (type == Type.DROP
          && !bottomAccent
          && (rollContext.active(now) || (drop && quietDuration >= 800) || lowReturn)) {
        candidateAt = now;
        candidateEnergy = energy;
        candidateSub = oldSub;
      }
      if (type == Type.DROP && bottomAccent) {
        lastBottomAt = now;
        candidateAt = -10000;
        rollContext.reset();
      } else if (type == Type.SNARE || type == Type.ROLL) rollContext.observe(now);
      if (type != Type.GLIDE || now - lastGlide >= 110) {
        event =
            new Event(
                now,
                type,
                voice,
                division,
                strength,
                similarity,
                centroid / 47,
                bottomAccent && type == Type.DROP,
                rollContext.active(now));
        if (type == Type.GLIDE) lastGlide = now;
        else lastOnset = now;
      }
    }
    if (now - candidateAt >= 50 && now - candidateAt <= 220 && now - lastBottomAt >= 8000) {
      float sub = 0;
      for (int i = 0; i < 9; i++) sub += bands[i];
      if (sub / Math.max(.00001f, total) > .4f
          && high / Math.max(.00001f, total) > .08f
          && energy > candidateEnergy * 1.25f
          && sub > Math.max(.005f, candidateSub * 1.6f)
          && density >= 8) {
        event = new Event(now, Type.DROP, 0, 1, .85f, 1, centroid / 47, true, false);
        lastBottomAt = now;
        candidateAt = -10000;
        rollContext.reset();
      }
    }
    if (event == null
        && !quiet
        && tonality > .065f
        && centroid < 34
        && energy > Math.max(.0006f, slowEnergy * .12f)) {
      if (sustainSince < 0) sustainSince = now;
      glideTravel += Math.abs(centroid - previousCentroid);
      glideShift += centroid - previousCentroid;
      if (now - sustainSince > 100
          && now - lastGlide > 130
          && Math.abs(glideShift) > .65f
          && Math.abs(glideShift) > glideTravel * .35f) {
        event = new Event(now, Type.GLIDE, 0, 1, .6f, 1, centroid / 47);
        lastGlide = now;
        glideTravel = glideShift = 0;
      }
    } else if (quiet) {
      sustainSince = -1;
      glideTravel = glideShift = 0;
    }
    for (int i = 0; i < 48; i++)
      baseline[i] += (bands[i] - baseline[i]) * (1 - (float) Math.exp(-dt * 1.5f));
    float na = 1 - (float) Math.exp(-dt * 2);
    noiseDeviation += (Math.abs(noiseFlux - noiseMean) - noiseDeviation) * na;
    noiseMean += (noiseFlux - noiseMean) * na;
    previousNoiseFlux = noiseFlux;
    float a = 1 - (float) Math.exp(-dt * 2);
    fluxDeviation += (Math.abs(flux - fluxMean) - fluxDeviation) * a;
    fluxMean += (flux - fluxMean) * a;
    slowEnergy += (energy - slowEnergy) * (1 - (float) Math.exp(-dt * .5f));
    previousEnergy = energy;
    previousCentroid = centroid;
    previousDensity = density;
    System.arraycopy(bands, 0, previous, 0, 48);
    return event;
  }

  private int oldestVoice() {
    int index = 0;
    for (int i = 1; i < 12; i++) if (voiceTime[i] < voiceTime[index]) index = i;
    return index;
  }

  synchronized void reset() {
    Arrays.fill(previous, 0);
    Arrays.fill(baseline, 0);
    Arrays.fill(voiceTime, -10000);
    Arrays.fill(voiceInterval, 0);
    Arrays.fill(repeats, 0);
    noiseMean = noiseDeviation = .0002f;
    noiseArmed = true;
    lastPercussion = -10000;
    rollContext.reset();
    quietDuration = 0;
    candidateAt = lastBottomAt = -10000;
    voices = 0;
    noiseAt = lowHitAt = -10000;
    heardLow = false;
    noiseInterval = 0;
    lastFrame = -1;
    lastOnset = lastGlide = lastDrop = quietUntil = -10000;
    quietSince = sustainSince = -1;
    previousEnergy = previousCentroid = glideTravel = glideShift = 0;
    fluxMean = fluxDeviation = .001f;
    slowEnergy = .008f;
    previousDensity = 0;
  }
}
