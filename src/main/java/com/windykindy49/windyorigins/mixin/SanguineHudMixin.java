package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.common.Powers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The hunger row of a Sanguine is drawn from the blood sheet. The swap is made where the icon is drawn rather
 * than in the HUD class, because Forge draws the food bar in a class of its own.
 */
@Mixin({DrawContext.class})
public abstract class SanguineHudMixin {
    private static final Identifier ICONS = new Identifier("textures/gui/icons.png");
    private static final Identifier BLOOD = new Identifier("windyorigins", "textures/gui/blood.png");

    @ModifyVariable(
        method = {"drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V"},
        at = @At("HEAD"),
        argsOnly = true
    )
    private Identifier windyorigins$bloodRow(Identifier var1, Identifier var2, int var3, int var4, int var5, int var6, int var7, int var8) {
        if (var6 == 27 && var7 == 9 && var8 == 9 && ICONS.equals(var1)) {
            MinecraftClient var9 = MinecraftClient.getInstance();
            if (var9.player != null && Powers.has(var9.player, "blood_thirst")) {
                return BLOOD;
            }
        }

        return var1;
    }
}
