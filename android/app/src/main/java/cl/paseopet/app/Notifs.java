package cl.paseopet.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/** Avisos programados en el teléfono (funcionan con la app cerrada y tras reiniciar). */
public class Notifs extends BroadcastReceiver {

    static void channels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("avisos", "Avisos de PaseoPet", NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(new NotificationChannel("paseo", "Paseo en curso", NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel("listo", "App abierta", NotificationManager.IMPORTANCE_MIN));
    }

    private static PendingIntent pi(Context c, String id, String title, String body, int flags) {
        Intent i = new Intent(c, Notifs.class).setAction("cl.paseopet.app.AVISO." + id)
                .putExtra("id", id).putExtra("title", title).putExtra("body", body);
        return PendingIntent.getBroadcast(c, id.hashCode(), i, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    static void schedule(Context c, JSONArray list) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        SharedPreferences p = c.getSharedPreferences("pp", Context.MODE_PRIVATE);
        try {
            JSONArray old = new JSONArray(p.getString("sched", "[]"));
            for (int k = 0; k < old.length(); k++) {
                JSONObject o = old.getJSONObject(k);
                PendingIntent x = pi(c, o.getString("id"), "", "", PendingIntent.FLAG_NO_CREATE);
                if (x != null) { am.cancel(x); x.cancel(); }
            }
        } catch (Exception ignored) {}
        if (list == null) list = new JSONArray();
        p.edit().putString("sched", list.toString()).apply();
        arm(c, list);
    }

    private static void arm(Context c, JSONArray list) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        long now = System.currentTimeMillis();
        for (int k = 0; k < list.length() && k < 40; k++) {
            try {
                JSONObject o = list.getJSONObject(k);
                long at = o.getLong("at");
                if (at <= now) continue;
                PendingIntent x = pi(c, o.getString("id"), o.optString("title"), o.optString("body"), PendingIntent.FLAG_UPDATE_CURRENT);
                if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, x);
                else am.set(AlarmManager.RTC_WAKEUP, at, x);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onReceive(Context c, Intent i) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) {
            try { arm(c, new JSONArray(c.getSharedPreferences("pp", Context.MODE_PRIVATE).getString("sched", "[]"))); } catch (Exception ignored) {}
            return;
        }
        channels(c);
        String title = i.getStringExtra("title"), body = i.getStringExtra("body"), id = i.getStringExtra("id");
        if (title == null || title.isEmpty()) return;
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, "avisos") : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_stat).setContentTitle(title).setContentText(body)
         .setStyle(new Notification.BigTextStyle().bigText(body))
         .setAutoCancel(true).setContentIntent(WalkService.openApp(c));
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(id == null ? 1 : Math.abs(id.hashCode()) % 100000 + 100, b.build());
        } catch (SecurityException ignored) {}
    }
}
