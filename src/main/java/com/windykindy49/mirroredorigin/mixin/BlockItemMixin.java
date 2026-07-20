package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A boat, armor stand or any other entity standing in the OTHER phase must not stop a
 * player from placing a block there.
 *
 * The previous version could never work: after seeing canPlace() return false it re-tested
 * with world.canPlace(state, pos, ShapeContext.absent()), and World#canPlace itself runs
 * the entity-collision test that just failed. The guard was therefore false exactly when
 * the fix was needed, and the override never ran. That is the reported "blocks cannot be
 * placed onto a boat from the other phase".
 *
 * Now only the block-level validity is re-checked (canPlaceAt), and the entity test is
 * redone phase-aware: the placement is allowed unless a SAME-phase entity is in the way.
 */
@Mixin(value = {BlockItem.class})
public class BlockItemMixin {
    @Inject(method = {"canPlace"}, at = {@At(value = "RETURN")}, cancellable = true)
    private void onCanPlaceThroughGhosts(ItemPlacementContext context, BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            return;
        }
        PlayerEntity player = context.getPlayer();
        if (player == null) {
            return;
        }
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        // Vanilla refused for one of two reasons: the block cannot survive here, or an
        // entity is in the way. Only the second one is ours to override.
        if (!state.canPlaceAt(world, pos)) {
            return;
        }
        VoxelShape shape = state.getCollisionShape(world, pos, ShapeContext.of(player));
        if (shape.isEmpty()) {
            return;
        }
        Box box = shape.getBoundingBox().offset(pos);
        boolean phase = ((MirrorEntity) player).mirroredorigin$isInMirrorWorld();
        for (Entity entity : world.getOtherEntities(null, box)) {
            if (((MirrorEntity) entity).mirroredorigin$isInMirrorWorld() == phase) {
                return; // a real, same-phase obstacle
            }
        }
        cir.setReturnValue(true);
    }
}
