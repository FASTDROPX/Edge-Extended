package dev.edgewave;

import android.media.audiofx.Visualizer;
import android.os.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

final class AudioSpectrum {
  private final android.content.Context context;

  AudioSpectrum(android.content.Context context) {
    this.context = context.getApplicationContext();
  }

  interface Listener {
    void frame(float[] bands, boolean audible);
  }

  private volatile Visualizer visualizer;
  private volatile boolean classic, closed;
  private HandlerThread thread;

  void setClassic(boolean enabled) {
    if (classic == enabled) return;
    classic = enabled;
    if (visualizer != null && !closed) visualizer.setScalingMode(Visualizer.SCALING_MODE_AS_PLAYED);
  }

  void start(Listener listener) {
    closed = false;
    thread = new HandlerThread("EdgeExtended-spectrum", android.os.Process.THREAD_PRIORITY_DISPLAY);
    thread.start();
    CountDownLatch ready = new CountDownLatch(1);
    AtomicReference<RuntimeException> failure = new AtomicReference<>();
    new Handler(thread.getLooper())
        .post(
            () -> {
              try {
                Visualizer candidate = new Visualizer(0);
                synchronized (this) {
                  if (closed) {
                    candidate.release();
                    return;
                  }
                  visualizer = candidate;
                }
                visualizer.setEnabled(false);
                visualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
                visualizer.setScalingMode(Visualizer.SCALING_MODE_AS_PLAYED);
                int result =
                    visualizer.setDataCaptureListener(
                        new Visualizer.OnDataCaptureListener() {
                          public void onWaveFormDataCapture(Visualizer v, byte[] data, int rate) {}

                          public void onFftDataCapture(Visualizer v, byte[] fft, int rate) {
                            if (closed) return;
                            boolean old = classic;
                            float[] bands = decode(fft, rate / 1000f, false);
                            float peak = 0;
                            for (float value : bands) peak = Math.max(peak, value);
                            if (!closed) listener.frame(bands, peak > .01f);
                          }
                        },
                        Math.min(60000, Visualizer.getMaxCaptureRate()),
                        false,
                        true);
                if (result != Visualizer.SUCCESS)
                  throw new IllegalStateException(context.getString(R.string.error_fft, result));
                if (visualizer.setEnabled(true) != Visualizer.SUCCESS)
                  throw new IllegalStateException(
                      context.getString(R.string.error_visualizer_disabled));
              } catch (RuntimeException e) {
                failure.set(e);
              } finally {
                ready.countDown();
              }
            });
    try {
      if (!ready.await(1800, TimeUnit.MILLISECONDS)) {
        close();
        throw new IllegalStateException(context.getString(R.string.error_visualizer_timeout));
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      close();
      throw new IllegalStateException(
          context.getString(R.string.error_audio_interrupted), interrupted);
    }
    if (failure.get() != null) {
      close();
      throw failure.get();
    }
  }

  static float[] decode(byte[] fft, float sampleRate) {
    return decode(fft, sampleRate, false);
  }

  static float[] decode(byte[] fft, float sampleRate, boolean classic) {
    float[] bands = new float[48];
    if (fft.length < 4 || sampleRate <= 0) return bands;
    for (int b = 0; b < 48; b++) {
      int lo = Math.max(1, (int) (45 * Math.pow(16000.0 / 45, b / 48.0) * fft.length / sampleRate));
      int hi =
          Math.min(
              fft.length / 2 - 1,
              Math.max(
                  lo,
                  (int) (45 * Math.pow(16000.0 / 45, (b + 1) / 48.0) * fft.length / sampleRate)));
      float peak = 0;
      for (int k = lo; k <= hi; k++)
        peak = Math.max(peak, (float) Math.hypot(fft[2 * k], fft[2 * k + 1]));
      bands[b] = Math.min(1, classic ? (float) (Math.log1p(peak) / Math.log(128)) : peak / 128f);
    }
    return bands;
  }

  synchronized void close() {
    closed = true;
    if (visualizer != null) {
      try {
        visualizer.setEnabled(false);
      } catch (RuntimeException ignored) {
      }
      visualizer.release();
      visualizer = null;
    }
    if (thread != null) {
      thread.quitSafely();
      thread = null;
    }
  }
}
