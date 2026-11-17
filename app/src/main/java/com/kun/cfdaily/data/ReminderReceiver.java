package com.kun.cfdaily.data;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.kun.cfdaily.R;
import com.kun.cfdaily.data.Models.DailyItem;
import com.kun.cfdaily.ui.MainActivity;
import com.kun.cfdaily.ui.TodayWidget;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReminderReceiver extends BroadcastReceiver {

    public static final String ACTION_CONTEST = "com.kun.cfdaily.CONTEST";
    public static final String ACTION_DAILY = "com.kun.cfdaily.DAILY";
    public static final String ACTION_EVENING = "com.kun.cfdaily.EVENING";

    @Override
    public void onReceive(Context c, Intent intent) {
        Reminders.ensureChannels(c);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (ACTION_CONTEST.equals(intent.getAction())) {
            int id = intent.getIntExtra("id", 0);
            long start = intent.getLongExtra("start", 0) * 1000;
            String time = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(start));
            String url = intent.getStringExtra("url");
            if (url == null) url = "https://codeforces.com/contests/" + id;
            Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            PendingIntent pi = PendingIntent.getActivity(c, id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(c, Reminders.CH_CONTEST)
                    .setSmallIcon(R.drawable.ic_notify)
                    .setContentTitle(intent.getStringExtra("name"))
                    .setContentText("Starts at " + time + ". Tap to open the contest page.")
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            nm.notify(id, n);
        } else if (ACTION_DAILY.equals(intent.getAction())) {
            List<DailyItem> items = Recommender.ensureToday(c);
            TodayWidget.updateAll(c);
            if (items.isEmpty()) return;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) sb.append("\n");
                sb.append(items.get(i).problem.rating).append("  ").append(items.get(i).problem.name);
            }
            Notification n = new Notification.Builder(c, Reminders.CH_DAILY)
                    .setSmallIcon(R.drawable.ic_notify)
                    .setContentTitle("Today's " + Mode.site() + " problems")
                    .setContentText(items.size() + " problems picked for you")
                    .setStyle(new Notification.BigTextStyle().bigText(sb.toString()))
                    .setContentIntent(openApp(c))
                    .setAutoCancel(true)
                    .build();
            nm.notify(1, n);
        } else if (ACTION_EVENING.equals(intent.getAction())) {
            // Checks Codeforces first so we do not nag about problems that were solved since the last sync.
            final PendingResult pending = goAsync();
            final Context app = c.getApplicationContext();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        try {
                            Sync.quickBlocking(app, true);
                        } catch (Exception ignored) {
                        }
                        evening(app);
                        TodayWidget.updateAll(app);
                    } finally {
                        pending.finish();
                    }
                }
            }).start();
        }
    }

    private static PendingIntent openApp(Context c) {
        return PendingIntent.getActivity(c, 1, new Intent(c, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void evening(Context c) {
        Db db = Db.get(c);
        if (db.kv("handle", "").length() == 0) return;
        List<DailyItem> items = db.daily(Recommender.today());
        int left = 0;
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).solved && !items.get(i).problem.solved) left++;
        }
        if (items.isEmpty() || left == 0) return;
        int done = items.size() - left;
        int streak = db.streaks()[0];
        String title;
        String text;
        if (done == 0 && streak > 0) {
            title = "Your " + streak + "-day streak ends tonight";
            text = "Solve one of today's picks to keep it going.";
        } else if (done == 0) {
            title = "Today's problems are still waiting";
            text = "Solve one to start a streak.";
        } else {
            title = left + " of " + items.size() + " picks left today";
            text = "You are " + done + " down. Finish the set?";
        }
        Notification n = new Notification.Builder(c, Reminders.CH_DAILY)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(openApp(c))
                .setAutoCancel(true)
                .build();
        c.getSystemService(NotificationManager.class).notify(2, n);
    }
}
