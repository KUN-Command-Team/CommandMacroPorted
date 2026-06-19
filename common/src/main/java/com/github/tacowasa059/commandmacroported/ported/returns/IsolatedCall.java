package com.github.tacowasa059.commandmacroported.ported.returns;

import java.util.function.Consumer;

/**
 * Faithful port of 1.20.3 {@code net.minecraft.commands.execution.tasks.IsolatedCall}: runs a child
 * action in a fresh {@link Frame} (one level deeper) whose result is delivered to the given callback.
 * Used by {@code execute if|unless function} to capture each function's return value independently.
 */
public class IsolatedCall<T extends ExecutionCommandSource<T>> implements EntryAction<T> {
    private final Consumer<ExecutionControl<T>> childAction;
    private final CommandResultCallback callback;

    public IsolatedCall(Consumer<ExecutionControl<T>> childAction, CommandResultCallback callback) {
        this.childAction = childAction;
        this.callback = callback;
    }

    @Override
    public void execute(ExecutionContext<T> context, Frame frame) {
        int depth = frame.depth() + 1;
        Frame newFrame = new Frame(depth, this.callback, context.frameControlForDepth(depth));
        this.childAction.accept(ExecutionControl.create(context, newFrame));
    }
}
