package com.windykindy49.windyorigins.mirror;

import com.windykindy49.windyorigins.common.PhaseFx;
import com.windykindy49.windyorigins.common.Powers;
import com.windykindy49.windyorigins.common.WindyEffects;
import com.windykindy49.windyorigins.util.MirrorEntity;
import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

/**
 * Mirrored origin: banishment bookkeeping (banished players return to their own phase after
 * five minutes, or at once when they charge a respawn anchor) and the pacifist rules.
 */
public final class MirrorManager {
    public static final int BANISH_TICKS = 6000;
    public static final int GUILT_TICKS = 600;
    public static final float REFLECT = 0.3F;
    public static final float GUILT_DAMAGE = 1.3F;
    public static final int TOXICITY_PER_HIT = 40;
    private static final Identifier IN_MIRROR = Powers.id("in_mirror_world");
    private static final Identifier COMMAND_SOURCE = new Identifier("apoli", "command");
    private static final ThreadLocal<Boolean> REFLECTING = ThreadLocal.withInitial(() -> false);

    private MirrorManager() {
    }

    public static boolean inMirror(Entity entity) {
        return ((MirrorEntity)entity).windyorigins$isInMirrorWorld();
    }

    /** Moves a player between phases regardless of which source put them there. */
    public static void setPlayerPhase(ServerPlayerEntity player, boolean mirror) {
        if (!PowerTypeRegistry.contains(IN_MIRROR)) {
            return;
        }

        PowerType<?> type = PowerTypeRegistry.get(IN_MIRROR);
        PowerHolderComponent component = PowerHolderComponent.KEY.get(player);
        boolean changed = false;
        if (mirror) {
            if (!component.hasPower(type)) {
                changed = component.addPower(type, COMMAND_SOURCE);
            }
        } else {
            for (Identifier source : new ArrayList<>(component.getSources(type))) {
                component.removePower(type, source);
                changed = true;
            }
        }

        if (changed) {
            component.sync();
        }
    }

    // ------------------------------------------------------------------ banishment

    public static void banish(Entity actor, Entity target) {
        if (target.getWorld().isClient()) {
            return;
        }

        if (target instanceof ServerPlayerEntity player) {
            boolean current = inMirror(player);
            Banished state = Banished.get(player.server);
            Entry old = state.entries.remove(player.getUuid());
            if (old != null) {
                // Punched a second time: the banishment is simply undone.
                setPlayerPhase(player, old.homeMirror);
                player.sendMessage(Text.translatable("message.windyorigins.mirror.returned").formatted(Formatting.AQUA), true);
            } else {
                setPlayerPhase(player, !current);
                // The Mirrored move between phases on their own; they never need to be brought back.
                if (!Powers.has(player, "phase_shift")) {
                    state.entries.put(player.getUuid(), new Entry(current, player.server.getOverworld().getTime() + BANISH_TICKS));
                    player.sendMessage(Text.translatable("message.windyorigins.mirror.banished", 5).formatted(Formatting.LIGHT_PURPLE), false);
                }
            }

            state.markDirty();
        } else if (target instanceof MirrorEntity mirror) {
            mirror.windyorigins$setInMirrorWorld(!mirror.windyorigins$isInMirrorWorld());
        }
    }

    public static boolean isBanished(PlayerEntity player) {
        return player instanceof ServerPlayerEntity sp && Banished.get(sp.server).entries.containsKey(sp.getUuid());
    }

