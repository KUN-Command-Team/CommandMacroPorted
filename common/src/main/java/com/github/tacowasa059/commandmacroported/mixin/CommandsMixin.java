package com.github.tacowasa059.commandmacroported.mixin;

import com.github.tacowasa059.commandmacroported.ported.returns.CommandExecution;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Routes top-level command dispatch through the ported 1.20.3 deferred engine, while preserving
 * everything else {@code performCommand} does around the dispatch call.
 *
 * <p>1.20.1's {@code performCommand} calls {@code dispatcher.execute(parse)} directly, which never
 * invokes the {@code CustomCommandExecutor}/{@code CustomModifierExecutor} adapters that
 * {@code /return} (and {@code return run}/{@code return fail}) rely on. We replace only that
 * {@code dispatcher.execute(parse)} call with {@link CommandExecution#performCommand}, which builds a
 * {@code ContextChain} and drives an {@code ExecutionContext} queue, mirroring 1.20.3.
 *
 * <p><b>Why a {@code @Redirect} and not an {@code @Inject}(HEAD, cancellable):</b> on Forge,
 * {@code Commands#performCommand} is patched to fire {@code net.minecraftforge.event.CommandEvent}
 * <em>before</em> the {@code dispatcher.execute} call, and mods hook command handling onto that
 * event. WorldEdit is the clearest case: its brigadier nodes only carry a placeholder
 * {@code CommandWrapper.FAKE_COMMAND} (which just returns {@code 1}), and the real work happens in a
 * {@code CommandEvent} handler that recognises {@code FAKE_COMMAND}, cancels the event, and dispatches
 * the raw command string to WorldEdit's own command manager. Cancelling the whole
 * {@code performCommand} at HEAD skips the Forge event entirely, so every WorldEdit command silently
 * does nothing (no error, because the placeholder "succeeds"). Redirecting only the dispatch keeps the
 * Forge event - along with any handler cancellation or {@code parse}/{@code command} modification -
 * intact, and still swaps in the ported engine for commands that actually reach the dispatcher.
 */
@Mixin(Commands.class)
public abstract class CommandsMixin {

    @Redirect(
            method = "performCommand(Lcom/mojang/brigadier/ParseResults;Ljava/lang/String;)I",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/CommandDispatcher;execute(Lcom/mojang/brigadier/ParseResults;)I"
            )
    )
    private int commandmacroported$routeThroughEngine(CommandDispatcher<CommandSourceStack> dispatcher, ParseResults<CommandSourceStack> parse) {
        // `parse` is whatever reaches the dispatch call (on Forge, possibly the CommandEvent-modified
        // parse); its reader string is the command line we're executing, matching the old `command` arg.
        return CommandExecution.performCommand(parse, parse.getReader().getString());
    }
}
