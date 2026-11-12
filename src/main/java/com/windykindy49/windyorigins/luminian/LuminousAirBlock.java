package com.windykindy49.windyorigins.luminian;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LightBlock;
import net.minecraft.block.AbstractBlock.Settings;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

public class LuminousAirBlock extends LightBlock {
    private static final int CHECK_TICKS = 20;

    public LuminousAirBlock(Settings var1) {
        super(var1);
    }

    @Override
    public void onBlockAdded(BlockState var1, World var2, BlockPos var3, BlockState var4, boolean var5) {
        super.onBlockAdded(var1, var2, var3, var4, var5);
        if (!var2.isClient()) {
            var2.scheduleBlockTick(var3, this, 20);
        }
    }

    @Override
    public void scheduledTick(BlockState var1, ServerWorld var2, BlockPos var3, Random var4) {
        super.scheduledTick(var1, var2, var3, var4);
        PlayerEntity var5 = var2.getClosestPlayer((double)var3.getX() + 0.5, (double)var3.getY() + 0.5, (double)var3.getZ() + 0.5, 2.5, var1x -> {
            if (var1x instanceof PlayerEntity var2x && LuminianManager.isLightAt(var2x, var3)) {
                return true;
            }

            return false;
        });
        if (var5 == null) {
            var2.setBlockState(var3, var1.get(WATERLOGGED) ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState());
        } else {
            var2.scheduleBlockTick(var3, this, 20);
        }
    }
}
