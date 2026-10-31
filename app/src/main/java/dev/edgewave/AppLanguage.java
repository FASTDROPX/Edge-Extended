package dev.edgewave;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

final class AppLanguage {
  static final String[] TAGS = {"", "ru", "en", "zh-Hans", "fr", "de"};

  static int index() {
    String tags = AppCompatDelegate.getApplicationLocales().toLanguageTags();
    for (int i = 1; i < TAGS.length; i++)
      if (tags.startsWith(TAGS[i]) || (i == 3 && tags.startsWith("zh"))) return i;
    return 0;
  }

  static void select(int index) {
    AppCompatDelegate.setApplicationLocales(
        LocaleListCompat.forLanguageTags(TAGS[Math.max(0, Math.min(TAGS.length - 1, index))]));
  }
}
