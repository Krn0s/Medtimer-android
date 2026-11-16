package com.medtimer.app;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Stores the reminder list sent by the web UI and arms one alarm per reminder. */
public final class Scheduler {
    static final String PREFS = "medtimer";
    static final String KEY_SCHED = "schedule";
    static final String KEY_EVENTS = "events";
    static final String KEY_STATE = "state";
    static final String CHANNEL = "reminders";

    private Scheduler() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void ensureChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Eat, drink and wear-off reminders");
            ch.enableVibration(true);
            nm.createNotificationChannel(ch);
        }
    }

    static PendingIntent alarmIntent(Context c, String id, String title, String body, int flags) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction(AlarmReceiver.FIRE);
        i.setData(Uri.parse("medtimer://fire/" + Uri.encode(id)));
        i.putExtra("id", id);
        i.putExtra("title", title);
        i.putExtra("body", body);
        return PendingIntent.getBroadcast(c, id.hashCode(), i, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    static boolean canExact(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        return Build.VERSION.SDK_INT < 31 || (am != null && am.canScheduleExactAlarms());
    }

    static void setAlarm(Context c, long t, PendingIntent p) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        try {
            if (canExact(c)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p);
        }
    }

    static synchronized void cancelAll(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        try {
            JSONArray old = new JSONArray(prefs(c).getString(KEY_SCHED, "[]"));
            for (int k = 0; k < old.length(); k++) {
                String id = old.getJSONObject(k).getString("id");
                PendingIntent p = alarmIntent(c, id, "", "", PendingIntent.FLAG_NO_CREATE);
                if (p != null) {
                    if (am != null) am.cancel(p);
                    p.cancel();
                }
            }
        } catch (JSONException ignored) {}
    }

    static synchronized void scheduleAll(Context c, String json) {
        cancelAll(c);
        prefs(c).edit().putString(KEY_SCHED, json).apply();
        arm(c);
    }

    /** (Re)arms every future reminder in the stored list — also used after a reboot. */
    static synchronized void arm(Context c) {
        long now = System.currentTimeMillis();
        try {
            JSONArray a = new JSONArray(prefs(c).getString(KEY_SCHED, "[]"));
            for (int k = 0; k < a.length(); k++) {
                JSONObject o = a.getJSONObject(k);
                long t = o.getLong("t");
                if (t <= now) continue;
                PendingIntent p = alarmIntent(c, o.getString("id"), o.optString("title"), o.optString("body"),
                        PendingIntent.FLAG_UPDATE_CURRENT);
                setAlarm(c, t, p);
            }
        } catch (JSONException ignored) {}
    }

    /** Taps on Done / Snooze in a notification are queued here for the web UI to pick up. */
    static synchronized void addEvent(Context c, String type, String id, long t) {
        try {
            JSONArray ev = new JSONArray(prefs(c).getString(KEY_EVENTS, "[]"));
            JSONObject o = new JSONObject();
            o.put("type", type);
            o.put("id", id);
            o.put("t", t);
            ev.put(o);
            prefs(c).edit().putString(KEY_EVENTS, ev.toString()).apply();
        } catch (JSONException ignored) {}
    }

    static synchronized String pullEvents(Context c) {
        String s = prefs(c).getString(KEY_EVENTS, "[]");
        prefs(c).edit().putString(KEY_EVENTS, "[]").apply();
        return s;
    }
}
