package id.richtranslate;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, qualifiers = "w540dp-h260dp-xhdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class SoftBackgroundTest {
  Context context = RuntimeEnvironment.getApplication();

  private TranslationCard card() {
    Rect source = new Rect(100, 100, 500, 160);
    TextPlacement.Result p =
        TextPlacement.place(context, "Buka pintu", source, List.of(source), 1080, 520, 35, 520);
    TranslationCard c = new TranslationCard(context, "Buka pintu", p);
    c.measure(
        View.MeasureSpec.makeMeasureSpec(p.bounds.width(), View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(p.bounds.height(), View.MeasureSpec.EXACTLY));
    c.layout(0, 0, p.bounds.width(), p.bounds.height());
    return c;
  }

  private Bitmap render(TranslationCard c) {
    Bitmap b = Bitmap.createBitmap(c.getWidth(), c.getHeight(), Bitmap.Config.ARGB_8888);
    c.draw(new Canvas(b));
    return b;
  }

  @Test
  public void blankSourceStripIsWhiteButNotOpaque() {
    TranslationCard c = card();
    Bitmap b = render(c);
    int pixel = b.getPixel(b.getWidth() - 30, b.getHeight() / 2);
    assertEquals(TranslationCard.BACKGROUND_ALPHA, Color.alpha(pixel));
    assertEquals(255, Color.red(pixel));
    assertEquals(255, Color.green(pixel));
    assertEquals(255, Color.blue(pixel));
    b.recycle();
  }

  @Test
  public void sourceAndTranslatedOverlapDoNotBecomeDoubleOpaque() {
    TranslationCard c = card();
    TextPlacement.Result p = c.placement;
    Rect glyphs = new Rect();
    p.layout.getPaint().getTextBounds(c.translatedText, 0, c.translatedText.length(), glyphs);
    Bitmap b = render(c);
    int x = Math.round(p.textX + 20),
        y = (int) Math.ceil(p.textY + p.layout.getLineBaseline(0) + glyphs.bottom + 1);
    int alpha = Color.alpha(b.getPixel(x, y));
    assertTrue(alpha >= 220 && alpha < 245);
    b.recycle();
  }

  @Test
  public void outlineHasLighterFeatherThanInterior() {
    TranslationCard c = card();
    Bitmap b = render(c);
    int edge = Color.alpha(b.getPixel(1, b.getHeight() / 2)),
        inside = Color.alpha(b.getPixel(12, b.getHeight() - 16));
    assertTrue(edge > 0 && edge < 80);
    assertTrue(inside > 220);
    b.recycle();
  }

  @Test
  public void textIsNotBlurredOrMadeTransparent() {
    TranslationCard c = card();
    assertEquals(1f, c.getAlpha(), .001);
    assertEquals(255, c.placement.layout.getPaint().getAlpha());
    assertEquals(Color.rgb(20, 20, 20), c.placement.layout.getPaint().getColor());
    assertNull(c.placement.layout.getPaint().getMaskFilter());
    assertNull(c.placement.layout.getPaint().getShader());
    Bitmap b = render(c);
    int solidText = 0;
    for (int y = 0; y < b.getHeight(); y++)
      for (int x = 0; x < b.getWidth(); x++) {
        int color = b.getPixel(x, y);
        if (Color.alpha(color) == 255
            && Color.red(color) <= 25
            && Color.green(color) <= 25
            && Color.blue(color) <= 25) solidText++;
      }
    assertTrue(solidText > 20);
    b.recycle();
  }

  @Test
  public void noSoftwareLayerOrScreenshotBufferAdded() {
    TranslationCard c = card();
    assertEquals(View.LAYER_TYPE_NONE, c.getLayerType());
    for (java.lang.reflect.Field f : TranslationCard.class.getDeclaredFields())
      assertNotEquals(Bitmap.class, f.getType());
  }

  @Test
  public void repeatedDrawDoesNotAnimateOrChangeBackground() {
    TranslationCard c = card();
    Bitmap a = render(c), b = render(c);
    assertTrue(a.sameAs(b));
    a.recycle();
    b.recycle();
  }

  @Test
  public void whiteTintMaintainsHighContrastOnDarkScene() {
    TranslationCard c = card();
    Bitmap b = Bitmap.createBitmap(c.getWidth(), c.getHeight(), Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(b);
    canvas.drawColor(Color.BLACK);
    c.draw(canvas);
    int light = Color.red(b.getPixel(b.getWidth() - 30, b.getHeight() / 2));
    assertTrue(light >= 225);
    b.recycle();
  }
}
