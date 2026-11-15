package com.windykindy49.windyorigins.revenant;

import com.windykindy49.windyorigins.common.MobGroups;
import com.windykindy49.windyorigins.common.PhaseFx;
import com.windykindy49.windyorigins.common.Powers;
import com.windykindy49.windyorigins.common.WindySounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * The Revenant's wisps: three small spirits that are sent out on errands one at a time. A wisp can
 * dig out a vein, fetch what lies on the ground, tend a friend or torment an enemy, and comes back
 * with whatever it gathered before resting a moment.
 */
public final class WispManager {
    public static final int COST = 3;
    public static final int COUNT = 3;
    public static final int LIFETIME = 2400;
    public static final double REACH = 24.0;
    private static final int NAP_TICKS = 100;
    private static final int VEIN = 6;
    private static final int AID_TICKS = 400;
    private static final int HAUNT_TICKS = 300;
    /** No more than two wisps may torment one player at a time. */
    private static final int MAX_ON_PLAYER = 2;
    private static final int GATHER_TICKS = 300;
    private static final double GATHER_RADIUS = 16.0;
    private static final double LEASH = 56.0;
    private static final ItemStack[] TOOLS = new ItemStack[]{
        new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.IRON_AXE), new ItemStack(Items.IRON_SHOVEL), new ItemStack(Items.IRON_HOE)
    };
    private static final Map<UUID, Squad> SQUADS = new HashMap<>();
    private static int nextBreakerId = -90000;

    private WispManager() {
    }

    private enum State {
        ORBIT,
        TO_BLOCK,
        MINING,
        AID,
        HAUNT,
        GATHER,
        RETURN
    }

    private static final class Wisp {
        final int index;
        final int breakerId = nextBreakerId--;
        Vec3d pos;
        State state = State.ORBIT;
        int timer;
        int nap;
        int veinLeft;
        float mineTotal;
        BlockPos block;
        Block blockType;
        UUID target;
        Entity quarry;
        int echoTarget = -1;
        final List<ItemStack> cargo = new ArrayList<>();
        int xp;
        int echoes;

        Wisp(int index, Vec3d pos) {
            this.index = index;
            this.pos = pos;
        }
    }

    private static final class Squad {
        final ServerWorld world;
        final long expires;
        final List<Wisp> wisps = new ArrayList<>();

        Squad(ServerWorld world, long expires) {
            this.world = world;
            this.expires = expires;
        }
    }

    /** How many wisps, from every Revenant, are tormenting this creature right now. */
    private static int haunting(UUID target) {
        int n = 0;

        for (Squad squad : SQUADS.values()) {
            for (Wisp w : squad.wisps) {
                if (w.state == State.HAUNT && target.equals(w.target)) {
                    n++;
                }
            }
        }

        return n;
    }

    public static boolean has(ServerPlayerEntity player) {
        return SQUADS.containsKey(player.getUuid());
    }

    // ------------------------------------------------------------------ orders

    public static void command(ServerPlayerEntity player) {
        if (RevenantManager.isGhost(player) || player.isSpectator()) {
            deny(player, null);
            return;
        }

        Squad squad = SQUADS.get(player.getUuid());
        if (squad == null) {
            if (Powers.resource(player, "echo", 0) < COST) {
                deny(player, Text.translatable("message.windyorigins.revenant.no_echo", COST));
                return;
            }

            Powers.addResource(player, "echo", -COST);
            squad = new Squad(player.getWorld(), player.getWorld().getTime() + LIFETIME);

            for (int i = 0; i < COUNT; i++) {
                squad.wisps.add(new Wisp(i, player.getPos().add(0.0, 1.0, 0.0)));
            }

            SQUADS.put(player.getUuid(), squad);
            PhaseFx.sound(player, WindySounds.REV_WISPS, SoundCategory.PLAYERS, 1.0F, 1.0F);
            say(player, "summoned");
            return;
        }

        Wisp wisp = null;

        for (Wisp w : squad.wisps) {
            if (w.state == State.ORBIT && w.nap <= 0) {
                wisp = w;
                break;
            }
        }

        if (wisp == null) {
            deny(player, Text.translatable("message.windyorigins.revenant.wisp.busy"));
            return;
        }

        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0F);
        Vec3d end = eye.add(look.multiply(REACH));
        BlockHitResult blockHit = player.getWorld()
            .raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
        Vec3d limit = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getPos();
        EntityHitResult entityHit = ProjectileUtil.raycast(
            player,
            eye,
            limit,
            new Box(eye, limit).expand(1.0),
            e -> e instanceof LivingEntity && !(e instanceof ArmorStandEntity) && e != player && e.isAlive() && !e.isSpectator(),
            eye.squaredDistanceTo(limit)
        );
        if (entityHit != null) {
            LivingEntity target = (LivingEntity)entityHit.getEntity();
            if (MobGroups.of(target) == MobGroups.Group.BOSS) {
                deny(player, Text.translatable("message.windyorigins.revenant.wisp.refuse"));
                return;
            }

            boolean own = target instanceof TameableEntity pet && player.getUuid().equals(pet.getOwnerUuid());
            boolean hostile = !own
                && (target instanceof Monster || target instanceof MobEntity mob && mob.getTarget() == player || player.isSneaking());
            if (hostile && target instanceof PlayerEntity && haunting(target.getUuid()) >= MAX_ON_PLAYER) {
                deny(player, Text.translatable("message.windyorigins.revenant.wisp.crowded"));
                return;
            }

            wisp.target = target.getUuid();
            wisp.state = hostile ? State.HAUNT : State.AID;
            wisp.timer = hostile ? HAUNT_TICKS : AID_TICKS;
            say(player, hostile ? "haunt" : "aid");
        } else if (blockHit.getType() != HitResult.Type.MISS) {
            BlockPos pos = blockHit.getBlockPos();
            BlockState state = player.getWorld().getBlockState(pos);
            if (toolFor(player.getWorld(), pos, state) == null || !mayBreak(player, pos, state)) {
                deny(player, Text.translatable("message.windyorigins.revenant.wisp.refuse"));
                return;
            }

            wisp.block = pos.toImmutable();
            wisp.blockType = state.getBlock();
            wisp.veinLeft = VEIN;
            wisp.state = State.TO_BLOCK;
            say(player, "mine");
        } else if (player.isSneaking()) {
            wisp.target = player.getUuid();
            wisp.state = State.AID;
            wisp.timer = AID_TICKS;
            say(player, "aid_self");
        } else {
            wisp.state = State.GATHER;
            wisp.timer = GATHER_TICKS;
            wisp.quarry = null;
            wisp.echoTarget = -1;
            say(player, "gather");
        }

        PhaseFx.sound(player, WindySounds.REV_WHISPER, SoundCategory.PLAYERS, 0.7F, 1.5F);
    }

    private static void say(ServerPlayerEntity player, String key) {
        player.sendMessage(Text.translatable("message.windyorigins.revenant.wisp." + key).formatted(Formatting.AQUA), true);
    }

    private static void deny(ServerPlayerEntity player, Text text) {
        PhaseFx.privateSound(player, WindySounds.DENY, 0.6F, 1.0F);
        if (text != null) {
            player.sendMessage(text.copy().formatted(Formatting.DARK_AQUA), true);
        }
    }

    /** The iron tool a wisp would need for this block, or null when the block is beyond it. */
    private static ItemStack toolFor(ServerWorld world, BlockPos pos, BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty() && state.getCollisionShape(world, pos).isEmpty() || state.hasBlockEntity()) {
            return null;
        }

        float hardness = state.getHardness(world, pos);
        if (hardness < 0.0F || hardness > 5.0F) {
            return null;
        }

        for (ItemStack tool : TOOLS) {
            if (tool.isSuitableFor(state)) {
                return tool;
            }
        }

        return state.isToolRequired() ? null : ItemStack.EMPTY;
    }

    private static boolean mayBreak(ServerPlayerEntity player, BlockPos pos, BlockState state) {
        return player.getWorld().canPlayerModifyAt(player, pos)
            && !player.isBlockBreakingRestricted(player.getWorld(), pos, player.interactionManager.getGameMode())
            && PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(player.getWorld(), player, pos, state, null);
    }

    // ------------------------------------------------------------------ ticking

    public static void tick(ServerPlayerEntity player) {
        Squad squad = SQUADS.get(player.getUuid());
        if (squad == null) {
            return;
        }

        long now = player.getWorld().getTime();
        if (!player.isAlive() || RevenantManager.isGhost(player) || squad.world != player.getWorld() || squad.expires < now || !RevenantManager.isRevenant(player)) {
            dismiss(player);
            return;
        }

        if (player.age % 5 == 0) {
            Powers.setResource(player, "wisps", (int)(squad.expires - now));
        }

        List<Integer> haunted = new ArrayList<>();

        for (Wisp wisp : squad.wisps) {
            tickWisp(player, squad, wisp, haunted);
        }

        if (player.age % 10 == 0) {
            RevenantNetworking.marks(player, haunted.stream().mapToInt(Integer::intValue).toArray());
        }
    }

    private static Vec3d orbit(ServerPlayerEntity player, Wisp wisp) {
        double angle = (double)player.age * 0.12 + (double)wisp.index * Math.PI * 2.0 / (double)COUNT;
        return new Vec3d(player.getX() + Math.cos(angle) * 1.1, player.getY() + 1.7 + Math.sin(angle * 2.0) * 0.15, player.getZ() + Math.sin(angle) * 1.1);
    }

    /** Wisps are spirits: they drift straight to where they are going, walls or no walls. */
    private static boolean fly(Wisp wisp, Vec3d to, double speed) {
        Vec3d delta = to.subtract(wisp.pos);
        double distance = delta.length();
        if (distance <= speed) {
            wisp.pos = to;
            return true;
        }

        wisp.pos = wisp.pos.add(delta.multiply(speed / distance));
        return false;
    }

    private static void tickWisp(ServerPlayerEntity player, Squad squad, Wisp wisp, List<Integer> haunted) {
        ServerWorld world = squad.world;
        if (wisp.state != State.ORBIT && wisp.pos.squaredDistanceTo(player.getPos()) > LEASH * LEASH) {
            stopMining(world, wisp);
            wisp.state = State.RETURN;
        }

        switch (wisp.state) {
            case ORBIT -> {
                Vec3d home = orbit(player, wisp);
                if (wisp.pos.squaredDistanceTo(home) > 9.0) {
                    fly(wisp, home, 0.9);
                } else {
                    wisp.pos = home;
                }

                if (wisp.nap > 0) {
                    wisp.nap--;
                }
            }
            case TO_BLOCK -> {
                if (fly(wisp, Vec3d.ofCenter(wisp.block), 0.55)) {
                    BlockState state = world.getBlockState(wisp.block);
                    if (state.getBlock() != wisp.blockType || toolFor(world, wisp.block, state) == null) {
                        wisp.state = State.RETURN;
                    } else {
                        wisp.state = State.MINING;
                        wisp.mineTotal = MathHelper.clamp(state.getHardness(world, wisp.block) * 25.0F, 8.0F, 110.0F);
                        wisp.timer = 0;
                    }
                }
            }
            case MINING -> mine(player, world, wisp);
            case AID -> {
                Entity entity = world.getEntity(wisp.target);
                if (!(entity instanceof LivingEntity target) || !target.isAlive() || --wisp.timer <= 0 || !PhaseFx.samePhase(player, target)) {
                    wisp.state = State.RETURN;
                } else {
                    hover(wisp, target, player.age);
                    if (wisp.timer % 40 == 0) {
                        target.heal(1.0F);
                        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 60, 0, true, false, true));
                        PhaseFx.particles(player, ParticleTypes.SOUL, target.getX(), target.getBodyY(0.6), target.getZ(), 3, 0.2, 0.3, 0.2, 0.01);
                    }
                }
            }
            case HAUNT -> {
                Entity entity = world.getEntity(wisp.target);
                if (!(entity instanceof LivingEntity target) || !target.isAlive() || --wisp.timer <= 0 || !PhaseFx.samePhase(player, target)) {
                    wisp.state = State.RETURN;
                } else {
                    hover(wisp, target, player.age);
                    haunted.add(target.getId());
                    if (wisp.timer % 20 == 0) {
                        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 0, true, false, true), player);
                        target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 30, 0, true, false, true), player);
                    }

                    if (wisp.timer % 40 == 0) {
                        target.damage(world.getDamageSources().indirectMagic(player, player), 2.0F);
                        PhaseFx.particles(player, ParticleTypes.SCULK_SOUL, target.getX(), target.getBodyY(0.6), target.getZ(), 4, 0.2, 0.3, 0.2, 0.02);
                    }
                }
            }
            case GATHER -> gather(player, world, wisp);
            case RETURN -> {
                if (fly(wisp, player.getPos().add(0.0, 1.0, 0.0), 0.75)) {
                    deliver(player, wisp);
                    wisp.state = State.ORBIT;
                    wisp.nap = NAP_TICKS;
                }
            }
        }

        boolean resting = wisp.state == State.ORBIT && wisp.nap > 0;
        if (!resting || player.age % 3 == 0) {
            PhaseFx.particles(player, resting ? ParticleTypes.SOUL : ParticleTypes.SOUL_FIRE_FLAME, wisp.pos.x, wisp.pos.y, wisp.pos.z, 1, 0.0, 0.0, 0.0, 0.0);
        }

        if (wisp.state != State.ORBIT && player.age % 4 == 0) {
            PhaseFx.particles(player, ParticleTypes.SCULK_SOUL, wisp.pos.x, wisp.pos.y, wisp.pos.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    private static void hover(Wisp wisp, LivingEntity target, int age) {
        double angle = (double)age * 0.2 + (double)wisp.index * 2.1;
        double radius = (double)target.getWidth() * 0.5 + 0.5;
        fly(
            wisp,
            new Vec3d(target.getX() + Math.cos(angle) * radius, target.getY() + (double)target.getHeight() + 0.3, target.getZ() + Math.sin(angle) * radius),
            0.8
        );
    }

    private static void stopMining(ServerWorld world, Wisp wisp) {
        if (wisp.state == State.MINING && wisp.block != null) {
            world.setBlockBreakingInfo(wisp.breakerId, wisp.block, -1);
        }
    }

    private static void mine(ServerPlayerEntity player, ServerWorld world, Wisp wisp) {
        BlockPos pos = wisp.block;
        BlockState state = world.getBlockState(pos);
        ItemStack tool = toolFor(world, pos, state);
        if (state.getBlock() != wisp.blockType || tool == null) {
            world.setBlockBreakingInfo(wisp.breakerId, pos, -1);
            wisp.state = State.RETURN;
            return;
        }

        wisp.timer++;
        if (wisp.timer % 4 == 0) {
            world.setBlockBreakingInfo(wisp.breakerId, pos, Math.min(9, (int)((float)wisp.timer / wisp.mineTotal * 10.0F)));
            PhaseFx.particles(player, new BlockStateParticleEffect(ParticleTypes.BLOCK, state), wisp.pos.x, wisp.pos.y, wisp.pos.z, 4, 0.3, 0.3, 0.3, 0.05);
            PhaseFx.sound(
                player, wisp.pos.x, wisp.pos.y, wisp.pos.z, state.getSoundGroup().getHitSound(), SoundCategory.BLOCKS, 0.35F, state.getSoundGroup().getPitch() * 0.7F
            );
        }

        if ((float)wisp.timer < wisp.mineTotal) {
            return;
        }

        world.setBlockBreakingInfo(wisp.breakerId, pos, -1);
        if (!mayBreak(player, pos, state)) {
            wisp.state = State.RETURN;
            return;
        }

        for (ItemStack drop : Block.getDroppedStacks(state, world, pos, null, player, tool)) {
            stow(wisp, drop);
        }

        state.onStacksDropped(world, pos, tool, true);
        world.breakBlock(pos, false, player);
        wisp.veinLeft--;
        BlockPos next = null;
        if (wisp.veinLeft > 0) {
            double best = Double.MAX_VALUE;

            for (BlockPos near : BlockPos.iterate(pos.add(-1, -1, -1), pos.add(1, 1, 1))) {
                if (world.getBlockState(near).getBlock() == wisp.blockType) {
                    double distance = near.getSquaredDistance(pos);
                    if (distance < best) {
                        best = distance;
                        next = near.toImmutable();
                    }
                }
            }
        }

        if (next != null) {
            wisp.block = next;
            wisp.state = State.TO_BLOCK;
        } else {
            wisp.state = State.RETURN;
        }
    }

    private static void stow(Wisp wisp, ItemStack stack) {
        for (ItemStack held : wisp.cargo) {
            if (ItemStack.canCombine(held, stack) && held.getCount() < held.getMaxCount()) {
                int moved = Math.min(stack.getCount(), held.getMaxCount() - held.getCount());
                held.increment(moved);
                stack.decrement(moved);
                if (stack.isEmpty()) {
                    return;
                }
            }
        }

        if (!stack.isEmpty()) {
            wisp.cargo.add(stack.copy());
        }
    }

    private static void gather(ServerPlayerEntity player, ServerWorld world, Wisp wisp) {
        if (--wisp.timer <= 0 || wisp.cargo.size() >= 9) {
            wisp.state = State.RETURN;
            return;
        }

        if (wisp.echoTarget >= 0) {
            RevenantManager.Echo echo = RevenantManager.echo(wisp.echoTarget);
            if (echo == null) {
                wisp.echoTarget = -1;
            } else if (fly(wisp, echo.pos(), 0.6)) {
                if (RevenantManager.takeEcho(echo)) {
                    wisp.echoes++;
                    PhaseFx.particles(player, ParticleTypes.SOUL, wisp.pos.x, wisp.pos.y, wisp.pos.z, 8, 0.2, 0.2, 0.2, 0.04);
                }

                wisp.echoTarget = -1;
            }

            return;
        }

        if (wisp.quarry != null) {
            if (!wisp.quarry.isAlive()) {
                wisp.quarry = null;
            } else if (fly(wisp, wisp.quarry.getPos().add(0.0, 0.25, 0.0), 0.6)) {
                if (wisp.quarry instanceof ItemEntity item) {
                    stow(wisp, item.getStack().copy());
                    item.discard();
                } else if (wisp.quarry instanceof ExperienceOrbEntity orb) {
                    wisp.xp += orb.getExperienceAmount();
                    orb.discard();
                }

                PhaseFx.sound(player, wisp.pos.x, wisp.pos.y, wisp.pos.z, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.3F, 1.6F);
                wisp.quarry = null;
            }

            return;
        }

        if (player.age % 5 != 0) {
            return;
        }

        Squad squad = SQUADS.get(player.getUuid());
        Box area = player.getBoundingBox().expand(GATHER_RADIUS);
        double best = Double.MAX_VALUE;
        if (Powers.resource(player, "echo", 0) + wisp.echoes < RevenantManager.ECHO_MAX) {
            for (RevenantManager.Echo echo : RevenantManager.echoesNear(player, GATHER_RADIUS + 8.0)) {
                if (!claimed(squad, wisp, null, echo.id())) {
                    double distance = echo.pos().squaredDistanceTo(wisp.pos);
                    if (distance < best) {
                        best = distance;
                        wisp.echoTarget = echo.id();
                    }
                }
            }

            if (wisp.echoTarget >= 0) {
                return;
            }
        }

        for (Entity entity : world.getOtherEntities(
            null, area, e -> (e instanceof ItemEntity || e instanceof ExperienceOrbEntity) && e.isAlive() && PhaseFx.samePhase(e, player)
        )) {
            if (entity instanceof ItemEntity item && item.cannotPickup() || claimed(squad, wisp, entity, -1)) {
                continue;
            }

            double distance = entity.squaredDistanceTo(wisp.pos);
            if (distance < best) {
                best = distance;
                wisp.quarry = entity;
            }
        }

        // Nothing left lying about: come home rather than idle out the whole errand.
        if (wisp.quarry == null) {
            wisp.timer = Math.min(wisp.timer, 30);
        }
    }

    private static boolean claimed(Squad squad, Wisp self, Entity entity, int echoId) {
        if (squad == null) {
            return false;
        }

        for (Wisp other : squad.wisps) {
            if (other != self && (entity != null && other.quarry == entity || echoId >= 0 && other.echoTarget == echoId)) {
                return true;
            }
        }

        return false;
    }

    private static void deliver(ServerPlayerEntity player, Wisp wisp) {
        boolean any = !wisp.cargo.isEmpty() || wisp.xp > 0 || wisp.echoes > 0;

        for (ItemStack stack : wisp.cargo) {
            if (!player.getInventory().insertStack(stack) && !stack.isEmpty()) {
                player.dropItem(stack, false);
            }
        }

        wisp.cargo.clear();
        if (wisp.xp > 0) {
            player.addExperience(wisp.xp);
            wisp.xp = 0;
        }

        if (wisp.echoes > 0) {
            Powers.setResource(player, "echo", Math.min(RevenantManager.ECHO_MAX, Powers.resource(player, "echo", 0) + wisp.echoes));
            PhaseFx.privateSound(player, WindySounds.REV_ECHO, 0.8F, 1.2F);
            wisp.echoes = 0;
        }

        if (any) {
            PhaseFx.privateSound(player, SoundEvents.ENTITY_ITEM_PICKUP, 0.4F, 1.2F);
            player.currentScreenHandler.sendContentUpdates();
        }
    }

    /** Sends the wisps away; whatever they still carry is handed over or left where they were. */
    public static void dismiss(ServerPlayerEntity player) {
        Squad squad = SQUADS.remove(player.getUuid());
        if (squad == null) {
            return;
        }

        for (Wisp wisp : squad.wisps) {
            stopMining(squad.world, wisp);
            if (player.isAlive() && !player.isRemoved() && player.getWorld() == squad.world && !RevenantManager.isGhost(player)) {
                deliver(player, wisp);
            } else {
                for (ItemStack stack : wisp.cargo) {
                    ItemEntity item = new ItemEntity(squad.world, wisp.pos.x, wisp.pos.y, wisp.pos.z, stack);
                    squad.world.spawnEntity(item);
                }

                if (wisp.xp > 0) {
                    ExperienceOrbEntity.spawn(squad.world, wisp.pos, wisp.xp);
                }

                wisp.cargo.clear();
            }

            squad.world.spawnParticles(ParticleTypes.SOUL, wisp.pos.x, wisp.pos.y, wisp.pos.z, 6, 0.1, 0.1, 0.1, 0.03);
        }

        Powers.setResource(player, "wisps", 0);
        RevenantNetworking.marks(player, new int[0]);
    }

    static void reset() {
        SQUADS.clear();
    }

    public static void forget(ServerPlayerEntity player) {
        dismiss(player);
    }

    static Iterator<Squad> squads() {
        return SQUADS.values().iterator();
    }
}
