package id.richtranslate;

import static org.junit.Assert.*;

import android.accessibilityservice.AccessibilityService;
import android.app.Application;
import android.content.Context;
import android.graphics.Rect;
import android.os.Looper;
import android.view.*;
import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AutoWindowCaptureTest {
  public static class CaptureService extends RichAccessibilityService {
    int windowCalls, displayCalls, error = -1;
    boolean missingMetadata, throwCapture;
    List<Integer> observedVisibility = new ArrayList<>();
    TakeScreenshotCallback lastCallback;

    @Override
    int focusedApplicationWindow() {
      return 7;
    }

    @Override
    Rect applicationWindowBounds(int id) {
      return missingMetadata ? null : new Rect(0, 40, 1080, 1880);
    }

    @Override
    void captureWindow(int id, TakeScreenshotCallback callback) {
      windowCalls++;
      lastCallback = callback;
      try {
        observedVisibility.add(((View) field(this, "overlay")).getVisibility());
      } catch (Exception e) {
        throw new AssertionError(e);
      }
      if (throwCapture) throw new IllegalStateException("test window error");
      if (error >= 0) callback.onFailure(error);
    }

    @Override
    void captureDisplay(TakeScreenshotCallback callback) {
      displayCalls++;
      lastCallback = callback;
    }
  }

  Context context = RuntimeEnvironment.getApplication();
  org.robolectric.android.controller.ServiceController<CaptureService> controller;
  CaptureService service;
  OverlayHost host;

  static Object field(Object o, String name) throws Exception {
    Field f = RichAccessibilityService.class.getDeclaredField(name);
    f.setAccessible(true);
    return f.get(o);
  }

  void set(String name, Object value) throws Exception {
    Field f = RichAccessibilityService.class.getDeclaredField(name);
    f.setAccessible(true);
    f.set(service, value);
  }

  void call(String name) throws Exception {
    Method m = RichAccessibilityService.class.getDeclaredMethod(name);
    m.setAccessible(true);
    m.invoke(service);
  }

  void request(int epoch) throws Exception {
    Method m =
        RichAccessibilityService.class.getDeclaredMethod(
            "requestScreenshot", int.class, int.class, List.class);
    m.setAccessible(true);
    m.invoke(service, epoch, 0, field(service, "monitoredArea"));
  }

  @Before
  public void setup() throws Exception {
    Prefs.get(context).edit().clear().commit();
    Shadows.shadowOf((Application) context)
        .grantPermissions("id.richtranslate.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");
    controller = Robolectric.buildService(CaptureService.class).create();
    service = controller.get();
    WindowManager wm =
        (WindowManager)
            Proxy.newProxyInstance(
                WindowManager.class.getClassLoader(),
                new Class[] {WindowManager.class},
                (proxy, method, args) -> null);
    set("wm", wm);
    set("width", 1080);
    set("height", 1920);
    set("active", true);
    set("automatic", true);
    set("targetWindow", 7);
    set(
        "monitoredArea",
        AutoCapturePolicy.rectangle(
            List.of(new float[] {100, 1200}, new float[] {900, 1200}, new float[] {900, 1700}),
            1080,
            1920));
    host = new OverlayHost(context, 1080, 1920, () -> {});
    set("overlay", host);
    host.setVisibility(View.VISIBLE);
  }

  @After
  public void cleanup() {
    controller.destroy();
    Prefs.get(context).edit().clear().commit();
  }

  @Test
  public void modernScanNeverHidesResultsAndNeverUsesDisplayCapture() throws Exception {
    call("beginAutoScan");
    assertEquals(1, service.windowCalls);
    assertEquals(0, service.displayCalls);
    assertEquals(List.of(View.VISIBLE), service.observedVisibility);
    assertEquals(View.VISIBLE, host.getVisibility());
    assertSame(host, field(service, "overlay"));
    assertFalse((boolean) field(service, "hiddenForCapture"));
  }

  @Test
  public void invalidWindowRetriesStopInsteadOfEnablingFlickeringFallback() throws Exception {
    service.error = 5;
    call("beginAutoScan");
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(4000));
    assertEquals(3, service.windowCalls);
    assertEquals(0, service.displayCalls);
    assertEquals(List.of(View.VISIBLE, View.VISIBLE, View.VISIBLE), service.observedVisibility);
    assertFalse((boolean) field(service, "automatic"));
    assertEquals("BALL", service.currentStage());
  }

  @Test
  public void temporaryFailureKeepsSameHostAndStopVisible() throws Exception {
    service.error = 5;
    call("beginAutoScan");
    assertSame(host, field(service, "overlay"));
    assertEquals(View.VISIBLE, host.getVisibility());
    assertTrue((boolean) field(service, "automatic"));
    assertNotNull(field(service, "nextScan"));
  }

  @Test
  public void unavailableMetadataDoesNotFallBackToDisplayCapture() throws Exception {
    service.missingMetadata = true;
    call("beginAutoScan");
    assertEquals(0, service.windowCalls);
    assertEquals(0, service.displayCalls);
    assertEquals(View.VISIBLE, host.getVisibility());
    assertTrue((boolean) field(service, "automatic"));
  }

  @Test
  public void intervalErrorsHaveFiniteModernRetryBudget() throws Exception {
    service.error = AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT;
    call("beginAutoScan");
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(4000));
    assertEquals(3, service.windowCalls);
    assertEquals(0, service.displayCalls);
    assertFalse((boolean) field(service, "automatic"));
  }

  @Test
  public void secureWindowFailureStopsWithoutBypass() throws Exception {
    service.error = 6;
    call("beginAutoScan");
    assertEquals(1, service.windowCalls);
    assertEquals(0, service.displayCalls);
    assertFalse((boolean) field(service, "automatic"));
  }

  @Test
  public void missingPermissionStopsWithoutBypass() throws Exception {
    service.error = AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS;
    call("beginAutoScan");
    assertEquals(1, service.windowCalls);
    assertEquals(0, service.displayCalls);
    assertFalse((boolean) field(service, "automatic"));
  }

  @Test
  public void runtimeWindowFailureNeverStartsDisplayCapture() throws Exception {
    service.throwCapture = true;
    call("beginAutoScan");
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(4000));
    assertEquals(3, service.windowCalls);
    assertEquals(0, service.displayCalls);
    assertFalse((boolean) field(service, "automatic"));
  }

  @Test
  public void timeoutInvalidatesOldCallbackBeforeNextScan() throws Exception {
    call("beginAutoScan");
    var late = service.lastCallback;
    int capturedEpoch = (int) field(service, "generation");
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(6500));
    assertEquals(1, field(service, "windowCaptureFailures"));
    assertTrue((int) field(service, "generation") > capturedEpoch);
    late.onFailure(5);
    assertEquals(1, field(service, "windowCaptureFailures"));
    assertEquals(View.VISIBLE, host.getVisibility());
    assertEquals(0, service.displayCalls);
  }

  @Test
  public void cancelledEpochCannotStartCapture() throws Exception {
    service.stopSession(null);
    request(0);
    assertEquals(0, service.windowCalls);
    assertEquals(0, service.displayCalls);
  }

  @Test
  public void manualCaptureStillUsesDisplayApi() throws Exception {
    set("automatic", false);
    request(0);
    assertEquals(0, service.windowCalls);
    assertEquals(1, service.displayCalls);
  }

  @Test
  @Config(sdk = 30)
  public void android11RetainsExplicitLegacyHidePath() throws Exception {
    call("beginAutoScan");
    assertEquals(View.INVISIBLE, host.getVisibility());
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(110));
    assertEquals(1, service.displayCalls);
    assertEquals(0, service.windowCalls);
    service.lastCallback.onFailure(AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT);
    assertEquals(View.VISIBLE, host.getVisibility());
  }
}
