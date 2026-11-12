package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.dream.DreamSnapshotHolder;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ServerPlayerEntity.class})
public abstract class DreamSnapshotMixin implements DreamSnapshotHolder {
    @Unique
    private NbtCompound windyorigins$dreamSnapshot;

    @Override
    public NbtCompound windyorigins$getDreamSnapshot() {
        return this.windyorigins$dreamSnapshot;
    }

    @Override
    public void windyorigins$setDreamSnapshot(NbtCompound var1) {
        this.windyorigins$dreamSnapshot = var1;
    }

    @Inject(
        method = {"writeCustomDataToNbt"},
        at = {@At("TAIL")}
    )
    private void windyorigins$saveSnapshot(NbtCompound var1, CallbackInfo var2) {
        if (this.windyorigins$dreamSnapshot != null) {
            var1.put("WindyDreamSnapshot", this.windyorigins$dreamSnapshot);
        }
    }

    @Inject(
        method = {"readCustomDataFromNbt"},
        at = {@At("TAIL")}
    )
    private void windyorigins$loadSnapshot(NbtCompound var1, CallbackInfo var2) {
        this.windyorigins$dreamSnapshot = var1.contains("WindyDreamSnapshot") ? var1.getCompound("WindyDreamSnapshot") : null;
    }
}
