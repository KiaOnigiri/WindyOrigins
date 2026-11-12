package com.windykindy49.windyorigins.host;

import com.windykindy49.windyorigins.common.PhaseFx;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

/** Mends a Host's broken chip: hold it in use for two seconds. Letting go early wastes nothing but the time. */
public class ChipRepairItem extends Item {
    public ChipRepairItem(Settings settings) {
        super(settings);
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BRUSH;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return HostManager.REPAIR_TICKS;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (HostManager.isHost(user) && HostManager.isBroken(user)) {
            user.setCurrentHand(hand);
            return TypedActionResult.consume(stack);
        }

        if (!world.isClient()) {
            user.sendMessage(Text.translatable("message.windyorigins.volta.chip_fine").formatted(Formatting.GRAY), true);
        }

        return TypedActionResult.pass(stack);
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (world.isClient() || !(user instanceof ServerPlayerEntity player)) {
            return;
        }

        int done = HostManager.REPAIR_TICKS - remainingUseTicks;
        if (done % 5 == 0) {
            PhaseFx.sound(player, SoundEvents.BLOCK_COPPER_HIT, SoundCategory.PLAYERS, 0.6F, 1.2F + (float)done / (float)HostManager.REPAIR_TICKS);
            PhaseFx.particles(player, ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getEyeY() - 0.1, player.getZ(), 4, 0.2, 0.15, 0.2, 0.2);
            player.sendMessage(
                Text.translatable("message.windyorigins.volta.chip_repairing", Math.min(99, done * 100 / HostManager.REPAIR_TICKS)).formatted(Formatting.AQUA), true
            );
        }
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (user instanceof ServerPlayerEntity player && HostManager.repairChip(player) && !player.isCreative()) {
            stack.decrement(1);
        }

        return stack;
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, java.util.List<Text> tooltip, net.minecraft.client.item.TooltipContext context) {
        tooltip.add(Text.translatable("item.windyorigins.chip_repair_kit.tooltip").formatted(Formatting.GRAY));
    }
}
