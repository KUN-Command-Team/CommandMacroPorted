package com.github.tacowasa059.commandmacroported.ported.returns;

import net.minecraft.server.ServerFunctionManager;

import javax.annotation.Nullable;

public interface ExecutionControl<T> {
    void queueNext(EntryAction<T> p_309475_);

    void tracer(@Nullable ServerFunctionManager.TraceCallbacks p_309557_);

    @Nullable
    ServerFunctionManager.TraceCallbacks tracer();

    Frame currentFrame();

    static <T extends ExecutionCommandSource<T>> ExecutionControl<T> create(final ExecutionContext<T> p_310088_, final Frame p_312154_) {
        return new ExecutionControl<T>() {
            public void queueNext(EntryAction<T> p_311389_) {
                p_310088_.queueNext(new CommandQueueEntry<>(p_312154_, p_311389_));
            }

            public void tracer(@Nullable ServerFunctionManager.TraceCallbacks p_313185_) {
                p_310088_.tracer(p_313185_);
            }

            @Nullable
            public ServerFunctionManager.TraceCallbacks tracer() {
                return p_310088_.tracer();
            }

            public Frame currentFrame() {
                return p_312154_;
            }
        };
    }
}
