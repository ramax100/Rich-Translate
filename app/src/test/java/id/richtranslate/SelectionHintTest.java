package id.richtranslate;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, qualifiers = "w540dp-h260dp-xhdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class SelectionHintTest {
  Context context = RuntimeEnvironment.getApplication();
  int[] callbacks = {0, 0};

  LassoView view() {
    LassoView v =
        new LassoView(
            context,
            new LassoView.Done() {
              public void selected(List<float[]> p) {
                callbacks[0]++;
              }

              public void cancel() {
                callbacks[1]++;
              }
            });
    v.layout(0, 0, 1080, 520);
    return v;
  }

  RectF rect(LassoView view, String name) throws Exception {
    Field f = LassoView.class.getDeclaredField(name);
    f.setAccessible(true);
    return new RectF((RectF) f.get(view));
  }

  void touch(LassoView v, int action, float x, float y) {
    MotionEvent e = MotionEvent.obtain(0, 10, action, x, y, 0);
    v.onTouchEvent(e);
    e.recycle();
  }

  Bitmap draw(LassoView view) {
    Bitmap b = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
    view.draw(new Canvas(b));
    return b;
  }

  @Test
  public void chipIsLessThanHalfPreviousArea() throws Exception {
    LassoView v = view();
    RectF r = rect(v, "label");
    assertEquals(Ui.dp(context, 196), r.width(), .1);
    assertEquals(Ui.dp(context, 30), r.height(), .1);
    assertTrue(r.width() * r.height() < Ui.dp(context, 300) * Ui.dp(context, 48) * .5f);
    assertEquals(540, r.centerX(), .1);
  }

  @Test
  public void backgroundIsSofterAndOldBannerAreaIsNowTransparent() throws Exception {
    LassoView v = view();
    RectF r = rect(v, "label");
    Bitmap b = draw(v);
    assertEquals(200, Color.alpha(b.getPixel((int) r.left + Ui.dp(context, 4), (int) r.centerY())));
    assertEquals(0, Color.alpha(b.getPixel(540 - Ui.dp(context, 130), (int) r.centerY())));
    assertEquals(0, Color.alpha(b.getPixel(540, (int) r.bottom + Ui.dp(context, 6))));
    b.recycle();
  }

  @Test
  public void cancelKeepsFortyFourDpTouchTarget() throws Exception {
    LassoView v = view();
    RectF r = rect(v, "cancelBounds"), label = rect(v, "label");
    assertEquals(Ui.dp(context, 44), r.width(), .1);
    assertEquals(Ui.dp(context, 44), r.height(), .1);
    assertTrue(r.top < label.top);
    touch(v, MotionEvent.ACTION_DOWN, r.centerX(), r.top + 1);
    touch(v, MotionEvent.ACTION_UP, r.centerX(), r.top + 1);
    assertEquals(1, callbacks[1]);
    assertEquals(0, callbacks[0]);
  }

  @Test
  public void cancelGestureNeverSubmitsOnMoveOrRelease() throws Exception {
    LassoView v = view();
    RectF r = rect(v, "cancelBounds");
    touch(v, MotionEvent.ACTION_DOWN, r.centerX(), r.centerY());
    touch(v, MotionEvent.ACTION_MOVE, 100, 300);
    touch(v, MotionEvent.ACTION_MOVE, 300, 350);
    touch(v, MotionEvent.ACTION_UP, 100, 400);
    assertEquals(1, callbacks[1]);
    assertEquals(0, callbacks[0]);
  }

  @Test
  public void normalRedSelectionStillCompletes() throws Exception {
    LassoView v = view();
    touch(v, MotionEvent.ACTION_DOWN, 100, 250);
    touch(v, MotionEvent.ACTION_MOVE, 300, 250);
    touch(v, MotionEvent.ACTION_MOVE, 300, 400);
    touch(v, MotionEvent.ACTION_MOVE, 100, 400);
    touch(v, MotionEvent.ACTION_UP, 100, 250);
    assertEquals(1, callbacks[0]);
    assertEquals(0, callbacks[1]);
    Field f = LassoView.class.getDeclaredField("line");
    f.setAccessible(true);
    assertEquals(Ui.SELECTION_RED, ((Paint) f.get(v)).getColor());
  }

  @Test
  public void chipAndCancelRemainOnSmallScreens() throws Exception {
    LassoView v = view();
    v.layout(0, 0, Ui.dp(context, 150), Ui.dp(context, 120));
    for (String name : new String[] {"label", "cancelBounds"}) {
      RectF r = rect(v, name);
      assertTrue(r.left >= 0 && r.top >= 0);
      assertTrue(r.right <= v.getWidth() && r.bottom <= v.getHeight());
    }
  }

  @Test
  public void noFullScreenDimmingOrSoftwareBitmapLayer() throws Exception {
    LassoView v = view();
    Bitmap b = draw(v);
    for (int[] point : new int[][] {{0, 0}, {100, 200}, {900, 450}, {540, 400}})
      assertEquals(0, Color.alpha(b.getPixel(point[0], point[1])));
    assertEquals(View.LAYER_TYPE_NONE, v.getLayerType());
    for (Field field : LassoView.class.getDeclaredFields())
      assertNotEquals(Bitmap.class, field.getType());
    b.recycle();
  }

  @Test
  public void renderCompactHintAndRetainAccessibleInstructions() throws Exception {
    LassoView v = view();
    assertTrue(v.getContentDescription().toString().contains("lepas"));
    assertTrue(v.getContentDescription().toString().contains("×"));
    Bitmap b = Bitmap.createBitmap(1080, 520, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(b);
    canvas.drawColor(0xff253344);
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    p.setColor(0xffb0bdcd);
    p.setTextSize(22);
    canvas.drawText("SIMULASI TAMPILAN · BUKAN UJI GAME / OCR", 32, 490, p);
    p.setColor(Color.WHITE);
    p.setTextSize(36);
    canvas.drawText("The door is open. Let's go!", 80, 350, p);
    v.draw(canvas);
    java.io.File file = new java.io.File("build/reports/ui/selection-compact.png");
    file.getParentFile().mkdirs();
    try (java.io.FileOutputStream stream = new java.io.FileOutputStream(file)) {
      b.compress(Bitmap.CompressFormat.PNG, 100, stream);
    } finally {
      b.recycle();
    }
  }
}
