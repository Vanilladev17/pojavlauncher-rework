package net.kdt.pojavlaunch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.utils.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ImportGameFileActivity extends Activity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Tools.checkStorageInteractive(this)) return;
        Tools.initStorageConstants(getApplicationContext());

        List<Uri> uris = collectUris();
        if (uris.isEmpty()) { finish(); return; }

        Instance instance = Instances.loadSelectedInstance();
        if (instance == null) {
            Toast.makeText(this, R.string.no_instance, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        instance.migrateToVersionIsolation();
        File gameDir = instance.getGameDirectory();
        FileUtils.ensureDirectorySilently(gameDir);

        String firstName = Tools.getFileName(this, uris.get(0));
        String suffix = uris.size() > 1 ? " (+" + (uris.size() - 1) + ")" : "";

        // Step 1: pick version. Only title + items (no message) so the list always renders.
        ArrayList<File> versionDirs = new ArrayList<>();
        ArrayList<String> versionLabels = new ArrayList<>();
        File versionsRoot = new File(Tools.DIR_HOME_VERSION);
        File[] all = versionsRoot.listFiles();
        if (all != null) {
            Arrays.sort(all, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (File f : all) {
                if (f.isDirectory() && new File(f, f.getName() + ".json").exists()) {
                    versionDirs.add(f);
                    versionLabels.add(f.getName());
                }
            }
        }
        versionLabels.add(getString(R.string.import_dest_profile));
        versionDirs.add(instance.getInstanceRootDirectory());

        if (versionDirs.size() == 1) {
            showFolderPicker(uris, versionDirs.get(0), true);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.import_file_where, firstName + suffix))
                .setItems(versionLabels.toArray(new String[0]), (dialog, which) ->
                        showFolderPicker(uris, versionDirs.get(which), which == versionLabels.size() - 1))
                .setOnCancelListener(d -> finish())
                .show();
    }

    private void showFolderPicker(List<Uri> uris, File baseDir, boolean isProfile) {
        String[] labels = {
                getString(R.string.import_dest_mods),
                getString(R.string.import_dest_resourcepacks),
                getString(R.string.import_dest_shaderpacks),
                getString(R.string.import_dest_saves),
                getString(R.string.import_dest_saves_unzip),
                getString(R.string.import_dest_config),
                isProfile ? getString(R.string.import_dest_profile) : getString(R.string.import_dest_root)
        };
        File[] targets = {
                new File(baseDir, "mods"),
                new File(baseDir, "resourcepacks"),
                new File(baseDir, "shaderpacks"),
                new File(baseDir, "saves"),
                new File(baseDir, "saves"),
                new File(baseDir, "config"),
                baseDir
        };
        boolean[] unzip = {false, false, false, false, true, false, false};
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.import_file_where_folder, baseDir.getName()))
                .setItems(labels, (dialog, which) -> doImport(uris, targets[which], unzip[which], baseDir.getName() + "/" + targets[which].getName()))
                .setOnCancelListener(d -> finish())
                .show();
    }

    private List<Uri> collectUris() {
        List<Uri> uris = new ArrayList<>();
        Intent intent = getIntent();
        if (intent == null) return uris;
        Uri data = intent.getData();
        if (data != null) uris.add(data);
        Object stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (stream instanceof Uri) uris.add((Uri) stream);
        else if (stream instanceof ArrayList) {
            for (Object o : (ArrayList<?>) stream) if (o instanceof Uri) uris.add((Uri) o);
        }
        if (uris.isEmpty() && intent.getClipData() != null) {
            for (int i = 0; i < intent.getClipData().getItemCount(); i++) {
                Uri u = intent.getClipData().getItemAt(i).getUri();
                if (u != null) uris.add(u);
            }
        }
        return uris;
    }

    private void doImport(List<Uri> uris, File targetDir, boolean unzip, String label) {
        new Thread(() -> {
            boolean ok = true;
            try {
                FileUtils.ensureDirectorySilently(targetDir);
                for (Uri uri : uris) {
                    String raw = Tools.getFileName(this, uri);
                    if (raw == null || raw.trim().isEmpty()) raw = "imported_file";
                    String name = new File(raw).getName().replaceAll("[\\\\/:*?\"<>|]", "_");
                    if (unzip && name.toLowerCase().endsWith(".zip")) {
                        File tmp = new File(getCacheDir(), name);
                        copyUri(uri, tmp);
                        String base = name.substring(0, name.length() - 4);
                        ok = unzipFile(tmp, uniqueFile(targetDir, base)) && ok;
                        tmp.delete();
                    } else {
                        copyUri(uri, uniqueFile(targetDir, name));
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("ImportGameFile", "Import failed", e);
                ok = false;
            }
            boolean finalOk = ok;
            runOnUiThread(() -> {
                Toast.makeText(this, finalOk ? getString(R.string.import_file_done, label) : getString(R.string.gamefiles_error), Toast.LENGTH_LONG).show();
                finish();
            });
        }).start();
    }

    private void copyUri(Uri uri, File dest) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(dest)) {
            if (in == null) throw new java.io.IOException("Cannot open " + uri);
            byte[] buf = new byte[8192]; int len;
            while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
        }
    }

    private File uniqueFile(File dir, String name) {
        File dest = new File(dir, name);
        if (!dest.exists()) return dest;
        String base = name, ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) { base = name.substring(0, dot); ext = name.substring(dot); }
        int i = 1;
        while (dest.exists()) dest = new File(dir, base + " (" + (i++) + ")" + ext);
        return dest;
    }

    private boolean unzipFile(File zip, File destDir) {
        try {
            String destPath = destDir.getCanonicalPath();
            FileUtils.ensureDirectory(destDir);
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zip))) {
                ZipEntry entry; byte[] buf = new byte[8192];
                while ((entry = zis.getNextEntry()) != null) {
                    File out = new File(destDir, entry.getName());
                    if (!out.getCanonicalPath().startsWith(destPath + File.separator)) continue;
                    if (entry.isDirectory()) FileUtils.ensureDirectorySilently(out);
                    else {
                        FileUtils.ensureParentDirectorySilently(out);
                        try (OutputStream os = new FileOutputStream(out)) { int len; while ((len = zis.read(buf)) != -1) os.write(buf, 0, len); }
                    }
                    zis.closeEntry();
                }
            }
            return true;
        } catch (Exception e) {
            android.util.Log.e("ImportGameFile", "Unzip failed", e);
            return false;
        }
    }
}
