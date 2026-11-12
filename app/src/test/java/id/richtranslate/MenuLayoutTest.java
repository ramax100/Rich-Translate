package id.richtranslate;

import static org.junit.Assert.*;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, qualifiers = "w393dp-h851dp-xhdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class MenuLayoutTest {
  Context context = RuntimeEnvironment.getApplication();
  org.robolectric.android.controller.ActivityController<MainActivity> controller;
  MainActivity activity;

  @Before
  public void setup() {
    Prefs.get(context).edit().clear().commit();
    Shadows.shadowOf((Application) context)
        .grantPermissions("id.richtranslate.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");
    controller = Robolectric.buildActivity(MainActivity.class).setup();
    activity = controller.get();
  }

  @After
  public void cleanup() {
    controller.pause().stop().destroy();
    Prefs.get(context).edit().clear().commit();
  }

  private View tagged(String tag) {
    return activity.getWindow().getDecorView().findViewWithTag(tag);
  }

  private void set(String key, Object value) throws Exception {
    Field field = MainActivity.class.getDeclaredField(key);
    field.setAccessible(true);
    field.set(activity, value);
  }

  private void refresh() throws Exception {
    Method method = MainActivity.class.getDeclaredMethod("refresh");
    method.setAccessible(true);
    method.invoke(activity);
  }

  private void measure() {
    View decor = activity.getWindow().getDecorView();
    int width = activity.getResources().getDisplayMetrics().widthPixels,
        height = activity.getResources().getDisplayMetrics().heightPixels;
    decor.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    decor.layout(0, 0, width, height);
  }

  private void saveScreenshot(String name) {
    measure();
    View decor = activity.getWindow().getDecorView();
    android.graphics.Bitmap bitmap =
        android.graphics.Bitmap.createBitmap(
            decor.getWidth(), decor.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
    decor.draw(new android.graphics.Canvas(bitmap));
    java.io.File file = new java.io.File("build/reports/ui/" + name + ".png");
    file.getParentFile().mkdirs();
    try (java.io.FileOutputStream stream = new java.io.FileOutputStream(file)) {
      bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream);
    } catch (java.io.IOException error) {
      throw new RuntimeException(error);
    } finally {
      bitmap.recycle();
    }
  }

  @Test
  public void mainTabShowsOnlyPrimaryControls() {
    assertEquals(View.VISIBLE, tagged("home_panel").getVisibility());
    assertEquals(View.GONE, tagged("settings_panel").getVisibility());
    assertTrue(tagged("home_tab").isSelected());
    assertEquals(View.VISIBLE, tagged("start_button").getVisibility());
    assertEquals(View.GONE, tagged("stop_button").getVisibility());
  }

  @Test
  public void tabsSeparateSettingsWithoutLosingControls() {
    tagged("settings_tab").performClick();
    assertEquals(View.GONE, tagged("home_panel").getVisibility());
    assertEquals(View.VISIBLE, tagged("settings_panel").getVisibility());
    assertTrue(tagged("settings_tab").isSelected());
    saveScreenshot("settings");
    tagged("home_tab").performClick();
    assertEquals(View.VISIBLE, tagged("home_panel").getVisibility());
  }

  @Test
  public void preferencesStillPersist() {
    tagged("settings_tab").performClick();
    Switch wifi = (Switch) tagged("wifi");
    assertTrue(wifi.isChecked());
    wifi.setChecked(false);
    assertFalse(Prefs.get(context).getBoolean("wifi", true));
    assertNotNull(tagged("secure"));
  }

  @Test
  public void selectedTabSurvivesActivityRecreation() {
    tagged("settings_tab").performClick();
    Bundle state = new Bundle();
    controller.saveInstanceState(state).pause().stop().destroy();
    controller =
        Robolectric.buildActivity(MainActivity.class)
            .create(state)
            .start()
            .restoreInstanceState(state)
            .resume()
            .visible();
    activity = controller.get();
    assertEquals(View.VISIBLE, tagged("settings_panel").getVisibility());
    assertTrue(tagged("settings_tab").isSelected());
  }

  @Test
  public void readyModelDoesNotRepeatDownloadAction() throws Exception {
    set("modelsReady", true);
    refresh();
    assertEquals(View.GONE, tagged("download_button").getVisibility());
    set("modelsReady", false);
    refresh();
    assertEquals(View.VISIBLE, tagged("download_button").getVisibility());
  }

  @Test
  public void downloadingDisablesLanguageAndStartControls() throws Exception {
    set("downloading", true);
    refresh();
    assertFalse(tagged("start_button").isEnabled());
    assertFalse(tagged("download_button").isEnabled());
    assertFalse(tagged("source").isEnabled());
    assertFalse(tagged("target").isEnabled());
  }

  @Test
  @Config(qualifiers = "w320dp-h640dp-mdpi")
  public void narrowScreenStacksLanguagePickers() {
    measure();
    assertEquals(LinearLayout.VERTICAL, ((LinearLayout) tagged("language_pair")).getOrientation());
    assertTrue(tagged("start_button").getWidth() > 0);
    assertTrue(tagged("start_button").getHeight() >= 48);
  }

  @Test
  public void headerHelpAndSettingsHelpRemainAvailable() {
    assertNotNull(tagged("help_button"));
    assertNotNull(tagged("about_button"));
  }
}
