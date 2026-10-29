package app.recentstv;

import android.animation.LayoutTransition;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Экран «Список приложений»: плитки приложений.
 * OK — открыть, ↑ — закрыть выбранное, ↓ — перейти к крестику «закрыть всё».
 */
public class MainActivity extends Activity {

    private static final int ACCENT_RED = 0xFFFF5A5F;
    private static final int ACCENT_BLUE = 0xFF4C8DFF;

    private final List<AppEntry> entries = new ArrayList<>();

    private HorizontalScrollView scroll;
    private LinearLayout row;
    private LinearLayout permBox;
    private TextView permBtn;
    private View closeAllBtn;
    private int lastFocusIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    // ------------------------------------------------------------------ данные

    private void refresh() {
        if (!RecentsRepo.hasUsageAccess(this)) {
            showPermission();
            return;
        }
        permBox.setVisibility(View.GONE);
        closeAllBtn.setVisibility(View.VISIBLE);
        entries.clear();
        entries.addAll(RecentsRepo.load(this));
        if (entries.isEmpty()) {
            leave();
            return;
        }
        rebuildRow();
    }

    private void rebuildRow() {
        row.removeAllViews();
        for (AppEntry e : entries) {
            row.addView(makeTile(e));
        }
        lastFocusIndex = 0;
        updateState();
        row.post(new Runnable() {
            @Override
            public void run() {
                if (row.getChildCount() > 0) {
                    row.getChildAt(0).requestFocus();
                } else {
                    closeAllBtn.requestFocus();
                }
            }
        });
    }

    private void updateState() {
        if (entries.isEmpty()) {
            leave();
        }
    }

    /** Список пуст — сразу уходим на главный экран. */
    private void leave() {
        finish();
        overridePendingTransition(0, 0);
    }

    private void showPermission() {
        entries.clear();
        row.removeAllViews();
        scroll.setVisibility(View.GONE);
        closeAllBtn.setVisibility(View.GONE);
        permBox.setVisibility(View.VISIBLE);
        permBox.post(new Runnable() {
            @Override
            public void run() {
                permBtn.requestFocus();
            }
        });
    }

    // ---------------------------------------------------------------- действия

    private void openEntry(AppEntry entry) {
        if (entry.closing) {
            return;
        }
        try {
            startActivity(entry.launch);
            finish();
            overridePendingTransition(0, 0);
        } catch (Exception ex) {
            Toast.makeText(this, "Не удалось открыть: " + entry.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void closeEntry(final AppEntry entry, final View tile) {
        if (entry.closing) {
            return;
        }
        entry.closing = true;
        RecentsRepo.kill(this, entry.pkg);
        RecentsRepo.dismiss(this, entry.pkg);

        // Фокус сразу уходит на соседнюю плитку, чтобы можно было закрывать подряд.
        int idx = row.indexOfChild(tile);
        int count = row.getChildCount();
        View next = null;
        for (int i = idx + 1; i < count && next == null; i++) {
            View c = row.getChildAt(i);
            if (!((AppEntry) c.getTag()).closing) {
                next = c;
            }
        }
        for (int i = idx - 1; i >= 0 && next == null; i--) {
            View c = row.getChildAt(i);
            if (!((AppEntry) c.getTag()).closing) {
                next = c;
            }
        }
        if (next != null) {
            next.requestFocus();
        } else {
            closeAllBtn.requestFocus();
        }

        tile.animate()
                .translationY(-dp(150))
                .alpha(0f)
                .scaleX(0.85f)
                .scaleY(0.85f)
                .setDuration(220)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        row.removeView(tile);
                        entries.remove(entry);
                        updateState();
                    }
                })
                .start();
    }

    private void closeAll() {
        if (entries.isEmpty()) {
            leave();
            return;
        }
        int delay = 0;
        for (int i = 0; i < row.getChildCount(); i++) {
            View t = row.getChildAt(i);
            AppEntry e = (AppEntry) t.getTag();
            if (e.closing) {
                continue;
            }
            e.closing = true;
            RecentsRepo.kill(this, e.pkg);
            RecentsRepo.dismiss(this, e.pkg);
            t.animate()
                    .translationY(-dp(150))
                    .alpha(0f)
                    .scaleX(0.85f)
                    .scaleY(0.85f)
                    .setStartDelay(delay)
                    .setDuration(220)
                    .start();
            delay += 45;
        }
        scroll.postDelayed(new Runnable() {
            @Override
            public void run() {
                row.removeAllViews();
                entries.clear();
                leave();
            }
        }, delay + 280L);
    }

