package id.richtranslate;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.hardware.HardwareBuffer;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.*;

/** User-selected screenshot + OCR, optionally monitored. No gestures or node-tree reads. */
public class RichAccessibilityService extends AccessibilityService {
  static final String STATE_CHANGED = "id.richtranslate.STATE_CHANGED";
  private static volatile java.lang.ref.WeakReference<RichAccessibilityService> connection =
      new java.lang.ref.WeakReference<>(null);

  static RichAccessibilityService connectedService() {
    return connection.get();
  }

  enum Stage {
    IDLE,
    BALL,
    SELECTING,
    PROCESSING,
    RESULT
  }

  private final Handler main = new Handler(Looper.getMainLooper());
  private WindowManager wm;
  private View ball, selector;
  private OverlayHost overlay;
  private WindowManager.LayoutParams ballParams;
  private final java.util.concurrent.ExecutorService screenshotWorker =
      java.util.concurrent.Executors.newSingleThreadExecutor();
  private boolean screenshotPending;
  private int screenshotRequest;
  private TranslationEngine translator;
  private TextRecognizer recognizer;
  private Stage stage = Stage.IDLE;
  private volatile boolean active, destroying;
  private boolean secure, screenReceiverRegistered;
  private int width, height;
  private volatile int generation;
  private long lastScreenshotAt;
  private boolean automatic, autoBusy, hiddenForCapture;
  private View autoClose;
  private List<float[]> monitoredArea;
  private final AutoTextTracker autoTracker = new AutoTextTracker();
  private List<Patch> pendingPatches;
  private String pendingText = "";
  private int targetWindow = -1;
  private int autoCycle, windowCaptureFailures;
  private Runnable nextScan, jobTimeout;

  void disableAuto() {
    if (automatic && active) resetToBall();
  }

  private void cancelAuto() {
    if (nextScan != null) main.removeCallbacks(nextScan);
    if (jobTimeout != null) main.removeCallbacks(jobTimeout);
    nextScan = jobTimeout = null;
    automatic = autoBusy = false;
    hiddenForCapture = false;
    monitoredArea = null;
    pendingPatches = null;
    pendingText = "";
    targetWindow = -1;
    windowCaptureFailures = 0;
    autoTracker.reset();
  }

