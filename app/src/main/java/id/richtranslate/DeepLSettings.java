package id.richtranslate;

import android.app.*;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.*;
import android.widget.*;

/** Own-key settings. No key is ever prefilled, saved in View state or printed in errors. */
final class DeepLSettings extends LinearLayout implements AutoCloseable {
  private final Activity activity;
  private final DeepLKeyStore keys;
  private final Runnable changed;
  private final Spinner provider;
  private final LinearLayout form;
  private final EditText input;
  private final TextView status;
  private final Button save, test, remove;
  private TranslationEngine checking;
  private boolean active, busy, closed;

  DeepLSettings(Activity activity, Runnable changed) {
    this(activity, changed, new DeepLKeyStore(activity));
  }

  DeepLSettings(Activity activity, Runnable changed, DeepLKeyStore keys) {
    super(activity);
    this.activity = activity;
    this.changed = changed;
    this.keys = keys;
    setOrientation(VERTICAL);
    addView(Ui.bold(activity, "Mesin terjemahan", 17, Ui.INK));
    space();
    provider = new Spinner(activity);
    provider.setTag("translation_provider");
    provider.setContentDescription("Mesin terjemahan");
    provider.setAdapter(
        new ArrayAdapter<>(
            activity,
            android.R.layout.simple_spinner_dropdown_item,
            new String[] {"ML Kit · offline", "DeepL · API sendiri"}));
    provider.setMinimumHeight(Ui.dp(activity, 48));
    addView(provider, new LayoutParams(-1, -2));
    provider.setSelection(Prefs.deepL(activity) ? 1 : 0);
    status = Ui.text(activity, "", 12, Ui.MUTED);
    status.setTag("deepl_status");
    space();
    addView(status);
    form = Ui.column(activity);
    addView(form, new LayoutParams(-1, -2));
    TextView note =
        Ui.text(
            activity,
            "Online · kuota/biaya akun sendiri. API Free (:fx) / Pro dipilih otomatis. Uji koneksi"
                + " hanya memeriksa penggunaan akun.",
            12,
            Ui.MUTED);
    space(form);
    form.addView(note);
    space(form);
    input = new EditText(activity);
    input.setTag("deepl_key_input");
    input.setHint("Masukkan API key DeepL sendiri");
    input.setContentDescription("API key DeepL");
    input.setSingleLine(true);
    input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    input.setTransformationMethod(PasswordTransformationMethod.getInstance());
    input.setSaveEnabled(false);
    input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
    input.setImeOptions(
        android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            | android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
    form.addView(input, new LayoutParams(-1, Ui.dp(activity, 56)));
    save = button("Simpan key", "deepl_save", form);
    test = button("Uji koneksi & kuota", "deepl_test", form);
    remove = button("Hapus key DeepL", "deepl_remove", this);
    save.setOnClickListener(
        v -> {
          try {
            DeepLApi.validateKey(input.getText().toString());
            keys.save(input.getText().toString());
            input.setText("");
            cancelCheck();
            changed.run();
            update();
            status.setText("Key tersimpan terenkripsi · belum diuji. Free/Pro mengikuti key.");
          } catch (Exception e) {
            input.setText("");
            status.setText(
                "Key tidak dapat disimpan. Periksa format atau Keystore perangkat; tidak ada"
                    + " penyimpanan plaintext.");
          }
        });
    remove.setOnClickListener(
        v -> {
          cancelCheck();
          keys.clear();
          input.setText("");
          provider.setSelection(0);
          changed.run();
          update();
          status.setText("Key DeepL dan persetujuan dihapus. ML Kit dipilih.");
        });
    test.setOnClickListener(v -> check());
    provider.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onNothingSelected(AdapterView<?> parent) {}

          public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            boolean remote = position == 1;
            if (remote == Prefs.deepL(activity)) return;
            if (active) {
              provider.setSelection(Prefs.deepL(activity) ? 1 : 0);
              return;
            }
            if (!remote) {
              select(false);
              return;
            }
            provider.setSelection(0);
            new AlertDialog.Builder(activity)
                .setTitle("Gunakan DeepL API?")
                .setMessage(
                    "Teks hasil OCR dari area pilihan akan dikirim melalui HTTPS langsung ke server"
                        + " DeepL untuk diterjemahkan, termasuk saat Auto dialog aktif. Screenshot"
                        + " tetap diproses lokal dan tidak diunggah. API key dikirim hanya ke host"
                        + " resmi DeepL untuk autentikasi. Perlu internet; kuota atau biaya"
                        + " mengikuti akun API Free/Pro kamu. Akun DeepL biasa belum tentu memiliki"
                        + " akses API. Tidak ada fallback diam-diam atau retry terjemahan otomatis."
                        + " Gunakan hanya untuk teks yang boleh kamu kirim ke DeepL. Kebijakan:"
                        + " https://www.deepl.com/privacy . Key disimpan terenkripsi dengan Android"
                        + " Keystore, tetapi tidak dijamin aman pada perangkat root/terkompromi.")
                .setNegativeButton("Batal", null)
                .setPositiveButton(
                    "Setuju, gunakan DeepL",
                    (dialog, which) -> {
                      select(true);
                      provider.setSelection(1);
                    })
                .show();
          }
        });
    update();
  }

  private void select(boolean remote) {
    cancelCheck();
    input.setText("");
    Prefs.get(activity)
        .edit()
        .putString("translation_provider", remote ? "deepl" : "mlkit")
        .putBoolean("deepl_consent", remote)
        .apply();
    changed.run();
    update();
  }

  private void space() {
    space(this);
  }

  private void space(LinearLayout box) {
    box.addView(new Space(activity), new LayoutParams(1, Ui.dp(activity, 10)));
  }

  private Button button(String label, String tag, LinearLayout box) {
    space(box);
    Button button = Ui.button(activity, label);
    button.setTag(tag);
    button.setMinimumHeight(Ui.dp(activity, 48));
    button.setMinHeight(Ui.dp(activity, 48));
    box.addView(button, new LayoutParams(-1, -2));
    return button;
  }

  void refreshControls(boolean locked) {
    active = locked;
    updateControls();
  }

  private void updateControls() {
    boolean remote = Prefs.deepL(activity);
    form.setVisibility(remote ? VISIBLE : GONE);
    provider.setEnabled(!active && !busy);
    input.setEnabled(!active && !busy);
    save.setEnabled(!active && !busy);
    test.setEnabled(
        !active
            && !busy
            && keys.hasKey()
            && Prefs.get(activity).getBoolean("deepl_consent", false));
    remove.setVisibility(keys.hasKey() ? VISIBLE : GONE);
    remove.setEnabled(!active && !busy);
    test.setText(busy ? "Memeriksa…" : "Uji koneksi & kuota");
  }

  private void update() {
    status.setText(
        Prefs.deepL(activity)
            ? (keys.hasKey()
                ? "Key tersimpan terenkripsi · uji untuk memeriksa akun."
                : "Belum ada key. Masukkan lalu simpan API key sendiri.")
            : (keys.hasKey()
                ? "ML Kit offline · key DeepL tersimpan, tidak digunakan."
                : "ML Kit menerjemahkan lokal setelah model siap."));
    updateControls();
  }

  private void check() {
    if (active
        || busy
        || !Prefs.deepL(activity)
        || !Prefs.get(activity).getBoolean("deepl_consent", false)) return;
    try {
      checking = new TranslationEngine(Prefs.source(activity), Prefs.target(activity), keys.read());
      busy = true;
      status.setText("Menghubungi DeepL untuk memeriksa key dan penggunaan akun…");
      updateControls();
      checking.checkApi(
          new TranslationEngine.Result() {
            public void ok(String message) {
              done(message);
            }

            public void error(String message) {
              done(message);
            }

            private void done(String message) {
              if (closed || activity.isDestroyed()) return;
              cancelCheck();
              status.setText(message);
              updateControls();
            }
          });
    } catch (Exception e) {
      status.setText("Key tersimpan tidak dapat dibuka. Hapus dan simpan ulang key DeepL.");
    }
  }

  private void cancelCheck() {
    if (checking != null) {
      checking.close();
      checking = null;
    }
    busy = false;
  }

  void pause() {
    input.setText("");
    if (busy) status.setText("Uji koneksi dibatalkan.");
    cancelCheck();
    updateControls();
  }

  @Override
  public void close() {
    closed = true;
    pause();
  }
}
