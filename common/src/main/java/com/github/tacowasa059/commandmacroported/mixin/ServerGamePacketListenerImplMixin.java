package com.github.tacowasa059.commandmacroported.mixin;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ArgumentSignatures;
import net.minecraft.commands.arguments.SignedArgument;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.chat.SignableCommand;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fixes a secure-chat desync introduced by routing commands through the ported execution engine
 * / by this mod's modified command tree.
 *
 * <p>Background: the player's signed-message chain ({@code signedMessageDecoder}, an index-linked
 * chain on the connection) advances once per signed command argument in
 * {@code collectSignedArguments}. The set of arguments to sign/unpack is computed by
 * {@link SignableCommand#of} (vanilla code), which STOPS collecting at the first redirect back to
 * the dispatcher root (so e.g. the {@code say} message in {@code execute run say …} is NOT signed).
 *
 * <p>The CLIENT decides what to sign using the command tree the server sent it
 * ({@code ClientboundCommandsPacket}). With this server-side mod's command tree, the vanilla
 * client's {@code SignableCommand.of} can DISAGREE with the server's - the client signs the
 * redirect-trailing {@code message} argument while the server skips it. The client's encoder then
 * advances one step further than the server's decoder, so the very next normal chat message fails
 * {@code signedMessageDecoder.unpack} and the player is kicked with
 * {@code multiplayer.disconnect.unsigned_chat}. Confirmed via isolation: {@code say aaaa} is fine,
 * but {@code execute run say aaaa} / {@code return run say aaaa} kick the next chat;
 * {@code execute run scoreboard …} / {@code execute run tellraw …} (no signed message arg) do not.
 *
 * <p>Fix: redirect the {@code SignableCommand.of(parse)} call inside {@code performChatCommand} and
 * return a {@link SignableCommand} whose arguments exactly mirror what the client actually signed
 * ({@code packet.argumentSignatures()}), pulled from the full parse chain (without the
 * redirect-stop). This keeps the server's decoder in lockstep with the client's encoder regardless
 * of how the command tree diverges, and (as a bonus) lets {@code say}/{@code msg}/… behind a
 * redirect broadcast as properly signed messages.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {

    @SuppressWarnings("unchecked")
    @Redirect(
            method = "performChatCommand",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/chat/SignableCommand;of(Lcom/mojang/brigadier/ParseResults;)Lnet/minecraft/network/chat/SignableCommand;"
            )
    )
    private SignableCommand<CommandSourceStack> commandmacroported$alignSignedArguments(
            ParseResults<CommandSourceStack> parse,
            ServerboundChatCommandPacket packet,
            LastSeenMessages lastSeen) {
        String command = parse.getReader().getString();

        // Collect EVERY signable argument across the whole parse chain (no redirect-stop), keyed by name.
        Map<String, SignableCommand.Argument<CommandSourceStack>> all = new HashMap<>();
        for (CommandContextBuilder<CommandSourceStack> ctx = parse.getContext(); ctx != null; ctx = ctx.getChild()) {
            for (ParsedCommandNode<CommandSourceStack> parsed : ctx.getNodes()) {
                CommandNode<CommandSourceStack> node = parsed.getNode();
                if (node instanceof ArgumentCommandNode<?, ?> argNode && argNode.getType() instanceof SignedArgument) {
                    ParsedArgument<CommandSourceStack, ?> value = ctx.getArguments().get(argNode.getName());
                    if (value != null) {
                        all.putIfAbsent(
                                argNode.getName(),
                                new SignableCommand.Argument<>(
                                        (ArgumentCommandNode<CommandSourceStack, ?>) node,
                                        value.getRange().get(command)));
                    }
                }
            }
        }

        // Keep exactly the arguments the client signed, in the order the client signed them.
        List<SignableCommand.Argument<CommandSourceStack>> aligned = new ArrayList<>();
        for (ArgumentSignatures.Entry entry : packet.argumentSignatures().entries()) {
            SignableCommand.Argument<CommandSourceStack> arg = all.get(entry.name());
            if (arg != null) {
                aligned.add(arg);
            }
        }
        return new SignableCommand<>(aligned);
    }
}
