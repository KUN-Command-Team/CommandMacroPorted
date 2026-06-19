package com.github.tacowasa059.commandmacroported.ported;

import com.github.tacowasa059.commandmacroported.mixin.GameRulesIntegerValueInvoker;
import com.github.tacowasa059.commandmacroported.mixin.GameRulesInvoker;
import net.minecraft.world.level.GameRules;

/**
 * Registers the 1.20.3 {@code maxCommandForkCount} game rule (absent in 1.20.1) so the engine's
 * fork limit is configurable via {@code /gamerule maxCommandForkCount} exactly like vanilla 1.20.3,
 * instead of a hard-coded constant. Registering the rule is enough for the vanilla {@code /gamerule}
 * command to list and set it.
 *
 * <p>Must be registered during mod init (before any world's {@code GameRules} is built); see
 * {@code CommandMacroPortedCommon#init}.
 */
public final class ModGameRules {
    public static final int DEFAULT_MAX_COMMAND_FORK_COUNT = 65536;

    public static GameRules.Key<GameRules.IntegerValue> RULE_MAX_COMMAND_FORK_COUNT;

    private ModGameRules() {
    }

    public static void register() {
        if (RULE_MAX_COMMAND_FORK_COUNT == null) {
            RULE_MAX_COMMAND_FORK_COUNT = GameRulesInvoker.commandmacroported$register(
                    "maxCommandForkCount",
                    GameRules.Category.MISC,
                    GameRulesIntegerValueInvoker.commandmacroported$create(DEFAULT_MAX_COMMAND_FORK_COUNT));
        }
    }
}
