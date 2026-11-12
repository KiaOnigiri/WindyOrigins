package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.CrossPhaseEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LightningEntity.class})
public abstract class LightningEntityMixin implements CrossPhaseEntity {
    @Unique
    private static final TrackedData<Boolean> MIRRORED_ORIGIN$CROSS_PHASE = DataTracker.registerData(LightningEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    @Inject(
        method = {"<init>"},
        at = {@At("TAIL")}
    )
    private void windyorigins$initCrossPhase(CallbackInfo var1) {
        ((LightningEntity)(Object)this).getDataTracker().startTracking(MIRRORED_ORIGIN$CROSS_PHASE, Boolean.FALSE);
    }

    @Override
    public boolean windyorigins$isCrossPhase() {
        return ((LightningEntity)(Object)this).getDataTracker().get(MIRRORED_ORIGIN$CROSS_PHASE);
    }

    @Override
    public void windyorigins$setCrossPhase(boolean var1) {
        ((LightningEntity)(Object)this).getDataTracker().set(MIRRORED_ORIGIN$CROSS_PHASE, var1);
    }

    @Inject(
        method = {"writeCustomDataToNbt"},
        at = {@At("RETURN")}
    )
    private void windyorigins$write(NbtCompound var1, CallbackInfo var2) {
        var1.putBoolean("WindyOrigins_CrossPhase", this.windyorigins$isCrossPhase());
    }

    @Inject(
        method = {"readCustomDataFromNbt"},
        at = {@At("RETURN")}
    )
    private void windyorigins$read(NbtCompound var1, CallbackInfo var2) {
        if (var1.contains("WindyOrigins_CrossPhase")) {
            this.windyorigins$setCrossPhase(var1.getBoolean("WindyOrigins_CrossPhase"));
        }
    }
}
