package id.richtranslate;

import static org.junit.Assert.*;

import android.app.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class DeepLIntegrationTest {
  Context context = RuntimeEnvironment.getApplication();

  void set(Object o, String name, Object value) throws Exception {
    Field f = o.getClass().getDeclaredField(name);
    f.setAccessible(true);
    f.set(o, value);
  }

  Object get(Object o, String name) throws Exception {
    Field f = o.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return f.get(o);
  }

  @Before
  public void setup() {
    Prefs.get(context).edit().clear().commit();
    Shadows.shadowOf((Application) context)
        .grantPermissions("id.richtranslate.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");
  }

  @After
  public void clean() {
    Prefs.get(context).edit().clear().commit();
  }

  @Test
  public void deepLHomeHidesOfflineDownloadAndDoesNotGateOnModels() throws Exception {
    new DeepLKeyStore(context, () -> new SecretKeySpec(new byte[32], "AES"))
        .save(DeepLApiTest.FAKE);
    Prefs.get(context)
        .edit()
        .putString("translation_provider", "deepl")
        .putBoolean("deepl_consent", true)
        .commit();
    var c = Robolectric.buildActivity(MainActivity.class).setup();
    MainActivity activity = c.get();
    assertEquals(
        View.GONE,
        activity.getWindow().getDecorView().findViewWithTag("download_button").getVisibility());
    TextView status = activity.getWindow().getDecorView().findViewWithTag("model_status");
    assertTrue(status.getText().toString().contains("DeepL"));
    Method start = MainActivity.class.getDeclaredMethod("start");
    start.setAccessible(true);
    start.invoke(activity);
    assertNotNull(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog());
    assertFalse(status.getText().toString().contains("Unduh model terlebih"));
    c.pause().stop().destroy();
  }

  @Test
  public void deepLResultsNeverUseGoogleTranslationBadge() throws Exception {
    var c = Robolectric.buildService(RichAccessibilityService.class).create();
    var service = c.get();
    TranslationEngine engine =
        new TranslationEngine(
            "en", "id", new DeepLApi(DeepLApiTest.FAKE, url -> new DeepLApiTest.Fake(url)));
    OverlayHost host = new OverlayHost(context, 1080, 520, () -> {});
    set(service, "translator", engine);
    set(service, "overlay", host);
    set(service, "width", 1080);
    set(service, "height", 520);
    Method method =
        RichAccessibilityService.class.getDeclaredMethod("addAttribution", List.class, List.class);
    method.setAccessible(true);
    method.invoke(service, Collections.emptyList(), Collections.emptyList());
    assertEquals(1, host.resultLayer.getChildCount());
    assertTrue(host.resultLayer.getChildAt(0) instanceof TextView);
    assertEquals("DeepL API", ((TextView) host.resultLayer.getChildAt(0)).getText().toString());
    c.destroy();
  }

  @Test
  public void closeToBallClosesRemoteBackendRatherThanOnlyDiscardingResult() throws Exception {
    var c = Robolectric.buildService(RichAccessibilityService.class).create();
    var service = c.get();
    TranslationEngine engine =
        new TranslationEngine(
            "en", "id", new DeepLApi(DeepLApiTest.FAKE, url -> new DeepLApiTest.Fake(url)));
    WindowManager wm =
        (WindowManager)
            Proxy.newProxyInstance(
                WindowManager.class.getClassLoader(),
                new Class[] {WindowManager.class},
                (proxy, method, args) -> null);
    set(service, "translator", engine);
    set(service, "wm", wm);
    set(service, "active", true);
    set(service, "width", 1080);
    set(service, "height", 520);
    Method reset = RichAccessibilityService.class.getDeclaredMethod("resetToBall");
    reset.setAccessible(true);
    reset.invoke(service);
    assertTrue((boolean) get(engine, "closed"));
    assertNull(get(service, "translator"));
    assertEquals("BALL", service.currentStage());
    c.destroy();
  }
}
