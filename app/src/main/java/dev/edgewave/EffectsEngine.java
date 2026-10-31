package dev.edgewave;

import android.content.SharedPreferences;
import java.util.*;

/** Main-thread bounded scene, batched into a single pass-through drawing surface. */
final class EffectsEngine {
  static final String[] NAMES = {
    "Шлейф волны",
    "Бегущие края",
    "Частицы сверху",
    "Частицы по бокам",
    "Боковой пульс",
    "Нижний пульс",
    "Частицы снизу",
    "Эхо контура",
    "Струны",
    "Дуэт частот"
  };
  static final String[] KEYS = {
    "fx_line",
    "fx_trace",
    "fx_particles_top",
    "fx_particles_side",
    "fx_side",
    "fx_bottom",
    "fx_particles_bottom",
    "fx_frame",
    "fx_strings",
    "fx_duet"
  };
  static final int COUNT = KEYS.length;
  final TrackPatterns patterns = new TrackPatterns();
  final java.util.concurrent.ConcurrentLinkedQueue<TrackPatterns.Event> pending =
      new java.util.concurrent.ConcurrentLinkedQueue<>();
  volatile boolean externalAnalysis;
  int testMask = -1;
  String patternStatus = "Ожидаю звук";
  int patternEvents;
  long lastPatternAt;

  boolean legacyMode() {
    return prefs.getInt("effectMode", 1) == 0;
  }

  void offerAudioFrame(float[] data, long now) {
    externalAnalysis = true;
    if (legacyMode()) return;
    TrackPatterns.Event e = patterns.analyze(data, now, tracker.bpm);
    if (e != null) {
      if (pending.size() >= 32) pending.poll();
      pending.offer(e);
    }
  }

  final BeatTracker tracker = new BeatTracker();
  final EffectScheduler scheduler = new EffectScheduler();
  final ArrayList<Fx> items = new ArrayList<>();
  long shutdownAt = -1;
  int shutdownDuration = 450;
  float origin;
  int forcedKind = -1;
  Runnable onChanged;
  final TempoMemory memory;
  final SharedPreferences prefs;
  private final Random random = new Random(121);
  private long nextId, lastFrame = -1;
  private int sideCursor, duetCursor;
  private int liveMask;
  private boolean previousLegacy;
  final long[] lastGestureAt = new long[COUNT];
  int waitingUpper = -1, waitingPriority;
  long waitingAt;
  float waitingStrength;
  float low = .5f, high = .5f, pitch = .5f;

  EffectsEngine(SharedPreferences p) {
    prefs = p;
    memory = new TempoMemory(p);
    previousLegacy = legacyMode();
  }

  void beginShutdown(long now, int duration) {
    shutdownAt = now;
    shutdownDuration = duration;
    if (onChanged != null) onChanged.run();
  }

  void cancelShutdown() {
    shutdownAt = -1;
  }

  float visibility(long now) {
    if (shutdownAt < 0) return 1;
    float progress = Math.max(0, Math.min(1, (now - shutdownAt) / (float) shutdownDuration));
    return (1 - progress) * (1 - progress);
  }

  void useTrack(String key) {
    if (key != null && !key.equals(memory.key)) {
      patterns.reset();
      pending.clear();
      waitingUpper = -1;
    }
    memory.select(key, tracker);
  }

  void recalculate() {
    memory.recalculate(tracker);
  }

  void prune(long now) {
    if (shutdownAt >= 0 && now - shutdownAt >= shutdownDuration) {
      items.clear();
      return;
    }
    int enabled = mask();
    for (int i = items.size() - 1; i >= 0; i--) {
      Fx f = items.get(i);
      if (now - f.born >= f.duration || (enabled & (1 << f.kind)) == 0) items.remove(i);
    }
  }

  int mask() {
    if (testMask >= 0) return testMask;
    if (forcedKind >= 0) return 1 << forcedKind;
    int mask = 0;
    for (int i = 0; i < COUNT; i++) if (prefs.getBoolean(KEYS[i], false)) mask |= 1 << i;
    return mask;
  }

