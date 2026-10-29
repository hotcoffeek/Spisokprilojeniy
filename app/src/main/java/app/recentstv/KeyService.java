package app.recentstv;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Служба специальных возможностей приложения:
 * 1) ловит назначенную кнопку пульта и открывает (или закрывает) список;
 * 2) следит, какие приложения выходили на экран.
 */
public class KeyService extends AccessibilityService {

    private static final String SETTINGS = "settings";
    private static final String KEY_HOTKEY = "hotkey";

    /** Включается на экране настройки, пока ждём нажатия новой кнопки. */
    static volatile boolean assigning = false;
    static volatile boolean connected = false;
    static volatile KeyService instance;

    /** Экран настройки узнаёт о результате назначения кнопки. */
    interface Listener {
        void onKeyAssigned(int keyCode);

        void onKeyRejected();
    }

    static volatile Listener listener;

    private final Set<String> ignore = new HashSet<>();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        connected = true;
        instance = this;
        ignore.clear();
        ignore.add(getPackageName());
        ignore.add("android");
        ignore.add("com.android.systemui");
        ignore.add("com.android.permissioncontroller");
        ignore.add("com.google.android.permissioncontroller");
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                List<InputMethodInfo> list = imm.getEnabledInputMethodList();
                for (InputMethodInfo i : list) {
                    ignore.add(i.getPackageName());
                }
            }
        } catch (Throwable ignored) {
            // клавиатуры не определились — не страшно
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        CharSequence p = event.getPackageName();
        if (p == null) {
            return;
        }
        String pkg = p.toString();
        if (ignore.contains(pkg)) {
            return;
        }
        Tracker.record(this, pkg);
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        connected = false;
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (assigning) {
            // Ловим кнопку здесь: прошивка может забрать её (например, LIVE TV),
            // и до экрана настройки она не дойдёт.
            if (code == KeyEvent.KEYCODE_BACK) {
                return false;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                Listener l = listener;
                if (isForbidden(code)) {
                    if (l != null) {
                        l.onKeyRejected();
                    }
                } else {
                    setHotkey(this, code);
                    assigning = false;
                    if (l != null) {
                        l.onKeyAssigned(code);
                    }
                }
            }
            return true;
        }
        int key = getHotkey(this);
        if (key == 0 || code != key) {
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            toggleList();
        }
        return true;
    }

    private void toggleList() {
        MainActivity m = MainActivity.instance;
        if (m != null && MainActivity.visible) {
            m.finish();
            return;
        }
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ignored) {
            // система не дала открыть — ничего не делаем
        }
    }

    // ------------------------------------------------------------- настройки

    /**
     * Усыпляет приставку так же, как кнопка питания: Android при этом сам отправляет
     * телевизору команду «выключиться» по HDMI-CEC.
     */
    static boolean sleepNow() {
        KeyService s = instance;
        if (s == null || Build.VERSION.SDK_INT < 28) {
            return false;
        }
        return s.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    /** Кнопки, которые нельзя назначать: без них не поработать с пультом. */
    static boolean isForbidden(int k) {
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_POWER:
                return true;
            default:
                return false;
        }
    }

    static int getHotkey(Context ctx) {
        return ctx.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).getInt(KEY_HOTKEY, 0);
    }

    static void setHotkey(Context ctx, int keyCode) {
        ctx.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_HOTKEY, keyCode).apply();
    }

    /** Включена ли служба в настройках специальных возможностей. */
    static boolean isEnabled(Context ctx) {
        if (connected) {
            return true;
        }
        try {
            String s = Settings.Secure.getString(ctx.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (s == null) {
                return false;
            }
            ComponentName me = new ComponentName(ctx, KeyService.class);
            for (String part : s.split(":")) {
                if (me.equals(ComponentName.unflattenFromString(part))) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // нет доступа к настройке — считаем выключенной
        }
        return false;
    }
}
