package com.windykindy49.windyorigins.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({Entity.class})
public interface EntityFlagAccessor {
    @Invoker("getFlag")
    boolean windyorigins$getFlag(int var1);
}
