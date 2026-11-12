package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.runOnUiThread;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.math.MathUtils;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.ModItemAdapter;
import net.kdt.pojavlaunch.modloaders.modpacks.api.CommonApi;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModpackApi;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModrinthModApi;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.profiles.VersionSelectorDialog;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class SearchModFragment extends Fragment implements ModItemAdapter.SearchResultCallback {

    public static final String TAG = "SearchModFragment";
    public static final String TAG_MODS = "SearchModFragmentMods";
    /** Режим каталога модов (true) или модпаков (false) */
    public static final String ARG_SEARCH_MODS = "search_mods";
    private View mOverlay;
    private float mOverlayTopCache; // Padding cache reduce resource lookup

    private final RecyclerView.OnScrollListener mOverlayPositionListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            mOverlay.setY(MathUtils.clamp(mOverlay.getY() - dy, -mOverlay.getHeight(), mOverlayTopCache));
        }
    };

    private EditText mSearchEditText;
    private ImageButton mFilterButton;
    private RecyclerView mRecyclerview;
    private ModItemAdapter mModItemAdapter;
    private ProgressBar mSearchProgressBar;
    private TextView mStatusTextView;
    private ColorStateList mDefaultTextColor;
    private ModpackApi modpackApi;

    private final SearchFilters mSearchFilters;

    private Button mImportButton;
    private TaskCountListener mTaskCountListener;

    ActivityResultLauncher<String> mImportLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri == null) return;
                Context context = getContext();
                ContentResolver contentResolver = getContext().getContentResolver();
                PojavApplication.sExecutorService.execute(() -> {
                    performLocalInstall(uri, context, contentResolver);
                });
            });

    public void performLocalInstall(Uri uri, Context context, ContentResolver contentResolver) {
            String fileName = Tools.getFileName(context, uri);
            if (fileName == null) return;
            File outFile = new File(Tools.DIR_CACHE, fileName + ".cf");
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, R.string.multirt_progress_caching);
            try (InputStream inputStream = contentResolver.openInputStream(uri);
                 OutputStream outputStream = new FileOutputStream(outFile)) {
                if (inputStream == null) return;
                IOUtils.copy(inputStream, outputStream);
                outputStream.flush();
            } catch (IOException e) {
                Tools.showErrorRemote("Error", e);
                ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
                return;
            }
            try {
                modpackApi.installLocalModpack(fileName, outFile, null);
            } catch (IOException e) {
                Tools.showErrorRemote("Error", e);
            } finally {
                outFile.delete();
                ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
            }
    }

    public SearchModFragment(){
        super(R.layout.fragment_mod_search);
        mSearchFilters = new SearchFilters();
        mSearchFilters.isModpack = true;
    }

    /** Режим каталога модов: ищем моды и ставим их прямо в mods выбранного профиля */
    private boolean isModSearchMode() {
        Bundle arguments = getArguments();
        return arguments != null && arguments.getBoolean(ARG_SEARCH_MODS, false);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (isModSearchMode()) {
            mSearchFilters.isModpack = false;
            modpackApi = new ModrinthModApi();
        } else {
            mSearchFilters.isModpack = true;
            modpackApi = new CommonApi(context.getString(R.string.curseforge_api_key));
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // You can only access resources after attaching to current context
        mModItemAdapter = new ModItemAdapter(getResources(), modpackApi, this);
        ProgressKeeper.addTaskCountListener(mModItemAdapter);
        mOverlayTopCache = getResources().getDimension(R.dimen.fragment_padding_medium);

        mOverlay = view.findViewById(R.id.search_mod_overlay);
        mSearchEditText = view.findViewById(R.id.search_mod_edittext);
        mSearchProgressBar = view.findViewById(R.id.search_mod_progressbar);
        mRecyclerview = view.findViewById(R.id.search_mod_list);
        mStatusTextView = view.findViewById(R.id.search_mod_status_text);
        mFilterButton = view.findViewById(R.id.search_mod_filter);

        mDefaultTextColor = mStatusTextView.getTextColors();

        mRecyclerview.setLayoutManager(new LinearLayoutManager(getContext()));
        mRecyclerview.setAdapter(mModItemAdapter);

        mRecyclerview.addOnScrollListener(mOverlayPositionListener);

        mSearchEditText.setOnEditorActionListener((v, actionId, event) -> {
            searchMods(mSearchEditText.getText().toString());
            mSearchEditText.clearFocus();
            return false;
        });

        mOverlay.post(()->{
           int overlayHeight = mOverlay.getHeight();
           mRecyclerview.setPadding(mRecyclerview.getPaddingLeft(),
                   mRecyclerview.getPaddingTop() + overlayHeight,
                   mRecyclerview.getPaddingRight(),
                   mRecyclerview.getPaddingBottom());
        });
        if (isModSearchMode()) {
            mImportButton = view.findViewById(R.id.mineButton_import_local_modpack);
            mImportButton.setVisibility(View.GONE);
        }
        updateFilterHint();
        mFilterButton.setOnClickListener(v -> displayFilterDialog());
        mImportButton = view.findViewById(R.id.mineButton_import_local_modpack);
        mImportButton.setOnClickListener(v -> {
            mImportLauncher.launch("*/*");
        });
        mTaskCountListener = taskCount -> {
            runOnUiThread(() -> mImportButton.setEnabled(taskCount == 0));
            return false;
        };
        ProgressKeeper.addTaskCountListener(mTaskCountListener);

        searchMods(null);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ProgressKeeper.removeTaskCountListener(mModItemAdapter);
        mRecyclerview.removeOnScrollListener(mOverlayPositionListener);
        if (mTaskCountListener != null) { ProgressKeeper.removeTaskCountListener(mTaskCountListener); }
    }

    @Override
    public void onSearchFinished() {
        mSearchProgressBar.setVisibility(View.GONE);
        mStatusTextView.setVisibility(View.GONE);
    }

    @Override
    public void onSearchError(int error) {
        mSearchProgressBar.setVisibility(View.GONE);
        mStatusTextView.setVisibility(View.VISIBLE);
        switch (error) {
            case ERROR_INTERNAL:
                mStatusTextView.setTextColor(requireContext().getColor(R.color.error_text));
                mStatusTextView.setText(isModSearchMode() ? R.string.search_mod_error : R.string.search_modpack_error);
                break;
            case ERROR_NO_RESULTS:
                mStatusTextView.setTextColor(mDefaultTextColor);
                mStatusTextView.setText(isModSearchMode() ? R.string.search_mod_no_result : R.string.search_modpack_no_result);
                break;
        }
    }

    private void searchMods(String name) {
        mSearchProgressBar.setVisibility(View.VISIBLE);
        mSearchFilters.name = name == null ? "" : name;
        mModItemAdapter.performSearchQuery(mSearchFilters);
        updateFilterHint();
    }

    /** Показываем под поиском, какие фильтры сейчас включены */
    private void updateFilterHint() {
        if (mSearchEditText == null) return;
        StringBuilder hint = new StringBuilder(getString(
                isModSearchMode() ? R.string.hint_search_mod : R.string.hint_search_modpack));
        if (mSearchFilters.hasMcVersion()) hint.append(" · ").append(mSearchFilters.mcVersion);
        if (mSearchFilters.hasModLoader()) hint.append(" · ").append(mSearchFilters.modLoader);
        mSearchEditText.setHint(hint.toString());
    }

    /** Загрузчик, который уже стоит у выбранного профиля (чтобы не искать «не то») */
    @Nullable
    private String detectInstanceModLoader() {
        try {
            net.kdt.pojavlaunch.instances.Instance instance = net.kdt.pojavlaunch.instances.Instances.loadSelectedInstance();
            if (instance == null || instance.versionId == null) return null;
            String id = instance.versionId.toLowerCase();
            if (id.contains("neoforge")) return "neoforge";
            if (id.contains("fabric") || id.contains("quilt")) return "fabric";
            if (id.contains("forge")) return "forge";
        } catch (Exception ignored) {}
        return null;
    }

    private void displayFilterDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_mod_filters, null);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();

        final TextView selectedVersion = dialogView.findViewById(R.id.search_mod_selected_mc_version_textview);
        final Button selectVersionButton = dialogView.findViewById(R.id.search_mod_mc_version_button);
        final Button applyButton = dialogView.findViewById(R.id.search_mod_apply_filters);
        final Button resetButton = dialogView.findViewById(R.id.search_mod_reset_filters);
        final RadioGroup loaderGroup = dialogView.findViewById(R.id.search_mod_loader_group);

        // В режиме модпаков фильтр по загрузчику не нужен — прячем
        boolean modMode = isModSearchMode();
        dialogView.findViewById(R.id.search_mod_loader_textview).setVisibility(modMode ? View.VISIBLE : View.GONE);
        loaderGroup.setVisibility(modMode ? View.VISIBLE : View.GONE);

        selectVersionButton.setOnClickListener(v ->
                VersionSelectorDialog.open(v.getContext(), true, (id, snapshot) -> selectedVersion.setText(id)));

        if (modMode) {
            if (!mSearchFilters.hasModLoader()) {
                // первый запуск: подставляем загрузчик профиля, чтобы список был релевантным
                String detected = detectInstanceModLoader();
                if (detected != null) mSearchFilters.modLoader = detected;
            }
            int checkedId = R.id.search_mod_loader_any;
            String loader = mSearchFilters.modLoader == null ? "" : mSearchFilters.modLoader.toLowerCase();
            switch (loader) {
                case "fabric": checkedId = R.id.search_mod_loader_fabric; break;
                case "forge": checkedId = R.id.search_mod_loader_forge; break;
                case "neoforge": checkedId = R.id.search_mod_loader_neoforge; break;
            }
            ((RadioButton) dialogView.findViewById(checkedId)).setChecked(true);
        }

        selectedVersion.setText(mSearchFilters.hasMcVersion() ? mSearchFilters.mcVersion : "");

        applyButton.setOnClickListener(v -> {
            mSearchFilters.mcVersion = selectedVersion.getText().toString().trim();
            if (modMode) {
                int id = loaderGroup.getCheckedRadioButtonId();
                if (id == R.id.search_mod_loader_fabric) mSearchFilters.modLoader = "fabric";
                else if (id == R.id.search_mod_loader_forge) mSearchFilters.modLoader = "forge";
                else if (id == R.id.search_mod_loader_neoforge) mSearchFilters.modLoader = "neoforge";
                else mSearchFilters.modLoader = null;
            }
            searchMods(mSearchEditText.getText().toString());
            dialog.dismiss();
        });

        resetButton.setOnClickListener(v -> {
            selectedVersion.setText("");
            mSearchFilters.mcVersion = null;
            mSearchFilters.modLoader = null;
            if (modMode) ((RadioButton) dialogView.findViewById(R.id.search_mod_loader_any)).setChecked(true);
        });

        dialog.show();
    }
}
