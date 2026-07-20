package com.windykindy49.mirroredorigin.mixin;

import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.item.ArmorStandItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Same idea as EndCrystalItemMixin: an armor stand from the other phase must not occupy
 * the spot for a player placing one in this phase.
 */
@Mixin(value={ArmorStandItem.class})
public class ArmorStandItemMixin {
    @Redirect(
        method = "useOnBlock",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getOtherEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;)Ljava/util/List;")
    )
    private List<Entity> mirroredorigin$onlySamePhase(World world, Entity except, Box box, ItemUsageContext context) {
        List<Entity> list = world.getOtherEntities(except, box);
        if (context.getPlayer() != null) {
            boolean phase = ((MirrorEntity)context.getPlayer()).mirroredorigin$isInMirrorWorld();
            list.removeIf(entity -> ((MirrorEntity)entity).mirroredorigin$isInMirrorWorld() != phase);
        }
        return list;
    }
}
