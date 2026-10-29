package app.recentstv;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Список недавних приложений строится по статистике использования (UsageStats):
 * берутся приложения, выходившие на передний план с момента последней загрузки приставки.
 * Закрытое пользователем приложение запоминается и пропадает из списка,
 * пока его снова не запустят.
 */
final class RecentsRepo {
    private static final long WINDOW_MS = 3L * 24 * 60 * 60 * 1000;
    private static final int MAX_ITEMS = 24;
    private static final String PREFS = "dismissed";

    private RecentsRepo() {
    }

    @SuppressWarnings("deprecation")
    static boolean hasUsageAccess(Context ctx) {
        AppOpsManager aom = (AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
        if (aom == null) {
            return false;
        }
        int mode;
        if (Build.VERSION.SDK_INT >= 29) {
            mode = aom.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), ctx.getPackageName());
        } else {
            mode = aom.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), ctx.getPackageName());
        }
        if (mode == AppOpsManager.MODE_DEFAULT) {
            return ctx.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    @SuppressWarnings("deprecation")
    static List<AppEntry> load(Context ctx) {
        List<AppEntry> out = new ArrayList<>();
        UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) {
            return out;
        }
        PackageManager pm = ctx.getPackageManager();

        long now = System.currentTimeMillis();
        long boot = now - SystemClock.elapsedRealtime();
        long begin = Math.max(boot, now - WINDOW_MS);

        UsageEvents events = usm.queryEvents(begin, now);
        if (events == null) {
            return out;
        }
        Map<String, Long> last = new HashMap<>();
        UsageEvents.Event ev = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(ev);
            if (ev.getEventType() != UsageEvents.Event.MOVE_TO_FOREGROUND) {
                continue;
            }
            String p = ev.getPackageName();
            long t = ev.getTimeStamp();
            Long prev = last.get(p);
            if (prev == null || t > prev) {
                last.put(p, t);
            }
        }

        // Что не показываем: сам переключатель, системные оболочки и все лаунчеры.
        Set<String> skip = new HashSet<>();
        skip.add(ctx.getPackageName());
        skip.add("android");
        skip.add("com.android.systemui");
        Intent homeIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo ri : pm.queryIntentActivities(homeIntent, 0)) {
            skip.add(ri.activityInfo.packageName);
        }

        SharedPreferences dismissed = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        List<Map.Entry<String, Long>> sorted = new ArrayList<>(last.entrySet());
        Collections.sort(sorted, (a, b) -> Long.compare(b.getValue(), a.getValue()));

        for (Map.Entry<String, Long> me : sorted) {
            if (out.size() >= MAX_ITEMS) {
                break;
            }
            String pkg = me.getKey();
            long used = me.getValue();
            if (skip.contains(pkg)) {
                continue;
            }
            if (dismissed.getLong(pkg, 0L) >= used) {
                continue;
            }
            Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (launch == null) {
                launch = pm.getLaunchIntentForPackage(pkg);
            }
            if (launch == null) {
                continue;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                CharSequence label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
                Drawable icon = pm.getApplicationIcon(pkg);
                out.add(new AppEntry(pkg, String.valueOf(label), icon, launch, used));
            } catch (PackageManager.NameNotFoundException ignored) {
                // приложение удалили — пропускаем
            }
        }
        return out;
    }

    /**
     * Приложение, которое сейчас открыто «под» нами (например, YouTube, поверх которого
     * показан список). Такое приложение нельзя убить, пока оно на экране.
     * Возвращает null, если под нами главный экран или ничего не определилось.
     */
    @SuppressWarnings("deprecation")
    static String behindPackage(Context ctx) {
        UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        UsageEvents events = usm.queryEvents(now - 6L * 60 * 60 * 1000, now);
        if (events == null) {
            return null;
        }
        String topPkg = null;
        String topCls = null;
        UsageEvents.Event ev = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(ev);
            String p = ev.getPackageName();
            if (p == null || p.equals(ctx.getPackageName())) {
                continue;
            }
            int type = ev.getEventType();
            if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                topPkg = p;
                topCls = ev.getClassName();
            } else if (type == 23 /* ACTIVITY_STOPPED */
                    && p.equals(topPkg)
                    && java.util.Objects.equals(ev.getClassName(), topCls)) {
                topPkg = null;
                topCls = null;
            }
        }
        return topPkg;
    }

    /** Закрывает фоновые процессы приложения (то же, что «убить» в списке недавних). */
    static void kill(Context ctx, String pkg) {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                am.killBackgroundProcesses(pkg);
            }
        } catch (Throwable ignored) {
            // если система не дала — просто уберём из списка
        }
    }

    /** Запоминает, что приложение закрыто, чтобы оно исчезло из списка. */
    static void dismiss(Context ctx, String pkg) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(pkg, System.currentTimeMillis())
                .apply();
    }
}
