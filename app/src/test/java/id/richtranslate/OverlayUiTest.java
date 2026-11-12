package id.richtranslate;

import static org.junit.Assert.*;

import android.content.Context;
import android.view.View;
import android.view.WindowManager;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Android framework layout tests in JVM, not a physical device test. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, qualifiers = "w393dp-h851dp-xhdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class OverlayUiTest {
  private final Context context = RuntimeEnvironment.getApplication();

  @Test
  public void trustedAccessibilityOverlayRemainsFullyOpaque() throws Exception {
    org.robolectric.android.controller.ServiceController<RichAccessibilityService> controller =
        Robolectric.buildService(RichAccessibilityService.class).create();
    Method method =
        RichAccessibilityService.class.getDeclaredMethod(
            "params", int.class, int.class, boolean.class);
    method.setAccessible(true);
    WindowManager.LayoutParams p =
        (WindowManager.LayoutParams) method.invoke(controller.get(), 200, 80, false);
    assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, p.type);
    assertEquals(1f, p.alpha, .001f);
    assertTrue((p.flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0);
    controller.destroy();
  }

  @Test
  public void accessibilityDoesNotRequestTextTreeOrGestures() throws Exception {
    android.content.res.XmlResourceParser xml =
        context.getResources().getXml(R.xml.accessibility_service);
    while (xml.next() != org.xmlpull.v1.XmlPullParser.START_TAG) {}
    String ns = "http://schemas.android.com/apk/res/android";
    assertTrue(xml.getAttributeBooleanValue(ns, "canTakeScreenshot", false));
    assertFalse(xml.getAttributeBooleanValue(ns, "canRetrieveWindowContent", true));
    assertFalse(xml.getAttributeBooleanValue(ns, "canPerformGestures", true));
    xml.close();
  }

  @Test
  public void selectorDoesNotUseSoftwareBitmapLayerOrTakeFocus() {
    LassoView view =
        new LassoView(
            context,
            new LassoView.Done() {
              public void selected(java.util.List<float[]> p) {}

              public void cancel() {}
            });
    assertFalse(view.isFocusable());
    assertEquals(android.view.View.LAYER_TYPE_NONE, view.getLayerType());
    for (java.lang.reflect.Field field : LassoView.class.getDeclaredFields())
      assertNotEquals(android.graphics.Bitmap.class, field.getType());
  }

  @Test
  public void transparentBallHasNoOpaqueFill() {
    FloatingBallView view = new FloatingBallView(context);
    view.layout(0, 0, 80, 80);
    android.graphics.Bitmap image =
        android.graphics.Bitmap.createBitmap(80, 80, android.graphics.Bitmap.Config.ARGB_8888);
    view.draw(new android.graphics.Canvas(image));
    assertEquals(32, FloatingBallView.VISIBLE_DP);
    assertEquals(40, FloatingBallView.TOUCH_DP);
    int alpha = android.graphics.Color.alpha(image.getPixel(40, 40));
    assertEquals(102, alpha);
    int center = image.getPixel(40, 40);
    assertEquals(android.graphics.Color.red(center), android.graphics.Color.green(center));
    assertEquals(android.graphics.Color.green(center), android.graphics.Color.blue(center));
    assertEquals(0, android.graphics.Color.alpha(image.getPixel(0, 0)));
    image.recycle();
  }

  @Test
  public void selectionStrokeIsRed() throws Exception {
    LassoView view =
        new LassoView(
            context,
            new LassoView.Done() {
              public void selected(java.util.List<float[]> p) {}

              public void cancel() {}
            });
    java.lang.reflect.Field field = LassoView.class.getDeclaredField("line");
    field.setAccessible(true);
    assertEquals(0xFFE53935, ((android.graphics.Paint) field.get(view)).getColor());
  }

  @Test
  public void newPackageAndLabelsAreConsistent() throws Exception {
    assertEquals("id.richtranslate", context.getPackageName());
    android.content.pm.ApplicationInfo info =
        context.getPackageManager().getApplicationInfo(context.getPackageName(), 0);
    assertEquals(
        "Rich Translate", context.getPackageManager().getApplicationLabel(info).toString());
  }

  @Test
  public void adaptiveIconResourceCanBeLoaded() {
    assertNotNull(context.getDrawable(R.mipmap.ic_launcher));
    assertNotNull(context.getDrawable(R.mipmap.ic_launcher_round));
  }

  @Test
  public void canceledGestureDoesNotSubmitTextSelection() {
    int[] events = {0, 0};
    LassoView view =
        new LassoView(
            context,
            new LassoView.Done() {
              public void selected(java.util.List<float[]> p) {
                events[0]++;
              }

              public void cancel() {
                events[1]++;
              }
            });
    view.layout(0, 0, 480, 800);
    android.view.MotionEvent down =
        android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_DOWN, 100, 300, 0);
    view.onTouchEvent(down);
    down.recycle();
    android.view.MotionEvent cancel =
        android.view.MotionEvent.obtain(0, 20, android.view.MotionEvent.ACTION_CANCEL, 100, 300, 0);
    view.onTouchEvent(cancel);
    cancel.recycle();
    assertEquals(0, events[0]);
    assertEquals(1, events[1]);
  }

  @Test
  public void failedBallAttachmentReturnsToIdle() throws Exception {
    org.robolectric.android.controller.ServiceController<RichAccessibilityService> controller =
        Robolectric.buildService(RichAccessibilityService.class).create();
    RichAccessibilityService service = controller.get();
    java.lang.reflect.Field active = RichAccessibilityService.class.getDeclaredField("active");
    active.setAccessible(true);
    active.setBoolean(service, true);
    Method show = RichAccessibilityService.class.getDeclaredMethod("showBall");
    show.setAccessible(true);
    show.invoke(service);
    assertFalse(service.isActive());
    assertEquals("IDLE", service.currentStage());
    controller.destroy();
  }

  @Test
  public void stopRetainsQueuedBitmapCleanupCallbacks() throws Exception {
    org.robolectric.android.controller.ServiceController<RichAccessibilityService> controller =
        Robolectric.buildService(RichAccessibilityService.class).create();
    RichAccessibilityService service = controller.get();
    java.lang.reflect.Field main = RichAccessibilityService.class.getDeclaredField("main");
    main.setAccessible(true);
    int[] delivered = {0};
    ((android.os.Handler) main.get(service)).post(() -> delivered[0]++);
    service.stopSession(null);
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    assertEquals(1, delivered[0]);
    controller.destroy();
  }

  @Test
  public void greyBallIsVisibleOnLightAndDarkBackgrounds() {
    FloatingBallView view = new FloatingBallView(context);
    view.layout(0, 0, 80, 80);
    android.graphics.Bitmap bitmap =
        android.graphics.Bitmap.createBitmap(80, 80, android.graphics.Bitmap.Config.ARGB_8888);
    android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
    canvas.drawColor(android.graphics.Color.WHITE);
    view.draw(canvas);
    int light = android.graphics.Color.red(bitmap.getPixel(40, 40));
    assertTrue(light > 120 && light < 210);
    canvas.drawColor(android.graphics.Color.BLACK);
    view.draw(canvas);
    int dark = android.graphics.Color.red(bitmap.getPixel(40, 40));
    assertTrue(dark > 5 && dark < 60);
    bitmap.recycle();
  }

  @Test
  public void homeScreenCanBeCreated() {
    // Android grants this app's own signature permission on installation;
    // Robolectric requires explicitly simulating that grant.
    org.robolectric.Shadows.shadowOf((android.app.Application) context)
        .grantPermissions("id.richtranslate.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");
    org.robolectric.android.controller.ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).setup();
    assertNotNull(controller.get().findViewById(android.R.id.content));
    View decor = controller.get().getWindow().getDecorView();
    int w = controller.get().getResources().getDisplayMetrics().widthPixels;
    int h = controller.get().getResources().getDisplayMetrics().heightPixels;
    decor.measure(
        View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
    decor.layout(0, 0, w, h);
    android.graphics.Bitmap bitmap =
        android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
    decor.draw(new android.graphics.Canvas(bitmap));
    java.io.File output = new java.io.File("build/reports/ui/home.png");
    output.getParentFile().mkdirs();
    try (java.io.FileOutputStream stream = new java.io.FileOutputStream(output)) {
      bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream);
    } catch (java.io.IOException e) {
      throw new RuntimeException(e);
    }
    bitmap.recycle();
    controller.pause().stop().destroy();
  }
}
