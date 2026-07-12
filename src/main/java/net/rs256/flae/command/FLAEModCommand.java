package net.rs256.flae.command;

import net.rs256.flae.FLAEMod;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.*;
import net.minecraft.network.chat.*;

public class FLAEModCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext commandBuildContext) {
        dispatcher.register(
                Commands.literal(FLAEMod.MOD_ID)
                        .then(Commands.literal("reload")
                                .executes(commandContext -> executeReload())
                        )
        );
    }

    private static int executeReload() {
        return 1;
    }
}
