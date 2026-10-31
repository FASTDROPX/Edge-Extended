package dev.edgewave;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.FrameLayout;
import com.google.android.material.materialswitch.MaterialSwitch;

/** A white immersive stage. Its only app control is the centre on/off switch. */
public class WhiteStageActivity extends androidx.appcompat.app.AppCompatActivity {
  MaterialSwitch control;
  boolean syncing, pendingStart, notificationAsked;
  FrameLayout root;
  long startUntil;
  final Handler handler = new Handler(Looper.getMainLooper());
  final Runnable poll =
      new Runnable() {
        public void run() {
          if (!pendingStart && (WaveService.active || SystemClock.uptimeMillis() > startUntil))
            sync();
          handler.postDelayed(this, 300);
        }
      };

  protected void onCreate(Bundle state) {
    super.onCreate(state);
    AppDefaults.apply(getSharedPreferences("wave", 0));
    getWindow().setStatusBarContrastEnforced(false);
    getWindow().setNavigationBarContrastEnforced(false);
    getWindow().setStatusBarColor(Color.TRANSPARENT);
    getWindow().setNavigationBarColor(Color.TRANSPARENT);
    getWindow().getAttributes().layoutInDisplayCutoutMode =
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
    root = new FrameLayout(this);
    root.setBackgroundColor(Color.WHITE);
    setContentView(root);
    control = new MaterialSwitch(this);
    control.setText(null);
    control.setShowText(false);
    control.setMinHeight(Math.round(48 * getResources().getDisplayMetrics().density));
    control.setContentDescription(getString(R.string.enable_visualization));
    root.addView(control, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
    sync();
    control.setOnCheckedChangeListener(
        (button, on) -> {
          if (syncing) return;
          control.setContentDescription(
              on
                  ? getString(R.string.disable_visualization)
                  : getString(R.string.enable_visualization));
          if (on) requestStart();
          else {
            startUntil = 0;
            pendingStart = false;
            startService(new Intent(this, WaveService.class).setAction("stop"));
          }
        });
    immersive();
  }

  void immersive() {
    int flags =
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
    getWindow().getDecorView().setSystemUiVisibility(flags);
    if (Build.VERSION.SDK_INT >= 30) {
      getWindow().setDecorFitsSystemWindows(false);
      WindowInsetsController c = getWindow().getInsetsController();
      if (c != null) {
        c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        c.hide(WindowInsets.Type.systemBars());
      }
    }
  }

  void sync() {
    syncing = true;
    boolean on = WaveService.active && WaveService.requested;
    control.setChecked(on);
    control.setContentDescription(
        on ? getString(R.string.disable_visualization) : getString(R.string.enable_visualization));
    syncing = false;
  }

  protected void onResume() {
    super.onResume();
    immersive();
    if (pendingStart) {
      if (Settings.canDrawOverlays(this)) requestStart();
      else {
        pendingStart = false;
        setOff();
      }
    } else if (control != null && SystemClock.uptimeMillis() > startUntil) sync();
    handler.post(poll);
  }

  protected void onPause() {
    handler.removeCallbacks(poll);
    super.onPause();
  }

  public void onWindowFocusChanged(boolean focused) {
    super.onWindowFocusChanged(focused);
    if (focused) immersive();
  }

  void requestStart() {
    pendingStart = true;
    if (!Settings.canDrawOverlays(this)) {
      setOff();
      startActivity(
          new Intent(
              Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
      return;
    }
    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, 4);
      return;
    }
    if (Build.VERSION.SDK_INT >= 33
        && !notificationAsked
        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
      notificationAsked = true;
      requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 5);
      return;
    }
    pendingStart = false;
    startUntil = SystemClock.uptimeMillis() + 2500;
    startForegroundService(new Intent(this, WaveService.class));
    syncing = true;
    control.setChecked(true);
    syncing = false;
  }

  void setOff() {
    syncing = true;
    control.setChecked(false);
    control.setContentDescription(getString(R.string.enable_visualization));
    syncing = false;
  }

  public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(code, permissions, results);
    if (code == 5 || (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED))
      requestStart();
    else {
      pendingStart = false;
      setOff();
    }
  }
}
