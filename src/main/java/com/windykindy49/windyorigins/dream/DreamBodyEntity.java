package com.windykindy49.windyorigins.dream;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

public class DreamBodyEntity extends Entity {
    private static final TrackedData<Optional<UUID>> OWNER = DataTracker.registerData(DreamBodyEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    public static final ChunkTicketType<ChunkPos> TICKET = ChunkTicketType.create("windyorigins_dream_body", Comparator.comparingLong(ChunkPos::toLong), 60);

    public static void holdChunk(ServerWorld var0, BlockPos var1) {
        ChunkPos var2 = new ChunkPos(var1);
        var0.getChunkManager().addTicket(TICKET, var2, 2, var2);
    }

    public DreamBodyEntity(EntityType<?> var1, World var2) {
        super(var1, var2);
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(OWNER, Optional.empty());
    }

    public void setOwner(UUID var1) {
        this.dataTracker.set(OWNER, Optional.of(var1));
    }

    public UUID getOwner() {
        return this.dataTracker.get(OWNER).orElse(null);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.hasNoGravity()) {
            this.setVelocity(this.getVelocity().add(0.0, -0.04, 0.0));
            this.move(MovementType.SELF, this.getVelocity());
            this.setVelocity(this.getVelocity().multiply(0.5));
        }

        if (this.getWorld().isClient()) {
            if (this.age % 20 == 0) {
                this.getWorld().addParticle(ParticleTypes.NOTE, this.getX(), this.getY() + 0.8, this.getZ(), 0.6, 0.0, 0.0);
            }
        } else if (this.getOwner() != null && DreamManager.isDreaming(this.getOwner())) {
            if (this.age % 20 == 0 && this.getWorld() instanceof ServerWorld var1) {
                holdChunk(var1, this.getBlockPos());
            }
        } else {
            this.discard();
        }
    }

    @Override
    public boolean damage(DamageSource var1, float var2) {
        if (!this.isInvulnerableTo(var1) && !this.getWorld().isClient()) {
            DreamManager.onBodyHit(this, var1);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean canHit() {
        return !this.isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldSave() {
        return false;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound var1) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound var1) {
    }
}
