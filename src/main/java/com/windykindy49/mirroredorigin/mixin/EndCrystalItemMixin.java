package com.windykindy49.mirroredorigin.mixin;

import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.item.EndCrystalItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * An end crystal belonging to the other phase used to block placement (and vice versa),
 * because the "is this spot free?" check counts entities regardless of phase.
 * Filter that list down to the placing player's phase.
 */
@Mixin(value={EndCrystalItem.class})
public class EndCrystalItemMixin {
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
