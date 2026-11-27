package dev.blockwright.agent.mixin;

import dev.blockwright.agent.AgentHooks;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
abstract class KeyboardInputMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void blockwright$beforeInput(CallbackInfo info) {
        AgentHooks.beforeKeyboardInput();
    }
}
