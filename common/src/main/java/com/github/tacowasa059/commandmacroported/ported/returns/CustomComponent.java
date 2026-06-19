package com.github.tacowasa059.commandmacroported.ported.returns;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;

import javax.annotation.Nullable;

public class CustomComponent {
    static MutableComponent translatableEscape(String p_312579_, Object... p_312922_) {
        for(int i = 0; i < p_312922_.length; ++i) {
            Object object = p_312922_[i];
            if (!isAllowedPrimitiveArgument(object) && !(object instanceof Component)) {
                p_312922_[i] = String.valueOf(object);
            }
        }

        return Component.translatable(p_312579_, p_312922_);
    }

    public static boolean isAllowedPrimitiveArgument(@Nullable Object p_313191_) {
        return p_313191_ instanceof Number || p_313191_ instanceof Boolean || p_313191_ instanceof String;
    }
}