  void update(float[] data, long now, boolean active, float[] shape) {
    boolean legacy = legacyMode();
    if (legacy != previousLegacy) {
      previousLegacy = legacy;
      pending.clear();
      waitingUpper = -1;
      patterns.reset();
      scheduler.reset();
      patternStatus = legacy ? "Старый режим · по битам" : "Новый режим · по паттернам";
    }
    liveMask = mask();
    prune(now);
    if (shutdownAt >= 0) return;
    if (!active) {
      items.clear();
      waitingUpper = -1;
      tracker.update(data, now, false);
      if (!legacy && !externalAnalysis) patterns.analyze(data, now, tracker.bpm);
      pending.clear();
      return;
    }
    if (lastFrame == now) return;
    lastFrame = now;
    BeatTracker.Beat beat = tracker.update(data, now, true);
    memory.save(tracker);
    low = high = 0;
    float frequency = 0, total = 0;
    for (int i = 0; i < 48; i++) {
      float value = Math.max(0, data[i]);
      if (i < 16) low += value / 16;
      if (i >= 32) high += value / 16;
      if (i >= 6) {
        frequency += value * i;
        total += value;
      }
    }
    low = 1 - (float) Math.exp(-low * 4);
    high = 1 - (float) Math.exp(-high * 6);
    pitch = total > 0 ? frequency / (47 * total) : .5f;
    if (forcedKind >= 0) {
      if (beat != null && beat.index % 4 == 1) spawn(forcedKind, now, beat.strength, shape);
      return;
    }
    if (legacy) {
      pending.clear();
      waitingUpper = -1;
      legacyBeat(beat, now, shape);
      return;
    }
    if (!externalAnalysis) {
      TrackPatterns.Event e = patterns.analyze(data, now, tracker.bpm);
      if (e != null) pending.offer(e);
    }
    TrackPatterns.Event event;
    while ((event = pending.poll()) != null) {
      if (now - event.time > 350) continue;
      patternStatus = MusicalDirector.describe(event);
      patternEvents++;
      lastPatternAt = now;
      int eventSide = -1;
      int eligible = liveMask;
      int chosen = MusicalDirector.choose(event, eligible, prefs.getBoolean("fx_combos", false));
      if (event.type == TrackPatterns.Type.DROP)
        for (int i = 0; items.size() > 8 && i < items.size(); ) {
          int kind = items.get(i).kind;
          if (kind == 2 || kind == 3 || kind == 6) items.remove(i);
          else i++;
        }
      // Reserve visible gestures before filling the bounded particle budget.
      for (int pass = 0; pass < 2; pass++)
        for (int kind = 0; kind < COUNT; kind++) {
          boolean particle = kind == 2 || kind == 3 || kind == 6;
          if ((pass == 1) == particle && (chosen & (1 << kind)) != 0) {
            long gap =
                kind == 5
                    ? 8000
                    : kind == 8
                        ? 400
                        : kind == 9
                            ? 250
                            : kind == 7
                                ? 2200
                                : kind == 1
                                    ? 900
                                    : kind == 3
                                        ? 650
                                        : particle
                                            ? 180
                                            : kind == 0 && event.type == TrackPatterns.Type.ROLL
                                                ? 35
                                                : 250;
            if ((event.type != TrackPatterns.Type.DROP || kind == 5)
                && lastGestureAt[kind] > 0
                && now - lastGestureAt[kind] < gap) continue;
            if (ComboPlanner.upper(kind)
                && ((compatibleMask(1 << kind) & (1 << kind)) == 0
                    || (kind != 0 && hasKind(kind)))) {
              int priority = event.type == TrackPatterns.Type.ROLL ? 2 : 1;
              if (waitingUpper < 0 || now - waitingAt > 350 || priority >= waitingPriority) {
                waitingUpper = kind;
                waitingAt = now;
                waitingPriority = priority;
                waitingStrength = event.strength;
              }
              continue;
            }
            if (kind == 1 && hasKind(kind)) continue;
            boolean lateral = kind == 3 || kind == 4;
            int side = lateral ? (eventSide < 0 ? sideCursor % 3 : eventSide) : eventSide;
            int before = items.size();
            spawn(kind, now, event.strength, shape, side);
            if (items.size() > before) {
              if (lateral && eventSide < 0) {
                eventSide = side;
                sideCursor++;
              }
              lastGestureAt[kind] = now;
            }
          }
        }
    }
    if (waitingUpper >= 0) {
      int kind = waitingUpper;
      if (now - waitingAt > 350 || (liveMask & (1 << kind)) == 0) waitingUpper = -1;
      else if ((compatibleMask(1 << kind) & (1 << kind)) != 0 && !hasKind(kind)) {
        spawn(kind, now, waitingStrength, shape);
        lastGestureAt[kind] = now;
        waitingUpper = -1;
      }
    }
  }

  void legacyBeat(BeatTracker.Beat beat, long now, float[] shape) {
    if (beat == null) return;
    patternStatus = "Старый режим · акцент по биту";
    patternEvents++;
    lastPatternAt = now;
    int eligible = compatibleMask(liveMask);
    for (int kind : new int[] {1, 8, 9}) if (hasKind(kind)) eligible &= ~(1 << kind);
    int primary = scheduler.choose(eligible, beat, tracker.bpm);
    if (primary < 0) return;
    int partner =
        ComboPlanner.partner(
            primary,
            scheduler.available(eligible, beat, tracker.bpm),
            prefs.getBoolean("fx_combos", false),
            random);
    int side = sideCursor % 3;
    boolean sideUsed = false;
    for (int kind : new int[] {primary, partner}) {
      if (kind < 0) continue;
      int before = items.size();
      spawn(kind, now, beat.strength, shape, side);
      if (items.size() > before) {
        sideUsed |= kind == 3 || kind == 4;
        if (kind == partner) scheduler.markPartner(kind, now);
      }
    }
    if (sideUsed) sideCursor++;
  }

