package cl.paseopet.app;

import android.content.Context;

import org.json.JSONObject;

/** Mensajes que llegan desde la web (paseopet.site). */
final class Bridge {
    private Bridge() {}

    static void onWeb(Context c, String msg) {
        try {
            JSONObject o = new JSONObject(msg);
            String t = o.optString("t");
            if ("sched".equals(t)) {
                Notifs.schedule(c, o.optJSONArray("list"));
            } else if ("walk".equals(t)) {
                WalkService.setWalking(o.optBoolean("on"), o.optString("name", null));
            } else if ("pull".equals(t)) {
                MainActivity a = MainActivity.live;
                if (a != null) a.send(WalkService.pointsSince(o.optLong("since", 0)));
            }
        } catch (Exception ignored) {}
    }
}
