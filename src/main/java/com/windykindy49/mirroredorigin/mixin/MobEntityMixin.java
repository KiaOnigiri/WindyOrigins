package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={MobEntity.class})
public abstract class MobEntityMixin {
    @Shadow
    public abstract LivingEntity getTarget();

    @Shadow
    public abstract void setTarget(LivingEntity var1);

    @Inject(method={"setTarget"}, at={@At(value="HEAD")}, cancellable=true)
    private void onSetTarget(LivingEntity target, CallbackInfo ci) {
        if (target != null && ((MirrorEntity)(Object)this).mirroredorigin$isInMirrorWorld() != ((MirrorEntity)target).mirroredorigin$isInMirrorWorld()) {
            ci.cancel();
        }
    }

    @Inject(method={"tick"}, at={@At(value="RETURN")})
    private void onTick(CallbackInfo ci) {
        Entity holdingEntity;
        MobEntity mob = (MobEntity)(Object)this;
        boolean myPhase = ((MirrorEntity)mob).mirroredorigin$isInMirrorWorld();
        LivingEntity currentTarget = this.getTarget();
        if (currentTarget != null && myPhase != ((MirrorEntity)currentTarget).mirroredorigin$isInMirrorWorld()) {
            this.setTarget(null);
        }
        if ((holdingEntity = mob.getHoldingEntity()) != null && myPhase != ((MirrorEntity)holdingEntity).mirroredorigin$isInMirrorWorld()) {
            mob.detachLeash(true, true);
        }
    }

    @Inject(method={"loot"}, at={@At(value="HEAD")}, cancellable=true)
    private void onLootItem(ItemEntity item, CallbackInfo ci) {
        if (item != null && ((MirrorEntity)(Object)this).mirroredorigin$isInMirrorWorld() != ((MirrorEntity)item).mirroredorigin$isInMirrorWorld()) {
            ci.cancel();
        }
    }
}

