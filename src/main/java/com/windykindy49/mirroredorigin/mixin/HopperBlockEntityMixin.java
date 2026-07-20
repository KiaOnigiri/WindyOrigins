package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.block.entity.Hopper;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.world.World;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={HopperBlockEntity.class})
public class HopperBlockEntityMixin {
    @Inject(method={"getInputItemEntities"}, at={@At(value="RETURN")})
    private static void filterItemsForEntityHoppers(World world, Hopper hopper, CallbackInfoReturnable<List<ItemEntity>> cir) {
        if (hopper instanceof Entity) {
            Entity entityHopper = (Entity)hopper;
            List<ItemEntity> items = cir.getReturnValue();
            items.removeIf(item -> ((MirrorEntity)item).mirroredorigin$isInMirrorWorld() != ((MirrorEntity)entityHopper).mirroredorigin$isInMirrorWorld());
        }
    }
}

