package cl.paseopet.app;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsCallback;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsService;
import androidx.browser.customtabs.CustomTabsServiceConnection;
import androidx.browser.customtabs.CustomTabsSession;
import androidx.browser.trusted.TrustedWebActivityIntentBuilder;

import java.util.ArrayList;
import java.util.List;

/** Abre PaseoPet como Trusted Web Activity y mantiene un canal de mensajes con la web. */
public class MainActivity extends Activity {
    static final Uri ORIGIN = Uri.parse("https://paseopet.site");
    static final Uri HOME = Uri.parse("https://paseopet.site/");
    static MainActivity live;

    private CustomTabsSession session;
    private CustomTabsServiceConnection conn;
    private boolean launched = false;
    private boolean channel = false;
    private Uri target = HOME;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        live = this;
        Notifs.channels(this);
        Uri data = getIntent() != null ? getIntent().getData() : null;
        if (data != null && "paseopet.site".equals(data.getHost())) target = data;
        List<String> need = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION);
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        SharedPreferences p = getSharedPreferences("pp", MODE_PRIVATE);
        if (Build.VERSION.SDK_INT >= 23 && !need.isEmpty() && !p.getBoolean("asked", false)) {
            p.edit().putBoolean("asked", true).apply();
            requestPermissions(need.toArray(new String[0]), 1);
        } else {
            start();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        start();
    }

    private void start() {
        WalkService.ensure(this);
        String pkg = CustomTabsClient.getPackageName(this, null);
        if (pkg == null) { fallback(); return; }
        conn = new CustomTabsServiceConnection() {
            @Override
            public void onCustomTabsServiceConnected(ComponentName name, CustomTabsClient client) {
                try { client.warmup(0); } catch (Exception ignored) {}
                session = client.newSession(new CustomTabsCallback() {
                    @Override
                    public void onRelationshipValidationResult(int relation, Uri origin, boolean result, Bundle extras) {
                        runOnUiThread(() -> {
                            launch();
                            try { if (session != null) session.requestPostMessageChannel(ORIGIN); } catch (Exception ignored) {}
                        });
                    }
                    @Override
                    public void onMessageChannelReady(Bundle extras) {
                        channel = true;
                        send("{\"t\":\"hello\",\"v\":1}");
                    }
                    @Override
                    public void onPostMessage(String message, Bundle extras) {
                        Bridge.onWeb(MainActivity.this, message);
                    }
                });
                if (session == null) { launch(); return; }
                boolean ok = false;
                try { ok = session.validateRelationship(CustomTabsService.RELATION_USE_AS_ORIGIN, ORIGIN, null); } catch (Exception ignored) {}
                if (!ok) launch();
            }
            @Override
            public void onServiceDisconnected(ComponentName name) { session = null; channel = false; }
        };
        if (!CustomTabsClient.bindCustomTabsService(this, pkg, conn)) fallback();
    }

    private void launch() {
        if (launched) return;
        launched = true;
        int navy = Color.parseColor("#17123A");
        CustomTabColorSchemeParams col = new CustomTabColorSchemeParams.Builder()
                .setToolbarColor(navy).setNavigationBarColor(navy).build();
        TrustedWebActivityIntentBuilder bld = new TrustedWebActivityIntentBuilder(target).setDefaultColorSchemeParams(col);
        if (session != null) bld.build(session).launchTrustedWebActivity(this);
        else { startActivity(new Intent(Intent.ACTION_VIEW, target)); }
    }

    private void fallback() {
        launched = true;
        try { startActivity(new Intent(Intent.ACTION_VIEW, target)); } catch (Exception ignored) {}
        finish();
    }

    void send(String json) {
        try { if (session != null && channel) session.postMessage(json, null); } catch (Exception ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (launched) { // la app web se cerró
            if (!WalkService.walking) WalkService.stopIdle();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        if (live == this) live = null;
        try { if (conn != null) unbindService(conn); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
