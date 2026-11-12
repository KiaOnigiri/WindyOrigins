package com.windykindy49.windyorigins.luminian;

import com.windykindy49.windyorigins.common.PhaseFx;
import com.windykindy49.windyorigins.common.Powers;
import com.windykindy49.windyorigins.common.WindySounds;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LightBlock;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

public final class LuminianManager {
    public static final String LIGHT = "inner_light";
    public static final LuminousAirBlock LUMINOUS_AIR = Registry.register(
        Registries.BLOCK, new Identifier("windyorigins", "luminous_air"), new LuminousAirBlock(FabricBlockSettings.copyOf(Blocks.LIGHT).dropsNothing())
    );
    private static final Map<UUID, LuminianManager.Placed> PLACED = new HashMap<>();

    private LuminianManager() {
    }

    public static void init() {
    }

    public static boolean isLightAt(PlayerEntity var0, BlockPos var1) {
        LuminianManager.Placed var2 = PLACED.get(var0.getUuid());
        return var2 != null && var2.world == var0.getWorld() && var2.pos.equals(var1);
    }

    public static void tick(MinecraftServer var0) {
        for (ServerPlayerEntity var2 : var0.getPlayerManager().getPlayerList()) {
            boolean var3 = Powers.has(var2, "radiance") && var2.isAlive() && !var2.isSpectator();
            if (!var3) {
                remove(var2);
            } else {
                if (var2.age % 20 == 0 && Powers.has(var2, "inner_light")) {
                    updateInnerLight(var2);
                }

                if (var2.age % 2 == 0) {
                    placeLight(var2);
                }

                if (var2.age % 20 == 10 && Powers.has(var2, "undead_hatred")) {
                    drawUndead(var2);
                }

                if (var2.age % 5 == 0 && var2.getVelocity().horizontalLengthSquared() > 0.003 && Powers.resource(var2, "inner_light", 0) > 0) {
                    PhaseFx.particles(var2, ParticleTypes.WAX_OFF, var2.getX(), var2.getBodyY(0.4), var2.getZ(), 1, 0.25, 0.4, 0.25, 0.0);
                }
            }
        }
    }

    private static void updateInnerLight(ServerPlayerEntity var0) {
        World var1 = var0.getWorld();
        BlockPos var2 = BlockPos.ofFloored(var0.getEyePos());
        byte var3;
        if (var1.isDay() && !var1.isRaining() && var1.getLightLevel(LightType.SKY, var2) >= 13 && var1.getDimension().hasSkyLight()) {
            var3 = 3;
        } else if (foreignLight(var0, var2)) {
            var3 = 1;
        } else if (var1.getLightLevel(LightType.SKY, var2) - var1.getAmbientDarkness() >= 8) {
            var3 = 0;
        } else {
            var3 = -1;
        }

        int var4 = Powers.resource(var0, "inner_light", 0);
        Powers.setResource(var0, "inner_light", MathHelper.clamp(var4 + var3, 0, 100));
        if (var4 > 0 && var4 + var3 <= 0) {
            PhaseFx.sound(var0, WindySounds.LUMI_FADE, SoundCategory.PLAYERS, 0.8F, 0.7F);
        }
    }

    private static boolean foreignLight(ServerPlayerEntity var0, BlockPos var1) {
        World var2 = var0.getWorld();

        for (BlockPos var4 : BlockPos.iterate(var1.add(-7, -4, -7), var1.add(7, 4, 7))) {
            BlockState var5 = var2.getBlockState(var4);
            if (var5.getLuminance() >= 10 && !var5.isOf(LUMINOUS_AIR)) {
                return true;
            }
        }

        return false;
    }

