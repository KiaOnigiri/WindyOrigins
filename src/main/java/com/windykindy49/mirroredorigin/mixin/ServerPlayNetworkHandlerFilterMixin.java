package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BossBarS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import com.windykindy49.mirroredorigin.util.MirrorContext;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import com.windykindy49.mirroredorigin.util.SoundState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ServerPlayNetworkHandler.class})
public class ServerPlayNetworkHandlerFilterMixin {
    @Shadow
    public ServerPlayerEntity player;

    @Inject(method={"sendPacket"}, at={@At(value="HEAD")}, cancellable=true)
    private void onSendPacket(Packet<?> packet, CallbackInfo ci) {
        // (1) Effects explicitly marked cross-phase (phase_shift, banishment teleport
        //     feedback) must reach everyone regardless of phase.
        if (SoundState.isCrossPhase()) {
            return;
        }
        if (this.player == null) {
            return;
        }
        boolean playerPhase = ((MirrorEntity)(Object)this.player).mirroredorigin$isInMirrorWorld();

        // (2) Packets that name their entity are resolved from that entity directly.
        //     This is authoritative and does not depend on the ambient tick context,
        //     which fixes e.g. the TNT minecart priming flash inside the mirror world
        //     and entity sounds leaking across phases.
        if (packet instanceof ItemPickupAnimationS2CPacket) {
            // The pickup sound is NOT sent by the server: the client plays it itself when
            // this packet arrives (entity.item.pickup, or entity.experience_orb.pickup for
            // orbs), together with the item-flying-into-you particle. That is why no amount
            // of phase context around ItemEntity#onPlayerCollision ever silenced it - there
            // was no sound packet to filter. Dropping the packet itself is the only place
            // where it can be stopped, and it covers items, XP orbs, arrows and tridents in
            // one go, since all of them announce a pickup through this packet.
            ItemPickupAnimationS2CPacket pickup = (ItemPickupAnimationS2CPacket) packet;
            Entity collector = this.player.getWorld().getEntityById(pickup.getCollectorEntityId());
            if (collector == null) {
                collector = this.player.getWorld().getEntityById(pickup.getEntityId());
            }
            if (collector != null && ((MirrorEntity) collector).mirroredorigin$isInMirrorWorld() != playerPhase) {
                ci.cancel();
            }
            return;
        }
        if (packet instanceof EntityStatusS2CPacket) {
            Entity target = ((EntityStatusS2CPacket)packet).getEntity(this.player.getWorld());
            if (target != null) {
                if (((MirrorEntity)target).mirroredorigin$isInMirrorWorld() != playerPhase) {
                    ci.cancel();
                }
                return;
            }
        } else if (packet instanceof PlaySoundFromEntityS2CPacket) {
            Entity target = this.player.getWorld().getEntityById(((PlaySoundFromEntityS2CPacket)packet).getEntityId());
            if (target != null) {
                if (((MirrorEntity)target).mirroredorigin$isInMirrorWorld() != playerPhase) {
                    ci.cancel();
                }
                return;
            }
        }

        // (3) Everything else: phase of the emitting sound source if known, else the
        //     ambient tick/action context.
        Boolean contextPhase = MirrorContext.get();
        Entity soundSource = SoundState.CURRENT_SOUND_SOURCE.get();
        if (soundSource != null) {
            contextPhase = ((MirrorEntity)soundSource).mirroredorigin$isInMirrorWorld();
        }
        if (contextPhase == null) {
            return;
        }
        if (contextPhase != playerPhase) {
            WorldEventS2CPacket worldEvent;
            int id;
            if (packet instanceof ParticleS2CPacket) {
                ParticleS2CPacket p = (ParticleS2CPacket)packet;
                String type = Registries.PARTICLE_TYPE.getId(p.getParameters().getType()).toString();
                if (!(type.contains("block") || type.contains("dust") || type.contains("item"))) {
                    ci.cancel();
                }
            } else if (packet instanceof PlaySoundFromEntityS2CPacket || packet instanceof EntityAnimationS2CPacket || packet instanceof BossBarS2CPacket || packet instanceof EntityStatusS2CPacket || packet instanceof ExplosionS2CPacket) {
                ci.cancel();
            } else if (packet instanceof PlaySoundS2CPacket) {
                PlaySoundS2CPacket soundPacket = (PlaySoundS2CPacket)packet;
                if (soundPacket.getCategory() != SoundCategory.BLOCKS) {
                    ci.cancel();
                }
            } else if (packet instanceof WorldEventS2CPacket && ((id = (worldEvent = (WorldEventS2CPacket)packet).getEventId()) == 2002 || id == 2007)) {
                ci.cancel();
            }
        }
    }
}
