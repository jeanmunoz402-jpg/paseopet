package cl.paseopet.app;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

/** Servicio en primer plano: mantiene vivo el GPS durante el paseo aunque la pantalla esté apagada. */
public class WalkService extends Service implements LocationListener {
    static volatile boolean walking = false;
    static WalkService inst;
    static String petName = "tu perro";
    static final ArrayList<double[]> PTS = new ArrayList<>();
    private LocationManager lm;

    static boolean hasLoc(Context c) {
        return Build.VERSION.SDK_INT < 23 || c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Se llama al abrir la app (en primer plano), así el servicio puede iniciar sin restricciones. */
    static void ensure(Context c) {
        if (inst != null || !hasLoc(c)) return;
        try {
            Intent i = new Intent(c, WalkService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Exception ignored) {}
    }

    static void stopIdle() {
        if (inst != null && !walking) inst.stopSelf();
    }

    static void setWalking(boolean on, String name) {
        if (name != null && !name.isEmpty()) petName = name;
        WalkService s = inst;
        if (s == null) return;
        s.toggle(on);
    }

    static String pointsSince(long since) {
        try {
            JSONArray arr = new JSONArray();
            synchronized (PTS) {
                for (double[] p : PTS) {
                    if ((long) p[3] > since) {
                        JSONArray q = new JSONArray();
                        q.put(p[0]); q.put(p[1]); q.put(p[2]); q.put((long) p[3]);
                        arr.put(q);
                    }
                }
            }
            JSONObject o = new JSONObject();
            o.put("t", "pts");
            o.put("pts", arr);
            return o.toString();
        } catch (Exception e) {
            return "{\"t\":\"pts\",\"pts\":[]}";
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        inst = this;
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        goForeground();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    private Notification build() {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, walking ? "paseo" : "listo")
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_stat).setOngoing(true).setContentIntent(openApp(this))
         .setContentTitle(walking ? "Paseo en curso con " + petName : "PaseoPet está abierto")
         .setContentText(walking ? "El GPS sigue grabando aunque apagues la pantalla." : "Listo para pasear.");
        if (walking) { b.setUsesChronometer(true).setWhen(System.currentTimeMillis()); }
        return b.build();
    }

    private void goForeground() {
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(7, build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            else startForeground(7, build());
        } catch (Exception e) {
            inst = null;
            stopSelf();
        }
    }

    private void toggle(boolean on) {
        if (on == walking) return;
        walking = on;
        try {
            if (on) {
                synchronized (PTS) { PTS.clear(); }
                if (hasLoc(this)) lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 2f, this, Looper.getMainLooper());
            } else {
                lm.removeUpdates(this);
            }
        } catch (SecurityException | IllegalArgumentException ignored) {}
        goForeground();
    }

    @Override
    public void onLocationChanged(Location l) {
        synchronized (PTS) {
            if (PTS.size() < 30000) PTS.add(new double[]{l.getLatitude(), l.getLongitude(), l.hasAccuracy() ? l.getAccuracy() : 30, l.getTime()});
        }
    }

    @Override public void onStatusChanged(String p, int s, Bundle e) {}
    @Override public void onProviderEnabled(String p) {}
    @Override public void onProviderDisabled(String p) {}

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (!walking) stopSelf();
    }

    @Override
    public void onDestroy() {
        try { lm.removeUpdates(this); } catch (Exception ignored) {}
        walking = false;
        if (inst == this) inst = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    static PendingIntent openApp(Context c) {
        Intent i = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (i == null) i = new Intent(c, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
