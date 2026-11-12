package com.windykindy49.windyorigins.common;

import java.util.Locale;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.mob.FlyingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public final class MobGroups {
    private static final TagKey<EntityType<?>> BOSSES = TagKey.of(RegistryKeys.ENTITY_TYPE, new Identifier("c", "bosses"));

    private MobGroups() {
    }

    public static MobGroups.Group of(LivingEntity var0) {
        if (var0 instanceof PlayerEntity) {
            return MobGroups.Group.PLAYER;
        } else {
            EntityType var1 = var0.getType();

            for (MobGroups.Group var5 : MobGroups.Group.values()) {
                if (var5 != MobGroups.Group.PLAYER && var1.isIn(var5.tag)) {
                    return var5;
                }
            }

            if (var1.isIn(BOSSES) || var0.getMaxHealth() > 100.0F) {
                return MobGroups.Group.BOSS;
            } else if (var0 instanceof MerchantEntity) {
                return MobGroups.Group.VILLAGER;
            } else if (var0.getWorld().getRegistryKey() == World.END) {
                return MobGroups.Group.END;
            } else if (!var1.isFireImmune() && var0.getWorld().getRegistryKey() != World.NETHER) {
                EntityGroup var6 = var0.getGroup();
                if (var6 == EntityGroup.ILLAGER) {
                    return MobGroups.Group.ILLAGER;
                } else if (var6 == EntityGroup.UNDEAD) {
                    return MobGroups.Group.UNDEAD;
                } else if (var6 == EntityGroup.ARTHROPOD) {
                    return MobGroups.Group.ARTHROPOD;
                } else {
                    SpawnGroup var7 = var1.getSpawnGroup();
                    if (var6 == EntityGroup.AQUATIC
                        || var7 == SpawnGroup.WATER_CREATURE
                        || var7 == SpawnGroup.WATER_AMBIENT
                        || var7 == SpawnGroup.UNDERGROUND_WATER_CREATURE
                        || var7 == SpawnGroup.AXOLOTLS) {
                        return MobGroups.Group.AQUATIC;
                    } else if (var0 instanceof FlyingEntity || var7 == SpawnGroup.AMBIENT) {
                        return MobGroups.Group.AERIAL;
                    } else {
                        if (var0 instanceof MobEntity var8 && var8.getNavigation() instanceof BirdNavigation) {
                            return MobGroups.Group.AERIAL;
                        }

                        return var7 != SpawnGroup.CREATURE && !(var0 instanceof AbstractHorseEntity) ? MobGroups.Group.WILD : MobGroups.Group.PASTORAL;
                    }
                }
            } else {
                return MobGroups.Group.INFERNAL;
            }
        }
    }

    public static enum Group {
        BOSS,
        END,
        INFERNAL,
        ILLAGER,
        UNDEAD,
        ARTHROPOD,
        AQUATIC,
        AERIAL,
        PASTORAL,
        WILD,
        VILLAGER,
        PLAYER;

        public final TagKey<EntityType<?>> tag = TagKey.of(
            RegistryKeys.ENTITY_TYPE, new Identifier("windyorigins", "groups/" + this.name().toLowerCase(Locale.ROOT))
        );

        public String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
