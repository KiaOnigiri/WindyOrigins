package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BoatItem;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({BoatItem.class})
public class BoatItemMixin {
    @Redirect(
        method = {"use"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;getOtherEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;Ljava/util/function/Predicate;)Ljava/util/List;"
        ),
        require = 0
    )
    private List<Entity> windyorigins$onlySamePhase(World var1, Entity var2, Box var3, Predicate<? super Entity> var4, World var5, PlayerEntity var6, Hand var7) {
        List var8 = var1.getOtherEntities(var2, var3, var4);
        boolean var9 = ((MirrorEntity)var6).windyorigins$isInMirrorWorld();
        var8.removeIf(var1x -> ((MirrorEntity)var1x).windyorigins$isInMirrorWorld() != var9);
        return var8;
    }

    @Redirect(
        method = {"use"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;isSpaceEmpty(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;)Z"
        ),
        require = 0
    )
    private boolean windyorigins$spaceEmptyForPhase(World var1, Entity var2, Box var3, World var4, PlayerEntity var5, Hand var6) {
        if (var1.isSpaceEmpty(var2, var3)) {
            return true;
        } else if (var1.getBlockCollisions(var2, var3).iterator().hasNext()) {
            return false;
        } else {
            boolean var7 = ((MirrorEntity)var5).windyorigins$isInMirrorWorld();

            for (Entity var9 : var1.getOtherEntities(var2, var3)) {
                if (((MirrorEntity)var9).windyorigins$isInMirrorWorld() == var7) {
                    return false;
                }
            }

            return true;
        }
    }
}
