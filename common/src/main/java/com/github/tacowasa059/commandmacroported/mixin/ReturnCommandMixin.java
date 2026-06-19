package com.github.tacowasa059.commandmacroported.mixin;

import com.github.tacowasa059.commandmacroported.ported.returns.PortedReturnCommand;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.ReturnCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(ReturnCommand.class)
public class ReturnCommandMixin {
    @Inject(method = "register", at=@At("HEAD"), cancellable = true)
    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, CallbackInfo ci) {
        LiteralArgumentBuilder<CommandSourceStack> root =
                LiteralArgumentBuilder.<CommandSourceStack>literal("return")
                        .requires(source -> source.hasPermission(2));

        RequiredArgumentBuilder valueArg =
                RequiredArgumentBuilder.<CommandSourceStack, Integer>argument(
                                "value",
                                IntegerArgumentType.integer()
                        )
                        .executes(new PortedReturnCommand.ReturnValueCustomExecutor());

        LiteralArgumentBuilder fail =
                LiteralArgumentBuilder.<CommandSourceStack>literal("fail")
                        .executes(new PortedReturnCommand.ReturnFailCustomExecutor());

        LiteralArgumentBuilder run =
                LiteralArgumentBuilder.<CommandSourceStack>literal("run")
                        .forward(
                                dispatcher.getRoot(),
                                new PortedReturnCommand.ReturnFromCommandCustomModifier(),
                                false
                        );


        root.then(valueArg);
        root.then(fail);
        root.then(run);

        dispatcher.register(root);
        ci.cancel();
        ci.cancel();
    }

}
