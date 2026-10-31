package dev.edgewave;

import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.*;

/** Fallback only: a single accelerated pass-through surface, never a window per particle. */
final class EffectsOverlay {
  final Context context;
  final WindowManager wm;
  final boolean trusted;
  final EffectsEngine engine;
  Scene view;
  boolean running, attached;

  EffectsOverlay(Context c, WindowManager manager, EffectsEngine e) {
    this(c, manager, e, false);
  }

  EffectsOverlay(Context c, WindowManager manager, EffectsEngine e, boolean trust) {
    context = c;
    wm = manager;
    engine = e;
    trusted = trust;
  }

  void start() {
    if (running) return;
    running = true;
    view = new Scene();
    view.setVisibility(View.INVISIBLE);
    wm.addView(view, OverlayWindows.scene(trusted));
    attached = true;
    engine.onChanged = this::wake;
    wake();
  }

  void wake() {
    if (!running || view == null) return;
    if (view.getVisibility() != View.VISIBLE && !engine.items.isEmpty()) {
      view.fpsAt = SystemClock.uptimeMillis();
      view.frames = 0;
      view.measuredFps = 0;
    }
    view.setVisibility(engine.items.isEmpty() ? View.INVISIBLE : View.VISIBLE);
    if (!engine.items.isEmpty()) view.postInvalidateOnAnimation();
  }

  void render() {
    if (!running) start();
    wake();
  }

  void clear() {
    engine.items.clear();
    if (view != null) view.setVisibility(View.INVISIBLE);
  }

  void close() {
    running = false;
    engine.onChanged = null;
    if (attached) {
      try {
        wm.removeView(view);
      } catch (RuntimeException ignored) {
      }
      attached = false;
    }
    clear();
  }

  final class Scene extends View {
    int bottom, mode = -1, offset = -1, frames;
    long fpsAt;
    float measuredFps;
    boolean dirty = true;

    Scene() {
      super(context);
    }

    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
      dirty = true;
    }

    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
      dirty = true;
      return super.onApplyWindowInsets(insets);
    }

    protected void onDraw(Canvas c) {
      long now = SystemClock.uptimeMillis();
      frames++;
      if (now - fpsAt >= 500) {
        measuredFps = frames * 1000f / Math.max(1, now - fpsAt);
        frames = 0;
        fpsAt = now;
      }
      engine.prune(now);
      if (engine.items.isEmpty()) {
        setVisibility(INVISIBLE);
        return;
      }
      int m = engine.prefs.getInt("bottomMode", 1), o = engine.prefs.getInt("bottomOffset", 0);
      if (dirty || m != mode || o != offset) {
        mode = m;
        offset = o;
        bottom =
            ScreenGeometry.localBottom(
                this,
                getRootWindowInsets(),
                m == 1,
                Math.round(o * getResources().getDisplayMetrics().density));
        dirty = false;
      }
      EffectsPainter.scene(
          c,
          engine,
          getWidth(),
          bottom,
          getResources().getDisplayMetrics().density,
          engine.origin,
          now);
      postInvalidateOnAnimation();
    }
  }
}
