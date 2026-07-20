package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import com.windykindy49.mirroredorigin.util.CrossPhaseEntity;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={EntityRenderDispatcher.class})
public class EntityRenderDispatcherMixin {
    @Inject(method={"render"}, at={@At(value="HEAD")}, cancellable=true)
    private <E extends Entity> void onRenderEntity(E entity, double x, double y, double z, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        boolean isEntityInMirror;
        boolean isCameraInMirror;
        // Phase-neutral entities - weather lightning - are drawn for everyone.
        if (entity instanceof CrossPhaseEntity && ((CrossPhaseEntity)entity).mirroredorigin$isCrossPhase()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        Entity cameraEntity = client.cameraEntity;
        if (cameraEntity != null && (isCameraInMirror = ((MirrorEntity)cameraEntity).mirroredorigin$isInMirrorWorld()) != (isEntityInMirror = ((MirrorEntity)entity).mirroredorigin$isInMirrorWorld())) {
            ci.cancel();
        }
    }
}