  int focusedApplicationWindow() {
    List<AccessibilityWindowInfo> windows = getWindows();
    try {
      for (AccessibilityWindowInfo window : windows)
        if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION && window.isFocused())
          return window.getId();
      return -1;
    } finally {
      for (AccessibilityWindowInfo window : windows) window.recycle();
    }
  }

  Rect applicationWindowBounds(int id) {
    List<AccessibilityWindowInfo> windows = getWindows();
    try {
      for (AccessibilityWindowInfo window : windows)
        if (window.getId() == id && window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
          Rect bounds = new Rect();
          window.getBoundsInScreen(bounds);
          return bounds;
        }
      return null;
    } finally {
      for (AccessibilityWindowInfo window : windows) window.recycle();
    }
  }

  void captureWindow(int windowId, TakeScreenshotCallback callback) {
    if (Build.VERSION.SDK_INT >= 34) takeScreenshotOfWindow(windowId, getMainExecutor(), callback);
    else throw new UnsupportedOperationException("Screenshot jendela memerlukan Android 14+");
  }

  void captureDisplay(TakeScreenshotCallback callback) {
    takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), callback);
  }

  private void windowCaptureFailed(int epoch, String message) {
    if (!valid(epoch) || !automatic) return;
    screenshotPending = false;
    generation++; // Cancel conversion/OCR deliveries already queued for this failed scan.
    screenshotRequest++; // Invalidate late results, including a timed-out API34 callback.
    if (AutoCapturePolicy.canRetryWindowCapture(++windowCaptureFailures)) {
      scheduleAuto(AutoCapturePolicy.windowRetryDelay(windowCaptureFailures));
    } else
      fail(
          message
              + " Auto dihentikan, bukan beralih ke scan yang menyembunyikan hasil. Gunakan mode"
              + " manual atau pilih area kembali.",
          null);
  }

  private void scheduleAuto() {
    scheduleAuto(AutoCapturePolicy.SCAN_DELAY_MS);
  }

  private void scheduleAuto(long delay) {
    if (!automatic || !active) return;
    autoBusy = false;
    if (jobTimeout != null) main.removeCallbacks(jobTimeout);
    final int epoch = generation;
    if (nextScan != null) main.removeCallbacks(nextScan);
    nextScan =
        () -> {
          if (valid(epoch) && automatic) beginAutoScan();
        };
    main.postDelayed(nextScan, delay);
  }

  private void beginAutoScan() {
    if (!automatic || autoBusy || !active) return;
    if (((KeyguardManager) getSystemService(KEYGUARD_SERVICE)).isKeyguardLocked()) {
      stopSession("Layar terkunci.");
      return;
    }
    int focused = focusedApplicationWindow();
    if (focused < 0 || focused != targetWindow) {
      fail("Jendela game berubah. Pilih kembali area dialog.", null);
      return;
    }
    autoBusy = true;
    final int epoch = ++generation, cycle = ++autoCycle;
    stage = Stage.PROCESSING;
    jobTimeout =
        () -> {
          if (valid(epoch) && automatic && autoBusy && cycle == autoCycle)
            fail("Pemindaian otomatis terlalu lama. Periksa model lalu pilih area kembali.", null);
        };
    main.postDelayed(jobTimeout, AutoCapturePolicy.JOB_TIMEOUT_MS);
    if (!AutoCapturePolicy.hideOverlayForCapture(Build.VERSION.SDK_INT, automatic))
      requestScreenshot(epoch, 0, monitoredArea);
    else {
      if (overlay != null) overlay.setVisibility(View.INVISIBLE);
      if (autoClose != null) autoClose.setVisibility(View.INVISIBLE);
      hiddenForCapture = true;
      main.postDelayed(
          () -> {
            if (valid(epoch) && automatic) requestScreenshot(epoch, 0, monitoredArea);
          },
          AutoCapturePolicy.HIDE_SETTLE_MS);
      // Never leave the user's stop control hidden while waiting for a broken capture.
      main.postDelayed(
          () -> {
            if (valid(epoch) && hiddenForCapture)
              fail("Screenshot terlalu lama. Mode otomatis dijeda untuk menjaga tampilan.", null);
          },
          AutoCapturePolicy.HIDE_DEADLINE_MS);
    }
  }

  private void restoreAutoOverlay() {
    hiddenForCapture = false;
    if (overlay != null) overlay.setVisibility(View.VISIBLE);
    if (autoClose != null) autoClose.setVisibility(View.VISIBLE);
  }

  private String signature(List<Patch> patches) {
    StringBuilder value = new StringBuilder();
    for (Patch patch : patches) {
      if (value.length() > 0) value.append(' ');
      value.append(patch.original);
    }
    return AutoTextTracker.normalize(value.toString());
  }

  private boolean handleAutoOcr(List<Patch> patches, int epoch) {
    String text = signature(patches);
    AutoTextTracker.Decision decision = autoTracker.observe(text);
    if (decision.clearOld && overlay != null) overlay.clearResults();
    if (pendingPatches != null) {
      boolean matches = text.equals(pendingText) && patches.size() == pendingPatches.size();
      for (int i = 0; matches && i < patches.size(); i++)
        matches =
            AutoTextTracker.normalize(patches.get(i).original)
                .equals(AutoTextTracker.normalize(pendingPatches.get(i).original));
      if (matches && autoTracker.markDisplayed(text)) {
        for (int i = 0; i < patches.size(); i++)
          patches.get(i).translated = pendingPatches.get(i).translated;
        pendingPatches = null;
        pendingText = "";
        render(patches, epoch);
        scheduleAuto();
        return true;
      }
      pendingPatches = null;
      pendingText = "";
    }
    if (!decision.translate) {
      scheduleAuto();
      return true;
    }
    return false;
  }

  private void translationsReady(List<Patch> patches, int epoch) {
    if (!automatic) {
      render(patches, epoch);
      return;
    }
    pendingPatches = patches;
    pendingText = signature(patches);
    scheduleAuto();
  }

  private final BroadcastReceiver screenOff =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
          stopSession("Layar terkunci. Mulai lagi setelah membuka kunci.");
        }
      };

  private static final class Patch {
    final Rect source;
    final List<Rect> sourceLines;
    final float fontPx;
    String original, translated = "";
    TextPlacement.Result placement;

    Patch(Rect source, String original, List<Rect> sourceLines, float fontPx) {
      this.source = new Rect(source);
      this.original = original;
      this.sourceLines = sourceLines;
      this.fontPx = fontPx;
    }
  }

  @Override
  public void onCreate() {
    super.onCreate();
    Prefs.migrateOfflineSettings(this);
    IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
    if (Build.VERSION.SDK_INT >= 33)
      registerReceiver(screenOff, filter, Context.RECEIVER_NOT_EXPORTED);
    else registerReceiver(screenOff, filter);
    screenReceiverRegistered = true;
  }

  @Override
  protected void onServiceConnected() {
    super.onServiceConnected();
    wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    connection = new java.lang.ref.WeakReference<>(this);
    destroying = false;
    dimensions();
    announce();
    // Never start capture just because Accessibility has been enabled.
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) {
    // Window state only. Never call event.getSource(), getRoot() or read text nodes.
    if (!automatic || !active || targetWindow < 0) return;
    final int target = targetWindow;
    main.postDelayed(
        () -> {
          if (!automatic || !active || targetWindow != target) return;
          int focused = focusedApplicationWindow();
          if (focused >= 0 && focused != target) resetToBall();
        },
        150);
  }

  @Override
  public void onInterrupt() {
    stopSession("Layanan terinterupsi. Tekan Mulai untuk melanjutkan.");
  }

  boolean isActive() {
    return active;
  }

  String currentStage() {
    return stage.name();
  }

  void startSession() {
    if (destroying || wm == null) return;
    if (!Prefs.get(this).getBoolean("accessibility_consent", false)) {
      report("Buka aplikasi dan setujui penjelasan akses screenshot terlebih dahulu.", null);
      return;
    }
    if (((KeyguardManager) getSystemService(KEYGUARD_SERVICE)).isKeyguardLocked()) {
      report("Buka kunci layar terlebih dahulu.", null);
      return;
    }
    stopSession(null);
    try {
      secure = Prefs.get(this).getBoolean("secure", false);
      translator = TranslationEngine.forContext(this);
      recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
      active = true;
      dimensions();
      Prefs.get(this).edit().remove("last_error").apply();
      showBall();
      announce();
    } catch (RuntimeException e) {
      report("Layanan tidak dapat dimulai", e);
      stopSession(null);
    }
  }

  void stopSession(String message) {
    generation++;
    cancelAuto();
    active = false;
    stage = Stage.IDLE;
    // Epoch guards cancel stale work. Keep queued bitmap-delivery callbacks so they
    // can recycle their buffers instead of dropping an owned bitmap from the queue.
    clearVisuals();
    if (translator != null) {
      translator.close();
      translator = null;
    }
    if (recognizer != null) {
      try {
        recognizer.close();
      } catch (RuntimeException error) {
        android.util.Log.w("RichTranslate", "SDK OCR close failed", error);
      } finally {
        recognizer = null;
      }
    }
    if (message != null) Prefs.get(this).edit().putString("last_error", message).apply();
    announce();
  }

  private void announce() {
    sendBroadcast(new Intent(STATE_CHANGED).setPackage(getPackageName()));
  }

  private void dimensions() {
    android.util.DisplayMetrics m = new android.util.DisplayMetrics();
    wm.getDefaultDisplay().getRealMetrics(m);
    width = m.widthPixels;
    height = m.heightPixels;
  }

  // Screenshot coordinates are physical left-to-right even under an RTL locale.
  @android.annotation.SuppressLint("RtlHardcoded")
  private WindowManager.LayoutParams params(int w, int h, boolean focus) {
    int flags =
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
    if (!focus) flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
    if (secure) flags |= WindowManager.LayoutParams.FLAG_SECURE;
    WindowManager.LayoutParams p =
        new WindowManager.LayoutParams(
            w,
            h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT);
    p.gravity = Gravity.TOP | Gravity.LEFT;
    p.layoutInDisplayCutoutMode =
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
    p.setFitInsetsTypes(0);
    p.alpha = 1f;
    p.windowAnimations = 0;
    return p;
  }

  private void showBall() {
    if (!active || destroying) return;
    remove(ball);
    FloatingBallView t = new FloatingBallView(this);
    t.setContentDescription("Terjemahkan teks. Geser untuk pindah; tekan lama untuk berhenti.");
    int size = Ui.dp(this, FloatingBallView.TOUCH_DP);
    ballParams = params(size, size, false);
    ballParams.x = Ui.dp(this, 12);
    ballParams.y = Math.min(height - size - Ui.dp(this, 12), Ui.dp(this, 150));
    ball = t;
    try {
      wm.addView(ball, ballParams);
    } catch (RuntimeException e) {
      ball = null;
      report("Floating ball tidak dapat ditampilkan", e);
      stopSession(null);
      return;
    }
    stage = Stage.BALL;
    t.setOnClickListener(v -> beginSelection());
    t.setOnLongClickListener(
        v -> {
          stopSession(null);
          Toast.makeText(
                  this,
                  "Penerjemah dihentikan. Accessibility tetap tersambung.",
                  Toast.LENGTH_SHORT)
              .show();
          return true;
        });
    t.setOnTouchListener(
        new View.OnTouchListener() {
          float sx, sy;
          int ox, oy;
          boolean moved, held;
          final Runnable hold =
              () -> {
                if (!moved && active && ball == t && stage == Stage.BALL) {
                  held = true;
                  t.performLongClick();
                }
              };

          public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
              case MotionEvent.ACTION_DOWN:
                sx = e.getRawX();
                sy = e.getRawY();
                ox = ballParams.x;
                oy = ballParams.y;
                moved = false;
                held = false;
                main.postDelayed(hold, 650);
                return true;
              case MotionEvent.ACTION_MOVE:
                float dx = e.getRawX() - sx, dy = e.getRawY() - sy;
                if (Math.abs(dx) + Math.abs(dy) > Ui.dp(RichAccessibilityService.this, 8)) {
                  moved = true;
                  main.removeCallbacks(hold);
                }
                if (moved && !held && t.isAttachedToWindow()) {
                  ballParams.x = Math.max(0, Math.min(width - t.getWidth(), ox + (int) dx));
                  ballParams.y = Math.max(0, Math.min(height - t.getHeight(), oy + (int) dy));
                  try {
                    wm.updateViewLayout(t, ballParams);
                  } catch (RuntimeException ignored) {
                  }
                }
                return true;
              case MotionEvent.ACTION_UP:
                main.removeCallbacks(hold);
                if (!moved && !held) v.performClick();
                return true;
              case MotionEvent.ACTION_CANCEL:
                main.removeCallbacks(hold);
                return true;
              default:
                return false;
            }
          }
        });
  }

  private void beginSelection() {
    if (!active || stage != Stage.BALL) return;
    if (translator == null) {
      try {
        translator = TranslationEngine.forContext(this);
      } catch (RuntimeException e) {
        report("Mesin terjemahan tidak dapat dimulai", e);
        stopSession(null);
        return;
      }
    }
    if (((KeyguardManager) getSystemService(KEYGUARD_SERVICE)).isKeyguardLocked()) {
      stopSession("Layar terkunci.");
      return;
    }
    automatic = Prefs.get(this).getBoolean("auto_translate", false);
    windowCaptureFailures = 0;
    final int epoch = ++generation;
    stage = Stage.SELECTING;
    remove(ball);
    ball = null;
    selector =
        new LassoView(
            this,
            new LassoView.Done() {
              public void selected(List<float[]> screenPoints) {
                if (!valid(epoch)) return;
                remove(selector);
                selector = null;
                stage = Stage.PROCESSING;
                // Capture only once the live lasso is gone. No frozen screenshot is painted
                // over a running game and the game never loses keyboard/window focus.
                if (automatic) {
                  try {
                    monitoredArea = AutoCapturePolicy.rectangle(screenPoints, width, height);
                    targetWindow = focusedApplicationWindow();
                    if (targetWindow < 0)
                      throw new IllegalStateException("Jendela game tidak ditemukan");
                    showClose();
                    beginAutoScan();
                  } catch (RuntimeException e) {
                    fail("Area otomatis tidak dapat dimulai", e);
                  }
                  return;
                }
                long delay = Math.max(120, 450 - (SystemClock.uptimeMillis() - lastScreenshotAt));
                screenshotPending = true;
                main.postDelayed(() -> requestScreenshot(epoch, 0, screenPoints), delay);
              }

              public void cancel() {
                resetToBall();
              }
            });
    try {
      wm.addView(selector, params(-1, -1, false));
    } catch (RuntimeException e) {
      fail("Seleksi tidak dapat ditampilkan", e);
    }
  }

  private void requestScreenshot(int epoch, int retry, List<float[]> screenPoints) {
    if (!valid(epoch)) return;
    lastScreenshotAt = SystemClock.uptimeMillis();
    screenshotPending = true;
    final int requestId = ++screenshotRequest;
    final int captureScreenWidth = width, captureScreenHeight = height;
    final boolean windowCapture = automatic && Build.VERSION.SDK_INT >= 34;
    try {
      final Rect requestedFrame;
      if (windowCapture) {
        requestedFrame = applicationWindowBounds(targetWindow);
        if (!AutoCapturePolicy.useWindowCapture(Build.VERSION.SDK_INT, targetWindow)
            || requestedFrame == null
            || requestedFrame.isEmpty()) {
          windowCaptureFailed(epoch, "Metadata jendela game belum tersedia.");
          return;
        }
        if (!WindowCaptureFrame.containsSelection(requestedFrame, screenPoints)) {
          fail(
              "Area dialog harus berada seluruhnya di dalam jendela game. Pilih ulang area.", null);
          return;
        }
      } else requestedFrame = new Rect(0, 0, captureScreenWidth, captureScreenHeight);
      TakeScreenshotCallback callback =
          new TakeScreenshotCallback() {
            public void onSuccess(ScreenshotResult result) {
              if (!valid(epoch) || requestId != screenshotRequest) {
                result.getHardwareBuffer().close();
                return;
              }
              final Rect captureFrame =
                  windowCapture
                      ? WindowCaptureFrame.resolve(
                          result.getHardwareBuffer().getWidth(),
                          result.getHardwareBuffer().getHeight(),
                          requestedFrame,
                          captureScreenWidth,
                          captureScreenHeight)
                      : new Rect(requestedFrame);
              if (windowCapture
                  && (captureFrame == null
                      || !WindowCaptureFrame.containsSelection(captureFrame, screenPoints))) {
                result.getHardwareBuffer().close();
                windowCaptureFailed(
                    epoch, "Ukuran screenshot jendela game belum dapat dipetakan dengan aman.");
                return;
              }
              windowCaptureFailures = 0;
              if (hiddenForCapture) restoreAutoOverlay();
              try {
                showClose();
                screenshotWorker.execute(
                    () ->
                        convertScreenshot(
                            result,
                            screenPoints,
                            epoch,
                            captureScreenWidth,
                            captureScreenHeight,
                            captureFrame));
              } catch (RuntimeException e) {
                result.getHardwareBuffer().close();
                if (valid(epoch)) fail("Layanan sedang dihentikan", e);
              }
            }

            public void onFailure(int error) {
              if (!valid(epoch) || requestId != screenshotRequest) return;
              screenshotPending = false;
              if (hiddenForCapture) restoreAutoOverlay();
              if (windowCapture
                  && error != 6
                  && error != ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS) {
                windowCaptureFailed(epoch, "Screenshot jendela game gagal (kode " + error + ").");
                return;
              }
              if (automatic && error == ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                scheduleAuto();
                return;
              }
              if (error == ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT && retry == 0) {
                main.postDelayed(() -> requestScreenshot(epoch, 1, screenPoints), 500);
                return;
              }
              String message =
                  error == 6
                      ? "Layar ini dilindungi dari screenshot. Tidak dapat diterjemahkan."
                      : error == ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS
                          ? "Akses screenshot Accessibility tidak tersedia. Periksa layanan di"
                              + " setelan."
                          : "Screenshot Accessibility gagal (kode " + error + ").";
              fail(message, null);
            }
          };
      if (windowCapture) captureWindow(targetWindow, callback);
      else captureDisplay(callback);
      main.postDelayed(
          () -> {
            if (valid(epoch) && requestId == screenshotRequest && screenshotPending) {
              if (windowCapture)
                windowCaptureFailed(epoch, "Screenshot jendela game tidak merespons.");
              else fail("Screenshot tidak merespons. Periksa layanan Accessibility.", null);
            }
          },
          6500);
    } catch (RuntimeException e) {
      screenshotPending = false;
      if (windowCapture && !(e instanceof SecurityException))
        windowCaptureFailed(epoch, "Screenshot jendela belum tersedia pada game/perangkat ini.");
      else fail("Tidak dapat meminta screenshot Accessibility", e);
    }
  }

  private void convertScreenshot(
      ScreenshotResult result,
      List<float[]> screenPoints,
      int epoch,
      int screenWidth,
      int screenHeight,
      Rect captureFrame) {
    HardwareBuffer buffer = result.getHardwareBuffer();
    Bitmap hardware = null, copy = null;
    try {
      if (!valid(epoch)) return;
      hardware = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
      if (hardware == null) throw new IllegalStateException("Bitmap layar tidak tersedia");
      copy = hardware.copy(Bitmap.Config.ARGB_8888, false);
      if (copy == null) throw new IllegalStateException("Gambar layar tidak dapat disalin");
      Bitmap mapped = WindowCaptureFrame.compose(copy, captureFrame, screenWidth, screenHeight);
      if (mapped != copy) copy.recycle();
      copy = mapped;
      List<float[]> polygon = new ArrayList<>(screenPoints.size());
      for (float[] point : screenPoints)
        polygon.add(
            new float[] {
              point[0] * copy.getWidth() / screenWidth, point[1] * copy.getHeight() / screenHeight
            });
      Bitmap snapshot = copy;
      copy = null;
      main.post(
          () -> {
            if (!valid(epoch)) {
              snapshot.recycle();
              return;
            }
            screenshotPending = false;
            process(snapshot, polygon, epoch);
          });
    } catch (RuntimeException | OutOfMemoryError e) {
      if (copy != null) copy.recycle();
      main.post(
          () -> {
            if (valid(epoch)) {
              screenshotPending = false;
              fail("Screenshot tidak dapat diproses. Coba kurangi beban aplikasi.", e);
            }
          });
    } finally {
      if (hardware != null) hardware.recycle();
      buffer.close();
    }
  }

  private boolean valid(int epoch) {
    return active && !destroying && generation == epoch;
  }

  private void process(Bitmap snapshot, List<float[]> polygon, int epoch) {
    if (snapshot == null || !valid(epoch)) {
      if (snapshot != null) snapshot.recycle();
      return;
    }
    float minX = snapshot.getWidth(), minY = snapshot.getHeight(), maxX = 0, maxY = 0;
    for (float[] point : polygon) {
      minX = Math.min(minX, point[0]);
      minY = Math.min(minY, point[1]);
      maxX = Math.max(maxX, point[0]);
      maxY = Math.max(maxY, point[1]);
    }
    final int left = Math.max(0, (int) Math.floor(minX)), top = Math.max(0, (int) Math.floor(minY));
    int right = Math.min(snapshot.getWidth(), (int) Math.ceil(maxX)),
        bottom = Math.min(snapshot.getHeight(), (int) Math.ceil(maxY));
    if (right - left < 12 || bottom - top < 12) {
      snapshot.recycle();
      fail("Lingkari seluruh tulisan, bukan titik atau garis kecil.", null);
      return;
    }
    stage = Stage.PROCESSING;
    try {
      showClose();
      announce();
    } catch (RuntimeException e) {
      snapshot.recycle();
      fail("Tampilan proses tidak dapat dibuka", e);
      return;
    }
    final float sx = (float) width / snapshot.getWidth(),
        sy = (float) height / snapshot.getHeight();
    Bitmap allocatedCrop = null;
    try {
      allocatedCrop = Bitmap.createBitmap(right - left, bottom - top, Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(allocatedCrop);
      canvas.drawColor(Color.WHITE);
      Path path = new Path();
      path.moveTo(polygon.get(0)[0] - left, polygon.get(0)[1] - top);
      for (int i = 1; i < polygon.size(); i++)
        path.lineTo(polygon.get(i)[0] - left, polygon.get(i)[1] - top);
      path.close();
      canvas.save();
      canvas.clipPath(path);
      canvas.drawBitmap(snapshot, -left, -top, null);
      canvas.restore();
    } catch (RuntimeException | OutOfMemoryError e) {
      if (allocatedCrop != null) allocatedCrop.recycle();
      snapshot.recycle();
      fail("Area terlalu besar untuk diproses", e);
      return;
    }
    snapshot.recycle();
    final Bitmap crop = allocatedCrop;
    try {
      recognizer
          .process(InputImage.fromBitmap(crop, 0))
          .addOnSuccessListener(
              text -> {
                if (!valid(epoch)) return;
                List<Patch> patches = new ArrayList<>();
                for (Text.TextBlock block : text.getTextBlocks()) {
                  Rect box = new Rect();
                  List<Rect> lineBoxes = new ArrayList<>();
                  List<Float> fontSizes = new ArrayList<>();
                  StringBuilder value = new StringBuilder();
                  for (Text.Line line : block.getLines()) {
                    Rect lineBox = new Rect();
                    for (Text.Element word : line.getElements()) {
                      Rect b = word.getBoundingBox();
                      if (b != null
                          && !b.isEmpty()
                          && Geometry.contains(
                              polygon, left + b.exactCenterX(), top + b.exactCenterY())) {
                        if (value.length() > 0) value.append(' ');
                        value.append(word.getText());
                        if (box.isEmpty()) box.set(b);
                        else box.union(b);
                        if (lineBox.isEmpty()) lineBox.set(b);
                        else lineBox.union(b);
                        float glyphHeight = b.height() * sy;
                        Point[] corners = word.getCornerPoints();
                        if (corners != null && corners.length == 4) {
                          float sideA =
                              (float)
                                  Math.hypot(
                                      (corners[3].x - corners[0].x) * sx,
                                      (corners[3].y - corners[0].y) * sy);
                          float sideB =
                              (float)
                                  Math.hypot(
                                      (corners[2].x - corners[1].x) * sx,
                                      (corners[2].y - corners[1].y) * sy);
                          if (sideA > 0 && sideB > 0) glyphHeight = (sideA + sideB) / 2;
                        }
                        fontSizes.add(TextPlacement.estimateFontPx(word.getText(), glyphHeight));
                      }
                    }
                    if (!lineBox.isEmpty()) lineBoxes.add(mapSource(lineBox, left, top, sx, sy));
                  }
                  if (value.length() > 0 && !box.isEmpty()) {
                    Collections.sort(fontSizes);
                    float fontPx = fontSizes.get(fontSizes.size() / 2);
                    patches.add(
                        new Patch(
                            mapSource(box, left, top, sx, sy),
                            value.toString(),
                            lineBoxes,
                            fontPx));
                  }
                }
                if (patches.isEmpty()) {
                  if (automatic) {
                    handleAutoOcr(patches, epoch);
                    return;
                  }
                  fail(
                      "Teks tidak terbaca. Coba lingkari lebih longgar atau pilih tulisan yang"
                          + " lebih jelas.",
                      null);
                  return;
                }
                if (patches.size() > 16) {
                  fail(
                      "Terlalu banyak blok teks. Pilih area lebih kecil (maksimal 16 blok).", null);
                  return;
                }
                patches.sort(
                    (a, b) ->
                        a.source.top == b.source.top
                            ? Integer.compare(a.source.left, b.source.left)
                            : Integer.compare(a.source.top, b.source.top));
                if (automatic && handleAutoOcr(patches, epoch)) return;
                List<String> originals = new ArrayList<>();
                for (Patch patch : patches) originals.add(patch.original);
                translator.translateBatch(
                    originals,
                    new TranslationEngine.BatchResult() {
                      public void ok(List<String> translations) {
                        if (!valid(epoch)) return;
                        for (int i = 0; i < patches.size(); i++)
                          patches.get(i).translated = translations.get(i);
                        translationsReady(patches, epoch);
                      }

                      public void error(String message) {
                        if (valid(epoch)) fail(message, null);
                      }
                    });
              })
          .addOnFailureListener(
              e -> {
                if (valid(epoch)) fail("OCR gagal", e);
              })
          .addOnCompleteListener(task -> crop.recycle());
    } catch (RuntimeException e) {
      crop.recycle();
      fail("OCR tidak dapat dimulai", e);
    }
  }

  private Rect mapSource(Rect box, int cropLeft, int cropTop, float sx, float sy) {
    return new Rect(
        Math.max(0, Math.round((cropLeft + box.left) * sx)),
        Math.max(0, Math.round((cropTop + box.top) * sy)),
        Math.min(width, Math.round((cropLeft + box.right) * sx)),
        Math.min(height, Math.round((cropTop + box.bottom) * sy)));
  }

  private void addAttribution(List<Patch> patches, List<TranslationCard> strips) {
    View badge;
    if (translator != null && translator.isDeepL()) {
      TextView label = Ui.bold(this, "DeepL API", 11, Ui.INK);
      label.setGravity(Gravity.CENTER);
      label.setContentDescription("Terjemahan melalui DeepL API");
      badge = label;
    } else {
      ImageView image = new ImageView(this);
      image.setImageResource(R.drawable.powered_by_google_dark);
      image.setScaleType(ImageView.ScaleType.FIT_CENTER);
      image.setContentDescription("powered by Google Translate");
      badge = image;
    }

    int pad = Ui.dp(this, 4);
    badge.setPadding(pad, pad, pad, pad);
    badge.setBackground(Ui.shape(Color.WHITE, 4, this));
    int w = Math.min(width, Ui.dp(this, 132)),
        h = Math.min(height, Ui.dp(this, 26)),
        margin = Ui.dp(this, 8);
    int bx = Math.max(0, width - w - margin), by = Math.max(0, height - h - margin);
    // Prefer a free corner so attribution does not make the translated strips larger.
    int[][] candidates = {
      {bx, by},
      {Math.min(margin, Math.max(0, width - w)), by},
      {Math.min(margin, Math.max(0, width - w)), Math.min(margin, Math.max(0, height - h))}
    };
    for (int[] pos : candidates) {
      Rect area = new Rect(pos[0], pos[1], pos[0] + w, pos[1] + h);
      boolean clear = true;
      for (Patch patch : patches)
        if (Rect.intersects(area, patch.placement.bounds)) {
          clear = false;
          break;
        }
      if (clear) {
        bx = pos[0];
        by = pos[1];
        break;
      }
    }
    overlay.commit(strips, badge, new Rect(bx, by, bx + w, by + h));
  }

  private void render(List<Patch> patches, int epoch) {
    if (!valid(epoch) || stage == Stage.RESULT) return;
    for (Patch patch : patches) {
      int availableBottom = height;
      for (Patch other : patches) {
        if (other == patch || other.source.top < patch.source.bottom) continue;
        // Don't let a longer translation grow down across another selected text block.
        if (other.source.left < patch.source.right && other.source.right > patch.source.left)
          availableBottom = Math.min(availableBottom, other.source.top - Ui.dp(this, 4));
      }
      patch.placement =
          TextPlacement.place(
              this,
              patch.translated,
              patch.source,
              patch.sourceLines,
              width,
              height,
              patch.fontPx,
              availableBottom);
    }
    try {
      showClose();
      List<TranslationCard> strips = new ArrayList<>();
      for (Patch patch : patches) {
        strips.add(new TranslationCard(this, patch.translated, patch.placement));
      }
      addAttribution(patches, strips);
      stage = Stage.RESULT;
      announce();
    } catch (RuntimeException e) {
      fail("Hasil terjemahan tidak dapat ditampilkan", e);
    }
  }

  private void showClose() {
    if (overlay != null) return;
    OverlayHost host = new OverlayHost(this, width, height, this::resetToBall);
    WindowManager.LayoutParams hostParams = params(width, height, false);
    hostParams.flags = AutoCapturePolicy.flags(hostParams.flags, automatic);
    if (automatic) {
      host.closeButton.setVisibility(View.GONE);
      host.setClickable(false);
    }
    wm.addView(host, hostParams);
    overlay = host;
    if (automatic) {
      TextView close = Ui.bold(this, "×", 26, Ui.INK);
      close.setGravity(Gravity.CENTER);
      close.setBackground(Ui.shape(Color.WHITE, 100, this));
      close.setContentDescription("Hentikan otomatis dan kembali ke bola");
      close.setOnClickListener(v -> resetToBall());
      int size = Ui.dp(this, 44), margin = Ui.dp(this, 8);
      int[][] corners = {
        {width - size - margin, Ui.dp(this, 32)},
        {margin, Ui.dp(this, 32)},
        {width - size - margin, height - size - margin},
        {margin, height - size - margin}
      };
      WindowManager.LayoutParams cp = params(size, size, false);
      boolean placed = false;
      for (int[] corner : corners) {
        Rect b = new Rect(corner[0], corner[1], corner[0] + size, corner[1] + size);
        if (monitoredArea == null || !AutoCapturePolicy.controlOverlaps(monitoredArea, b)) {
          cp.x = corner[0];
          cp.y = corner[1];
          placed = true;
          break;
        }
      }
      if (!placed)
        throw new IllegalArgumentException(
            "Sisakan sudut layar di luar area dialog untuk tombol ×");
      wm.addView(close, cp);
      autoClose = close;
    }
  }

  private void resetToBall() {
    if (!active) return;
    generation++;
    cancelAuto();
    if (translator != null && translator.isDeepL()) {
      translator.close();
      translator = null;
    }
    // Epoch guards cancel stale work. Keep queued bitmap-delivery callbacks so they
    // can recycle their buffers instead of dropping an owned bitmap from the queue.
    clearVisuals();
    showBall();
    announce();
  }

  private void fail(String message, Throwable error) {
    report(message, error);
    resetToBall();
  }

  private void report(String message, Throwable error) {
    String detail =
        message
            + (error == null
                ? ""
                : ": " + error.getClass().getSimpleName() + " — " + error.getLocalizedMessage());
    Prefs.get(this).edit().putString("last_error", detail).apply();
    android.util.Log.e("Rich Translate", message, error);
    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    announce();
  }

  private void remove(View view) {
    if (wm != null && view != null && view.isAttachedToWindow())
      try {
        wm.removeViewImmediate(view);
      } catch (RuntimeException ignored) {
      }
  }

  private void clearVisuals() {
    remove(autoClose);
    autoClose = null;
    remove(selector);
    selector = null;
    remove(ball);
    ball = null;
    remove(overlay);
    overlay = null;
    screenshotPending = false;
  }

  @Override
  public void onConfigurationChanged(Configuration config) {
    super.onConfigurationChanged(config);
    if (wm == null) return;
    dimensions();
    if (active) resetToBall();
  }

  @Override
  public boolean onUnbind(Intent intent) {
    stopSession(null);
    if (connection.get() == this) connection.clear();
    return super.onUnbind(intent);
  }

  @Override
  public void onDestroy() {
    destroying = true;
    stopSession(null);
    if (connection.get() == this) connection.clear();
    screenshotWorker.shutdown();
    if (screenReceiverRegistered) unregisterReceiver(screenOff);
    super.onDestroy();
  }
}
