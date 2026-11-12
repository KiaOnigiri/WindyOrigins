package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.item.EndCrystalItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({EndCrystalItem.class})
public class EndCrystalItemMixin {
    @Redirect(
        method = {"useOnBlock"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;getOtherEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;)Ljava/util/List;"
        )
    )
    private List<Entity> windyorigins$onlySamePhase(World var1, Entity var2, Box var3, ItemUsageContext var4) {
        List var5 = var1.getOtherEntities(var2, var3);
        if (var4.getPlayer() != null) {
            boolean var6 = ((MirrorEntity)var4.getPlayer()).windyorigins$isInMirrorWorld();
            var5.removeIf(var1x -> ((MirrorEntity)var1x).windyorigins$isInMirrorWorld() != var6);
        }

        return var5;
    }
}
