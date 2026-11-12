package com.windykindy49.windyorigins.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.windykindy49.windyorigins.common.Powers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Beacon: a Luminian cannot hide, so crouching neither dims its name tag nor hides it behind walls. */
@Mixin({EntityRenderer.class})
public abstract class LuminianNameTagMixin {
    @ModifyExpressionValue(
        method = {"renderLabelIfPresent"},
        at = {@At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isSneaky()Z")}
    )
    private boolean windyorigins$beaconName(boolean var1, Entity var2, Text var3, MatrixStack var4, VertexConsumerProvider var5, int var6) {
        return var1 && !Powers.has(var2, "beacon");
    }
}
