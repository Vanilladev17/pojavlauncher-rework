package net.kdt.pojavlaunch.modloaders.modpacks.api;

import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.modloaders.modpacks.api.modloader.LoaderInstaller;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.utils.FileUtils;

import java.io.File;
import java.io.IOException;

/**
 * Каталог модов Modrinth: ищем мод, выбираем версию — файл скачивается прямо в папку mods
 * выбранного профиля. Профиль не пересоздаётся, ничего не устанавливается — только сам мод.
 */
public class ModrinthModApi extends ModrinthApi {

    /** Отдельный «API», который вешает .jar в mods текущего профиля */
    @Override
    public LoaderInstaller installModpack(ModDetail modDetail, int selectedVersion) throws IOException {
        if (modDetail == null || modDetail.versionUrls == null
                || selectedVersion < 0 || selectedVersion >= modDetail.versionUrls.length) {
            throw new IOException("No downloadable version selected");
        }
        String downloadUrl = modDetail.versionUrls[selectedVersion];
        if (downloadUrl == null || downloadUrl.isEmpty()) {
            throw new IOException("Mod version has no download url");
        }

        Instance instance = Instances.loadSelectedInstance();
        if (instance == null) throw new IOException("No selected profile");

        File modsDirectory = new File(instance.getGameDirectory(), "mods");
        FileUtils.ensureDirectory(modsDirectory);
        if (!modsDirectory.isDirectory() || !modsDirectory.canWrite()) {
            throw new IOException("Cannot write into mods directory: " + modsDirectory);
        }

        String fileName = sanitizeFileName(modDetail.title);
        if (!fileName.toLowerCase().endsWith(".jar")) fileName += ".jar";
        File targetFile = uniqueFile(modsDirectory, fileName);

        DownloadUtils.downloadFile(downloadUrl, targetFile);
        // Лоадер ставить не нужно: мод уже лежит в mods
        return new InstalledModInstaller(targetFile);
    }

    @Override
    public LoaderInstaller installLocalModpack(String modpackName, File modpackFile, String icon) throws IOException {
        throw new IOException("Local modpack import is not supported by the mod catalogue");
    }

    private static String sanitizeFileName(String name) {
        if (name == null || name.trim().isEmpty()) return "mod";
        String cleaned = name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        return cleaned.length() > 80 ? cleaned.substring(0, 80) : cleaned;
    }

    private static File uniqueFile(File directory, String fileName) {
        File candidate = new File(directory, fileName);
        if (!candidate.exists()) return candidate;
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String extension = dot > 0 ? fileName.substring(dot) : "";
        for (int i = 1; i < 1000; i++) {
            candidate = new File(directory, base + " (" + i + ")" + extension);
            if (!candidate.exists()) return candidate;
        }
        return new File(directory, base + "-" + System.currentTimeMillis() + extension);
    }

    /** Заглушка-установщик: сообщает, что мод уже на месте */
    private static class InstalledModInstaller implements LoaderInstaller {
        private final File mInstalledFile;

        InstalledModInstaller(File installedFile) {
            this.mInstalledFile = installedFile;
        }

        @Override
        public boolean requiresGuiInstallation() {
            return false;
        }

        @Override
        public net.kdt.pojavlaunch.instances.InstanceInstaller createInstaller() throws IOException {
            throw new IOException("Mod is already installed to " + mInstalledFile);
        }

        @Override
        public String installHeadlessly() throws IOException {
            return "Installed: " + mInstalledFile.getAbsolutePath();
        }
    }
}
