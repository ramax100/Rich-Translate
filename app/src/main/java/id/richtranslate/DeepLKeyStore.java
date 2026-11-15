package id.richtranslate;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** BYOK only. No developer API key, plaintext preference, backup or plaintext fallback. */
final class DeepLKeyStore {
  private static final String ALIAS = "richtranslate.deepl.key.v1";
  private static final String CIPHER = "deepl_key_cipher", IV = "deepl_key_iv";

  interface SecretSource {
    SecretKey get() throws Exception;
  }

  private final Context context;
  private final SecretSource secret;

  DeepLKeyStore(Context context) {
    this(context, DeepLKeyStore::androidKey);
  }

  DeepLKeyStore(Context context, SecretSource secret) {
    this.context = context.getApplicationContext();
    this.secret = secret;
  }

  private static SecretKey androidKey() throws Exception {
    KeyStore store = KeyStore.getInstance("AndroidKeyStore");
    store.load(null);
    if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
    KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
    gen.init(
        new KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build());
    return gen.generateKey();
  }

  boolean hasKey() {
    return Prefs.get(context).contains(CIPHER) && Prefs.get(context).contains(IV);
  }

  void save(String value) throws GeneralSecurityException {
    try {
      String key = DeepLApi.validateKey(value);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, secret.get());
      byte[] encrypted = cipher.doFinal(key.getBytes(StandardCharsets.UTF_8));
      boolean saved =
          Prefs.get(context)
              .edit()
              .putString(CIPHER, Base64.encodeToString(encrypted, Base64.NO_WRAP))
              .putString(IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
              .commit();
      if (!saved) throw new GeneralSecurityException();
    } catch (Exception e) {
      throw new GeneralSecurityException(
          "Key tidak dapat disimpan dengan aman. Tidak ada penyimpanan plaintext.");
    }
  }

  String read() throws GeneralSecurityException {
    if (!hasKey())
      throw new GeneralSecurityException("Masukkan dan simpan API key DeepL di Setelan.");
    try {
      byte[] iv = Base64.decode(Prefs.get(context).getString(IV, ""), Base64.NO_WRAP);
      byte[] data = Base64.decode(Prefs.get(context).getString(CIPHER, ""), Base64.NO_WRAP);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, secret.get(), new GCMParameterSpec(128, iv));
      return DeepLApi.validateKey(new String(cipher.doFinal(data), StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new GeneralSecurityException(
          "Key tersimpan tidak dapat dibuka. Hapus dan simpan ulang API key DeepL.");
    }
  }

  void clear() {
    Prefs.get(context)
        .edit()
        .remove(CIPHER)
        .remove(IV)
        .remove("deepl_consent")
        .putString("translation_provider", "mlkit")
        .commit();
  }
}
