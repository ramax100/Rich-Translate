package id.richtranslate;

import static org.junit.Assert.*;

import android.app.*;
import android.content.*;
import android.os.Looper;
import android.text.method.PasswordTransformationMethod;
import android.view.*;
import android.widget.*;
import javax.crypto.spec.SecretKeySpec;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class DeepLSettingsTest {
  Context context = RuntimeEnvironment.getApplication();
  org.robolectric.android.controller.ActivityController<Activity> controller;
  Activity activity;
  DeepLKeyStore keys;
  DeepLSettings panel;
  int[] changes = {0};
  final String fake = "unit-test-only-key-not-real:fx";

  @Before
  public void setup() {
    Prefs.get(context).edit().clear().commit();
    controller = Robolectric.buildActivity(Activity.class).setup();
    activity = controller.get();
    keys = new DeepLKeyStore(activity, () -> new SecretKeySpec(new byte[32], "AES"));
  }

  void build() {
    panel = new DeepLSettings(activity, () -> changes[0]++, keys);
    activity.setContentView(panel);
    panel.measure(
        View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST));
    panel.layout(0, 0, 800, panel.getMeasuredHeight());
    Shadows.shadowOf(Looper.getMainLooper()).idle();
  }

  void chooseRemote() {
    Spinner spinner = panel.findViewWithTag("translation_provider");
    spinner.getOnItemSelectedListener().onItemSelected(spinner, null, 1, 1);
  }

  @After
  public void cleanup() {
    if (panel != null) panel.close();
    controller.pause().stop().destroy();
    Prefs.get(context).edit().clear().commit();
  }

  @Test
  public void providerDefaultsLocalAndKeyFieldIsMaskedUnsavedAndAutofillOff() {
    build();
    assertFalse(Prefs.deepL(activity));
    EditText input = panel.findViewWithTag("deepl_key_input");
    assertTrue(input.getTransformationMethod() instanceof PasswordTransformationMethod);
    assertFalse(input.isSaveEnabled());
    assertEquals(
        View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS, input.getImportantForAutofill());
    assertEquals("", input.getText().toString());
  }

  @Test
  public void providerSelectionRequiresExplicitConsent() {
    build();
    chooseRemote();
    assertFalse(Prefs.deepL(activity));
    AlertDialog dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    assertNotNull(dialog);
    dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertTrue(Prefs.deepL(activity));
    assertTrue(Prefs.get(activity).getBoolean("deepl_consent", false));
    assertEquals(1, changes[0]);
  }

  @Test
  public void declinedConsentDoesNotEnableProvider() {
    build();
    chooseRemote();
    AlertDialog dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertFalse(Prefs.deepL(activity));
    assertFalse(Prefs.get(activity).getBoolean("deepl_consent", false));
  }

  @Test
  public void savedKeyIsNeverPrefilledAndCanBeDeletedWhileLocal() throws Exception {
    keys.save(fake);
    build();
    EditText input = panel.findViewWithTag("deepl_key_input");
    assertEquals("", input.getText().toString());
    Button remove = panel.findViewWithTag("deepl_remove");
    assertEquals(View.VISIBLE, remove.getVisibility());
    remove.performClick();
    assertFalse(keys.hasKey());
    assertFalse(Prefs.deepL(activity));
  }

  @Test
  public void pauseClearsUnsavedKey() {
    build();
    EditText input = panel.findViewWithTag("deepl_key_input");
    input.setText(fake);
    panel.pause();
    assertEquals("", input.getText().toString());
  }

  @Test
  public void activeSessionLocksProviderAndKeyChanges() {
    build();
    panel.refreshControls(true);
    for (String tag :
        new String[] {"translation_provider", "deepl_key_input", "deepl_save", "deepl_test"})
      assertFalse(panel.findViewWithTag(tag).isEnabled());
    chooseRemote();
    assertFalse(Prefs.deepL(activity));
  }

  @Test
  public void savingKeyEncryptsAndClearsInput() throws Exception {
    Prefs.get(activity)
        .edit()
        .putString("translation_provider", "deepl")
        .putBoolean("deepl_consent", true)
        .commit();
    build();
    EditText input = panel.findViewWithTag("deepl_key_input");
    input.setText(fake);
    panel.findViewWithTag("deepl_save").performClick();
    assertEquals(fake, keys.read());
    assertEquals("", input.getText().toString());
    assertFalse(Prefs.get(context).getAll().toString().contains(fake));
    assertTrue(panel.findViewWithTag("deepl_test").isEnabled());
  }
}