    private void openUsageSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        } catch (Exception ex) {
            Toast.makeText(this,
                    "На этой приставке нет такого экрана — выдайте доступ командой ADB",
                    Toast.LENGTH_LONG).show();
        }
    }

    // ---------------------------------------------------------------------- UI

    private View buildUi() {
        FrameLayout rootFrame = new FrameLayout(this);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(56), dp(36), dp(56), dp(36));
        rootFrame.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Заголовок и подсказка
        TextView title = text("Список приложений", 38, Color.WHITE, true);
        content.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Средняя часть: плитки / пустое состояние / запрос доступа
        FrameLayout mid = new FrameLayout(this);
        content.addView(mid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        mid.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL));

        row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(dp(24), dp(28), dp(24), dp(28));
        LayoutTransition lt = new LayoutTransition();
        lt.disableTransitionType(LayoutTransition.APPEARING);
        lt.disableTransitionType(LayoutTransition.DISAPPEARING);
        lt.disableTransitionType(LayoutTransition.CHANGE_APPEARING);
        lt.setStartDelay(LayoutTransition.CHANGE_DISAPPEARING, 0);
        lt.setDuration(180);
        row.setLayoutTransition(lt);
        scroll.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        permBox = buildPermissionBox();
        permBox.setVisibility(View.GONE);
        mid.addView(permBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        // Нижняя кнопка «Закрыть всё»
        closeAllBtn = new CloseX(this);
        closeAllBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeAll();
            }
        });
        closeAllBtn.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN
                        && keyCode == KeyEvent.KEYCODE_DPAD_UP
                        && row.getChildCount() > 0) {
                    int i = Math.min(lastFocusIndex, row.getChildCount() - 1);
                    row.getChildAt(i).requestFocus();
                    return true;
                }
                return false;
            }
        });
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(dp(72), dp(72));
        btnLp.gravity = Gravity.CENTER_HORIZONTAL;
        btnLp.topMargin = dp(8);
        content.addView(closeAllBtn, btnLp);

        return rootFrame;
    }

    private LinearLayout buildPermissionBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView h = text("Нужен доступ к статистике использования", 28, Color.WHITE, true);
        h.setGravity(Gravity.CENTER);
        box.addView(h);

        TextView d = text("Без него приложение не знает, какие программы вы запускали.\n"
                        + "Выполните на компьютере одну команду (приставка подключена по ADB):",
                18, 0xCCFFFFFF, false);
        d.setGravity(Gravity.CENTER);
        d.setLineSpacing(0f, 1.2f);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dLp.topMargin = dp(14);
        box.addView(d, dLp);

        TextView cmd = text("adb shell appops set " + getPackageName() + " GET_USAGE_STATS allow",
                18, 0xFFFFFFFF, false);
        cmd.setTypeface(Typeface.MONOSPACE);
        cmd.setPadding(dp(20), dp(12), dp(20), dp(12));
        GradientDrawable cmdBg = new GradientDrawable();
        cmdBg.setCornerRadius(dp(12));
        cmdBg.setColor(0x4D000000);
        cmd.setBackground(cmdBg);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cLp.topMargin = dp(16);
        box.addView(cmd, cLp);

        TextView after = text("После выполнения вернитесь сюда — список обновится сам.",
                16, 0x99FFFFFF, false);
        LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        aLp.topMargin = dp(14);
        box.addView(after, aLp);

        permBtn = makeButton("Открыть настройки доступа", ACCENT_BLUE);
        permBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openUsageSettings();
            }
        });
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bLp.topMargin = dp(24);
        box.addView(permBtn, bLp);

        return box;
    }

    private View makeTile(final AppEntry entry) {
        final LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setPadding(dp(16), dp(24), dp(16), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(200), dp(236));
        lp.setMargins(dp(12), 0, dp(12), 0);
        tile.setLayoutParams(lp);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setTag(entry);

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(entry.icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(112), dp(112)));

        TextView name = text(entry.label, 17, Color.WHITE, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameLp.topMargin = dp(16);
        tile.addView(name, nameLp);

        styleTile(tile, false);

        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                styleTile((LinearLayout) v, hasFocus);
                float s = hasFocus ? 1.1f : 1f;
                v.animate().scaleX(s).scaleY(s).setDuration(140).start();
                if (hasFocus) {
                    lastFocusIndex = Math.max(0, row.indexOfChild(v));
                }
            }
        });
        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openEntry(entry);
            }
        });
        tile.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) {
                    return false;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    if (event.getRepeatCount() == 0) {
                        closeEntry(entry, v);
                    }
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    closeAllBtn.requestFocus();
                    return true;
                }
                return false;
            }
        });
        return tile;
    }

    private void styleTile(LinearLayout tile, boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(26));
        g.setColor(focused ? 0x40FFFFFF : 0x1AFFFFFF);
        g.setStroke(dp(focused ? 3 : 1), focused ? 0xFFFFFFFF : 0x22FFFFFF);
        tile.setBackground(g);
    }

    /** Кнопка «закрыть всё»: просто красивый крестик без круга. */
    private class CloseX extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        CloseX(android.content.Context ctx) {
            super(ctx);
            setFocusable(true);
            setClickable(true);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(dp(5));
        }

        @Override
        protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
            float s = gainFocus ? 1.35f : 1f;
            animate().scaleX(s).scaleY(s).setDuration(140).start();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            paint.setColor(isFocused() ? ACCENT_RED : 0x99FFFFFF);
            float w = getWidth();
            float h = getHeight();
            float r = Math.min(w, h) * 0.22f;
            float cx = w / 2f;
            float cy = h / 2f;
            c.drawLine(cx - r, cy - r, cx + r, cy + r, paint);
            c.drawLine(cx - r, cy + r, cx + r, cy - r, paint);
        }
    }

    private TextView makeButton(String label, final int accent) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(44), dp(14), dp(44), dp(14));
        b.setFocusable(true);
        b.setClickable(true);
        styleButton(b, accent, false);
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                styleButton((TextView) v, accent, hasFocus);
                float s = hasFocus ? 1.08f : 1f;
                v.animate().scaleX(s).scaleY(s).setDuration(140).start();
            }
        });
        return b;
    }

    private void styleButton(TextView b, int accent, boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(40));
        g.setColor(focused ? accent : (0x33000000 | (accent & 0x00FFFFFF)));
        g.setStroke(dp(2), focused ? 0xFFFFFFFF : (0x99000000 | (accent & 0x00FFFFFF)));
        b.setBackground(g);
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return t;
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
