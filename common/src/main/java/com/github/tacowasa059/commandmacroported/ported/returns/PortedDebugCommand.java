package com.github.tacowasa059.commandmacroported.ported.returns;

import com.github.tacowasa059.commandmacroported.ported.FunctionInstantiationException;
import com.github.tacowasa059.commandmacroported.ported.PortedCommandFunction;
import com.github.tacowasa059.commandmacroported.ported.Tracer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.commands.CommandFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Faithful 1.20.3 {@code DebugCommand.TraceCustomExecutor} port (CommandSourceStack-specialised).
 *
 * <p>{@code /debug function} runs each function through the engine with a {@link Tracer} set on the
 * {@link ExecutionControl}, so the trace file records the deferred command/return/call flow
 * (including {@code return}). Rejects {@code return run debug function} and nested tracing, matching
 * 1.20.3.
 *
 * <p>Unlike 1.20.3 (whose {@code TraceCallbacks} is {@code AutoCloseable} and closed via
 * {@code ExecutionContext.close()}), 1.20.1's {@code ServerFunctionManager.TraceCallbacks} has no
 * {@code close()}, so the trace writer is closed in the final queued task after all functions run.
 */
public final class PortedDebugCommand {
    private static final Logger LOGGER = LogUtils.getLogger();
    // 1.20.1 client key; localizes.
    static final SimpleCommandExceptionType ERROR_NO_RECURSION = new SimpleCommandExceptionType(
            Component.translatable("commands.debug.function.noRecursion"));
    // 1.20.3 key, absent on 1.20.1 clients -> literal with the vanilla 1.20.3 wording.
    static final SimpleCommandExceptionType ERROR_NO_RETURN_RUN = new SimpleCommandExceptionType(
            Component.literal("Tracing can't be used with return run"));

    private PortedDebugCommand() {
    }

    public static final class TraceCustomExecutor implements CustomCommandExecutor.CommandAdapter<CommandSourceStack> {
        @Override
        public void run(CommandSourceStack source, ContextChain<CommandSourceStack> contextChain, ChainModifiers modifiers, ExecutionControl<CommandSourceStack> control) {
            try {
                this.runGuarded(source, contextChain, modifiers, control);
            } catch (CommandSyntaxException exception) {
                PortedFunctionCommand.srcHandleError(source, exception, modifiers.isForked(), control.tracer());
                PortedFunctionCommand.srcCallback(source).onFailure();
            }
        }

        private void runGuarded(CommandSourceStack source, ContextChain<CommandSourceStack> contextChain, ChainModifiers modifiers, ExecutionControl<CommandSourceStack> control) throws CommandSyntaxException {
            if (modifiers.isReturn()) {
                throw ERROR_NO_RETURN_RUN.create();
            }
            if (control.tracer() != null) {
                throw ERROR_NO_RECURSION.create();
            }

            CommandContext<CommandSourceStack> context = contextChain.getTopContext();
            Collection<CommandFunction> functions = FunctionArgument.getFunctions(context, "name");
            MinecraftServer server = source.getServer();
            String fileName = "debug-trace-" + Util.getFilenameFormattedDateTime() + ".txt";
            CommandDispatcher<CommandSourceStack> dispatcher = server.getFunctions().getDispatcher();
            int count = 0;

            try {
                Path dir = server.getFile("debug").toPath();
                Files.createDirectories(dir);
                final PrintWriter writer = new PrintWriter(Files.newBufferedWriter(dir.resolve(fileName), StandardCharsets.UTF_8));
                Tracer tracer = new Tracer(writer);
                control.tracer(tracer);

                for (CommandFunction function : functions) {
                    try {
                        CommandSourceStack functionSource = source.withSource(tracer).withMaximumPermission(2);
                        CommandFunction instantiated = ((PortedCommandFunction) function).instantiate(null, dispatcher, functionSource);
                        InstantiatedFunction<CommandSourceStack> engineFunction = new PortedInstantiatedFunction(instantiated);
                        ResourceLocation id = function.getId();
                        control.queueNext((executionContext, frame) -> writer.println(id));
                        queueCall(control, engineFunction, CommandResultCallback.EMPTY, functionSource);
                        count += engineFunction.entries().size();
                    } catch (FunctionInstantiationException exception) {
                        source.sendFailure(exception.messageComponent());
                    }
                }

                int total = count;
                control.queueNext((executionContext, frame) -> {
                    if (functions.size() == 1) {
                        source.sendSuccess(() -> Component.translatable("commands.debug.function.success.single",
                                total, functions.iterator().next().getId(), fileName), true);
                    } else {
                        source.sendSuccess(() -> Component.translatable("commands.debug.function.success.multiple",
                                total, functions.size(), fileName), true);
                    }
                    writer.close();
                });
            } catch (IOException | UncheckedIOException exception) {
                LOGGER.warn("Tracing failed", exception);
                source.sendFailure(Component.translatable("commands.debug.function.traceFailed"));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends ExecutionCommandSource<T>> void queueCall(Object control, Object function, CommandResultCallback callback, Object source) {
        ((ExecutionControl<T>) control).queueNext(new CallFunction<T>((InstantiatedFunction<T>) function, callback, false).bind((T) source));
    }
}
