package cl.paseopet.app;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import java.io.File;

/**
 * Descarga la versión nueva del APK y abre el instalador de Android.
 * Android no deja que una app instalada fuera de Google Play se reemplace sola:
 * el usuario siempre confirma con un toque en «Actualizar» o «Instalar».
 */
final class Updater {
    private Updater() {}

    private static long dlId = -1;
    private static File pending = null;
    private static BroadcastReceiver rx = null;

    static void start(Context c, String url) {
        Context app = c.getApplicationContext();
        try {
            File dir = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir == null) dir = app.getFilesDir();
            File f = new File(dir, "PaseoPet-nueva.apk");
            if (f.exists()) f.delete();
            DownloadManager dm = (DownloadManager) app.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url))
                    .setTitle("Actualización de PaseoPet")
                    .setDescription("Descargando la versión nueva")
                    .setMimeType("application/vnd.android.package-archive")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setDestinationUri(Uri.fromFile(f));
            listen(app);
            dlId = dm.enqueue(r);
            pending = f;
            send("dl");
        } catch (Exception e) {
            send("err");
        }
    }

    private static void listen(Context app) {
        if (rx != null) return;
        rx = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent i) {
                long id = i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id != dlId) return;
                if (ok(ctx, id)) install(ctx); else send("err");
            }
        };
        IntentFilter f = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(rx, f, Context.RECEIVER_EXPORTED);
        else app.registerReceiver(rx, f);
    }

    private static boolean ok(Context c, long id) {
        DownloadManager dm = (DownloadManager) c.getSystemService(Context.DOWNLOAD_SERVICE);
        try (Cursor cur = dm.query(new DownloadManager.Query().setFilterById(id))) {
            if (cur != null && cur.moveToFirst()) {
                int col = cur.getColumnIndex(DownloadManager.COLUMN_STATUS);
                return col >= 0 && cur.getInt(col) == DownloadManager.STATUS_SUCCESSFUL;
            }
        } catch (Exception ignored) {}
        return false;
    }

    /** Abre el instalador; si falta el permiso «Instalar apps desconocidas», abre ese ajuste primero. */
    static void install(Context c) {
        if (pending == null || !pending.exists()) { send("err"); return; }
        Context app = c.getApplicationContext();
        if (Build.VERSION.SDK_INT >= 26 && !app.getPackageManager().canRequestPackageInstalls()) {
            send("perm");
            Intent s = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + app.getPackageName()));
            Bridge.open(app, s);
            return;
        }
        try {
            Uri u = FileProvider.getUriForFile(app, app.getPackageName() + ".files", pending);
            Intent i = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(u, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            app.startActivity(i);
            send("ready");
        } catch (Exception e) {
            send("err");
        }
    }

    /** Al volver a la app después de dar el permiso, sigue con la instalación. */
    static void resume(Context c) {
        if (pending == null || !pending.exists()) { send("gone"); return; }
        if (Build.VERSION.SDK_INT >= 26 && !c.getPackageManager().canRequestPackageInstalls()) { send("perm"); return; }
        install(c);
    }

    private static void send(String st) {
        Bridge.reply("{\"t\":\"upd\",\"st\":\"" + st + "\"}");
    }
}
