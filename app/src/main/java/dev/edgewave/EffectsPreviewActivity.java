package dev.edgewave;

import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.google.android.material.button.MaterialButton;

public class EffectsPreviewActivity extends androidx.appcompat.app.AppCompatActivity {
  static volatile boolean previewing;
  private final Handler main = new Handler(Looper.getMainLooper());
  WaveView wave;
  TextView diagnostic;
  LinearLayout panel;

  protected void onResume() {
    super.onResume();
    previewing = true;
    main.post(update);
  }

  protected void onPause() {
    previewing = false;
    main.removeCallbacksAndMessages(null);
    super.onPause();
  }

  private final Runnable update =
      new Runnable() {
        public void run() {
          diagnostic.setText(
              getString(R.string.preview_prefix)
                  + Math.round(wave.measuredFps)
                  + getString(R.string.preview_suffix));
          main.postDelayed(this, 500);
        }
      };

  int dp(int v) {
    return Math.round(v * getResources().getDisplayMetrics().density);
  }

  protected void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().setStatusBarContrastEnforced(false);
    getWindow().setNavigationBarContrastEnforced(false);
    getWindow().setNavigationBarColor(Color.TRANSPARENT);
    FrameLayout root = new FrameLayout(this);
    root.setBackgroundColor(
        com.google.android.material.color.MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorSurface, Color.WHITE));
    setContentView(root);
    wave = new WaveView(this, true);
    wave.forcedEffect = getIntent().getIntExtra("effect", -1);
    root.addView(wave, new FrameLayout.LayoutParams(-1, -1));
    diagnostic = new com.google.android.material.textview.MaterialTextView(this);
    diagnostic.setTextAppearance(
        com.google.android.material.R.style.TextAppearance_Material3_LabelSmall);
    diagnostic.setTextColor(
        com.google.android.material.color.MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY));
    diagnostic.setTextSize(11);
    diagnostic.setGravity(Gravity.CENTER);
    FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(-1, dp(24), Gravity.TOP);
    root.addView(diagnostic, textParams);
    panel = new LinearLayout(this);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setGravity(Gravity.CENTER);
    panel.setPadding(dp(16), 0, dp(16), 0);
    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
    lp.topMargin = dp(56);
    root.addView(panel, lp);
    MaterialButton toggle = new MaterialButton(this);
    toggle.setText(getString(R.string.toggle_transition));
    toggle.setOnClickListener(
        v -> {
          wave.demoPlaying = !wave.demoPlaying;
          wave.invalidate();
        });
    panel.addView(toggle);
    MaterialButton close = new MaterialButton(this);
    close.setText(getString(R.string.close_test));
    close.setOnClickListener(v -> finish());
    panel.addView(close);
    root.setOnApplyWindowInsetsListener(
        (v, i) -> {
          int top = i.getSystemWindowInsetTop();
          FrameLayout.LayoutParams a = (FrameLayout.LayoutParams) diagnostic.getLayoutParams();
          a.topMargin = top + dp(6);
          diagnostic.setLayoutParams(a);
          FrameLayout.LayoutParams b = (FrameLayout.LayoutParams) panel.getLayoutParams();
          b.topMargin = top + dp(36);
          panel.setLayoutParams(b);
          return i;
        });
    root.setOnClickListener(
        v -> panel.setVisibility(panel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
    main.postDelayed(() -> panel.setVisibility(View.GONE), 2500);
  }
}
