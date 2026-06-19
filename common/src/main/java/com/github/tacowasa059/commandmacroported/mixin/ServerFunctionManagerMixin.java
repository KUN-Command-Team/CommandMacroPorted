package com.github.tacowasa059.commandmacroported.mixin;

import com.github.tacowasa059.commandmacroported.accessor.ServerFunctionManagerAccessor;
import com.github.tacowasa059.commandmacroported.ported.FunctionInstantiationException;
import com.github.tacowasa059.commandmacroported.ported.PortedCommandFunction;
import com.github.tacowasa059.commandmacroported.ported.returns.CommandExecution;
import com.github.tacowasa059.commandmacroported.ported.returns.CommandResultCallback;
import com.github.tacowasa059.commandmacroported.ported.returns.InstantiatedFunction;
import com.github.tacowasa059.commandmacroported.ported.returns.PortedInstantiatedFunction;
import net.minecraft.commands.CommandFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.ServerFunctionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

@Mixin(ServerFunctionManager.class)
public abstract class ServerFunctionManagerMixin implements ServerFunctionManagerAccessor {

    @Shadow
    public abstract int execute(CommandFunction p_179961_, CommandSourceStack p_179962_, @Nullable ServerFunctionManager.TraceCallbacks p_179963_);

    public int commandmacroported$execute(CommandFunction precommandFunction, CommandSourceStack p_179962_,
                                          @Nullable ServerFunctionManager.TraceCallbacks p_179963_, @Nullable CompoundTag p_300204_)
            throws FunctionInstantiationException {
        ServerFunctionManager manager = (ServerFunctionManager) (Object) this;
        CommandFunction commandfunction = ((PortedCommandFunction)precommandFunction).instantiate(p_300204_, manager.getDispatcher(), p_179962_);

        // Run the function body through the ported 1.20.3 deferred engine (queueInitialFunctionCall)
        // instead of the 1.20.1 deque model, so that `return`/`return run`/`return fail` inside the
        // function propagate through the call Frame. The function's return value is delivered via the
        // callback below (the engine only fires it when the function executes `return`).
        InstantiatedFunction<CommandSourceStack> instantiated = new PortedInstantiatedFunction(commandfunction);
        int[] result = new int[]{0};
        CommandResultCallback callback = (success, value) -> {
            if (success) {
                result[0] = value;
            }
        };
        CommandExecution.executeCommandInContext(p_179962_, executionContext ->
                CommandExecution.queueInitialFunctionCall(executionContext, instantiated, p_179962_, callback));
        return result[0];
    }

    @Inject(method = "execute(Lnet/minecraft/commands/CommandFunction;Lnet/minecraft/commands/CommandSourceStack;)I", at = @At("HEAD"), cancellable = true)
    public void commandmacroported$executeinjection(CommandFunction functionObject, CommandSourceStack source, CallbackInfoReturnable<Integer> cir) {
        try {
            cir.setReturnValue(this.commandmacroported$execute(functionObject, source, (ServerFunctionManager.TraceCallbacks)null, (CompoundTag)null));
        } catch (FunctionInstantiationException functioninstantiationexception) {
            cir.setReturnValue(0);
        }
        cir.cancel();
    }
}
