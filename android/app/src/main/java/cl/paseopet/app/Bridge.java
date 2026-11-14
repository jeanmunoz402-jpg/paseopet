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

    /** ¿Está encendida la Ubicación del teléfono (servicios de ubicación)? */
    static boolean gpsOn(Context c) {
        try {
            android.location.LocationManager lm = (android.location.LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 28) return lm.isLocationEnabled();
            return lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                || lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) { return true; }
    }

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
            o.put("gps", gpsOn(c));
            try {
                android.content.pm.PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
                o.put("vc", Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode);
                o.put("vn", pi.versionName);
            } catch (Exception ignored) {}
            o.put("upd", true);
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
            } else if ("update".equals(t)) {
                String url = o.optString("url", "");
                if (url.startsWith("https://github.com/jeanmunoz402-jpg/paseopet/releases/")) Updater.start(c, url);
            } else if ("updresume".equals(t)) {
                Updater.resume(c);
            } else if ("osloc".equals(t)) {
                // Abre los ajustes para arreglar el GPS: "gps" = Ubicación del teléfono
                // apagada; cualquier otro valor = permiso de ubicación de la app.
                Intent i = "gps".equals(o.optString("what"))
                        ? new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.getPackageName()));
                open(c, i);
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
        open(c, i);
    }

    static void open(Context c, Intent i) {
        Context ctx = MainActivity.live != null ? MainActivity.live : c;
        if (ctx == c && !(c instanceof android.app.Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { ctx.startActivity(i); } catch (Exception e) {
            try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.getApplicationContext().startActivity(i); } catch (Exception ignored) {}
        }
    }
}
