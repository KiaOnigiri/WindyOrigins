package com.windykindy49.windyorigins.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.windykindy49.windyorigins.util.MirrorEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({PotionEntity.class})
public class ThrownPotionEntityMixin {
    @ModifyExpressionValue(
        method = {"applySplashPotion"},
        at = {@At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;getNonSpectatingEntities(Ljava/lang/Class;Lnet/minecraft/util/math/Box;)Ljava/util/List;"
        )}
    )
    private List<LivingEntity> filterPotionTargets(List<LivingEntity> var1) {
        ArrayList var2 = new ArrayList();
        PotionEntity var3 = (PotionEntity)(Object)this;
        boolean var4 = ((MirrorEntity)var3).windyorigins$isInMirrorWorld();

        for (LivingEntity var6 : var1) {
            if (((MirrorEntity)var6).windyorigins$isInMirrorWorld() == var4) {
                var2.add(var6);
            }
        }

        return var2;
    }
}
