package dev.edgewave;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.*;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.*;
import com.google.android.material.textfield.*;
import com.google.android.material.textview.MaterialTextView;

public class MainActivity extends AppCompatActivity {
  static final int NAV_HOME = 1001, NAV_WAVE = 1002, NAV_KEYS = 1003, NAV_EFFECTS = 1004;
  SharedPreferences prefs;
  LinearLayout content, rootPanel;
  TextView state, keyStatus, bpmStatus, renderStatus, trackStatus;
  MaterialToolbar toolbar;
  BottomNavigationView navigation;
  NestedScrollView scroll;
  MaterialSwitch power;
  boolean notificationAsked, syncingPower, barVisible;
  int page = NAV_HOME;
  final Handler handler = new Handler(Looper.getMainLooper());
  final Runnable refresh =
      new Runnable() {
        public void run() {
          refreshState();
          handler.postDelayed(this, 350);
        }
      };

  int dp(float value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  int color(int attribute) {
    return MaterialColors.getColor(this, attribute, Color.BLACK);
  }

  @Override
  public void onCreate(Bundle stateBundle) {
    super.onCreate(stateBundle);
    prefs = getSharedPreferences("wave", 0);
    AppDefaults.apply(prefs);
    if (!prefs.contains("dent3"))
      prefs.edit().putInt("dent3", Math.max(1, prefs.getInt("dent", 24) / 3)).apply();
    getWindow().setStatusBarContrastEnforced(false);
    getWindow().setNavigationBarContrastEnforced(false);
    rootPanel =
        new LinearLayout(this) {
          final android.graphics.Paint paint = new android.graphics.Paint();

          protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            if (barVisible) {
              paint.setColor(Color.BLACK);
              canvas.drawRect(0, 0, getWidth(), getPaddingTop(), paint);
            }
          }
        };
    rootPanel.setOrientation(LinearLayout.VERTICAL);
    rootPanel.setWillNotDraw(false);
    rootPanel.setBackgroundColor(color(com.google.android.material.R.attr.colorSurface));
    setContentView(rootPanel);
    rootPanel.setOnApplyWindowInsetsListener(
        (view, insets) -> {
          view.setPadding(
              insets.getSystemWindowInsetLeft(),
              insets.getSystemWindowInsetTop(),
              insets.getSystemWindowInsetRight(),
              insets.getSystemWindowInsetBottom());
          return insets.consumeSystemWindowInsets();
        });
    toolbar = new MaterialToolbar(this);
    toolbar.setTitle(getString(R.string.app_name));
    toolbar.setSubtitle(null);
    toolbar.getMenu().add(getString(R.string.theme)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
    toolbar
        .getMenu()
        .add(getString(R.string.permissions))
        .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
    toolbar.getMenu().add("GitHub").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
    toolbar
        .getMenu()
        .add(getString(R.string.language))
        .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
    toolbar.getMenu().add(getString(R.string.about)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
    toolbar.setOnMenuItemClickListener(
        item -> {
          if ("GitHub".contentEquals(item.getTitle())) openGitHub();
          else if (getString(R.string.language).contentEquals(item.getTitle())) languageDialog();
          else if (getString(R.string.theme).contentEquals(item.getTitle())) themeDialog();
          else if (getString(R.string.permissions).contentEquals(item.getTitle()))
            permissionsDialog();
          else aboutDialog();
          return true;
        });
    rootPanel.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(48)));
    scroll = new NestedScrollView(this);
    scroll.setFillViewport(true);
    scroll.setClipToPadding(false);
    rootPanel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    content = column();
    content.setPadding(dp(12), dp(2), dp(12), dp(16));
    scroll.addView(content);
    navigation = new BottomNavigationView(this);
    navigation.setLabelVisibilityMode(
        com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_LABELED);
    navigation.setItemHorizontalTranslationEnabled(false);
    String[] names = {
      getString(R.string.home),
      getString(R.string.wave),
      getString(R.string.buttons),
      getString(R.string.effects)
    };
    int[] ids = {NAV_HOME, NAV_WAVE, NAV_KEYS, NAV_EFFECTS};
    int[] icons = {
      R.drawable.nav_home, R.drawable.nav_wave, R.drawable.nav_keys, R.drawable.nav_effects
    };
    for (int i = 0; i < 4; i++)
      navigation.getMenu().add(Menu.NONE, ids[i], i, names[i]).setIcon(icons[i]);
    navigation.setOnItemSelectedListener(
        item -> {
          showPage(item.getItemId());
          return true;
        });
    rootPanel.addView(navigation, new LinearLayout.LayoutParams(-1, dp(72)));
    updateSystemBars();
    page = stateBundle == null ? NAV_HOME : stateBundle.getInt("page", NAV_HOME);
    navigation.setSelectedItemId(page);
    if (content.getChildCount() == 0) showPage(page);
  }

