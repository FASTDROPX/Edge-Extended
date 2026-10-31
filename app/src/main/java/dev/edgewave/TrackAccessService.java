package dev.edgewave;

import android.content.*;
import android.media.*;
import android.media.session.*;
import android.os.*;
import android.service.notification.NotificationListenerService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;

/** Uses media-session metadata only; notification payloads are not inspected. */
public class TrackAccessService extends NotificationListenerService {
  static volatile boolean connected;
  static volatile String trackKey = "", trackTitle = "";
  static volatile Consumer<String> listener;
  private MediaSessionManager manager;
  private MediaController selected;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final MediaSessionManager.OnActiveSessionsChangedListener sessions = this::select;
  private final MediaController.Callback callback =
      new MediaController.Callback() {
        public void onMetadataChanged(MediaMetadata metadata) {
          publish();
        }

        public void onPlaybackStateChanged(PlaybackState state) {
          refresh();
        }

        public void onSessionDestroyed() {
          selected = null;
          refresh();
        }
      };

  public void onListenerConnected() {
    connected = true;
    manager = getSystemService(MediaSessionManager.class);
    try {
      manager.addOnActiveSessionsChangedListener(
          sessions, new ComponentName(this, TrackAccessService.class), handler);
      refresh();
    } catch (SecurityException ignored) {
      connected = false;
    }
  }

  private void refresh() {
    if (manager == null || !connected) return;
    try {
      select(manager.getActiveSessions(new ComponentName(this, TrackAccessService.class)));
    } catch (SecurityException ignored) {
    }
  }

  private void select(List<MediaController> controllers) {
    if (controllers == null) return;
    MediaController next = null;
    for (MediaController c : controllers) {
      if (c.getMetadata() == null) continue;
      if (next == null) next = c;
      PlaybackState state = c.getPlaybackState();
      if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
        next = c;
        break;
      }
    }
    if (next == null) return;
    if (selected == null || !selected.getSessionToken().equals(next.getSessionToken())) {
      if (selected != null) selected.unregisterCallback(callback);
      selected = next;
      selected.registerCallback(callback, handler);
    }
    publish();
  }

  private void publish() {
    if (selected == null) return;
    MediaMetadata m = selected.getMetadata();
    if (m == null) return;
    String title = m.getString(MediaMetadata.METADATA_KEY_TITLE),
        artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST),
        id = m.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
    if ((title == null || title.isEmpty()) && (id == null || id.isEmpty())) return;
    String raw =
        selected.getPackageName()
            + "\n"
            + id
            + "\n"
            + title
            + "\n"
            + artist
            + "\n"
            + m.getLong(MediaMetadata.METADATA_KEY_DURATION);
    String key = hash(raw);
    trackTitle = title == null ? getString(R.string.current_track) : title;
    if (!key.equals(trackKey)) {
      trackKey = key;
      Consumer<String> cb = listener;
      if (cb != null) cb.accept(key);
    }
  }

  static String hash(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder s = new StringBuilder();
      for (byte b : digest) s.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
      return s.toString();
    } catch (Exception impossible) {
      return Integer.toHexString(value.hashCode());
    }
  }

  public void onListenerDisconnected() {
    stop();
  }

  private void stop() {
    connected = false;
    if (manager != null)
      try {
        manager.removeOnActiveSessionsChangedListener(sessions);
      } catch (RuntimeException ignored) {
      }
    if (selected != null) {
      selected.unregisterCallback(callback);
      selected = null;
    }
  }

  public void onDestroy() {
    stop();
    super.onDestroy();
  }
}
