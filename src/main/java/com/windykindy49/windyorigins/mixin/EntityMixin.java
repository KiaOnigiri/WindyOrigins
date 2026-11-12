package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Entity.class})
public abstract class EntityMixin implements MirrorEntity {
    @Unique
    private static final TrackedData<Boolean> IN_MIRROR_WORLD = DataTracker.registerData(Entity.class, TrackedDataHandlerRegistry.BOOLEAN);
    @Unique
    private static final Identifier IN_MIRROR_ID = new Identifier("windyorigins", "in_mirror_world");

    @Shadow
    public abstract DataTracker getDataTracker();

    @Inject(
        method = {"<init>"},
        at = {@At("TAIL")}
    )
    private void onInit(EntityType<?> var1, World var2, CallbackInfo var3) {
        this.getDataTracker().startTracking(IN_MIRROR_WORLD, false);
    }

    @Override
    public boolean windyorigins$isInMirrorWorld() {
        Entity var1 = (Entity)(Object)this;
        if (var1 instanceof PlayerEntity) {
            if (PowerTypeRegistry.contains(IN_MIRROR_ID)) {
                PowerHolderComponent var2 = PowerHolderComponent.KEY.get(var1);
                if (var2 != null) {
                    return var2.hasPower(PowerTypeRegistry.get(IN_MIRROR_ID));
                }
            }

            return this.getDataTracker().get(IN_MIRROR_WORLD);
        } else {
            return this.getDataTracker().get(IN_MIRROR_WORLD);
        }
    }

    @Override
    public void windyorigins$setInMirrorWorld(boolean var1) {
        if (!((Entity)(Object)(Object)this instanceof PlayerEntity)) {
            this.getDataTracker().set(IN_MIRROR_WORLD, var1);
        }
    }

    @Inject(
        method = {"collidesWith"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void onCollidesWith(Entity var1, CallbackInfoReturnable<Boolean> var2) {
        if (this.windyorigins$isInMirrorWorld() != ((MirrorEntity)var1).windyorigins$isInMirrorWorld()) {
            var2.setReturnValue(false);
        }
    }

    @Inject(
        method = {"damage"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void windyorigins$blockCrossPhaseDamage(DamageSource var1, float var2, CallbackInfoReturnable<Boolean> var3) {
        Entity var4 = var1.getAttacker();
        if (var4 != null && this.windyorigins$isInMirrorWorld() != ((MirrorEntity)var4).windyorigins$isInMirrorWorld()) {
            var3.setReturnValue(false);
        }
    }

    @Inject(
        method = {"pushAwayFrom"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void onPushAwayFrom(Entity var1, CallbackInfo var2) {
        if (this.windyorigins$isInMirrorWorld() != ((MirrorEntity)var1).windyorigins$isInMirrorWorld()) {
            var2.cancel();
        }
    }

    @Inject(
        method = {"tick"},
        at = {@At("RETURN")}
    )
    private void onTickTail(CallbackInfo var1) {
        Entity var2 = (Entity)(Object)this;
        if (!var2.getWorld().isClient()) {
            Entity var3 = var2.getVehicle();
            if (var3 != null && this.windyorigins$isInMirrorWorld() != ((MirrorEntity)var3).windyorigins$isInMirrorWorld()) {
                var2.stopRiding();
            }
        }

        if (var2 instanceof PlayerEntity) {
            boolean var4 = this.windyorigins$isInMirrorWorld();
            if (this.getDataTracker().get(IN_MIRROR_WORLD) != var4) {
                this.getDataTracker().set(IN_MIRROR_WORLD, var4);
            }
        }
    }

    @Inject(
        method = {"writeNbt"},
        at = {@At("RETURN")}
    )
    private void onWriteNbt(NbtCompound var1, CallbackInfoReturnable<NbtCompound> var2) {
        var1.putBoolean("WindyOrigins_IsMirror", this.getDataTracker().get(IN_MIRROR_WORLD));
    }

    @Inject(
        method = {"readNbt"},
        at = {@At("RETURN")}
    )
    private void onReadNbt(NbtCompound var1, CallbackInfo var2) {
        if (var1.contains("WindyOrigins_IsMirror")) {
            this.getDataTracker().set(IN_MIRROR_WORLD, var1.getBoolean("WindyOrigins_IsMirror"));
        }
    }
}
