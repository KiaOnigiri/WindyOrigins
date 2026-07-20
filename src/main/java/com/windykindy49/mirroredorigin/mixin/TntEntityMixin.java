package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.world.World;
import com.windykindy49.mirroredorigin.util.MirrorContext;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import com.windykindy49.mirroredorigin.util.PhaseScope;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={TntEntity.class})
public class TntEntityMixin {
    @Inject(method={"tick"}, at={@At(value="HEAD")})
    private void onTickHead(CallbackInfo ci) {
        PhaseScope.enter(((MirrorEntity)(Object)this).mirroredorigin$isInMirrorWorld());
    }

    @Inject(method={"tick"}, at={@At(value="RETURN")})
    private void onTickTail(CallbackInfo ci) {
        PhaseScope.exit();
    }

    @Inject(method={"<init>(Lnet/minecraft/world/World;DDDLnet/minecraft/entity/LivingEntity;)V"}, at={@At(value="TAIL")})
    private void onInit(World world, double x, double y, double z, @Nullable LivingEntity igniter, CallbackInfo ci) {
        if (igniter != null) {
            ((MirrorEntity)(Object)this).mirroredorigin$setInMirrorWorld(((MirrorEntity)igniter).mirroredorigin$isInMirrorWorld());
        } else {
            Boolean ctx = MirrorContext.get();
            if (ctx != null) {
                ((MirrorEntity)(Object)this).mirroredorigin$setInMirrorWorld(ctx);
            }
        }
    }
}

