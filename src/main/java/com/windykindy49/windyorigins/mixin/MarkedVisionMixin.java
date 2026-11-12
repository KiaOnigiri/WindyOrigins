package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.bounty.client.ClientBountyState;
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
public class MarkedVisionMixin {
    @Inject(
        method = {"render"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private <E extends Entity> void windyorigins$narrowMarkedSight(
        E var1, double var2, double var4, double var6, float var8, float var9, MatrixStack var10, VertexConsumerProvider var11, int var12, CallbackInfo var13
    ) {
        if (ClientBountyState.isMarked()) {
            MinecraftClient var14 = MinecraftClient.getInstance();
            Entity var15 = var14.cameraEntity;
            if (var15 != null && var1 != var15 && var1 != var15.getVehicle()) {
                if (var15.squaredDistanceTo(var1) > 25.0) {
                    var13.cancel();
                }
            }
        }
    }
}
