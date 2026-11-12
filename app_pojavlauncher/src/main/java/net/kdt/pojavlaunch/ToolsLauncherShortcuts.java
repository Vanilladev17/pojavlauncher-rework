package net.kdt.pojavlaunch;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.os.Build;

import net.kdt.pojavlaunch.authenticator.accounts.Account;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.instances.Instance;

import java.util.Collections;

/**
 * Ярлыки профилей на рабочем столе: тапнул «1.12.2 Forge» — игра запускается сразу,
 * без входа в лаунчер и без нажатия «Играть».
 *
 * На Android 8+ используется системный запрос «Добавить на главный экран»
 * (shortcut API), на более старых версиях — старый broadcast INSTALL_SHORTCUT.
 */
public class ToolsLauncherShortcuts {
    /** Данные ярлыка: имя папки профиля + имя файла аккаунта */
    public static final String EXTRA_PAYLOAD = "rework_shortcut_payload";
    private static final String ACTION_INSTALL_SHORTCUT = "com.android.launcher.action.INSTALL_SHORTCUT";

    /**
     * Создаёт ярлык запуска игры для указанного профиля.
     * @return null при успехе, иначе короткий текст ошибки/подсказки для тоста
     */
    public static String createInstanceShortcut(Context context, Instance instance) {
        if (instance == null || instance.getInstanceRootDirectory() == null) {
            return context.getString(R.string.shortcut_no_instance);
        }
        String label = instance.name != null && !instance.name.trim().isEmpty()
                ? instance.name.trim()
                : instance.versionId;
        if (label == null) label = context.getString(R.string.app_short_name);

        String accountFile = "";
        try {
            Account current = Accounts.getCurrent();
            if (current != null) accountFile = current.getSaveFileName();
        } catch (Throwable ignored) { }

        Intent launchIntent = new Intent(context, ShortcutLaunchActivity.class);
        launchIntent.setAction(Intent.ACTION_MAIN);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        launchIntent.putExtra(EXTRA_PAYLOAD, instance.getInstanceRootDirectory().getName() + "\n" + accountFile);

        Bitmap icon = appIconBitmap(context);

        if (Build.VERSION.SDK_INT >= 26) {
            try {
                ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);
                if (shortcutManager != null) {
                    ShortcutInfo.Builder builder = new ShortcutInfo.Builder(context, "rework_" + instance.getInstanceRootDirectory().getName())
                            .setShortLabel(label)
                            .setLongLabel(context.getString(R.string.shortcut_play_label, label))
                            .setIntent(launchIntent);
                    if (icon != null) builder.setIcon(Icon.createWithBitmap(icon));
                    ShortcutInfo shortcutInfo = builder.build();

                    if (shortcutManager.isRequestPinShortcutSupported()) {
                        shortcutManager.requestPinShortcut(shortcutInfo, null);
                        return null;
                    }
                    // Лончер не умеет пинить ярлыки — публикуем как динамический:
                    // попадёт в меню long-press по иконке лаунчера
                    shortcutManager.setDynamicShortcuts(Collections.singletonList(shortcutInfo));
                    return context.getString(R.string.shortcut_in_menu_hint);
                }
            } catch (Throwable ignored) { }
        }

        // Старый путь (Android 5–7 и некоторые лончеры): ярлык появляется сразу на рабочем столе
        Intent putIntent = new Intent();
        putIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent);
        putIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, label);
        putIntent.putExtra("duplicate", false);
        if (icon != null) putIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON, icon);
        putIntent.setAction(ACTION_INSTALL_SHORTCUT);
        context.sendBroadcast(putIntent);
        return null;
    }

    private static Bitmap appIconBitmap(Context context) {
        try {
            return android.graphics.BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher);
        } catch (Throwable throwable) {
            return null;
        }
    }
}
