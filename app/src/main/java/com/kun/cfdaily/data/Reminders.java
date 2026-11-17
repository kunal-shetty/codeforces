package com.kun.cfdaily.data;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import com.kun.cfdaily.data.Models.Contest;

import java.util.Calendar;
import java.util.List;

public class Reminders {

    public static final String CH_CONTEST = "contests";
    public static final String CH_DAILY = "daily";
    public static final long LEAD_MS = 30L * 60 * 1000;
    private static final int DAILY_REQUEST = 900000001;
    private static final int EVENING_REQUEST = 900000002;

    public static void ensureChannels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH_CONTEST, "Contest reminders", NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(new NotificationChannel(CH_DAILY, "Daily problems", NotificationManager.IMPORTANCE_DEFAULT));
    }

    private static PendingIntent contestIntent(Context c, Contest ct) {
        Intent i = new Intent(c, ReminderReceiver.class);
        i.setAction(ReminderReceiver.ACTION_CONTEST);
        i.setData(Uri.parse("cfdaily://contest/" + ct.id));
        i.putExtra("id", ct.id);
        i.putExtra("name", ct.name);
        i.putExtra("start", ct.startSeconds);
        i.putExtra("url", ct.url());
        return PendingIntent.getBroadcast(c, ct.id, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Exact when the system allows it, so the reminder is not held back by battery saving. */
    public static void schedule(Context c, Contest ct) {
        long start = ct.startSeconds * 1000;
        long now = System.currentTimeMillis();
        if (start <= now) return;
        long when = Math.max(now + 2000, start - LEAD_MS);
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = contestIntent(c, ct);
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
                return;
            }
        } catch (SecurityException ignored) {
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
    }

    public static void cancel(Context c, Contest ct) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        am.cancel(contestIntent(c, ct));
    }

    public static void rescheduleAll(Context c) {
        for (int mode = 0; mode < 2; mode++) {
            List<Contest> list = Db.forMode(c, mode == 1).contests(true);
            for (int i = 0; i < list.size(); i++) schedule(c, list.get(i));
        }
    }

    private static void repeating(Context c, String action, int request, boolean on, int hour, int minute) {
        Intent i = new Intent(c, ReminderReceiver.class);
        i.setAction(action);
        PendingIntent pi = PendingIntent.getBroadcast(c, request, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = c.getSystemService(AlarmManager.class);
        am.cancel(pi);
        if (!on) return;
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);
    }

    /** (Re)schedules both daily notifications: the morning picks and the evening nudge. */
    public static void scheduleDaily(Context c) {
        Db db = Db.get(c);
        repeating(c, ReminderReceiver.ACTION_DAILY, DAILY_REQUEST, "1".equals(db.kv("daily_notify", "1")),
                db.kvInt("notify_hour", 9), db.kvInt("notify_min", 0));
        repeating(c, ReminderReceiver.ACTION_EVENING, EVENING_REQUEST, "1".equals(db.kv("evening_notify", "1")), 20, 0);
    }
}
