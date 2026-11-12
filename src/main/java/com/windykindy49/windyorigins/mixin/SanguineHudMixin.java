package com.windykindy49.windyorigins.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.windykindy49.windyorigins.common.Powers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({InGameHud.class})
public abstract class SanguineHudMixin {
    private static final Identifier BLOOD = new Identifier("windyorigins", "textures/gui/blood.png");

    @WrapOperation(
        method = {"renderStatusBars"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/hud/InGameHud;drawTexture(Lnet/minecraft/client/util/math/MatrixStack;IIIIII)V"
        )
    )
    private void windyorigins$bloodRow(MatrixStack var1, int var2, int var3, int var4, int var5, int var6, int var7, Operation<Void> var8) {
        MinecraftClient var9 = MinecraftClient.getInstance();
        boolean var10 = var5 == 27 && var9.player != null && Powers.has(var9.player, "blood_thirst");
        if (var10) {
            RenderSystem.setShaderTexture(0, BLOOD);
        }

        var8.call(var1, var2, var3, var4, var5, var6, var7);
        if (var10) {
            RenderSystem.setShaderTexture(0, DrawableHelper.GUI_ICONS_TEXTURE);
        }
    }
}
