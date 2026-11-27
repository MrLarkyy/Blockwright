package dev.blockwright.agent.mixin;

import dev.blockwright.agent.AgentHooks;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The loading screen waits for the player's section to be compiled for rendering, which never happens in logic. */
@Mixin(targets = "net.minecraft.client.multiplayer.LevelLoadTracker$WaitingForPlayerChunk")
abstract class WaitingForPlayerChunkMixin {
    @Shadow
    public abstract LocalPlayer player();

    @Shadow
    public abstract ClientLevel level();

    @Inject(method = "isReady", at = @At("RETURN"), cancellable = true)
    private void blockwright$readyWithChunkData(CallbackInfoReturnable<Boolean> info) {
        if (!info.getReturnValueZ() && AgentHooks.playerChunkReceived(level(), player())) info.setReturnValue(true);
    }
}
