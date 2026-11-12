package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin({ProjectileUtil.class})
public class ProjectileUtilMixin {
    @ModifyVariable(
        method = {"raycast"},
        at = @At("HEAD"),
        ordinal = 0,
        argsOnly = true
    )
    private static Predicate<Entity> filterMirrorWorldRaycast(Predicate<Entity> var0, Entity var1) {
        return var1 == null
            ? var0
            : var2 -> var0.test(var2) && ((MirrorEntity)var1).windyorigins$isInMirrorWorld() == ((MirrorEntity)var2).windyorigins$isInMirrorWorld();
    }
}
