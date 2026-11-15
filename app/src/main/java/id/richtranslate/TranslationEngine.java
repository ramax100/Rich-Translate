package id.richtranslate;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.*;
import java.util.*;
import java.util.concurrent.*;

final class TranslationEngine implements AutoCloseable {
  interface Result {
    void ok(String text);

    void error(String message);
  }

  interface BatchResult {
    void ok(List<String> texts);

    void error(String message);
  }

  private final Translator translator;
  private final DeepLApi remote;
  private final String source, target;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final ExecutorService network;
  private volatile boolean closed;

  static TranslationEngine forContext(Context context) {
    if (!Prefs.deepL(context))
      return new TranslationEngine(Prefs.source(context), Prefs.target(context));
    if (!Prefs.get(context).getBoolean("deepl_consent", false))
      throw new IllegalStateException(
          "Setujui pengiriman teks ke DeepL di Setelan terlebih dahulu.");
    try {
      return new TranslationEngine(
          Prefs.source(context), Prefs.target(context), new DeepLKeyStore(context).read());
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException(e.getMessage());
    }
  }

  TranslationEngine(String source, String target, String key) {
    this(source, target, new DeepLApi(key));
  }

  TranslationEngine(String source, String target, DeepLApi remote) {
    this.source = source;
    this.target = target;
    translator = null;
    this.remote = remote;
    network = Executors.newSingleThreadExecutor();
  }

  boolean isDeepL() {
    return remote != null;
  }

  void checkApi(Result callback) {
    if (closed) return;
    if (remote == null) {
      callback.error("Pilih DeepL untuk uji API.");
      return;
    }
    network.execute(
        () -> {
          try {
            String result = remote.checkUsage();
            main.post(
                () -> {
                  if (!closed) callback.ok(result);
                });
          } catch (DeepLApi.Failure e) {
            main.post(
                () -> {
                  if (!closed) callback.error(e.getMessage());
                });
          }
        });
  }

  void translateBatch(List<String> values, BatchResult callback) {
    if (closed) return;
    List<String> inputs = new ArrayList<>(values);
    if (inputs.isEmpty()) {
      callback.error("Tidak ada teks untuk diterjemahkan.");
      return;
    }
    if (remote == null) {
      List<String> results = new ArrayList<>(Collections.nCopies(inputs.size(), ""));
      int[] remaining = {inputs.size()};
      boolean[] failed = {false};
      for (int i = 0; i < inputs.size(); i++) {
        final int index = i;
        translate(
            inputs.get(i),
            new Result() {
              public void ok(String text) {
                if (closed || failed[0]) return;
                results.set(index, text);
                if (--remaining[0] == 0) callback.ok(results);
              }

              public void error(String message) {
                if (closed || failed[0]) return;
                failed[0] = true;
                callback.error(message);
              }
            });
      }
      return;
    }
    List<String> missing = new ArrayList<>();
    Map<String, String> ready = new HashMap<>();
    for (String input : inputs) {
      if (cache.containsKey(input)) ready.put(input, cache.get(input));
      else if (!missing.contains(input)) missing.add(input);
    }
    if (missing.isEmpty()) {
      List<String> result = new ArrayList<>();
      for (String input : inputs) result.add(ready.get(input));
      callback.ok(result);
      return;
    }
    network.execute(
        () -> {
          try {
            List<String> translated = remote.translate(missing, source, target);
            main.post(
                () -> {
                  if (closed) return;
                  for (int i = 0; i < missing.size(); i++) {
                    ready.put(missing.get(i), translated.get(i));
                    cache.put(missing.get(i), translated.get(i));
                  }
                  List<String> result = new ArrayList<>();
                  for (String input : inputs) result.add(ready.get(input));
                  callback.ok(result);
                });
          } catch (DeepLApi.Failure e) {
            main.post(
                () -> {
                  if (!closed) callback.error(e.getMessage());
                });
          }
        });
  }

  private final Map<String, String> cache =
      new LinkedHashMap<String, String>(64, .75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String, String> item) {
          return size() > 150;
        }
      };

  TranslationEngine(String source, String target) {
    this.source = source;
    this.target = target;
    remote = null;
    network = null;
    translator =
        Translation.getClient(
            new TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(target)
                .build());
  }

  void prepare(boolean wifiOnly, Result callback) {
    if (closed) return;
    if (remote != null) {
      callback.error("DeepL memakai internet, bukan model offline.");
      return;
    }
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
    if (remote != null) {
      translateBatch(
          Collections.singletonList(value),
          new BatchResult() {
            public void ok(List<String> texts) {
              callback.ok(texts.get(0));
            }

            public void error(String message) {
              callback.error(message);
            }
          });
      return;
    }
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
    if (remote != null) {
      remote.close();
      network.shutdownNow();
      return;
    }
    try {
      translator.close();
    } catch (RuntimeException error) {
      android.util.Log.w("RichTranslate", "SDK translator close failed", error);
    }
  }
}
