package com.github.tacowasa059.commandmacroported.ported.returns;

import com.github.tacowasa059.commandmacroported.ported.FunctionInstantiationException;
import com.github.tacowasa059.commandmacroported.ported.PortedCommandFunction;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.datafixers.util.Pair;
import net.minecraft.commands.CommandFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerFunctionManager;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Faithful 1.20.3 {@code FunctionCommand} port (specialised to {@code CommandSourceStack}).
 *
 * <p>The {@code /function} command becomes a {@link CustomCommandExecutor} that queues each
 * function as a {@link CallFunction} onto the engine, so {@code return}/{@code return run}/
 * {@code return fail} propagate through the call {@link Frame} and the function's returned value
 * is reported via {@code commands.function.success.*.result} (those keys exist on 1.20.1 clients).
 *
 * <p>The 1.20.3 {@code commands.function.scheduled.*} / {@code instantiationFailure} keys are new;
 * they ship in the mod's {@code en_us.json} (modded clients render them; vanilla 1.20.1 clients
 * show the raw key, which is inherent to backporting a new-feature message).
 *
 * <p>F-bounded engine calls ({@link CallFunction}, {@link FallthroughTask}, {@link ExecutionControl})
 * are reached through generic {@code Object}-param helpers; the {@link ExecutionCommandSource}
 * methods on {@code CommandSourceStack} (which only implements the interface at runtime via mixin)
 * are reached through raw-interface bridge helpers.
 */
public final class PortedFunctionCommand {
    // 1.20.3 wording sent as literals (these keys do not exist on 1.20.1 clients).
    static final DynamicCommandExceptionType ERROR_NO_FUNCTIONS = new DynamicCommandExceptionType(
            (name) -> Component.literal("Can't find any functions for name " + name));
    public static final Dynamic2CommandExceptionType ERROR_FAILED_TO_INSTANTIATE = new Dynamic2CommandExceptionType(
            (name, reason) -> Component.literal("Failed to instantiate function " + name + ": ")
                    .append(reason instanceof Component component ? component : Component.literal(String.valueOf(reason))));

    /** Per-function result message; uses a 1.20.1 client key so it renders ("Function '%2$s' returned %1$s"). */
    public static final Callbacks SOURCE_CALLBACKS = (source, id, result) ->
            source.sendSuccess(() -> Component.translatable("commands.function.success.single.result", result, id), true);

    private PortedFunctionCommand() {
    }

    public interface Callbacks {
        void signalResult(CommandSourceStack source, ResourceLocation id, int result);
    }

    static final class Accumulator {
        boolean any;
        int sum;

        void add(int value) {
            this.any = true;
            this.sum += value;
        }
    }

    public abstract static class FunctionCustomExecutor implements CustomCommandExecutor.CommandAdapter<CommandSourceStack> {
        @Nullable
        protected abstract CompoundTag arguments(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;

        @Override
        public void run(CommandSourceStack source, ContextChain<CommandSourceStack> contextChain, ChainModifiers modifiers, ExecutionControl<CommandSourceStack> control) {
            try {
                this.runGuarded(source, contextChain, modifiers, control);
            } catch (CommandSyntaxException exception) {
                srcHandleError(source, exception, modifiers.isForked(), control.tracer());
                srcCallback(source).onFailure();
            }
        }

        private void runGuarded(CommandSourceStack source, ContextChain<CommandSourceStack> contextChain, ChainModifiers modifiers, ExecutionControl<CommandSourceStack> control) throws CommandSyntaxException {
            CommandContext<CommandSourceStack> context = contextChain.getTopContext().copyFor(source);
            Pair<ResourceLocation, Collection<CommandFunction>> nameAndFunctions = resolveFunctions(context);
            Collection<CommandFunction> functions = nameAndFunctions.getSecond();
            if (functions.isEmpty()) {
                throw ERROR_NO_FUNCTIONS.create(nameAndFunctions.getFirst());
            }

            CompoundTag args = this.arguments(context);
            CommandSourceStack functionSource = source.withSuppressedOutput().withMaximumPermission(2);
            // 1.20.3 "scheduled" wording sent as a literal: the commands.function.scheduled.* keys do
            // not exist on 1.20.1 clients and a server-side lang file would shadow the whole vanilla
            // table. The returned-value message below uses the vanilla commands.function.success.*.result
            // key, so it still localizes.
            if (functions.size() == 1) {
                ResourceLocation id = functions.iterator().next().getId();
                source.sendSuccess(() -> Component.literal("Running function " + id), true);
            } else {
                String names = functions.stream().map(f -> f.getId().toString()).collect(Collectors.joining(", "));
                source.sendSuccess(() -> Component.literal("Running functions " + names), true);
            }

            runFunctions(functions, args, source, functionSource, control, SOURCE_CALLBACKS, modifiers);
        }
    }

    @SuppressWarnings("unchecked")
    private static Pair<ResourceLocation, Collection<CommandFunction>> resolveFunctions(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Pair<ResourceLocation, ?> pair = FunctionArgument.getFunctionOrTag(context, "name");
        return Pair.of(pair.getFirst(), FunctionArgument.getFunctions(context, "name"));
    }

    static void runFunctions(Collection<CommandFunction> functions, @Nullable CompoundTag args,
                             CommandSourceStack source, CommandSourceStack functionSource, ExecutionControl<CommandSourceStack> control,
                             Callbacks callbacks, ChainModifiers modifiers) throws CommandSyntaxException {
        if (modifiers.isReturn()) {
            runFunctionsAsReturn(functions, args, source, functionSource, control, callbacks);
        } else {
            runFunctionsNormal(functions, args, source, functionSource, control, callbacks);
        }
    }

