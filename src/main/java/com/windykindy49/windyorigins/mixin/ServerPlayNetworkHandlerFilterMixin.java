package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorContext;
import com.windykindy49.windyorigins.util.MirrorEntity;
import com.windykindy49.windyorigins.util.SoundState;
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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ServerPlayNetworkHandler.class})
public class ServerPlayNetworkHandlerFilterMixin {
    @Shadow
    public ServerPlayerEntity player;

    @Inject(
        method = {"sendPacket"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void onSendPacket(Packet<?> var1, CallbackInfo var2) {
        if (!SoundState.isCrossPhase()) {
            if (this.player != null) {
                boolean var3 = ((MirrorEntity)(Object)this.player).windyorigins$isInMirrorWorld();
                if (var1 instanceof ItemPickupAnimationS2CPacket var12) {
                    Entity var13 = this.player.getWorld().getEntityById(var12.getCollectorEntityId());
                    if (var13 == null) {
                        var13 = this.player.getWorld().getEntityById(var12.getEntityId());
                    }

                    if (var13 != null && ((MirrorEntity)var13).windyorigins$isInMirrorWorld() != var3) {
                        var2.cancel();
                    }
                } else {
                    if (var1 instanceof EntityStatusS2CPacket) {
                        Entity var4 = ((EntityStatusS2CPacket)var1).getEntity(this.player.getWorld());
                        if (var4 != null) {
                            if (((MirrorEntity)var4).windyorigins$isInMirrorWorld() != var3) {
                                var2.cancel();
                            }

                            return;
                        }
                    } else if (var1 instanceof PlaySoundFromEntityS2CPacket) {
                        Entity var10 = this.player.getWorld().getEntityById(((PlaySoundFromEntityS2CPacket)var1).getEntityId());
                        if (var10 != null) {
                            if (((MirrorEntity)var10).windyorigins$isInMirrorWorld() != var3) {
                                var2.cancel();
                            }

                            return;
                        }
                    }

                    Boolean var11 = MirrorContext.get();
                    Entity var5 = SoundState.CURRENT_SOUND_SOURCE.get();
                    if (var5 != null) {
                        var11 = ((MirrorEntity)var5).windyorigins$isInMirrorWorld();
                    }

                    if (var11 != null) {
                        if (var11 != var3) {
                            if (var1 instanceof ParticleS2CPacket var8) {
                                String var9 = Registries.PARTICLE_TYPE.getId(var8.getParameters().getType()).toString();
                                if (!var9.contains("block") && !var9.contains("dust") && !var9.contains("item")) {
                                    var2.cancel();
                                }
                            } else if (var1 instanceof PlaySoundFromEntityS2CPacket
                                || var1 instanceof EntityAnimationS2CPacket
                                || var1 instanceof BossBarS2CPacket
                                || var1 instanceof EntityStatusS2CPacket
                                || var1 instanceof ExplosionS2CPacket) {
                                var2.cancel();
                            } else if (var1 instanceof PlaySoundS2CPacket var14) {
                                if (var14.getCategory() != SoundCategory.BLOCKS) {
                                    var2.cancel();
                                }
                            } else {
                                WorldEventS2CPacket var6;
                                int var7;
                                if (var1 instanceof WorldEventS2CPacket && ((var7 = (var6 = (WorldEventS2CPacket)var1).getEventId()) == 2002 || var7 == 2007)) {
                                    var2.cancel();
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
