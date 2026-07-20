package com.windykindy49.mirroredorigin.mixin;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BoatItem;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A boat from the other phase must not block placing a boat here.
 *
 * The first attempt only filtered the getOtherEntities call, which is merely the "is the
 * player standing inside something" test. The check that actually refuses the placement is
 * world.isSpaceEmpty(newBoat, box) further down, and it counts every entity regardless of
 * phase - that is why boats still would not stack while blocks already did.
 *
 * Block collisions are re-tested separately so that real terrain still blocks the boat;
 * only the entity half of the test becomes phase-aware.
 */
@Mixin(value = {BoatItem.class})
public class BoatItemMixin {

    @Redirect(
        method = "use",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getOtherEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;Ljava/util/function/Predicate;)Ljava/util/List;"),
        require = 0
    )
    private List<Entity> mirroredorigin$onlySamePhase(World world, Entity except, Box box, Predicate<? super Entity> predicate, World world2, PlayerEntity user, Hand hand) {
        List<Entity> list = world.getOtherEntities(except, box, predicate);
        boolean phase = ((MirrorEntity) user).mirroredorigin$isInMirrorWorld();
        list.removeIf(entity -> ((MirrorEntity) entity).mirroredorigin$isInMirrorWorld() != phase);
        return list;
    }

    @Redirect(
        method = "use",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;isSpaceEmpty(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;)Z"),
        require = 0
    )
    private boolean mirroredorigin$spaceEmptyForPhase(World world, Entity boat, Box box, World world2, PlayerEntity user, Hand hand) {
        if (world.isSpaceEmpty(boat, box)) {
            return true;
        }
        // Vanilla says occupied. Terrain still counts for everyone...
        if (world.getBlockCollisions(boat, box).iterator().hasNext()) {
            return false;
        }
        // ...but entities only count if they share the placing player's phase.
        boolean phase = ((MirrorEntity) user).mirroredorigin$isInMirrorWorld();
        for (Entity entity : world.getOtherEntities(boat, box)) {
            if (((MirrorEntity) entity).mirroredorigin$isInMirrorWorld() == phase) {
                return false;
            }
        }
        return true;
    }
}
