package com.munazzim.pro;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private static final String CHANNEL_ID   = "munazzim_main";
    private static final String CHANNEL_NAME = "مُنَظِّم Pro";
    private int notifId = 1000;
    private ActivityResultLauncher<String> notifPermLauncher;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createNotificationChannel();

        // Permission launcher — called when user responds to permission dialog
        notifPermLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                final String result = granted ? "granted" : "denied";
                mainHandler.post(() -> {
                    if (webView != null) {
                        webView.evaluateJavascript(
                            "(function(){" +
                            "  window._androidNotifResult='" + result + "';" +
                            "  if(typeof window._androidNotifCallback==='function'){" +
                            "    window._androidNotifCallback('" + result + "');" +
                            "  }" +
                            "  if(typeof window._notifGranted!=='undefined'){" +
                            "    window._notifGranted=" + granted + ";" +
                            "  }" +
                            "})()",
                            null
                        );
                    }
                });
            }
        );

        // WebView setup
        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMediaPlaybackRequiresUserGesture(false);

        // Android bridge
        webView.addJavascriptInterface(new AndroidBridge(), "Android");

        // Grant all WebView permission requests automatically
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.grant(request.getResources());
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Inject permission status after page loads
                injectPermissionStatus();
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    /** Tell the HTML what permission status we have right now */
    private void injectPermissionStatus() {
        boolean granted = hasNotifPermission();
        mainHandler.post(() -> {
            if (webView != null) {
                webView.evaluateJavascript(
                    "(function(){" +
                    "  window._androidBridgeReady = true;" +
                    "  window._notifGranted = " + granted + ";" +
                    "  if(typeof window._onAndroidReady==='function') window._onAndroidReady(" + granted + ");" +
                    "})()",
                    null
                );
            }
        });
    }

    private boolean hasNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true; // Android < 13: always granted
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("تذكيرات المهام والعادات اليومية");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 300, 100, 300});
            channel.enableLights(true);
            channel.setLightColor(Color.parseColor("#7c3aed"));
            channel.setShowBadge(true);
            NotificationManager mgr = getSystemService(NotificationManager.class);
            if (mgr != null) mgr.createNotificationChannel(channel);
        }
    }

    // ─────────────────────────────────────────────
    // JavaScript ↔ Android Bridge
    // ─────────────────────────────────────────────
    class AndroidBridge {

        /** Check if notification permission is granted */
        @JavascriptInterface
        public boolean hasPermission() {
            return hasNotifPermission();
        }

        /** Request notification permission from the user */
        @JavascriptInterface
        public void requestNotificationPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (!hasNotifPermission()) {
                    mainHandler.post(() ->
                        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    );
                } else {
                    // Already granted — notify JS
                    mainHandler.post(() -> {
                        if (webView != null) {
                            webView.evaluateJavascript(
                                "if(typeof window._androidNotifCallback==='function')" +
                                "  window._androidNotifCallback('granted');",
                                null
                            );
                        }
                    });
                }
            } else {
                // Android < 13 — always granted
                mainHandler.post(() -> {
                    if (webView != null) {
                        webView.evaluateJavascript(
                            "if(typeof window._androidNotifCallback==='function')" +
                            "  window._androidNotifCallback('granted');" +
                            "window._notifGranted=true;",
                            null
                        );
                    }
                });
            }
        }

        /** Show a native Android notification */
        @JavascriptInterface
        public void showNotification(String title, String body) {
            if (!hasNotifPermission()) return;

            NotificationManager mgr =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (mgr == null) return;

            // Tap notification → open app
            Intent intent = new Intent(MainActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                : PendingIntent.FLAG_UPDATE_CURRENT;
            PendingIntent pi = PendingIntent.getActivity(
                MainActivity.this, notifId, intent, flags
            );

            NotificationCompat.Builder builder =
                new NotificationCompat.Builder(MainActivity.this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setVibrate(new long[]{0, 300, 100, 300})
                    .setLights(Color.parseColor("#7c3aed"), 500, 500)
                    .setContentIntent(pi);

            mgr.notify(notifId++, builder.build());
        }

        /** Show notification with custom type/category */
        @JavascriptInterface
        public void showNotificationWithType(String title, String body, String type) {
            showNotification(title, body);
        }

        /** Vibrate the device */
        @JavascriptInterface
        public void vibrate(int ms) {
            mainHandler.post(() -> {
                try {
                    android.os.Vibrator v;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        android.os.VibratorManager vm =
                            (android.os.VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
                        v = vm != null ? vm.getDefaultVibrator() : null;
                    } else {
                        v = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
                    }
                    if (v != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            v.vibrate(android.os.VibrationEffect.createOneShot(
                                ms, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                        } else {
                            v.vibrate(ms);
                        }
                    }
                } catch (Exception ignored) {}
            });
        }

        /** Cancel all notifications */
        @JavascriptInterface
        public void cancelAllNotifications() {
            NotificationManager mgr =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (mgr != null) mgr.cancelAll();
        }

        /** Get Android version info */
        @JavascriptInterface
        public String getDeviceInfo() {
            return "{\"sdk\":" + Build.VERSION.SDK_INT +
                   ",\"release\":\"" + Build.VERSION.RELEASE + "\"" +
                   ",\"model\":\"" + Build.MODEL + "\"}";
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-inject permission status when user returns from settings
        injectPermissionStatus();
    }
}
class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val title = intent.getStringExtra("title") ?: ""
            val body = intent.getStringExtra("body") ?: ""
            val customData = intent.getStringExtra("customData") ?: ""
            
            // تمرير البيانات إلى JavaScript داخل WebView
            webView.evaluateJavascript("""
                window.onNotificationReceived({
                    title: '$title',
                    body: '$body',
                    customData: '$customData'
                });
            """.trimIndent(), null)
        }
    }
    
    override fun onResume() {
        super.onResume()
        registerReceiver(notificationReceiver, IntentFilter("NEW_NOTIFICATION"))
    }
    
    override fun onPause() {
        super.onPause()
        unregisterReceiver(notificationReceiver)
    }
}
