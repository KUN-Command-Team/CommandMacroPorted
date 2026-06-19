package com.github.tacowasa059.commandmacroported.ported.returns;

import com.github.tacowasa059.commandmacroported.mixin.CommandEntryAccessor;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapts an already-instantiated 1.20.1 {@link CommandFunction} (whose entries are
 * {@code CommandFunction.CommandEntry} holding parsed commands) into the engine's
 * {@link InstantiatedFunction}, whose entries are {@link UnboundEntryAction}s.
 *
 * <p>This lets the existing macro pipeline ({@code PortedCommandFunction}) keep handling
 * parsing/substitution while function bodies run through the deferred engine, so that
 * {@code return}/{@code return run}/{@code return fail} inside a function propagate via the
 * call {@link Frame} instead of the old abort-flag deque model.
 */
public final class PortedInstantiatedFunction implements InstantiatedFunction<CommandSourceStack> {
    private final ResourceLocation id;
    private final List<UnboundEntryAction<CommandSourceStack>> entries;

    public PortedInstantiatedFunction(CommandFunction instantiated) {
        this.id = instantiated.getId();
        List<UnboundEntryAction<CommandSourceStack>> list = new ArrayList<>();

        for (CommandFunction.Entry entry : instantiated.getEntries()) {
            // After instantiation every line is a CommandEntry (macros already substituted).
            if (entry instanceof CommandFunction.CommandEntry commandEntry) {
                ParseResults<CommandSourceStack> parse = ((CommandEntryAccessor) commandEntry).commandmacroported$getParse();
                String input = parse.getReader().getString();
                ContextChain<CommandSourceStack> chain = ContextChain.tryFlatten(parse.getContext().build(input))
                        .orElseThrow(() -> new IllegalStateException("Function line is not executable: " + input));
                list.add(makeUnbound(input, chain));
            }
        }

        this.entries = List.copyOf(list);
    }

    @Override
    public ResourceLocation id() {
        return this.id;
    }

    @Override
    public List<UnboundEntryAction<CommandSourceStack>> entries() {
        return this.entries;
    }

    // BuildContexts.Unbound is F-bounded (<T extends ExecutionCommandSource<T>>); built raw to
    // dodge javac's bound check (CommandSourceStack only satisfies it at runtime via mixin).
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static UnboundEntryAction<CommandSourceStack> makeUnbound(String input, ContextChain<CommandSourceStack> chain) {
        return new BuildContexts.Unbound(input, chain);
    }
}
