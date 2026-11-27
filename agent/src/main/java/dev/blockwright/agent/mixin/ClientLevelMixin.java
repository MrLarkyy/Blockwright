package dev.blockwright.agent.mixin;

import dev.blockwright.agent.AgentHooks;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Block break particles are built from block models, which the logic profile does not load. */
@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {
    @Inject(method = {"addDestroyBlockEffect", "addBreakingBlockEffects"}, at = @At("HEAD"), cancellable = true)
    private void blockwright$skipBlockParticles(CallbackInfo info) {
        if (AgentHooks.cutsParticles()) info.cancel();
    }
}
