package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;
import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={InGameHud.class})
public abstract class InGameHudMixin {
    @Unique
    private static final Identifier MIRRORED_ORIGIN$IN_MIRROR_ID =
        new Identifier("mirroredorigin", "in_mirror_world");

    @Inject(method={"render"}, at={@At(value="TAIL")})
    private void onRender(DrawContext context, float tickDelta, CallbackInfo ci) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return;
        }
        // PowerTypeRegistry.get() THROWS when the id is absent, so it must never be
        // called unguarded. The registry is empty until the server finishes syncing
        // powers on join, and stays empty if the mod's datapack failed to load -- the
        // original code crashed the client in both cases.
        if (!PowerTypeRegistry.contains(MIRRORED_ORIGIN$IN_MIRROR_ID)) {
            return;
        }
        // Deliberately not cached in a static field: PowerTypeRegistry is cleared and
        // rebuilt on every datapack reload / server join, so a cached PowerType from a
        // previous session goes stale and hasPower() silently returns false forever.
        PowerType<?> inMirrorPower = PowerTypeRegistry.get(MIRRORED_ORIGIN$IN_MIRROR_ID);
        PowerHolderComponent powers = PowerHolderComponent.KEY.get(player);
        if (powers != null && powers.hasPower(inMirrorPower)) {
            context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), 571539626);
        }
    }
}