  LinearLayout column() {
    LinearLayout view = new LinearLayout(this);
    view.setOrientation(LinearLayout.VERTICAL);
    return view;
  }

  TextView text(LinearLayout host, String value, int appearance) {
    MaterialTextView t = new MaterialTextView(this);
    t.setTextAppearance(appearance);
    t.setText(value);
    t.setTextColor(color(com.google.android.material.R.attr.colorOnSurface));
    t.setPadding(0, dp(3), 0, dp(6));
    host.addView(t);
    return t;
  }

  TextView body(LinearLayout host, String value) {
    TextView t =
        text(host, value, com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
    t.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant));
    return t;
  }

  TextView addText(LinearLayout host, String value, int size, boolean bold, int unused) {
    return bold
        ? text(
            host, value, com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
        : body(host, value);
  }

  MaterialCardView card() {
    return new MaterialCardView(this);
  }

  LinearLayout section(String title, String subtitle) {
    MaterialCardView card = card();
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
    params.setMargins(0, dp(5), 0, dp(5));
    content.addView(card, params);
    LinearLayout inside = column();
    inside.setPadding(dp(14), dp(12), dp(14), dp(12));
    card.addView(inside);
    text(inside, title, com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
    if (subtitle != null && !subtitle.isEmpty()) body(inside, subtitle);
    return inside;
  }

  MaterialButton button(String label, View.OnClickListener listener, boolean secondary) {
    MaterialButton button =
        new MaterialButton(
            this,
            null,
            secondary
                ? com.google.android.material.R.attr.materialButtonTonalStyle
                : com.google.android.material.R.attr.materialButtonStyle);
    button.setText(label);
    button.setOnClickListener(listener);
    button.setMaxLines(2);
    return button;
  }

  void action(
      LinearLayout host, String label, int icon, boolean secondary, View.OnClickListener listener) {
    MaterialButton b = button(label, listener, secondary);
    if (icon != 0) b.setIconResource(icon);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(48));
    p.setMargins(0, dp(4), 0, dp(4));
    host.addView(b, p);
  }

  MaterialButton textButton(String label, View.OnClickListener listener) {
    MaterialButton b =
        new MaterialButton(this, null, androidx.appcompat.R.attr.borderlessButtonStyle);
    b.setText(label);
    b.setOnClickListener(listener);
    return b;
  }

  void showPage(int next) {
    page = next;
    content.removeAllViews();
    keyStatus = bpmStatus = renderStatus = trackStatus = state = null;
    power = null;
    switch (next) {
      case NAV_WAVE:
        toolbar.setTitle(getString(R.string.wave));
        toolbar.setSubtitle(null);
        geometry();
        break;
      case NAV_KEYS:
        toolbar.setTitle(getString(R.string.buttons));
        toolbar.setSubtitle(null);
        keys();
        break;
      case NAV_EFFECTS:
        toolbar.setTitle(getString(R.string.effects));
        toolbar.setSubtitle(null);
        effectsSettings();
        break;
      default:
        toolbar.setTitle(getString(R.string.app_name));
        toolbar.setSubtitle(null);
        home();
    }
    scroll.scrollTo(0, 0);
    refreshState();
  }

  void home() {
    LinearLayout hero = section(getString(R.string.visualization), null);
    LinearLayout row = new LinearLayout(this);
    row.setGravity(Gravity.CENTER_VERTICAL);
    power = new MaterialSwitch(this);
    power.setText(getString(R.string.enable_visualization));
    power.setMinHeight(dp(48));
    power.setChecked(WaveService.active && WaveService.requested);
    power.setOnCheckedChangeListener(
        (v, on) -> {
          if (syncingPower) return;
          if (on) start();
          else stop();
        });
    row.addView(power, new LinearLayout.LayoutParams(0, -2, 1));
    hero.addView(row);
    LinearLayout tests = new LinearLayout(this);
    MaterialButton
        white =
            button(
                getString(R.string.white_screen),
                v -> startActivity(new Intent(this, WhiteStageActivity.class)),
                false),
        test =
            button(
                getString(R.string.test_effects),
                v -> startActivity(new Intent(this, EffectsPreviewActivity.class)),
                true);
    LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(0, dp(48), 1);
    first.setMargins(0, dp(4), dp(4), 0);
    tests.addView(white, first);
    LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, dp(48), 1);
    second.setMargins(dp(4), dp(4), 0, 0);
    tests.addView(test, second);
    hero.addView(tests);
    LinearLayout access = section(getString(R.string.permissions), null);
    action(
        access,
        Settings.canDrawOverlays(this)
            ? getString(R.string.overlay_allowed)
            : getString(R.string.overlay_permission),
        0,
        true,
        v -> openOverlay());
    action(
        access,
        VolumeKeysService.connected
            ? getString(R.string.keys_service_on)
            : getString(R.string.keys_black),
        0,
        true,
        v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
    action(access, getString(R.string.background), 0, true, v -> batterySettings());
    LinearLayout appearance = section(getString(R.string.appearance), null);
    choice(
        appearance,
        getString(R.string.theme),
        "themeMode",
        new String[] {
          getString(R.string.system_default), getString(R.string.light), getString(R.string.dark)
        });
    languageRow(appearance);
  }

  void geometry() {
    LinearLayout response = section(getString(R.string.movement), null);
    responseMode(response, "classic".equals(prefs.getString("responseMode", "live")));
    boolean classic = "classic".equals(prefs.getString("responseMode", "live")),
        hybrid = "hybrid".equals(prefs.getString("responseMode", "live"));
    slider(
        response,
        getString(R.string.sensitivity),
        hybrid ? "hybridSensitivity" : classic ? "classicSensitivity" : "sensitivity",
        50,
        300,
        classic ? 100 : 160,
        " %");
    slider(
        response,
        getString(R.string.motion_speed),
        hybrid ? "hybridSpeed" : classic ? "classicSpeed" : "waveSpeed",
        50,
        220,
        130,
        " %");
    LinearLayout form =
        section(getString(R.string.size_position), getString(R.string.muted_hidden));
    slider(form, getString(R.string.camera_offset), "offset2", -16, 48, 0, " dp");
    slider(form, getString(R.string.wave_height), "depth2", 4, 64, 24, " dp");
    form.addView(
        textButton(
            getString(R.string.reset_wave_settings),
            v ->
                new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.reset_wave_title))
                    .setMessage(getString(R.string.reset_wave_message))
                    .setNegativeButton(getString(R.string.cancel), null)
                    .setPositiveButton(
                        getString(R.string.reset),
                        (dialog, which) -> {
                          prefs
                              .edit()
                              .remove("offset2")
                              .remove("depth2")
                              .remove("sensitivity")
                              .remove("waveSpeed")
                              .remove("classicSensitivity")
                              .remove("classicSpeed")
                              .remove("hybridSensitivity")
                              .remove("hybridSpeed")
                              .apply();
                          showPage(NAV_WAVE);
                        })
                    .show()));
  }

  void responseMode(LinearLayout host, boolean unused) {
    String[]
        labels =
            {
              getString(R.string.classic),
              getString(R.string.live),
              getString(R.string.classic_live)
            },
        keys = {"classic", "live", "hybrid"};
    String mode = prefs.getString("responseMode", "live");
    int selected = "classic".equals(mode) ? 0 : "hybrid".equals(mode) ? 2 : 1;
    selector(
        host,
        getString(R.string.wave_character),
        "responseMode",
        labels,
        selected,
        index -> {
          prefs.edit().putString("responseMode", keys[index]).apply();
          showPage(NAV_WAVE);
        });
  }

  void keys() {
    LinearLayout setup =
        section(getString(R.string.volume_buttons), getString(R.string.hold_description));
    keyStatus = body(setup, "");
    action(
        setup,
        getString(R.string.setup_keys),
        0,
        true,
        v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
    toggle(setup, getString(R.string.button_effect), "volume", false);
    selector(
        setup,
        getString(R.string.button_side),
        "buttonSide",
        new String[] {getString(R.string.left_side), getString(R.string.right_side)},
        prefs.getBoolean("right", true) ? 1 : 0,
        index -> prefs.edit().putBoolean("right", index == 1).apply());
    LinearLayout position = section(getString(R.string.position_shape), null);
    slider(position, getString(R.string.volume_up_position), "up", 5, 80, 24, " %");
    slider(position, getString(R.string.volume_down_position), "down", 5, 80, 32, " %");
    slider(position, getString(R.string.dent_length), "length", 30, 160, 76, " dp");
    slider(position, getString(R.string.dent_depth), "dent3", 1, 20, 8, " dp");
    position.addView(
        textButton(
            getString(R.string.accessibility_about),
            v ->
                new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.keys_only))
                    .setMessage(getString(R.string.keys_permission_message))
                    .setPositiveButton(getString(R.string.got_it), null)
                    .show()));
  }

  void effectsSettings() {
    LinearLayout selection = section(getString(R.string.effect_selection), null);
    choice(
        selection,
        getString(R.string.mode),
        "effectMode",
        new String[] {getString(R.string.beat_mode), getString(R.string.pattern_mode)});
    toggle(selection, getString(R.string.combinations), "fx_combos", false);
    LinearLayout animation = section(getString(R.string.transitions), null);
    String[] modes = {
      getString(R.string.edge_pull),
      getString(R.string.compression),
      getString(R.string.from_center),
      getString(R.string.left_to_right),
      getString(R.string.liquid_pull)
    };
    choice(animation, getString(R.string.appear), "enterEffect", modes);
    choice(animation, getString(R.string.disappear), "exitEffect", modes);
    slider(
        animation,
        getString(R.string.duration),
        "transitionMs",
        200,
        1500,
        450,
        getString(R.string.unit_ms));
    LinearLayout accents =
        section(getString(R.string.visual_effects), getString(R.string.effect_help));
    for (int i = 0; i < EffectsEngine.COUNT; i++) {
      final int kind = i;
      LinearLayout row = new LinearLayout(this);
      row.setGravity(Gravity.CENTER_VERTICAL);
      MaterialSwitch enabled = new MaterialSwitch(this);
      enabled.setText(effectName(i));
      enabled.setTextAppearance(
          com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
      enabled.setMinHeight(dp(48));
      enabled.setChecked(prefs.getBoolean(EffectsEngine.KEYS[i], false));
      enabled.setOnCheckedChangeListener(
          (v, on) -> prefs.edit().putBoolean(EffectsEngine.KEYS[kind], on).apply());
      row.addView(enabled, new LinearLayout.LayoutParams(0, -2, 1));
      MaterialButton more = textButton("⋯", v -> effectDialog(kind));
      more.setContentDescription(getString(R.string.settings_prefix) + effectName(i));
      more.setMinWidth(0);
      more.setMinimumWidth(0);
      more.setPadding(0, 0, 0, 0);
      row.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48)));
      accents.addView(row);
    }
    LinearLayout tests = section(getString(R.string.checking), null);
    action(
        tests,
        getString(R.string.white_screen),
        R.drawable.ic_white,
        false,
        v -> startActivity(new Intent(this, WhiteStageActivity.class)));
    action(
        tests,
        getString(R.string.test_animations),
        R.drawable.ic_play,
        true,
        v -> startActivity(new Intent(this, EffectsPreviewActivity.class)));
    LinearLayout bounds = section(getString(R.string.bottom_boundary), null);
    choice(
        bounds,
        getString(R.string.placement),
        "bottomMode",
        new String[] {getString(R.string.above_navigation), getString(R.string.physical_edge)});
    slider(bounds, getString(R.string.inset), "bottomOffset", 0, 48, 0, " dp");
    LinearLayout rhythm = section(getString(R.string.rhythm_background), null);
    bpmStatus =
        text(
            rhythm,
            getString(R.string.waiting_music),
            com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
    trackStatus = body(rhythm, "");
    rhythm.addView(
        textButton(
            getString(R.string.track_bpm_memory),
            v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))));
    rhythm.addView(
        textButton(
            getString(R.string.recalculate_bpm),
            v -> {
              if (WaveService.resetTempo != null) WaveService.resetTempo.run();
            }));
    renderStatus = body(rhythm, "");
    rhythm.addView(
        textButton(
            getString(R.string.keys_solid_setup),
            v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
  }

  void effectDialog(int kind) {
    String[] details = {
      getString(R.string.fx_desc_0),
      getString(R.string.fx_desc_1),
      getString(R.string.fx_desc_2),
      getString(R.string.fx_desc_3),
      getString(R.string.fx_desc_4),
      getString(R.string.fx_desc_5),
      getString(R.string.fx_desc_6),
      getString(R.string.fx_desc_7),
      getString(R.string.fx_desc_8),
      getString(R.string.fx_desc_9)
    };
    LinearLayout panel = column();
    panel.setPadding(dp(24), 0, dp(24), 0);
    body(panel, details[kind]);
    if (kind == 9)
      choice(
          panel,
          getString(R.string.side_heights),
          "duetHeights",
          new String[] {
            getString(R.string.left_always_higher), getString(R.string.alternate_sides)
          });
    new MaterialAlertDialogBuilder(this)
        .setTitle(effectName(kind))
        .setView(panel)
        .setPositiveButton(
            getString(R.string.test),
            (d, w) ->
                startActivity(
                    new Intent(this, EffectsPreviewActivity.class).putExtra("effect", kind)))
        .setNegativeButton(getString(R.string.close), null)
        .show();
  }

  interface Selection {
    void picked(int index);
  }

  MaterialButton selector(
      LinearLayout host,
      String label,
      String key,
      String[] values,
      int selected,
      Selection listener) {
    MaterialButton row =
        new MaterialButton(
            this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
    row.setTag(key);
    row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    row.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
    row.setCornerRadius(dp(12));
    row.setStrokeWidth(dp(1));
    row.setIconResource(R.drawable.ic_expand);
    row.setIconGravity(MaterialButton.ICON_GRAVITY_END);
    row.setIconSize(dp(18));
    row.setMaxLines(2);
    row.setMinHeight(dp(56));
    row.setInsetTop(0);
    row.setInsetBottom(0);
    setSelectionText(row, label, values[selected]);
    row.setOnClickListener(
        v ->
            new MaterialAlertDialogBuilder(this)
                .setTitle(label)
                .setSingleChoiceItems(
                    values,
                    selectedIndex(key, values, selected),
                    (dialog, index) -> {
                      setSelectionText(row, label, values[index]);
                      dialog.dismiss();
                      listener.picked(index);
                    })
                .setNegativeButton(getString(R.string.cancel), null)
                .show());
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(0, dp(3), 0, dp(3));
    host.addView(row, lp);
    return row;
  }

  int selectedIndex(String key, String[] values, int fallback) {
    if ("language".equals(key)) return AppLanguage.index();
    if ("buttonSide".equals(key)) return prefs.getBoolean("right", true) ? 1 : 0;
    if ("responseMode".equals(key)) {
      String mode = prefs.getString(key, "live");
      return "classic".equals(mode) ? 0 : "hybrid".equals(mode) ? 2 : 1;
    }
    return Math.max(0, Math.min(values.length - 1, prefs.getInt(key, fallback)));
  }

  void setSelectionText(MaterialButton row, String label, String value) {
    android.text.SpannableString text = new android.text.SpannableString(label + "\n" + value);
    text.setSpan(new android.text.style.RelativeSizeSpan(.78f), 0, label.length(), 0);
    text.setSpan(
        new android.text.style.ForegroundColorSpan(
            color(com.google.android.material.R.attr.colorOnSurfaceVariant)),
        0,
        label.length(),
        0);
    row.setTextColor(color(com.google.android.material.R.attr.colorOnSurface));
    row.setText(text);
    row.setContentDescription(label + ": " + value);
  }

  void choice(LinearLayout host, String label, String key, String[] values) {
    int fallback =
        "themeMode".equals(key)
            ? 0
            : "bottomMode".equals(key) || "effectMode".equals(key) || "duetHeights".equals(key)
                ? 1
                : 2;
    int selected = selectedIndex(key, values, fallback);
    selector(
        host,
        label,
        key,
        values,
        selected,
        index -> {
          prefs.edit().putInt(key, index).apply();
          if ("themeMode".equals(key)) AppAppearance.apply(this);
        });
  }

  String effectName(int kind) {
    return getResources().getStringArray(R.array.effect_names)[kind];
  }

  void openGitHub() {
    startActivity(
        new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/FASTDROPX/Edge-Extended")));
  }

  String[] languageNames() {
    return new String[] {
      getString(R.string.system_default),
      getString(R.string.russian_native),
      "English",
      "简体中文",
      "Français",
      "Deutsch"
    };
  }

  void languageRow(LinearLayout host) {
    selector(
        host,
        getString(R.string.language),
        "language",
        languageNames(),
        AppLanguage.index(),
        AppLanguage::select);
  }

  void languageDialog() {
    new MaterialAlertDialogBuilder(this)
        .setTitle(getString(R.string.language))
        .setSingleChoiceItems(
            languageNames(),
            AppLanguage.index(),
            (dialog, index) -> {
              dialog.dismiss();
              AppLanguage.select(index);
            })
        .setNegativeButton(getString(R.string.cancel), null)
        .show();
  }

  void themeDialog() {
    String[] values = {
      getString(R.string.system_default), getString(R.string.light), getString(R.string.dark)
    };
    new MaterialAlertDialogBuilder(this)
        .setTitle(getString(R.string.theme))
        .setSingleChoiceItems(
            values,
            prefs.getInt("themeMode", 0),
            (dialog, index) -> {
              prefs.edit().putInt("themeMode", index).apply();
              dialog.dismiss();
              AppAppearance.apply(this);
            })
        .setNegativeButton(getString(R.string.cancel), null)
        .show();
  }

  void updateSystemBars() {
    boolean dark = AppAppearance.dark(this);
    getWindow()
        .setStatusBarColor(
            barVisible ? Color.BLACK : color(com.google.android.material.R.attr.colorSurface));
    getWindow().setNavigationBarColor(color(com.google.android.material.R.attr.colorSurface));
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            (dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
                | (dark || barVisible ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR));
  }

  void toggle(LinearLayout host, String title, String key, boolean fallback) {
    MaterialSwitch s = new MaterialSwitch(this);
    s.setText(title);
    s.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
    s.setMinHeight(dp(48));
    s.setChecked(prefs.getBoolean(key, fallback));
    s.setOnCheckedChangeListener((view, value) -> prefs.edit().putBoolean(key, value).apply());
    host.addView(s, new LinearLayout.LayoutParams(-1, -2));
  }

  void slider(
      LinearLayout host, String title, String key, int min, int max, int fallback, String unit) {
    LinearLayout row = new LinearLayout(this);
    row.setGravity(Gravity.CENTER_VERTICAL);
    MaterialTextView label = new MaterialTextView(this), value = new MaterialTextView(this);
    label.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
    value.setTextAppearance(
        com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
    label.setText(title);
    value.setText(prefs.getInt(key, fallback) + unit);
    value.setTextColor(color(androidx.appcompat.R.attr.colorPrimary));
    row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
    row.addView(value);
    LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
    rp.setMargins(0, dp(8), 0, 0);
    host.addView(row, rp);
    Slider slider = new Slider(this);
    slider.setContentDescription(title);
    slider.setValueFrom(min);
    slider.setValueTo(max);
    slider.setStepSize(1);
    slider.setTickVisible(false);
    slider.setTrackStopIndicatorSize(0);
    slider.setTrackHeight(dp(4));
    slider.setThumbRadius(dp(8));
    slider.setValue(prefs.getInt(key, fallback));
    slider.setLabelFormatter(n -> Math.round(n) + unit);
    slider.addOnChangeListener(
        (view, n, user) -> {
          prefs.edit().putInt(key, Math.round(n)).apply();
          value.setText(Math.round(n) + unit);
        });
    host.addView(slider, new LinearLayout.LayoutParams(-1, dp(48)));
  }

  void refreshState() {
    if (power != null) {
      syncingPower = true;
      power.setChecked(WaveService.active && WaveService.requested);
      syncingPower = false;
    }
    if (keyStatus != null)
      keyStatus.setText(
          VolumeKeysService.connected
              ? getString(R.string.keys_service_status)
              : getString(R.string.keys_service_request));
    if (bpmStatus != null)
      bpmStatus.setText(
          WaveService.bpm > 0
              ? "≈ "
                  + Math.round(WaveService.bpm)
                  + " BPM · "
                  + (WaveService.bpmLocked
                      ? getString(R.string.bpm_saved)
                      : getString(R.string.bpm_refining))
              : getString(R.string.play_to_detect));
    if (trackStatus != null)
      trackStatus.setText(
          TrackAccessService.connected
              ? getString(R.string.player_prefix) + TrackAccessService.trackTitle
              : getString(R.string.session_bpm));
    if (renderStatus != null)
      renderStatus.setText(
          WaveService.active
              ? (WaveService.rendererTrusted
                      ? getString(R.string.trusted_layer)
                      : getString(R.string.fallback_layer))
                  + " · ≈ "
                  + Math.round(WaveService.fps)
                  + " FPS"
              : getString(R.string.stats_after_start));
    boolean visible = WaveService.active && WaveService.waveVisible;
    if (visible != barVisible) {
      barVisible = visible;
      rootPanel.invalidate();
      updateSystemBars();
    }
  }

  void stop() {
    startService(new Intent(this, WaveService.class).setAction("stop"));
  }

  void openOverlay() {
    startActivity(
        new Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
  }

  void batterySettings() {
    try {
      startActivity(
          new Intent(
              Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
              Uri.parse("package:" + getPackageName())));
    } catch (Exception missing) {
      startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
    }
  }

  void permissionsDialog() {
    String[] options = {
      getString(R.string.overlay_short),
      getString(R.string.volume_buttons),
      getString(R.string.track_bpm_memory),
      getString(R.string.background)
    };
    new MaterialAlertDialogBuilder(this)
        .setTitle(getString(R.string.permissions))
        .setItems(
            options,
            (dialog, index) -> {
              if (index == 0) openOverlay();
              else if (index == 1)
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
              else if (index == 2)
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
              else batterySettings();
            })
        .setNegativeButton(getString(R.string.close), null)
        .show();
  }

  void aboutDialog() {
    new MaterialAlertDialogBuilder(this)
        .setTitle(getString(R.string.app_name))
        .setMessage(getString(R.string.about_message))
        .setPositiveButton(getString(R.string.got_it), null)
        .show();
  }

  void start() {
    if (!Settings.canDrawOverlays(this)) {
      new MaterialAlertDialogBuilder(this)
          .setTitle(getString(R.string.allow_overlay_title))
          .setMessage(getString(R.string.allow_overlay_message))
          .setNegativeButton(getString(R.string.later), null)
          .setPositiveButton(getString(R.string.open_settings), (dialog, which) -> openOverlay())
          .show();
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
    startForegroundService(new Intent(this, WaveService.class));
  }

  @Override
  public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(code, permissions, results);
    if (results.length > 0 && (results[0] == PackageManager.PERMISSION_GRANTED || code == 5))
      start();
  }

  @Override
  protected void onResume() {
    super.onResume();
    handler.post(refresh);
  }

  @Override
  protected void onPause() {
    handler.removeCallbacks(refresh);
    super.onPause();
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    state.putInt("page", page);
    super.onSaveInstanceState(state);
  }
}
