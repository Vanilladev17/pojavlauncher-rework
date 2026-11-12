package net.kdt.pojavlaunch;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;

import net.kdt.pojavlaunch.authenticator.accounts.Account;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.tasks.AsyncAssetManager;
import net.kdt.pojavlaunch.tasks.MoJsonDownloader;

/**
 * Невидимый посредник запуска с ярлыка на рабочем столе.
 *
 * Ярлык ведёт сюда: выставляем нужный профиль (и аккаунт, если он был на момент создания ярлыка),
 * затем сразу поднимаем LauncherActivity, который выполняет обычный сценарий «Играть».
 * Меню лаунчера пользователь не видит.
 */
public class ShortcutLaunchActivity extends Activity {
    private static final int REQUEST_STORAGE_REQUEST_CODE = 1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT < 29
                && !TestStorageActivity.isStorageAllowed(this)) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_STORAGE_REQUEST_CODE);
            return;
        }
        startGame(getIntent());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_STORAGE_REQUEST_CODE) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startGame(getIntent());
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.global_error)
                    .setMessage(R.string.toast_permission_denied)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                    .show();
        }
    }

    private void startGame(Intent intent) {
        try {
            LauncherPreferences.loadPreferences(this);
            AsyncAssetManager.unpackComponents(this);
            MoJsonDownloader.prepareSubstitutionMap(getAssets());

            if (!Tools.checkStorageRoot(this)) {
                startActivity(new Intent(this, MissingStorageActivity.class));
                finish();
                return;
            }

            applyShortcutPayload(intent);

            Intent launcherIntent = new Intent(this, LauncherActivity.class);
            launcherIntent.setAction(Intent.ACTION_MAIN);
            launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            launcherIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            launcherIntent.putExtra(LauncherActivity.EXTRA_AUTO_LAUNCH, true);
            startActivity(launcherIntent);
        } catch (Throwable throwable) {
            Tools.showError(this, throwable);
        }
        finish();
    }

    /** Выставляет профиль и аккаунт, зашитые в ярлык. */
    private void applyShortcutPayload(Intent intent) {
        if (intent == null) return;
        String payload = intent.getStringExtra(ToolsLauncherShortcuts.EXTRA_PAYLOAD);
        if (payload == null || !payload.contains("\n")) return;

        String[] parts = payload.split("\n", -1);
        String instanceDirectory = parts[0].trim();
        String accountFile = parts.length > 1 ? parts[1].trim() : "";

        try {
            Instances.selectInstanceByDirectory(instanceDirectory);
        } catch (Throwable ignored) { }

        if (!accountFile.isEmpty()) {
            try {
                for (Account account : Accounts.load().accounts) {
                    if (account != null && accountFile.equals(account.getSaveFileName())) {
                        Accounts.setCurrent(account);
                        break;
                    }
                }
            } catch (Throwable ignored) { }
        }
        ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
    }
}
