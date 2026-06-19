package com.github.tacowasa059.commandmacroported.ported.returns;

public class FallthroughTask<T extends ExecutionCommandSource<T>> implements EntryAction<T> {
    private static final FallthroughTask<? extends ExecutionCommandSource<?>> INSTANCE = new FallthroughTask<>();

    public static <T extends ExecutionCommandSource<T>> EntryAction<T> instance() {
        return (EntryAction<T>)INSTANCE;
    }

    public void execute(ExecutionContext<T> p_311441_, Frame p_309937_) {
        p_309937_.returnFailure();
        p_309937_.discard();
    }
}
