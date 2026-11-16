package id.richtranslate;

import static org.junit.Assert.*;

import android.app.Application;
import android.content.Context;
import android.graphics.Rect;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class AutoModeTest {
  Context context = RuntimeEnvironment.getApplication();

  void field(Object o, String name, Object value) throws Exception {
    Field f = o.getClass().getDeclaredField(name);
    f.setAccessible(true);
    f.set(o, value);
  }

  Object get(Object o, String name) throws Exception {
    Field f = o.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return f.get(o);
  }

  @Test
  public void autoDefaultsOff() {
    Prefs.get(context).edit().clear().commit();
    assertFalse(Prefs.get(context).getBoolean("auto_translate", false));
  }

  @Test
  public void touchPassThroughPreservesSecureAndFocusFlags() {
    int flags =
        WindowManager.LayoutParams.FLAG_SECURE | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
    int auto = AutoCapturePolicy.flags(flags, true);
    assertTrue((auto & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0);
    assertEquals(flags, auto & ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    assertEquals(flags, AutoCapturePolicy.flags(flags, false));
  }

  @Test
  public void api34WindowCaptureOnlyWithValidTarget() {
    assertFalse(AutoCapturePolicy.useWindowCapture(30, 4));
    assertFalse(AutoCapturePolicy.useWindowCapture(33, 4));
    assertFalse(AutoCapturePolicy.useWindowCapture(34, -1));
    assertTrue(AutoCapturePolicy.useWindowCapture(34, 4));
  }

  @Test
  public void partialWindowCannotUseDisplayCoordinates() {
    assertNull(WindowCaptureFrame.resolve(1000, 400, new Rect(0, 0, 1080, 520), 1080, 520));
    assertEquals(
        new Rect(0, 0, 1080, 520),
        WindowCaptureFrame.resolve(1080, 520, new Rect(0, 0, 1080, 520), 1080, 520));
  }

  @Test
  public void selectedLassoBecomesDialogueRectangle() {
    List<float[]> r =
        AutoCapturePolicy.rectangle(
            Arrays.asList(new float[] {20, 40}, new float[] {300, 60}, new float[] {250, 150}),
            1080,
            520);
    assertArrayEquals(new float[] {20, 40}, r.get(0), 0);
    assertArrayEquals(new float[] {300, 150}, r.get(2), 0);
    assertTrue(AutoCapturePolicy.controlOverlaps(r, new Rect(22, 45, 50, 80)));
    assertFalse(AutoCapturePolicy.controlOverlaps(r, new Rect(900, 0, 1000, 50)));
  }

  @Test(expected = IllegalArgumentException.class)
  public void invalidAreaRejected() {
    AutoCapturePolicy.rectangle(
        Arrays.asList(new float[] {0, 0}, new float[] {1, 1}, new float[] {2, 2}), 1080, 520);
  }

  @Test
  public void resultLayerCanBeReplacedWithoutReplacingClose() {
    OverlayHost host = new OverlayHost(context, 1080, 520, () -> {});
    View close = host.closeButton;
    host.commit(Collections.emptyList(), new TextView(context), new Rect(0, 0, 100, 30));
    assertEquals(1, host.resultLayer.getChildCount());
    host.clearResults();
    assertEquals(0, host.resultLayer.getChildCount());
    host.commit(Collections.emptyList(), new TextView(context), new Rect(0, 0, 100, 30));
    assertEquals(1, host.resultLayer.getChildCount());
    assertSame(close, host.closeButton);
  }

  @Test
  public void serviceSeparatesTouchableStopFromUntouchableResults() throws Exception {
    var controller = Robolectric.buildService(RichAccessibilityService.class).create();
    var service = controller.get();
    List<WindowManager.LayoutParams> windows = new ArrayList<>();
    List<View> views = new ArrayList<>();
    WindowManager wm =
        (WindowManager)
            Proxy.newProxyInstance(
                WindowManager.class.getClassLoader(),
                new Class[] {WindowManager.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("addView")) {
                    views.add((View) args[0]);
                    windows.add((WindowManager.LayoutParams) args[1]);
                  }
                  return null;
                });
    field(service, "wm", wm);
    field(service, "width", 1080);
    field(service, "height", 520);
    field(service, "active", true);
    field(service, "automatic", true);
    field(
        service,
        "monitoredArea",
        AutoCapturePolicy.rectangle(
            Arrays.asList(new float[] {20, 300}, new float[] {900, 300}, new float[] {900, 480}),
            1080,
            520));
    Method show = RichAccessibilityService.class.getDeclaredMethod("showClose");
    show.setAccessible(true);
    show.invoke(service);
    show.invoke(service);
    assertEquals(2, windows.size());
    assertTrue((windows.get(0).flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0);
    assertFalse((windows.get(1).flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0);
    assertEquals(44, windows.get(1).width / context.getResources().getDisplayMetrics().density, 1);
    assertEquals(View.GONE, ((OverlayHost) views.get(0)).closeButton.getVisibility());
    service.stopSession(null);
    assertFalse((boolean) get(service, "automatic"));
    assertNull(get(service, "monitoredArea"));
    assertNull(get(service, "autoClose"));
    controller.destroy();
  }

  @Test
  public void cancellationInvalidatesQueuedAutomaticWork() throws Exception {
    var c = Robolectric.buildService(RichAccessibilityService.class).create();
    var s = c.get();
    field(s, "automatic", true);
    field(s, "autoBusy", true);
    field(s, "pendingText", "Previous dialogue");
    field(s, "generation", 7);
    s.stopSession(null);
    assertEquals(8, get(s, "generation"));
    assertFalse((boolean) get(s, "autoBusy"));
    assertEquals("", get(s, "pendingText"));
    assertNull(get(s, "nextScan"));
    assertNull(get(s, "jobTimeout"));
    c.destroy();
  }

  @Test
  public void enablingAutoRequiresSeparateConsent() {
    Shadows.shadowOf((Application) context)
        .grantPermissions("id.richtranslate.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");
    Prefs.get(context).edit().clear().commit();
    var c = Robolectric.buildActivity(MainActivity.class).setup();
    Switch control = c.get().getWindow().getDecorView().findViewWithTag("auto_translate");
    assertNotNull(control);
    control.setChecked(true);
    assertFalse(Prefs.get(context).getBoolean("auto_translate", false));
    android.app.AlertDialog dialog =
        org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    assertNotNull(dialog);
    dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    assertTrue(Prefs.get(context).getBoolean("auto_translate", false));
    c.pause().stop().destroy();
    Prefs.get(context).edit().clear().commit();
  }
}
