package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import java.util.function.Predicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value={ProjectileUtil.class})
public class ProjectileUtilMixin {
    @ModifyVariable(method={"raycast"}, at=@At(value="HEAD"), ordinal=0, argsOnly=true)
    private static Predicate<Entity> filterMirrorWorldRaycast(Predicate<Entity> predicate, Entity sourceEntity) {
        // A raycast with no source has no phase to compare against. Vanilla always passes one,
        // but this runs for every projectile and every crosshair query, so a mod handing over
        // null must not turn into an NPE on the hottest path in the game.
        if (sourceEntity == null) {
            return predicate;
        }
        return target -> predicate.test((Entity)target) && ((MirrorEntity)sourceEntity).mirroredorigin$isInMirrorWorld() == ((MirrorEntity)target).mirroredorigin$isInMirrorWorld();
    }
}

