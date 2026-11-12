package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.common.WindyEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Entity.class})
public abstract class GhostNoClipMixin {
    @Inject(
        method = {"move"},
        at = {@At("HEAD")}
    )
    private void windyorigins$ghostThroughWalls(MovementType var1, Vec3d var2, CallbackInfo var3) {
        if ((Object)this instanceof PlayerEntity var4 && var4.hasStatusEffect(WindyEffects.VENGEANCE)) {
            var4.noClip = true;
        }
    }
}
