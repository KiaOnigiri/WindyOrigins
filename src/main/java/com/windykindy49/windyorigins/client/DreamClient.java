package com.windykindy49.windyorigins.client;

import com.windykindy49.windyorigins.common.Powers;
import com.windykindy49.windyorigins.common.WindyEffects;
import com.windykindy49.windyorigins.dream.DreamEntities;
import com.windykindy49.windyorigins.dream.DreamManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import com.windykindy49.windyorigins.client.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.BatEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import org.joml.Vector3f;

public final class DreamClient {
    private static final DustParticleEffect HAZE = new DustParticleEffect(new Vector3f(0.75F, 0.65F, 1.0F), 0.8F);
    private static int fade;
    private static boolean fadeIn;

    private DreamClient() {
    }

    public static void init() {
        EntityModelLayerRegistry.registerModelLayer(NightmareRenderer.LAYER, NightmareModel::getTexturedModelData);
        EntityRendererRegistry.register(DreamEntities.NIGHTMARE, NightmareRenderer::new);
        EntityRendererRegistry.register(DreamEntities.BODY, DreamBodyRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(DreamManager.STATE, (var0, var1, var2, var3) -> {
            boolean var4 = var2.readBoolean();
            var0.execute(() -> {
                fade = 40;
                fadeIn = var4;
            });
        });
        ClientTickEvents.END_CLIENT_TICK.register(DreamClient::tick);
        HudRenderCallback.EVENT.register((matrices, delta) -> DreamClient.render(new com.windykindy49.windyorigins.client.DrawContext(matrices), delta));
    }

    private static int nextBeat;

    /**
     * Under Nightmare the victim hears their own pulse: not a sound in the world but one in the ears, so it has
     * no place and nobody else hears it. The lower the health, the faster it goes.
     */
    private static void pulse(MinecraftClient client) {
        if (!client.player.hasStatusEffect(WindyEffects.NIGHTMARE)) {
            nextBeat = 0;
            return;
        }

        if (--nextBeat > 0) {
            return;
        }

        float health = MathHelper.clamp(client.player.getHealth() / Math.max(1.0F, client.player.getMaxHealth()), 0.0F, 1.0F);
        nextBeat = 11 + Math.round(8.0F * health);
        float volume = 0.55F + 0.45F * (1.0F - health);
        client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(com.windykindy49.windyorigins.common.WindySounds.DREAM_PULSE, 0.85F + 0.25F * (1.0F - health), volume));
    }

    private static void tick(MinecraftClient var0) {
        if (fade > 0) {
            fade--;
        }

        if (var0.player != null && var0.world != null && !var0.isPaused()) {
            pulse(var0);
            if (var0.player.age % 10 == 0 && Powers.has(var0.player, "dream_sense")) {
                for (Entity var2 : var0.world.getOtherEntities(var0.player, var0.player.getBoundingBox().expand(24.0))) {
                    boolean var10000;
                    label52: {
                        if (var2 instanceof LivingEntity var5 && var5.isSleeping() || var2 instanceof BatEntity var4 && var4.isRoosting()) {
                            var10000 = true;
                            break label52;
                        }

                        var10000 = false;
                    }

                    boolean var3 = var10000;
                    if (var3) {
                        var0.world
                            .addParticle(
                                HAZE,
                                var2.getX() + (var0.world.random.nextDouble() - 0.5) * 0.6,
                                var2.getY() + (double)var2.getHeight() + 0.3,
                                var2.getZ() + (var0.world.random.nextDouble() - 0.5) * 0.6,
                                0.0,
                                0.02,
                                0.0
                            );
                    }
                }
            }
        }
    }

    private static void render(DrawContext var0, float var1) {
        MinecraftClient var2 = MinecraftClient.getInstance();
        if (var2.player != null) {
            int var3 = var0.getScaledWindowWidth();
            int var4 = var0.getScaledWindowHeight();
            if (var2.world != null && var2.world.getRegistryKey() == DreamManager.DREAMS) {
                int var5 = Math.max(16, Math.min(var3, var4) / 6);
                float var6 = 0.5F + 0.5F * MathHelper.sin(((float)var2.player.age + var1) * 0.05F);
                int var7 = (int)(64.0F + 32.0F * var6) << 24;
                int var8 = var7 | 6963152;
                var0.fillGradient(0, 0, var3, var5, var8, 6963152);
                var0.fillGradient(0, var4 - var5, var3, var4, 6963152, var8);
            }

            if (fade > 0) {
                float var9 = (float)fade / 40.0F;
                int var11 = (int)(255.0F * (fadeIn ? var9 : var9 * 0.8F));
                var0.fill(0, 0, var3, var4, MathHelper.clamp(var11, 0, 255) << 24 | (fadeIn ? 656916 : 16777215));
            }

            if (var2.player.hasStatusEffect(WindyEffects.NIGHTMARE)) {
                Random var10 = Random.create((long)(var2.player.age / 3));
                if (var10.nextInt(20) == 0) {
                    var0.fill(0, 0, var3, var4, 1342177280);
                    int var12 = var10.nextInt(var4);
                    var0.fill(0, var12, var3, var12 + 2, -2144333760);
                }
            }
        }
    }
}