    private static void placeLight(ServerPlayerEntity var0) {
        ServerWorld var1 = var0.getServerWorld();
        int var2 = Powers.resource(var0, "inner_light", 100);
        int var3 = var2 <= 0 ? 0 : MathHelper.clamp(6 + var2 * 9 / 100, 6, 15);
        LuminianManager.Placed var4 = PLACED.get(var0.getUuid());
        if (var3 == 0) {
            remove(var0);
        } else {
            BlockPos var5 = pick(var1, BlockPos.ofFloored(var0.getEyePos()), var4);
            if (var5 == null) {
                var5 = pick(var1, var0.getBlockPos(), var4);
            }

            if (var5 != null) {
                if (var4 == null || var4.world != var1 || !var4.pos.equals(var5) || var4.level != var3) {
                    BlockState var6 = var1.getBlockState(var5);
                    boolean var7 = var6.getFluidState().isOf(Fluids.WATER) && var6.getFluidState().isStill()
                        || var6.isOf(LUMINOUS_AIR) && var6.get(LightBlock.WATERLOGGED);
                    var1.setBlockState(
                        var5,
                        LUMINOUS_AIR.getDefaultState().with(LightBlock.LEVEL_15, Integer.valueOf(var3)).with(LightBlock.WATERLOGGED, Boolean.valueOf(var7))
                    );
                    PLACED.put(var0.getUuid(), new LuminianManager.Placed(var1, var5.toImmutable(), var3));
                    if (var4 != null && (var4.world != var1 || !var4.pos.equals(var5))) {
                        clear(var4);
                    }
                }
            }
        }
    }

    private static BlockPos pick(World var0, BlockPos var1, LuminianManager.Placed var2) {
        BlockState var3 = var0.getBlockState(var1);
        if (var3.isAir() || var3.isOf(LUMINOUS_AIR)) {
            return var1;
        } else {
            return var3.isOf(Blocks.WATER) && var3.getFluidState().isStill() ? var1 : null;
        }
    }

    private static void clear(LuminianManager.Placed var0) {
        BlockState var1 = var0.world.getBlockState(var0.pos);
        if (var1.isOf(LUMINOUS_AIR)) {
            var0.world.setBlockState(var0.pos, var1.get(LightBlock.WATERLOGGED) ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState());
        }
    }

    /** Takes back every light that is still standing, so none is saved into the world. */
    public static void reset() {
        for (LuminianManager.Placed placed : new java.util.ArrayList<>(PLACED.values())) {
            clear(placed);
        }

        PLACED.clear();
    }

    public static void remove(PlayerEntity var0) {
        LuminianManager.Placed var1 = PLACED.remove(var0.getUuid());
        if (var1 != null) {
            clear(var1);
        }
    }

    private static void drawUndead(ServerPlayerEntity var0) {
        Box var1 = var0.getBoundingBox().expand(16.0);

        for (HostileEntity var3 : var0.getWorld()
            .getEntitiesByClass(HostileEntity.class, var1, var1x -> var1x.getGroup() == EntityGroup.UNDEAD && var1x.isAlive() && PhaseFx.samePhase(var1x, var0))) {
            LivingEntity var4 = var3.getTarget();
            if ((!(var4 instanceof PlayerEntity) || !Powers.has(var4, "radiance")) && var3.canSee(var0) && !var0.isCreative()) {
                var3.setTarget(var0);
            }
        }
    }

    public static void revive(ServerPlayerEntity var0) {
        var0.setHealth(var0.getMaxHealth() / 2.0F);
        var0.clearStatusEffects();
        var0.extinguish();
        var0.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 100, 0));
        Box var1 = var0.getBoundingBox().expand(8.0);

        for (LivingEntity var3 : var0.getWorld()
            .getEntitiesByClass(LivingEntity.class, var1, var1x -> var1x != var0 && var1x.isAlive() && PhaseFx.samePhase(var1x, var0))) {
            if (var3.getGroup() == EntityGroup.UNDEAD) {
                var3.setOnFireFor(8);
            } else {
                var3.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0), var0);
            }
        }

        PhaseFx.particles(var0, ParticleTypes.FLASH, var0.getX(), var0.getBodyY(0.5), var0.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        PhaseFx.particles(var0, ParticleTypes.END_ROD, var0.getX(), var0.getBodyY(0.5), var0.getZ(), 80, 0.3, 0.6, 0.3, 0.35);
        PhaseFx.particles(var0, ParticleTypes.TOTEM_OF_UNDYING, var0.getX(), var0.getBodyY(0.5), var0.getZ(), 40, 0.5, 0.8, 0.5, 0.4);
        PhaseFx.sound(var0, WindySounds.LUMI_REVIVE, SoundCategory.PLAYERS, 1.2F, 1.1F);
    }

    private static record Placed(World world, BlockPos pos, int level) {
    }
}
