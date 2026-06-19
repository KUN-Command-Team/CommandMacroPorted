package com.github.tacowasa059.commandmacroported.ported.returns;

import com.github.tacowasa059.commandmacroported.ported.FunctionInstantiationException;
import com.github.tacowasa059.commandmacroported.ported.PortedCommandFunction;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntPredicate;

/**
 * Faithful 1.20.3 {@code ExecuteCommand} port of {@code execute (if|unless) function}
 * (CommandSourceStack-specialised).
 *
 * <p>Runs the named function(s) through the engine (each in an {@link IsolatedCall} with
 * {@code returnParentFrame=true} so its {@code return} value is captured) and continues the
 * {@code execute} chain only for the sources whose result matches the if/unless predicate.
 */
public final class PortedExecuteCommand {
    private PortedExecuteCommand() {
    }

    @FunctionalInterface
    public interface CommandGetter {
        Collection<CommandFunction> get(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;
    }

    public static final class ExecuteIfFunctionCustomModifier implements CustomModifierExecutor.ModifierAdapter<CommandSourceStack> {
        private final IntPredicate resultPredicate;

        public ExecuteIfFunctionCustomModifier(boolean isIf) {
            this.resultPredicate = isIf ? (result) -> result != 0 : (result) -> result == 0;
        }

        @Override
        public void apply(CommandSourceStack source, List<CommandSourceStack> sources, ContextChain<CommandSourceStack> contextChain, ChainModifiers modifiers, ExecutionControl<CommandSourceStack> control) {
            runFunction(source, sources, ExecuteIfFunctionCustomModifier::modifySource, this.resultPredicate, contextChain,
                    null, control, (context) -> FunctionArgument.getFunctions(context, "name"), modifiers);
        }

        // == FunctionCommand.modifySource: withSuppressedOutput().withMaximumPermission(2)
        private static CommandSourceStack modifySource(CommandSourceStack source) {
            return source.withSuppressedOutput().withMaximumPermission(2);
        }
    }

    static void runFunction(CommandSourceStack source, List<CommandSourceStack> sources,
                            Function<CommandSourceStack, CommandSourceStack> sourceModifier, IntPredicate resultPredicate,
                            ContextChain<CommandSourceStack> contextChain, @Nullable CompoundTag args,
                            ExecutionControl<CommandSourceStack> control, CommandGetter functionGetter, ChainModifiers modifiers) {
        List<CommandSourceStack> passed = new ArrayList<>(sources.size());

        Collection<CommandFunction> functions;
        try {
            functions = functionGetter.get(contextChain.getTopContext().copyFor(source));
        } catch (CommandSyntaxException exception) {
            PortedFunctionCommand.srcHandleError(source, exception, modifiers.isForked(), control.tracer());
            return;
        }

        if (functions.isEmpty()) {
            return;
        }

        CommandDispatcher<CommandSourceStack> dispatcher = source.getServer().getFunctions().getDispatcher();
        List<InstantiatedFunction<CommandSourceStack>> instantiated = new ArrayList<>(functions.size());
        try {
            for (CommandFunction function : functions) {
                try {
                    instantiated.add(new PortedInstantiatedFunction(((PortedCommandFunction) function).instantiate(args, dispatcher, source)));
                } catch (FunctionInstantiationException exception) {
                    throw PortedFunctionCommand.ERROR_FAILED_TO_INSTANTIATE.create(function.getId(), exception.messageComponent());
                }
            }
        } catch (CommandSyntaxException exception) {
            PortedFunctionCommand.srcHandleError(source, exception, modifiers.isForked(), control.tracer());
        }

        for (CommandSourceStack original : sources) {
            CommandSourceStack target = sourceModifier.apply(clearCallbacks(original));
            CommandResultCallback callback = (success, result) -> {
                if (resultPredicate.test(result)) {
                    passed.add(original);
                }
            };
            queueIsolatedFunctionCall(control, instantiated, target, callback);
        }

        ContextChain<CommandSourceStack> next = contextChain.nextStage();
        String input = contextChain.getTopContext().getInput();
        queueContinuation(control, input, next, modifiers, source, passed);
    }

    // --- bridges / boundary helpers (F-bounded engine + raw ExecutionCommandSource) ---

    @SuppressWarnings("unchecked")
    private static CommandSourceStack clearCallbacks(CommandSourceStack source) {
        return (CommandSourceStack) ((ExecutionCommandSource) source).clearCallbacks();
    }

    @SuppressWarnings("unchecked")
    private static <T extends ExecutionCommandSource<T>> void queueIsolatedFunctionCall(Object control, List<?> instantiated, Object boundSource, CommandResultCallback callback) {
        ExecutionControl<T> typedControl = (ExecutionControl<T>) control;
        List<InstantiatedFunction<T>> functions = (List<InstantiatedFunction<T>>) (List<?>) instantiated;
        T target = (T) boundSource;
        typedControl.queueNext(new IsolatedCall<T>((childControl) -> {
            for (InstantiatedFunction<T> function : functions) {
                childControl.queueNext(new CallFunction<T>(function, childControl.currentFrame().returnValueConsumer(), true).bind(target));
            }
            childControl.queueNext(FallthroughTask.instance());
        }, callback));
    }

    @SuppressWarnings("unchecked")
    private static <T extends ExecutionCommandSource<T>> void queueContinuation(Object control, String input, Object nextStage, ChainModifiers modifiers, Object source, List<?> passedSources) {
        ((ExecutionControl<T>) control).queueNext(new BuildContexts.Continuation<T>(
                input, (ContextChain<T>) nextStage, modifiers, (T) source, (List<T>) passedSources));
    }
}
