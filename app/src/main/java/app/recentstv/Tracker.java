package app.recentstv;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

/**
 * Запоминает, какие приложения выходили на экран (по событиям службы специальных
 * возможностей). Благодаря этому список работает без ADB и без доступа к статистике.
 */
final class Tracker {
    private static final String PREFS = "fg";

    /** Приложение, которое сейчас на экране (в памяти, пока работает служба). */
    static volatile String current;

    private Tracker() {
    }

    static void record(Context ctx, String pkg) {
        if (pkg.equals(current)) {
            return;
        }
        current = pkg;
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(pkg, System.currentTimeMillis())
                .apply();
    }

    static Map<String, Long> all(Context ctx) {
        Map<String, Long> out = new HashMap<>();
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
            if (e.getValue() instanceof Long) {
                out.put(e.getKey(), (Long) e.getValue());
            }
        }
        return out;
    }
}
