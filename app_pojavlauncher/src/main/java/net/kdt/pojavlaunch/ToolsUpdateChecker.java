package net.kdt.pojavlaunch;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Авто-обновление из GitHub Releases.
 *
 * Проверяет последний релиз репозитория Rework, сравнивает версию с установленной
 * и предлагает обновиться: APK скачивается, после чего открывается системный установщик.
 */
public class ToolsUpdateChecker {
    /** Репозиторий, откуда берутся обновления */
    public static final String RELEASE_REPO = "Vanilladev17/pojavlauncher-rework";
    private static final String LATEST_RELEASE_API = "https://api.github.com/repos/" + RELEASE_REPO + "/releases/latest";
    private static final String APK_ASSET_NAME = "PojavLauncher-Rework.apk";
    private static final String APK_CACHE_NAME = "rework_update.apk";
    /** Чтобы не спамить запросами: успешная проверка не чаще раза в 12 часов, ручная — всегда */
    private static final long CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L;
    private static final String PREF_LAST_CHECK = "reworkLastUpdateCheck";
    private static final String PREF_LAST_SEEN_TAG = "reworkLastSeenUpdateTag";
    private static final AtomicBoolean sCheckRunning = new AtomicBoolean(false);

    /** Данные о найденном релизе */
    public static class ReleaseInfo {
        public String tagName;
        public String notes;
        public String apkUrl;
        public long apkSize;
    }

    /** Полный адрес скачивания (не зависит от того, прикреплён файл или нет) */
    private static String latestApkUrl() {
        return "https://github.com/" + RELEASE_REPO + "/releases/latest/download/" + APK_ASSET_NAME;
    }

    /**
     * Тихая проверка при старте лаунчера.
     */
    public static void checkOnStartup(Activity activity) {
        if (activity == null || !LauncherPreferences.PREF_CHECK_UPDATES) return;
        long lastCheck = LauncherPreferences.DEFAULT_PREF.getLong(PREF_LAST_CHECK, 0L);
        if (System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) return;
        check(activity, false);
    }

    /**
     * Проверка по кнопке из настроек: результат показывается всегда, даже если обновлений нет.
     */
    public static void checkManually(Activity activity) {
        check(activity, true);
    }

    private static void check(final Activity activity, final boolean manual) {
        if (!sCheckRunning.compareAndSet(false, true)) return;
        if (manual) Toast.makeText(activity, R.string.update_check_started, Toast.LENGTH_SHORT).show();

        PojavApplication.sExecutorService.submit(() -> {
            ReleaseInfo releaseInfo = null;
            Throwable error = null;
            try {
                releaseInfo = fetchLatestRelease();
            } catch (Throwable throwable) {
                error = throwable;
            }
            final ReleaseInfo finalRelease = releaseInfo;
            final Throwable finalError = error;

            Tools.runOnUiThread(() -> {
                sCheckRunning.set(false);
                if (finalError != null || finalRelease == null) {
                    if (manual) {
                        Toast.makeText(activity, R.string.update_check_failed, Toast.LENGTH_LONG).show();
                    }
                    return;
                }
                LauncherPreferences.DEFAULT_PREF.edit()
                        .putLong(PREF_LAST_CHECK, System.currentTimeMillis())
                        .apply();

                if (!isNewer(finalRelease.tagName)) {
                    if (manual) Toast.makeText(activity, R.string.update_up_to_date, Toast.LENGTH_LONG).show();
                    return;
                }
                LauncherPreferences.DEFAULT_PREF.edit()
                        .putString(PREF_LAST_SEEN_TAG, finalRelease.tagName)
                        .apply();
                showUpdateDialog(activity, finalRelease);
            });
        });
    }

    // ------------------------------------------------------------------ сеть

    private static ReleaseInfo fetchLatestRelease() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(LATEST_RELEASE_API).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        try {
            int code = connection.getResponseCode();
            if (code != 200) throw new IllegalStateException("GitHub API HTTP " + code);
            String body;
            try (InputStream inputStream = connection.getInputStream()) {
                body = new String(readAll(inputStream), "UTF-8");
            }
            JsonElement element = JsonParser.parseString(body);
            if (!element.isJsonObject()) throw new IllegalStateException("Unexpected response");
            JsonObject jsonObject = element.getAsJsonObject();

            ReleaseInfo releaseInfo = new ReleaseInfo();
            releaseInfo.tagName = jsonObject.get("tag_name").getAsString();
            if (jsonObject.has("body") && !jsonObject.get("body").isJsonNull()) {
                releaseInfo.notes = jsonObject.get("body").getAsString();
            }
            releaseInfo.apkUrl = latestApkUrl();
            if (jsonObject.has("assets")) {
                JsonArray assets = jsonObject.getAsJsonArray("assets");
                for (JsonElement assetElement : assets) {
                    JsonObject asset = assetElement.getAsJsonObject();
                    if (!asset.has("name") || asset.get("name").isJsonNull()) continue;
                    if (!APK_ASSET_NAME.equals(asset.get("name").getAsString())) continue;
                    if (asset.has("browser_download_url")) {
                        releaseInfo.apkUrl = asset.get("browser_download_url").getAsString();
                    }
                    if (asset.has("size")) releaseInfo.apkSize = asset.get("size").getAsLong();
                }
            }
            return releaseInfo;
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] readAll(InputStream inputStream) throws Exception {
        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        int read;
        while ((read = inputStream.read(buffer)) > 0) outputStream.write(buffer, 0, read);
        return outputStream.toByteArray();
    }

