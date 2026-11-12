package com.windykindy49.windyorigins.host;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Mends a Host's broken chip: hold it and use it. */
public class ChipRepairItem extends Item {
    public ChipRepairItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient()) {
            return HostManager.isHost(user) && HostManager.isBroken(user) ? TypedActionResult.success(stack) : TypedActionResult.pass(stack);
        }

        if (user instanceof ServerPlayerEntity player && HostManager.repairChip(player)) {
            if (!player.isCreative()) {
                stack.decrement(1);
            }

            return TypedActionResult.success(stack);
        }

        user.sendMessage(Text.translatable("message.windyorigins.volta.chip_fine").formatted(Formatting.GRAY), true);
        return TypedActionResult.pass(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, java.util.List<Text> tooltip, net.minecraft.client.item.TooltipContext context) {
        tooltip.add(Text.translatable("item.windyorigins.chip_repair_kit.tooltip").formatted(Formatting.GRAY));
    }
}
