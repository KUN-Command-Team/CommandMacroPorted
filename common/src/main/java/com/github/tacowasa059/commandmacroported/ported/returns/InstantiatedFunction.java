package com.github.tacowasa059.commandmacroported.ported.returns;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public interface InstantiatedFunction<T> {
    ResourceLocation id();

    List<UnboundEntryAction<T>> entries();
}
