package dev.edgewave;

import android.content.Context;
import android.content.res.Configuration;
import androidx.appcompat.app.AppCompatDelegate;

final class AppAppearance {
  static int nightMode(int choice) {
    return choice == 1
        ? AppCompatDelegate.MODE_NIGHT_NO
        : choice == 2
            ? AppCompatDelegate.MODE_NIGHT_YES
            : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
  }

  static void apply(Context context) {
    AppCompatDelegate.setDefaultNightMode(
        nightMode(context.getSharedPreferences("wave", 0).getInt("themeMode", 0)));
  }

  static boolean dark(Context context) {
    return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
        == Configuration.UI_MODE_NIGHT_YES;
  }
}
