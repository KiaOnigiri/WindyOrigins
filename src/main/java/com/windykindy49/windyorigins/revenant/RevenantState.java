package com.windykindy49.windyorigins.revenant;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

public class RevenantState extends PersistentState {
    public final Map<UUID, RevenantState.Possession> possessions = new HashMap<>();
    public final Map<UUID, RevenantState.Ghost> ghosts = new HashMap<>();

    public static RevenantState get(MinecraftServer var0) {
        return var0.getOverworld().getPersistentStateManager().getOrCreate(new net.minecraft.world.PersistentState.Type<>(RevenantState::new, RevenantState::fromNbt, null), "windyorigins_revenants");
    }

    static RevenantState fromNbt(NbtCompound var0) {
        RevenantState var1 = new RevenantState();
        NbtList var2 = var0.getList("Possessions", 10);

        for (int var3 = 0; var3 < var2.size(); var3++) {
            NbtCompound var4 = var2.getCompound(var3);
            RevenantState.Possession var5 = new RevenantState.Possession();
            var5.host = var4.containsUuid("Host") ? var4.getUuid("Host") : null;
            var5.previous = GameMode.byId(var4.getInt("Previous"));
            var5.ticksLeft = var4.getInt("Ticks");
            var1.possessions.put(var4.getUuid("Soul"), var5);
        }

        var2 = var0.getList("Ghosts", 10);

        for (int var7 = 0; var7 < var2.size(); var7++) {
            NbtCompound var8 = var2.getCompound(var7);
            RevenantState.Ghost var9 = new RevenantState.Ghost();
            var9.world = RegistryKey.of(RegistryKeys.WORLD, new Identifier(var8.getString("World")));
            var9.deathPos = new Vec3d(var8.getDouble("X"), var8.getDouble("Y"), var8.getDouble("Z"));
            var9.killer = var8.containsUuid("Killer") ? var8.getUuid("Killer") : null;
            var9.ticksLeft = var8.getInt("Ticks");
            var1.ghosts.put(var8.getUuid("Soul"), var9);
        }

        return var1;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound var1) {
        NbtList var2 = new NbtList();

        for (Entry var4 : this.possessions.entrySet()) {
            NbtCompound var5 = new NbtCompound();
            var5.putUuid("Soul", (UUID)var4.getKey());
            if (((RevenantState.Possession)var4.getValue()).host != null) {
                var5.putUuid("Host", ((RevenantState.Possession)var4.getValue()).host);
            }

            var5.putInt("Previous", ((RevenantState.Possession)var4.getValue()).previous.getId());
            var5.putInt("Ticks", ((RevenantState.Possession)var4.getValue()).ticksLeft);
            var2.add(var5);
        }

        var1.put("Possessions", var2);
        var2 = new NbtList();

        for (Entry var8 : this.ghosts.entrySet()) {
            NbtCompound var9 = new NbtCompound();
            var9.putUuid("Soul", (UUID)var8.getKey());
            var9.putString("World", ((RevenantState.Ghost)var8.getValue()).world.getValue().toString());
            var9.putDouble("X", ((RevenantState.Ghost)var8.getValue()).deathPos.x);
            var9.putDouble("Y", ((RevenantState.Ghost)var8.getValue()).deathPos.y);
            var9.putDouble("Z", ((RevenantState.Ghost)var8.getValue()).deathPos.z);
            if (((RevenantState.Ghost)var8.getValue()).killer != null) {
                var9.putUuid("Killer", ((RevenantState.Ghost)var8.getValue()).killer);
            }

            var9.putInt("Ticks", ((RevenantState.Ghost)var8.getValue()).ticksLeft);
            var2.add(var9);
        }

        var1.put("Ghosts", var2);
        return var1;
    }

    public static final class Ghost {
        public RegistryKey<World> world;
        public Vec3d deathPos;
        public UUID killer;
        public int ticksLeft;
    }

    public static final class Possession {
        public UUID host;
        public GameMode previous;
        public int ticksLeft;
        public int spent;
        /** Which side of the mirror the host was on when it was taken; a host that crosses over shakes the Revenant off. */
        public boolean phase;
        public boolean phaseKnown;
    }
}
