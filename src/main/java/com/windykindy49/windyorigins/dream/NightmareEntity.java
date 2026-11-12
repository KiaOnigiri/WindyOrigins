package com.windykindy49.windyorigins.dream;

import com.windykindy49.windyorigins.common.PhaseFx;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.DefaultAttributeContainer.Builder;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class NightmareEntity extends HostileEntity {
    public static final int SHADE = 0;
    public static final int GRABBER = 1;
    public static final int COLOSSUS = 2;
    private static final TrackedData<Integer> KIND = DataTracker.registerData(NightmareEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> TELL = DataTracker.registerData(NightmareEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final UUID LIGHT_MOD = UUID.fromString("5f6a3c22-7b84-4e0f-9f3a-1d2c3b4a5e60");
    private BlockPos home;
    private UUID owner;
    private UUID session;
    private int life = -1;
    private int specialCooldown = 60;
    private boolean minion;

    public NightmareEntity(EntityType<? extends HostileEntity> var1, World var2) {
        super(var1, var2);
        this.experiencePoints = 0;
    }

    public static Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
            .add(EntityAttributes.GENERIC_MAX_HEALTH, 16.0)
            .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3.0)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.27)
            .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.5)
            .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(KIND, 0);
        this.dataTracker.startTracking(TELL, 0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.add(6, new WanderAroundGoal(this, 0.6));
        this.goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 12.0F));
        this.goalSelector.add(8, new LookAroundGoal(this));
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, 10, true, false, var1 -> DreamManager.isPrey(this, var1)));
    }

    public void setup(int var1, BlockPos var2, UUID var3, UUID var4, boolean var5, float var6) {
        this.dataTracker.set(KIND, var1);
        this.home = var2;
        this.session = var3;
        this.owner = var4;
        this.minion = var5;

        double var7 = switch (var1) {
            case 1 -> 12.0;
            case 2 -> 40.0;
            default -> 16.0;
        };

        double var9 = switch (var1) {
            case 1 -> 2.0;
            case 2 -> 6.0;
            default -> 3.0;
        };

        double var11 = switch (var1) {
            case 0 -> 0.27;
            default -> 0.2;
        };
        var7 *= (double)var6 * (var5 ? 0.5 : 1.0);
        this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(var7);
        this.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).setBaseValue(var9);
        this.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(var11);
        this.setHealth((float)var7);
        if (var5) {
            this.life = 400;
        }
    }

    public int getKind() {
        return this.dataTracker.get(KIND);
    }

    public int getTell() {
        return this.dataTracker.get(TELL);
    }

    public UUID getOwner() {
        return this.owner;
    }

    public UUID getSession() {
        return this.session;
    }

    public boolean isMinion() {
        return this.minion;
    }

    @Override
    public boolean shouldSave() {
        return false;
    }

    @Override
    public boolean canImmediatelyDespawn(double var1) {
        return false;
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public boolean handleFallDamage(float var1, float var2, DamageSource var3) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        World var1 = this.getWorld();
        if (var1.isClient()) {
            if (this.getTell() > 0) {
                var1.addParticle(ParticleTypes.REVERSE_PORTAL, this.getParticleX(0.8), this.getRandomBodyY(), this.getParticleZ(0.8), 0.0, 0.0, 0.0);
            }

            if (this.random.nextInt(4) == 0) {
                var1.addParticle(ParticleTypes.SMOKE, this.getParticleX(0.6), this.getRandomBodyY(), this.getParticleZ(0.6), 0.0, 0.01, 0.0);
            }
        } else if (this.session != null && DreamManager.sessionAlive(this.session)) {
            if (this.life > 0 && --this.life == 0) {
                PhaseFx.particles(this, ParticleTypes.LARGE_SMOKE, this.getX(), this.getBodyY(0.5), this.getZ(), 20, 0.3, 0.6, 0.3, 0.02);
                this.discard();
            } else {
                if (this.age % 20 == 0) {
                    this.adjustForLight();
                }

                if (this.home != null && !this.minion && this.squaredDistanceTo(Vec3d.ofCenter(this.home)) > 100.0) {
                    this.setTarget(null);
                    this.getNavigation().startMovingTo((double)this.home.getX() + 0.5, (double)this.home.getY(), (double)this.home.getZ() + 0.5, 1.1);
                }

                if (this.isTouchingWater()) {
                    this.setVelocity(this.getVelocity().multiply(-0.5, 0.0, -0.5));
                }

                this.tickSpecial();
            }
        } else {
            this.discard();
        }
    }

    private void adjustForLight() {
        EntityAttributeInstance var1 = this.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (var1 != null) {
            var1.removeModifier(LIGHT_MOD);
            int var2 = this.getWorld().getLightLevel(this.getBlockPos());
            if (var2 >= 10) {
                var1.addTemporaryModifier(new EntityAttributeModifier(LIGHT_MOD, "dream light", -0.3, Operation.MULTIPLY_TOTAL));
            } else if (var2 <= 4) {
                var1.addTemporaryModifier(new EntityAttributeModifier(LIGHT_MOD, "dream dark", 0.2, Operation.MULTIPLY_TOTAL));
                if (this.age % 40 == 0 && !this.minion) {
                    this.heal(1.0F);
                }
            }
        }
    }

    private void tickSpecial() {
        LivingEntity var1 = this.getTarget();
        int var2 = this.getTell();
        if (var2 > 0) {
            this.dataTracker.set(TELL, var2 - 1);
            if (var2 - 1 == 0 && var1 != null && var1.isAlive()) {
                this.release(var1);
            }
        } else if (--this.specialCooldown <= 0 && var1 != null && var1.isAlive()) {
            double var3 = this.squaredDistanceTo(var1);
            switch (this.getKind()) {
                case 0:
                    if (var3 > 9.0) {
                        this.dataTracker.set(TELL, 10);
                    }

                    this.specialCooldown = 80 + this.random.nextInt(40);
                    break;
                case 1:
                    if (var3 < 25.0 && this.canSee(var1)) {
                        this.dataTracker.set(TELL, 8);
                        this.specialCooldown = 120;
                    }
                    break;
                case 2:
                    if (var3 < 9.0) {
                        this.dataTracker.set(TELL, 20);
                        this.playSound(SoundEvents.ENTITY_WARDEN_ROAR, 0.6F, 1.6F);
                        this.specialCooldown = 60;
                    }
            }
        }
    }

    private void release(LivingEntity var1) {
        switch (this.getKind()) {
            case 0:
                Vec3d var6 = var1.getPos().subtract(this.getPos()).normalize().multiply(2.0 + this.random.nextDouble());
                Vec3d var7 = this.getPos().add(var6);
                this.requestTeleport(var7.x, this.getY(), var7.z);
                PhaseFx.sound(this, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 0.4F, 0.4F);
                break;
            case 1:
                Vec3d var5 = this.getPos().subtract(var1.getPos()).normalize().multiply(1.2);
                var1.setVelocity(var5.x, 0.35, var5.z);
                var1.velocityModified = true;
                PhaseFx.sound(this, SoundEvents.ENTITY_FISHING_BOBBER_RETRIEVE, SoundCategory.HOSTILE, 1.0F, 0.5F);
                break;
            case 2:
                for (LivingEntity var3 : this.getWorld()
                    .getEntitiesByClass(
                        LivingEntity.class, this.getBoundingBox().expand(2.0), var1x -> var1x != this && !(var1x instanceof NightmareEntity) && var1x.isAlive()
                    )) {
                    var3.damage(this.getDamageSources().mobAttack(this), 6.0F);
                    Vec3d var4 = var3.getPos().subtract(this.getPos()).normalize().multiply(1.2);
                    var3.setVelocity(var4.x, 0.5, var4.z);
                    var3.velocityModified = true;
                }

                PhaseFx.particles(this, ParticleTypes.EXPLOSION, this.getX(), this.getY() + 0.2, this.getZ(), 3, 1.0, 0.1, 1.0, 0.0);
                PhaseFx.sound(this, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 0.6F, 0.6F);
        }
    }

    @Override
    public boolean tryAttack(Entity var1) {
        if (this.minion && var1 instanceof ServerPlayerEntity var2 && DreamManager.minionMustSpare(this, var2)) {
            return false;
        }

        return super.tryAttack(var1);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource var1) {
        return SoundEvents.ENTITY_PHANTOM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_PHANTOM_DEATH;
    }
}
