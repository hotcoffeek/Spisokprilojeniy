package app.recentstv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Настройка: включить службу и назначить кнопку вызова списка. */
public class SetupActivity extends Activity {

    private static final int GREEN = 0xFF5BD98A;
    private static final int RED = 0xFFFF6B6F;
    private static final PathInterpolator EASE = new PathInterpolator(0.2f, 0f, 0f, 1f);

    private TextView svcStatus;
    private TextView svcHint;
    private TextView infoBtn;
    private TextView keyStatus;
    private TextView svcBtn;
    private boolean capturing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        update();
        svcBtn.requestFocus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopCapture();
    }

    // ------------------------------------------------------------------ логика

    private void update() {
        boolean on = KeyService.isEnabled(this);
        svcStatus.setText(on ? "Включена" : "Выключена");
        svcStatus.setTextColor(on ? GREEN : RED);
        svcHint.setVisibility(on ? View.GONE : View.VISIBLE);
        infoBtn.setVisibility(on ? View.GONE : View.VISIBLE);

        if (capturing) {
            keyStatus.setText("Нажмите нужную кнопку на пульте…   (Назад — отмена)");
            keyStatus.setTextColor(0xFFFFFFFF);
        } else {
            int k = KeyService.getHotkey(this);
            if (k == 0) {
                keyStatus.setText("Не назначена");
                keyStatus.setTextColor(RED);
            } else {
                keyStatus.setText(KeyEvent.keyCodeToString(k).replace("KEYCODE_", ""));
                keyStatus.setTextColor(GREEN);
            }
        }
    }

    private void startCapture() {
        capturing = true;
        KeyService.assigning = true;
        update();
    }

    private void stopCapture() {
        capturing = false;
        KeyService.assigning = false;
    }

    private boolean isForbidden(int k) {
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

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (capturing) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                int k = e.getKeyCode();
                if (k == KeyEvent.KEYCODE_BACK) {
                    stopCapture();
                    update();
                } else if (isForbidden(k)) {
                    keyStatus.setText("Эта кнопка не подходит — нажмите другую");
                    keyStatus.setTextColor(RED);
                } else {
                    KeyService.setHotkey(this, k);
                    stopCapture();
                    update();
                }
            }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception ex) {
            Toast.makeText(this, "Откройте: Настройки → Специальные возможности",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void openAppInfo() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception ex) {
            Toast.makeText(this, "На этой приставке нет такого экрана", Toast.LENGTH_LONG).show();
        }
    }

    // ---------------------------------------------------------------------- UI

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(64), dp(44), dp(64), dp(32));

        TextView title = text("Список приложений", 32, 0xF2FFFFFF);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        root.addView(title);

        TextView sub = text("Настройка", 20, 0x99FFFFFF);
        LinearLayout.LayoutParams subLp = wrap();
        subLp.topMargin = dp(4);
        root.addView(sub, subLp);

        // Шаг 1
        LinearLayout c1 = card();
        c1.addView(heading("1.  Служба специальных возможностей"));
        svcStatus = text("", 20, RED);
        svcStatus.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams sLp = wrap();
        sLp.topMargin = dp(8);
        c1.addView(svcStatus, sLp);
        svcHint = text("Откроется список служб: выберите «Список приложений» и включите.\n"
                + "Если переключатель серый — откройте «Сведения о приложении», "
                + "нажмите ⋮ и выберите «Разрешить ограниченные настройки».", 16, 0xB3FFFFFF);
        svcHint.setLineSpacing(0f, 1.15f);
        LinearLayout.LayoutParams hLp = wrap();
        hLp.topMargin = dp(8);
        c1.addView(svcHint, hLp);

        LinearLayout btns1 = new LinearLayout(this);
        btns1.setOrientation(LinearLayout.HORIZONTAL);
        svcBtn = button("Открыть настройки службы", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAccessibilitySettings();
            }
        });
        btns1.addView(svcBtn, wrap());
        infoBtn = button("Сведения о приложении", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAppInfo();
            }
        });
        LinearLayout.LayoutParams iLp = wrap();
        iLp.leftMargin = dp(16);
        btns1.addView(infoBtn, iLp);
        LinearLayout.LayoutParams b1Lp = wrap();
        b1Lp.topMargin = dp(16);
        c1.addView(btns1, b1Lp);
        root.addView(c1, cardLp());

        // Шаг 2
        LinearLayout c2 = card();
        c2.addView(heading("2.  Кнопка вызова списка"));
        keyStatus = text("", 20, RED);
        keyStatus.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams kLp = wrap();
        kLp.topMargin = dp(8);
        c2.addView(keyStatus, kLp);
        TextView keyBtn = button("Назначить кнопку", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startCapture();
            }
        });
        LinearLayout.LayoutParams k2Lp = wrap();
        k2Lp.topMargin = dp(16);
        c2.addView(keyBtn, k2Lp);
        root.addView(c2, cardLp());

        return root;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(28), dp(22), dp(28), dp(24));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x30FFFFFF, 0x12FFFFFF});
        g.setCornerRadius(dp(28));
        g.setStroke(dp(1), 0x40FFFFFF);
        c.setBackground(g);
        return c;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(20);
        return lp;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private TextView heading(String s) {
        TextView t = text(s, 22, Color.WHITE);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private TextView button(String label, View.OnClickListener click) {
        TextView b = text(label, 18, Color.WHITE);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(32), dp(12), dp(32), dp(12));
        b.setFocusable(true);
        b.setClickable(true);
        styleButton(b, false);
        b.setOnClickListener(click);
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                styleButton((TextView) v, hasFocus);
                float s = hasFocus ? 1.06f : 1f;
                v.animate().scaleX(s).scaleY(s).setStartDelay(0).setDuration(220)
                        .setInterpolator(EASE).start();
            }
        });
        return b;
    }

    private void styleButton(TextView b, boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(40));
        g.setColor(focused ? 0x66FFFFFF : 0x26FFFFFF);
        g.setStroke(dp(focused ? 2 : 1), focused ? 0xF2FFFFFF : 0x55FFFFFF);
        b.setBackground(g);
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
