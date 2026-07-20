package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import com.windykindy49.mirroredorigin.util.CrossPhaseEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Carries the "belongs to both phases" flag for a bolt.
 *
 * Tracked data rather than a plain field, because the client has to know as well: the
 * renderer hides entities from the other phase, and a storm both worlds are supposed to see
 * must not be hidden from either. Registered on LightningEntity alone so no other entity
 * pays for a flag only lightning uses.
 */
@Mixin(value = {LightningEntity.class})
public abstract class LightningEntityMixin implements CrossPhaseEntity {
    @Unique
    private static final TrackedData<Boolean> MIRRORED_ORIGIN$CROSS_PHASE =
        DataTracker.registerData(LightningEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mirroredorigin$initCrossPhase(CallbackInfo ci) {
        ((LightningEntity) (Object) this).getDataTracker()
            .startTracking(MIRRORED_ORIGIN$CROSS_PHASE, Boolean.FALSE);
    }

    @Override
    public boolean mirroredorigin$isCrossPhase() {
        return ((LightningEntity) (Object) this).getDataTracker()
            .get(MIRRORED_ORIGIN$CROSS_PHASE).booleanValue();
    }

    @Override
    public void mirroredorigin$setCrossPhase(boolean crossPhase) {
        ((LightningEntity) (Object) this).getDataTracker()
            .set(MIRRORED_ORIGIN$CROSS_PHASE, Boolean.valueOf(crossPhase));
    }

    // A bolt lives about four ticks and is never saved, but it is re-sent to clients that
    // come into range, so the flag has to survive that round trip rather than the disk.
    @Inject(method = "writeCustomDataToNbt", at = @At("RETURN"))
    private void mirroredorigin$write(NbtCompound nbt, CallbackInfo ci) {
        nbt.putBoolean("MirroredOrigin_CrossPhase", this.mirroredorigin$isCrossPhase());
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("RETURN"))
    private void mirroredorigin$read(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains("MirroredOrigin_CrossPhase")) {
            this.mirroredorigin$setCrossPhase(nbt.getBoolean("MirroredOrigin_CrossPhase"));
        }
    }
}
