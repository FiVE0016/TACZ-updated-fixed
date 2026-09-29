package com.tacz.guns.platform;

import net.minecraft.server.packs.resources.IoSupplier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Loader facts that common code needs: which mods are present, where the game directories
 * are, and which physical side is running.
 */
public interface Platform {
    Platform INSTANCE = Services.load(Platform.class);

    /**
     * @return "Fabric", "Quilt", "NeoForge" or "Forge"
     */
    String loaderName();

    boolean isModLoaded(String modId);

    /**
     * @return the version string a loaded mod declares, if the mod is present
     */
    Optional<String> getModVersion(String modId);

    /**
     * Opens a file inside a loaded mod's jar (or its dev-time output folders). Not every loader exposes mod
     * files as {@link Path}s, so this hands back an opener instead.
     *
     * @return an opener for the file, or empty when the mod or the file does not exist
     */
    Optional<IoSupplier<InputStream>> findModResource(String modId, String path);

    /**
     * Visits every file below a directory inside a loaded mod's jar (or its dev-time output folders).
     *
     * @param directory the directory relative to the mod's root, without leading or trailing slash
     * @param visitor   receives each file's path relative to {@code directory} and an opener for it
     * @return false when the mod is not loaded
     */
    boolean visitModFiles(String modId, String directory, BiConsumer<String, IoSupplier<InputStream>> visitor) throws IOException;

    Path getGameDir();

    Path getConfigDir();

    boolean isPhysicalClient();

    boolean isDevelopmentEnvironment();
}
