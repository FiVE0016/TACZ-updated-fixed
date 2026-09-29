package com.tacz.guns.command.sub;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.tacz.guns.config.TaczConfigs;
import com.tacz.guns.config.sync.SyncConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Arrays;

public class ConfigCommand {
    private static final String CONFIG_NAME = "config";
    private static final String KEY = "key";
    private static final String ENABLE = "state";

    public static LiteralArgumentBuilder<CommandSourceStack> get() {
        var config = Commands.literal(CONFIG_NAME);
        // A plain word argument with suggestions keeps the command tree readable by every client;
        // a custom enum argument type would need its own registration on each loader.
        var configKey = Commands.argument(KEY, StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        Arrays.stream(ConfigKey.values()).map(Enum::name), builder));
        var state = Commands.argument(ENABLE, BoolArgumentType.bool());
        return config.then(configKey.then(state.executes(ConfigCommand::setConfig)));
    }

    private static int setConfig(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, KEY);
        ConfigKey key = Arrays.stream(ConfigKey.values())
                .filter(candidate -> candidate.name().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
        boolean state = BoolArgumentType.getBool(context, ENABLE);

        if (key == null) {
            context.getSource().sendFailure(Component.literal("Unknown config key: " + name));
            return 0;
        }
        switch (key) {
            case defaultTableLimit -> SyncConfig.ENABLE_TABLE_FILTER.set(state);
            case serverShootNetworkCheck -> SyncConfig.SERVER_SHOOT_NETWORK_V.set(state);
            case serverShootCooldownCheck -> SyncConfig.SERVER_SHOOT_COOLDOWN_V.set(state);
        }
        TaczConfigs.saveServer();
        context.getSource().sendSystemMessage(Component.translatable(key.lang + "." + (state ? "enabled" : "disabled")));

        return Command.SINGLE_SUCCESS;
    }

    public enum ConfigKey {
        defaultTableLimit("commands.tacz.config.default_table_limit"),
        serverShootNetworkCheck("commands.tacz.config.server_shoot_network_check"),
        serverShootCooldownCheck("commands.tacz.config.server_shoot_cooldown_check"),
        ;

        public final String lang;

        ConfigKey(String lang) {
            this.lang = lang;
        }
    }
}
