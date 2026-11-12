package com.windykindy49.windyorigins.sanguine;

import com.windykindy49.windyorigins.common.Flight;
import com.windykindy49.windyorigins.common.MobGroups;
import com.windykindy49.windyorigins.common.PhaseFx;
import com.windykindy49.windyorigins.common.Powers;
import com.windykindy49.windyorigins.common.WindyEffects;
import com.windykindy49.windyorigins.common.WindySounds;
import com.windykindy49.windyorigins.util.SpawnPhaseOverride;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.BatEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.potion.PotionUtil;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import net.minecraft.world.PersistentState;
import org.joml.Vector3f;

public final class SanguineManager {
    public static final TagKey<Item> ASPEN = TagKey.of(RegistryKeys.ITEM, new Identifier("windyorigins", "aspen"));
    public static final int TASTE_TICKS = 2400;
    public static final int BAT_TICKS = 120;
    public static final int BAT_COST = 4;
    public static final int TASTE_PLAIN = 0;
    public static final int TASTE_ARTHROPOD = 1;
    public static final int TASTE_AQUATIC = 2;
    public static final int TASTE_INFERNAL = 3;
    public static final int TASTE_AERIAL = 4;
    public static final int TASTE_PEOPLE = 5;
    private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.6F, 0.02F, 0.05F), 1.0F);
    private static final Map<UUID, Long> BITE_COOLDOWN = new HashMap<>();
    private static final Map<UUID, Integer> LAST_MEAL = new HashMap<>();
    private static final Map<UUID, Integer> SUN_TICKS = new HashMap<>();
    private static final Map<UUID, Long> LAST_REST = new HashMap<>();
    private static final List<BatEntity> CLOUD = new ArrayList<>();

    private SanguineManager() {
    }

    public static boolean isSanguine(Entity var0) {
        return Powers.has(var0, "blood_thirst");
    }

    public static int food(PlayerEntity var0) {
        return var0.getHungerManager().getFoodLevel();
    }

    public static boolean isBeast(PlayerEntity var0) {
        return isSanguine(var0) && food(var0) <= 2;
    }

    public static boolean onSwing(ServerPlayerEntity var0, Entity var1) {
        if (isSanguine(var0) && var1 instanceof LivingEntity var2 && var2.isAlive() && PhaseFx.samePhase(var0, var1)) {
            boolean var3 = var0.isSneaking() && var0.getMainHandStack().isEmpty() && Powers.has(var0, "bite");
            boolean var4 = isBeast(var0);
            if (!var3 && !var4) {
                return false;
            }

            long var5 = var0.getWorld().getTime();
            Long var7 = BITE_COOLDOWN.get(var0.getUuid());
            if (var7 != null && var7 > var5) {
                return var3;
            }

            MobGroups.Group var8 = MobGroups.of(var2);
            if (var8 == MobGroups.Group.BOSS) {
                PhaseFx.privateSound(var0, WindySounds.DENY, 0.6F, 0.8F);
                var0.sendMessage(Text.translatable("message.windyorigins.sanguine.no_boss").formatted(Formatting.DARK_RED), true);
                return var3;
            }

            BITE_COOLDOWN.put(var0.getUuid(), var5 + 20L);
            drink(var0, var2, var8);
            if (var3) {
                var2.damage(var0.getWorld().getDamageSources().playerAttack(var0), 2.0F);
            }

            return var3;
        }

        return false;
    }

    public static final int TASTE_WARLIKE = 5;
    public static final int TASTE_PEACEFUL = 6;
    public static final int TASTE_ENDER = 7;
    public static final int TASTE_PREDATOR = 8;
    public static final int TASTE_HERD = 9;
    public static final int TASTE_LEAPER = 10;
    public static final int TASTE_CREEPER = 11;
    public static final int TASTE_SLIME = 12;
    private static final int NO_BLOOD = -1;
    private static final int DEAD_BLOOD = -2;

    /** What a creature's blood tastes of. Specific kinds are sorted out first, broad groups last. */
    public static int tasteOf(LivingEntity var0, MobGroups.Group var1) {
        if (var0 instanceof PlayerEntity var2) {
            StatusEffectInstance var3 = var2.getStatusEffect(WindyEffects.TASTE);
            return var3 != null ? var3.getAmplifier() : LAST_MEAL.getOrDefault(var2.getUuid(), 0);
        }

        if (var0 instanceof net.minecraft.entity.passive.IronGolemEntity || var0 instanceof net.minecraft.entity.passive.SnowGolemEntity) {
            return NO_BLOOD;
        }

        if (var0.getGroup() == net.minecraft.entity.EntityGroup.UNDEAD || var1 == MobGroups.Group.UNDEAD) {
            return DEAD_BLOOD;
        }

        if (var0 instanceof net.minecraft.entity.mob.CreeperEntity) {
            return TASTE_CREEPER;
        }

        if (var0 instanceof net.minecraft.entity.mob.MagmaCubeEntity) {
            return TASTE_INFERNAL;
        }

        if (var0 instanceof net.minecraft.entity.mob.SlimeEntity) {
            return TASTE_SLIME;
        }

        if (var0 instanceof net.minecraft.entity.mob.EndermanEntity
            || var0 instanceof net.minecraft.entity.mob.EndermiteEntity
            || var0 instanceof net.minecraft.entity.mob.ShulkerEntity) {
            return TASTE_ENDER;
        }

        if (var0 instanceof net.minecraft.entity.mob.AbstractPiglinEntity || var0 instanceof net.minecraft.entity.mob.WitchEntity) {
            return TASTE_WARLIKE;
        }

        if (var0 instanceof net.minecraft.entity.passive.AbstractHorseEntity
            || var0 instanceof net.minecraft.entity.passive.RabbitEntity
            || var0 instanceof net.minecraft.entity.passive.FrogEntity
            || var0 instanceof net.minecraft.entity.passive.GoatEntity) {
            return TASTE_LEAPER;
        }

        if (var0 instanceof net.minecraft.entity.passive.WolfEntity
            || var0 instanceof net.minecraft.entity.passive.FoxEntity
            || var0 instanceof net.minecraft.entity.passive.CatEntity
            || var0 instanceof net.minecraft.entity.passive.OcelotEntity
            || var0 instanceof net.minecraft.entity.passive.PolarBearEntity
            || var0 instanceof net.minecraft.entity.passive.PandaEntity) {
            return TASTE_PREDATOR;
        }

        if (var0 instanceof net.minecraft.entity.passive.ChickenEntity) {
            return TASTE_AERIAL;
        }

        return switch (var1) {
            case ARTHROPOD -> TASTE_ARTHROPOD;
            case AQUATIC -> TASTE_AQUATIC;
            case INFERNAL -> TASTE_INFERNAL;
            case END -> TASTE_ENDER;
            case AERIAL -> TASTE_AERIAL;
            case ILLAGER -> TASTE_WARLIKE;
            case VILLAGER -> TASTE_PEACEFUL;
            case PASTORAL -> TASTE_HERD;
            default -> TASTE_PLAIN;
        };
    }

    private static void drink(ServerPlayerEntity var0, LivingEntity var1, MobGroups.Group var2) {
        int var3 = Math.max(1, Math.min(6, Math.round(var1.getMaxHealth() / 4.0F)));
        float var4 = 0.6F;
        int var5 = tasteOf(var1, var2);
        if (var2 == MobGroups.Group.VILLAGER || var2 == MobGroups.Group.ILLAGER) {
            var3 = Math.max(var3, 5);
        } else if (var2 == MobGroups.Group.PLAYER) {
            var3 = 6;
            var4 = 1.2F;
        }

        if (var5 == NO_BLOOD) {
            PhaseFx.privateSound(var0, WindySounds.DENY, 0.6F, 0.8F);
            var0.sendMessage(Text.translatable("message.windyorigins.sanguine.no_blood").formatted(Formatting.GRAY), true);
            return;
        }

        if (var5 == DEAD_BLOOD) {
            var0.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 160, 0));
            var0.sendMessage(Text.translatable("message.windyorigins.sanguine.dead_blood").formatted(Formatting.DARK_GREEN), true);
        } else {
            var0.getHungerManager().add(var3, var4);
            setTaste(var0, var5);
            if (var5 != TASTE_PLAIN) {
                var0.sendMessage(Text.translatable("message.windyorigins.sanguine.taste." + var5).formatted(Formatting.DARK_RED), true);
            }
        }

        PhaseFx.particles(var0, BLOOD, var1.getX(), var1.getBodyY(0.7), var1.getZ(), 14, 0.2, 0.25, 0.2, 0.0);
        PhaseFx.particles(var0, ParticleTypes.DAMAGE_INDICATOR, var1.getX(), var1.getBodyY(0.7), var1.getZ(), 3, 0.2, 0.2, 0.2, 0.1);
        PhaseFx.sound(var0, WindySounds.BLOOD_BITE, SoundCategory.PLAYERS, 0.9F, 0.8F + var0.getRandom().nextFloat() * 0.2F);
    }

    private static final StatusEffect[] TASTE_GIFTS = new StatusEffect[]{
        StatusEffects.WATER_BREATHING,
        StatusEffects.DOLPHINS_GRACE,
        StatusEffects.FIRE_RESISTANCE,
        StatusEffects.SLOW_FALLING,
        StatusEffects.NIGHT_VISION,
        StatusEffects.SPEED,
        StatusEffects.HASTE,
        StatusEffects.JUMP_BOOST
    };

    private static void setTaste(ServerPlayerEntity var0, int var1) {
        // The previous taste goes with its gifts; only one blood lingers at a time.
        if (var0.hasStatusEffect(WindyEffects.TASTE)) {
            for (StatusEffect var5 : TASTE_GIFTS) {
                StatusEffectInstance var6 = var0.getStatusEffect(var5);
                if (var6 != null && !var6.shouldShowParticles() && var6.getDuration() <= 2400) {
                    var0.removeStatusEffect(var5);
                }
            }
        }

        var0.removeStatusEffect(WindyEffects.TASTE);
        var0.addStatusEffect(new StatusEffectInstance(WindyEffects.TASTE, 2400, var1, false, false, true));
        switch (var1) {
            case TASTE_AQUATIC:
                gift(var0, StatusEffects.WATER_BREATHING, 0);
                gift(var0, StatusEffects.DOLPHINS_GRACE, 0);
                break;
            case TASTE_INFERNAL:
                gift(var0, StatusEffects.FIRE_RESISTANCE, 0);
                break;
            case TASTE_AERIAL:
                gift(var0, StatusEffects.SLOW_FALLING, 0);
                break;
            case TASTE_ENDER:
                gift(var0, StatusEffects.NIGHT_VISION, 0);
                break;
            case TASTE_PREDATOR:
                gift(var0, StatusEffects.SPEED, 0);
                break;
            case TASTE_HERD:
                gift(var0, StatusEffects.HASTE, 0);
                break;
            case TASTE_LEAPER:
                gift(var0, StatusEffects.JUMP_BOOST, 1);
        }
    }

    private static void gift(ServerPlayerEntity var0, StatusEffect var1, int var2) {
        var0.addStatusEffect(new StatusEffectInstance(var1, 2400, var2, false, false, true));
    }

    public static void noteMeal(PlayerEntity var0, ItemStack var1) {
        int var2 = TASTE_PLAIN;
        if (var1.isIn(ItemTags.FISHES) || var1.isOf(Items.DRIED_KELP) || var1.isOf(Items.PUFFERFISH)) {
            var2 = TASTE_AQUATIC;
        } else if (var1.isOf(Items.SPIDER_EYE) || var1.isOf(Items.FERMENTED_SPIDER_EYE)) {
            var2 = TASTE_ARTHROPOD;
        } else if (var1.isOf(Items.CHORUS_FRUIT)) {
            var2 = TASTE_ENDER;
        } else if (var1.isOf(Items.HONEY_BOTTLE) || var1.isOf(Items.CHICKEN) || var1.isOf(Items.COOKED_CHICKEN)) {
            var2 = TASTE_AERIAL;
        } else if (var1.isOf(Items.RABBIT) || var1.isOf(Items.COOKED_RABBIT) || var1.isOf(Items.RABBIT_STEW)) {
            var2 = TASTE_LEAPER;
        } else if (var1.isOf(Items.BEEF)
            || var1.isOf(Items.COOKED_BEEF)
            || var1.isOf(Items.PORKCHOP)
            || var1.isOf(Items.COOKED_PORKCHOP)
            || var1.isOf(Items.MUTTON)
            || var1.isOf(Items.COOKED_MUTTON)) {
            var2 = TASTE_HERD;
        } else if (var1.isOf(Items.GOLDEN_CARROT) || var1.isOf(Items.BREAD) || var1.isOf(Items.BAKED_POTATO) || var1.isOf(Items.PUMPKIN_PIE)) {
            var2 = TASTE_PEACEFUL;
        } else if (var1.isOf(Items.POTION)) {
            String var3 = PotionUtil.getPotion(var1).finishTranslationKey("");
            if (var3.contains("fire_resistance")) {
                var2 = TASTE_INFERNAL;
            } else if (var3.contains("water_breathing")) {
                var2 = TASTE_AQUATIC;
            } else if (var3.contains("slow_falling")) {
                var2 = TASTE_AERIAL;
            } else if (var3.contains("strength")) {
                var2 = TASTE_WARLIKE;
            } else if (var3.contains("swiftness")) {
                var2 = TASTE_PREDATOR;
            } else if (var3.contains("leaping")) {
                var2 = TASTE_LEAPER;
            } else if (var3.contains("night_vision")) {
                var2 = TASTE_ENDER;
            }
        }

        LAST_MEAL.put(var0.getUuid(), Integer.valueOf(var2));
    }

    public static void lifesteal(PlayerEntity var0, float var1) {
        if (isSanguine(var0) && Powers.has(var0, "vampirism")) {
            int var2 = food(var0);
            float var3 = var2 <= 2 ? 0.3F : (var2 > 10 ? 0.15F : 0.0F);
            if (var3 > 0.0F) {
                var0.heal(var1 * var3);
            }
        }
    }

    public static void tick(MinecraftServer var0) {
        for (ServerPlayerEntity var2 : var0.getPlayerManager().getPlayerList()) {
            tickBatForm(var2, var0);
            if (isSanguine(var2) && var2.isAlive() && !var2.isSpectator() && !var2.isCreative()) {
                if (Powers.has(var2, "sunburn")) {
                    tickSun(var2);
                }

                if (var2.age % 10 == 0
                    && Powers.has(var2, "running_water")
                    && var2.isTouchingWater()
                    && !var2.getWorld().getFluidState(var2.getBlockPos()).isStill()) {
                    var2.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 2, false, false, true));
                }
            } else {
                SUN_TICKS.remove(var2.getUuid());
            }
        }

        Iterator var3 = CLOUD.iterator();

        while (var3.hasNext()) {
            BatEntity var4 = (BatEntity)var3.next();
            if (var4.isRemoved() || var4.age > 50) {
                var4.discard();
                var3.remove();
            }
        }
    }

    private static void tickSun(ServerPlayerEntity var0) {
        ServerWorld var1 = var0.getServerWorld();
        BlockPos var2 = BlockPos.ofFloored(var0.getEyePos());
        boolean var3 = var1.isDay()
            && !var1.isRaining()
            && var1.getDimension().hasSkyLight()
            && var1.isSkyVisible(var2)
            && var1.getLightLevel(LightType.SKY, var2) >= 15
            && !var0.isTouchingWaterOrRain();
        if (!var3) {
            SUN_TICKS.remove(var0.getUuid());
        } else {
            ItemStack var4 = var0.getEquippedStack(EquipmentSlot.HEAD);
            if (!var4.isEmpty()) {
                if (var0.age % 10 == 0 && var4.isDamageable()) {
                    var4.damage(1, var0, var0x -> var0x.sendEquipmentBreakStatus(EquipmentSlot.HEAD));
                }
            } else {
                int var5 = SUN_TICKS.merge(var0.getUuid(), 1, Integer::sum);
                if (var5 < 40) {
                    if (var5 % 4 == 0) {
                        PhaseFx.particles(var0, ParticleTypes.SMOKE, var0.getX(), var0.getBodyY(0.8), var0.getZ(), 3, 0.25, 0.3, 0.25, 0.01);
                    }

                    if (var5 == 1) {
                        PhaseFx.sound(var0, WindySounds.BLOOD_SIZZLE, SoundCategory.PLAYERS, 0.6F, 1.2F);
                    }
                } else {
                    if (var0.age % 20 == 0) {
                        var0.setOnFireFor(4);
                    }
                }
            }
        }
    }

    public static void batForm(ServerPlayerEntity var0) {
        if (!var0.hasStatusEffect(WindyEffects.BAT_FORM)) {
            if (food(var0) < 4) {
                PhaseFx.privateSound(var0, WindySounds.DENY, 0.6F, 0.8F);
                var0.sendMessage(Text.translatable("message.windyorigins.sanguine.too_thirsty").formatted(Formatting.DARK_RED), true);
            } else {
                var0.getHungerManager().setFoodLevel(food(var0) - 4);
                var0.addStatusEffect(new StatusEffectInstance(WindyEffects.BAT_FORM, 120, 0, false, false, true));
                var0.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 120, 0, false, false, false));
                SanguineManager.BatGrants.get(var0.getServer()).grant(var0);
                Flight.grant(var0, Flight.BAT, true);
                burst(var0);
            }
        }
    }

    private static void burst(ServerPlayerEntity var0) {
        PhaseFx.sound(var0, WindySounds.BLOOD_BATS, SoundCategory.PLAYERS, 1.0F, 1.0F);
        PhaseFx.particles(var0, ParticleTypes.LARGE_SMOKE, var0.getX(), var0.getBodyY(0.5), var0.getZ(), 30, 0.4, 0.6, 0.4, 0.02);
        PhaseFx.particles(var0, BLOOD, var0.getX(), var0.getBodyY(0.5), var0.getZ(), 20, 0.4, 0.6, 0.4, 0.0);
        ServerWorld var1 = var0.getServerWorld();
        Boolean var2 = SpawnPhaseOverride.push(PhaseFx.phase(var0));

        try {
            for (int var3 = 0; var3 < 5; var3++) {
                BatEntity var4 = EntityType.BAT.create(var1);
                if (var4 != null) {
                    var4.refreshPositionAndAngles(var0.getX(), var0.getBodyY(0.6), var0.getZ(), var0.getRandom().nextFloat() * 360.0F, 0.0F);
                    var4.setInvulnerable(true);
                    var4.setSilent(var3 > 1);
                    var1.spawnEntity(var4);
                    CLOUD.add(var4);
                }
            }
        } finally {
            SpawnPhaseOverride.restore(var2);
        }
    }

    private static void tickBatForm(ServerPlayerEntity var0, MinecraftServer var1) {
        SanguineManager.BatGrants var2 = SanguineManager.BatGrants.get(var1);
        if (var2.has(var0)) {
            if (var0.hasStatusEffect(WindyEffects.BAT_FORM) && var0.isAlive()) {
                var0.fallDistance = 0.0F;
                if (var0.age % 3 == 0) {
                    PhaseFx.particles(var0, ParticleTypes.SMOKE, var0.getX(), var0.getBodyY(0.5), var0.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
                }
            } else {
                var2.revoke(var0);
                Flight.revoke(var0, Flight.BAT);
                if (!var0.isCreative() && !var0.isSpectator()) {
                    var0.removeStatusEffect(StatusEffects.INVISIBILITY);
                    var0.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 60, 0, false, false, false));
                }

                if (var0.isAlive()) {
                    burst(var0);
                }
            }
        }
    }

    public static boolean dayRest(ServerPlayerEntity var0, BlockPos var1) {
        if (Powers.has(var0, "day_rest") && var0.getWorld().isDay()) {
            var0.setSpawnPoint(var0.getWorld().getRegistryKey(), var1, var0.getYaw(), false, true);
            long var2 = var0.getWorld().getTimeOfDay() / 24000L;
            Long var4 = LAST_REST.get(var0.getUuid());
            if (var4 != null && var4 == var2) {
                var0.sendMessage(Text.translatable("message.windyorigins.sanguine.already_rested").formatted(Formatting.GRAY), true);
            } else {
                LAST_REST.put(var0.getUuid(), var2);
                var0.getHungerManager().add(6, 0.5F);
                var0.heal(4.0F);
                PhaseFx.privateSound(var0, WindySounds.BLOOD_REST, 0.8F, 0.9F);
                var0.sendMessage(Text.translatable("message.windyorigins.sanguine.rested").formatted(Formatting.DARK_RED), true);
            }

            return true;
        } else {
            return false;
        }
    }

    public static class BatGrants extends PersistentState {
        private final Set<UUID> granted = new HashSet<>();

        public static SanguineManager.BatGrants get(MinecraftServer var0) {
            return var0.getOverworld()
                .getPersistentStateManager()
                .getOrCreate(SanguineManager.BatGrants::fromNbt, SanguineManager.BatGrants::new, "windyorigins_bat_grants");
        }

        static SanguineManager.BatGrants fromNbt(NbtCompound var0) {
            SanguineManager.BatGrants var1 = new SanguineManager.BatGrants();
            NbtList var2 = var0.getList("Players", 8);

            for (int var3 = 0; var3 < var2.size(); var3++) {
                var1.granted.add(UUID.fromString(var2.getString(var3)));
            }

            return var1;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound var1) {
            NbtList var2 = new NbtList();

            for (UUID var4 : this.granted) {
                var2.add(NbtString.of(var4.toString()));
            }

            var1.put("Players", var2);
            return var1;
        }

        boolean has(PlayerEntity var1) {
            return this.granted.contains(var1.getUuid());
        }

        void grant(PlayerEntity var1) {
            if (this.granted.add(var1.getUuid())) {
                this.markDirty();
            }
        }

        void revoke(PlayerEntity var1) {
            if (this.granted.remove(var1.getUuid())) {
                this.markDirty();
            }
        }
    }
}
