package com.github.tacowasa059.commandmacroported.mixin;

import com.github.tacowasa059.commandmacroported.ported.ModSuggestionProviders;
import com.github.tacowasa059.commandmacroported.ported.returns.PortedExecuteCommand;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.server.commands.ExecuteCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds {@code execute (if|unless) function <name>} (1.20.3) by appending a {@code function} branch
 * to the {@code if}/{@code unless} subtree built by vanilla {@code ExecuteCommand.addConditionals}.
 *
 * <p>Rather than rewrite the whole {@code /execute} tree, we mutate the {@code ArgumentBuilder}
 * returned by {@code addConditionals} (at {@code RETURN}, before the caller attaches it). The branch
 * forks back to the dispatcher root with {@link PortedExecuteCommand.ExecuteIfFunctionCustomModifier},
 * which runs the function(s) through the engine and continues only when the result matches.
 */
@Mixin(ExecuteCommand.class)
public class ExecuteCommandMixin {

    @Inject(method = "addConditionals", at = @At("RETURN"))
    private static void commandmacroported$addIfUnlessFunction(
            CommandNode<CommandSourceStack> root,
            LiteralArgumentBuilder<CommandSourceStack> builder,
            boolean isIf,
            CommandBuildContext buildContext,
            CallbackInfoReturnable<ArgumentBuilder<CommandSourceStack, ?>> cir) {
        cir.getReturnValue().then(
                Commands.literal("function").then(
                        Commands.argument("name", FunctionArgument.functions())
                                .suggests(ModSuggestionProviders.SUGGEST_FUNCTION)
                                .fork(root, new PortedExecuteCommand.ExecuteIfFunctionCustomModifier(isIf))));
    }
}