  boolean hasKind(int kind) {
    for (Fx f : items) if (f.kind == kind) return true;
    return false;
  }

  int compatibleMask(int mask) {
    for (int i = 0; i < items.size(); i++) {
      int active = items.get(i).kind;
      for (int candidate = 0; candidate < COUNT; candidate++)
        if (ComboPlanner.conflicts(active, candidate)) mask &= ~(1 << candidate);
    }
    return mask;
  }

  void spawn(int kind, long now, float strength, float[] shape) {
    spawn(kind, now, strength, shape, -1);
  }

  void spawn(int kind, long now, float strength, float[] shape, int chosenSide) {
    if (kind < 0 || kind >= COUNT || (compatibleMask(1 << kind) & (1 << kind)) == 0) return;
    if (items.size() > 68) return;
    int side = chosenSide < 0 ? random.nextInt(3) : chosenSide;
    float period = tracker.bpm > 0 ? 60000 / tracker.bpm : 500;
    if (kind == 2 || kind == 3 || kind == 6) {
      int count = kind == 3 ? 6 + (int) (strength * 4) : 16 + (int) (strength * 8);
      if (kind == 3) {
        int activeSides = 0;
        for (Fx f : items) if (f.kind == 3) activeSides++;
        count = Math.min(count, Math.max(0, 18 - activeSides));
      }
      for (int i = 0; i < count && items.size() < 80; i++) {
        int particleSide = side == 2 ? (i % 2) : side;
        add(
            new Fx(
                ++nextId,
                kind,
                now,
                (int) Math.max(750, Math.min(1300, period * 2)) + random.nextInt(200),
                strength,
                particleSide,
                random.nextFloat(),
                random.nextFloat(),
                null));
      }
    } else {
      add(
          new Fx(
              ++nextId,
              kind,
              now,
              (int)
                  (kind == 1
                      ? Math.max(1600, Math.min(3200, period * 4))
                      : kind == 7
                          ? Math.max(1300, Math.min(2600, period * 3.2f))
                          : kind == 0
                              ? Math.max(600, Math.min(1000, period * 1.5f))
                              : kind == 8
                                  ? Math.max(550, Math.min(1000, period * 1.5f))
                                  : kind >= 9
                                      ? Math.max(850, Math.min(1600, period * 2.4f))
                                      : Math.max(400, Math.min(1000, period * 1.3f))),
              strength,
              side,
              random.nextFloat(),
              random.nextFloat(),
              shape == null ? new float[48] : shape.clone()));
    }
    if (onChanged != null) onChanged.run();
  }

  private void add(Fx item) {
    item.exitStyle = prefs.getInt("exitEffect", 2);
    if (item.kind == 9)
      item.duetReverse = prefs.getInt("duetHeights", 1) == 1 && (duetCursor++ % 2) == 1;
    item.low = low;
    item.high = high;
    item.pitch = pitch;
    items.add(item);
  }

  void syncShape(float[] shape) {
    for (int i = 0; i < items.size(); i++) {
      Fx f = items.get(i);
      if (f.kind == 9 && f.shape != null && f.shape.length == shape.length)
        System.arraycopy(shape, 0, f.shape, 0, shape.length);
      if (f.kind == 8 && f.shape != null) {
        float clearance = 0;
        for (int j = 0; j < shape.length; j++)
          clearance = Math.max(clearance, shape[j] - f.shape[j]);
        f.clearance = clearance;
      }
    }
  }

  void clear() {
    shutdownAt = -1;
    items.clear();
    tracker.suspend();
    lastFrame = -1;
    waitingUpper = -1;
    pending.clear();
    patterns.reset();
  }

  static final class Fx {
    final long id, born;
    final int kind, side, duration;
    final float strength, seed, seed2;
    final float[] shape;
    float low = .6f, high = .6f, pitch = .75f;
    float clearance;
    boolean duetReverse;
    int exitStyle = 2;

    Fx(
        long id,
        int kind,
        long born,
        int duration,
        float strength,
        int side,
        float seed,
        float seed2,
        float[] shape) {
      this.id = id;
      this.kind = kind;
      this.born = born;
      this.duration = duration;
      this.strength = strength;
      this.side = side;
      this.seed = seed;
      this.seed2 = seed2;
      this.shape = shape;
    }

    float t(long now) {
      return Math.max(0, Math.min(1, (now - born) / (float) duration));
    }
  }
}
