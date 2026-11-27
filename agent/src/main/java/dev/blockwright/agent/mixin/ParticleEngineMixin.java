package dev.blockwright.agent.mixin;

import dev.blockwright.agent.AgentHooks;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
abstract class ParticleEngineMixin {
    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void blockwright$skipCreate(CallbackInfoReturnable<Particle> info) {
        if (AgentHooks.cutsParticles()) info.setReturnValue(null);
    }

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void blockwright$skipAdd(CallbackInfo info) {
        if (AgentHooks.cutsParticles()) info.cancel();
    }

    @Inject(
        method = {
            "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;)V",
            "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V"
        },
        at = @At("HEAD"),
        cancellable = true
    )
    private void blockwright$skipEmitter(CallbackInfo info) {
        if (AgentHooks.cutsParticles()) info.cancel();
    }
}
