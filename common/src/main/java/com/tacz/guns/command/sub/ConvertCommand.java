package com.tacz.guns.command.sub;
import com.tacz.guns.platform.Platform;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.tacz.guns.resource.PackConvertor;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class ConvertCommand {
    private static final String CONVERT_NAME = "convert";

    public static LiteralArgumentBuilder<CommandSourceStack> get() {
        LiteralArgumentBuilder<CommandSourceStack> reload = Commands.literal(CONVERT_NAME);
        reload.executes(ConvertCommand::convert);
        return reload;
    }

    private static int convert(CommandContext<CommandSourceStack> context) {
        if (Platform.INSTANCE.isPhysicalClient())
            PackConvertor.convert(context.getSource());
        return Command.SINGLE_SUCCESS;
    }
}
