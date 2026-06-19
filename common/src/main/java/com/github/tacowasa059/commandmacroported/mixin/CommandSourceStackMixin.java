package com.github.tacowasa059.commandmacroported.mixin;

import com.github.tacowasa059.commandmacroported.ported.returns.CallbackResultConsumer;
import com.github.tacowasa059.commandmacroported.ported.returns.CommandResultCallback;
import com.github.tacowasa059.commandmacroported.ported.returns.ExecutionCommandSource;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.ResultConsumer;
import com.mojang.brigadier.exceptions.CommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import javax.annotation.Nullable;

/**
 * Makes 1.20.1's {@link CommandSourceStack} implement the ported (1.20.3)
 * {@link ExecutionCommandSource}, so the deferred command-execution engine can
 * drive it with {@code T = CommandSourceStack}.
 *
 * <p>The ported interface is F-bounded ({@code T extends ExecutionCommandSource<T>}),
 * which javac cannot accept here because the mixin is applied at runtime — at
 * compile time it does not yet see {@code CommandSourceStack} as an
 * {@code ExecutionCommandSource}. We therefore implement the <b>raw</b> interface
 * in this thin mixin (generics are erased at runtime anyway); all engine and glue
 * code stays cleanly generic and reaches this through a single boundary cast.
 *
 * <p>In 1.20.3 the source carries one {@code CommandResultCallback}. 1.20.1 still
 * carries a brigadier {@code ResultConsumer<CommandSourceStack>} ({@code resultConsumer}
 * / {@code f_81297_}) which is already copied by every wither. We piggyback the
 * callback onto that field via {@link CallbackResultConsumer}, giving immutable
 * propagation for free and keeping vanilla {@code execute store} interoperable.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
@Mixin(CommandSourceStack.class)
public abstract class CommandSourceStackMixin implements ExecutionCommandSource {

    @Shadow @Final @Nullable private ResultConsumer<CommandSourceStack> consumer;

    @Shadow @Final private boolean silent;

    @Shadow public abstract CommandSourceStack withCallback(ResultConsumer<CommandSourceStack> p_81335_);

    @Shadow public abstract MinecraftServer getServer();

    @Shadow public abstract void sendFailure(Component p_81353_);

    // Return type is the (erased) interface type: at compile time javac does not yet
    // see CommandSourceStack as an ExecutionCommandSource, so it cannot be the declared
    // return. At runtime the returned object is a CommandSourceStack.
    public ExecutionCommandSource withCallback(CommandResultCallback callback) {
        return (ExecutionCommandSource) (Object) this.withCallback(new CallbackResultConsumer(callback));
    }

    @Override
    public CommandResultCallback callback() {
        ResultConsumer<CommandSourceStack> rc = this.consumer;
        if (rc == null) {
            return CommandResultCallback.EMPTY;
        } else if (rc instanceof CallbackResultConsumer wrapper) {
            return wrapper.callback();
        } else {
            // A raw vanilla consumer (e.g. set by execute store). Its onCommandComplete
            // does not use the CommandContext, so a null context is safe here.
            return (success, value) -> rc.onCommandComplete(null, success, value);
        }
    }

    @Override
    public CommandDispatcher<CommandSourceStack> dispatcher() {
        return this.getServer().getCommands().getDispatcher();
    }

    @Override
    public void handleError(CommandExceptionType type, Message message, boolean silentError, @Nullable ServerFunctionManager.TraceCallbacks tracer) {
        if (!silentError) {
            this.sendFailure(ComponentUtils.fromMessage(message));
        }
    }

    @Override
    public boolean isSilent() {
        return this.silent;
    }
}
