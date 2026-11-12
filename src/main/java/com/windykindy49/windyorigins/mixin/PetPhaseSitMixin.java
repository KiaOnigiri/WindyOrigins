package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({MobEntity.class})
public abstract class PetPhaseSitMixin {
    @Unique
    private boolean windyorigins$autoSit;
    @Unique
    private boolean windyorigins$manualSit;

    @Inject(
        method = {"tick"},
        at = {@At("TAIL")}
    )
    private void windyorigins$syncPetSitting(CallbackInfo var1) {
        if ((Object)this instanceof TameableEntity var2) {
            if (!var2.getWorld().isClient() && var2.isTamed()) {
                LivingEntity var3 = var2.getOwner();
                if (var3 != null) {
                    boolean var4 = ((MirrorEntity)var2).windyorigins$isInMirrorWorld() != ((MirrorEntity)var3).windyorigins$isInMirrorWorld();
                    if (var4) {
                        if (!this.windyorigins$autoSit) {
                            this.windyorigins$manualSit = var2.isSitting();
                            this.windyorigins$autoSit = true;
                            var2.setSitting(true);
                            var2.setInSittingPose(true);
                        }
                    } else if (this.windyorigins$autoSit) {
                        this.windyorigins$autoSit = false;
                        var2.setSitting(this.windyorigins$manualSit);
                        var2.setInSittingPose(this.windyorigins$manualSit);
                    }
                }
            }
        }
    }

    @Inject(
        method = {"writeCustomDataToNbt"},
        at = {@At("TAIL")}
    )
    private void windyorigins$writeSitState(NbtCompound var1, CallbackInfo var2) {
        if (this.windyorigins$autoSit) {
            var1.putBoolean("WindyOrigins_PhaseAutoSit", true);
            var1.putBoolean("WindyOrigins_PhaseManualSit", this.windyorigins$manualSit);
        }
    }

    @Inject(
        method = {"readCustomDataFromNbt"},
        at = {@At("TAIL")}
    )
    private void windyorigins$readSitState(NbtCompound var1, CallbackInfo var2) {
        this.windyorigins$autoSit = var1.getBoolean("WindyOrigins_PhaseAutoSit");
        this.windyorigins$manualSit = var1.getBoolean("WindyOrigins_PhaseManualSit");
    }
}
