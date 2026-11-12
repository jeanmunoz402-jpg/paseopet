package cl.paseopet.app;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONObject;

/** Mensajes que llegan desde la web (paseopet.site). */
final class Bridge {
    private Bridge() {}

    static boolean bgOn(Context c) {
        return c.getSharedPreferences("pp", Context.MODE_PRIVATE).getBoolean("bg", true);
    }

    static String status(Context c) {
        try {
            boolean notif = true;
            if (Build.VERSION.SDK_INT >= 24) {
                NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
                notif = nm.areNotificationsEnabled();
            }
            JSONObject o = new JSONObject();
            o.put("t", "status");
            o.put("v", 2);
            o.put("notif", notif);
            o.put("loc", WalkService.hasLoc(c));
            o.put("bg", bgOn(c));
            o.put("svc", WalkService.inst != null);
            return o.toString();
        } catch (Exception e) {
            return "{\"t\":\"status\",\"v\":2}";
        }
    }

    static void reply(String json) {
        MainActivity a = MainActivity.live;
        if (a != null) a.send(json);
    }

    static void onWeb(Context c, String msg) {
        try {
            JSONObject o = new JSONObject(msg);
            String t = o.optString("t");
            if ("sched".equals(t)) {
                Notifs.schedule(c, o.optJSONArray("list"));
            } else if ("walk".equals(t)) {
                WalkService.setWalking(o.optBoolean("on"), o.optString("name", null));
            } else if ("pull".equals(t)) {
                reply(WalkService.pointsSince(o.optLong("since", 0)));
            } else if ("status".equals(t)) {
                reply(status(c));
            } else if ("cfg".equals(t)) {
                if (o.has("bg")) {
                    boolean bg = o.optBoolean("bg", true);
                    SharedPreferences.Editor e = c.getSharedPreferences("pp", Context.MODE_PRIVATE).edit();
                    e.putBoolean("bg", bg).apply();
                    if (!bg) WalkService.stopIdle();
                }
                reply(status(c));
            } else if ("test".equals(t)) {
                Notifs.show(c, "prueba", o.optString("title", "PaseoPet 🐾"), o.optString("body", "Así se verán tus avisos."));
            } else if ("osnotif".equals(t)) {
                openNotifSettings(c);
            }
        } catch (Exception ignored) {}
    }

    static void openNotifSettings(Context c) {
        Intent i;
        if (Build.VERSION.SDK_INT >= 26) {
            i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.getPackageName());
        } else {
            i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.getPackageName()));
        }
        Context ctx = MainActivity.live != null ? MainActivity.live : c;
        if (ctx == c && !(c instanceof android.app.Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { ctx.startActivity(i); } catch (Exception e) {
            try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.getApplicationContext().startActivity(i); } catch (Exception ignored) {}
        }
    }
}
