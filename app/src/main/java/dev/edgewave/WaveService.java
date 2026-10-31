package dev.edgewave;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.media.*;
import android.os.*;
import android.view.*;

public class WaveService extends Service {
  WindowManager wm;
  WaveView wave;
  final DentView[] dents = new DentView[2];
  final boolean[] heldKeys = new boolean[2];
  WindowManager.LayoutParams topParams;
  AudioSpectrum spectrum;
  EffectsOverlay effectsOverlay;
  VolumeKeysService rendererHost, rejectedHost;
  boolean running, screenOn = true, stopping;
  Handler main = new Handler(Looper.getMainLooper());
  AudioManager audio;
  int previous;
  long lastHardwareKey = -10000;
  static volatile boolean active, requested, waveVisible, bpmLocked, rendererTrusted;
  static volatile float bpm, bpmConfidence, fps;
  static volatile String status = "Stopped";
  static volatile Runnable resetTempo;
  final Runnable finishStop = () -> stopSelf();
  final BroadcastReceiver receiver =
      new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
          screenOn = Intent.ACTION_SCREEN_ON.equals(i.getAction());
          if (wave != null) {
            wave.setVisibility(
                screenOn && !EffectsPreviewActivity.previewing ? View.VISIBLE : View.GONE);
            wave.effects.clear();
          }
          removeDent();
          if (effectsOverlay != null) effectsOverlay.clear();
        }
      };
  final Runnable monitor =
      new Runnable() {
        public void run() {
          if (!running) return;
          VolumeKeysService desired = VolumeKeysService.current;
          if (desired == rejectedHost) desired = null;
          if (desired != rendererHost) attachRenderer();
          boolean show = screenOn && !EffectsPreviewActivity.previewing;
          if (wave.getVisibility() != (show ? View.VISIBLE : View.GONE)) {
            wave.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) {
              wave.appear = 0;
              wave.invalidate();
            } else {
              removeDent();
              wave.effects.clear();
              if (effectsOverlay != null) effectsOverlay.clear();
            }
          }
          spectrum.setClassic(
              "classic".equals(getSharedPreferences("wave", 0).getString("responseMode", "live")));
          boolean playing = audio.isMusicActive();
          if (wave.playbackActive != playing) {
            wave.playbackActive = playing;
            wave.invalidate();
          }
          int v = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
          if (v != previous) {
            boolean up = v > previous;
            long observed = SystemClock.uptimeMillis();
            main.postDelayed(
                () -> {
                  if (running && !stopping && lastHardwareKey < observed - 150) showDent(up);
                },
                100);
          }
          if (wave.muted != (v == 0)) {
            wave.muted = v == 0;
            wave.invalidate();
          }
          previous = v;
          bpm = wave.effects.tracker.bpm;
          bpmConfidence = wave.effects.tracker.confidence;
          bpmLocked = wave.effects.tracker.locked;
          fps = wave.measuredFps;
          if (effectsOverlay != null
              && effectsOverlay.view != null
              && !wave.effects.items.isEmpty()
              && effectsOverlay.view.measuredFps > 0)
            fps = Math.min(fps, effectsOverlay.view.measuredFps);
          waveVisible = wave.appear > .002f;
          main.postDelayed(this, 80);
        }
      };

  public int onStartCommand(Intent intent, int flags, int id) {
    if (intent == null) {
      stopSelf();
      return START_NOT_STICKY;
    }
    if ("stop".equals(intent.getAction())) {
      requested = false;
      stopping = true;
      removeDent();
      if (wave != null) {
        wave.exitingAt = SystemClock.uptimeMillis();
        wave.effects.beginShutdown(
            wave.exitingAt, getSharedPreferences("wave", 0).getInt("transitionMs", 450));
        wave.invalidate();
        main.removeCallbacks(finishStop);
        main.postDelayed(
            finishStop, getSharedPreferences("wave", 0).getInt("transitionMs", 450) * 3 / 2 + 80);
      } else stopSelf();
      return START_NOT_STICKY;
    }
    if (running) {
      if (stopping) {
        stopping = false;
        requested = true;
        main.removeCallbacks(finishStop);
        wave.exitingAt = 0;
        wave.effects.cancelShutdown();
        wave.invalidate();
      }
      return START_NOT_STICKY;
    }
    try {
      NotificationManager nm = getSystemService(NotificationManager.class);
      nm.createNotificationChannel(
          new NotificationChannel(
              "wave", getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW));
      PendingIntent open =
          PendingIntent.getActivity(
              this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
      PendingIntent stop =
          PendingIntent.getService(
              this,
              1,
              new Intent(this, WaveService.class).setAction("stop"),
              PendingIntent.FLAG_IMMUTABLE);
      Notification n =
          new Notification.Builder(this, "wave")
              .setSmallIcon(dev.edgewave.R.drawable.ic_wave)
              .setContentTitle(getString(R.string.notification_enabled))
              .setContentText(getString(R.string.notification_body))
              .setContentIntent(open)
              .setOngoing(true)
              .addAction(
                  new Notification.Action.Builder(null, getString(R.string.disable), stop).build())
              .build();
      if (Build.VERSION.SDK_INT >= 34)
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
      else startForeground(1, n);
      if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
          != PackageManager.PERMISSION_GRANTED)
        throw new SecurityException(getString(R.string.audio_permission_needed));
      AppDefaults.apply(getSharedPreferences("wave", 0));
      screenOn = getSystemService(PowerManager.class).isInteractive();
      wave = new WaveView(this, false);
      attachRenderer();
      if (TrackAccessService.connected) wave.effects.useTrack(TrackAccessService.trackKey);
      TrackAccessService.listener =
          key ->
              main.post(
                  () -> {
                    if (wave != null) wave.effects.useTrack(key);
                  });
      resetTempo =
          () -> {
            if (wave != null) wave.effects.recalculate();
          };
      spectrum = new AudioSpectrum(this);
      spectrum.setClassic(
          "classic".equals(getSharedPreferences("wave", 0).getString("responseMode", "live")));
      spectrum.start(
          (bands, audible) -> {
            wave.effects.offerAudioFrame(bands, SystemClock.uptimeMillis());
            wave.target = bands;
            wave.sounding = audible;
            wave.audioFrameId++;
            if (!wave.animating && audible && !wave.muted) wave.postInvalidateOnAnimation();
          });
      running = true;
      active = true;
      requested = true;
      status = getString(R.string.running_waiting);
      audio = getSystemService(AudioManager.class);
      previous = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
      wave.muted = previous == 0;
      IntentFilter f = new IntentFilter();
      f.addAction(Intent.ACTION_SCREEN_ON);
      f.addAction(Intent.ACTION_SCREEN_OFF);
      androidx.core.content.ContextCompat.registerReceiver(
          this, receiver, f, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
      VolumeKeysService.listener =
          (up, down) ->
              main.post(
                  () -> {
                    if (running && !stopping) {
                      lastHardwareKey = SystemClock.uptimeMillis();
                      int slot = up ? 0 : 1;
                      if (down) {
                        if (!heldKeys[slot]) {
                          heldKeys[slot] = true;
                          showDent(up, true);
                        }
                      } else {
                        heldKeys[slot] = false;
                        if (wave.sceneInWave) wave.releaseDent(up);
                        else if (dents[slot] != null) dents[slot].release();
                      }
                    }
                  });
      main.post(monitor);
    } catch (Exception e) {
      status = getString(R.string.start_failed) + e.getMessage();
      android.widget.Toast.makeText(this, status, android.widget.Toast.LENGTH_LONG).show();
      stopSelf();
    }
    return START_NOT_STICKY;
  }

  void attachRenderer() {
    removeDent();
    if (effectsOverlay != null) {
      effectsOverlay.close();
      effectsOverlay = null;
    }
    if (wm != null && wave.isAttachedToWindow())
      try {
        wm.removeViewImmediate(wave);
      } catch (RuntimeException ignored) {
      }
    VolumeKeysService host = VolumeKeysService.current;
    if (host == rejectedHost) host = null;
    rendererHost = host;
    wm = getSystemService(WindowManager.class);
    wave.sceneInWave = false;
    rendererTrusted = host != null;
    topParams = OverlayWindows.create(-1, 1);
    topParams.alpha = 0;
    wave.heightChanged =
        height -> {
          float opacity = height <= 1 ? 0 : 1;
          if (topParams.height != height || topParams.alpha != opacity) {
            topParams.height = height;
            topParams.alpha = opacity;
            wm.updateViewLayout(wave, topParams);
          }
        };
    wave.lastHeight = -1;
    wave.geometryDirty = true;
    wave.setOnTouchListener((v, e) -> true);
    wm.addView(wave, topParams);
    try {
      effectsOverlay =
          host == null
              ? new EffectsOverlay(this, wm, wave.effects)
              : new EffectsOverlay(
                  host, (WindowManager) host.getSystemService(WINDOW_SERVICE), wave.effects, true);
      effectsOverlay.start();
    } catch (RuntimeException failure) {
      if (host == null) throw failure;
      rejectedHost = host;
      rendererHost = null;
      rendererTrusted = false;
      effectsOverlay.close();
      effectsOverlay = new EffectsOverlay(this, wm, wave.effects);
      effectsOverlay.start();
    }
    wave.invalidate();
  }

  void removeDent() {
    removeDent(0);
    removeDent(1);
    heldKeys[0] = heldKeys[1] = false;
    if (wave != null) {
      wave.releaseDent(true);
      wave.releaseDent(false);
    }
  }

  void removeDent(int slot) {
    DentView view = dents[slot];
    if (view != null) {
      try {
        wm.removeView(view);
      } catch (Exception ignored) {
      }
      dents[slot] = null;
    }
  }

  void showDent(boolean up) {
    if (!heldKeys[up ? 0 : 1]) showDent(up, false);
  }

  void showDent(boolean up, boolean hold) {
    android.content.SharedPreferences p = getSharedPreferences("wave", 0);
    if (!p.getBoolean("volume", false) || wave.getVisibility() != View.VISIBLE) return;
    if (wave.sceneInWave) {
      if (hold) wave.pressDent(up);
      else wave.dent(up);
      return;
    }
    int slot = up ? 0 : 1;
    if (hold && dents[slot] != null) {
      dents[slot].press();
      return;
    }
    removeDent(slot);
    float density = getResources().getDisplayMetrics().density;
    int length = Math.round(p.getInt("length", 76) * density),
        depth = Math.round(p.getInt("dent3", Math.max(1, p.getInt("dent", 24) / 3)) * density);
    android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
    wm.getDefaultDisplay().getRealMetrics(metrics);
    boolean right = p.getBoolean("right", true);
    int center = metrics.heightPixels * p.getInt(up ? "up" : "down", up ? 24 : 32) / 100,
        y = Math.max(0, center - length),
        height = Math.min(metrics.heightPixels - y, length * 2);
    DentView current = new DentView(this, right, depth, length, center - y);
    dents[slot] = current;
    if (hold) current.press();
    else
      main.postDelayed(
          () -> {
            if (!current.hardwareHeld) current.release();
          },
          160);
    current.finished =
        () -> {
          if (dents[slot] == current) removeDent(slot);
        };
    WindowManager.LayoutParams params = OverlayWindows.create(depth + 1, height);
    params.x = right ? metrics.widthPixels - depth - 1 : 0;
    params.y = y;
    current.setOnTouchListener((v, e) -> true);
    wm.addView(current, params);
  }

  public void onDestroy() {
    running = false;
    TrackAccessService.listener = null;
    resetTempo = null;
    VolumeKeysService.listener = null;
    if (wave != null) wave.effects.memory.save(wave.effects.tracker);
    if (effectsOverlay != null) effectsOverlay.close();
    removeDent();
    active = requested = waveVisible = bpmLocked = rendererTrusted = false;
    bpm = bpmConfidence = fps = 0;
    main.removeCallbacksAndMessages(null);
    try {
      unregisterReceiver(receiver);
    } catch (Exception ignored) {
    }
    if (spectrum != null) spectrum.close();
    if (wm != null && wave != null)
      try {
        wm.removeView(wave);
      } catch (Exception ignored) {
      }
    stopForeground(STOP_FOREGROUND_REMOVE);
    super.onDestroy();
  }

  public IBinder onBind(Intent i) {
    return null;
  }
}
