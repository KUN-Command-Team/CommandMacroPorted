package com.github.tacowasa059.commandmacroported;

import com.github.tacowasa059.commandmacroported.ported.ModGameRules;

public final class CommandMacroPortedCommon {
    public static final String MOD_ID = "commandmacroported";

    private CommandMacroPortedCommon() {
    }

    public static void init() {
        ModGameRules.register();
    }
}
