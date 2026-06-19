package com.github.tacowasa059.commandmacroported.mixin;

import com.github.tacowasa059.commandmacroported.ported.ModSuggestionProviders;
import com.github.tacowasa059.commandmacroported.ported.returns.PortedFunctionCommand;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.FunctionCommand;
import net.minecraft.server.commands.data.DataAccessor;
import net.minecraft.server.commands.data.DataCommands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/**
 * Registers {@code /function} as an engine {@link PortedFunctionCommand.FunctionCustomExecutor}
 * (faithful 1.20.3 behaviour) instead of a plain {@code executes} lambda, so that a function's
 * {@code return} propagates and its returned value is reported.
 */
@Mixin(FunctionCommand.class)
public abstract class FunctionCommandMixin {

    @Unique
    private static final DynamicCommandExceptionType ERROR_ARGUMENT_NOT_COMPOUND = new DynamicCommandExceptionType((p_296505_) -> {
        return Component.translatable("commands.function.error.argument_not_compound", p_296505_);
    });

    @Inject(method = "register", at = @At("HEAD"), cancellable = true)
    private static void injectRegister(CommandDispatcher<CommandSourceStack> dispatcher, CallbackInfo ci) {
        commandmacroported$register(dispatcher);
        ci.cancel();
    }

    @Unique
    private static void commandmacroported$register(CommandDispatcher<CommandSourceStack> p_137715_) {
        LiteralArgumentBuilder<CommandSourceStack> literalargumentbuilder = Commands.literal("with");

        for (DataCommands.DataProvider datacommands$dataprovider : DataCommands.SOURCE_PROVIDERS) {
            datacommands$dataprovider.wrap(literalargumentbuilder, (p_296503_) -> {
                return p_296503_.executes(new PortedFunctionCommand.FunctionCustomExecutor() {
                    @Override
                    protected CompoundTag arguments(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
                        return datacommands$dataprovider.access(context).getData();
                    }
                }).then(Commands.argument("path", NbtPathArgument.nbtPath()).executes(new PortedFunctionCommand.FunctionCustomExecutor() {
                    @Override
                    protected CompoundTag arguments(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
                        return commandmacroported$getArgumentTag(NbtPathArgument.getPath(context, "path"), datacommands$dataprovider.access(context));
                    }
                }));
            });
        }

        p_137715_.register(Commands.literal("function").requires((p_137722_) -> {
            return p_137722_.hasPermission(2);
        }).then(Commands.argument("name", FunctionArgument.functions()).suggests(ModSuggestionProviders.SUGGEST_FUNCTION).executes(new PortedFunctionCommand.FunctionCustomExecutor() {
            @Override
            @Nullable
            protected CompoundTag arguments(CommandContext<CommandSourceStack> context) {
                return null;
            }
        }).then(Commands.argument("arguments", CompoundTagArgument.compoundTag()).executes(new PortedFunctionCommand.FunctionCustomExecutor() {
            @Override
            protected CompoundTag arguments(CommandContext<CommandSourceStack> context) {
                return CompoundTagArgument.getCompoundTag(context, "arguments");
            }
        })).then(literalargumentbuilder)));
    }

    @Unique
    private static CompoundTag commandmacroported$getArgumentTag(NbtPathArgument.NbtPath p_298274_, DataAccessor dataAccessor) throws CommandSyntaxException {
        Tag tag = DataCommandsMixin.commandmacroported$getSingleTag(p_298274_, dataAccessor);
        if (tag instanceof CompoundTag) {
            return (CompoundTag) tag;
        } else {
            throw ERROR_ARGUMENT_NOT_COMPOUND.create(tag.getType().getName());
        }
    }
}
