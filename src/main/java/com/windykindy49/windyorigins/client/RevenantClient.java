package com.windykindy49.windyorigins.client;

import com.windykindy49.windyorigins.revenant.RevenantNetworking;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

public final class RevenantClient {
    private static final Map<Integer, Vec3d> ECHOES = new HashMap<>();
    private static final DustParticleEffect GLOW = new DustParticleEffect(new Vector3f(0.45F, 0.95F, 1.0F), 0.6F);
    private static boolean haunted;

    private RevenantClient() {
    }

    public static boolean isHaunted() {
        return haunted;
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(RevenantNetworking.ECHO_ADD, (var0, var1, var2, var3) -> {
            int var4 = var2.readVarInt();
            Vec3d var5 = new Vec3d(var2.readDouble(), var2.readDouble(), var2.readDouble());
            var0.execute(() -> ECHOES.put(var4, var5));
        });
        ClientPlayNetworking.registerGlobalReceiver(RevenantNetworking.ECHO_REMOVE, (var0, var1, var2, var3) -> {
            int var4 = var2.readVarInt();
            var0.execute(() -> ECHOES.remove(var4));
        });
        ClientPlayNetworking.registerGlobalReceiver(RevenantNetworking.HAUNTED, (var0, var1, var2, var3) -> {
            boolean var4 = var2.readBoolean();
            var0.execute(() -> haunted = var4);
        });
        ClientPlayNetworking.registerGlobalReceiver(RevenantNetworking.MARKS, (var0, var1, var2, var3) -> {
            int[] var4 = var2.readIntArray();
            var0.execute(() -> {
                ClientOutlines.SERVER_MARKED.clear();

                for (int var4x : var4) {
                    ClientOutlines.SERVER_MARKED.add(var4x);
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(RevenantNetworking.EJECT, (var0, var1, var2, var3) -> {
            float var4 = var2.readFloat();
            float var5 = var2.readFloat();
            var0.execute(() -> eject(var0, var4, var5));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((var0, var1) -> {
            ECHOES.clear();
            ClientOutlines.SERVER_MARKED.clear();
            haunted = false;
        });
        ClientTickEvents.END_CLIENT_TICK.register(RevenantClient::tick);
        ClientTickEvents.END_CLIENT_TICK.register(RevenantClient::tickGhostAndHost);
    }

    /** Flung out of a host: along the held movement keys, or along the view when none are held. */
    private static void eject(MinecraftClient var0, float var1, float var2) {
        if (var0.player == null) {
            return;
        }

        float var3 = (var0.options.forwardKey.isPressed() ? 1.0F : 0.0F) - (var0.options.backKey.isPressed() ? 1.0F : 0.0F);
        float var4 = (var0.options.leftKey.isPressed() ? 1.0F : 0.0F) - (var0.options.rightKey.isPressed() ? 1.0F : 0.0F);
        double var5 = Math.toRadians((double)var1);
        double var7;
        double var9;
        double var11;
        if (var3 != 0.0F || var4 != 0.0F) {
            double var13 = Math.sqrt((double)(var3 * var3 + var4 * var4));
            var7 = ((double)var4 * Math.cos(var5) - (double)var3 * Math.sin(var5)) / var13 * 1.15;
            var11 = ((double)var3 * Math.cos(var5) + (double)var4 * Math.sin(var5)) / var13 * 1.15;
            var9 = 0.55;
        } else {
            Vec3d var15 = Vec3d.fromPolar(var2, var1);
            var7 = var15.x * 1.15;
            var11 = var15.z * 1.15;
            var9 = Math.max(0.0, var15.y * 1.15) + 0.45;
        }

        var0.player.setVelocity(var7, var9, var11);
        var0.player.fallDistance = 0.0F;
    }

    private static boolean releaseHeld;

    private static void tickGhostAndHost(MinecraftClient var0) {
        if (var0.player == null) {
            releaseHeld = false;
            return;
        }

        // Vengeance is creative flight that cannot be dropped out of.
        if (var0.player.hasStatusEffect(com.windykindy49.windyorigins.common.WindyEffects.VENGEANCE) && !var0.player.getAbilities().flying) {
            var0.player.getAbilities().flying = true;
        }

        // Sneak or jump ends a possession, read straight from the keys so it works whatever
        // state the spectating client is in.
        boolean var1 = var0.player.hasStatusEffect(com.windykindy49.windyorigins.common.WindyEffects.POSSESSED)
            && var0.currentScreen == null
            && (var0.options.sneakKey.isPressed() || var0.options.jumpKey.isPressed());
        if (var1 && !releaseHeld) {
            ClientPlayNetworking.send(RevenantNetworking.RELEASE, new net.minecraft.network.PacketByteBuf(io.netty.buffer.Unpooled.buffer()));
        }

        releaseHeld = var1;
    }

    private static void tick(MinecraftClient var0) {
        if (var0.world != null && var0.player != null && !var0.isPaused() && !ECHOES.isEmpty()) {
            int var1 = var0.player.age;

            for (Vec3d var3 : ECHOES.values()) {
                double var4 = (double)var1 * 0.25;
                var0.world
                    .addParticle(GLOW, var3.x + Math.cos(var4) * 0.25, var3.y + Math.sin(var4 * 0.5) * 0.1, var3.z + Math.sin(var4) * 0.25, 0.0, 0.0, 0.0);
                if (var1 % 4 == 0) {
                    var0.world.addParticle(ParticleTypes.SOUL, var3.x, var3.y - 0.2, var3.z, 0.0, 0.03, 0.0);
                }
            }
        }
    }
}
