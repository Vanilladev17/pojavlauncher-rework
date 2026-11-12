package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.ToolsLauncherShortcuts;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;

import java.util.Collections;
import java.util.List;

/**
 * Экран «Ярлыки»: одна кнопка на каждый профиль — создать ярлык запуска игры на рабочем столе.
 */
public class ShortcutsFragment extends Fragment {
    public static final String TAG = "SHORTCUTS_FRAGMENT";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_shortcuts, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        LinearLayout list = view.findViewById(R.id.shortcut_list);
        TextView empty = view.findViewById(R.id.shortcut_empty);

        view.findViewById(R.id.shortcut_add_all).setOnClickListener(v -> {
            List<Instance> instances = loadInstances();
            if (instances.isEmpty()) {
                Toast.makeText(requireContext(), R.string.shortcut_no_instance, Toast.LENGTH_LONG).show();
                return;
            }
            int created = 0;
            for (Instance instance : instances) {
                if (instance == null) continue;
                ToolsLauncherShortcuts.createInstanceShortcut(requireContext(), instance);
                created++;
            }
            Toast.makeText(requireContext(),
                    getString(R.string.shortcut_created_count, created), Toast.LENGTH_SHORT).show();
        });

        List<Instance> instances = loadInstances();
        if (instances.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (Instance instance : instances) {
            if (instance == null) continue;
            View row = inflater.inflate(R.layout.item_shortcut, list, false);
            TextView title = row.findViewById(R.id.shortcut_item_title);
            TextView subtitle = row.findViewById(R.id.shortcut_item_subtitle);

            String label = instance.name != null && !instance.name.trim().isEmpty()
                    ? instance.name.trim() : instance.versionId;
            title.setText(label);
            subtitle.setText(instance.versionId != null ? instance.versionId : "—");

            row.findViewById(R.id.shortcut_item_button).setOnClickListener(v -> {
                String result = ToolsLauncherShortcuts.createInstanceShortcut(requireContext(), instance);
                if (result != null) {
                    Toast.makeText(requireContext(), result, Toast.LENGTH_LONG).show();
                }
            });
            list.addView(row);
        }
    }

    private List<Instance> loadInstances() {
        try {
            return Instances.loadAllInstances();
        } catch (Throwable throwable) {
            return Collections.emptyList();
        }
    }
}
