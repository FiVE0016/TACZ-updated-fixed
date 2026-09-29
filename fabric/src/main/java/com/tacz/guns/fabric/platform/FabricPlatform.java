package com.tacz.guns.fabric.platform;

import com.tacz.guns.platform.Platform;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public final class FabricPlatform implements Platform {
    @Override
    public String loaderName() {
        // Quilt Loader runs Fabric mods and answers to Fabric Loader's API.
        return FabricLoader.getInstance().isModLoaded("quilt_loader") ? "Quilt" : "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public Optional<String> getModVersion(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(container -> container.getMetadata().getVersion().getFriendlyString());
    }

    @Override
    public Optional<IoSupplier<InputStream>> findModResource(String modId, String path) {
        return FabricLoader.getInstance().getModContainer(modId)
                .flatMap(container -> container.findPath(path))
                .filter(Files::exists)
                .map(IoSupplier::create);
    }

    @Override
    public boolean visitModFiles(String modId, String directory, BiConsumer<String, IoSupplier<InputStream>> visitor) throws IOException {
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(modId);
        if (container.isEmpty()) {
            return false;
        }
        for (Path root : container.get().getRootPaths()) {
            Path start = root.resolve(directory);
            if (!Files.isDirectory(start)) {
                continue;
            }
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
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isPhysicalClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }
}
