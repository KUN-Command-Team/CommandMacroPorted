package com.github.tacowasa059.commandmacroported.mixin;

import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Static invoker for the package-private {@code GameRules.IntegerValue.create(int)}.
 */
@Mixin(GameRules.IntegerValue.class)
public interface GameRulesIntegerValueInvoker {
    @Invoker("create")
    static GameRules.Type<GameRules.IntegerValue> commandmacroported$create(int defaultValue) {
        throw new AssertionError();
    }
}
