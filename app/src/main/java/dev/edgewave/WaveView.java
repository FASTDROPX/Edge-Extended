package dev.edgewave;

import android.content.*;
import android.graphics.*;
import android.view.*;

public class WaveView extends View {
  final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  final Path path = new Path();
  final android.content.SharedPreferences p;
  final boolean preview;
  final EffectsEngine effects;
  int forcedEffect = -1;
  volatile long audioFrameId;
  long analyzedFrame = -1, lastSound = -10000;
  final long demoStart = android.os.SystemClock.uptimeMillis();
  float demoClock;
  boolean sceneInWave, geometryDirty = true;
  volatile boolean animating;
  float measuredFps, cameraBase;
  int cachedBottom, cachedMode = -1, cachedOffset = -1;
  long fpsAt;
  int fpsFrames;
  final float[] prepared = new float[48],
      classicLevels = new float[48],
      classicPrepared = new float[48];
  float classicPhase;
  final float[] effectShape = new float[48];
  java.util.function.IntConsumer heightChanged;
  int lastHeight = -1;
  boolean demoPlaying = true;
  final SpectrumMotion motion = new SpectrumMotion();
  float[] levels = motion.levels;
  final float[] demoData = new float[48];
  volatile float[] target = new float[48];
  volatile boolean sounding, muted, playbackActive;
  float appear;
  long exitingAt;
  long last = System.nanoTime();
  float phase;
  final DentMotion[] previewDents = {new DentMotion(), new DentMotion()};
  final boolean[] previewHeld = new boolean[2];

  public WaveView(Context c, boolean demo) {
    super(c);
    preview = demo;
    p = c.getSharedPreferences("wave", 0);
    AppDefaults.apply(p);
    effects = new EffectsEngine(p);
    cameraBase = dp(46);
    paint.setColor(0xff000000);
    setAlpha(1f);
  }

  float dp(float x) {
    return x * getResources().getDisplayMetrics().density;
  }

  public void dent(boolean up) {
    int slot = up ? 0 : 1;
    previewDents[slot].press(android.os.SystemClock.uptimeMillis());
    postDelayed(
        () -> {
          if (!previewHeld[slot]) previewDents[slot].release(android.os.SystemClock.uptimeMillis());
        },
        160);
    invalidate();
  }

  public void pressDent(boolean up) {
    int slot = up ? 0 : 1;
    previewHeld[slot] = true;
    previewDents[slot].press(android.os.SystemClock.uptimeMillis());
    invalidate();
  }

  public void releaseDent(boolean up) {
    int slot = up ? 0 : 1;
    previewHeld[slot] = false;
    previewDents[slot].release(android.os.SystemClock.uptimeMillis());
    invalidate();
  }

