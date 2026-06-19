package com.github.tacowasa059.commandmacroported.mixin;

import com.github.tacowasa059.commandmacroported.ported.returns.CommandExecution;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Routes top-level command execution through the ported 1.20.3 deferred engine.
 *
 * <p>1.20.1's {@code performCommand} calls {@code dispatcher.execute(parse)} directly,
 * which never invokes the {@code CustomCommandExecutor}/{@code CustomModifierExecutor}
 * adapters that {@code /return} (and {@code return run}/{@code return fail}) rely on.
 * We replace it with {@link CommandExecution#performCommand}, which builds a
 * {@code ContextChain} and drives an {@code ExecutionContext} queue, mirroring 1.20.3.
 */
@Mixin(Commands.class)
public abstract class CommandsMixin {

    @Inject(
            method = "performCommand(Lcom/mojang/brigadier/ParseResults;Ljava/lang/String;)I",
            at = @At("HEAD"),
            cancellable = true
    )
    private void commandmacroported$performCommand(ParseResults<CommandSourceStack> parse, String command, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(CommandExecution.performCommand(parse, command));
    }
}
