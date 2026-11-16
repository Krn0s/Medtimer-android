package com.medtimer.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;

public class AlarmReceiver extends BroadcastReceiver {
    static final String FIRE = "com.medtimer.app.FIRE";
    static final String DONE = "com.medtimer.app.DONE";
    static final String SNOOZE = "com.medtimer.app.SNOOZE";

    @Override
    public void onReceive(Context c, Intent i) {
        String id = i.getStringExtra("id");
        if (id == null) return;
        String title = i.getStringExtra("title");
        String body = i.getStringExtra("body");
        if (title == null) title = "MedTimer";
        if (body == null) body = "";
        int nid = id.hashCode();
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        String action = i.getAction();

        if (DONE.equals(action)) {
            Scheduler.addEvent(c, "done", id, 0);
            if (nm != null) nm.cancel(nid);
            return;
        }
        if (SNOOZE.equals(action)) {
            long t = System.currentTimeMillis() + 10 * 60_000L;
            Scheduler.addEvent(c, "snooze", id, t);
            if (nm != null) nm.cancel(nid);
            Scheduler.setAlarm(c, t, Scheduler.alarmIntent(c, id, title, body, PendingIntent.FLAG_UPDATE_CURRENT));
            return;
        }

        Scheduler.ensureChannel(c);
        Intent open = new Intent(c, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder b = new Notification.Builder(c, Scheduler.CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setColor(0xFF7C9CFF)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(openPi);
        if (!id.startsWith("test")) {
            b.addAction(new Notification.Action.Builder((Icon) null, "Done", actionPi(c, DONE, id, title, body)).build());
            b.addAction(new Notification.Action.Builder((Icon) null, "Snooze 10 min", actionPi(c, SNOOZE, id, title, body)).build());
        }
        try {
            if (nm != null) nm.notify(nid, b.build());
        } catch (SecurityException ignored) {}
    }

    static PendingIntent actionPi(Context c, String action, String id, String title, String body) {
        Intent x = new Intent(c, AlarmReceiver.class);
        x.setAction(action);
        x.setData(Uri.parse("medtimer://" + (DONE.equals(action) ? "done" : "snooze") + "/" + Uri.encode(id)));
        x.putExtra("id", id);
        x.putExtra("title", title);
        x.putExtra("body", body);
        return PendingIntent.getBroadcast(c, (action + id).hashCode(), x,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
