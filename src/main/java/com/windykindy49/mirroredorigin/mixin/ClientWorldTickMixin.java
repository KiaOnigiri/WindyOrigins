package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import com.windykindy49.mirroredorigin.util.CrossPhaseEntity;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import com.windykindy49.mirroredorigin.util.PhaseScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ClientWorld.class})
public class ClientWorldTickMixin {
    /**
     * Client-side counterpart of the reset ServerWorldTickMixin does per world tick. The
     * client opens phase scopes too (EntityContextMixin around Entity#tick), and nothing
     * else would ever clean up after a tick that threw. No entity is being ticked at this
     * point, so anything still on the stack is leftover.
     */
    @Inject(method={"tickEntities"}, at={@At(value="HEAD")}, require = 0)
    private void mirroredorigin$resetLeakedScopes(CallbackInfo ci) {
        PhaseScope.reset();
    }

    @Inject(method={"tickEntity"}, at={@At(value="HEAD")}, cancellable=true)
    private void onTickEntity(Entity entity, CallbackInfo ci) {
        boolean playerPhase;
        boolean entityPhase;
        // Weather lightning is ticked by everyone: without this it would be frozen for one
        // of the two worlds, drawn but never animating or fading out.
        if (entity instanceof CrossPhaseEntity && ((CrossPhaseEntity)entity).mirroredorigin$isCrossPhase()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.cameraEntity != null && entity != client.cameraEntity && (entityPhase = ((MirrorEntity)entity).mirroredorigin$isInMirrorWorld()) != (playerPhase = ((MirrorEntity)client.cameraEntity).mirroredorigin$isInMirrorWorld())) {
            ci.cancel();
        }
    }
}

