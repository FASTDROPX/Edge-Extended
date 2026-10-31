package dev.edgewave;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import java.util.function.BiConsumer;

/** Observes volume keys without consuming them or requesting screen contents. */
public class VolumeKeysService extends AccessibilityService {
  static volatile boolean connected;
  static volatile VolumeKeysService current;
  static volatile BiConsumer<Boolean, Boolean> listener;

  @Override
  protected void onServiceConnected() {
    AccessibilityServiceInfo info = getServiceInfo();
    info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
    info.eventTypes = 0;
    setServiceInfo(info);
    current = this;
    connected = true;
  }

  @Override
  protected boolean onKeyEvent(KeyEvent event) {
    int key = event.getKeyCode();
    if ((event.getAction() == KeyEvent.ACTION_DOWN || event.getAction() == KeyEvent.ACTION_UP)
        && (key == KeyEvent.KEYCODE_VOLUME_UP || key == KeyEvent.KEYCODE_VOLUME_DOWN)) {
      BiConsumer<Boolean, Boolean> callback = listener;
      if (callback != null)
        callback.accept(
            key == KeyEvent.KEYCODE_VOLUME_UP, event.getAction() == KeyEvent.ACTION_DOWN);
    }
    return false; // Android still changes volume and handles the key normally.
  }

  void releaseKeys() {
    BiConsumer<Boolean, Boolean> callback = listener;
    if (callback != null) {
      callback.accept(true, false);
      callback.accept(false, false);
    }
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) {}

  @Override
  public void onInterrupt() {
    releaseKeys();
  }

  @Override
  public boolean onUnbind(Intent intent) {
    current = null;
    connected = false;
    releaseKeys();
    return super.onUnbind(intent);
  }

  @Override
  public void onDestroy() {
    current = null;
    connected = false;
    releaseKeys();
    super.onDestroy();
  }
}
