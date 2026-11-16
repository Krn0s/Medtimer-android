package com.medtimer.app;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private WebView web;
    private int notifAsks = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF0F1115);
        getWindow().setNavigationBarColor(0xFF0F1115);
        Scheduler.ensureChannel(this);

        web = new WebView(this);
        web.setBackgroundColor(0xFF0F1115);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("file".equals(u.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;
            }
        });
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
        askNotifications();
    }

    private void askNotifications() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            if (notifAsks++ < 2) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
                return;
            }
        }
        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        try { startActivity(i); } catch (Exception ignored) {}
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        refreshWeb();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshWeb();
    }

    private void refreshWeb() {
        if (web != null) web.evaluateJavascript("window.onAppResume && window.onAppResume()", null);
    }

    private boolean notificationsOn() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        return nm != null && nm.areNotificationsEnabled();
    }

    class Bridge {
        @JavascriptInterface public void schedule(String json) { Scheduler.scheduleAll(MainActivity.this, json); }
        @JavascriptInterface public String pullEvents() { return Scheduler.pullEvents(MainActivity.this); }
        @JavascriptInterface public void saveState(String json) {
            Scheduler.prefs(MainActivity.this).edit().putString(Scheduler.KEY_STATE, json).apply();
        }
        @JavascriptInterface public String loadState() {
            return Scheduler.prefs(MainActivity.this).getString(Scheduler.KEY_STATE, "");
        }
        @JavascriptInterface public String status() {
            JSONObject o = new JSONObject();
            try {
                o.put("notif", notificationsOn());
                o.put("exact", Scheduler.canExact(MainActivity.this));
            } catch (JSONException ignored) {}
            return o.toString();
        }
        @JavascriptInterface public void requestNotif() { runOnUiThread(MainActivity.this::askNotifications); }
        @JavascriptInterface public void openExactSettings() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 31) {
                    Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()));
                    try { startActivity(i); } catch (Exception ignored) {}
                }
            });
        }
        @JavascriptInterface public void testNotif() {
            long t = System.currentTimeMillis() + 5000;
            Scheduler.setAlarm(MainActivity.this, t, Scheduler.alarmIntent(MainActivity.this, "test",
                    "💊 MedTimer test", "Notifications work! Reminders will look like this.",
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT));
        }
    }
}
