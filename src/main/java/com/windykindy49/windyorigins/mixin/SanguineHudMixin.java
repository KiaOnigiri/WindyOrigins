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
    @ModifyArg(
        method = {"renderStatusBars"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V"
        ),
        index = 0
    )
    private Identifier windyorigins$bloodRow(Identifier var1) {
        MinecraftClient var2 = MinecraftClient.getInstance();
        return var1.getNamespace().equals("minecraft") && var1.getPath().startsWith("hud/food_") && var2.player != null && Powers.has(var2.player, "blood_thirst")
            ? new Identifier("windyorigins", var1.getPath())
            : var1;
    }
}
