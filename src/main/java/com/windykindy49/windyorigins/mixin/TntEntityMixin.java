package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorContext;
import com.windykindy49.windyorigins.util.MirrorEntity;
import com.windykindy49.windyorigins.util.PhaseScope;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({TntEntity.class})
public class TntEntityMixin {
    @Inject(
        method = {"tick"},
        at = {@At("HEAD")}
    )
    private void onTickHead(CallbackInfo var1) {
        PhaseScope.enter(((MirrorEntity)(Object)this).windyorigins$isInMirrorWorld());
    }

    @Inject(
        method = {"tick"},
        at = {@At("RETURN")}
    )
    private void onTickTail(CallbackInfo var1) {
        PhaseScope.exit();
    }

    @Inject(
        method = {"<init>(Lnet/minecraft/world/World;DDDLnet/minecraft/entity/LivingEntity;)V"},
        at = {@At("TAIL")}
    )
    private void onInit(World var1, double var2, double var4, double var6, @Nullable LivingEntity var8, CallbackInfo var9) {
        if (var8 != null) {
            ((MirrorEntity)(Object)this).windyorigins$setInMirrorWorld(((MirrorEntity)var8).windyorigins$isInMirrorWorld());
        } else {
            Boolean var10 = MirrorContext.get();
            if (var10 != null) {
                ((MirrorEntity)(Object)this).windyorigins$setInMirrorWorld(var10);
            }
        }
    }
}
