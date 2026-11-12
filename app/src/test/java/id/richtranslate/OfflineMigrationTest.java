package id.richtranslate;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class OfflineMigrationTest {
  private final Context context = RuntimeEnvironment.getApplication();

  @Before
  public void setup() {
    Prefs.get(context).edit().clear().commit();
  }

  @After
  public void cleanup() {
    Prefs.get(context).edit().clear().commit();
  }

  @Test
  public void remoteCredentialsAndSelectorAreRemovedButUserSettingsStay() {
    SharedPreferences prefs = Prefs.get(context);
    prefs
        .edit()
        .putString("engine", "argos")
        .putString("argos_url", "https://example.test")
        .putString("argos_key", "test-not-real-key")
        .putBoolean("argos_consent", true)
        .putString("last_error", "Old server error")
        .putString("source", "fr")
        .putString("target", "id")
        .putBoolean("accessibility_consent", true)
        .putBoolean("secure", true)
        .putBoolean("wifi", false)
        .commit();
    Prefs.migrateOfflineSettings(context);
    for (String key :
        new String[] {"engine", "argos_url", "argos_key", "argos_consent", "last_error"})
      assertFalse(prefs.contains(key));
    assertEquals("fr", Prefs.source(context));
    assertEquals("id", Prefs.target(context));
    assertTrue(prefs.getBoolean("accessibility_consent", false));
    assertTrue(prefs.getBoolean("secure", false));
    assertFalse(prefs.getBoolean("wifi", true));
  }

  @Test
  public void migrationIsIdempotentAndDoesNotRemoveNonServerError() {
    SharedPreferences prefs = Prefs.get(context);
    prefs.edit().putString("last_error", "OCR failed").commit();
    Prefs.migrateOfflineSettings(context);
    Prefs.migrateOfflineSettings(context);
    assertEquals("OCR failed", prefs.getString("last_error", ""));
  }

  @Test
  public void serviceStartupAlsoClearsOldServerCredentials() {
    SharedPreferences prefs = Prefs.get(context);
    prefs.edit().putString("argos_key", "test-not-real-key").commit();
    org.robolectric.android.controller.ServiceController<RichAccessibilityService> controller =
        Robolectric.buildService(RichAccessibilityService.class).create();
    assertFalse(prefs.contains("argos_key"));
    controller.destroy();
  }
}
