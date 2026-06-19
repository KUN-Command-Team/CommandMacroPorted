package com.github.tacowasa059.commandmacroported.ported.returns;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.ServerFunctionManager;

public class ExecuteCommand<T extends ExecutionCommandSource<T>> implements UnboundEntryAction<T> {
    private final String commandInput;
    private final ChainModifiers modifiers;
    private final CommandContext<T> executionContext;

    public ExecuteCommand(String p_310766_, ChainModifiers p_309629_, CommandContext<T> p_310460_) {
        this.commandInput = p_310766_;
        this.modifiers = p_309629_;
        this.executionContext = p_310460_;
    }

    public void execute(T p_310632_, ExecutionContext<T> p_310757_, Frame p_311301_) {
        p_310757_.profiler().push(() -> {
            return "execute " + this.commandInput;
        });

        try {
            p_310757_.incrementCost();
            int i = ContextChain.runExecutable(this.executionContext, p_310632_, ExecutionCommandSource.resultConsumer(), this.modifiers.isForked());
            ServerFunctionManager.TraceCallbacks tracecallbacks = p_310757_.tracer();
            if (tracecallbacks != null) {
                tracecallbacks.onReturn(p_311301_.depth(), this.commandInput, i);
            }
        } catch (CommandSyntaxException commandsyntaxexception) {
            p_310632_.handleError(commandsyntaxexception, this.modifiers.isForked(), p_310757_.tracer());
        } finally {
            p_310757_.profiler().pop();
        }

    }
}