package id.richtranslate;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;
import org.json.*;

/** Fixed official HTTPS endpoints. Errors never echo server bodies, request text or credentials. */
final class DeepLApi implements AutoCloseable {
  static final int MAX_BODY = 128 * 1024, MAX_RESPONSE = 1024 * 1024;

  interface Connections {
    HttpsURLConnection open(URL url) throws IOException;
  }

  static final class Failure extends Exception {
    Failure(String message) {
      super(message);
    }
  }

  private final Connections connections;
  private volatile String key;
  private volatile HttpsURLConnection active;
  private volatile boolean closed;

  DeepLApi(String key) {
    this(key, url -> (HttpsURLConnection) url.openConnection());
  }

  DeepLApi(String key, Connections connections) {
    this.key = validateKey(key);
    this.connections = connections;
  }

  static String validateKey(String key) {
    if (key == null) throw new IllegalArgumentException("Masukkan API key DeepL.");
    String value = key.trim();
    if (value.isEmpty() || value.length() > 256)
      throw new IllegalArgumentException("API key DeepL tidak valid.");
    for (int i = 0; i < value.length(); i++)
      if (value.charAt(i) <= 32 || value.charAt(i) >= 127)
        throw new IllegalArgumentException(
            "API key DeepL tidak boleh berisi spasi atau baris baru.");
    return value;
  }

  static String host(String key) {
    return validateKey(key).endsWith(":fx")
        ? "https://api-free.deepl.com"
        : "https://api.deepl.com";
  }

  static String language(String code, boolean target) {
    if (code == null) throw new IllegalArgumentException("Bahasa tidak didukung.");
    switch (code) {
      case "en":
        return target ? "EN-US" : "EN";
      case "pt":
        return target ? "PT-PT" : "PT";
      case "id":
      case "es":
      case "fr":
      case "de":
      case "it":
      case "tr":
        return code.toUpperCase(Locale.ROOT);
      default:
        throw new IllegalArgumentException("Bahasa tidak didukung.");
    }
  }

  static byte[] body(List<String> texts, String source, String target) throws Failure {
    if (texts == null || texts.isEmpty() || texts.size() > 50)
      throw new Failure("Pilih 1–50 blok teks untuk DeepL.");
    try {
      JSONArray values = new JSONArray();
      for (String value : texts) {
        if (value == null || value.trim().isEmpty())
          throw new Failure("Blok teks kosong tidak dikirim ke DeepL.");
        values.put(value);
      }
      JSONObject request =
          new JSONObject()
              .put("text", values)
              .put("source_lang", language(source, false))
              .put("target_lang", language(target, true));
      byte[] data = request.toString().getBytes(StandardCharsets.UTF_8);
      if (data.length > MAX_BODY)
        throw new Failure(
            "Teks terlalu besar untuk DeepL (maksimal request 128 KiB). Pilih area lebih kecil.");
      return data;
    } catch (JSONException | IllegalArgumentException e) {
      throw new Failure("Bahasa atau format permintaan DeepL tidak valid.");
    }
  }

  List<String> translate(List<String> values, String source, String target) throws Failure {
    byte[] data = body(values, source, target);
    String response = request("POST", "/v2/translate", data);
    try {
      JSONArray translations = new JSONObject(response).getJSONArray("translations");
      if (translations.length() != values.size()) throw new JSONException("count");
      List<String> result = new ArrayList<>();
      for (int i = 0; i < translations.length(); i++) {
        Object value = translations.getJSONObject(i).get("text");
        if (!(value instanceof String) || ((String) value).trim().isEmpty())
          throw new JSONException("text");
        result.add((String) value);
      }
      return result;
    } catch (JSONException e) {
      throw new Failure("Respons DeepL tidak valid. Tidak ada hasil yang ditampilkan.");
    }
  }

  String checkUsage() throws Failure {
    String response = request("GET", "/v2/usage", null);
    try {
      JSONObject usage = new JSONObject(response);
      if (usage.has("character_count") && usage.has("character_limit")) {
        long used = usage.getLong("character_count"), limit = usage.getLong("character_limit");
        if (used < 0 || limit < 0) throw new JSONException("negative");
        return "Koneksi DeepL berhasil · penggunaan "
            + used
            + " / "
            + limit
            + " karakter. Rincian tagihan lihat akun DeepL.";
      }
      if (usage.optJSONArray("products") != null)
        return "Koneksi DeepL Pro berhasil. Penggunaan dan batas tiap produk tersedia di akun"
            + " DeepL.";
      throw new JSONException("schema");
    } catch (JSONException e) {
      throw new Failure("Respons penggunaan DeepL tidak valid.");
    }
  }

  static String statusMessage(int status) {
    switch (status) {
      case 400:
        return "Permintaan atau pasangan bahasa DeepL tidak didukung (400).";
      case 401:
      case 403:
        return "API key DeepL ditolak (403). Periksa key API Free/Pro dan status akun.";
      case 413:
      case 414:
        return "Teks terlalu besar untuk DeepL. Pilih area lebih kecil.";
      case 429:
        return "Terlalu banyak permintaan DeepL (429). Auto dihentikan; tunggu lalu coba kembali.";
      case 456:
        return "Kuota atau batas biaya DeepL habis (456). Periksa akun DeepL.";
      default:
        return status >= 500
            ? "Layanan DeepL sedang bermasalah. Coba kembali nanti."
            : "Permintaan DeepL gagal (HTTP " + status + ").";
    }
  }

  private String request(String method, String path, byte[] data) throws Failure {
    if (closed || Thread.currentThread().isInterrupted())
      throw new Failure("Permintaan DeepL dibatalkan.");
    HttpsURLConnection connection = null;
    try {
      String auth = key;
      connection = connections.open(new URL(host(auth) + path));
      active = connection;
      if (closed) throw new Failure("Permintaan DeepL dibatalkan.");
      connection.setInstanceFollowRedirects(false);
      connection.setConnectTimeout(3000);
      connection.setReadTimeout(6000);
      connection.setRequestMethod(method);
      connection.setRequestProperty("Authorization", "DeepL-Auth-Key " + auth);
      connection.setRequestProperty("Accept", "application/json");
      if (data != null) {
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setFixedLengthStreamingMode(data.length);
        try (OutputStream out = connection.getOutputStream()) {
          out.write(data);
        }
      }
      int status = connection.getResponseCode();
      if (status != 200) throw new Failure(statusMessage(status));
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (InputStream in = connection.getInputStream()) {
        byte[] buffer = new byte[4096];
        int size;
        while ((size = in.read(buffer)) != -1) {
          if (closed || Thread.currentThread().isInterrupted())
            throw new Failure("Permintaan DeepL dibatalkan.");
          if (bytes.size() + size > MAX_RESPONSE) throw new Failure("Respons DeepL terlalu besar.");
          bytes.write(buffer, 0, size);
        }
      }
      if (closed) throw new Failure("Permintaan DeepL dibatalkan.");
      return bytes.toString(StandardCharsets.UTF_8.name());
    } catch (SocketTimeoutException e) {
      throw new Failure("Koneksi DeepL melewati batas waktu. Periksa internet lalu coba kembali.");
    } catch (IOException | IllegalArgumentException e) {
      throw new Failure(
          "Tidak dapat terhubung ke DeepL. Periksa internet, key tersimpan, atau coba kembali"
              + " nanti.");
    } finally {
      if (connection != null) connection.disconnect();
      active = null;
    }
  }

  @Override
  public void close() {
    closed = true;
    key = "";
    HttpsURLConnection connection = active;
    if (connection != null) connection.disconnect();
  }
}
