package app.recentstv;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * «Список приложений»: стеклянные плитки в стиле tvOS.
 * OK — открыть, ↑ — закрыть выбранное, ↓ — перейти к крестику «закрыть всё».
 */
public class MainActivity extends Activity {

    private static final int ACCENT_RED = 0xFFFF5A5F;
    private static final int ACCENT_BLUE = 0xFF4C8DFF;

    /** Мягкое торможение — основа всей анимации. */
    private static final PathInterpolator EASE = new PathInterpolator(0.2f, 0f, 0f, 1f);
    /** Плавное ускорение для «улёта» плитки вверх. */
    private static final PathInterpolator FLY = new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);

    private final List<AppEntry> entries = new ArrayList<>();

    private HorizontalScrollView scroll;
    private LinearLayout row;
    private View closeAllBtn;
    private int lastFocusIndex = 0;
    private ValueAnimator scrollAnim;
    private boolean leaving = false;
    /** Приложение, открытое под списком (его нельзя убить, пока оно на экране). */
    private String behindPkg;

    /** Нужны службе: открыт ли список сейчас (повторное нажатие кнопки закрывает его). */
    static MainActivity instance;
    static volatile boolean visible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        leaving = false;
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        visible = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (instance == this) {
            instance = null;
        }
    }

    // ------------------------------------------------------------------ данные

    private void refresh() {
        if (!RecentsRepo.isReady(this)) {
            // Ничего не настроено — сначала экран настройки.
            try {
                startActivity(new Intent(this, SetupActivity.class));
            } catch (Exception ignored) {
                // нет экрана настройки — просто выходим
            }
            leave();
            return;
        }
        scroll.setVisibility(View.VISIBLE);
        closeAllBtn.setVisibility(View.VISIBLE);
        behindPkg = RecentsRepo.behindPackage(this);
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
        int i = 0;
        for (AppEntry e : entries) {
            View t = makeTile(e);
            row.addView(t);
            // Плавное появление: плитки по очереди выплывают снизу.
            t.setAlpha(0f);
            t.setTranslationY(dp(40));
            t.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(Math.min(i, 8) * 40L)
                    .setDuration(420)
                    .setInterpolator(EASE)
                    .start();
            i++;
        }
        lastFocusIndex = 0;
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
        if (leaving) {
            return;
        }
        leaving = true;
        finish();
        overridePendingTransition(0, 0);
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

    private View nearestOpenTile(int idx) {
        int count = row.getChildCount();
        for (int d = 0; d < count; d++) {
            int a = idx + d;
            int b = idx - d;
            if (a >= 0 && a < count && !((AppEntry) row.getChildAt(a).getTag()).closing) {
                return row.getChildAt(a);
            }
            if (b >= 0 && b < count && !((AppEntry) row.getChildAt(b).getTag()).closing) {
                return row.getChildAt(b);
            }
        }
        return null;
    }

    private void flyAway(View tile, long delay) {
        tile.animate()
                .translationY(-dp(130))
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setStartDelay(delay)
                .setDuration(280)
                .setInterpolator(FLY)
                .start();
        ObjectAnimator fade = ObjectAnimator.ofFloat(tile, View.ALPHA, 0f);
        fade.setStartDelay(delay);
        fade.setDuration(190);
        fade.start();
    }

    private void closeEntry(final AppEntry entry, final View tile) {
        if (entry.closing) {
            return;
        }
        entry.closing = true;
        final boolean behind = entry.pkg.equals(behindPkg);
        if (!behind) {
            RecentsRepo.kill(this, entry.pkg);
        }
        RecentsRepo.dismiss(this, entry.pkg);

        // Фокус сразу уходит на соседнюю плитку, чтобы можно было закрывать подряд.
        View next = nearestOpenTile(row.indexOfChild(tile));
        if (next != null) {
            next.requestFocus();
        }

        flyAway(tile, 0);

        if (behind) {
            // Приложение открыто под списком: сначала уходим на главный экран, потом убиваем.
            behindPkg = null;
            final boolean comeBack = next != null;
            scroll.postDelayed(new Runnable() {
                @Override
                public void run() {
                    List<String> one = new ArrayList<>();
                    one.add(entry.pkg);
                    goHomeThenKill(one, comeBack);
                    if (!comeBack) {
                        leave();
                    }
                }
            }, 260);
            return;
        }

        if (next == null) {
            // Это была последняя плитка — после улёта сразу на главный.
            scroll.postDelayed(new Runnable() {
                @Override
                public void run() {
                    entries.remove(entry);
                    updateState();
                }
            }, 230);
            return;
        }

        // Соседи плавно съезжаются на освободившееся место.
        final LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) tile.getLayoutParams();
        final int w = lp.width;
        final int ml = lp.leftMargin;
        final int mr = lp.rightMargin;
        ValueAnimator collapse = ValueAnimator.ofFloat(1f, 0f);
        collapse.setStartDelay(150);
        collapse.setDuration(320);
        collapse.setInterpolator(EASE);
        collapse.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float f = (float) a.getAnimatedValue();
                lp.width = Math.round(w * f);
                lp.leftMargin = Math.round(ml * f);
                lp.rightMargin = Math.round(mr * f);
                tile.setLayoutParams(lp);
            }
        });
        collapse.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                row.removeView(tile);
                entries.remove(entry);
                updateState();
            }
        });
        collapse.start();
    }

    private void closeAll() {
        if (entries.isEmpty()) {
            leave();
            return;
        }
        long delay = 0;
        final List<String> closedPkgs = new ArrayList<>();
        for (int i = 0; i < row.getChildCount(); i++) {
            View t = row.getChildAt(i);
            AppEntry e = (AppEntry) t.getTag();
            if (e.closing) {
                continue;
            }
            e.closing = true;
            RecentsRepo.kill(this, e.pkg);
            RecentsRepo.dismiss(this, e.pkg);
            closedPkgs.add(e.pkg);
            flyAway(t, delay);
            delay += 45;
        }
        scroll.postDelayed(new Runnable() {
            @Override
            public void run() {
                row.removeAllViews();
                entries.clear();
                if (behindPkg != null && closedPkgs.contains(behindPkg)) {
                    // Одно из приложений открыто под списком — его убиваем уже с главного экрана.
                    goHomeThenKill(closedPkgs, false);
                }
                leave();
            }
        }, delay + 300L);
    }

    /** Уходит на главный экран, затем убивает приложения (на переднем плане их убить нельзя). */
    private void goHomeThenKill(List<String> pkgs, final boolean comeBack) {
        final Context app = getApplicationContext();
        final List<String> list = new ArrayList<>(pkgs);
        try {
            startActivity(homeIntent());
        } catch (Exception ignored) {
            // нет главного экрана — всё равно попробуем убить
        }
        final Handler h = new Handler(Looper.getMainLooper());
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                for (String p : list) {
                    RecentsRepo.kill(app, p);
                }
            }
        }, 700);
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                for (String p : list) {
                    RecentsRepo.kill(app, p);
                }
            }
        }, 1500);
        if (comeBack) {
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent i = new Intent(app, MainActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        app.startActivity(i);
                    } catch (Exception ignored) {
                        // не дали открыть из фона — список откроется кнопкой
                    }
                }
            }, 1000);
        }
    }

    private Intent homeIntent() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo ri = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        if (ri != null && ri.activityInfo != null && !"android".equals(ri.activityInfo.packageName)) {
            home.setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name));
        }
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return home;
    }

    // --------------------------------------------------------- плавная прокрутка

    private int scrollTarget(View v) {
        int t = v.getLeft() + v.getWidth() / 2 - scroll.getWidth() / 2;
        int max = Math.max(0, row.getWidth() - scroll.getWidth());
        return Math.max(0, Math.min(t, max));
    }

    /** Плавно ставит плитку по центру; цель пересчитывается каждый кадр, пока соседи сдвигаются. */
    private void centerOn(final View v) {
        if (scrollAnim != null) {
            scrollAnim.cancel();
        }
        final int start = scroll.getScrollX();
        scrollAnim = ValueAnimator.ofFloat(0f, 1f);
        scrollAnim.setDuration(440);
        scrollAnim.setInterpolator(EASE);
        scrollAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float f = (float) a.getAnimatedValue();
                int target = scrollTarget(v);
                scroll.scrollTo(Math.round(start + (target - start) * f), 0);
            }
        });
        scrollAnim.start();
    }

    // ---------------------------------------------------------------------- UI

    private View buildUi() {
        FrameLayout rootFrame = new FrameLayout(this);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(40), 0, dp(28));
        rootFrame.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Заголовок
        TextView title = text("Список приложений", 32, 0xF2FFFFFF, false);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.leftMargin = dp(64);
        content.addView(title, titleLp);

        // Средняя часть: плитки / запрос доступа
        FrameLayout mid = new FrameLayout(this);
        content.addView(mid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        scroll = new HorizontalScrollView(this) {
            @Override
            protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
                // Прокрутку по фокусу делаем сами — плавно (centerOn).
                return 0;
            }
        };
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
        row.setPadding(dp(52), dp(30), dp(52), dp(30));
        scroll.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Нижняя кнопка «закрыть всё»
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
                    View t = nearestOpenTile(Math.min(lastFocusIndex, row.getChildCount() - 1));
                    if (t != null) {
                        t.requestFocus();
                    }
                    return true;
                }
                return false;
            }
        });
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(dp(120), dp(68));
        btnLp.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(closeAllBtn, btnLp);

        return rootFrame;
    }

    private View makeTile(final AppEntry entry) {
        final LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(208), dp(208));
        lp.setMargins(dp(14), 0, dp(14), 0);
        tile.setLayoutParams(lp);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setTag(entry);

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(entry.icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(104), dp(104)));

        TextView name = text(entry.label, 17, 0xCCFFFFFF, false);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
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
                float s = hasFocus ? 1.08f : 1f;
                v.animate()
                        .scaleX(s)
                        .scaleY(s)
                        .setStartDelay(0)
                        .setDuration(260)
                        .setInterpolator(EASE)
                        .start();
                if (hasFocus) {
                    lastFocusIndex = Math.max(0, row.indexOfChild(v));
                    centerOn(v);
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

    /** «Стекло»: полупрозрачная заливка, блик сверху, тонкая светлая кромка. */
    private void styleTile(LinearLayout tile, boolean focused) {
        float r = dp(34);

        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{focused ? 0x56FFFFFF : 0x30FFFFFF, focused ? 0x28FFFFFF : 0x12FFFFFF});
        base.setCornerRadius(r);
        base.setStroke(dp(focused ? 2 : 1), focused ? 0xF2FFFFFF : 0x40FFFFFF);

        GradientDrawable shine = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{focused ? 0x44FFFFFF : 0x26FFFFFF, 0x00FFFFFF});
        shine.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});

        LayerDrawable glass = new LayerDrawable(new Drawable[]{base, shine});
        glass.setLayerInset(1, dp(2), dp(2), dp(2), 0);
        glass.setLayerHeight(1, dp(96));
        glass.setLayerGravity(1, Gravity.TOP);
        tile.setBackground(glass);

        View name = tile.getChildAt(1);
        if (name instanceof TextView) {
            ((TextView) name).setTextColor(focused ? 0xFFFFFFFF : 0xB3FFFFFF);
        }
    }

    /**
     * Кнопка «закрыть всё»: тонкий крестик. При фокусе плавно проявляется
     * стеклянная красноватая капсула, а крестик становится белым.
     */
    private class CloseX extends View {
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float amt = 0f;
        private ValueAnimator anim;

        CloseX(Context ctx) {
            super(ctx);
            setFocusable(true);
            setClickable(true);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setStrokeWidth(dp(3));
            fill.setStyle(Paint.Style.FILL);
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(dp(1.5f));
        }

        @Override
        protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
            float target = gainFocus ? 1f : 0f;
            if (anim != null) {
                anim.cancel();
            }
            anim = ValueAnimator.ofFloat(amt, target);
            anim.setDuration(260);
            anim.setInterpolator(EASE);
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    amt = (float) a.getAnimatedValue();
                    invalidate();
                }
            });
            anim.start();
            float s = gainFocus ? 1.1f : 1f;
            animate().scaleX(s).scaleY(s).setStartDelay(0).setDuration(260)
                    .setInterpolator(EASE).start();
        }

        private int lerp(int from, int to, float t) {
            int a = Math.round(Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t);
            int r = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t);
            int g = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t);
            int b = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
            return Color.argb(a, r, g, b);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth();
            float h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;

            if (amt > 0.01f) {
                rect.set(dp(6), dp(8), w - dp(6), h - dp(8));
                float rad = rect.height() / 2f;
                fill.setColor(Color.argb(Math.round(0x66 * amt), 255, 90, 95));
                c.drawRoundRect(rect, rad, rad, fill);
                edge.setColor(Color.argb(Math.round(0xB0 * amt), 255, 255, 255));
                c.drawRoundRect(rect, rad, rad, edge);
            }

            line.setColor(lerp(0x99FFFFFF, 0xFFFFFFFF, amt));
            float r = dp(9);
            c.drawLine(cx - r, cy - r, cx + r, cy + r, line);
            c.drawLine(cx - r, cy + r, cx + r, cy - r, line);
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
                v.animate().scaleX(s).scaleY(s).setStartDelay(0).setDuration(240)
                        .setInterpolator(EASE).start();
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
