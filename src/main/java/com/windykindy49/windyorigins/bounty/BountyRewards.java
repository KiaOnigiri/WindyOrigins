package com.windykindy49.windyorigins.bounty;

import java.util.Locale;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

public final class BountyRewards {
    public static final TagKey<EntityType<?>> BOSSES = TagKey.of(RegistryKeys.ENTITY_TYPE, new Identifier("c", "bosses"));

    private BountyRewards() {
    }

    public static BountyRewards.Tier tierOf(LivingEntity var0) {
        if (var0 instanceof ServerPlayerEntity) {
            return BountyRewards.Tier.HEAD_HUNT;
        } else {
            float var1 = var0.getMaxHealth();
            if (var0.getType().isIn(BOSSES) || var1 > 100.0F) {
                return BountyRewards.Tier.LEGENDARY;
            } else if (var1 > 40.0F) {
                return BountyRewards.Tier.ELITE;
            } else {
                return var1 >= 20.0F ? BountyRewards.Tier.STANDARD : BountyRewards.Tier.SMALL_FRY;
            }
        }
    }

    public static BountyRewards.Tier pay(ServerPlayerEntity var0, LivingEntity var1) {
        BountyRewards.Tier var2 = tierOf(var1);
        Random var3 = var0.getRandom();
        switch (var2) {
            case SMALL_FRY:
                if (var3.nextFloat() < 0.5F) {
                    give(var0, new ItemStack(Items.GOLD_NUGGET, 1 + var3.nextInt(2)));
                }
                break;
            case STANDARD:
                if (var3.nextFloat() < 0.25F) {
                    give(var0, new ItemStack(Items.EMERALD));
                }

                if (var3.nextFloat() < 0.5F) {
                    give(var0, new ItemStack(Items.GOLD_NUGGET, 2 + var3.nextInt(3)));
                }
                break;
            case ELITE:
                give(var0, new ItemStack(Items.EMERALD, 1 + var3.nextInt(2)));
                if (var3.nextFloat() < 0.5F) {
                    give(var0, new ItemStack(Items.GOLD_INGOT));
                }

                if (var3.nextFloat() < 0.15F) {
                    give(var0, new ItemStack(Items.DIAMOND));
                }
                break;
            case LEGENDARY:
                give(var0, new ItemStack(Items.EMERALD, 5 + var3.nextInt(4)));
                give(var0, new ItemStack(Items.DIAMOND, 1 + var3.nextInt(2)));
                give(var0, new ItemStack(Items.GOLD_INGOT, 2 + var3.nextInt(2)));
                if (var3.nextFloat() < 0.25F) {
                    give(var0, new ItemStack(Items.NETHERITE_SCRAP));
                }
                break;
            case HEAD_HUNT:
                ItemStack var4 = new ItemStack(Items.PLAYER_HEAD);
                NbtCompound var5 = NbtHelper.writeGameProfile(new NbtCompound(), ((ServerPlayerEntity)var1).getGameProfile());
                var4.getOrCreateNbt().put("SkullOwner", var5);
                give(var0, var4);
        }

        return var2;
    }

    public static void give(ServerPlayerEntity var0, ItemStack var1) {
        if (!var1.isEmpty()) {
            if (!var0.getInventory().insertStack(var1) && !var1.isEmpty()) {
                var0.dropItem(var1, false);
            } else if (!var1.isEmpty()) {
                var0.dropItem(var1, false);
            }

            var0.currentScreenHandler.sendContentUpdates();
        }
    }

    public static enum Tier {
        SMALL_FRY(1.0F),
        STANDARD(2.0F),
        ELITE(3.0F),
        LEGENDARY(10.0F),
        HEAD_HUNT(1.0F);

        public final float experience;

        private Tier(float nullxx) {
            this.experience = nullxx;
        }

        public String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
