package com.windykindy49.windyorigins.bounty;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

public class BountyMarkState extends PersistentState {
    private static final String KEY = "windyorigins_bounty_marks";
    private static final String NBT_ENTRIES = "Marks";
    private final Map<UUID, BountyMarkState.Entry> entries = new HashMap<>();

    public static BountyMarkState fromNbt(NbtCompound var0) {
        BountyMarkState var1 = new BountyMarkState();
        NbtList var2 = var0.getList("Marks", 10);

        for (int var3 = 0; var3 < var2.size(); var3++) {
            NbtCompound var4 = var2.getCompound(var3);
            if (var4.containsUuid("Hunter")) {
                BountyMarkState.Entry var5 = new BountyMarkState.Entry();
                if (var4.containsUuid("Target")) {
                    var5.target = var4.getUuid("Target");
                }

                var5.markTicks = var4.getInt("MarkTicks");
                var5.cooldownTicks = var4.getInt("CooldownTicks");
                var1.entries.put(var4.getUuid("Hunter"), var5);
            }
        }

        return var1;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound var1) {
        NbtList var2 = new NbtList();

        for (Map.Entry var4 : this.entries.entrySet()) {
            BountyMarkState.Entry var5 = (BountyMarkState.Entry)var4.getValue();
            if (!var5.isIdle()) {
                NbtCompound var6 = new NbtCompound();
                var6.putUuid("Hunter", (UUID)var4.getKey());
                if (var5.target != null) {
                    var6.putUuid("Target", var5.target);
                }

                var6.putInt("MarkTicks", var5.markTicks);
                var6.putInt("CooldownTicks", var5.cooldownTicks);
                var2.add(var6);
            }
        }

        var1.put("Marks", var2);
        return var1;
    }

    public static BountyMarkState get(ServerWorld var0) {
        ServerWorld var1 = var0.getServer().getOverworld();
        return var1.getPersistentStateManager().getOrCreate(BountyMarkState::fromNbt, BountyMarkState::new, "windyorigins_bounty_marks");
    }

    public BountyMarkState.Entry getOrCreate(UUID var1) {
        return this.entries.computeIfAbsent(var1, var0 -> new BountyMarkState.Entry());
    }

    public BountyMarkState.Entry peek(UUID var1) {
        return this.entries.get(var1);
    }

    public Map<UUID, BountyMarkState.Entry> all() {
        return this.entries;
    }

    public void forget(UUID var1) {
        if (this.entries.remove(var1) != null) {
            this.markDirty();
        }
    }

    public void touch() {
        this.markDirty();
    }

    public static class Entry {
        public UUID target;
        public int markTicks;
        public int cooldownTicks;
        public int lostTicks;
        public int phaseTicks;
        public double lastDistance = -1.0;

        public boolean isIdle() {
            return this.target == null && this.cooldownTicks <= 0;
        }
    }
}
