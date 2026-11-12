package id.richtranslate;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.*;
import java.util.LinkedHashMap;
import java.util.Map;

final class TranslationEngine implements AutoCloseable {
  interface Result {
    void ok(String text);

    void error(String message);
  }

  private final Translator translator;
  private boolean closed;
  private final Map<String, String> cache =
      new LinkedHashMap<String, String>(64, .75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String, String> item) {
          return size() > 150;
        }
      };

  TranslationEngine(String source, String target) {
    translator =
        Translation.getClient(
            new TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(target)
                .build());
  }

  void prepare(boolean wifiOnly, Result callback) {
    if (closed) return;
    try {
      DownloadConditions.Builder options = new DownloadConditions.Builder();
      if (wifiOnly) options.requireWifi();
      translator
          .downloadModelIfNeeded(options.build())
          .addOnSuccessListener(
              ignored -> {
                if (!closed) callback.ok("Model bahasa siap. Bisa digunakan offline.");
              })
          .addOnFailureListener(
              error -> {
                if (!closed)
                  callback.error(
                      "Model belum siap. Periksa internet, ruang penyimpanan, atau pilihan Wi-Fi. "
                          + error.getLocalizedMessage());
              });
    } catch (RuntimeException error) {
      if (!closed)
        callback.error("SDK unduhan model belum siap: " + error.getClass().getSimpleName());
    }
  }

  void translate(String value, Result callback) {
    if (closed) return;
    if (cache.containsKey(value)) {
      callback.ok(cache.get(value));
      return;
    }
    try {
      translator
          .translate(value)
          .addOnSuccessListener(
              text -> {
                if (!closed) {
                  cache.put(value, text);
                  callback.ok(text);
                }
              })
          .addOnFailureListener(
              error -> {
                if (!closed)
                  callback.error(
                      "Gagal menerjemahkan. Periksa model offline. " + error.getLocalizedMessage());
              });
    } catch (RuntimeException error) {
      if (!closed) callback.error("SDK terjemahan belum siap: " + error.getClass().getSimpleName());
    }
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    cache.clear();
    try {
      translator.close();
    } catch (RuntimeException error) {
      android.util.Log.w("RichTranslate", "SDK translator close failed", error);
    }
  }
}
