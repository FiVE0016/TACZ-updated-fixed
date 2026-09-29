package com.tacz.guns.forge.platform;

import com.tacz.guns.platform.Platform;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModFileInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public final class ForgePlatform implements Platform {
    @Override
    public String loaderName() {
        return "Forge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.isLoaded(modId);
    }

    @Override
    public Optional<String> getModVersion(String modId) {
        return ModList.getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString());
    }

    @Override
    public Optional<IoSupplier<InputStream>> findModResource(String modId, String path) {
        IModFileInfo modFile = ModList.getModFileById(modId);
        if (modFile == null) {
            return Optional.empty();
        }
        Path resource = modFile.getFile().findResource(path);
        return Files.exists(resource) ? Optional.of(IoSupplier.create(resource)) : Optional.empty();
    }

    @Override
    public boolean visitModFiles(String modId, String directory, BiConsumer<String, IoSupplier<InputStream>> visitor) throws IOException {
        IModFileInfo modFile = ModList.getModFileById(modId);
        if (modFile == null) {
            return false;
        }
        Path start = modFile.getFile().findResource(directory);
        if (Files.isDirectory(start)) {
            List<Path> files;
            try (Stream<Path> walk = Files.walk(start)) {
                files = walk.filter(Files::isRegularFile).toList();
            }
            for (Path file : files) {
                visitor.accept(start.relativize(file).toString().replace('\\', '/'), IoSupplier.create(file));
            }
        }
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
        return FMLEnvironment.dist.isClient();
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLEnvironment.production;
    }
}
