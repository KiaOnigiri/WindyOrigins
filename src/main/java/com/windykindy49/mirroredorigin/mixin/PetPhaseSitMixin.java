package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.nbt.NbtCompound;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A tamed pet sits down when its owner leaves for the other phase and gets up again when
 * the owner comes back - the same idea as the owner stepping through a nether portal,
 * except that here both of them stay in the same world.
 *
 * FollowOwnerGoalMixin already stopped pets from teleporting across phases, but they kept
 * wandering, fighting and drifting away from the spot where the owner left them. Sitting is
 * done through setSitting(), which is exactly what the "order to sit" interaction uses, so
 * SitGoal takes over and every tamed mob is covered - wolves, cats, parrots, foxes and
 * anything else built on TameableEntity.
 *
 * A manual sit issued by the player is remembered and restored, so a dog told to sit before
 * the owner switched phase does not stand up on their return. Both flags are persisted:
 * without that, a pet auto-sitting while the world is saved would be indistinguishable from
 * one the player sat down on purpose, and would stay seated forever.
 *
 * MobEntity is the target rather than TameableEntity because tick() is declared there;
 * the instanceof check keeps the behaviour limited to tameable mobs.
 */
@Mixin(value = {MobEntity.class})
public abstract class PetPhaseSitMixin {
    @Unique
    private boolean mirroredorigin$autoSit;
    @Unique
    private boolean mirroredorigin$manualSit;

    @Inject(method = "tick", at = @At("TAIL"))
    private void mirroredorigin$syncPetSitting(CallbackInfo ci) {
        if (!(((Object) this) instanceof TameableEntity)) {
            return;
        }
        TameableEntity pet = (TameableEntity) (Object) this;
        if (pet.getWorld().isClient() || !pet.isTamed()) {
            return;
        }
        LivingEntity owner = pet.getOwner();
        if (owner == null) {
            // Owner is offline: vanilla already leaves the pet where it is, and we must not
            // strand it in an auto-sit we could never lift.
            return;
        }
        boolean separated = ((MirrorEntity) pet).mirroredorigin$isInMirrorWorld()
            != ((MirrorEntity) owner).mirroredorigin$isInMirrorWorld();

        if (separated) {
            if (!this.mirroredorigin$autoSit) {
                this.mirroredorigin$manualSit = pet.isSitting();
                this.mirroredorigin$autoSit = true;
                pet.setSitting(true);
                pet.setInSittingPose(true);
            }
        } else if (this.mirroredorigin$autoSit) {
            this.mirroredorigin$autoSit = false;
            pet.setSitting(this.mirroredorigin$manualSit);
            pet.setInSittingPose(this.mirroredorigin$manualSit);
        }
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void mirroredorigin$writeSitState(NbtCompound nbt, CallbackInfo ci) {
        if (this.mirroredorigin$autoSit) {
            nbt.putBoolean("MirroredOrigin_PhaseAutoSit", true);
            nbt.putBoolean("MirroredOrigin_PhaseManualSit", this.mirroredorigin$manualSit);
        }
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void mirroredorigin$readSitState(NbtCompound nbt, CallbackInfo ci) {
        this.mirroredorigin$autoSit = nbt.getBoolean("MirroredOrigin_PhaseAutoSit");
        this.mirroredorigin$manualSit = nbt.getBoolean("MirroredOrigin_PhaseManualSit");
    }
}
