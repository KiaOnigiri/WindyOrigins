package com.windykindy49.windyorigins.folded;

import com.windykindy49.windyorigins.common.PhaseFx;
import com.windykindy49.windyorigins.common.Powers;
import com.windykindy49.windyorigins.common.WindySounds;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item.Settings;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

public class FoldRifleItem extends Item {
    public static final int CHARGE_TICKS = 25;

    public FoldRifleItem(Settings var1) {
        super(var1);
    }

    @Override
    public UseAction getUseAction(ItemStack var1) {
        return UseAction.BOW;
    }

    @Override
    public int getMaxUseTime(ItemStack var1) {
        return 72000;
    }

    @Override
    public TypedActionResult<ItemStack> use(World var1, PlayerEntity var2, Hand var3) {
        ItemStack var4 = var2.getStackInHand(var3);
        if (Powers.has(var2, "fold_rifle") && Powers.resource(var2, "fold", 0) >= FoldedManager.SHOT_COST) {
            var2.setCurrentHand(var3);
            if (!var1.isClient()) {
                PhaseFx.sound(var2, WindySounds.FOLD_CHARGE, SoundCategory.PLAYERS, 0.7F, 1.4F);
            }

            return TypedActionResult.consume(var4);
        } else {
            if (var2 instanceof ServerPlayerEntity var5) {
                PhaseFx.privateSound(var5, WindySounds.DENY, 0.5F, 1.3F);
            }

            return TypedActionResult.fail(var4);
        }
    }

    @Override
    public void onStoppedUsing(ItemStack var1, World var2, LivingEntity var3, int var4) {
        int var5 = this.getMaxUseTime(var1) - var4;
        if (var5 >= CHARGE_TICKS) {
            if (var3 instanceof ServerPlayerEntity var6) {
                FoldedManager.requestShot(var6);
            } else if (var2.isClient()) {
                FoldedManager.clientShot.accept(var3);
            }
        }
    }

    public static float charge(LivingEntity var0) {
        return var0.isUsingItem() && var0.getActiveItem().getItem() instanceof FoldRifleItem ? Math.min(1.0F, (float)var0.getItemUseTime() / (float)CHARGE_TICKS) : 0.0F;
    }
}
