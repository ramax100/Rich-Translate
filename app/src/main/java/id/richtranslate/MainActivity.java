package id.richtranslate;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.translate.TranslateRemoteModel;
import java.util.*;

public class MainActivity extends Activity {
  private LinearLayout page, errorCard, homePanel, settingsPanel;
  private TextView connection, accessStatus, downloadStatus, errorText;
  private Button homeTab, settingsTab, accessButton;
  private ScrollView scroll;
  private int selectedTab;
  private Button startButton, stopButton, modelButton;
  private TranslationEngine downloadEngine;
  private boolean downloading, modelsReady, registered;
  private int modelQuery;
  private final List<Spinner> languagePickers = new ArrayList<>();
  private final BroadcastReceiver stateChanged =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
          refresh();
        }
      };

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    Prefs.migrateOfflineSettings(this);
    selectedTab = state == null ? 0 : state.getInt("selected_tab", 0);
    build();
  }

  private void gap(LinearLayout parent, int dp) {
    parent.addView(new Space(this), new LinearLayout.LayoutParams(1, Ui.dp(this, dp)));
  }

  private LinearLayout card(LinearLayout parent) {
    LinearLayout box = Ui.column(this);
    box.setPadding(Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18));
    box.setBackground(Ui.shape(Color.WHITE, 22, this));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.bottomMargin = Ui.dp(this, 16);
    parent.addView(box, p);
    return box;
  }

  private void divider(LinearLayout parent) {
    View line = new View(this);
    line.setBackgroundColor(Ui.LINE);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, Ui.dp(this, 1));
    p.topMargin = Ui.dp(this, 14);
    p.bottomMargin = Ui.dp(this, 14);
    parent.addView(line, p);
  }

  private Button secondary(String label) {
    Button b = Ui.button(this, label);
    b.setBackground(Ui.shape(Ui.TINT, 14, this));
    b.setTextColor(Ui.ACCENT);
    return b;
  }

  private void addButton(LinearLayout parent, Button button, int height) {
    button.setMinHeight(Ui.dp(this, height));
    button.setMinimumHeight(Ui.dp(this, height));
    button.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
    parent.addView(button, new LinearLayout.LayoutParams(-1, -2));
  }

  private void build() {
    scroll = new ScrollView(this);
    scroll.setBackgroundColor(Ui.BG);
    scroll.setFillViewport(true);
    LinearLayout outer = Ui.column(this);
    outer.setGravity(Gravity.CENTER_HORIZONTAL);
    scroll.addView(outer);
    page = Ui.column(this);
    int screenDp =
        Math.round(
            getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density);
    outer.addView(page, new LinearLayout.LayoutParams(Ui.dp(this, Math.min(560, screenDp)), -2));
    page.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 24));
    setContentView(scroll);

    LinearLayout header = new LinearLayout(this);
    header.setGravity(Gravity.CENTER_VERTICAL);
    ImageView logo = new ImageView(this);
    logo.setImageResource(R.drawable.brand_symbol);
    logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
    logo.setContentDescription("Logo Rich Translate");
    header.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
    TextView title = Ui.bold(this, "Rich Translate", 21, Ui.INK);
    LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1);
    titleParams.leftMargin = Ui.dp(this, 10);
    titleParams.rightMargin = Ui.dp(this, 8);
    header.addView(title, titleParams);
    TextView help = Ui.bold(this, "?", 18, Ui.ACCENT);
    help.setGravity(Gravity.CENTER);
    help.setBackground(Ui.shape(Ui.TINT, 24, this));
    help.setContentDescription("Bantuan dan privasi");
    help.setTag("help_button");
    help.setOnClickListener(v -> showHelp());
    header.addView(help, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
    page.addView(header);
    gap(page, 20);

    LinearLayout tabs = new LinearLayout(this);
    tabs.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
    tabs.setBackground(Ui.shape(Ui.TINT, 18, this));
    homeTab = secondary("Utama");
    settingsTab = secondary("Setelan");
    homeTab.setTag("home_tab");
    settingsTab.setTag("settings_tab");
    for (Button tab : new Button[] {homeTab, settingsTab}) {
      tab.setMinHeight(Ui.dp(this, 44));
      tab.setMinimumHeight(Ui.dp(this, 44));
      tabs.addView(tab, new LinearLayout.LayoutParams(0, -2, 1));
    }
    page.addView(tabs);
    gap(page, 20);
    homePanel = Ui.column(this);
    homePanel.setTag("home_panel");
    settingsPanel = Ui.column(this);
    settingsPanel.setTag("settings_panel");
    page.addView(homePanel);
    page.addView(settingsPanel);
    homeTab.setOnClickListener(v -> selectTab(0));
    settingsTab.setOnClickListener(v -> selectTab(1));

    connection = Ui.bold(this, "Layanan belum aktif", 12, Ui.MUTED);
    connection.setTag("service_status");
    homePanel.addView(connection);
    gap(homePanel, 12);
    LinearLayout languages = card(homePanel);
    languages.addView(Ui.bold(this, "Bahasa", 17, Ui.INK));
    gap(languages, 14);
    LinearLayout pair = new LinearLayout(this);
    boolean stacked = screenDp < 360 || getResources().getConfiguration().fontScale > 1.3f;
    pair.setTag("language_pair");
    pair.setOrientation(stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
    pair.setGravity(Gravity.CENTER_VERTICAL);
    LinearLayout from = Ui.column(this), to = Ui.column(this);
    pair.addView(
        from,
        stacked ? new LinearLayout.LayoutParams(-1, -2) : new LinearLayout.LayoutParams(0, -2, 1));
    if (stacked) {
      gap(pair, 10);
    } else {
      TextView arrow = Ui.text(this, "→", 20, Ui.ACCENT);
      arrow.setGravity(Gravity.CENTER);
      pair.addView(arrow, new LinearLayout.LayoutParams(Ui.dp(this, 28), -2));
    }
    pair.addView(
        to,
        stacked ? new LinearLayout.LayoutParams(-1, -2) : new LinearLayout.LayoutParams(0, -2, 1));
    languages.addView(pair);
    language(from, "Dari", "source", 0);
    language(to, "Ke", "target", 1);
    gap(languages, 10);
    downloadStatus = Ui.text(this, "Memeriksa model…", 12, Ui.MUTED);
    downloadStatus.setTag("model_status");
    languages.addView(downloadStatus);
    gap(languages, 12);
    modelButton = secondary("Unduh model");
    modelButton.setTag("download_button");
    addButton(languages, modelButton, 48);
    modelButton.setOnClickListener(v -> downloadModels());

    LinearLayout access = card(homePanel);
    access.addView(Ui.bold(this, "Akses layar", 17, Ui.INK));
    gap(access, 6);
    accessStatus = Ui.text(this, "Belum aktif", 12, Ui.MUTED);
    accessStatus.setTag("access_status");
    access.addView(accessStatus);
    gap(access, 12);
    accessButton = secondary("Aktifkan Accessibility");
    accessButton.setTag("access_button");
    addButton(access, accessButton, 48);
    accessButton.setOnClickListener(v -> openAccessibility());
    startButton = Ui.button(this, "Mulai");
    startButton.setTag("start_button");
    addButton(homePanel, startButton, 54);
    startButton.setOnClickListener(v -> start());
    stopButton = Ui.button(this, "Hentikan");
    stopButton.setTag("stop_button");
    addButton(homePanel, stopButton, 54);
    stopButton.setOnClickListener(
        v -> {
          RichAccessibilityService service = RichAccessibilityService.connectedService();
          if (service != null) service.stopSession(null);
          refresh();
        });
    gap(homePanel, 12);
    errorCard = card(homePanel);
    errorCard.setTag("error_card");
    errorCard.addView(Ui.bold(this, "Perlu diperiksa", 14, Ui.INK));
    gap(errorCard, 8);
    errorText = Ui.text(this, "", 12, Ui.MUTED);
    errorCard.addView(errorText);

    LinearLayout preferences = card(settingsPanel);
    preferences.addView(Ui.bold(this, "Preferensi", 17, Ui.INK));
    gap(preferences, 18);
    toggle(preferences, "Unduh via Wi-Fi", null, "wifi", true, false);
    divider(preferences);
    toggle(preferences, "Mode streaming", "Dapat membuat rekaman hitam.", "secure", false, true);
    Button about = secondary("Bantuan & privasi");
    about.setTag("about_button");
    addButton(settingsPanel, about, 48);
    about.setOnClickListener(v -> showHelp());
    gap(settingsPanel, 16);
    TextView version = Ui.text(this, "Versi 0.10.0 · Android 11+", 11, Ui.MUTED);
    version.setGravity(Gravity.CENTER);
    settingsPanel.addView(version);
    selectTab(selectedTab);
    refresh();
  }

  private void selectTab(int selected) {
    selectedTab = selected == 1 ? 1 : 0;
    boolean home = selectedTab == 0;
    homePanel.setVisibility(home ? View.VISIBLE : View.GONE);
    settingsPanel.setVisibility(home ? View.GONE : View.VISIBLE);
    homeTab.setBackground(Ui.shape(home ? Color.WHITE : Color.TRANSPARENT, 14, this));
    settingsTab.setBackground(Ui.shape(home ? Color.TRANSPARENT : Color.WHITE, 14, this));
    homeTab.setSelected(home);
    settingsTab.setSelected(!home);
    scroll.scrollTo(0, 0);
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    out.putInt("selected_tab", selectedTab);
    super.onSaveInstanceState(out);
  }

  private void language(LinearLayout box, String caption, String key, int fallback) {
    box.addView(Ui.text(this, caption, 12, Ui.MUTED));
    gap(box, 4);
    Spinner spinner = new Spinner(this);
    spinner.setTag(key);
    languagePickers.add(spinner);
    ArrayAdapter<String> adapter =
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, Prefs.NAMES);
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    spinner.setAdapter(adapter);
    String current = Prefs.get(this).getString(key, Prefs.CODES[fallback]);
    for (int i = 0; i < Prefs.CODES.length; i++)
      if (current.equals(Prefs.CODES[i])) spinner.setSelection(i);
    spinner.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onNothingSelected(AdapterView<?> p) {}

          public void onItemSelected(AdapterView<?> p, View v, int position, long id) {
            String next = Prefs.CODES[position];
            String old = Prefs.get(MainActivity.this).getString(key, Prefs.CODES[fallback]);
            if (!next.equals(old)) {
              Prefs.get(MainActivity.this).edit().putString(key, next).apply();
              RichAccessibilityService service = RichAccessibilityService.connectedService();
              if (service != null && service.isActive()) service.stopSession(null);
              modelsReady = false;
              checkModels();
              refresh();
            }
          }
        });
    spinner.setMinimumHeight(Ui.dp(this, 48));
    box.addView(spinner, new LinearLayout.LayoutParams(-1, -2));
  }

  private void toggle(
      LinearLayout box, String title, String subtitle, String key, boolean fallback, boolean warn) {
    LinearLayout row = new LinearLayout(this);
    row.setGravity(Gravity.CENTER_VERTICAL);
    LinearLayout copy = Ui.column(this);
    copy.addView(Ui.bold(this, title, 14, Ui.INK));
    if (subtitle != null) {
      gap(copy, 5);
      copy.addView(Ui.text(this, subtitle, 12, Ui.MUTED));
    }
    row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
    Switch control = new Switch(this);
    control.setTag(key);
    control.setContentDescription(title);
    control.setChecked(Prefs.get(this).getBoolean(key, fallback));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
    p.leftMargin = Ui.dp(this, 12);
    row.addView(control, p);
    box.addView(row);
    control.setOnCheckedChangeListener(
        (button, on) -> {
          Prefs.get(this).edit().putBoolean(key, on).apply();
          if (warn && on)
            new AlertDialog.Builder(this)
                .setTitle("Uji sebelum streaming")
                .setMessage(
                    "FLAG_SECURE dapat membuat hasil rekaman kosong/hitam sepanjang seleksi,"
                        + " pemrosesan, dan hasil layar penuh. Bandingkan dengan Mode streaming OFF"
                        + " untuk mengecek kedip lokal. Accessibility menghindari perebutan sesi"
                        + " MediaProjection, tetapi bukan jaminan tersembunyi dari semua alat"
                        + " rekam. Perubahan mode diterapkan saat Mulai ulang.")
                .setPositiveButton("Mengerti", null)
                .show();
        });
  }

  private void refresh() {
    if (connection == null) return;
    RichAccessibilityService service = RichAccessibilityService.connectedService();
    boolean connected = service != null, active = connected && service.isActive();
    connection.setText(
        active ? "Penerjemah berjalan" : connected ? "Siap menerjemahkan" : "Layanan belum aktif");
    connection.setTextColor(connected ? Ui.ACCENT : Ui.MUTED);
    accessStatus.setText(connected ? "Accessibility aktif" : "Accessibility diperlukan");
    accessStatus.setTextColor(connected ? Ui.ACCENT : Ui.MUTED);
    accessButton.setText(connected ? "Buka setelan akses" : "Aktifkan Accessibility");
    startButton.setVisibility(active ? View.GONE : View.VISIBLE);
    startButton.setEnabled(!downloading);
    stopButton.setVisibility(active ? View.VISIBLE : View.GONE);
    for (Spinner spinner : languagePickers) spinner.setEnabled(!downloading && !active);
    modelButton.setVisibility(modelsReady && !downloading ? View.GONE : View.VISIBLE);
    modelButton.setEnabled(!downloading && !active);
    String last = Prefs.get(this).getString("last_error", "");
    errorCard.setVisibility(last.isEmpty() ? View.GONE : View.VISIBLE);
    errorText.setText(last);
  }

  private void checkModels() {
    if (downloadStatus == null) return;
    final int query = ++modelQuery;
    final String source = Prefs.source(this), target = Prefs.target(this);
    if (source.equals(target)) {
      modelsReady = false;
      downloadStatus.setText("Pilih dua bahasa yang berbeda.");
      refresh();
      return;
    }
    try {
      RemoteModelManager.getInstance()
          .getDownloadedModels(TranslateRemoteModel.class)
          .addOnSuccessListener(
              models -> {
                if (isDestroyed() || query != modelQuery) return;
                Set<String> languages = new HashSet<>();
                for (TranslateRemoteModel model : models) languages.add(model.getLanguage());
                modelsReady =
                    (source.equals("en") || languages.contains(source))
                        && (target.equals("en") || languages.contains(target));
                if (!downloading)
                  downloadStatus.setText(
                      modelsReady ? "Model siap · offline" : "Model belum diunduh.");
                refresh();
              })
          .addOnFailureListener(
              e -> {
                if (isDestroyed() || query != modelQuery) return;
                modelsReady = false;
                downloadStatus.setText("Model belum dapat diperiksa. Coba unduh model.");
                refresh();
              });
    } catch (RuntimeException e) {
      modelsReady = false;
      downloadStatus.setText("Model belum dapat diperiksa. Buka ulang aplikasi bila perlu.");
      refresh();
    }
  }

  private void downloadModels() {
    if (downloading) return;
    if (Prefs.source(this).equals(Prefs.target(this))) {
      downloadStatus.setText("Pilih dua bahasa yang berbeda.");
      return;
    }
    if (downloadEngine != null) downloadEngine.close();
    try {
      downloadEngine = new TranslationEngine(Prefs.source(this), Prefs.target(this));
    } catch (RuntimeException e) {
      downloadStatus.setText("SDK terjemahan belum siap: " + e.getClass().getSimpleName());
      return;
    }
    downloading = true;
    modelButton.setEnabled(false);
    modelButton.setText("Menyiapkan model…");
    downloadStatus.setText("Biarkan halaman terbuka. Jika Wi-Fi saja aktif, sambungkan ke Wi-Fi.");
    refresh();
    downloadEngine.prepare(
        Prefs.get(this).getBoolean("wifi", true),
        new TranslationEngine.Result() {
          public void ok(String message) {
            done(message);
          }

          public void error(String message) {
            done(message);
          }

          private void done(String message) {
            if (isDestroyed()) return;
            downloading = false;
            modelButton.setEnabled(true);
            modelButton.setText("Unduh model");
            downloadStatus.setText(message);
            checkModels();
            refresh();
          }
        });
  }

  private void openAccessibility() {
    new AlertDialog.Builder(this)
        .setTitle("Akses screenshot melalui Accessibility")
        .setMessage(
            "Rich Translate menggunakan kemampuan screenshot layar untuk membaca area yang kamu"
                + " pilih dan menerjemahkannya di perangkat. Gambar dan teks tidak dikirim ke"
                + " server aplikasi. SDK Google dapat memakai jaringan untuk model dan"
                + " diagnostik.\n\n"
                + "Layanan tidak membaca hierarki teks aplikasi, tidak menekan tombol, tidak"
                + " melakukan gestur otomatis, dan tidak bermain untukmu. Screenshot diminta"
                + " setelah kamu selesai melingkari teks.\n\n"
                + "Aktifkan hanya jika kamu menyetujui akses ini. Kamu dapat menghentikan"
                + " penerjemah kapan saja dan mematikan layanan di setelan Accessibility.")
        .setNegativeButton("Batal", null)
        .setPositiveButton(
            "Setuju, buka setelan",
            (dialog, which) -> {
              Prefs.get(this).edit().putBoolean("accessibility_consent", true).apply();
              try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
              } catch (RuntimeException e) {
                Toast.makeText(
                        this,
                        "Buka Setelan > Accessibility > Rich Translate secara manual.",
                        Toast.LENGTH_LONG)
                    .show();
              }
            })
        .show();
  }

  private void start() {
    if (Prefs.source(this).equals(Prefs.target(this))) {
      Toast.makeText(this, "Bahasa sumber dan tujuan harus berbeda.", Toast.LENGTH_SHORT).show();
      return;
    }
    if (!modelsReady) {
      downloadStatus.setText("Unduh model terlebih dahulu.");
      Toast.makeText(this, "Model bahasa belum siap.", Toast.LENGTH_SHORT).show();
      return;
    }
    RichAccessibilityService service = RichAccessibilityService.connectedService();
    if (!Prefs.get(this).getBoolean("accessibility_consent", false) || service == null) {
      openAccessibility();
      return;
    }
    service.startSession();
    refresh();
  }

  private void showHelp() {
    ImageView fullLogo = new ImageView(this);
    fullLogo.setImageResource(R.drawable.brand_full);
    fullLogo.setAdjustViewBounds(true);
    fullLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
    fullLogo.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
    fullLogo.setMaxHeight(Ui.dp(this, 180));
    fullLogo.setContentDescription("Rich Translate");
    new AlertDialog.Builder(this)
        .setView(fullLogo)
        .setTitle("Rich Translate / bantuan & privasi")
        .setMessage(
            "Android 11+ diperlukan. Aplikasi ini memakai screenshot Accessibility, bukan"
                + " MediaProjection atau izin tampil di atas aplikasi lain.\n\n"
                + "Buka Setelan > Accessibility > Rich Translate dan aktifkan secara manual setelah"
                + " membaca penjelasan izin. Pada Android tertentu, instalasi sideload dapat"
                + " dibatasi. Jika kamu mempercayai APK, lihat Info aplikasi dan opsi setelan"
                + " dibatasi yang disediakan Android; aplikasi tidak membuka batasan secara"
                + " otomatis.\n\n"
                + "Cara pakai: ketuk bola, lingkari teks, lalu lepas jari. Geser bola untuk pindah;"
                + " tekan lama untuk berhenti. Geser strip putih untuk hasil panjang. Tekan × untuk"
                + " kembali bermain.\n\n"
                + "Game yang melindungi screenshot tidak didukung. Hasil memakai latar putih"
                + " transparan lembut dan teks hitam, bukan inpainting AI. Ukuran dan posisi"
                + " mengikuti perkiraan OCR. Hanya tombol × muncul bersama hasil.\n\n"
                + "Accessibility membantu menghindari konflik sesi MediaProjection dengan perekam."
                + " Android/penghemat baterai tetap dapat menghentikan layanan; tidak ada jaminan"
                + " anti-close. Tidak ada permintaan mematikan penghemat baterai secara paksa.\n\n"
                + "Tidak ada backend aplikasi, iklan, atau analytics buatan aplikasi. Screenshot"
                + " dan cache teks sementara di memori. Error terakhir disimpan lokal tanpa"
                + " screenshot atau riwayat OCR. Model dan diagnostik SDK tunduk pada kebijakan"
                + " Google.\n\n"
                + "Terjemahan didukung Google Translate melalui ML Kit. Rich Translate tidak"
                + " berafiliasi dengan Google.\n\n"
                + "THIS SERVICE MAY CONTAIN TRANSLATIONS POWERED BY GOOGLE. GOOGLE DISCLAIMS ALL"
                + " WARRANTIES RELATED TO THE TRANSLATIONS, EXPRESS OR IMPLIED, INCLUDING ANY"
                + " WARRANTIES OF ACCURACY, RELIABILITY, AND ANY IMPLIED WARRANTIES OF"
                + " MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.\n\n"
                + "https://developers.google.com/ml-kit/terms\n"
                + "https://policies.google.com/privacy\n"
                + "https://cloud.google.com/translate")
        .setPositiveButton("Tutup", null)
        .show();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (!registered) {
      IntentFilter filter = new IntentFilter(RichAccessibilityService.STATE_CHANGED);
      androidx.core.content.ContextCompat.registerReceiver(
          this, stateChanged, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
      registered = true;
    }
    refresh();
    checkModels();
  }

  @Override
  protected void onPause() {
    if (registered) {
      unregisterReceiver(stateChanged);
      registered = false;
    }
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    if (downloadEngine != null) downloadEngine.close();
    super.onDestroy();
  }
}
