package com.windykindy49.windyorigins.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({LivingEntity.class})
public interface LivingEntityAccessor {
    @Accessor("lastAttackedTicks")
    int windyorigins$getLastAttackedTicks();

    @Accessor("lastAttackedTicks")
    void windyorigins$setLastAttackedTicks(int var1);
}
