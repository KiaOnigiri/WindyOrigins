package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.CrossPhaseEntity;
import com.windykindy49.windyorigins.util.MirrorEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({EntityRenderDispatcher.class})
public class EntityRenderDispatcherMixin {
    @Inject(
        method = {"render"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private <E extends Entity> void onRenderEntity(
        E var1, double var2, double var4, double var6, float var8, float var9, MatrixStack var10, VertexConsumerProvider var11, int var12, CallbackInfo var13
    ) {
        if (!(var1 instanceof CrossPhaseEntity) || !((CrossPhaseEntity)var1).windyorigins$isCrossPhase()) {
            MinecraftClient var16 = MinecraftClient.getInstance();
            Entity var17 = var16.cameraEntity;
            if (var17 != null && ((MirrorEntity)var17).windyorigins$isInMirrorWorld() != ((MirrorEntity)var1).windyorigins$isInMirrorWorld()) {
                var13.cancel();
            }
        }
    }
}
