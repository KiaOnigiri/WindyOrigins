package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({MobEntity.class})
public abstract class MobEntityMixin {
    @Shadow
    public abstract LivingEntity getTarget();

    @Shadow
    public abstract void setTarget(LivingEntity var1);

    @Inject(
        method = {"setTarget"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void onSetTarget(LivingEntity var1, CallbackInfo var2) {
        if (var1 != null && ((MirrorEntity)(Object)this).windyorigins$isInMirrorWorld() != ((MirrorEntity)var1).windyorigins$isInMirrorWorld()) {
            var2.cancel();
        }
    }

    @Inject(
        method = {"tick"},
        at = {@At("RETURN")}
    )
    private void onTick(CallbackInfo var1) {
        MobEntity var3 = (MobEntity)(Object)this;
        boolean var4 = ((MirrorEntity)var3).windyorigins$isInMirrorWorld();
        LivingEntity var5 = this.getTarget();
        if (var5 != null && var4 != ((MirrorEntity)var5).windyorigins$isInMirrorWorld()) {
            this.setTarget(null);
        }

        Entity var2;
        if ((var2 = var3.getHoldingEntity()) != null && var4 != ((MirrorEntity)var2).windyorigins$isInMirrorWorld()) {
            var3.detachLeash(true, true);
        }
    }

    @Inject(
        method = {"loot"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void onLootItem(ItemEntity var1, CallbackInfo var2) {
        if (var1 != null && ((MirrorEntity)(Object)this).windyorigins$isInMirrorWorld() != ((MirrorEntity)var1).windyorigins$isInMirrorWorld()) {
            var2.cancel();
        }
    }
}
