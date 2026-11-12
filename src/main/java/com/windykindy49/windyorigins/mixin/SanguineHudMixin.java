package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.common.Powers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin({InGameHud.class})
public abstract class SanguineHudMixin {
    private static final Identifier BLOOD = new Identifier("windyorigins", "textures/gui/blood.png");

    @ModifyArg(
        method = {"renderStatusBars"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V"
        ),
        index = 0
    )
    private Identifier windyorigins$bloodRow(Identifier var1, int var2, int var3, int var4, int var5, int var6, int var7) {
        MinecraftClient var8 = MinecraftClient.getInstance();
        return var5 == 27 && var8.player != null && Powers.has(var8.player, "blood_thirst") ? BLOOD : var1;
    }
}
