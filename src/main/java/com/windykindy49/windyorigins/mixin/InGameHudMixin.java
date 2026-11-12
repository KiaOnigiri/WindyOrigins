package com.windykindy49.windyorigins.mixin;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import net.minecraft.client.MinecraftClient;
import com.windykindy49.windyorigins.client.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({InGameHud.class})
public abstract class InGameHudMixin {
    @Unique
    private static final Identifier MIRRORED_ORIGIN$IN_MIRROR_ID = new Identifier("windyorigins", "in_mirror_world");

    @Inject(
        method = {"render"},
        at = {@At("TAIL")}
    )
    private void onRender(net.minecraft.client.util.math.MatrixStack matrices, float var2, CallbackInfo var3) {
        DrawContext var1 = new DrawContext(matrices);
        ClientPlayerEntity var4 = MinecraftClient.getInstance().player;
        if (var4 != null) {
            if (PowerTypeRegistry.contains(MIRRORED_ORIGIN$IN_MIRROR_ID)) {
                PowerType var5 = PowerTypeRegistry.get(MIRRORED_ORIGIN$IN_MIRROR_ID);
                PowerHolderComponent var6 = PowerHolderComponent.KEY.get(var4);
                if (var6 != null && var6.hasPower(var5)) {
                    var1.fill(0, 0, var1.getScaledWindowWidth(), var1.getScaledWindowHeight(), 571539626);
                }
            }
        }
    }
}
