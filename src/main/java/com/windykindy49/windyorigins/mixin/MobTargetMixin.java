package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.common.WindyEffects;
import com.windykindy49.windyorigins.dream.DreamManager;
import com.windykindy49.windyorigins.host.HostManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({MobEntity.class})
public abstract class MobTargetMixin {
    @Inject(
        method = {"setTarget"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void windyorigins$refuseTarget(LivingEntity var1, CallbackInfo var2) {
        MobEntity var3 = (MobEntity)(Object)this;
        if (var1 != null && !var3.getWorld().isClient()) {
            if (var3.hasStatusEffect(WindyEffects.DREAMING) || DreamManager.neutralTo(var3, var1) || HostManager.isTurnedAgainst(var3, var1)
                || com.windykindy49.windyorigins.mirror.MirrorManager.ignoredBy(var3, var1)) {
                var2.cancel();
            }
        }
    }
}
