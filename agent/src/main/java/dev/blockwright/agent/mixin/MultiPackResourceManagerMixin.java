package dev.blockwright.agent.mixin;

import dev.blockwright.agent.AgentHooks;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPackResourceManager.class)
abstract class MultiPackResourceManagerMixin {
    @Inject(method = "getResource", at = @At("HEAD"))
    private void blockwright$countResource(Identifier id, CallbackInfoReturnable<?> info) {
        AgentHooks.recordResourceLookup(id.getPath());
    }

    @Inject(method = "getResourceStack", at = @At("HEAD"))
    private void blockwright$countStack(Identifier id, CallbackInfoReturnable<?> info) {
        AgentHooks.recordResourceLookup(id.getPath());
    }

    @Inject(method = "listResources", at = @At("HEAD"))
    private void blockwright$countListing(String directory, ResourceManager.Selector selector, CallbackInfoReturnable<?> info) {
        AgentHooks.recordResourceLookup(directory);
    }

    @Inject(method = "listResourceStacks", at = @At("HEAD"))
    private void blockwright$countStackListing(String directory, ResourceManager.Selector selector, CallbackInfoReturnable<?> info) {
        AgentHooks.recordResourceLookup(directory);
    }
}
