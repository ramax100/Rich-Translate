package id.richtranslate;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, qualifiers = "w540dp-h260dp-xhdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class OverlayHostTest {
  Context context = RuntimeEnvironment.getApplication();

  private TranslationCard strip() {
    Rect r = new Rect(120, 150, 400, 190);
    return new TranslationCard(
        context,
        "Buka pintu",
        TextPlacement.place(context, "Buka pintu", r, List.of(r), 1080, 520, 35, 520));
  }

  private void layout(View v) {
    v.measure(
        View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(520, View.MeasureSpec.EXACTLY));
    v.layout(0, 0, 1080, 520);
  }

  private void field(Object o, String name, Object value) throws Exception {
    Field f = o.getClass().getDeclaredField(name);
    f.setAccessible(true);
    f.set(o, value);
  }

  @Test
  public void processingAndResultShareTheSameCloseView() {
    OverlayHost host = new OverlayHost(context, 1080, 520, () -> {});
    View original = host.closeButton;
    host.commit(List.of(strip()), null, new Rect());
    layout(host);
    assertSame(original, host.closeButton);
    assertEquals(2, host.getChildCount());
    assertEquals(1, host.resultLayer.getChildCount());
  }

  @Test
  public void commitIsIdempotentAndCloseRemainsLast() {
    OverlayHost host = new OverlayHost(context, 1080, 520, () -> {});
    host.commit(List.of(strip()), new TextView(context), new Rect(10, 450, 200, 500));
    host.commit(List.of(strip()), null, new Rect());
    assertEquals(2, host.resultLayer.getChildCount());
    assertSame(host.closeButton, host.getChildAt(host.getChildCount() - 1));
  }

  @Test
  public void blankHostDoesNotPaintOrDimGame() {
    OverlayHost host = new OverlayHost(context, 1080, 520, () -> {});
    layout(host);
    Bitmap b = Bitmap.createBitmap(1080, 520, Bitmap.Config.ARGB_8888);
    host.draw(new Canvas(b));
    assertEquals(0, Color.alpha(b.getPixel(100, 200)));
    assertFalse(host.isFocusable());
    b.recycle();
  }

  @Test
  public void rootPreservesScreenCoordinatesWithoutPerStripWindows() {
    OverlayHost host = new OverlayHost(context, 1080, 520, () -> {});
    TranslationCard c = strip();
    host.commit(List.of(c), null, new Rect());
    layout(host);
    assertEquals(c.placement.bounds.left, c.getLeft());
    assertEquals(c.placement.bounds.top, c.getTop());
    assertSame(host.resultLayer, c.getParent());
  }

  @Test
  public void closeActionWorksBeforeAndAfterCommit() {
    int[] clicks = {0};
    OverlayHost host = new OverlayHost(context, 1080, 520, () -> clicks[0]++);
    host.closeButton.performClick();
    host.commit(List.of(strip()), null, new Rect());
    host.closeButton.performClick();
    assertEquals(2, clicks[0]);
  }

  @Test
  public void serviceCreatesOnlyOneWindowForProcessingAndMultipleResults() throws Exception {
    org.robolectric.android.controller.ServiceController<RichAccessibilityService> controller =
        Robolectric.buildService(RichAccessibilityService.class).create();
    RichAccessibilityService service = controller.get();
    int[] operations = {0, 0};
    WindowManager wm =
        (WindowManager)
            Proxy.newProxyInstance(
                WindowManager.class.getClassLoader(),
                new Class[] {WindowManager.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("addView")) operations[0]++;
                  if (method.getName().equals("updateViewLayout")) operations[1]++;
                  return null;
                });
    field(service, "wm", wm);
    field(service, "width", 1080);
    field(service, "height", 520);
    field(service, "active", true);
    field(service, "stage", RichAccessibilityService.Stage.PROCESSING);
    Method show = RichAccessibilityService.class.getDeclaredMethod("showClose");
    show.setAccessible(true);
    show.invoke(service);
    show.invoke(service);
    Class<?> type = Class.forName("id.richtranslate.RichAccessibilityService$Patch");
    Constructor<?> constructor =
        type.getDeclaredConstructor(Rect.class, String.class, List.class, float.class);
    constructor.setAccessible(true);
    List<Object> patches = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      Rect r = new Rect(100, 80 + i * 42, 400, 110 + i * 42);
      Object patch = constructor.newInstance(r, "Open door", List.of(r), 28f);
      Field translated = type.getDeclaredField("translated");
      translated.setAccessible(true);
      translated.set(patch, "Buka pintu");
      patches.add(patch);
    }
    Method render =
        RichAccessibilityService.class.getDeclaredMethod("render", List.class, int.class);
    render.setAccessible(true);
    render.invoke(service, patches, 0);
    render.invoke(service, patches, 0);
    assertEquals(1, operations[0]);
    assertEquals(0, operations[1]);
    assertEquals("RESULT", service.currentStage());
    controller.destroy();
  }

  @Test
  public void secureAndNonFocusFlagsAreNotRemovedByStabilityFix() throws Exception {
    org.robolectric.android.controller.ServiceController<RichAccessibilityService> controller =
        Robolectric.buildService(RichAccessibilityService.class).create();
    RichAccessibilityService service = controller.get();
    field(service, "secure", true);
    Method params =
        RichAccessibilityService.class.getDeclaredMethod(
            "params", int.class, int.class, boolean.class);
    params.setAccessible(true);
    WindowManager.LayoutParams p =
        (WindowManager.LayoutParams) params.invoke(service, 1080, 520, false);
    assertTrue((p.flags & WindowManager.LayoutParams.FLAG_SECURE) != 0);
    assertTrue((p.flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0);
    assertEquals(0, p.windowAnimations);
    assertEquals(0, p.getFitInsetsTypes());
    controller.destroy();
  }
}
