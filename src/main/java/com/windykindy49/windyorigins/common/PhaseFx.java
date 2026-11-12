package com.windykindy49.windyorigins.common;

import com.windykindy49.windyorigins.util.MirrorEntity;
import com.windykindy49.windyorigins.util.SoundState;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

public final class PhaseFx {
    private static final double PARTICLE_RANGE = 64.0;
    private static final double SOUND_RANGE = 32.0;

    private PhaseFx() {
    }

    public static boolean phase(Entity var0) {
        return ((MirrorEntity)var0).windyorigins$isInMirrorWorld();
    }

    public static boolean samePhase(Entity var0, Entity var1) {
        return phase(var0) == phase(var1);
    }

    public static <T extends ParticleEffect> void particles(
        Entity var0, T var1, double var2, double var4, double var6, int var8, double var9, double var11, double var13, double var15
    ) {
        if (var0.getWorld() instanceof ServerWorld var17) {
            boolean var24 = phase(var0);
            SoundState.markCrossPhase();

            try {
                for (ServerPlayerEntity var20 : var17.getPlayers()) {
                    if (phase(var20) == var24 && var20.squaredDistanceTo(var2, var4, var6) < 4096.0) {
                        var17.spawnParticles(var20, var1, true, var2, var4, var6, var8, var9, var11, var13, var15);
                    }
                }
            } finally {
                SoundState.clearCrossPhase();
            }
        }
    }

    /** Particles that only one player is shown. */
    public static <T extends ParticleEffect> void only(
        ServerPlayerEntity var0, T var1, double var2, double var4, double var6, int var8, double var9, double var11, double var13, double var15
    ) {
        SoundState.markCrossPhase();

        try {
            var0.getServerWorld().spawnParticles(var0, var1, true, var2, var4, var6, var8, var9, var11, var13, var15);
        } finally {
            SoundState.clearCrossPhase();
        }
    }

    public static void sound(Entity var0, SoundEvent var1, SoundCategory var2, float var3, float var4) {
        sound(var0, var0.getX(), var0.getY(), var0.getZ(), var1, var2, var3, var4);
    }

    public static void sound(Entity var0, double var1, double var3, double var5, SoundEvent var7, SoundCategory var8, float var9, float var10) {
        if (var0.getWorld() instanceof ServerWorld var11) {
            boolean var22 = phase(var0);
            double var13 = Math.max(32.0, (double)var9 * 16.0);
            long var15 = var11.getRandom().nextLong();
            SoundState.markCrossPhase();

            try {
                for (ServerPlayerEntity var18 : var11.getPlayers()) {
                    if (phase(var18) == var22 && var18.squaredDistanceTo(var1, var3, var5) < var13 * var13) {
                        var18.networkHandler
                            .sendPacket(new PlaySoundS2CPacket(Registries.SOUND_EVENT.getEntry(var7), var8, var1, var3, var5, var9, var10, var15));
                    }
                }
            } finally {
                SoundState.clearCrossPhase();
            }
        }
    }

    public static void privateSound(ServerPlayerEntity var0, SoundEvent var1, float var2, float var3) {
        SoundState.markCrossPhase();

        try {
            var0.playSound(var1, SoundCategory.PLAYERS, var2, var3);
        } finally {
            SoundState.clearCrossPhase();
        }
    }
}