    private static void bringBack(ServerPlayerEntity player, Entry entry, boolean anchor) {
        setPlayerPhase(player, entry.homeMirror);
        player.sendMessage(
            Text.translatable(anchor ? "message.windyorigins.mirror.anchor" : "message.windyorigins.mirror.returned").formatted(Formatting.AQUA), true
        );
        PhaseFx.particles(player, ParticleTypes.REVERSE_PORTAL, player.getX(), player.getBodyY(0.5), player.getZ(), 60, 0.5, 0.8, 0.5, 0.1);
        PhaseFx.sound(player, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.8F, 0.7F);
    }

    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 0) {
            return;
        }

        Banished state = Banished.get(server);

        // Nobody but the Mirrored and the banished belongs in the Mirror phase. Someone who gave up
        // the origin while standing in it would otherwise be left there with no way back.
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (inMirror(player) && !Powers.has(player, "phase_shift") && !state.entries.containsKey(player.getUuid()) && !player.isSpectator()) {
                setPlayerPhase(player, false);
                player.sendMessage(Text.translatable("message.windyorigins.mirror.returned").formatted(Formatting.AQUA), true);
            }
        }

        if (state.entries.isEmpty()) {
            return;
        }

        long now = server.getOverworld().getTime();
        Iterator<Map.Entry<UUID, Entry>> it = state.entries.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<UUID, Entry> e = it.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(e.getKey());
            if (player == null) {
                continue;
            }

            Entry entry = e.getValue();
            long left = entry.until - now;
            if (inMirror(player) == entry.homeMirror) {
                it.remove();
                state.markDirty();
            } else if (left <= 0L) {
                bringBack(player, entry, false);
                it.remove();
                state.markDirty();
            } else {
                long seconds = (left + 19L) / 20L;
                player.sendMessage(
                    Text.translatable("message.windyorigins.mirror.countdown", String.format("%d:%02d", seconds / 60L, seconds % 60L))
                        .formatted(Formatting.LIGHT_PURPLE),
                    true
                );
            }
        }
    }

    /** Charging a respawn anchor tears a banished player straight back into their own phase. */
    public static void onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
        if (world.isClient() || !(player instanceof ServerPlayerEntity sp)) {
            return;
        }

        Banished state = Banished.get(sp.server);
        Entry entry = state.entries.get(sp.getUuid());
        if (entry == null) {
            return;
        }

        BlockState block = world.getBlockState(hit.getBlockPos());
        if (block.isOf(Blocks.RESPAWN_ANCHOR) && block.get(RespawnAnchorBlock.CHARGES) < 4 && player.getStackInHand(hand).isOf(Items.GLOWSTONE)) {
            state.entries.remove(sp.getUuid());
            state.markDirty();
            bringBack(sp, entry, true);
        }
    }

    // ------------------------------------------------------------------ pacifism

    /** Hostile creatures leave a Mirrored alone until it draws blood. */
    public static boolean ignoredBy(LivingEntity mob, LivingEntity target) {
        if (!(target instanceof PlayerEntity) || target.hasStatusEffect(WindyEffects.GUILT)) {
            return false;
        }

        if (mob instanceof WitherEntity || mob instanceof EnderDragonEntity || mob instanceof WardenEntity) {
            return false;
        }

        return Powers.has(target, "harmless_reflection");
    }

    public static float scaleIncoming(LivingEntity victim, float amount) {
        // The reflected blow is a flat share of what was dealt; Guilt does not feed on itself.
        return !REFLECTING.get() && victim.hasStatusEffect(WindyEffects.GUILT) ? amount * GUILT_DAMAGE : amount;
    }

    public static void afterDamage(LivingEntity victim, DamageSource source, float amount) {
        if (REFLECTING.get() || !(source.getAttacker() instanceof ServerPlayerEntity attacker) || attacker == victim) {
            return;
        }

        if (victim instanceof ArmorStandEntity || !Powers.has(attacker, "reflected_pain")) {
            return;
        }

        // The banishing punch is the one blow a reflection may land freely.
        if (Powers.resource(attacker, "banishment_punch_charge_timer", 0) > 0 || Powers.resource(attacker, "banishment_punch_cooldown_timer", 0) >= 11990) {
            return;
        }

        attacker.addStatusEffect(new StatusEffectInstance(WindyEffects.GUILT, GUILT_TICKS, 0, false, false, true));
        if (!inMirror(attacker)) {
            Powers.addResource(attacker, "reality_toxicity_timer", TOXICITY_PER_HIT);
        }

        if (!attacker.isCreative() && amount > 0.0F) {
            REFLECTING.set(true);

            try {
                attacker.damage(attacker.getDamageSources().magic(), Math.max(1.0F, amount * REFLECT));
            } finally {
                REFLECTING.set(false);
            }

            PhaseFx.sound(attacker, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.6F, 1.6F);
            PhaseFx.particles(attacker, ParticleTypes.ENCHANTED_HIT, attacker.getX(), attacker.getBodyY(0.6), attacker.getZ(), 8, 0.3, 0.4, 0.3, 0.1);
        }

        if (victim instanceof MobEntity mob && mob.getTarget() == null && mob.isAlive()) {
            mob.setTarget(attacker);
        }
    }

    // ------------------------------------------------------------------ persistence

    private record Entry(boolean homeMirror, long until) {
    }

    private static final class Banished extends PersistentState {
        final Map<UUID, Entry> entries = new HashMap<>();

        static Banished get(MinecraftServer server) {
            return server.getOverworld().getPersistentStateManager().getOrCreate(new net.minecraft.world.PersistentState.Type<>(Banished::new, Banished::read, null), "windyorigins_banished");
        }

        static Banished read(NbtCompound nbt) {
            Banished state = new Banished();
            NbtList list = nbt.getList("entries", NbtElement.COMPOUND_TYPE);

            for (int i = 0; i < list.size(); i++) {
                NbtCompound tag = list.getCompound(i);
                state.entries.put(tag.getUuid("player"), new Entry(tag.getBoolean("home"), tag.getLong("until")));
            }

            return state;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt) {
            NbtList list = new NbtList();

            for (Map.Entry<UUID, Entry> e : this.entries.entrySet()) {
                NbtCompound tag = new NbtCompound();
                tag.putUuid("player", e.getKey());
                tag.putBoolean("home", e.getValue().homeMirror);
                tag.putLong("until", e.getValue().until);
                list.add(tag);
            }

            nbt.put("entries", list);
            return nbt;
        }
    }
}
