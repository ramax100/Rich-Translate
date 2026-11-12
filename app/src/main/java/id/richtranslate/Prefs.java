package id.richtranslate;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
  static final String[] NAMES = {
    "Inggris", "Indonesia", "Spanyol", "Prancis", "Jerman", "Italia", "Portugis", "Turki"
  };
  static final String[] CODES = {"en", "id", "es", "fr", "de", "it", "pt", "tr"};

  static SharedPreferences get(Context c) {
    return c.getSharedPreferences("settings", Context.MODE_PRIVATE);
  }

  static String source(Context c) {
    return get(c).getString("source", "en");
  }

  static String target(Context c) {
    return get(c).getString("target", "id");
  }

  /**
   * Remove retired remote settings without touching languages, offline models or Accessibility
   * consent.
   */
  static void migrateOfflineSettings(Context context) {
    SharedPreferences prefs = get(context);
    if (prefs.contains("engine")
        || prefs.contains("argos_url")
        || prefs.contains("argos_key")
        || prefs.contains("argos_consent"))
      prefs
          .edit()
          .remove("engine")
          .remove("argos_url")
          .remove("argos_key")
          .remove("argos_consent")
          .remove("last_error")
          .apply();
  }
}
