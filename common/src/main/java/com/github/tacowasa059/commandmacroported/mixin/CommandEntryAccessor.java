package com.github.tacowasa059.commandmacroported.mixin;

import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandFunction;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the {@link ParseResults} held by a 1.20.1 {@code CommandFunction.CommandEntry}
 * (mojmap field {@code parse}) so an instantiated function's lines can be converted into
 * engine {@code BuildContexts.Unbound} entries.
 */
@Mixin(CommandFunction.CommandEntry.class)
public interface CommandEntryAccessor {
    @Accessor("parse")
    ParseResults<CommandSourceStack> commandmacroported$getParse();
}
