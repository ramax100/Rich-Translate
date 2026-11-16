package id.richtranslate;

import static org.junit.Assert.*;

import android.graphics.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class WindowCaptureFrameTest {
  @Test
  public void displaySizedBufferUsesDisplayOrigin() {
    assertEquals(
        new Rect(0, 0, 1080, 1920),
        WindowCaptureFrame.resolve(1080, 1920, new Rect(0, 60, 1080, 1860), 1080, 1920));
  }

  @Test
  public void windowWithStatusNavigationInsetsDoesNotRequireFullDisplay() {
    Rect w = new Rect(0, 60, 1080, 1860);
    assertEquals(w, WindowCaptureFrame.resolve(1080, 1800, w, 1080, 1920));
  }

  @Test
  public void offsetLandscapeWindowRetainsPhysicalPosition() {
    Rect w = new Rect(100, 40, 1820, 1040);
    assertEquals(w, WindowCaptureFrame.resolve(1720, 1000, w, 1920, 1080));
  }

  @Test
  public void proportionallyScaledWindowIsSupported() {
    Rect w = new Rect(100, 40, 1820, 1040);
    assertEquals(w, WindowCaptureFrame.resolve(860, 500, w, 1920, 1080));
  }

  @Test
  public void unknownGeometryIsRejectedInsteadOfGuessingInsets() {
    assertNull(WindowCaptureFrame.resolve(1000, 400, new Rect(0, 0, 1080, 520), 1080, 520));
  }

  @Test
  public void offscreenOrEmptyWindowRejected() {
    assertNull(WindowCaptureFrame.resolve(1000, 400, new Rect(-10, 0, 990, 400), 1080, 520));
    assertNull(WindowCaptureFrame.resolve(1000, 400, new Rect(), 1080, 520));
    assertNull(WindowCaptureFrame.resolve(0, 400, new Rect(0, 0, 1000, 400), 1080, 520));
  }

  @Test
  public void selectedAreaMustStayInsideGame() {
    Rect frame = new Rect(20, 40, 1000, 500);
    assertTrue(
        WindowCaptureFrame.containsSelection(
            frame, List.of(new float[] {20, 40}, new float[] {900, 450})));
    assertFalse(
        WindowCaptureFrame.containsSelection(
            frame, List.of(new float[] {19, 40}, new float[] {900, 450})));
    assertFalse(WindowCaptureFrame.containsSelection(frame, List.of(new float[] {Float.NaN, 40})));
  }

  @Test
  public void composeWindowPixelsAtOffsetWithoutReadingOverlay() {
    Bitmap window = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888);
    window.eraseColor(Color.RED);
    Bitmap mapped = WindowCaptureFrame.compose(window, new Rect(40, 60, 240, 160), 320, 240);
    assertEquals(320, mapped.getWidth());
    assertEquals(240, mapped.getHeight());
    assertEquals(Color.RED, mapped.getPixel(100, 100));
    assertEquals(Color.WHITE, mapped.getPixel(20, 20));
    assertEquals(Color.WHITE, mapped.getPixel(250, 180));
    assertFalse(window.isRecycled());
    mapped.recycle();
    window.recycle();
  }

  @Test
  public void largeDisplayMappingUsesBounded1920Buffer() {
    Bitmap window = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888);
    window.eraseColor(Color.BLUE);
    Bitmap mapped = WindowCaptureFrame.compose(window, new Rect(400, 200, 800, 400), 2400, 1080);
    assertEquals(1920, mapped.getWidth());
    assertEquals(864, mapped.getHeight());
    assertEquals(Color.BLUE, mapped.getPixel(480, 240));
    assertEquals(Color.WHITE, mapped.getPixel(10, 10));
    mapped.recycle();
    window.recycle();
  }

  @Test
  public void matchingFullFrameDoesNotAllocateSecondCopy() {
    Bitmap source = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888);
    assertSame(source, WindowCaptureFrame.compose(source, new Rect(0, 0, 320, 240), 320, 240));
    source.recycle();
  }

  @Test
  public void noHidePolicyIsMandatoryOnAndroid14EvenWithoutValidTarget() {
    assertFalse(AutoCapturePolicy.hideOverlayForCapture(34, true));
    assertFalse(AutoCapturePolicy.hideOverlayForCapture(35, true));
    assertTrue(AutoCapturePolicy.hideOverlayForCapture(30, true));
    assertTrue(AutoCapturePolicy.hideOverlayForCapture(33, true));
    assertFalse(AutoCapturePolicy.hideOverlayForCapture(30, false));
    assertFalse(AutoCapturePolicy.useWindowCapture(34, -1));
  }

  @Test
  public void windowRetryBudgetAndDelayAreBounded() {
    assertTrue(AutoCapturePolicy.canRetryWindowCapture(1));
    assertTrue(AutoCapturePolicy.canRetryWindowCapture(2));
    assertFalse(AutoCapturePolicy.canRetryWindowCapture(3));
    assertEquals(1200, AutoCapturePolicy.windowRetryDelay(1));
    assertEquals(2400, AutoCapturePolicy.windowRetryDelay(2));
    assertEquals(3600, AutoCapturePolicy.windowRetryDelay(20));
  }
}
