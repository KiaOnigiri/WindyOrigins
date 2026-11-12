package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorContext;
import com.windykindy49.windyorigins.util.MirrorEntity;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({AbstractDecorationEntity.class, ItemFrameEntity.class})
public abstract class DecorationPlacementMixin {
    @Redirect(
        method = {"canStayAttached"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;getOtherEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;Ljava/util/function/Predicate;)Ljava/util/List;"
        ),
        require = 0
    )
    private List<Entity> windyorigins$onlySamePhase(World var1, Entity var2, Box var3, Predicate<? super Entity> var4) {
        List var5 = var1.getOtherEntities(var2, var3, var4);
        Boolean var6 = MirrorContext.get();
        boolean var7 = var6 != null ? var6 : ((MirrorEntity)(Object)this).windyorigins$isInMirrorWorld();
        var5.removeIf(var1x -> ((MirrorEntity)var1x).windyorigins$isInMirrorWorld() != var7);
        return var5;
    }
}
