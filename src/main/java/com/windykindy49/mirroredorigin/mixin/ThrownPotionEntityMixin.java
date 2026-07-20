package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value={PotionEntity.class})
public class ThrownPotionEntityMixin {
    @ModifyExpressionValue(method={"applySplashPotion"}, at={@At(value="INVOKE", target="Lnet/minecraft/world/World;getNonSpectatingEntities(Ljava/lang/Class;Lnet/minecraft/util/math/Box;)Ljava/util/List;")})
    private List<LivingEntity> filterPotionTargets(List<LivingEntity> original) {
        ArrayList<LivingEntity> safeList = new ArrayList<LivingEntity>();
        PotionEntity potion = (PotionEntity)(Object)this;
        // The potion's OWN phase, and deliberately nothing else. This used to start from the
        // potion and then overwrite it with the thrower's phase whenever the thrower was
        // still resolvable, which meant the splash followed them: throw a potion, shift phase
        // while it is in the air, and it landed in the phase you had moved to instead of the
        // one you threw it in. A projectile's phase is stamped when it is spawned and must
        // stay frozen from that moment - it is a thing in the world, not a remote control.
        boolean phase = ((MirrorEntity)potion).mirroredorigin$isInMirrorWorld();
        for (LivingEntity entity : original) {
            if (((MirrorEntity)entity).mirroredorigin$isInMirrorWorld() != phase) continue;
            safeList.add(entity);
        }
        return safeList;
    }
}

