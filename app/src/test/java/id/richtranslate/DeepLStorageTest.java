package id.richtranslate;

import static org.junit.Assert.*;

import android.content.Context;
import java.security.GeneralSecurityException;
import javax.crypto.spec.SecretKeySpec;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class DeepLStorageTest {
  Context context = RuntimeEnvironment.getApplication();
  final String fake = "unit-test-only-not-a-real-key:fx";
  DeepLKeyStore keys;

  @Before
  public void setup() {
    Prefs.get(context).edit().clear().commit();
    keys = new DeepLKeyStore(context, () -> new SecretKeySpec(new byte[32], "AES"));
  }

  @After
  public void clean() {
    Prefs.get(context).edit().clear().commit();
  }

  @Test
  public void ciphertextRoundTripsWithoutPlaintextPreference() throws Exception {
    keys.save(fake);
    assertTrue(keys.hasKey());
    assertEquals(fake, keys.read());
    assertFalse(Prefs.get(context).getAll().toString().contains(fake));
    assertTrue(Prefs.get(context).contains("deepl_key_iv"));
  }

  @Test
  public void randomizedIvChangesCiphertext() throws Exception {
    keys.save(fake);
    String first = Prefs.get(context).getString("deepl_key_cipher", "");
    keys.save(fake);
    assertNotEquals(first, Prefs.get(context).getString("deepl_key_cipher", ""));
    assertEquals(fake, keys.read());
  }

  @Test
  public void failedKeystoreNeverFallsBackToPlaintext() throws Exception {
    DeepLKeyStore broken =
        new DeepLKeyStore(
            context,
            () -> {
              throw new GeneralSecurityException("SECRET " + fake);
            });
    try {
      broken.save(fake);
      fail();
    } catch (GeneralSecurityException e) {
      assertFalse(e.getMessage().contains(fake));
    }
    assertFalse(broken.hasKey());
    assertTrue(Prefs.get(context).getAll().isEmpty());
  }

  @Test
  public void cannotDecryptWithAnotherDeviceKey() throws Exception {
    keys.save(fake);
    byte[] other = new byte[32];
    other[0] = 1;
    DeepLKeyStore another = new DeepLKeyStore(context, () -> new SecretKeySpec(other, "AES"));
    try {
      another.read();
      fail();
    } catch (GeneralSecurityException e) {
      assertTrue(e.getMessage().contains("simpan ulang"));
      assertFalse(e.getMessage().contains(fake));
    }
  }

  @Test
  public void tamperedCiphertextDoesNotReturnText() throws Exception {
    keys.save(fake);
    Prefs.get(context).edit().putString("deepl_key_cipher", "not-valid-ciphertext").commit();
    try {
      keys.read();
      fail();
    } catch (GeneralSecurityException e) {
      assertFalse(e.getMessage().contains("not-valid-ciphertext"));
    }
  }

  @Test
  public void clearRemovesKeyConsentAndSelectsLocal() throws Exception {
    keys.save(fake);
    Prefs.get(context)
        .edit()
        .putString("translation_provider", "deepl")
        .putBoolean("deepl_consent", true)
        .putBoolean("auto_translate", true)
        .commit();
    keys.clear();
    assertFalse(keys.hasKey());
    assertFalse(Prefs.deepL(context));
    assertFalse(Prefs.get(context).getBoolean("deepl_consent", false));
    assertTrue(Prefs.get(context).getBoolean("auto_translate", false));
  }

  @Test
  public void oldArgosMigrationDoesNotDeleteNewDeepLSettings() throws Exception {
    keys.save(fake);
    Prefs.get(context)
        .edit()
        .putString("engine", "argos")
        .putString("argos_key", "old-test")
        .putString("translation_provider", "deepl")
        .putBoolean("deepl_consent", true)
        .commit();
    Prefs.migrateOfflineSettings(context);
    assertFalse(Prefs.get(context).contains("argos_key"));
    assertTrue(Prefs.deepL(context));
    assertTrue(Prefs.get(context).getBoolean("deepl_consent", false));
    assertEquals(fake, keys.read());
  }

  @Test
  public void missingKeyHasActionableError() {
    try {
      keys.read();
      fail();
    } catch (GeneralSecurityException e) {
      assertTrue(e.getMessage().contains("Setelan"));
    }
  }

  @Test
  public void serverEngineCannotStartWithoutConsentOrStoredKey() {
    Prefs.get(context).edit().putString("translation_provider", "deepl").commit();
    try {
      TranslationEngine.forContext(context);
      fail();
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("Setujui"));
    }
    Prefs.get(context).edit().putBoolean("deepl_consent", true).commit();
    try {
      TranslationEngine.forContext(context);
      fail();
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("key"));
    }
  }
}
