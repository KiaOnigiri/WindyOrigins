package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.EntityData;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import com.windykindy49.mirroredorigin.util.MirrorContext;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={MobEntity.class})
public class MobEntitySpawnMixin {
    @Inject(method={"initialize"}, at={@At(value="TAIL")})
    private void onInitialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, @Nullable EntityData entityData, @Nullable NbtCompound entityNbt, CallbackInfoReturnable<EntityData> cir) {
        Boolean phase;
        if ((spawnReason == SpawnReason.SPAWN_EGG || spawnReason == SpawnReason.DISPENSER || spawnReason == SpawnReason.COMMAND) && (phase = MirrorContext.get()) != null) {
            ((MirrorEntity)(Object)this).mirroredorigin$setInMirrorWorld(phase);
        }
    }
}

