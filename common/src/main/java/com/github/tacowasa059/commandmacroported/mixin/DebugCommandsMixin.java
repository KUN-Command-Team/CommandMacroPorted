package com.github.tacowasa059.commandmacroported.mixin;

import com.github.tacowasa059.commandmacroported.ported.ModSuggestionProviders;
import com.github.tacowasa059.commandmacroported.ported.returns.PortedDebugCommand;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.server.commands.DebugCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Registers {@code /debug function} with the engine {@link PortedDebugCommand.TraceCustomExecutor}
 * so the trace runs through the deferred engine with a tracer attached (faithful 1.20.3 behaviour,
 * incl. {@code return} tracing and the no-recursion / no-return-run guards).
 */
@Mixin(DebugCommand.class)
public class DebugCommandsMixin {
    @Shadow
    private static int start(CommandSourceStack p_136910_) {
        return 0;
    }

    @Shadow
    private static int stop(CommandSourceStack p_136910_) {
        return 0;
    }

    @Inject(method = "register", at = @At("HEAD"), cancellable = true)
    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, CallbackInfo ci) {
        dispatcher.register(Commands.literal("debug").requires((source) -> {
            return source.hasPermission(3);
        }).then(Commands.literal("start").executes((context) -> {
            return DebugCommandsMixin.start(context.getSource());
        })).then(Commands.literal("stop").executes((context) -> {
            return DebugCommandsMixin.stop(context.getSource());
        })).then(Commands.literal("function").requires((source) -> {
            return source.hasPermission(3);
        }).then(Commands.argument("name", FunctionArgument.functions()).suggests(ModSuggestionProviders.SUGGEST_FUNCTION)
                .executes(new PortedDebugCommand.TraceCustomExecutor()))));
        ci.cancel();
    }
}
