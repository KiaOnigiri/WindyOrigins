package com.windykindy49.windyorigins.frost;

import com.windykindy49.windyorigins.common.PhaseFx;
import com.windykindy49.windyorigins.common.Powers;
import com.windykindy49.windyorigins.common.TempBlocks;
import com.windykindy49.windyorigins.common.WindyEffects;
import com.windykindy49.windyorigins.common.WindySounds;
import com.windykindy49.windyorigins.util.SpawnPhaseOverride;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class FrostManager {
    public static final String CHILL = "chill";
    public static final int CHILL_MAX = 100;
    public static final int COFFIN_TICKS = 100;
    public static final int COFFIN_MIN_CHILL = 20;
    public static final int WALL_COST = 30;
    private static final int WALL_TICKS = 300;
    private static final Map<UUID, Entity> COFFINS = new HashMap<>();

    private FrostManager() {
    }

    public static int chill(Entity var0) {
        return Powers.resource(var0, "chill", 0);
    }

    public static void tick(MinecraftServer var0) {
        for (ServerPlayerEntity var2 : var0.getPlayerManager().getPlayerList()) {
            tickCoffin(var2);
            if (Powers.has(var2, "chill") && !var2.isSpectator()) {
                if (var2.age % 20 == 0) {
                    updateChill(var2);
                }

                if ((var2.age % 2 == 0 || var2.hasVehicle()) && Powers.has(var2, "frost_walk")) {
                    frostWalk(var2);
                }

                if (var2.age % 10 == 0 && Powers.has(var2, "frost_aura")) {
                    aura(var2);
                }

                if (var2.age % 10 == 5 && Powers.has(var2, "permafrost")) {
                    permafrost(var2);
                }

                int var3 = chill(var2);
                if (var3 >= 70 && var2.age % 6 == 0) {
                    PhaseFx.particles(var2, ParticleTypes.SNOWFLAKE, var2.getX(), var2.getBodyY(0.6), var2.getZ(), 1, 0.5, 0.6, 0.5, 0.01);
                }
            }
        }
    }

    private static void updateChill(ServerPlayerEntity var0) {
        World var1 = var0.getWorld();
        BlockPos var2 = var0.getBlockPos();
        int var3;
        if (var1.getDimension().ultrawarm()) {
            var3 = -6;
        } else {
            float var4 = var1.getBiome(var2).value().getTemperature();
            if (var4 < 0.15F) {
                var3 = 3;
            } else if (var4 >= 1.0F) {
                var3 = -2;
            } else {
                int var5 = chill(var0);
                var3 = var5 > 40 ? -1 : (var5 < 40 ? 1 : 0);
            }
        }

        BlockState var6 = var1.getBlockState(var2.down());
        BlockState var7 = var1.getBlockState(var2);
        if (var6.isIn(BlockTags.ICE) || var6.isIn(BlockTags.SNOW) || var7.isOf(Blocks.SNOW)) {
            var3 += 2;
        }

        if (var0.inPowderSnow) {
            var3 += 3;
        }

        if (var0.isOnFire()) {
            var3 -= 6;
        }

        if (var0.isInLava()) {
            var3 -= 15;
        }

        if (nearHeat(var1, var2)) {
            var3 -= 3;
        }

        int var8 = MathHelper.clamp(chill(var0) + var3, 0, 100);
        if (var8 <= 0 && burnSnow(var0)) {
            var8 = 1;
        }

        Powers.setResource(var0, "chill", var8);
    }

    /** How long each thing keeps the last of the cold in, in ticks. Five stacks of snow blocks last ten minutes. */
    private static final net.minecraft.item.Item[] SNOW_ITEMS = new net.minecraft.item.Item[]{
        net.minecraft.item.Items.SNOWBALL,
        net.minecraft.item.Items.SNOW,
        net.minecraft.item.Items.SNOW_BLOCK,
        net.minecraft.item.Items.ICE,
        net.minecraft.item.Items.PACKED_ICE,
        net.minecraft.item.Items.POWDER_SNOW_BUCKET,
        net.minecraft.item.Items.BLUE_ICE
    };
    private static final int[] SNOW_TICKS = new int[]{10, 19, 38, 38, 76, 76, 152};
    private static final Map<UUID, Integer> SNOW_FUEL = new HashMap<>();

    /**
     * With no Chill left the Frostborn melts whatever snow and ice is being carried instead, cheapest
     * first. Returns false once there is nothing left to melt.
     */
    private static boolean burnSnow(ServerPlayerEntity player) {
        int fuel = SNOW_FUEL.getOrDefault(player.getUuid(), 0);
        if (fuel <= 0 && !player.isCreative()) {
            net.minecraft.entity.player.PlayerInventory inventory = player.getInventory();

            search:
            for (int kind = 0; kind < SNOW_ITEMS.length; kind++) {
                for (int slot = 0; slot < inventory.size(); slot++) {
                    net.minecraft.item.ItemStack stack = inventory.getStack(slot);
                    if (stack.isOf(SNOW_ITEMS[kind])) {
                        if (stack.isOf(net.minecraft.item.Items.POWDER_SNOW_BUCKET)) {
                            inventory.setStack(slot, new net.minecraft.item.ItemStack(net.minecraft.item.Items.BUCKET));
                        } else {
                            stack.decrement(1);
                        }

                        fuel += SNOW_TICKS[kind];
                        PhaseFx.particles(player, ParticleTypes.SNOWFLAKE, player.getX(), player.getBodyY(0.6), player.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
                        PhaseFx.privateSound(player, net.minecraft.sound.SoundEvents.BLOCK_SNOW_BREAK, 0.35F, 1.5F);
                        break search;
                    }
                }
            }
        }

        if (fuel <= 0 && !player.isCreative()) {
            SNOW_FUEL.remove(player.getUuid());
            return false;
        }

        // Checked once a second.
        SNOW_FUEL.put(player.getUuid(), fuel - 20);
        return true;
    }

    private static boolean nearHeat(World var0, BlockPos var1) {
        for (BlockPos var3 : BlockPos.iterate(var1.add(-3, -2, -3), var1.add(3, 2, 3))) {
            BlockState var4 = var0.getBlockState(var3);
            if (var4.isIn(BlockTags.FIRE)
                || var4.isOf(Blocks.LAVA)
                || var4.isOf(Blocks.MAGMA_BLOCK)
                || var4.isIn(BlockTags.CAMPFIRES) && var4.get(CampfireBlock.LIT)) {
                return true;
            }
        }

        return false;
    }

    private static final Map<UUID, Vec3d> RIDE_POS = new HashMap<>();

    private static void frostWalk(ServerPlayerEntity var0) {
        ServerWorld var1 = var0.getServerWorld();
        int var2 = 2 + chill(var0) / 50;
        Entity var3 = var0.getRootVehicle();
        if (var3 == var0 || var3 == null) {
            RIDE_POS.remove(var0.getUuid());
            freezeDisc(var0, var1, var0.getX(), var0.getY(), var0.getZ(), var2, var0.getBoundingBox());
            return;
        }

        // A mount or boat is moved by its rider's client, so the ice has to be laid well ahead of
        // it: at ice speeds a boat covers two blocks a tick and used to outrun the freezing.
        Vec3d var4 = var3.getPos();
        Vec3d var5 = RIDE_POS.put(var0.getUuid(), var4);
        freezeDisc(var0, var1, var4.x, var4.y, var4.z, var2, null);
        if (var5 != null) {
            double var6 = var4.x - var5.x;
            double var8 = var4.z - var5.z;
            double var10 = Math.sqrt(var6 * var6 + var8 * var8);
            if (var10 > 0.05 && var10 < 4.0) {
                double var12 = Math.min(32.0, var10 * 20.0 + 3.0);

                for (double var14 = 1.5; var14 <= var12; var14 += 1.5) {
                    freezeDisc(var0, var1, var4.x + var6 / var10 * var14, var4.y, var4.z + var8 / var10 * var14, 2, null);
                }
            }
        }

        liftOntoIce(var0, var3);
    }

    /**
     * Only the face of the water freezes, and the block right under a body that is in it, which
     * is what keeps a Frostborn from diving. Air above, as Frost Walker has it: "no water above"
     * would let the ice creep down a layer a tick, each layer finding ice over it and no water,
     * and all of that ice then melts block by block on ticks of its own.
     */
    private static boolean surface(ServerWorld var0, BlockPos var1) {
        return var0.getBlockState(var1.up()).isAir();
    }

    private static void freezeDisc(ServerPlayerEntity var0, ServerWorld var1, double var2, double var4, double var6, int var8, Box var9) {
        BlockPos var10 = BlockPos.ofFloored(var2, var4 - 0.2, var6);
        BlockState var11 = Blocks.FROSTED_ICE.getDefaultState();

        for (BlockPos var13 : BlockPos.iterate(var10.add(-var8, -var8, -var8), var10.add(var8, 0, var8))) {
            if (!(var13.getSquaredDistance(var10) > (double)(var8 * var8))) {
                BlockState var14 = var1.getBlockState(var13);
                FluidState var15 = var14.getFluidState();
                if (var14.isOf(Blocks.WATER)
                    && var15.isStill()
                    && var15.getFluid() == Fluids.WATER
                    && (var9 == null ? surface(var1, var13) : !new Box(var13).intersects(var9) && (surface(var1, var13) || new Box(var13.up()).intersects(var9)))) {
                    var1.setBlockState(var13, var11);
                    var1.scheduleBlockTick(var13, Blocks.FROSTED_ICE, MathHelper.nextInt(var0.getRandom(), 60, 120));
                }
            }
        }
    }

    /** A boat that was floating when the water froze would be left embedded in the ice. */
    private static void liftOntoIce(ServerPlayerEntity var0, Entity var1) {
        Box var2 = var1.getBoundingBox();
        int var3 = MathHelper.floor(var2.minY + 0.01);
        if (var2.minY - (double)var3 < 0.01) {
            return;
        }

        World var4 = var1.getWorld();
        boolean var5 = false;

        for (BlockPos var7 : BlockPos.iterate(
            MathHelper.floor(var2.minX + 0.05), var3, MathHelper.floor(var2.minZ + 0.05), MathHelper.floor(var2.maxX - 0.05), var3, MathHelper.floor(var2.maxZ - 0.05)
        )) {
            BlockState var8 = var4.getBlockState(var7);
            if (var8.isOf(Blocks.FROSTED_ICE) || var8.isIn(BlockTags.ICE)) {
                var5 = true;
            } else if (!var8.getCollisionShape(var4, var7).isEmpty()) {
                return;
            }
        }

        if (var5 && var4.isSpaceEmpty(var1, var2.offset(0.0, (double)(var3 + 1) - var2.minY + 0.01, 0.0))) {
            var1.setPosition(var1.getX(), (double)(var3 + 1) + (var1.getY() - var2.minY), var1.getZ());
            var1.setVelocity(var1.getVelocity().multiply(1.0, 0.0, 1.0));
            var0.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.VehicleMoveS2CPacket(var1));
        }
    }

    private static void aura(ServerPlayerEntity var0) {
        double var1 = 2.0 + (double)chill(var0) / 33.0;
        Box var3 = var0.getBoundingBox().expand(var1);

        for (MobEntity var5 : var0.getWorld()
            .getEntitiesByClass(
                MobEntity.class, var3, var1x -> var1x instanceof Monster && var1x.isAlive() && var1x.canFreeze() && PhaseFx.samePhase(var1x, var0)
            )) {
            if (!(var5.squaredDistanceTo(var0) > var1 * var1)) {
                int var6 = var5.getFrozenTicks();
                var5.setFrozenTicks(Math.min(var5.getMinFreezeDamageTicks() + 60, var6 + 30));
                PhaseFx.particles(var0, ParticleTypes.SNOWFLAKE, var5.getX(), var5.getBodyY(0.5), var5.getZ(), 2, 0.3, 0.4, 0.3, 0.0);
            }
        }
    }

    private static void permafrost(ServerPlayerEntity var0) {
        if (var0.isOnGround() && !(var0.getRandom().nextFloat() > 0.35F)) {
            World var1 = var0.getWorld();
            BlockPos var2 = var0.getBlockPos();
            if (!var1.getDimension().ultrawarm() && !(var1.getBiome(var2).value().getTemperature() >= 1.0F)) {
                if (var1.getBlockState(var2.down()).isOf(Blocks.GRASS_BLOCK)
                    && var1.getBlockState(var2).isAir()
                    && Blocks.SNOW.getDefaultState().canPlaceAt(var1, var2)) {
                    var1.setBlockState(var2, Blocks.SNOW.getDefaultState());
                }
            }
        }
    }

    public static void coffin(ServerPlayerEntity var0) {
        int var1 = chill(var0);
        if (var1 >= 20 && !var0.hasStatusEffect(WindyEffects.ICE_COFFIN)) {
            Powers.setResource(var0, "chill", 0);
            var0.addStatusEffect(new StatusEffectInstance(WindyEffects.ICE_COFFIN, 100, 0, false, false, true));
            var0.setVelocity(Vec3d.ZERO);
            var0.velocityModified = true;
            var0.extinguish();
            raiseCoffin(var0);

            PhaseFx.sound(var0, WindySounds.FROST_COFFIN, SoundCategory.PLAYERS, 1.0F, 1.4F);
            PhaseFx.particles(
                var0,
                new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.ICE.getDefaultState()),
                var0.getX(),
                var0.getBodyY(0.5),
                var0.getZ(),
                40,
                0.4,
                0.8,
                0.4,
                0.1
            );
        } else {
            PhaseFx.privateSound(var0, WindySounds.DENY, 0.6F, 1.2F);
            var0.sendMessage(Text.translatable("message.windyorigins.frost.too_warm", 20).formatted(Formatting.AQUA), true);
        }
    }

    private static void raiseCoffin(ServerPlayerEntity var0) {
        ServerWorld var2 = var0.getServerWorld();
        Boolean var3 = SpawnPhaseOverride.push(PhaseFx.phase(var0));

        try {
            FrostManager.Coffin var4 = new FrostManager.Coffin(var2);
            var4.setPosition(var0.getX(), var0.getY(), var0.getZ());
            var2.spawnEntity(var4);
            COFFINS.put(var0.getUuid(), var4);
        } finally {
            SpawnPhaseOverride.restore(var3);
        }
    }

    private static void tickCoffin(ServerPlayerEntity var0) {
        Entity var1 = COFFINS.get(var0.getUuid());
        boolean var2 = var0.hasStatusEffect(WindyEffects.ICE_COFFIN) && var0.isAlive();
        if (var2 && (var1 == null || var1.isRemoved() || var1.getWorld() != var0.getWorld())) {
            // Still frozen but the ice is gone: the player logged back in mid-coffin or changed worlds.
            if (var1 != null) {
                var1.discard();
            }

            raiseCoffin(var0);
            var1 = COFFINS.get(var0.getUuid());
        }

        if (var2) {
            if (var0.age % 10 == 0) {
                // Ten pulses over the five seconds: five hearts in all.
                var0.heal(1.0F);
            }

            if (var0.age % 4 == 0) {
                PhaseFx.particles(var0, ParticleTypes.SNOWFLAKE, var0.getX(), var0.getBodyY(0.5), var0.getZ(), 2, 0.45, 0.8, 0.45, 0.0);
            }

            if (var1 != null && var1.squaredDistanceTo(var0) > 0.25) {
                var1.setPosition(var0.getX(), var0.getY(), var0.getZ());
            }
        } else {
            if (var1 != null) {
                COFFINS.remove(var0.getUuid());
                var1.discard();
                if (var0.isAlive()) {
                    PhaseFx.sound(var0, WindySounds.FROST_COFFIN_BREAK, SoundCategory.PLAYERS, 1.0F, 1.0F);
                    PhaseFx.particles(
                        var0,
                        new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.ICE.getDefaultState()),
                        var0.getX(),
                        var0.getBodyY(0.5),
                        var0.getZ(),
                        60,
                        0.5,
                        0.9,
                        0.5,
                        0.15
                    );
                }
            }
        }
    }

    /** The server is going away: nothing may be left in the world, and nothing may leak into the next one. */
    public static void reset() {
        for (Entity coffin : COFFINS.values()) {
            coffin.discard();
        }

        COFFINS.clear();
        SNOW_FUEL.clear();
        RIDE_POS.clear();
    }

    public static void forget(ServerPlayerEntity var0) {
        RIDE_POS.remove(var0.getUuid());
        Entity var1 = COFFINS.remove(var0.getUuid());
        if (var1 != null) {
            var1.discard();
        }
    }

    public static void wall(ServerPlayerEntity var0) {
        if (chill(var0) < 30) {
            PhaseFx.privateSound(var0, WindySounds.DENY, 0.6F, 1.2F);
            var0.sendMessage(Text.translatable("message.windyorigins.frost.too_warm", 30).formatted(Formatting.AQUA), true);
        } else {
            ServerWorld var1 = var0.getServerWorld();
            Direction var2 = var0.getHorizontalFacing();
            Direction var3 = var2.rotateYClockwise();
            BlockPos var4 = var0.getBlockPos().offset(var2, 3);
            int var5 = 0;

            for (int var6 = 0; var6 < 3; var6++) {
                for (int var7 = -1; var7 <= 1; var7++) {
                    BlockPos var8 = var4.offset(var3, var7).up(var6);
                    BlockState var9 = var1.getBlockState(var8);
                    if ((var9.isAir() || var9.isReplaceable() && var9.getFluidState().isEmpty())
                        && var1.getOtherEntities(null, new Box(var8)).stream().noneMatch(var0x -> var0x.isCollidable() || var0x instanceof LivingEntity)
                        && TempBlocks.place(var1, var8, Blocks.PACKED_ICE.getDefaultState(), 300 + var0.getRandom().nextInt(20))) {
                        var5++;
                    }
                }
            }

            if (var5 == 0) {
                PhaseFx.privateSound(var0, WindySounds.DENY, 0.6F, 1.2F);
            } else {
                Powers.addResource(var0, "chill", -30);
                var1.playSound(null, var4, WindySounds.FROST_WALL, SoundCategory.BLOCKS, 1.0F, 0.9F);
                var1.spawnParticles(
                    new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.PACKED_ICE.getDefaultState()),
                    (double)var4.getX() + 0.5,
                    (double)var4.getY() + 1.5,
                    (double)var4.getZ() + 0.5,
                    50,
                    1.0,
                    1.0,
                    1.0,
                    0.1
                );
            }
        }
    }

    private static final class Coffin extends BlockDisplayEntity {
        Coffin(World var1) {
            super(EntityType.BLOCK_DISPLAY, var1);
            NbtCompound var2 = new NbtCompound();
            var2.put("block_state", NbtHelper.fromBlockState(Blocks.ICE.getDefaultState()));
            NbtCompound var3 = new NbtCompound();
            var3.put("translation", floats(-0.65F, -0.05F, -0.65F));
            var3.put("scale", floats(1.3F, 2.1F, 1.3F));
            var3.put("left_rotation", floats(0.0F, 0.0F, 0.0F, 1.0F));
            var3.put("right_rotation", floats(0.0F, 0.0F, 0.0F, 1.0F));
            var2.put("transformation", var3);
            this.readCustomDataFromNbt(var2);
        }

        private static NbtList floats(float... var0) {
            NbtList var1 = new NbtList();

            for (float var5 : var0) {
                var1.add(NbtFloat.of(var5));
            }

            return var1;
        }

        @Override
        public boolean shouldSave() {
            return false;
        }
    }
}
