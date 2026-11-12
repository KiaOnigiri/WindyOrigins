package com.windykindy49.windyorigins.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.windykindy49.windyorigins.common.Powers;
import com.windykindy49.windyorigins.common.WindyEffects;
import com.windykindy49.windyorigins.host.HostManager;
import com.windykindy49.windyorigins.host.HostNetworking;
import com.windykindy49.windyorigins.host.HostPrank;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import com.windykindy49.windyorigins.client.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil.Type;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public final class HostClient {
    public static final Identifier SHEET = new Identifier("windyorigins", "textures/entity/volta.png");
    public static final Identifier HOLO = new Identifier("windyorigins", "textures/entity/volta_holo.png");
    public static final Identifier HUD_FONT = new Identifier("windyorigins", "hud");
    public static final KeyBinding WHEEL = KeyBindingHelper.registerKeyBinding(
        new KeyBinding("key.windyorigins.volta_wheel", Type.KEYSYM, 86, "category.windyorigins")
    );
    private static final int NEON = -12128026;
    private static final int PINK = -1033016;
    private static final int PANEL = -1072688602;
    private static Text line;
    private static int face;
    private static long lineStart;
    private static int lineTicks;
    private static int castPose = -1;
    private static int castTicks;
    private static HostPrank prank;
    private static int prankTicks;
    private static int prankTotal;
    private static BlockPos teaseAt;
    private static boolean hudWasHidden;
    private static int glitchTicks;
    private static Vec3d found;
    private static Text foundLabel;
    private static int foundKind;
    private static int foundTicks;
    private static int age;
    private static int flipTicks;
    private static int flashTicks;
    private static int flashColor;
    private static float burst;

    private HostClient() {
    }

    public static void init() {
        VoltaNames.init();
        ClientPlayNetworking.registerGlobalReceiver(HostNetworking.SAY, (var0, var1, var2, var3) -> {
            Text var4 = var2.readText();
            int var5 = var2.readVarInt();
            var0.execute(() -> speak(var0, var4, var5));
        });
        ClientPlayNetworking.registerGlobalReceiver(HostNetworking.PRANK, (var0, var1, var2, var3) -> {
            HostPrank var4 = HostPrank.values()[var2.readVarInt()];
            Text var5 = var2.readText();
            BlockPos var6 = var2.readBoolean() ? var2.readBlockPos() : null;
            var0.execute(() -> startPrank(var0, var4, var5, var6));
        });
        ClientPlayNetworking.registerGlobalReceiver(HostNetworking.MARKS, (var0, var1, var2, var3) -> {
            int[] var4 = var2.readIntArray();
            int var5 = var2.readVarInt();
            var0.execute(() -> {
                if (var0.world != null) {
                    long var3x = var0.world.getTime() + (long)var5;

                    for (int var8 : var4) {
                        ClientOutlines.TIMED.put(var8, var3x);
                    }
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(HostNetworking.FOUND, (var0, var1, var2, var3) -> {
            Vec3d var4 = new Vec3d(var2.readDouble(), var2.readDouble(), var2.readDouble());
            int var5 = var2.readVarInt();
            Text var6 = var2.readText();
            var0.execute(() -> {
                found = var4;
                foundKind = var5;
                foundLabel = var6;
                foundTicks = 200;
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(HostNetworking.SCANNED, (var0, var1, var2, var3) -> var0.execute(() -> {
                glitchTicks = 15;
                play(var0, SoundEvents.BLOCK_BEACON_DEACTIVATE, 0.4F, 2.0F);
            }));
        ClientPlayNetworking.registerGlobalReceiver(HostNetworking.CAST, (var0, var1, var2, var3) -> {
            int var4 = var2.readVarInt();
            int var5 = var2.readVarInt();
            var0.execute(() -> {
                castPose = var4;
                castTicks = var5;
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(HostNetworking.FX, (var0, var1, var2, var3) -> {
            int var4 = var2.readByte();
            int var5 = var2.readByte();
            int var6 = var2.readInt();
            int var7 = var2.readInt();
            Vec3d var8 = new Vec3d(var2.readDouble(), var2.readDouble(), var2.readDouble());
            Vec3d var11 = new Vec3d(var2.readDouble(), var2.readDouble(), var2.readDouble());
            int var9 = var2.readInt();
            int var10 = var2.readVarInt();
            var0.execute(() -> VoltaFx.add(var4, var5, var6, var7, var8, var11, var9, var10));
        });
        WorldRenderEvents.AFTER_ENTITIES.register(VoltaFx::renderWorld);
        ClientPlayConnectionEvents.DISCONNECT.register((var0, var1) -> {
            VoltaFx.clear();
            flipTicks = 0;
            endPrank(var1);
            line = null;
            found = null;
            ClientOutlines.TIMED.clear();
        });
        ClientTickEvents.END_CLIENT_TICK.register(HostClient::tick);
        HudRenderCallback.EVENT.register((matrices, delta) -> HostClient.renderHud(new com.windykindy49.windyorigins.client.DrawContext(matrices), delta));
        WorldRenderEvents.AFTER_ENTITIES.register(HostClient::renderWorld);
    }

    public static boolean isHost(ClientPlayerEntity var0) {
        return var0 != null && Powers.has(var0, "volta");
    }

    private static void tick(MinecraftClient var0) {
        age++;
        VoltaFx.tick(var0);
        burst *= 0.8F;
        if (flipTicks > 0) {
            flipTicks--;
        }

        if (flashTicks > 0) {
            flashTicks--;
        }

        ClientPlayerEntity var1 = var0.player;
        if (var1 != null) {
            // A cooking chip does not just draw static: now and then the whole picture flips.
            boolean var5 = var1.hasStatusEffect(WindyEffects.CYBERPSYCHOSIS);
            if (isHost(var1) && !var0.isPaused() && flipTicks == 0) {
                int var6 = HostManager.heat(var1);
                if (var5 && age % 30 == 0 && var1.getRandom().nextFloat() < 0.5F) {
                    flipTicks = 5 + var1.getRandom().nextInt(14);
                } else if (var6 >= 85 && age % 60 == 0 && var1.getRandom().nextFloat() < 0.3F) {
                    flipTicks = 3 + var1.getRandom().nextInt(4);
                }
            }

            if (WHEEL.isPressed() && var0.currentScreen == null && isHost(var1)) {
                var0.setScreen(new VoltaWheelScreen());
            }

            if (castTicks > 0) {
                castTicks--;
            }

            boolean var2 = line != null && (long)age - lineStart < (long)lineTicks && (float)((long)age - lineStart) * 1.6F < (float)line.getString().length();
            int var3 = VoltaFx.castingFace() ? 1 : (prank != null ? popupFace() : (line != null && (long)age - lineStart < (long)lineTicks ? face : 0));
            VoltaRig.tick(var3, var2);
            if (foundTicks > 0) {
                foundTicks--;
            }

            if (glitchTicks > 0) {
                glitchTicks--;
            }

            if (prank != null) {
                prankTicks--;
                if (prank == HostPrank.HISS && prankTicks == 18) {
                    // ...and then it "goes off".
                    play(var0, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.0F, 0.9F);
                    burst = 1.0F;
                    flashTicks = 6;
                    flashColor = 16777215;
                }

                if (prank == HostPrank.HURT && (prankTicks == 26 || prankTicks == 12)) {
                    fakeHit(var0);
                }

                if (prank == HostPrank.SCREAM) {
                    burst = Math.max(burst, 0.9F);
                }

                if (prank == HostPrank.GLITCH && prankTicks % 9 == 0) {
                    flipTicks = 3;
                    play(var0, SoundEvents.BLOCK_BEACON_DEACTIVATE, 0.3F, 1.6F + var1.getRandom().nextFloat() * 0.4F);
                }

                if (prank == HostPrank.STEPS && prankTicks == 4) {
                    play(var0, SoundEvents.AMBIENT_CAVE.value(), 0.9F, 1.0F);
                }

                if (prank == HostPrank.FAKE_DEATH && prankTicks == 16) {
                    speak(var0, Text.translatable("message.windyorigins.volta.prank.fake_death_end"), 5);
                }

                if (prank == HostPrank.STEPS && prankTicks % 8 == 0 && prankTicks > 8) {
                    Vec3d var4 = var1.getPos().subtract(var1.getRotationVec(1.0F).multiply(2.5));
                    var0.getSoundManager()
                        .play(
                            new PositionedSoundInstance(
                                SoundEvents.BLOCK_STONE_STEP, SoundCategory.PLAYERS, 0.5F, 0.9F, Random.create(), var4.x, var4.y, var4.z
                            )
                        );
                }

                if (prankTicks <= 0) {
                    if (prank == HostPrank.TEASE) {
                        speak(var0, Text.translatable("message.windyorigins.volta.prank.tease_end"), 5);
                        play(var0, SoundEvents.ENTITY_VILLAGER_NO, 0.4F, 1.8F);
                    }

                    endPrank(var0);
                }
            }
        }
    }

    private static void speak(MinecraftClient var0, Text var1, int var2) {
        line = var1;
        face = var2;
        lineStart = (long)age;
        lineTicks = 80 + var1.getString().length() * 2;
        if (var0.player != null) {
            var0.player.sendMessage(Text.literal("[Volta] ").formatted(Formatting.AQUA).append(var1.copy().formatted(Formatting.WHITE)), false);
        }

        play(var0, SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), 0.35F, 1.6F + var0.world.getRandom().nextFloat() * 0.4F);
    }

    private static void endPrank(MinecraftClient var0) {
        if (prank == HostPrank.HUD) {
            var0.options.hudHidden = hudWasHidden;
        }

        prank = null;
        teaseAt = null;
    }

    private static void startPrank(MinecraftClient var0, HostPrank var1, Text var2, BlockPos var3) {
        endPrank(var0);
        prank = var1;
        teaseAt = var3;

        prankTicks = switch (var1) {
            case HUD, MIRROR -> 120;
            case DANCE, FISHEYE, STEPS -> 100;
            case INVERT -> 140;
            case BLACKOUT -> 100;
            case GLITCH -> 80;
            case TEASE, WINK, FAKE_DEATH -> 60;
            case HISS -> 50;
            case SCREAM -> 30;
            default -> 40;
        };
        prankTotal = prankTicks;

        byte var4 = switch (var1) {
            case HUD, TEASE, SLOT, SHUFFLE, RUBBERBAND -> 1;
            case DANCE, WINK, FISHEYE, JUMP, SHOVE, DROP, WHISTLE -> 6;
            case MIRROR, HISS, HURT, GLITCH, INVERT, SPIN, FAKE_DEATH, BLACKOUT -> 5;
            case SCREAM -> 4;
            default -> 0;
        };
        speak(var0, var2, var4);
        VoltaFx.prank(var1);
        switch (var1) {
            case HUD:
                hudWasHidden = var0.options.hudHidden;
                var0.options.hudHidden = true;
            case DANCE:
            case STEPS:
            case FISHEYE:
            case SLOT:
            default:
                break;
            case MIRROR:
                glitchTicks = 6;
                play(var0, SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.6F, 1.4F);
                break;
            case TEASE:
                play(var0, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.8F, 1.6F);
                break;
            case WINK:
                play(var0, SoundEvents.BLOCK_LEVER_CLICK, 0.6F, 0.6F);
                break;
            case HISS:
                Vec3d var5 = var0.player.getPos().subtract(var0.player.getRotationVec(1.0F).multiply(1.5));
                var0.getSoundManager()
                    .play(
                        new PositionedSoundInstance(
                            SoundEvents.ENTITY_CREEPER_PRIMED, SoundCategory.HOSTILE, 1.0F, 0.5F, Random.create(), var5.x, var5.y, var5.z
                        )
                    );
                break;
            case HURT:
                fakeHit(var0);
                break;
            case GLITCH:
                glitchTicks = 80;
                break;
            case SCREAM:
                play(var0, SoundEvents.ENTITY_GHAST_SCREAM, 1.0F, 0.55F);
                play(var0, SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 1.0F, 1.4F);
                burst = 1.0F;
                break;
            case FAKE_DEATH:
                play(var0, SoundEvents.ENTITY_PLAYER_DEATH, 1.0F, 1.0F);
                burst = 0.6F;
                break;
            case INVERT:
                glitchTicks = 8;
                play(var0, SoundEvents.BLOCK_PISTON_CONTRACT, 0.7F, 1.6F);
                break;
            case SPIN:
            case JUMP:
            case SHUFFLE:
            case RUBBERBAND:
                glitchTicks = 8;
                burst = 0.7F;
                break;
            case RENAME:
                play(var0, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.6F, 1.5F);
        }
    }

    public static boolean mirrored() {
        return prank == HostPrank.MIRROR && prankTicks > 0 || flipTicks > 0;
    }

    /** Volta has swapped the movement keys around. */
    public static boolean inverted() {
        return prank == HostPrank.INVERT && prankTicks > 0;
    }

    private static void fakeHit(MinecraftClient var0) {
        play(var0, SoundEvents.ENTITY_PLAYER_HURT, 1.0F, 1.0F);
        if (var0.player != null) {
            var0.player.maxHurtTime = 10;
            var0.player.hurtTime = 10;
        }

        flashTicks = 8;
        flashColor = 13111312;
        burst = Math.max(burst, 0.5F);
    }

    /** How badly the picture is breaking up: 0 below 40% heat, 1 at the limit, more in psychosis. */
    private static float interference(ClientPlayerEntity var0) {
        if (!isHost(var0)) {
            return 0.0F;
        } else if (var0.hasStatusEffect(WindyEffects.CYBERPSYCHOSIS)) {
            return 1.5F;
        } else {
            // A broken chip never clears: the picture stays torn until it is repaired.
            return Math.max(HostManager.isBroken(var0) ? 0.62F : 0.0F, MathHelper.clamp((float)(HostManager.heat(var0) - 40) / 60.0F, 0.0F, 1.0F));
        }
    }

    private static float shakeAmount() {
        ClientPlayerEntity var0 = MinecraftClient.getInstance().player;
        if (var0 == null || MinecraftClient.getInstance().isPaused()) {
            return 0.0F;
        } else {
            float var1 = interference(var0);
            if (HostManager.isBroken(var0) && !var0.hasStatusEffect(WindyEffects.CYBERPSYCHOSIS) && HostManager.heat(var0) < 70) {
                // Permanent static should not also make the Host seasick.
                var1 = 0.3F;
            }

            float var2 = var1 * var1 * 1.6F + burst * 3.5F + VoltaFx.kick(0.0F) * 2.2F;
            if (glitchTicks > 0) {
                var2 += 0.8F;
            }

            return var2;
        }
    }

    /** Camera shake in degrees; the kick of a shot throws the view up. */
    public static float shakeYaw(float var0) {
        float var1 = shakeAmount();
        if (var1 <= 0.001F) {
            return 0.0F;
        } else {
            float var2 = (float)age + var0;
            return var1 * (MathHelper.sin(var2 * 2.9F) * 0.6F + MathHelper.sin(var2 * 7.3F + 1.3F) * 0.4F);
        }
    }

    public static float shakePitch(float var0) {
        float var1 = shakeAmount();
        float var2 = (float)age + var0;
        float var3 = var1 <= 0.001F ? 0.0F : var1 * (MathHelper.sin(var2 * 3.7F + 0.7F) * 0.5F + MathHelper.sin(var2 * 8.9F) * 0.3F);
        return var3 - VoltaFx.kick(var0) * 2.4F;
    }

    private static void play(MinecraftClient var0, SoundEvent var1, float var2, float var3) {
        var0.getSoundManager().play(PositionedSoundInstance.master(var1, var3, var2));
    }

    public static float fovFactor(float var0) {
        float var2 = 1.0F + VoltaFx.kick(var0) * 0.07F;
        ClientPlayerEntity var3 = MinecraftClient.getInstance().player;
        float var4 = var3 == null ? 0.0F : interference(var3);
        if (var4 > 0.4F) {
            var2 *= 1.0F + (var4 - 0.4F) * 0.07F * MathHelper.sin(((float)age + var0) * 0.9F);
        }

        if (prank != HostPrank.FISHEYE) {
            return var2;
        } else {
            float var1 = ((float)(prankTotal - prankTicks) + var0) / (float)Math.max(1, prankTotal);
            return var2 * (1.0F + 0.55F * MathHelper.sin(var1 * (float) Math.PI) + 0.08F * MathHelper.sin(var1 * 40.0F));
        }
    }

    private static int frameFor(AbstractClientPlayerEntity var0, boolean var1) {
        if (var1 && prank == HostPrank.DANCE) {
            return 4 + age / 5 % 2;
        } else if (var1 && castTicks > 0 && castPose >= 0) {
            return castPose;
        } else {
            return var0.hasStatusEffect(WindyEffects.CYBERPSYCHOSIS) && var1 || HostManager.isBroken(var0) ? 7 : age / 12 % 2;
        }
    }

    private static void renderWorld(WorldRenderContext var0) {
        MinecraftClient var1 = MinecraftClient.getInstance();
        if (var1.world != null && var0.consumers() != null) {
            Camera var2 = var0.camera();
            Vec3d var3 = var2.getPos();
            float var4 = var0.tickDelta();
            MatrixStack var5 = var0.matrixStack();
            VertexConsumerProvider var6 = var0.consumers();
            if (prank == HostPrank.TEASE && teaseAt != null) {
                float var7 = 0.55F + 0.45F * MathHelper.sin(((float)age + var4) * 0.5F);
                Box var8 = new Box(teaseAt).expand(0.004).offset(-var3.x, -var3.y, -var3.z);
                WorldRenderer.drawBox(var5, var6.getBuffer(RenderLayer.getLines()), var8, 1.0F, 0.84F, 0.2F, var7);
                if (age % 4 == 0) {
                    var1.world
                        .addParticle(
                            ParticleTypes.WAX_ON,
                            (double)teaseAt.getX() + var1.world.random.nextDouble(),
                            (double)teaseAt.getY() + var1.world.random.nextDouble(),
                            (double)teaseAt.getZ() + var1.world.random.nextDouble(),
                            0.0,
                            0.0,
                            0.0
                        );
                }
            }

            for (AbstractClientPlayerEntity var55 : var1.world.getPlayers()) {
                if (!var55.isSpectator() && !var55.isInvisible() && Powers.has(var55, "volta")) {
                    boolean var9 = var55 == var1.player;
                    boolean var10 = var9 && !var2.isThirdPerson();
                    Vec3d var11 = var55.getLerpedPos(var4);
                    float var12 = MathHelper.lerp(var4, var55.prevBodyYaw, var55.bodyYaw) * (float) (Math.PI / 180.0);
                    double var13 = var10 ? 1.1 : 0.75;
                    double var15 = -Math.cos((double)var12);
                    double var17 = -Math.sin((double)var12);
                    double var19 = Math.sin((double)var12);
                    double var21 = -Math.cos((double)var12);
                    // The hologram is animated on threes: it holds a pose, then jumps to the next.
                    float var23 = (float)(var55.age / 3 * 3);
                    double var24 = (double)Math.round(Math.sin((double)var23 * 0.08) * 1.3) / 16.0;
                    double var26 = var11.x + var15 * var13 + var19 * 0.25;
                    double var28 = var11.y + (var10 ? 1.25 : 1.55) + var24;
                    double var30 = var11.z + var17 * var13 + var21 * 0.25;
                    long var32 = (long)(var23 / 3.0F) * 31L + (long)var55.getId();
                    double var34 = var32 % 23L == 0L ? 0.06 : 0.0;
                    var5.push();
                    var5.translate(var26 - var3.x + var34, var28 - var3.y, var30 - var3.z);
                    var5.multiply(var2.getRotation());
                    float var36 = 0.55F;
                    float var37 = 0.825F;
                    int var38 = frameFor(var55, var9);
                    float var39 = (float)(var38 * 32) / 256.0F;
                    float var40 = (float)((var38 + 1) * 32) / 256.0F;
                    float var41 = 0.0F;
                    float var42 = 0.1875F;
                    float var43 = 0.78F + 0.12F * MathHelper.sin(var23 * 0.9F) + (var32 % 17L == 0L ? -0.3F : 0.0F);
                    int var44 = MathHelper.clamp((int)(var43 * 255.0F), 40, 255);
                    VertexConsumer var45 = var6.getBuffer(RenderLayer.getEntityTranslucentEmissive(SHEET));
                    Matrix4f var46 = var5.peek().getPositionMatrix();
                    Matrix3f var47 = var5.peek().getNormalMatrix();
                    float var48 = 0.018F + (var32 % 23L == 0L ? 0.03F : 0.0F);
                    var5.push();
                    var5.translate(0.0F, 0.0F, 0.004F);
                    Matrix4f var49 = var5.peek().getPositionMatrix();
                    quad(
                        var45,
                        var49,
                        var47,
                        -var36 / 2.0F - var48,
                        -var37 / 2.0F,
                        var36 / 2.0F - var48,
                        var37 / 2.0F,
                        var39,
                        var41,
                        var40,
                        var42,
                        var44 / 3,
                        70,
                        240,
                        230
                    );
                    quad(
                        var45,
                        var49,
                        var47,
                        -var36 / 2.0F + var48,
                        -var37 / 2.0F,
                        var36 / 2.0F + var48,
                        var37 / 2.0F,
                        var39,
                        var41,
                        var40,
                        var42,
                        var44 / 3,
                        240,
                        60,
                        200
                    );
                    var5.pop();
                    // Drawn in strips so that, on a bad frame, some of them tear sideways.
                    for (int var56 = 0; var56 < 6; var56++) {
                        float var57 = var32 % 3L == 0L && (var32 + (long)var56 * 7L) % 5L == 0L ? ((var32 + (long)var56) % 2L == 0L ? 0.07F : -0.07F) : 0.0F;
                        float var58 = -var37 / 2.0F + var37 * (float)var56 / 6.0F;
                        float var59 = -var37 / 2.0F + var37 * (float)(var56 + 1) / 6.0F;
                        float var60 = var42 * (1.0F - (float)(var56 + 1) / 6.0F);
                        float var61 = var42 * (1.0F - (float)var56 / 6.0F);
                        quad(var45, var46, var47, -var36 / 2.0F + var57, var58, var36 / 2.0F + var57, var59, var39, var60, var40, var61, var44, 255, 255, 255);
                    }
                    int var50 = (int)(var23 / 2.0F) % 3;
                    float var51 = (float)(var50 * 48) / 256.0F;
                    float var52 = (float)(var50 * 48 + 48) / 256.0F;
                    var5.push();
                    var5.translate(0.0F, 0.0F, -0.004F);
                    VertexConsumer var53 = var6.getBuffer(RenderLayer.getEntityTranslucentEmissive(HOLO));
                    quad(
                        var53,
                        var5.peek().getPositionMatrix(),
                        var47,
                        -var36 / 2.0F,
                        -var37 / 2.0F,
                        var36 / 2.0F,
                        var37 / 2.0F,
                        var39,
                        var51,
                        var40,
                        var52,
                        (int)((float)var44 * 0.7F),
                        255,
                        255,
                        255
                    );
                    var5.pop();
                    var5.pop();
                }
            }
        }
    }

    static void quad(
        VertexConsumer var0,
        Matrix4f var1,
        Matrix3f var2,
        float var3,
        float var4,
        float var5,
        float var6,
        float var7,
        float var8,
        float var9,
        float var10,
        int var11,
        int var12,
        int var13,
        int var14
    ) {
        int var15 = 15728880;
        var0.vertex(var1, var3, var4, 0.0F)
            .color(var12, var13, var14, var11)
            .texture(var9, var10)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(var15)
            .normal(var2, 0.0F, 0.0F, 1.0F)
            .next();
        var0.vertex(var1, var5, var4, 0.0F)
            .color(var12, var13, var14, var11)
            .texture(var7, var10)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(var15)
            .normal(var2, 0.0F, 0.0F, 1.0F)
            .next();
        var0.vertex(var1, var5, var6, 0.0F)
            .color(var12, var13, var14, var11)
            .texture(var7, var8)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(var15)
            .normal(var2, 0.0F, 0.0F, 1.0F)
            .next();
        var0.vertex(var1, var3, var6, 0.0F)
            .color(var12, var13, var14, var11)
            .texture(var9, var8)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(var15)
            .normal(var2, 0.0F, 0.0F, 1.0F)
            .next();
        var0.vertex(var1, var3, var6, 0.0F)
            .color(var12, var13, var14, var11)
            .texture(var9, var8)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(var15)
            .normal(var2, 0.0F, 0.0F, -1.0F)
            .next();
        var0.vertex(var1, var5, var6, 0.0F)
            .color(var12, var13, var14, var11)
            .texture(var7, var8)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(var15)
            .normal(var2, 0.0F, 0.0F, -1.0F)
            .next();
        var0.vertex(var1, var5, var4, 0.0F)
            .color(var12, var13, var14, var11)
            .texture(var7, var10)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(var15)
            .normal(var2, 0.0F, 0.0F, -1.0F)
            .next();
        var0.vertex(var1, var3, var4, 0.0F)
            .color(var12, var13, var14, var11)
            .texture(var9, var10)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(var15)
            .normal(var2, 0.0F, 0.0F, -1.0F)
            .next();
    }

    private static void renderHud(DrawContext var0, float var1) {
        MinecraftClient var2 = MinecraftClient.getInstance();
        ClientPlayerEntity var3 = var2.player;
        if (var3 != null) {
            int var4 = var0.getScaledWindowWidth();
            int var5 = var0.getScaledWindowHeight();
            VoltaFx.renderHud(var0, var1, var4, var5);
            if (flashTicks > 0) {
                var0.fill(0, 0, var4, var5, flashColor | Math.min(200, flashTicks * 26) << 24);
            }

            if (prank == HostPrank.SCREAM) {
                drawScream(var0, var4, var5, var1);
            }

            if (prank == HostPrank.FAKE_DEATH && prankTicks > 14) {
                drawFakeDeath(var0, var2, var4, var5);
            }

            if (prank == HostPrank.INVERT) {
                Text var18 = hud("<< ? >>   " + (prankTicks + 19) / 20);
                var0.drawTextWithShadow(var2.textRenderer, var18, var4 / 2 - var2.textRenderer.getWidth(var18) / 2, var5 / 2 + 14, -1033016);
            }

            if (prank == HostPrank.DANCE) {
                int var6 = 4 + age / 5 % 2;
                int var7 = Math.max(3, var5 / 70);
                int var8 = 32 * var7;
                int var9 = 48 * var7;
                var0.getMatrices().push();
                var0.getMatrices().translate((float)var4 / 2.0F - (float)var8 / 2.0F, (float)var5 / 2.0F - (float)var9 / 2.0F, 0.0F);
                var0.getMatrices().scale((float)var7, (float)var7, 1.0F);
                var0.drawTexture(SHEET, 0, 0, var6 * 32, 0, 32, 48);
                var0.getMatrices().pop();
                Text var10 = hud("♪ VOLTA ♪ ".repeat(6));
                int var11 = age * 3 % var2.textRenderer.getWidth(hud("♪ VOLTA ♪ "));
                var0.drawTextWithShadow(var2.textRenderer, var10, -var11, var5 / 2 + var9 / 2 + 4, -1033016);
            }

            if (prank == HostPrank.WINK) {
                float var12 = ((float)(prankTotal - prankTicks) + var1) / (float)prankTotal;
                float var14 = var12 < 0.3F ? var12 / 0.3F : (var12 > 0.7F ? (1.0F - var12) / 0.3F : 1.0F);
                int var16 = (int)(MathHelper.clamp(var14, 0.0F, 1.0F) * 0.97F * 255.0F);
                var0.fill(0, 0, var4, var5, var16 << 24);
            }

            if (!var2.options.hudHidden || prank == HostPrank.HUD) {
                float var13 = interference(var3);
                if (var13 > 0.0F) {
                    noise(var0, var4, var5, var13);
                    if (HostManager.isBroken(var3)) {
                        if (age / 10 % 2 == 0) {
                            Text var19 = hud("// CHIP OFFLINE //");
                            var0.drawTextWithShadow(var2.textRenderer, var19, var4 / 2 - var2.textRenderer.getWidth(var19) / 2, 6, -1033016);
                        }
                    } else if (var13 >= 0.75F && age / 6 % 2 == 0) {
                        Text var15 = hud("// CHIP " + HostManager.heat(var3) + "% //");
                        var0.drawTextWithShadow(var2.textRenderer, var15, var4 / 2 - var2.textRenderer.getWidth(var15) / 2, 6, -1033016);
                    }
                }

                if (glitchTicks > 0) {
                    noise(var0, var4, var5, 1.1F);
                    var0.fillGradient(0, 0, var4, var5 / 6, 1626356936, 15744200);
                }

                if (foundTicks > 0 && found != null) {
                    drawFinder(var0, var2, var4);
                }

                if (line != null && (long)age - lineStart < (long)lineTicks) {
                    drawSpeech(var0, var2, var4, var5);
                }

                if (prank != null && prank != HostPrank.DANCE && prank != HostPrank.SCREAM) {
                    drawPopup(var0, var4, var5);
                }
            }
        }
    }

    /** Static, tearing and drop-outs over the whole view; var3 runs from 0 (a trace) to 1.5 (psychosis). */
    private static void noise(DrawContext var0, int var1, int var2, float var3) {
        Random var4 = Random.create((long)age * 7919L);
        float var5 = Math.min(1.0F, var3);
        // Scanlines crawling down the picture.
        int var6 = (int)(10.0F + 46.0F * var5) << 24;

        for (int var7 = age % 4; var7 < var2; var7 += 4) {
            var0.fill(0, var7, var1, var7 + 1, var6);
        }

        // Torn strips of colour.
        int var8 = (int)(6.0F + 54.0F * var3);

        for (int var9 = 0; var9 < var8; var9++) {
            int var10 = var4.nextInt(var2);
            int var11 = 20 + var4.nextInt(Math.max(1, (int)((float)var1 * (0.3F + 0.6F * var5))));
            int var12 = var4.nextInt(Math.max(1, var1 - var11 / 2)) - var11 / 4;
            int var13 = var4.nextInt(3) == 0 ? 16777215 : (var4.nextBoolean() ? 4649190 : 15744200);
            var0.fill(var12, var10, var12 + var11, var10 + 1 + var4.nextInt(1 + (int)(5.0F * var5)), var13 | (int)(40.0F + 110.0F * var5) << 24);
        }

        // Whole blocks of the image dropping out.
        int var14 = (int)(14.0F * var3 * var3);

        for (int var15 = 0; var15 < var14; var15++) {
            int var16 = 24 + var4.nextInt(Math.max(1, var1 / 4));
            int var17 = 6 + var4.nextInt(Math.max(1, var2 / 9));
            int var18 = var4.nextInt(Math.max(1, var1 - var16));
            int var19 = var4.nextInt(Math.max(1, var2 - var17));
            int var20 = var4.nextInt(4) == 0 ? 1052688 : (var4.nextBoolean() ? 4649190 : 15744200);
            var0.fill(var18, var19, var18 + var16, var19 + var17, var20 | (int)(50.0F + 90.0F * var5) << 24);
        }

        // A bright band rolling through, like a picture losing sync.
        int var21 = age * 6 % (var2 + 120) - 60;
        int var22 = (int)(70.0F * var5) << 24;
        var0.fillGradient(0, var21 - 30, var1, var21, 16777215, 16777215 | var22);
        var0.fillGradient(0, var21, var1, var21 + 30, 16777215 | var22, 16777215);
        if (var3 >= 0.5F) {
            // The edges of the view burn red as the chip nears its limit.
            int var23 = (int)((var5 - 0.4F) * (150.0F + 70.0F * MathHelper.sin((float)age * 0.6F))) << 24;
            int var24 = var2 / 4;
            var0.fillGradient(0, 0, var1, var24, 15744040 | var23, 15744040);
            var0.fillGradient(0, var2 - var24, var1, var2, 15744040, 15744040 | var23);
        }

        if (var3 >= 0.8F && var4.nextInt(var3 > 1.0F ? 5 : 11) == 0) {
            var0.fill(0, 0, var1, var2, 328965 | (int)(110.0F + 100.0F * (var3 - 0.8F)) << 24);
        }
    }

    private static void drawScream(DrawContext var0, int var1, int var2, float var3) {
        float var4 = ((float)(prankTotal - prankTicks) + var3) / (float)Math.max(1, prankTotal);
        float var5 = var4 < 0.12F ? var4 / 0.12F : (var4 > 0.8F ? (1.0F - var4) / 0.2F : 1.0F);
        var0.fill(0, 0, var1, var2, (age % 2 == 0 ? 15744040 : 1052688) | (int)(190.0F * var5) << 24);
        float var6 = (float)Math.max(var1, var2) * (0.9F + 0.5F * var4);
        float var7 = MathHelper.sin(((float)age + var3) * 3.1F) * 9.0F;
        float var8 = MathHelper.sin(((float)age + var3) * 4.3F) * 7.0F;
        VoltaRig.draw(var0, (float)var1 / 2.0F - var6 / 2.0F + var7, (float)var2 / 2.0F - var6 * 0.42F + var8, var6, var7 * 0.4F, Math.min(1.0F, var5 * 1.4F), var3);
        noise(var0, var1, var2, 1.2F);
    }

    private static void drawFakeDeath(DrawContext var0, MinecraftClient var1, int var2, int var3) {
        var0.fillGradient(0, 0, var2, var3, 1615855616, -1602211792);
        Text var4 = Text.translatable("deathScreen.title");
        var0.getMatrices().push();
        var0.getMatrices().scale(2.0F, 2.0F, 2.0F);
        var0.drawCenteredTextWithShadow(var1.textRenderer, var4, var2 / 2 / 2, 30, 16777215);
        var0.getMatrices().pop();
        var0.drawCenteredTextWithShadow(var1.textRenderer, Text.translatable("deathScreen.score").append(": ").append(Text.literal("0").formatted(Formatting.YELLOW)), var2 / 2, 100, 16777215);
        var0.drawCenteredTextWithShadow(var1.textRenderer, Text.translatable("deathScreen.respawn"), var2 / 2, var3 / 4 + 78, 10526880);
    }

    public static void panel(DrawContext var0, int var1, int var2, int var3, int var4, int var5) {
        byte var6 = 4;
        var0.fill(var1 + var6, var2, var3, var4 - var6, -1072688602);
        var0.fill(var1, var2 + var6, var3 - var6, var4, -1072688602);
        var0.fill(var1 + var6, var2, var3, var2 + 1, var5);
        var0.fill(var1, var4 - 1, var3 - var6, var4, var5);
        var0.fill(var1, var2 + var6, var1 + 1, var4, var5);
        var0.fill(var3 - 1, var2, var3, var4 - var6, var5);

        for (int var7 = 0; var7 < var6; var7++) {
            var0.fill(var1 + var6 - 1 - var7, var2 + var7, var1 + var6 - var7, var2 + var7 + 1, var5);
            var0.fill(var3 - var6 + var7, var4 - 1 - var7, var3 - var6 + var7 + 1, var4 - var7, var5);
        }

        for (int var8 = var2 + 2; var8 < var4 - 1; var8 += 3) {
            var0.fill(var1 + 2, var8, var3 - 2, var8 + 1, 205975782);
        }
    }

    private static void drawSpeech(DrawContext var0, MinecraftClient var1, int var2, int var3) {
        TextRenderer var4 = var1.textRenderer;
        int var5 = Math.min(220, var2 / 2);
        int var6 = var2 - 6;
        int var7 = var6 - var5;
        int var8 = var5 - 46;
        String var9 = line.getString();
        int var10 = Math.min(var9.length(), (int)((float)((long)age - lineStart) * 1.6F));
        StringBuilder var11 = new StringBuilder(var9.substring(0, var10));
        if (var10 < var9.length()) {
            String var12 = "#$%&*<>?/\\|01";
            var11.append(var12.charAt((age + var10) % var12.length()));
        }

        List var22 = var4.wrapLines(Text.literal(var11.toString()), var8);
        int var13 = Math.max(40, 14 + var22.size() * 10);
        int var14 = var3 - 64;
        int var15 = var14 - var13;
        int var16 = Math.max(0, 12 - (int)((long)age - lineStart)) * 8;
        var7 += var16;
        var6 += var16;
        panel(var0, var7, var15, var6, var14, -12128026);
        var0.enableScissor(var7 + 2, var15 - 22, var7 + 40, var14 - 2);
        VoltaRig.draw(var0, (float)(var7 - 7), (float)(var15 - 18), 56.0F, 0.0F, 1.0F, MinecraftClient.getInstance().getTickDelta());
        var0.disableScissor();
        var0.fill(var7 + 4, var15 + 37, var7 + 38, var15 + 38, -1033016);
        var0.drawText(var4, hud("VOLTA // LINK OK"), var7 + 42, var15 + 3, -13987943, false);
        int var17 = var15 + 13;

        for (OrderedText var19 : (Iterable<OrderedText>)(Object)(var22)) {
            var0.drawTextWithShadow(var4, var19, var7 + 42, var17, -1);
            var17 += 10;
        }
    }

    private static void drawPopup(DrawContext var0, int var1, int var2) {
        float var3 = MinecraftClient.getInstance().getTickDelta();
        int var4 = Math.max(1, prankTotal);
        float var5 = (float)(var4 - prankTicks) + var3;
        float var6 = MathHelper.clamp(var5 / 7.0F, 0.0F, 1.0F);
        float var7 = MathHelper.clamp(((float)prankTicks - var3) / 6.0F, 0.0F, 1.0F);
        float var8 = Math.min(easeOutBack(var6), var7 * var7);
        byte var9 = 64;
        float var10 = (float)var1 - (float)var9 * var8 - 2.0F;
        float var11 = (float)var2 / 3.0F + (1.0F - var6) * 10.0F;
        float var12 = MathHelper.sin(((float)age + var3) * 0.22F) * 5.0F * Math.min(var6, var7);
        float var13 = var5 < 7.0F ? 0.0F : MathHelper.clamp(1.0F - (var5 - 7.0F) / 8.0F, 0.0F, 1.0F);
        float var14 = 1.0F + 0.14F * MathHelper.sin(var13 * (float) Math.PI * 2.0F) * var13;
        var0.getMatrices().push();
        var0.getMatrices().translate(var10 + (float)var9 / 2.0F, var11 + (float)var9, 0.0F);
        var0.getMatrices().scale(var14, 1.0F / var14, 1.0F);
        var0.getMatrices().translate((float)(-var9) / 2.0F - 8.0F, (float)(-var9 - 18), 0.0F);
        VoltaRig.draw(var0, 0.0F, 0.0F, (float)(var9 + 16), var12, Math.min(1.0F, var8 * 1.5F), var3);
        var0.getMatrices().pop();

        String var15 = switch (prank) {
            case HUD, SLOT -> "?";
            default -> "★";
            case MIRROR -> "⇄";
            case TEASE -> "$";
            case HISS, HURT -> "!";
            case RENAME -> "✎";
            case SPIN, RUBBERBAND -> "↻";
            case JUMP -> "↑";
            case INVERT, SHUFFLE -> "⇄";
            case SCREAM, FAKE_DEATH, SHOVE, BLACKOUT, WHISTLE -> "!";
            case DROP -> "?";
            case GIFT -> "♥";
        };
        float var16 = MathHelper.clamp((var5 - 5.0F) / 5.0F, 0.0F, 1.0F);
        float var17 = 2.0F * (var16 < 1.0F ? easeOutBack(var16) : 1.0F) * var7;
        if (var17 > 0.05F) {
            float var18 = var10 - 6.0F;
            float var19 = var11 - 4.0F + MathHelper.sin(((float)age + var3) * 0.6F) * 2.0F;
            var0.getMatrices().push();
            var0.getMatrices().translate(var18, var19, 0.0F);
            var0.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-12.0F + var12));
            var0.getMatrices().scale(var17, var17, 1.0F);
            var0.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, hud(var15), -3, -4, -1033016);
            var0.getMatrices().pop();
        }

        int var22 = var6 < 1.0F ? 6 : 3;

        for (int var23 = 0; var23 < var22; var23++) {
            int var20 = (int)var11 + 10 + var23 * 9;
            int var21 = (int)((float)(24 + (age * 7 + var23 * 13) % 20) * Math.min(var6, var7));
            var0.fill((int)var10 - 6 - var21 - var23 * 4, var20, (int)var10 - 6 - var23 * 4, var20 + 1, -2142834458);
        }
    }

    private static int popupFace() {
        if (prank == HostPrank.SCREAM) {
            return 4;
        }

        return prank != HostPrank.HISS && prank != HostPrank.HURT && prank != HostPrank.MIRROR && prank != HostPrank.INVERT && prank != HostPrank.FAKE_DEATH
            ? (prank != HostPrank.TEASE && prank != HostPrank.SLOT ? (prank == HostPrank.GLITCH ? 7 : 6) : 1)
            : 5;
    }

    private static float easeOutBack(float var0) {
        float var1 = 1.70158F;
        float var2 = var1 + 1.0F;
        float var3 = var0 - 1.0F;
        return 1.0F + var2 * var3 * var3 * var3 + var1 * var3 * var3;
    }

    private static void drawFinder(DrawContext var0, MinecraftClient var1, int var2) {
        ClientPlayerEntity var3 = var1.player;
        Vec3d var4 = found.subtract(var3.getPos());
        double var5 = Math.toDegrees(Math.atan2(-var4.x, var4.z));
        double var7 = MathHelper.wrapDegrees(var5 - (double)var3.getYaw());
        int var9 = Math.min(120, var2 / 3);
        int var10 = var2 / 2;
        byte var11 = 24;
        var0.fill(var10 - var9, var11 + 6, var10 + var9, var11 + 7, -2142834458);

        for (byte var12 = -90; var12 <= 90; var12 += 15) {
            int var13 = var10 + (int)((double)var12 / 90.0 * (double)var9);
            var0.fill(var13, var11 + 4, var13 + 1, var11 + 9, 1615261926);
        }

        int var18 = foundKind == 2 ? -1033016 : -12128026;
        int var19 = var10 + (int)(MathHelper.clamp(var7, -90.0, 90.0) / 90.0 * (double)var9);
        TextRenderer var14 = var1.textRenderer;
        var0.drawTextWithShadow(var14, "⌜", var19 - 6, var11 - 1, var18);
        var0.drawTextWithShadow(var14, "⌟", var19 + 2, var11 + 6, var18);
        var0.fill(var19, var11 + 2, var19 + 1, var11 + 11, var18);
        Text var15 = hud((int)Math.round(var4.length()) + " m");
        Object var16 = foundKind == 2 ? Text.translatable("hud.windyorigins.volta.target_player").append(" ").append(foundLabel) : foundLabel;
        var0.drawTextWithShadow(var14, var15, var19 - var14.getWidth(var15) / 2, var11 + 13, -1);
        var0.drawTextWithShadow(var14, (Text)var16, var19 - var14.getWidth((StringVisitable)var16) / 2, var11 + 23, var18);
        if (Math.abs(var7) > 90.0) {
            String var17 = var7 > 0.0 ? "»" : "«";
            var0.drawTextWithShadow(var14, var17, var7 > 0.0 ? var10 + var9 + 4 : var10 - var9 - 10, var11 + 2, var18);
        }
    }

    public static Text hud(String var0) {
        for (int i = 0; i < var0.length(); i++) {
            if (var0.charAt(i) > '\u017f') {
                return Text.literal(var0);
            }
        }

        return Text.literal(var0).setStyle(Style.EMPTY.withFont(HUD_FONT));
    }

    static int pink() {
        return -1033016;
    }

    static int neon() {
        return -12128026;
    }

    static void unused() {
        RenderSystem.enableBlend();
    }
}
