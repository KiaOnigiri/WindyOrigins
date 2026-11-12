package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.dream.DreamRules;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
    value = {Power.class},
    remap = false
)
public abstract class DreamPowerMixin {
    @Shadow
    protected LivingEntity entity;
    @Shadow
    protected PowerType<?> type;

    @Inject(
        method = {"isActive"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void windyorigins$silentInDreams(CallbackInfoReturnable<Boolean> var1) {
        if (this.entity != null && this.type != null && DreamRules.silenced(this.entity, this.type.getIdentifier())) {
            var1.setReturnValue(false);
        }
    }
}
