package com.github.tacowasa059.commandmacroported.ported.returns;

import com.github.tacowasa059.commandmacroported.ported.ModGameRules;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.GameRules;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * 1.20.1 port of 1.20.3's {@code Commands#performCommand}, {@code finishParsing} and
 * {@code executeCommandInContext}: parses into a {@link ContextChain} and drives it
 * through the deferred {@link ExecutionContext} engine instead of the old
 * {@code CommandDispatcher#execute}. This is what makes the {@code /return} family
 * (and any {@code CustomCommandExecutor}/{@code CustomModifierExecutor}) actually fire.
 *
 * <p>1.20.3 made {@code performCommand} return {@code void}; 1.20.1 callers still expect
 * an {@code int}, so we capture the top-level result via a {@link CommandResultCallback}.
 *
 * <p>1.20.3 reads the fork limit from {@code GameRules.RULE_MAX_COMMAND_FORK_COUNT}; that rule
 * does not exist in 1.20.1, so the mod registers it ({@link ModGameRules}) and we read it here.
 */
public final class CommandExecution {
    private static final ThreadLocal<ExecutionContext<CommandSourceStack>> CURRENT = new ThreadLocal<>();

    private CommandExecution() {
    }

    public static int performCommand(ParseResults<CommandSourceStack> parse, String command) {
        CommandSourceStack source = parse.getContext().getSource();
        ProfilerFiller profiler = source.getServer().getProfiler();
        profiler.push(() -> "/" + command);
        int[] result = new int[]{0};

        try {
            ContextChain<CommandSourceStack> contextChain = finishParsing(parse, command, source);
            if (contextChain != null) {
                executeCommandInContext(source, executionContext ->
                        queueInitialCommandExecution(executionContext, command, contextChain, source,
                                (success, value) -> {
                                    if (success) {
                                        result[0] = value;
                                    }
                                }));
            }
        } catch (Exception exception) {
            MutableComponent message = Component.literal(exception.getMessage() == null ? exception.getClass().getName() : exception.getMessage());
            source.sendFailure(Component.translatable("command.failed").withStyle(
                    style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, message))));
            if (SharedConstants.IS_RUNNING_IN_IDE) {
                source.sendFailure(Component.literal(Util.describeError(exception)));
            }
        } finally {
            profiler.pop();
        }

        return result[0];
    }

    @Nullable
    private static ContextChain<CommandSourceStack> finishParsing(ParseResults<CommandSourceStack> parse, String command, CommandSourceStack source) {
        try {
            CommandSyntaxException parseException = Commands.getParseException(parse);
            if (parseException != null) {
                throw parseException;
            }

            return ContextChain.tryFlatten(parse.getContext().build(command)).orElseThrow(
                    () -> CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(parse.getReader()));
        } catch (CommandSyntaxException exception) {
            source.sendFailure(ComponentUtils.fromMessage(exception.getRawMessage()));
            if (exception.getInput() != null && exception.getCursor() >= 0) {
                int cursor = Math.min(exception.getInput().length(), exception.getCursor());
                MutableComponent pointer = Component.empty().withStyle(ChatFormatting.GRAY).withStyle(
                        style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/" + command)));
                if (cursor > 10) {
                    pointer.append(CommonComponents.ELLIPSIS);
                }

                pointer.append(exception.getInput().substring(Math.max(0, cursor - 10), cursor));
                if (cursor < exception.getInput().length()) {
                    Component rest = Component.literal(exception.getInput().substring(cursor)).withStyle(ChatFormatting.RED, ChatFormatting.UNDERLINE);
                    pointer.append(rest);
                }

                pointer.append(Component.translatable("command.context.here").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
                source.sendFailure(pointer);
            }

            return null;
        }
    }

    public static void executeCommandInContext(CommandSourceStack source, Consumer<ExecutionContext<CommandSourceStack>> action) {
        MinecraftServer server = source.getServer();
        ExecutionContext<CommandSourceStack> current = CURRENT.get();
        if (current == null) {
            int commandLimit = Math.max(1, server.getGameRules().getInt(GameRules.RULE_MAX_COMMAND_CHAIN_LENGTH));
            int forkLimit = ModGameRules.RULE_MAX_COMMAND_FORK_COUNT != null
                    ? server.getGameRules().getInt(ModGameRules.RULE_MAX_COMMAND_FORK_COUNT)
                    : ModGameRules.DEFAULT_MAX_COMMAND_FORK_COUNT;
            try (ExecutionContext<CommandSourceStack> executionContext = new ExecutionContext<>(commandLimit, forkLimit, server.getProfiler())) {
                CURRENT.set(executionContext);
                action.accept(executionContext);
                executionContext.runCommandQueue();
            } finally {
                CURRENT.set(null);
            }
        } else {
            action.accept(current);
        }
    }

    // --- boundary helpers to the F-bounded engine statics ---
    // The engine statics are <T extends ExecutionCommandSource<T>>; at compile time javac
    // does not see CommandSourceStack satisfying that self-bound, so we delegate to a generic
    // method whose T is unconstrained (erases to the bound) and cast through Object inside.

    static void queueInitialCommandExecution(ExecutionContext<CommandSourceStack> executionContext, String command,
                                             ContextChain<CommandSourceStack> contextChain, CommandSourceStack source,
                                             CommandResultCallback callback) {
        queueInitialCommandExecution0(executionContext, command, contextChain, source, callback);
    }

    @SuppressWarnings("unchecked")
    private static <T extends ExecutionCommandSource<T>> void queueInitialCommandExecution0(Object executionContext, String command,
                                                                                           Object contextChain, Object source,
                                                                                           CommandResultCallback callback) {
        ExecutionContext.queueInitialCommandExecution(
                (ExecutionContext<T>) executionContext, command, (ContextChain<T>) contextChain, (T) source, callback);
    }

    public static void queueInitialFunctionCall(ExecutionContext<CommandSourceStack> executionContext,
                                                InstantiatedFunction<CommandSourceStack> function, CommandSourceStack source,
                                                CommandResultCallback callback) {
        queueInitialFunctionCall0(executionContext, function, source, callback);
    }

    @SuppressWarnings("unchecked")
    private static <T extends ExecutionCommandSource<T>> void queueInitialFunctionCall0(Object executionContext,
                                                                                       Object function, Object source,
                                                                                       CommandResultCallback callback) {
        ExecutionContext.queueInitialFunctionCall(
                (ExecutionContext<T>) executionContext, (InstantiatedFunction<T>) function, (T) source, callback);
    }
}