  private final android.content.SharedPreferences.OnSharedPreferenceChangeListener
      preferenceChanged =
          (settings, key) -> {
            geometryDirty = true;
            postInvalidateOnAnimation();
          };

  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    p.registerOnSharedPreferenceChangeListener(preferenceChanged);
  }

  protected void onDetachedFromWindow() {
    p.unregisterOnSharedPreferenceChangeListener(preferenceChanged);
    super.onDetachedFromWindow();
  }

  protected void onSizeChanged(int w, int h, int oldW, int oldH) {
    geometryDirty = true;
  }

  public WindowInsets onApplyWindowInsets(WindowInsets insets) {
    geometryDirty = true;
    return super.onApplyWindowInsets(insets);
  }

  private void updateGeometry() {
    WindowInsets insets = getRootWindowInsets();
    cameraBase = dp(46);
    if (insets != null && insets.getDisplayCutout() != null) {
      int bottom = 0;
      for (Rect r : insets.getDisplayCutout().getBoundingRects())
        if (r.top < dp(80)) bottom = Math.max(bottom, r.bottom);
      if (bottom > 0) cameraBase = bottom + dp(2);
    }
    cachedMode = p.getInt("bottomMode", 1);
    cachedOffset = p.getInt("bottomOffset", 0);
    cachedBottom =
        ScreenGeometry.localBottom(this, insets, cachedMode == 1, Math.round(dp(cachedOffset)));
    geometryDirty = false;
  }

  protected void onDraw(Canvas c) {
    super.onDraw(c);
    long now = System.nanoTime();
    float dt = Math.min(.05f, (now - last) / 1e9f);
    last = now;
    long clock = android.os.SystemClock.uptimeMillis();
    demoClock = (clock - demoStart) / 1000f;
    fpsFrames++;
    if (clock - fpsAt >= 1000) {
      measuredFps = fpsFrames * 1000f / Math.max(1, clock - fpsAt);
      fpsFrames = 0;
      fpsAt = clock;
    }
    if (sounding) lastSound = clock;
    String mode = p.getString("responseMode", "live");
    boolean classic = "classic".equals(mode), hybrid = "hybrid".equals(mode);
    float speed =
        p.getInt(hybrid ? "hybridSpeed" : classic ? "classicSpeed" : "waveSpeed", 130) / 100f;
    classicPhase += dt * speed;
    phase += dt * speed * (classic ? 1 : 1 + motion.beat * .65f);
    boolean enabled =
        preview
            ? (demoPlaying && !muted)
            : WaveProfile.shouldShow(sounding || playbackActive || clock - lastSound < 260, muted);
    float goal = enabled && exitingAt == 0 ? 1 : 0;
    int duration = p.getInt("transitionMs", 450);
    appear += (goal - appear) * (1 - (float) Math.exp(-dt * (4.6f / (duration / 1000f))));
    if (goal == 0 && appear < .002f) appear = 0;
    if (goal == 1 && appear > .998f) appear = 1;
    int transition = p.getInt(goal > appear ? "enterEffect" : "exitEffect", 2);
    int w = getWidth(), h = getHeight();
    if (geometryDirty
        || cachedMode != p.getInt("bottomMode", 1)
        || cachedOffset != p.getInt("bottomOffset", 0)) updateGeometry();
    float base = cameraBase;
    float top = Math.max(dp(8), base + dp(p.getInt("offset2", 0)));
    if (preview && h < dp(300)) top = Math.max(dp(10), dp(26) + dp(p.getInt("offset2", 0)) * .55f);
    float depth = dp(p.getInt("depth2", 24));
    float[] data = target;
    if (preview) {
      float cycle = (demoClock % .5f) / .5f,
          kick = (float) Math.exp(-cycle * 13),
          lift = (demoClock % 12) > 9 ? 2.4f : 1;
      for (int i = 0; i < 48; i++) {
        float raw =
            demoPlaying && !muted
                ? Math.max(
                        0,
                        (float) (.025 + .015 * Math.sin(i * .2 - demoClock * 2))
                            + kick * .43f * (float) Math.exp(-i / 20f))
                    * lift
                : 0;
        demoData[i] = classic ? (float) (Math.log1p(Math.min(1, raw) * 128) / Math.log(128)) : raw;
      }
      data = demoData;
    }
    float[] analysisData = data;
    if (classic && !preview) {
      for (int i = 0; i < 48; i++)
        demoData[i] = (float) (Math.log1p(Math.min(1, data[i]) * 128) / Math.log(128));
      data = demoData;
    }
    if (classic) {
      float sensitivity = p.getInt("classicSensitivity", 100) / 100f,
          attack = 1 - (float) Math.exp(-dt * 12),
          release = 1 - (float) Math.exp(-dt * 5);
      for (int i = 0; i < 48; i++) {
        float v = Math.min(1, data[i] * sensitivity);
        levels[i] += (v - levels[i]) * (v > levels[i] ? attack : release);
      }
    } else
      motion.update(data, dt, p.getInt(hybrid ? "hybridSensitivity" : "sensitivity", 160) / 100f);
    WaveSmoothing.prepare(levels, prepared, classic);
    if (hybrid) {
      float attack = 1 - (float) Math.exp(-dt * 12),
          release = 1 - (float) Math.exp(-dt * 5),
          sensitivity = p.getInt("hybridSensitivity", 160) / 160f;
      for (int i = 0; i < 48; i++) {
        float value =
            Math.min(
                1, (float) (Math.log1p(Math.min(1, data[i]) * 128) / Math.log(128)) * sensitivity);
        classicLevels[i] +=
            (value - classicLevels[i]) * (value > classicLevels[i] ? attack : release);
      }
      WaveSmoothing.prepare(classicLevels, classicPrepared, true);
    }
    float peakY = 0;
    if (appear > 0) {
      // Three independently moving contours; solid black core over translucent black waves.
      float travel = top + depth * (hybrid ? 1.6f : classic ? 1 : 1.4f);
      int count = 47;
      for (int layer = 2; layer >= 0; layer--) {
        paint.setColor(Color.argb(layer == 0 ? 255 : layer == 1 ? 125 : 55, 0, 0, 0));
        path.reset();
        path.moveTo(0, 0);
        float px = 0, py = 0;
        for (int i = 0; i <= count; i++) {
          float x = w * i / (float) count;
          float full =
              top
                  + depth
                      * (hybrid
                          ? HybridWaveProfile.samplePrepared(
                              classicPrepared,
                              prepared,
                              i / (float) count,
                              classicPhase,
                              phase,
                              layer,
                              motion.beat)
                          : classic
                              ? ClassicWaveProfile.samplePrepared(
                                  prepared, i / (float) count, phase, layer)
                              : WaveProfile.samplePrepared(
                                  prepared, i / (float) count, phase, layer, motion.beat));
          float y = TransitionShape.y(transition, i / (float) count, full, travel, appear, phase);
          peakY = Math.max(peakY, y);
          if (i == 0) path.lineTo(x, y);
          else path.quadTo(px, py, (px + x) / 2, (py + y) / 2);
          px = x;
          py = y;
        }
        path.lineTo(w, py);
        path.lineTo(w, 0);
        path.close();
        c.drawPath(path, paint);
      }
    }
    effects.origin = top + depth * .4f;
    effects.forcedKind = forcedEffect;
    for (int i = 0; i < 48; i++) {
      float outer = 0;
      for (int layer = 0; layer < 3; layer++)
        outer =
            Math.max(
                outer,
                top
                    + depth
                        * (hybrid
                            ? HybridWaveProfile.samplePrepared(
                                classicPrepared,
                                prepared,
                                i / 47f,
                                classicPhase,
                                phase,
                                layer,
                                motion.beat)
                            : classic
                                ? ClassicWaveProfile.samplePrepared(prepared, i / 47f, phase, layer)
                                : WaveProfile.samplePrepared(
                                    prepared, i / 47f, phase, layer, motion.beat)));
      effectShape[i] = outer;
    }
    effects.syncShape(effectShape);
    if (preview || analyzedFrame != audioFrameId || !enabled || exitingAt != 0) {
      analyzedFrame = audioFrameId;
      effects.update(analysisData, clock, enabled && exitingAt == 0, effectShape);
    }
    if (preview || sceneInWave) {
      effects.prune(clock);
      EffectsPainter.scene(
          c,
          effects,
          w,
          cachedBottom,
          getResources().getDisplayMetrics().density * (preview && h < dp(300) ? .45f : 1),
          effects.origin,
          clock);
    }
    paint.setColor(Color.BLACK);
    boolean dentAnimating = false;
    if ((preview || sceneInWave) && p.getBoolean("volume", false))
      for (int slot = 0; slot < 2; slot++) {
        float amount = previewDents[slot].value(clock);
        if (amount <= 0) continue;
        if (!previewHeld[slot] || amount < .999f) dentAnimating = true;
        boolean plus = slot == 0, small = preview && h < dp(300);
        float cy =
            small
                ? dp(48) + (h - dp(48)) * p.getInt(plus ? "up" : "down", plus ? 24 : 32) / 100f
                : h * p.getInt(plus ? "up" : "down", plus ? 24 : 32) / 100f;
        float length = dp(p.getInt("length", 76)) * (small ? .45f : 1),
            d =
                dp(p.getInt("dent3", Math.max(1, p.getInt("dent", 24) / 3)))
                    * amount
                    * (small ? .55f : 1);
        c.save();
        if (p.getBoolean("right", true)) {
          c.translate(w, 0);
          c.scale(-1, 1);
        }
        DentShape.trace(path, d, length, cy);
        c.drawPath(path, paint);
        c.restore();
      }
    if (!preview && heightChanged != null) {
      int needed = peakY > 0 ? (int) Math.ceil(top + depth * (hybrid ? 1.6f : 1.4f)) + 2 : 1;
      if (needed != lastHeight) {
        lastHeight = needed;
        java.util.function.IntConsumer callback = heightChanged;
        post(
            () -> {
              if (isAttachedToWindow() && heightChanged == callback) callback.accept(needed);
            });
      }
    }
    animating = enabled || appear > 0 || !effects.items.isEmpty() || dentAnimating;
    if (animating && isAttachedToWindow()) postInvalidateOnAnimation();
  }
}
