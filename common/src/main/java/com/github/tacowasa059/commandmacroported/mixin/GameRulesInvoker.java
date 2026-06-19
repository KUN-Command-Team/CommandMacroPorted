package com.github.tacowasa059.commandmacroported.mixin;

import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Static invoker for the package-private {@code GameRules.register}, so the mod can register the
 * {@code maxCommandForkCount} rule. Using a mixin invoker (rather than reflection) keeps it
 * mapping-aware across Forge (SRG) and Fabric (intermediary).
 */
@Mixin(GameRules.class)
public interface GameRulesInvoker {
    @Invoker("register")
    static <T extends GameRules.Value<T>> GameRules.Key<T> commandmacroported$register(
            String name, GameRules.Category category, GameRules.Type<T> type) {
        throw new AssertionError();
    }
}