    private static void runFunctionsAsReturn(Collection<CommandFunction> functions, @Nullable CompoundTag args,
                                             CommandSourceStack source, CommandSourceStack functionSource,
                                             ExecutionControl<CommandSourceStack> control, Callbacks callbacks) throws CommandSyntaxException {
        CommandDispatcher<CommandSourceStack> dispatcher = srcDispatcher(source);
        CommandSourceStack target = srcClearCallbacks(functionSource);
        CommandResultCallback aggregate = CommandResultCallback.chain(srcCallback(source), control.currentFrame().returnValueConsumer());

        for (CommandFunction function : functions) {
            ResourceLocation id = function.getId();
            CommandResultCallback callback = decorate(source, callbacks, id, aggregate);
            queueFunction(args, control, dispatcher, target, function, id, callback, true);
        }

        if (aggregate != CommandResultCallback.EMPTY) {
            queueFallthrough(control);
        }
    }

    private static void runFunctionsNormal(Collection<CommandFunction> functions, @Nullable CompoundTag args,
                                           CommandSourceStack source, CommandSourceStack functionSource,
                                           ExecutionControl<CommandSourceStack> control, Callbacks callbacks) throws CommandSyntaxException {
        CommandDispatcher<CommandSourceStack> dispatcher = srcDispatcher(source);
        CommandSourceStack target = srcClearCallbacks(functionSource);
        CommandResultCallback sourceCallback = srcCallback(source);
        if (functions.isEmpty()) {
            return;
        }

        if (functions.size() == 1) {
            CommandFunction function = functions.iterator().next();
            ResourceLocation id = function.getId();
            queueFunction(args, control, dispatcher, target, function, id, decorate(source, callbacks, id, sourceCallback), false);
        } else if (sourceCallback == CommandResultCallback.EMPTY) {
            for (CommandFunction function : functions) {
                ResourceLocation id = function.getId();
                queueFunction(args, control, dispatcher, target, function, id, decorate(source, callbacks, id, sourceCallback), false);
            }
        } else {
            Accumulator accumulator = new Accumulator();
            CommandResultCallback collecting = (success, value) -> accumulator.add(value);
            for (CommandFunction function : functions) {
                ResourceLocation id = function.getId();
                queueFunction(args, control, dispatcher, target, function, id, decorate(source, callbacks, id, collecting), false);
            }
            queueAccumulatorFlush(control, accumulator, sourceCallback);
        }
    }

    private static CommandResultCallback decorate(CommandSourceStack source, Callbacks callbacks, ResourceLocation id, CommandResultCallback parent) {
        return srcIsSilent(source) ? parent : (success, value) -> {
            callbacks.signalResult(source, id, value);
            parent.onSuccess(value);
        };
    }

    private static void queueFunction(@Nullable CompoundTag args, ExecutionControl<CommandSourceStack> control,
                                      CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack target,
                                      CommandFunction function, ResourceLocation id, CommandResultCallback callback,
                                      boolean returnParentFrame) throws CommandSyntaxException {
        try {
            CommandFunction instantiated = ((PortedCommandFunction) function).instantiate(args, dispatcher, target);
            InstantiatedFunction<CommandSourceStack> engineFunction = new PortedInstantiatedFunction(instantiated);
            queueCall(control, engineFunction, callback, returnParentFrame, target);
        } catch (FunctionInstantiationException exception) {
            throw ERROR_FAILED_TO_INSTANTIATE.create(id, exception.messageComponent());
        }
    }

    // --- bridge helpers: CommandSourceStack -> raw ExecutionCommandSource (only valid at runtime via mixin) ---

    static CommandResultCallback srcCallback(CommandSourceStack source) {
        return ((ExecutionCommandSource) source).callback();
    }

    private static boolean srcIsSilent(CommandSourceStack source) {
        return ((ExecutionCommandSource) source).isSilent();
    }

    @SuppressWarnings("unchecked")
    private static CommandSourceStack srcClearCallbacks(CommandSourceStack source) {
        return (CommandSourceStack) ((ExecutionCommandSource) source).clearCallbacks();
    }

    @SuppressWarnings("unchecked")
    private static CommandDispatcher<CommandSourceStack> srcDispatcher(CommandSourceStack source) {
        return ((ExecutionCommandSource) source).dispatcher();
    }

    static void srcHandleError(CommandSourceStack source, CommandSyntaxException exception, boolean forked, @Nullable ServerFunctionManager.TraceCallbacks tracer) {
        ((ExecutionCommandSource) source).handleError(exception, forked, tracer);
    }

    // --- boundary helpers to the F-bounded engine statics/constructors ---

    @SuppressWarnings("unchecked")
    private static <T extends ExecutionCommandSource<T>> void queueCall(Object control, Object function, CommandResultCallback callback, boolean returnParentFrame, Object source) {
        ((ExecutionControl<T>) control).queueNext(new CallFunction<T>((InstantiatedFunction<T>) function, callback, returnParentFrame).bind((T) source));
    }

    @SuppressWarnings("unchecked")
    private static <T extends ExecutionCommandSource<T>> void queueFallthrough(Object control) {
        ((ExecutionControl<T>) control).queueNext(FallthroughTask.<T>instance());
    }

    @SuppressWarnings("unchecked")
    private static <T extends ExecutionCommandSource<T>> void queueAccumulatorFlush(Object control, Accumulator accumulator, CommandResultCallback sourceCallback) {
        ((ExecutionControl<T>) control).queueNext((executionContext, frame) -> {
            if (accumulator.any) {
                sourceCallback.onSuccess(accumulator.sum);
            }
        });
    }
}
