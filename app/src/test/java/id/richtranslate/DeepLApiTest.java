package id.richtranslate;

import static org.junit.Assert.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.*;
import javax.net.ssl.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class DeepLApiTest {
  static final String FAKE = "unit-test-not-a-real-deepl-key:fx";

  static class Fake extends HttpsURLConnection {
    int status = 200, calls;
    boolean disconnected, timeout;
    String response = "{\"translations\":[{\"text\":\"Buka pintu\"}]}";
    ByteArrayOutputStream sent = new ByteArrayOutputStream();

    Fake(URL url) {
      super(url);
    }

    @Override
    public void connect() {}

    @Override
    public void disconnect() {
      disconnected = true;
    }

    @Override
    public boolean usingProxy() {
      return false;
    }

    @Override
    public int getResponseCode() throws IOException {
      calls++;
      if (timeout) throw new SocketTimeoutException("SECRET " + FAKE);
      return status;
    }

    @Override
    public InputStream getInputStream() {
      return new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public OutputStream getOutputStream() {
      return sent;
    }

    @Override
    public String getCipherSuite() {
      return "test";
    }

    @Override
    public Certificate[] getLocalCertificates() {
      return null;
    }

    @Override
    public Certificate[] getServerCertificates() {
      return null;
    }

    @Override
    public Principal getPeerPrincipal() {
      return null;
    }

    @Override
    public Principal getLocalPrincipal() {
      return null;
    }
  }

  Fake[] last = new Fake[1];

  DeepLApi api(int status, String response) {
    return new DeepLApi(
        FAKE,
        url -> {
          Fake f = new Fake(url);
          f.status = status;
          if (response != null) f.response = response;
          last[0] = f;
          return f;
        });
  }

  @Test
  public void selectsOnlyOfficialFreeAndProHosts() {
    assertEquals("https://api-free.deepl.com", DeepLApi.host(FAKE));
    assertEquals("https://api.deepl.com", DeepLApi.host("unit-test-not-a-real-pro-key"));
  }

  @Test
  public void buildsUtf8JsonAndLanguageVariants() throws Exception {
    JSONObject body =
        new JSONObject(
            new String(
                DeepLApi.body(List.of("Café\nOpen \"door\""), "en", "id"), StandardCharsets.UTF_8));
    assertEquals("Café\nOpen \"door\"", body.getJSONArray("text").getString(0));
    assertEquals("EN", body.getString("source_lang"));
    assertEquals("ID", body.getString("target_lang"));
    assertEquals("EN-US", DeepLApi.language("en", true));
    assertEquals("PT-PT", DeepLApi.language("pt", true));
    assertEquals("PT", DeepLApi.language("pt", false));
    assertFalse(body.has("api_key"));
  }

  @Test
  public void postsAuthenticatedRequestWithoutRedirects() throws Exception {
    DeepLApi a = api(200, null);
    assertEquals(List.of("Buka pintu"), a.translate(List.of("Open door"), "en", "id"));
    Fake f = last[0];
    assertEquals("POST", f.getRequestMethod());
    assertEquals("https://api-free.deepl.com/v2/translate", f.getURL().toString());
    assertEquals("DeepL-Auth-Key " + FAKE, f.getRequestProperty("Authorization"));
    assertFalse(f.getInstanceFollowRedirects());
    assertEquals(3000, f.getConnectTimeout());
    assertEquals(6000, f.getReadTimeout());
    assertTrue(f.disconnected);
    assertFalse(f.sent.toString().contains(FAKE));
  }

  @Test
  public void returnsTranslationsInRequestedOrder() throws Exception {
    DeepLApi a = api(200, "{\"translations\":[{\"text\":\"Satu\"},{\"text\":\"Dua\"}]}");
    assertEquals(List.of("Satu", "Dua"), a.translate(List.of("One", "Two"), "en", "id"));
  }

  @Test
  public void usageDoesNotSendDialogueOrTranslationRequest() throws Exception {
    DeepLApi a = api(200, "{\"character_count\":10,\"character_limit\":1000}");
    assertTrue(a.checkUsage().contains("10 / 1000"));
    assertEquals("GET", last[0].getRequestMethod());
    assertEquals("/v2/usage", last[0].getURL().getPath());
    assertEquals(0, last[0].sent.size());
  }

  @Test
  public void proUsageSchemaAcceptedWithoutInventingQuota() throws Exception {
    DeepLApi a = api(200, "{\"products\":[{\"product_type\":\"translate\"}]}");
    assertTrue(a.checkUsage().contains("Pro"));
  }

  @Test
  public void allSupportedLanguageCodesMap() {
    for (String code : Prefs.CODES) {
      assertFalse(DeepLApi.language(code, true).isEmpty());
      assertFalse(DeepLApi.language(code, false).isEmpty());
    }
  }

  @Test
  public void rejectsHeaderInjection() {
    try {
      DeepLApi.validateKey("key\r\nAuthorization: other");
      fail();
    } catch (IllegalArgumentException expected) {
      assertFalse(expected.getMessage().contains("other"));
    }
  }

  @Test
  public void rejectsEmptyAndOverlongKeys() {
    for (String key : new String[] {"", "   ", "x".repeat(257)}) {
      try {
        DeepLApi.validateKey(key);
        fail();
      } catch (IllegalArgumentException expected) {
      }
    }
  }

  @Test
  public void validatesRequestBeforeConnecting() throws Exception {
    DeepLApi a = api(200, null);
    try {
      a.translate(List.of("x".repeat(DeepLApi.MAX_BODY)), "en", "id");
      fail();
    } catch (DeepLApi.Failure expected) {
      assertTrue(expected.getMessage().contains("128"));
    }
    assertNull(last[0]);
  }

  @Test
  public void rejectsOverFiftyAndEmptyBlocks() throws Exception {
    for (List<String> values :
        List.of(Collections.<String>emptyList(), List.of(""), Collections.nCopies(51, "x"))) {
      try {
        DeepLApi.body(values, "en", "id");
        fail();
      } catch (DeepLApi.Failure expected) {
      }
    }
  }

  @Test
  public void rejectsUnsupportedLanguageBeforeConnecting() throws Exception {
    DeepLApi a = api(200, null);
    try {
      a.translate(List.of("Hello"), "xx", "id");
      fail();
    } catch (DeepLApi.Failure expected) {
    }
    assertNull(last[0]);
  }

  @Test
  public void quotaAuthRateAndServerErrorsAreSafeAndNotRetried() throws Exception {
    for (int status : new int[] {400, 403, 413, 429, 456, 500, 504, 529}) {
      DeepLApi a = api(status, FAKE + " SECRET DIALOGUE");
      try {
        a.translate(List.of("PRIVATE TEXT"), "en", "id");
        fail();
      } catch (DeepLApi.Failure e) {
        assertFalse(e.getMessage().contains(FAKE));
        assertFalse(e.getMessage().contains("PRIVATE"));
        assertFalse(e.getMessage().contains("SECRET"));
      }
      assertEquals(1, last[0].calls);
      assertTrue(last[0].disconnected);
    }
  }

  @Test
  public void rejectsRedirectInsteadOfSendingKeyToNewHost() throws Exception {
    DeepLApi a = api(302, null);
    try {
      a.translate(List.of("Hello"), "en", "id");
      fail();
    } catch (DeepLApi.Failure e) {
      assertTrue(e.getMessage().contains("302"));
    }
    assertFalse(last[0].getInstanceFollowRedirects());
    assertEquals(1, last[0].calls);
  }

  @Test
  public void responseCountAndTypeMustMatch() throws Exception {
    for (String response :
        new String[] {
          "invalid",
          "{}",
          "{\"translations\":[]}",
          "{\"translations\":[{\"text\":123}]}",
          "{\"translations\":[{\"text\":\"\"}]}"
        }) {
      DeepLApi a = api(200, response);
      try {
        a.translate(List.of("Hello"), "en", "id");
        fail();
      } catch (DeepLApi.Failure e) {
        assertTrue(e.getMessage().contains("Respons"));
      }
    }
  }

  @Test
  public void boundedResponseAvoidsUnboundedAllocation() throws Exception {
    DeepLApi a = api(200, "x".repeat(DeepLApi.MAX_RESPONSE + 1));
    try {
      a.translate(List.of("Hello"), "en", "id");
      fail();
    } catch (DeepLApi.Failure e) {
      assertTrue(e.getMessage().contains("terlalu besar"));
    }
  }

  @Test
  public void timeoutDoesNotEchoSensitiveException() throws Exception {
    DeepLApi a =
        new DeepLApi(
            FAKE,
            url -> {
              Fake f = new Fake(url);
              f.timeout = true;
              last[0] = f;
              return f;
            });
    try {
      a.translate(List.of("Hello"), "en", "id");
      fail();
    } catch (DeepLApi.Failure e) {
      assertTrue(e.getMessage().contains("waktu"));
      assertFalse(e.getMessage().contains(FAKE));
    }
    assertTrue(last[0].disconnected);
  }

  @Test
  public void closePreventsNetworkUse() throws Exception {
    DeepLApi a = api(200, null);
    a.close();
    try {
      a.translate(List.of("Hello"), "en", "id");
      fail();
    } catch (DeepLApi.Failure expected) {
    }
    assertNull(last[0]);
  }
}
