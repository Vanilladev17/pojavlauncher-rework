package net.kdt.pojavlaunch.fragments;

import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.pickafile.FileListView;
import com.kdt.pickafile.FileSelectedListener;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.utils.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class GameFilesFragment extends Fragment {
    public static final String TAG = "GameFilesFragment";
    private FileListView mFileListView;
    private TextView mPathView;
    private File mGameDir;
    private File mPendingImportDir;

    private final ActivityResultLauncher<String[]> mImportLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
                if (uris == null || uris.isEmpty() || mPendingImportDir == null) return;
                File targetDir = mPendingImportDir;
                PojavApplication.sExecutorService.execute(() -> importFiles(uris, targetDir));
            });

    public GameFilesFragment() { super(R.layout.fragment_game_files); }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Instance instance = Instances.loadSelectedInstance();
        if (instance == null) {
            Toast.makeText(requireContext(), R.string.no_instance, Toast.LENGTH_LONG).show();
            Tools.removeCurrentFragment(requireActivity());
            return;
        }
        instance.migrateToVersionIsolation();
        mGameDir = instance.getGameDirectory();
        FileUtils.ensureDirectorySilently(mGameDir);
        for (String sub : new String[]{"mods", "resourcepacks", "shaderpacks", "saves", "config"}) {
            FileUtils.ensureDirectorySilently(new File(mGameDir, sub));
        }
        mFileListView = view.findViewById(R.id.game_files_list);
        mPathView = view.findViewById(R.id.game_files_current_path);
        mFileListView.setShowFiles(true);
        mFileListView.setShowFolders(true);
        mFileListView.setDialogTitleListener(title -> mPathView.setText(shortenPath(title)));
        mFileListView.setFileSelectedListener(new FileSelectedListener() {
            @Override public void onFileSelected(File file, String path) { showFileActions(file); }
            @Override public void onFileLongClick(File file, String path) { showFileActions(file); }
        });
        goTo(mGameDir);
        bindJump(view, R.id.game_files_jump_root, mGameDir);
        bindJump(view, R.id.game_files_jump_mods, new File(mGameDir, "mods"));
        bindJump(view, R.id.game_files_jump_resourcepacks, new File(mGameDir, "resourcepacks"));
        bindJump(view, R.id.game_files_jump_shaderpacks, new File(mGameDir, "shaderpacks"));
        bindJump(view, R.id.game_files_jump_saves, new File(mGameDir, "saves"));
        bindJump(view, R.id.game_files_jump_versions, new File(Tools.DIR_HOME_VERSION));
        bindJump(view, R.id.game_files_jump_profile, instance.getInstanceRootDirectory());
        bindJump(view, R.id.game_files_jump_config, new File(mGameDir, "config"));
        view.findViewById(R.id.game_files_import_mod).setOnClickListener(v -> startImport(new File(mGameDir, "mods"), new String[]{"application/java-archive", "*/*"}));
        view.findViewById(R.id.game_files_import_pack).setOnClickListener(v -> startImport(new File(mGameDir, "resourcepacks"), new String[]{"application/zip", "*/*"}));
        view.findViewById(R.id.game_files_import_here).setOnClickListener(v -> startImport(mFileListView.getFullPath(), new String[]{"*/*"}));
        view.findViewById(R.id.game_files_create_folder).setOnClickListener(v -> createFolder());
    }

    private String shortenPath(String path) {
        if (path == null) return ".";
        String home = Tools.DIR_GAME_HOME;
        if (home != null && path.startsWith(home)) return path.substring(home.length()).replaceFirst("^/", "");
        int idx = path.indexOf("/.minecraft/");
        if (idx >= 0) return path.substring(idx + 1);
        return path;
    }

    private void bindJump(View view, int id, File dir) {
        Button b = view.findViewById(id);
        if (b != null) b.setOnClickListener(v -> goTo(dir));
    }

    private void goTo(File dir) {
        if (dir != null) FileUtils.ensureDirectorySilently(dir);
        mFileListView.lockPathAt(dir);
        mFileListView.refreshPath();
    }

    private void startImport(File targetDir, String[] mimeTypes) {
        FileUtils.ensureDirectorySilently(targetDir);
        mPendingImportDir = targetDir;
        mImportLauncher.launch(mimeTypes);
    }

    private void importFiles(List<Uri> uris, File targetDir) {
        int imported = 0;
        for (Uri uri : uris) {
            try {
                String rawName = Tools.getFileName(requireContext(), uri);
                if (rawName == null || rawName.trim().isEmpty()) rawName = "imported_file";
                String name = new File(rawName).getName().replaceAll("[\\\\/:*?\"<>|]", "_");
                File dest = uniqueFile(targetDir, name);
                try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(dest)) {
                    if (in == null) continue;
                    byte[] buf = new byte[8192]; int len;
                    while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
                }
                imported++;
            } catch (Exception e) { android.util.Log.e(TAG, "Import failed", e); }
        }
        int finalImported = imported;
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            Toast.makeText(requireContext(), getString(R.string.gamefiles_imported, finalImported, targetDir.getName()), Toast.LENGTH_LONG).show();
            if (targetDir.equals(mFileListView.getFullPath())) mFileListView.refreshPath(); else goTo(targetDir);
        });
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

    private void createFolder() {
        EditText editText = new EditText(requireContext());
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.folder_dialog_insert_name).setView(editText)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.folder_dialog_create, (dialog, which) -> {
                    String name = editText.getText().toString().trim().replaceAll("[\\\\/:*?\"<>|]", "_");
                    if (name.isEmpty()) return;
                    if (new File(mFileListView.getFullPath(), name).mkdirs()) mFileListView.refreshPath();
                    else Toast.makeText(requireContext(), R.string.gamefiles_error, Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void showFileActions(File file) {
        if (!isAdded() || file == null || !file.isFile()) return;
        String lower = file.getName().toLowerCase();
        ArrayList<String> list = new ArrayList<>();
        if (lower.endsWith(".zip")) list.add(getString(R.string.gamefiles_extract));
        if (lower.endsWith(".jar") || lower.endsWith(".jar.disabled")) list.add(getString(R.string.gamefiles_toggle_mod));
        list.add(getString(R.string.gamefiles_rename));
        list.add(getString(R.string.gamefiles_delete));
        String[] actions = list.toArray(new String[0]);
        new AlertDialog.Builder(requireContext()).setTitle(file.getName()).setItems(actions, (dialog, which) -> {
            String chosen = actions[which];
            if (chosen.equals(getString(R.string.gamefiles_extract))) extractZip(file);
            else if (chosen.equals(getString(R.string.gamefiles_toggle_mod))) toggleMod(file);
            else if (chosen.equals(getString(R.string.gamefiles_rename))) renameFile(file);
            else if (chosen.equals(getString(R.string.gamefiles_delete))) deleteFile(file);
        }).show();
    }

    private void toggleMod(File file) {
        String name = file.getName(); String newName;
        if (name.toLowerCase().endsWith(".jar.disabled")) newName = name.substring(0, name.length() - ".disabled".length());
        else if (name.toLowerCase().endsWith(".jar")) newName = name + ".disabled";
        else return;
        if (file.renameTo(uniqueFile(file.getParentFile(), newName))) mFileListView.refreshPath();
        else Toast.makeText(requireContext(), R.string.gamefiles_error, Toast.LENGTH_SHORT).show();
    }

    private void renameFile(File file) {
        EditText editText = new EditText(requireContext());
        editText.setText(file.getName());
        new AlertDialog.Builder(requireContext()).setTitle(R.string.gamefiles_rename).setView(editText)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String name = editText.getText().toString().trim().replaceAll("[\\\\/:*?\"<>|]", "_");
                    if (name.isEmpty()) return;
                    if (file.renameTo(uniqueFile(file.getParentFile(), name))) mFileListView.refreshPath();
                    else Toast.makeText(requireContext(), R.string.gamefiles_error, Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void deleteFile(File file) {
        new AlertDialog.Builder(requireContext()).setTitle(R.string.gamefiles_delete).setMessage(file.getName())
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.gamefiles_delete, (dialog, which) -> PojavApplication.sExecutorService.execute(() -> {
                    deleteRecursively(file);
                    if (isAdded()) requireActivity().runOnUiThread(() -> mFileListView.refreshPath());
                })).show();
    }

    private void extractZip(File zip) {
        String base = zip.getName();
        int dot = base.toLowerCase().lastIndexOf(".zip");
        if (dot > 0) base = base.substring(0, dot);
        File destDir = uniqueFile(zip.getParentFile(), base);
        Toast.makeText(requireContext(), R.string.gamefiles_extracting, Toast.LENGTH_SHORT).show();
        PojavApplication.sExecutorService.execute(() -> {
            boolean ok = unzip(zip, destDir);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), ok ? getString(R.string.gamefiles_extracted, destDir.getName()) : getString(R.string.gamefiles_error), Toast.LENGTH_LONG).show();
                mFileListView.refreshPath();
            });
        });
    }

    private boolean unzip(File zip, File destDir) {
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
        } catch (IOException e) { android.util.Log.e(TAG, "Unzip failed", e); return false; }
    }

    private void deleteRecursively(File file) {
        if (file.isDirectory()) { File[] c = file.listFiles(); if (c != null) for (File ch : c) deleteRecursively(ch); }
        file.delete();
    }
}
