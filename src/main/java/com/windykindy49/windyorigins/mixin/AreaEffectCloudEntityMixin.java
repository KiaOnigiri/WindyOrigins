package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import com.windykindy49.windyorigins.util.SidedBridge;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({AreaEffectCloudEntity.class})
public class AreaEffectCloudEntityMixin {
    @Inject(
        method = {"tick"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void onTick(CallbackInfo var1) {
        if (((AreaEffectCloudEntity)(Object)this).getWorld().isClient()) {
            Entity var2 = SidedBridge.camera();
            if (var2 != null && ((MirrorEntity)(Object)this).windyorigins$isInMirrorWorld() != ((MirrorEntity)var2).windyorigins$isInMirrorWorld()) {
                var1.cancel();
            }
        }
    }

    @Inject(
        method = {"setOwner"},
        at = {@At("RETURN")}
    )
    private void onSetOwner(LivingEntity var1, CallbackInfo var2) {
        if (var1 != null) {
            ((MirrorEntity)(Object)this).windyorigins$setInMirrorWorld(((MirrorEntity)var1).windyorigins$isInMirrorWorld());
        }
    }

    @Redirect(
        method = {"tick"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;getNonSpectatingEntities(Ljava/lang/Class;Lnet/minecraft/util/math/Box;)Ljava/util/List;"
        )
    )
    private List<LivingEntity> filterCloudTargets(World var1, Class<? extends LivingEntity> var2, Box var3) {
        List var4 = var1.getNonSpectatingEntities(var2, var3);
        ArrayList var5 = new ArrayList(var4);
        boolean var6 = ((MirrorEntity)(Object)this).windyorigins$isInMirrorWorld();
        var5.removeIf(var1x -> ((MirrorEntity)var1x).windyorigins$isInMirrorWorld() != var6);
        return var5;
    }
}
