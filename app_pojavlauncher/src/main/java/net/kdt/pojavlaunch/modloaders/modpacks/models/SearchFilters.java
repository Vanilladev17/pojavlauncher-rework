package net.kdt.pojavlaunch.modloaders.modpacks.models;

import org.jetbrains.annotations.Nullable;

/**
 * Search filters, passed to APIs
 */
public class SearchFilters {
    public boolean isModpack;
    public String name;
    @Nullable public String mcVersion;
    /** Загрузчик модов: fabric, forge, neoforge, quilt (для поиска модов) */
    @Nullable public String modLoader;

    public boolean hasModLoader() {
        return modLoader != null && !modLoader.isEmpty() && !modLoader.equalsIgnoreCase("any");
    }

    public boolean hasMcVersion() {
        return mcVersion != null && !mcVersion.isEmpty();
    }
}
