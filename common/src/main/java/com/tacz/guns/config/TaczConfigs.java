package com.tacz.guns.config;

import com.tacz.guns.GunMod;
import com.tacz.guns.config.spec.TaczConfigSpec;
import com.tacz.guns.config.util.HeadShotAABBConfigRead;
import com.tacz.guns.config.util.InteractKeyConfigRead;
import com.tacz.guns.platform.Platform;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Owns TACZ's three config files and their lifecycle, mirroring Forge's config types:
 * <ul>
 *     <li>{@code config/tacz-common.toml} and {@code config/tacz-client.toml} load at startup;</li>
 *     <li>{@code <world>/serverconfig/tacz-server.toml} loads when a server starts (seeded from
 *     {@code defaultconfigs/tacz-server.toml} if present) and is sent to joining players, whose
 *     client then uses the server's values.</li>
 * </ul>
 */
public final class TaczConfigs {
    private static final String COMMON_FILE = "tacz-common.toml";
    private static final String CLIENT_FILE = "tacz-client.toml";
    private static final String SERVER_FILE = "tacz-server.toml";

    private static TaczConfigSpec common;
    private static TaczConfigSpec client;
    private static TaczConfigSpec server;

    private TaczConfigs() {
    }

    /**
     * Builds every spec and loads the common (and, on a physical client, the client) file.
     */
    public static synchronized void init() {
        if (common != null) {
            return;
        }
        common = CommonConfig.init();
        server = ServerConfig.init();
        client = ClientConfig.init();

        Path configDir = Platform.INSTANCE.getConfigDir();
        common.load(configDir.resolve(COMMON_FILE));
        if (Platform.INSTANCE.isPhysicalClient()) {
            client.load(configDir.resolve(CLIENT_FILE));
        }
        onServerConfigChanged();
    }

    public static TaczConfigSpec common() {
        return common;
    }

    public static TaczConfigSpec client() {
        return client;
    }

    public static TaczConfigSpec server() {
        return server;
    }

    public static void onServerStarting(MinecraftServer minecraftServer) {
        Path serverConfigDir = minecraftServer.getWorldPath(LevelResource.ROOT).resolve("serverconfig");
        Path file = serverConfigDir.resolve(SERVER_FILE);
        if (Files.notExists(file)) {
            Path defaults = Platform.INSTANCE.getGameDir().resolve("defaultconfigs").resolve(SERVER_FILE);
            if (Files.isRegularFile(defaults)) {
                try {
                    Files.createDirectories(serverConfigDir);
                    Files.copy(defaults, file);
                } catch (IOException e) {
                    GunMod.LOGGER.warn("Failed to copy default server config {}", defaults, e);
                }
            }
        }
        server.load(file);
        onServerConfigChanged();
    }

    public static void onServerStopped() {
        server.resetToDefaults();
        onServerConfigChanged();
    }

    /**
     * @return the server config as sent to joining clients
     */
    public static String serializeServerConfig() {
        return server.serialize();
    }

    /**
     * Applies server config values received from the server this client joined.
     */
    public static void applySyncedServerConfig(String toml) {
        try {
            server.deserialize(toml);
            onServerConfigChanged();
        } catch (IllegalArgumentException e) {
            GunMod.LOGGER.error("Received an unreadable server config from the server", e);
        }
    }

    /**
     * Persists values edited in a config screen.
     */
    public static void saveClientAndCommon() {
        client.save();
        common.save();
    }

    /**
     * Saves the server config after a command changed it.
     */
    public static void saveServer() {
        server.save();
    }

    private static void onServerConfigChanged() {
        HeadShotAABBConfigRead.init();
        InteractKeyConfigRead.init();
    }
}
