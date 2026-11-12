package com.windykindy49.windyorigins.host;

import com.windykindy49.windyorigins.common.PhaseFx;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.Text.Serializer;
import net.minecraft.world.PersistentState;

public class HostRenames extends PersistentState {
    private static final String KEY = "windyorigins_volta_renames";
    public static final int NAMES = 10;
    private static final int TICKS = 600;
    private final Map<UUID, HostRenames.Entry> entries = new HashMap<>();

    public static HostRenames get(MinecraftServer var0) {
        return var0.getOverworld().getPersistentStateManager().getOrCreate(HostRenames::fromNbt, HostRenames::new, "windyorigins_volta_renames");
    }

    public static HostRenames fromNbt(NbtCompound var0) {
        HostRenames var1 = new HostRenames();

        for (String var3 : var0.getKeys()) {
            NbtCompound var4 = var0.getCompound(var3);
            HostRenames.Entry var5 = new HostRenames.Entry();
            var5.original = var4.contains("Original") ? var4.getString("Original") : null;
            var5.visible = var4.getBoolean("Visible");
            var5.joke = var4.getString("Joke");
            var5.expires = var4.getLong("Expires");

            try {
                var1.entries.put(UUID.fromString(var3), var5);
            } catch (IllegalArgumentException var7) {
            }
        }

        return var1;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound var1) {
        for (Map.Entry var3 : this.entries.entrySet()) {
            NbtCompound var4 = new NbtCompound();
            HostRenames.Entry var5 = (HostRenames.Entry)var3.getValue();
            if (var5.original != null) {
                var4.putString("Original", var5.original);
            }

            var4.putBoolean("Visible", var5.visible);
            var4.putString("Joke", var5.joke);
            var4.putLong("Expires", var5.expires);
            var1.put(((UUID)var3.getKey()).toString(), var4);
        }

        return var1;
    }

    public static boolean rename(ServerPlayerEntity var0) {
        ServerWorld var1 = var0.getServerWorld();
        MobEntity var2 = null;
        double var3 = Double.MAX_VALUE;

        for (MobEntity var6 : var1.getEntitiesByClass(
            MobEntity.class, var0.getBoundingBox().expand(16.0), var1x -> var1x.isAlive() && PhaseFx.samePhase(var1x, var0)
        )) {
            boolean var10000;
            label50: {
                if (var6 instanceof TameableEntity var8 && var0.getUuid().equals(var8.getOwnerUuid())) {
                    var10000 = true;
                    break label50;
                }

                var10000 = false;
            }

            boolean var7 = var10000;
            if (var7 || !(var6.squaredDistanceTo(var0) > 100.0) && !(var6 instanceof EnderDragonEntity) && !(var6 instanceof WitherEntity)) {
                double var13 = var6.squaredDistanceTo(var0) - (var7 ? 10000.0 : 0.0);
                if (var13 < var3) {
                    var3 = var13;
                    var2 = var6;
                }
            }
        }

        if (var2 == null) {
            return false;
        } else {
            HostRenames var10 = get(var0.getServer());
            if (var10.entries.containsKey(var2.getUuid())) {
                return false;
            } else {
                HostRenames.Entry var11 = new HostRenames.Entry();
                Text var12 = var2.getCustomName();
                var11.original = var12 == null ? null : Serializer.toJson(var12);
                var11.visible = var2.isCustomNameVisible();
                MutableText var14 = Text.translatable("name.windyorigins.volta." + var0.getRandom().nextInt(10));
                var11.joke = Serializer.toJson(var14);
                var11.expires = var0.getServer().getOverworld().getTime() + 600L;
                var10.entries.put(var2.getUuid(), var11);
                var10.markDirty();
                var2.setCustomName(var14);
                var2.setCustomNameVisible(true);
                return true;
            }
        }
    }

    public static void tick(MinecraftServer var0) {
        HostRenames var1 = get(var0);
        if (!var1.entries.isEmpty()) {
            long var2 = var0.getOverworld().getTime();
            Iterator var4 = var1.entries.entrySet().iterator();

            while (var4.hasNext()) {
                Map.Entry var5 = (Map.Entry)var4.next();
                if (((HostRenames.Entry)var5.getValue()).expires <= var2) {
                    Entity var6 = find(var0, (UUID)var5.getKey());
                    if (var6 != null) {
                        restore(var6, (HostRenames.Entry)var5.getValue());
                        var4.remove();
                        var1.markDirty();
                    }
                }
            }
        }
    }

    public static void onLoad(Entity var0, ServerWorld var1) {
        if (var0 instanceof LivingEntity) {
            HostRenames var2 = get(var1.getServer());
            HostRenames.Entry var3 = var2.entries.get(var0.getUuid());
            if (var3 != null && var3.expires <= var1.getServer().getOverworld().getTime()) {
                restore(var0, var3);
                var2.entries.remove(var0.getUuid());
                var2.markDirty();
            }
        }
    }

    private static void restore(Entity var0, HostRenames.Entry var1) {
        Text var2 = var0.getCustomName();
        if (var2 != null && var1.joke.equals(Serializer.toJson(var2))) {
            var0.setCustomName(var1.original == null ? null : Serializer.fromJson(var1.original));
            var0.setCustomNameVisible(var1.visible);
        }
    }

    private static Entity find(MinecraftServer var0, UUID var1) {
        for (ServerWorld var3 : var0.getWorlds()) {
            Entity var4 = var3.getEntity(var1);
            if (var4 != null) {
                return var4;
            }
        }

        return null;
    }

    private static final class Entry {
        String original;
        boolean visible;
        String joke;
        long expires;
    }
}
