package app.recentstv;

import android.content.Intent;
import android.graphics.drawable.Drawable;

/** Одно недавнее приложение в списке. */
final class AppEntry {
    final String pkg;
    final String label;
    final Drawable icon;
    final Intent launch;
    final long lastUsed;
    /** true, пока плитка улетает вверх: защита от повторного нажатия. */
    boolean closing;

    AppEntry(String pkg, String label, Drawable icon, Intent launch, long lastUsed) {
        this.pkg = pkg;
        this.label = label;
        this.icon = icon;
        this.launch = launch;
        this.lastUsed = lastUsed;
    }
}
