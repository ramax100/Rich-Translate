package id.richtranslate;

import static org.junit.Assert.*;

import android.os.Looper;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class DeepLEngineTest {
  void flush(TranslationEngine engine) throws Exception {
    Field f = TranslationEngine.class.getDeclaredField("network");
    f.setAccessible(true);
    ((ExecutorService) f.get(engine)).submit(() -> {}).get(10, TimeUnit.SECONDS);
    Shadows.shadowOf(Looper.getMainLooper()).idle();
  }

  @Test
  public void batchesDeduplicatesCachesAndRestoresOrder() throws Exception {
    List<DeepLApiTest.Fake> requests = new ArrayList<>();
    DeepLApi api =
        new DeepLApi(
            DeepLApiTest.FAKE,
            url -> {
              var f = new DeepLApiTest.Fake(url);
              f.response = "{\"translations\":[{\"text\":\"Satu\"},{\"text\":\"Dua\"}]}";
              requests.add(f);
              return f;
            });
    TranslationEngine engine = new TranslationEngine("en", "id", api);
    List<List<String>> callbacks = new ArrayList<>();
    TranslationEngine.BatchResult callback =
        new TranslationEngine.BatchResult() {
          public void ok(List<String> values) {
            callbacks.add(values);
          }

          public void error(String message) {
            fail(message);
          }
        };
    engine.translateBatch(List.of("One", "Two", "One"), callback);
    flush(engine);
    assertEquals(List.of("Satu", "Dua", "Satu"), callbacks.get(0));
    assertEquals(1, requests.size());
    assertEquals(2, new JSONObject(requests.get(0).sent.toString()).getJSONArray("text").length());
    engine.translateBatch(List.of("Two", "One"), callback);
    assertEquals(1, requests.size());
    assertEquals(List.of("Dua", "Satu"), callbacks.get(1));
    engine.close();
  }

  @Test
  public void closeSuppressesQueuedCallbacks() throws Exception {
    DeepLApi api = new DeepLApi(DeepLApiTest.FAKE, url -> new DeepLApiTest.Fake(url));
    TranslationEngine engine = new TranslationEngine("en", "id", api);
    int[] count = {0};
    engine.translateBatch(
        List.of("Open door"),
        new TranslationEngine.BatchResult() {
          public void ok(List<String> v) {
            count[0]++;
          }

          public void error(String m) {
            count[0]++;
          }
        });
    Field f = TranslationEngine.class.getDeclaredField("network");
    f.setAccessible(true);
    ((ExecutorService) f.get(engine)).submit(() -> {}).get(10, TimeUnit.SECONDS);
    engine.close();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertEquals(0, count[0]);
  }

  @Test
  public void failureNeverRetriesOrFallsBackToMlkit() throws Exception {
    int[] requests = {0}, errors = {0};
    DeepLApi api =
        new DeepLApi(
            DeepLApiTest.FAKE,
            url -> {
              requests[0]++;
              var f = new DeepLApiTest.Fake(url);
              f.status = 456;
              return f;
            });
    TranslationEngine engine = new TranslationEngine("en", "id", api);
    engine.translateBatch(
        List.of("One", "Two"),
        new TranslationEngine.BatchResult() {
          public void ok(List<String> v) {
            fail();
          }

          public void error(String m) {
            errors[0]++;
            assertTrue(m.contains("456"));
          }
        });
    flush(engine);
    assertEquals(1, requests[0]);
    assertEquals(1, errors[0]);
    assertTrue(engine.isDeepL());
    engine.close();
  }
}
