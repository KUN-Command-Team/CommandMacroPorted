package com.github.tacowasa059.commandmacroported.ported.returns;

import com.mojang.brigadier.ResultConsumer;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;

/**
 * Bridges the 1.20.3 {@link CommandResultCallback} onto the 1.20.1
 * {@link ResultConsumer} field that {@code CommandSourceStack} already carries
 * and propagates through every wither.
 *
 * <p>In 1.20.3 vanilla, {@code CommandSourceStack} stores a single
 * {@code CommandResultCallback}. In 1.20.1 it stores a brigadier
 * {@code ResultConsumer<CommandSourceStack>} ({@code f_81297_}). Rather than add a
 * new field (which the existing vanilla copy-constructors would not propagate),
 * we wrap the callback in this consumer and store it in the existing field, so the
 * engine and vanilla {@code execute store} interoperate on one carrier.
 */
public record CallbackResultConsumer(CommandResultCallback callback) implements ResultConsumer<CommandSourceStack> {
    @Override
    public void onCommandComplete(CommandContext<CommandSourceStack> context, boolean success, int result) {
        this.callback.onResult(success, result);
    }
}