    // ------------------------------------------------------------------ версии

    /** Текущая версия Rework в виде "1.0.2" (задаётся в build.gradle как resValue rework_version) */
    public static String getCurrentVersionName(Context context) {
        try {
            return context.getString(R.string.rework_version);
        } catch (Throwable throwable) {
            return "0";
        }
    }

    /** Сравнение версий вида 1.0.1 / v1.0.1 / 1.2 / 2.0.0 */
    static boolean isNewer(String releaseTag) {
        if (releaseTag == null) return false;
        String current = sCurrentVersion != null ? sCurrentVersion : "0";
        int[] remote = parseVersion(releaseTag);
        int[] local = parseVersion(current);
        for (int i = 0; i < 3; i++) {
            if (remote[i] != local[i]) return remote[i] > local[i];
        }
        return false;
    }

    private static String sCurrentVersion;
    public static void init(Context context) {
        sCurrentVersion = getCurrentVersionName(context);
    }

    private static int[] parseVersion(String version) {
        int[] result = new int[]{0, 0, 0};
        if (version == null) return result;
        String cleaned = version.trim().replaceFirst("^[vV]", "");
        String[] parts = cleaned.split("[^0-9]+");
        int index = 0;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (index >= 3) break;
            try {
                result[index] = Integer.parseInt(part);
            } catch (NumberFormatException ignored) {
                result[index] = 0;
            }
            index++;
        }
        return result;
    }

    // ------------------------------------------------------------------ диалог и установка

    private static void showUpdateDialog(final Activity activity, final ReleaseInfo releaseInfo) {
        String notes = releaseInfo.notes;
        if (notes != null && notes.length() > 900) notes = notes.substring(0, 900) + "…";
        String message = activity.getString(R.string.update_available_message,
                getCurrentVersionName(activity), releaseInfo.tagName);
        if (notes != null && !notes.trim().isEmpty()) message += "\n\n" + notes;

        new AlertDialog.Builder(activity)
                .setTitle(R.string.update_available_title)
                .setMessage(message)
                .setPositiveButton(R.string.update_download_button, (dialog, which) ->
                        downloadAndInstall(activity, releaseInfo))
                .setNegativeButton(R.string.update_later_button, null)
                .show();
    }

    private static void downloadAndInstall(final Activity activity, final ReleaseInfo releaseInfo) {
        final File targetFile = new File(activity.getCacheDir(), APK_CACHE_NAME);
        Toast.makeText(activity, R.string.update_downloading, Toast.LENGTH_LONG).show();

        PojavApplication.sExecutorService.submit(() -> {
            Throwable error = null;
            try {
                downloadFile(releaseInfo.apkUrl, targetFile);
            } catch (Throwable throwable) {
                error = throwable;
            }
            final Throwable finalError = error;

            Tools.runOnUiThread(() -> {
                if (finalError != null) {
                    Tools.showError(activity, finalError);
                    return;
                }
                installApk(activity, targetFile);
            });
        });
    }

    private static void downloadFile(String url, File target) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(60000);
        connection.setRequestProperty("User-Agent", "PojavLauncher-Rework-Updater");
        try {
            int code = connection.getResponseCode();
            if (code != 200) throw new IllegalStateException("HTTP " + code);
            File temp = new File(target.getAbsolutePath() + ".part");
            try (InputStream inputStream = connection.getInputStream();
                 FileOutputStream outputStream = new FileOutputStream(temp)) {
                byte[] buffer = new byte[65536];
                int read;
                while ((read = inputStream.read(buffer)) > 0) outputStream.write(buffer, 0, read);
            }
            if (target.exists() && !target.delete()) {
                // не критично: перезапишем ниже
            }
            if (!temp.renameTo(target)) throw new IllegalStateException("Cannot move downloaded APK");
        } finally {
            connection.disconnect();
        }
    }

    private static void installApk(Activity activity, File apkFile) {
        try {
            Uri apkUri;
            if (Build.VERSION.SDK_INT >= 24) {
                apkUri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".reworkupdate", apkFile);
            } else {
                apkUri = Uri.fromFile(apkFile);
            }
            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(installIntent);
        } catch (Throwable throwable) {
            Tools.showError(activity, throwable);
        }
    }
}
