package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandler;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={Entity.class})
public abstract class EntityMixin
implements MirrorEntity {
    @Unique
    private static final TrackedData<Boolean> IN_MIRROR_WORLD = DataTracker.registerData(Entity.class, TrackedDataHandlerRegistry.BOOLEAN);
    @Unique
    private static final Identifier IN_MIRROR_ID = new Identifier("mirroredorigin", "in_mirror_world");

    @Shadow
    public abstract DataTracker getDataTracker();

    @Inject(method={"<init>"}, at={@At(value="TAIL")})
    private void onInit(EntityType<?> type, World world, CallbackInfo ci) {
        this.getDataTracker().startTracking(IN_MIRROR_WORLD, false);
    }

    @Override
    public boolean mirroredorigin$isInMirrorWorld() {
        Entity self = (Entity)(Object)this;
        if (self instanceof PlayerEntity) {
            // The PowerType is deliberately NOT cached in a static field. Apoli clears and
            // rebuilds PowerTypeRegistry on every datapack reload and on joining a server,
            // so a cached instance belongs to a dead registry: hasPower() then returns false
            // for everyone and phase separation dies silently until a restart. A single
            // /reload was enough to trigger it. Both calls below are plain map lookups.
            if (PowerTypeRegistry.contains(IN_MIRROR_ID)) {
                PowerHolderComponent powers = PowerHolderComponent.KEY.get(self);
                if (powers != null) {
                    return powers.hasPower(PowerTypeRegistry.get(IN_MIRROR_ID));
                }
            }
            // Registry not ready yet (mid-join, mid-reload, or a broken datapack). Fall back
            // to the tracked mirror of the flag instead of claiming "overworld phase", which
            // would teleport every player's perception into the wrong world for a few ticks.
            return (Boolean)(Object)this.getDataTracker().get(IN_MIRROR_WORLD);
        }
        return (Boolean)(Object)this.getDataTracker().get(IN_MIRROR_WORLD);
    }

    @Override
    public void mirroredorigin$setInMirrorWorld(boolean inMirror) {
        if (!((Entity)(Object)this instanceof PlayerEntity)) {
            this.getDataTracker().set(IN_MIRROR_WORLD, inMirror);
        }
    }

    @Inject(method={"collidesWith"}, at={@At(value="HEAD")}, cancellable=true)
    private void onCollidesWith(Entity other, CallbackInfoReturnable<Boolean> cir) {
        if (this.mirroredorigin$isInMirrorWorld() != ((MirrorEntity)other).mirroredorigin$isInMirrorWorld()) {
            cir.setReturnValue(false);
        }
    }

    /**
     * LivingEntityMixin guards LivingEntity#damage, but item frames, paintings, armor
     * stands, boats, minecarts and end crystals are plain Entities and override damage()
     * without ever calling super, so nothing covered them. In normal play the crosshair
     * cannot even find them (ProjectileUtilMixin filters the raycast), but that check lives
     * on the client: a modified one could punch out another phase's paintings. The server
     * decides here instead.
     */
    @Inject(method={"damage"}, at={@At(value="HEAD")}, cancellable=true)
    private void mirroredorigin$blockCrossPhaseDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Entity attacker = source.getAttacker();
        if (attacker != null && this.mirroredorigin$isInMirrorWorld() != ((MirrorEntity)attacker).mirroredorigin$isInMirrorWorld()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method={"pushAwayFrom"}, at={@At(value="HEAD")}, cancellable=true)
    private void onPushAwayFrom(Entity other, CallbackInfo ci) {
        if (this.mirroredorigin$isInMirrorWorld() != ((MirrorEntity)other).mirroredorigin$isInMirrorWorld()) {
            ci.cancel();
        }
    }

    /**
     * Publishing the phase context used to happen here as well, with a saved-field approach
     * that fought EntityContextMixin's stack-based one over the very same method. Mixin does
     * not order injections from different mixin classes, so on the exit path the stack could
     * be unwound first and this callback would then re-set the phase it had saved - leaving
     * the context alive after the tick had ended and mis-phasing everything spawned next.
     * Context is now EntityContextMixin's job alone; what is left here is the player mirror.
     *
     * Players keep their phase in an Apoli power, which the client cannot read for OTHER
     * players, so it is copied into tracked data that does sync. RETURN rather than TAIL so
     * an early exit cannot skip the copy and leave a stale value on everyone's client.
     */
    @Inject(method={"tick"}, at={@At(value="RETURN")})
    private void onTickTail(CallbackInfo ci) {
        Entity self = (Entity)(Object)this;
        // Riding survived a phase change: mount a horse or a boat, shift phase, and you were
        // still strapped to a vehicle you can neither see nor be seen on, dragged around by
        // something that no longer exists for you. startRiding was guarded, but only at the
        // moment of mounting. Vanilla replaces the passenger list on every change rather
        // than mutating it, so dismounting from inside a tick is safe.
        if (!self.getWorld().isClient()) {
            Entity vehicle = self.getVehicle();
            if (vehicle != null && this.mirroredorigin$isInMirrorWorld()
                != ((MirrorEntity)vehicle).mirroredorigin$isInMirrorWorld()) {
                self.stopRiding();
            }
        }
        if (self instanceof PlayerEntity) {
            boolean isNowInMirror = this.mirroredorigin$isInMirrorWorld();
            if ((Boolean)(Object)this.getDataTracker().get(IN_MIRROR_WORLD) != isNowInMirror) {
                this.getDataTracker().set(IN_MIRROR_WORLD, isNowInMirror);
            }
        }
    }

    @Inject(method={"writeNbt"}, at={@At(value="RETURN")})
    private void onWriteNbt(NbtCompound nbt, CallbackInfoReturnable<NbtCompound> cir) {
        nbt.putBoolean("MirroredOrigin_IsMirror", ((Boolean)(Object)this.getDataTracker().get(IN_MIRROR_WORLD)).booleanValue());
    }

    @Inject(method={"readNbt"}, at={@At(value="RETURN")})
    private void onReadNbt(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains("MirroredOrigin_IsMirror")) {
            this.getDataTracker().set(IN_MIRROR_WORLD, nbt.getBoolean("MirroredOrigin_IsMirror"));
        }
    }
}

