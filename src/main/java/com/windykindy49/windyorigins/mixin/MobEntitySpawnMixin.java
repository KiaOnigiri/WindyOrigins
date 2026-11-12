package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorContext;
import com.windykindy49.windyorigins.util.MirrorEntity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({MobEntity.class})
public class MobEntitySpawnMixin {
    @Inject(
        method = {"initialize"},
        at = {@At("TAIL")}
    )
    private void onInitialize(
        ServerWorldAccess var1,
        LocalDifficulty var2,
        SpawnReason var3,
        @Nullable EntityData var4,
        @Nullable NbtCompound var5,
        CallbackInfoReturnable<EntityData> var6
    ) {
        Boolean var7;
        if ((var3 == SpawnReason.SPAWN_EGG || var3 == SpawnReason.DISPENSER || var3 == SpawnReason.COMMAND) && (var7 = MirrorContext.get()) != null) {
            ((MirrorEntity)(Object)this).windyorigins$setInMirrorWorld(var7);
        }
    }
}
