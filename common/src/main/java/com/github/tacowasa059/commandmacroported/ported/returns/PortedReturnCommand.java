package com.github.tacowasa059.commandmacroported.ported.returns;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import java.util.List;

public class PortedReturnCommand {

    public static class ReturnFailCustomExecutor<T extends ExecutionCommandSource<T>> implements CustomCommandExecutor.CommandAdapter<T> {
        public void run(T p_312804_, ContextChain<T> p_313125_, ChainModifiers p_309843_, ExecutionControl<T> p_311523_) {
            p_312804_.callback().onFailure();
            Frame frame = p_311523_.currentFrame();
            frame.returnFailure();
            frame.discard();
        }
    }

    public static class ReturnFromCommandCustomModifier<T extends ExecutionCommandSource<T>> implements CustomModifierExecutor.ModifierAdapter<T> {
        public void apply(T p_310700_, List<T> p_310930_, ContextChain<T> p_313059_, ChainModifiers p_313220_, ExecutionControl<T> p_311638_) {
            if (p_310930_.isEmpty()) {
                if (p_313220_.isReturn()) {
                    p_311638_.queueNext(FallthroughTask.instance());
                }

            } else {
                p_311638_.currentFrame().discard();
                ContextChain<T> contextchain = p_313059_.nextStage();
                String s = contextchain.getTopContext().getInput();
                p_311638_.queueNext(new BuildContexts.Continuation<>(s, contextchain, p_313220_.setReturn(), p_310700_, p_310930_));
            }
        }
    }

    public static class ReturnValueCustomExecutor<T extends ExecutionCommandSource<T>> implements CustomCommandExecutor.CommandAdapter<T> {
        public void run(T p_309785_, ContextChain<T> p_312976_, ChainModifiers p_309726_, ExecutionControl<T> p_310375_) {
            int i = IntegerArgumentType.getInteger(p_312976_.getTopContext(), "value");
            p_309785_.callback().onSuccess(i);
            Frame frame = p_310375_.currentFrame();
            frame.returnSuccess(i);
            frame.discard();
        }
    }
}
