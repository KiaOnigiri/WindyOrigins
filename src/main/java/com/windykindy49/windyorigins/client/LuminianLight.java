package com.windykindy49.windyorigins.client;

import com.windykindy49.windyorigins.common.PhaseFx;
import com.windykindy49.windyorigins.common.Powers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class LuminianLight {
    private static volatile List<LuminianLight.Source> sources = List.of();
    private static final Map<Integer, LuminianLight.Source> REBUILT = new HashMap<>();
    private static ClientWorld lastWorld;
    private static final int REACH = 15;

    private LuminianLight() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(LuminianLight::tick);
    }

    private static void tick(MinecraftClient var0) {
        ClientWorld var1 = var0.world;
        if (var1 != lastWorld) {
            lastWorld = var1;
            REBUILT.clear();
            sources = List.of();
        }

        if (var1 != null && var0.player != null && var0.worldRenderer != null) {
            ArrayList var2 = new ArrayList();
            HashMap var3 = new HashMap();

            for (AbstractClientPlayerEntity var5 : var1.getPlayers()) {
                if (!var5.isSpectator() && Powers.has(var5, "inner_light") && PhaseFx.samePhase(var5, var0.player)) {
                    int var6 = Powers.resource(var5, "inner_light", 100);
                    if (var6 > 0) {
                        int var7 = MathHelper.clamp(6 + var6 * 9 / 100, 6, 15);
                        Vec3d var8 = var5.getEyePos();
                        LuminianLight.Source var9 = new LuminianLight.Source(var8.x, var8.y - 0.3, var8.z, var7);
                        var2.add(var9);
                        var3.put(var5.getId(), var9);
                    }
                }
            }

            sources = List.copyOf(var2);

            for (Entry var11 : (Iterable<Entry>)(Object)(var3.entrySet())) {
                LuminianLight.Source var12 = (LuminianLight.Source)var11.getValue();
                LuminianLight.Source var13 = REBUILT.get(var11.getKey());
                if (var13 == null
                    || var13.level != var12.level
                    || (var12.x - var13.x) * (var12.x - var13.x) + (var12.y - var13.y) * (var12.y - var13.y) + (var12.z - var13.z) * (var12.z - var13.z) > 0.36
                    )
                 {
                    rebuild(var0, var12);
                    if (var13 != null) {
                        rebuild(var0, var13);
                    }

                    REBUILT.put((Integer)var11.getKey(), var12);
                }
            }

            REBUILT.entrySet().removeIf(var2x -> {
                if (!var3.containsKey(var2x.getKey())) {
                    rebuild(var0, var2x.getValue());
                    return true;
                } else {
                    return false;
                }
            });
        }
    }

    private static void rebuild(MinecraftClient var0, LuminianLight.Source var1) {
        int var2 = MathHelper.floor(var1.x);
        int var3 = MathHelper.floor(var1.y);
        int var4 = MathHelper.floor(var1.z);
        var0.worldRenderer.scheduleBlockRenders(var2 - 15, var3 - 15, var4 - 15, var2 + 15, var3 + 15, var4 + 15);
    }

    public static int lightAt(BlockPos var0) {
        List var1 = sources;
        if (var1.isEmpty()) {
            return 0;
        } else {
            double var2 = (double)var0.getX() + 0.5;
            double var4 = (double)var0.getY() + 0.5;
            double var6 = (double)var0.getZ() + 0.5;
            int var8 = 0;

            for (LuminianLight.Source var10 : (Iterable<LuminianLight.Source>)(Object)(var1)) {
                double var11 = var2 - var10.x;
                double var13 = var4 - var10.y;
                double var15 = var6 - var10.z;
                double var17 = var11 * var11 + var13 * var13 + var15 * var15;
                if (!(var17 >= (double)(var10.level * var10.level))) {
                    int var19 = (int)((double)var10.level - Math.sqrt(var17));
                    if (var19 > var8) {
                        var8 = var19;
                    }
                }
            }

            return var8;
        }
    }

    private static record Source(double x, double y, double z, int level) {
    }
}
