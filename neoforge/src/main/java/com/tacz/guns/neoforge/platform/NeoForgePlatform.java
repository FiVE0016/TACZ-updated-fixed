package com.tacz.guns.neoforge.platform;

import com.tacz.guns.platform.Platform;
import net.minecraft.server.packs.resources.IoSupplier;
import net.neoforged.fml.ModList;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.fml.jarcontents.JarResource;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModFileInfo;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BiConsumer;

public final class NeoForgePlatform implements Platform {
    @Override
    public String loaderName() {
        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public Optional<String> getModVersion(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString());
    }

    @Override
    public Optional<IoSupplier<InputStream>> findModResource(String modId, String path) {
        IModFileInfo modFile = ModList.get().getModFileById(modId);
        if (modFile == null) {
            return Optional.empty();
        }
        JarContents contents = modFile.getFile().getContents();
        return contents.containsFile(path) ? Optional.of(() -> contents.openFile(path)) : Optional.empty();
    }

    @Override
    public boolean visitModFiles(String modId, String directory, BiConsumer<String, IoSupplier<InputStream>> visitor) {
        IModFileInfo modFile = ModList.get().getModFileById(modId);
        if (modFile == null) {
            return false;
        }
        // Visited paths are relative to the mod's root, and the resource object is reused between visits.
        String prefix = directory + "/";
        modFile.getFile().getContents().visitContent(directory, (path, resource) -> {
            if (path.startsWith(prefix)) {
                JarResource retained = resource.retain();
                visitor.accept(path.substring(prefix.length()), retained::open);
            }
        });
        return true;
    }

    @Override
    public Path getGameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isPhysicalClient() {
        return FMLEnvironment.getDist().isClient();
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLEnvironment.isProduction();
    }
}
