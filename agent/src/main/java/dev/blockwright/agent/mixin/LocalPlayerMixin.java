package dev.blockwright.agent.mixin;

import dev.blockwright.agent.AgentHooks;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin {
    @Inject(method = "tick", at = @At("RETURN"))
    private void blockwright$afterTick(CallbackInfo info) {
        AgentHooks.afterPlayerTick((LocalPlayer) (Object) this);
    }
}
