package com.windykindy49.windyorigins.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.windykindy49.windyorigins.host.HostNetworking;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import com.windykindy49.windyorigins.client.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Volta's energy. Everything here is pixel art drawn live: each frame the shapes are rasterised
 * onto a coarse grid of fat pixels, given an ink outline, and thrown at the Host's screen with
 * deliberate damage: torn rows, colour ghosts, dropped lines, afterimages. Nothing eases, nothing
 * is smooth; poses jump tick to tick. It is seen by the Host alone but is pinned to the world, so a
 * mass that swells at the edge of the view really does reach the block it is working on.
 */
public final class VoltaFx {
    private static final int INK = 0x0B1030;
    private static final int CYAN = 0x45F0E6;
    private static final int MAGENTA = 0xF03CC8;
    /** Alpha marker for cells that take no ink outline (speed lines, flecks). */
    private static final int BARE = 0xFE000000;
    private static final int SOLID = 0xFF000000;
    private static final String[] HIT_WORDS = new String[]{"ZAP!", "POW!", "BAM!", "KZZT!"};
    private static final String[] WORK_WORDS = new String[]{"KRAK", "CHOMP", "NOM", "SNAP", "YOINK"};
    /** Indexed by HostNetworking.WORD_*. */
    private static final String[] SAID = new String[]{"BLOCK!", "SLOOOW", "PING", "PATCHED", "MINE!", "SLAM!", "PSST", "ONLINE", "LIGHTS OUT", "SLURP", "INFECTED"};
    /** Pellet slot for what an arm hauls out of a container. */
    private static final int SLOT_HAUL = 40;
    private static final int JOINTS = 22;
    private static final float[] JX = new float[JOINTS];
    private static final float[] JY = new float[JOINTS];
    private static final float[] JR = new float[JOINTS];
    // The curve of the arm drawn last, so that what rides along it can be placed.
    private static float curveOx;
    private static float curveOy;
    private static float curveCx;
    private static float curveCy;
    private static float curveTx;
    private static float curveTy;
    private static float curveNx;
    private static float curveNy;
    /** Item icons to put on top of the canvas this frame: raw id, x, y, size, turn. */
    private static final List<float[]> ICONS = new ArrayList<>();
    private static final List<Fx> ACTIVE = new ArrayList<>();
    private static final List<Fleck> FLECKS = new ArrayList<>();
    private static final List<Pellet> PELLETS = new ArrayList<>();
    private static final List<Word> WORDS = new ArrayList<>();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f VIEW = new Matrix4f();
    private static Vec3d cameraPos = Vec3d.ZERO;
    private static boolean captured;
    private static int screenW = 480;
    private static int screenH = 270;
    private static float kick;
    private static float prevKick;
    private static float glitch;
    private static int impactTicks;
    private static float impactX;
    private static float impactY;
    private static int impactColor;
    // The pixel canvas.
    private static int cell = 3;
    private static int gw;
    private static int gh;
    private static int[] grid = new int[0];
    private static int[] inked = new int[0];
    private static int[] runs = new int[4096];
    private static int runCount;
    private static int[] lastRuns = new int[0];
    private static int lastRunCount;
    private static int[] ghostRuns = new int[0];
    private static int ghostRunCount;
    private static int ghostCell = 3;
    private static int lastBeat = -1;
    private static BufferBuilder batch;
    private static Matrix4f batchMatrix;

    private VoltaFx() {
    }

    private static final class Fx {
        final int kind;
        final int slot;
        final int casterId;
        int entityId;
        Vec3d pos;
        Vec3d from;
        final Vec3d source;
        final int color;
        int life;
        int age;
        int total;
        int punch = 99;
        final long seed;
        boolean landed;
        /** The target is a place on the screen (fractions of its width and height), not in the world. */
        boolean screen;
        /** A word of its own to shout when it lands. */
        String say;

        Fx(int kind, int slot, int casterId, int entityId, Vec3d pos, Vec3d source, int color, int ticks) {
            this.kind = kind;
            this.slot = slot;
            this.casterId = casterId;
            this.entityId = entityId;
            this.pos = pos;
            this.from = pos;
            this.source = source;
            this.color = color;
            this.total = ticks;
            this.life = kind == HostNetworking.FX_BOLT ? ticks + 5 : ticks;
            this.seed = (long)(Math.random() * 100000.0);
        }

        int launch() {
            return Math.max(0, this.total - 2);
        }
    }

    /** A loose pixel thrown off by an impact; lives in screen space. */
    private static final class Fleck {
        float x;
        float y;
        float vx;
        float vy;
        int size;
        int life;
        final int color;

        Fleck(float x, float y, float vx, float vy, int size, int life, int color) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.size = size;
            this.life = life;
            this.color = color;
        }
    }

    /** A chunk travelling along an arm: block rubble drawn back to the screen, or a load sent out. */
    private static final class Pellet {
        final int slot;
        float along;
        final float speed;
        final float side;
        final int color;

        Pellet(int slot, float along, float speed, float side, int color) {
            this.slot = slot;
            this.along = along;
            this.speed = speed;
            this.side = side;
            this.color = color;
        }
    }

    /** A comic-book sound effect popping out of an impact. */
    private static final class Word {
        final String text;
        final float x;
        final float y;
        final int color;
        final float turn;
        int age;
        final int life;

        Word(String text, float x, float y, int color, float turn, int life) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
            this.turn = turn;
            this.life = life;
        }
    }

    public static void clear() {
        ACTIVE.clear();
        FLECKS.clear();
        PELLETS.clear();
        WORDS.clear();
        kick = 0.0F;
        glitch = 0.0F;
        impactTicks = 0;
    }

    public static void add(int kind, int slot, int casterId, int entityId, Vec3d pos, Vec3d source, int color, int ticks) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!own(client, casterId)) {
            return;
        }

        if (kind == HostNetworking.FX_STOP) {
            if (slot == 2) {
                ACTIVE.clear();
                PELLETS.clear();
            } else {
                int gone = slot == 1 ? HostNetworking.FX_SELF : HostNetworking.FX_TENDRIL;

                for (Fx fx : ACTIVE) {
                    // Only the arms that work, gather and drink; a cable or a pointer has its own time.
                    if (fx.kind == gone && (gone != HostNetworking.FX_TENDRIL || fx.slot <= HostNetworking.SLOT_DRAIN)) {
                        fx.life = Math.min(fx.life, 2);
                    }
                }
            }

            return;
        }

        if (kind == HostNetworking.FX_TENDRIL) {
            for (Fx fx : ACTIVE) {
                if (fx.kind == kind && fx.slot == slot) {
                    // Same arm, new place to grip: it whips across and hits.
                    fx.from = current(fx, client);
                    fx.pos = pos;
                    fx.entityId = entityId;
                    fx.life = ticks;
                    fx.total = ticks;
                    fx.age = Math.min(fx.age, 2) + 100;
                    fx.punch = -2;
                    return;
                }
            }
        }

        if (kind == HostNetworking.FX_WORD) {
            float[] at = entityId == -2 || !captured ? new float[]{(float)screenW * 0.5F, (float)screenH * 0.4F, 1.0F} : project(pos);
            if (at[2] != 0.0F && slot >= 0 && slot < SAID.length) {
                word(SAID[slot], MathHelper.clamp(at[0], 60.0F, (float)screenW - 60.0F), MathHelper.clamp(at[1] - 26.0F * scale(), 30.0F, (float)screenH - 30.0F), color == 0 ? 0xFFFFFF : color, ticks);
                glitch = Math.max(glitch, 0.5F);
            }

            return;
        }

        if (kind == HostNetworking.FX_DECOY || kind == HostNetworking.FX_NODE) {
            ACTIVE.removeIf(fx -> fx.kind == kind);
        }

        if (kind == HostNetworking.FX_STRING || kind == HostNetworking.FX_TAG) {
            ACTIVE.removeIf(fx -> fx.kind == kind && fx.entityId == entityId && (kind != HostNetworking.FX_TAG || fx.slot == slot));
        }

        if (kind == HostNetworking.FX_STAMP) {
            ACTIVE.removeIf(fx -> fx.kind == kind && fx.slot == slot);
        }

        Fx fx = new Fx(kind, slot, casterId, entityId, pos, source, color, ticks);
        if (kind == HostNetworking.FX_TENDRIL) {
            fx.punch = -2;
        }

        ACTIVE.add(fx);
        if (kind == HostNetworking.FX_RUSH) {
            glitch = 1.0F;
            kick = 1.0F;
            word(slot == 0 ? "ZIP!" : "<< REWIND", (float)screenW * 0.5F, (float)screenH * 0.3F, slot == 0 ? 0xFFFFFF : MAGENTA, 8);
        } else if (kind == HostNetworking.FX_STRING) {
            glitch = Math.max(glitch, 0.5F);
        }

        if (kind == HostNetworking.FX_PULSE && slot == 0) {
            glitch = Math.max(glitch, 0.8F);
            kick = Math.max(kick, 0.5F);
            word("VWOOM", (float)screenW * 0.5F, (float)screenH * 0.78F, color, 8);
        } else if (kind == HostNetworking.FX_SELF) {
            glitch = Math.max(glitch, 0.6F);
        }
    }

    /** An effect started on this side, for the Host's own eyes: Volta's pranks have hands too. */
    private static Fx local(int kind, int slot, Vec3d pos, int color, int ticks, String say) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return null;
        }

        Fx fx = new Fx(kind, slot, client.player.getId(), -3, pos, pos, color, ticks);
        fx.screen = true;
        fx.say = say;
        if (kind == HostNetworking.FX_TENDRIL) {
            fx.punch = -2;
        }

        ACTIVE.removeIf(old -> old.kind == kind && old.slot == slot && old.screen);
        ACTIVE.add(fx);
        return fx;
    }

    /** What her hands do while she plays a prank. */
    public static void prank(com.windykindy49.windyorigins.host.HostPrank prank) {
        Vec3d hotbar = new Vec3d(0.5, 0.955, 0.0);
        switch (prank) {
            case SLOT -> local(HostNetworking.FX_TENDRIL, HostNetworking.SLOT_POKE, hotbar, CYAN, 12, "OOPS");
            case SHUFFLE -> local(HostNetworking.FX_TENDRIL, HostNetworking.SLOT_POKE, hotbar, MAGENTA, 12, "SHUFFLE");
            case DROP -> local(HostNetworking.FX_TENDRIL, HostNetworking.SLOT_POKE, new Vec3d(0.8, 0.8, 0.0), CYAN, 12, "YOINK!");
            case SHOVE -> local(HostNetworking.FX_STAMP, HostNetworking.STAMP_PALM, Vec3d.ZERO, MAGENTA, 8, "SHOVE!");
            case JUMP -> local(HostNetworking.FX_STAMP, HostNetworking.STAMP_PALM, Vec3d.ZERO, CYAN, 8, "HUP!");
            case BLACKOUT -> local(HostNetworking.FX_STAMP, HostNetworking.STAMP_HANDS, Vec3d.ZERO, CYAN, 14, "GUESS WHO");
            case RUBBERBAND -> {
                Fx fx = local(HostNetworking.FX_RUSH, 1, Vec3d.ZERO, CYAN, 7, null);
                if (fx != null) {
                    glitch = 1.0F;
                    kick = 1.0F;
                }
            }
            case WHISTLE -> {
                word("FWEEEET!", (float)screenW * 0.5F, (float)screenH * 0.35F, 0xFFFFFF, 12);
                glitch = 1.0F;
            }
            case SPIN -> {
                word("WHEEE", (float)screenW * 0.5F, (float)screenH * 0.35F, MAGENTA, 8);
                local(HostNetworking.FX_RUSH, 0, Vec3d.ZERO, MAGENTA, 6, null);
            }
            default -> {
            }
        }
    }

    /** Where an arm ends, in screen pixels; the third value is 0 when that place is behind the camera. */
    private static float[] tipOf(Fx fx, MinecraftClient client) {
        return fx.screen ? new float[]{(float)fx.pos.x * (float)screenW, (float)fx.pos.y * (float)screenH, 1.0F} : project(current(fx, client));
    }

    private static boolean own(MinecraftClient client, int casterId) {
        return client.player != null && client.player.getId() == casterId;
    }

    /** Whether a full cast (face first, then the shot) is playing for the local Host. */
    public static boolean castingFace() {
        for (Fx fx : ACTIVE) {
            if (fx.kind == HostNetworking.FX_BOLT && fx.slot == 0 && fx.age <= fx.total + 1) {
                return true;
            }
        }

        return false;
    }

    public static float kick(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevKick, kick);
    }

    private static void play(MinecraftClient client, SoundEvent sound, float volume, float pitch) {
        client.getSoundManager().play(PositionedSoundInstance.master(sound, pitch, volume));
    }

    private static void splash(float x, float y, int count, int color, float force) {
        float sc = scale();

        for (int i = 0; i < count; i++) {
            double angle = Math.random() * Math.PI * 2.0;
            float speed = (float)(3.0 + Math.random() * 9.0) * force * sc;
            int tint = Math.random() < 0.25 ? 0xFFFFFF : (Math.random() < 0.2 ? MAGENTA : color);
            FLECKS.add(
                new Fleck(x, y, (float)Math.cos(angle) * speed, (float)Math.sin(angle) * speed - 3.0F * sc, Math.random() < 0.3 ? 2 : 1, 4 + (int)(Math.random() * 7.0), tint)
            );
        }
    }

    private static void word(String text, float x, float y, int color, int life) {
        WORDS.add(new Word(text, x, y, color, (float)(Math.random() * 30.0 - 15.0), life));
    }

    public static void tick(MinecraftClient client) {
        prevKick = kick;
        kick *= 0.6F;
        glitch *= 0.72F;
        if (impactTicks > 0) {
            impactTicks--;
        }

        if (client.world == null) {
            clear();
            return;
        }

        FLECKS.removeIf(fleck -> {
            fleck.x += fleck.vx;
            fleck.y += fleck.vy;
            fleck.vy += 1.6F * scale();
            return --fleck.life <= 0;
        });
        PELLETS.removeIf(pellet -> {
            pellet.along += pellet.speed;
            return pellet.along < 0.0F || pellet.along > 1.0F;
        });
        WORDS.removeIf(word -> ++word.age > word.life);
        Iterator<Fx> it = ACTIVE.iterator();

        while (it.hasNext()) {
            Fx fx = it.next();
            fx.age++;
            fx.life--;
            fx.punch++;
            if (fx.kind == HostNetworking.FX_TENDRIL && fx.entityId >= 0 && !fx.screen) {
                // An arm has nothing to hold once its quarry is gone.
                Entity held = client.world.getEntityById(fx.entityId);
                if (held == null || !held.isAlive()) {
                    fx.life = Math.min(fx.life, 3);
                }
            }

            if (fx.kind == HostNetworking.FX_BOLT) {
                if (fx.age == fx.launch()) {
                    kick = 1.0F;
                    glitch = 1.0F;
                    play(client, SoundEvents.ITEM_TRIDENT_RIPTIDE_3, 0.5F, 2.0F);
                    play(client, SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, 0.5F, 1.9F);
                }

                if (!fx.landed && fx.age >= fx.total) {
                    fx.landed = true;
                    float[] at = project(current(fx, client));
                    impactTicks = 3;
                    impactX = at[0];
                    impactY = at[1];
                    impactColor = fx.color;
                    kick = 1.0F;
                    glitch = 1.0F;
                    splash(at[0], at[1], fx.slot == 0 ? 26 : 12, fx.color, 1.5F);
                    if (fx.slot == 0) {
                        word(HIT_WORDS[(int)(Math.random() * (double)HIT_WORDS.length)], at[0], at[1] - 34.0F * scale(), 0xFFFFFF, 9);
                    }

                    play(client, SoundEvents.ENTITY_GENERIC_EXPLODE, 0.25F, 2.0F);
                    play(client, SoundEvents.ENTITY_SLIME_SQUISH, 0.6F, 1.6F);
                }
            } else if (fx.kind == HostNetworking.FX_GRAB) {
                tickGrab(client, fx);
            } else if (fx.kind == HostNetworking.FX_SWAP) {
                tickSwap(client, fx);
            } else if (fx.kind == HostNetworking.FX_STRING) {
                Entity held = fx.entityId >= 0 ? client.world.getEntityById(fx.entityId) : null;
                if (fx.entityId >= 0 && (held == null || !held.isAlive())) {
                    fx.life = Math.min(fx.life, 3);
                }

                if (fx.age == 1) {
                    play(client, SoundEvents.ITEM_CROSSBOW_LOADING_END, 0.7F, 1.8F);
                }
            } else if (fx.kind == HostNetworking.FX_RUSH && fx.age == 1) {
                play(client, SoundEvents.ITEM_TRIDENT_RIPTIDE_1, 0.5F, fx.slot == 0 ? 2.0F : 0.6F);
            } else if (fx.kind == HostNetworking.FX_TAG) {
                Entity marked = fx.entityId >= 0 ? client.world.getEntityById(fx.entityId) : null;
                if (marked == null || !marked.isAlive()) {
                    fx.life = Math.min(fx.life, 3);
                }
            } else if (fx.kind == HostNetworking.FX_STAMP) {
                tickStamp(client, fx);
            } else if (fx.kind == HostNetworking.FX_TENDRIL && (fx.slot == HostNetworking.SLOT_POINT || fx.slot >= HostNetworking.SLOT_TRACE)) {
                // It only points; nothing is struck.
                fx.punch = 99;
                if (fx.age == 2) {
                    float[] at = tipOf(fx, client);
                    if (fx.slot == HostNetworking.SLOT_POINT) {
                        if (at[2] != 0.0F) {
                            word("THERE!", MathHelper.clamp(at[0], 60.0F, (float)screenW - 60.0F), MathHelper.clamp(at[1] - 30.0F * scale(), 30.0F, (float)screenH - 30.0F), 0xFFFFFF, 10);
                        }

                        play(client, SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), 0.6F, 1.8F);
                    } else {
                        splash(at[0], at[1], 4, fx.color, 0.6F);
                        play(client, SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), 0.3F, 1.2F + 0.1F * (float)(fx.slot - HostNetworking.SLOT_TRACE));
                    }
                }
            } else if (fx.kind == HostNetworking.FX_TENDRIL && fx.slot >= HostNetworking.SLOT_SHOVE && fx.slot <= HostNetworking.SLOT_CABLE) {
                int slot = fx.slot;
                boolean heavy = slot == HostNetworking.SLOT_SHOVE || slot == HostNetworking.SLOT_YANK;
                boolean staged = heavy || slot == HostNetworking.SLOT_INJECT || slot == HostNetworking.SLOT_SLAP;
                if (staged && fx.age == 6) {
                    // The first touch was only the wind-up: this is the throw, the plunger, the slap.
                    fx.punch = 0;
                }

                if (fx.punch == 0 && slot == HostNetworking.SLOT_CABLE) {
                    // A pulse down the cable.
                    for (int i = 0; i < 4; i++) {
                        PELLETS.add(new Pellet(slot, 0.0F, 0.2F + (float)Math.random() * 0.12F, (float)(Math.random() - 0.5) * 0.3F, i % 2 == 0 ? 0xFFFFFF : fx.color));
                    }

                    glitch = Math.max(glitch, 0.35F);
                } else if (fx.punch == 0) {
                    float[] at = tipOf(fx, client);
                    boolean second = staged && fx.age >= 6;
                    splash(at[0], at[1], second ? 20 : 9, fx.color, second ? 1.6F : 1.0F);
                    kick = Math.max(kick, second && heavy ? 1.0F : 0.55F);
                    glitch = Math.max(glitch, second ? 1.0F : 0.6F);
                    String said = fx.say;
                    if (said == null) {
                        said = switch (slot) {
                            case HostNetworking.SLOT_POKE -> "CLICK";
                            case HostNetworking.SLOT_SHOVE -> second ? "YEET!" : "GRAB";
                            case HostNetworking.SLOT_YANK -> second ? "C'MERE!" : "GRAB";
                            case HostNetworking.SLOT_INJECT -> second ? null : "JAB";
                            case HostNetworking.SLOT_PLANT -> "PLOP";
                            default -> null;
                        };
                    }

                    if (said != null) {
                        word(said, MathHelper.clamp(at[0], 50.0F, (float)screenW - 50.0F), at[1] - (second ? 34.0F : 22.0F) * scale(), 0xFFFFFF, second ? 9 : 6);
                    }

                    if (second && heavy) {
                        impactTicks = 3;
                        impactX = at[0];
                        impactY = at[1];
                        impactColor = fx.color;
                        play(client, SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.8F, 0.7F);
                        play(client, SoundEvents.ITEM_TRIDENT_RIPTIDE_3, 0.4F, 1.6F);
                    } else if (slot == HostNetworking.SLOT_POKE) {
                        play(client, SoundEvents.BLOCK_STONE_BUTTON_CLICK_ON, 0.7F, 1.6F);
                    } else if (slot == HostNetworking.SLOT_INJECT) {
                        play(client, second ? SoundEvents.ITEM_HONEY_BOTTLE_DRINK : SoundEvents.ENTITY_BEE_STING, 0.7F, second ? 1.6F : 1.4F);
                    } else if (slot == HostNetworking.SLOT_SLAP && second) {
                        play(client, SoundEvents.ENTITY_PLAYER_ATTACK_WEAK, 0.9F, 0.6F);
                    } else {
                        play(client, SoundEvents.ENTITY_SLIME_SQUISH, 0.6F, 1.2F);
                    }
                }
            } else if (fx.kind == HostNetworking.FX_TENDRIL) {
                boolean drain = fx.slot == HostNetworking.SLOT_DRAIN;
                if (drain && fx.age % 2 == 0) {
                    PELLETS.add(new Pellet(fx.slot, 1.0F, -0.17F, (float)(Math.random() - 0.5), Math.random() < 0.5 ? 0xFF3050 : 0xFFFFFF));
                }

                if (fx.punch == 0) {
                    // The grip lands: rubble in the block's own colour is hauled back up the arm.
                    Vec3d target = fx.screen ? client.player.getPos() : current(fx, client);
                    float[] at = tipOf(fx, client);
                    BlockPos block = BlockPos.ofFloored(target);
                    BlockState state = client.world.getBlockState(block);
                    boolean empty = fx.screen || state.isAir();
                    int rubble = empty ? fx.color : state.getMapColor(client.world, block).color;
                    if (rubble == 0) {
                        rubble = 0x9A9A9A;
                    }

                    if (!drain) {
                        for (int i = 0; i < 5; i++) {
                            PELLETS.add(
                                new Pellet(fx.slot, empty ? 0.0F : 1.0F, (empty ? 1.0F : -1.0F) * (0.2F + (float)Math.random() * 0.14F), (float)(Math.random() - 0.5), i % 2 == 0 ? rubble : mix(rubble, INK, 0.3F))
                            );
                        }
                    }

                    splash(at[0], at[1], 9, rubble, 1.0F);
                    kick = Math.max(kick, 0.55F);
                    glitch = Math.max(glitch, 0.7F);
                    if (Math.random() < 0.34) {
                        word(WORK_WORDS[(int)(Math.random() * (double)WORK_WORDS.length)], at[0], at[1] - 22.0F * scale(), 0xFFFFFF, 6);
                    }

                    play(client, SoundEvents.ENTITY_SLIME_SQUISH_SMALL, 0.5F, 1.3F + (float)Math.random() * 0.6F);
                }
            } else if (fx.kind == HostNetworking.FX_ARC && fx.age == 1) {
                float[] at = project(fx.pos);
                splash(at[0], at[1], 7, fx.color, 1.0F);
                glitch = Math.max(glitch, 0.6F);
                if (Math.random() < 0.4) {
                    word("BZZT", at[0], at[1] - 18.0F * scale(), 0xFFFFFF, 5);
                }
            }

            if (fx.life <= 0) {
                it.remove();
            }
        }
    }

    /** Fetching: reach, grip the lid on the third tick, haul the thing back, swallow it at the edge. */
    private static void tickGrab(MinecraftClient client, Fx fx) {
        float sc = scale();
        if (fx.age == 3) {
            float[] at = project(fx.pos);
            splash(at[0], at[1], 14, GOLD, 1.2F);
            word("YOINK!", MathHelper.clamp(at[0], 60.0F, (float)screenW - 60.0F), at[1] - 30.0F * sc, 0xFFFFFF, 8);
            kick = Math.max(kick, 0.7F);
            glitch = Math.max(glitch, 0.8F);
            play(client, SoundEvents.ENTITY_SLIME_SQUISH, 0.7F, 1.1F);
        } else if (fx.age > 3 && fx.age < 15 && fx.age % 2 == 0) {
            PELLETS.add(new Pellet(SLOT_HAUL, 1.0F - (float)(fx.age - 4) / 11.0F, -0.1F, (float)(Math.random() - 0.5), Math.random() < 0.5 ? GOLD : 0xFFFFFF));
        } else if (fx.age == 15) {
            splash((float)screenW - 10.0F * sc, (float)screenH * 0.62F, 16, GOLD, 1.3F);
            word("GOT IT", (float)screenW - 70.0F * sc, (float)screenH * 0.62F - 30.0F * sc, GOLD, 8);
            kick = Math.max(kick, 0.6F);
            play(client, SoundEvents.ENTITY_ITEM_PICKUP, 0.8F, 0.7F);
            play(client, SoundEvents.ENTITY_PLAYER_BURP, 0.25F, 1.6F);
        }
    }

    /** Trading places: both hands close on the target, and on the appointed tick the picture is wrenched over. */
    private static void tickSwap(MinecraftClient client, Fx fx) {
        float sc = scale();
        if (fx.age == 2) {
            float[] at = project(current(fx, client));
            splash(at[0], at[1], 12, fx.color, 1.0F);
            word("GOTCHA", MathHelper.clamp(at[0], 60.0F, (float)screenW - 60.0F), at[1] - 34.0F * sc, 0xFFFFFF, 5);
            glitch = Math.max(glitch, 0.7F);
            play(client, SoundEvents.ENTITY_SLIME_SQUISH, 0.7F, 0.9F);
        } else if (fx.age == fx.slot) {
            impactTicks = 3;
            impactX = (float)screenW * 0.5F;
            impactY = (float)screenH * 0.5F;
            impactColor = MAGENTA;
            kick = 1.0F;
            glitch = 1.0F;
            splash(impactX, impactY, 30, fx.color, 2.0F);
            word("SWAP!", impactX, impactY - 40.0F * sc, 0xFFFFFF, 10);
            play(client, SoundEvents.ITEM_TRIDENT_RIPTIDE_3, 0.6F, 1.4F);
            play(client, SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.9F, 1.6F);
        }
    }

    private static final int GOLD = 0xFFD24A;

    /** The pieces drawn on the screen itself: what each one does on the tick it lands. */
    private static void tickStamp(MinecraftClient client, Fx fx) {
        float sc = scale();
        float cx = (float)screenW * 0.5F;
        if (fx.age == 1 && fx.say != null) {
            word(fx.say, cx, (float)screenH * 0.3F, 0xFFFFFF, 9);
        }

        switch (fx.slot) {
            case HostNetworking.STAMP_PALM -> {
                if (fx.age == 2) {
                    impactTicks = 3;
                    impactX = cx;
                    impactY = (float)screenH * 0.6F;
                    impactColor = fx.color;
                    kick = 1.0F;
                    glitch = 1.0F;
                    splash(cx, (float)screenH * 0.55F, 24, fx.color, 1.8F);
                    play(client, SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.9F, 0.6F);
                }
            }
            case HostNetworking.STAMP_CLOCK -> {
                if (fx.age % 5 == 1) {
                    play(client, SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 0.35F, 0.6F);
                }
            }
            case HostNetworking.STAMP_MEND -> {
                if (fx.age % 4 == 1 && fx.life > 6) {
                    PELLETS.add(new Pellet(SLOT_HAUL + 1, 0.0F, 0.16F, (float)(Math.random() - 0.5) * 0.4F, Math.random() < 0.5 ? 0xFFFFFF : fx.color));
                    PELLETS.add(new Pellet(SLOT_HAUL + 2, 0.0F, 0.16F, (float)(Math.random() - 0.5) * 0.4F, Math.random() < 0.5 ? 0xFFFFFF : fx.color));
                }

                if (fx.age == 3) {
                    play(client, SoundEvents.ENTITY_SLIME_SQUISH_SMALL, 0.6F, 1.6F);
                }
            }
            case HostNetworking.STAMP_EYES -> {
                if (fx.age == 1) {
                    glitch = Math.max(glitch, 0.8F);
                    play(client, SoundEvents.BLOCK_BEACON_POWER_SELECT, 0.5F, 2.0F);
                }
            }
            case HostNetworking.STAMP_CRACK -> {
                if (fx.age == 1) {
                    impactTicks = 3;
                    impactX = (float)screenW * 0.82F;
                    impactY = (float)screenH * 0.2F;
                    impactColor = 0xFFFFFF;
                    kick = 1.0F;
                    glitch = 1.0F;
                    word("CRACK!", (float)screenW * 0.7F, (float)screenH * 0.32F, 0xFFFFFF, 12);
                    splash(impactX, impactY, 30, 0xFFFFFF, 2.0F);
                }
            }
            case HostNetworking.STAMP_HANDS -> {
                if (fx.age == 3) {
                    kick = Math.max(kick, 0.7F);
                    play(client, SoundEvents.ENTITY_SLIME_SQUISH, 0.8F, 0.7F);
                }
            }
            default -> {
            }
        }
    }

    /** Where the effect currently points: it follows its target entity while that still exists. */
    private static Vec3d current(Fx fx, MinecraftClient client) {
        Vec3d target = fx.pos;
        if (fx.entityId >= 0 && client.world != null) {
            Entity entity = client.world.getEntityById(fx.entityId);
            if (entity != null && entity.isAlive()) {
                target = entity.getPos().add(0.0, (double)entity.getHeight() * 0.6, 0.0);
                fx.pos = target;
            }
        }

        // A retargeted arm takes two ticks to whip across; no easing.
        if (fx.kind == HostNetworking.FX_TENDRIL && fx.age >= 100 && fx.age < 102) {
            return fx.from.lerp(target, 0.5 * (double)(fx.age - 99));
        }

        return target;
    }

    private static double hash(long seed, int a, int b) {
        long h = seed * 0x9E3779B97F4A7C15L + (long)a * 0xC2B2AE3D27D4EB4FL + (long)b * 0x165667B19E3779F9L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (double)(h & 0xFFFFFFL) / (double)0xFFFFFF - 0.5;
    }

    private static float scale() {
        return Math.max(0.75F, (float)screenH / 360.0F);
    }

    // ------------------------------------------------------------------ world

    /** Remembers the camera for this frame, so the screen pass can pin its drawings to the world. */
    public static void renderWorld(WorldRenderContext context) {
        PROJECTION.set(context.projectionMatrix());
        VIEW.set(context.matrixStack().peek().getPositionMatrix());
        cameraPos = context.camera().getPos();
        captured = true;
        if (context.consumers() == null) {
            return;
        }

        for (Fx fx : ACTIVE) {
            if (fx.kind == HostNetworking.FX_DECOY) {
                hologram(context.matrixStack(), context.consumers(), context.camera(), fx);
            }
        }
    }

    /** World point to screen point. The third value is 0 when the point is behind the camera. */
    private static float[] project(Vec3d world) {
        Vector4f v = new Vector4f((float)(world.x - cameraPos.x), (float)(world.y - cameraPos.y), (float)(world.z - cameraPos.z), 1.0F);
        v.mul(VIEW);
        v.mul(PROJECTION);
        if (v.w <= 0.05F) {
            float len = Math.max(0.001F, (float)Math.sqrt((double)(v.x * v.x + v.y * v.y)));
            return new float[]{(float)screenW * 0.5F + v.x / len * (float)screenW * 1.5F, (float)screenH * 0.5F - v.y / len * (float)screenH * 1.5F, 0.0F};
        }

        return new float[]{(v.x / v.w * 0.5F + 0.5F) * (float)screenW, (1.0F - (v.y / v.w * 0.5F + 0.5F)) * (float)screenH, 1.0F};
    }

    /** The decoy: Volta's sprite standing in the world, flickering and slipping like a bad signal. */
    private static void hologram(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, Fx fx) {
        Vec3d cam = camera.getPos();
        int step = fx.age / 2;
        if (hash(fx.seed, step, 0) > 0.36 || fx.age < 2 && fx.age % 2 == 1) {
            return;
        }

        float width = 0.95F;
        float height = 1.42F;
        int alpha = fx.life < 6 && fx.life % 2 == 0 ? 90 : 235;
        matrices.push();
        matrices.translate(fx.pos.x - cam.x, fx.pos.y + (double)height / 2.0 + 0.05 + (double)(step % 4 == 0 ? 0.0625F : 0.0F) - cam.y, fx.pos.z - cam.z);
        matrices.multiply(new org.joml.Quaternionf().rotationY((float)Math.toRadians((double)(180.0F - camera.getYaw()))));
        int frame = 2 + fx.age / 6 % 2;
        float u0 = (float)(frame * 32) / 256.0F;
        float u1 = (float)((frame + 1) * 32) / 256.0F;
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(HostClient.SHEET));
        int strips = 6;

        for (int i = 0; i < strips; i++) {
            // Now and then a strip of her tears sideways.
            float slip = hash(fx.seed, step, 10 + i) > 0.3 ? (float)hash(fx.seed, step, 20 + i) * 0.3F : 0.0F;
            float y0 = -height / 2.0F + height * (float)i / (float)strips;
            float y1 = -height / 2.0F + height * (float)(i + 1) / (float)strips;
            float v1 = 0.1875F * (1.0F - (float)i / (float)strips);
            float v0 = 0.1875F * (1.0F - (float)(i + 1) / (float)strips);
            HostClient.quad(
                buffer, matrices.peek().getPositionMatrix(), matrices.peek().getNormalMatrix(), -width / 2.0F + slip, y0, width / 2.0F + slip, y1, u0, v0, u1, v1, alpha, 190, 255, 250
            );
        }

        matrices.pop();
    }

    // ------------------------------------------------------------------ the pixel canvas

    private static void canvas(int width, int height) {
        cell = Math.max(2, Math.round((float)height / 110.0F));
        gw = width / cell + 2;
        gh = height / cell + 2;
        int need = gw * gh;
        if (grid.length < need) {
            grid = new int[need];
            inked = new int[need];
        } else {
            Arrays.fill(grid, 0, need, 0);
        }
    }

    private static void put(int x, int y, int color) {
        if (x >= 0 && y >= 0 && x < gw && y < gh) {
            grid[y * gw + x] = color;
        }
    }

    private static int mix(int a, int b, float t) {
        int r = (int)MathHelper.lerp(t, (float)(a >> 16 & 0xFF), (float)(b >> 16 & 0xFF));
        int g = (int)MathHelper.lerp(t, (float)(a >> 8 & 0xFF), (float)(b >> 8 & 0xFF));
        int bl = (int)MathHelper.lerp(t, (float)(a & 0xFF), (float)(b & 0xFF));
        return r << 16 | g << 8 | bl;
    }

    /** Cel shading: a band of shadow low on the right, a streak of light high on the left. */
    private static int shaded(int rgb, float ux, float uy, float radius) {
        if (radius < 1.6F) {
            return SOLID | rgb;
        }

        float across = ux * 0.55F + uy * 0.83F;
        if (across > 0.36F) {
            return SOLID | mix(rgb, INK, 0.34F);
        }

        float along = ux * 0.83F - uy * 0.55F;
        return across < -0.42F && across > -0.84F && Math.abs(along) < 0.6F ? SOLID | mix(rgb, 0xFFFFFF, 0.78F) : SOLID | rgb;
    }

    /** A shaded lump of the mass. Coordinates are screen pixels. */
    private static void blob(float x, float y, float radius, int rgb) {
        limb(x, y, radius, x, y, radius, rgb);
    }

    /** A shaded, tapering limb between two lumps. */
    private static void limb(float x1, float y1, float r1, float x2, float y2, float r2, int rgb) {
        raster(x1, y1, r1, x2, y2, r2, rgb, true);
    }

    /** A flat-coloured stroke; pass {@link #BARE} in the colour's alpha to leave it without ink. */
    private static void stroke(float x1, float y1, float x2, float y2, float radius, int argb) {
        raster(x1, y1, radius, x2, y2, radius, argb, false);
    }

    private static void raster(float x1, float y1, float r1, float x2, float y2, float r2, int color, boolean shade) {
        float c = (float)cell;
        float ax = x1 / c;
        float ay = y1 / c;
        float bx = x2 / c;
        float by = y2 / c;
        float ra = Math.max(0.55F, r1 / c);
        float rb = Math.max(0.55F, r2 / c);
        float reach = Math.max(ra, rb);
        int minX = Math.max(0, MathHelper.floor(Math.min(ax, bx) - reach));
        int maxX = Math.min(gw - 1, MathHelper.ceil(Math.max(ax, bx) + reach));
        int minY = Math.max(0, MathHelper.floor(Math.min(ay, by) - reach));
        int maxY = Math.min(gh - 1, MathHelper.ceil(Math.max(ay, by) + reach));
        float dx = bx - ax;
        float dy = by - ay;
        float len2 = dx * dx + dy * dy;

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                float px = (float)x + 0.5F - ax;
                float py = (float)y + 0.5F - ay;
                float t = len2 < 1.0E-4F ? 0.0F : MathHelper.clamp((px * dx + py * dy) / len2, 0.0F, 1.0F);
                float ox = px - dx * t;
                float oy = py - dy * t;
                float r = ra + (rb - ra) * t;
                if (ox * ox + oy * oy <= r * r) {
                    grid[y * gw + x] = shade ? shaded(color & 0xFFFFFF, ox / r, oy / r, r) : color;
                }
            }
        }
    }

    private static void box(float x0, float y0, float x1, float y1, int argb) {
        int minX = Math.max(0, MathHelper.floor(x0 / (float)cell));
        int maxX = Math.min(gw - 1, MathHelper.floor(x1 / (float)cell));
        int minY = Math.max(0, MathHelper.floor(y0 / (float)cell));
        int maxY = Math.min(gh - 1, MathHelper.floor(y1 / (float)cell));

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                grid[y * gw + x] = argb;
            }
        }
    }

    /** A spiked star: the shape of a comic-book hit. */
    private static void spike(float x, float y, float outer, float inner, int points, float turn, int argb) {
        float c = (float)cell;
        float cx = x / c;
        float cy = y / c;
        float ro = outer / c;
        float ri = inner / c;
        int minX = Math.max(0, MathHelper.floor(cx - ro));
        int maxX = Math.min(gw - 1, MathHelper.ceil(cx + ro));
        int minY = Math.max(0, MathHelper.floor(cy - ro));
        int maxY = Math.min(gh - 1, MathHelper.ceil(cy + ro));
        double wedge = Math.PI * 2.0 / (double)points;

        for (int py = minY; py <= maxY; py++) {
            for (int px = minX; px <= maxX; px++) {
                float dx = (float)px + 0.5F - cx;
                float dy = (float)py + 0.5F - cy;
                double a = (Math.atan2((double)dy, (double)dx) - (double)turn) / wedge;
                float tip = 1.0F - Math.abs((float)(a - Math.floor(a)) * 2.0F - 1.0F);
                float r = ri + (ro - ri) * tip * tip;
                if (dx * dx + dy * dy <= r * r) {
                    grid[py * gw + px] = argb;
                }
            }
        }
    }

    /** A ring squashed to lie on the ground; on this grid it comes out in steps. */
    private static void hoop(float x, float y, float radius, float squash, float thickness, int argb) {
        float c = (float)cell;
        float cx = x / c;
        float cy = y / c;
        float r = radius / c;
        float th = Math.max(1.0F, thickness / c);
        int minX = Math.max(0, MathHelper.floor(cx - r));
        int maxX = Math.min(gw - 1, MathHelper.ceil(cx + r));
        int minY = Math.max(0, MathHelper.floor(cy - r * squash));
        int maxY = Math.min(gh - 1, MathHelper.ceil(cy + r * squash));

        for (int py = minY; py <= maxY; py++) {
            for (int px = minX; px <= maxX; px++) {
                float dx = (float)px + 0.5F - cx;
                float dy = ((float)py + 0.5F - cy) / squash;
                float d = (float)Math.sqrt((double)(dx * dx + dy * dy));
                if (d <= r && d >= r - th) {
                    grid[py * gw + px] = argb;
                }
            }
        }
    }

    private static void quad(float x0, float y0, float x1, float y1, int argb) {
        int a = argb >>> 24;
        int r = argb >> 16 & 0xFF;
        int g = argb >> 8 & 0xFF;
        int b = argb & 0xFF;
        batch.vertex(batchMatrix, x0, y0, 0.0F).color(r, g, b, a).next();
        batch.vertex(batchMatrix, x0, y1, 0.0F).color(r, g, b, a).next();
        batch.vertex(batchMatrix, x1, y1, 0.0F).color(r, g, b, a).next();
        batch.vertex(batchMatrix, x0, y0, 0.0F).color(r, g, b, a).next();
        batch.vertex(batchMatrix, x1, y1, 0.0F).color(r, g, b, a).next();
        batch.vertex(batchMatrix, x1, y0, 0.0F).color(r, g, b, a).next();
    }

    private static void drawRuns(int[] list, int count, int size, int shiftCells, int tint, int alpha) {
        float c = (float)size;

        for (int i = 0; i < count; i += 4) {
            int color = tint != 0 ? tint & 0xFFFFFF | alpha << 24 : list[i + 3] & 0xFFFFFF | alpha << 24;
            quad((float)(list[i + 1] + shiftCells) * c, (float)list[i] * c, (float)(list[i + 2] + shiftCells) * c, (float)(list[i] + 1) * c, color);
        }
    }

    /** Inks the canvas, breaks it up a little, and puts it on the screen. */
    private static void flush(DrawContext context, int beat, int tickAge) {
        int need = gw * gh;
        // Ink: every empty cell that touches paint becomes outline.
        System.arraycopy(grid, 0, inked, 0, need);

        for (int y = 0; y < gh; y++) {
            for (int x = 0; x < gw; x++) {
                int i = y * gw + x;
                if (grid[i] != 0) {
                    continue;
                }

                if (x > 0 && grid[i - 1] >>> 24 == 0xFF
                    || x < gw - 1 && grid[i + 1] >>> 24 == 0xFF
                    || y > 0 && grid[i - gw] >>> 24 == 0xFF
                    || y < gh - 1 && grid[i + gw] >>> 24 == 0xFF) {
                    inked[i] = SOLID | INK;
                }
            }
        }

        float g = MathHelper.clamp(0.22F + glitch, 0.0F, 1.0F);
        runCount = 0;

        for (int y = 0; y < gh; y++) {
            // Dropped lines and rows knocked sideways: the signal is never clean.
            if (hash(977L, y, tickAge) + 0.5 < 0.015 + 0.07 * (double)g) {
                continue;
            }

            int band = y / 5;
            int shift = hash(31L, band, beat) + 0.5 < 0.05 + 0.3 * (double)g ? (int)(hash(57L, band, beat) * (double)(4.0F + 22.0F * g)) : 0;
            int start = -1;
            int color = 0;

            for (int x = 0; x <= gw; x++) {
                int now = x < gw ? inked[y * gw + x] : 0;
                if (now != color) {
                    if (color != 0) {
                        if (runCount + 4 > runs.length) {
                            runs = Arrays.copyOf(runs, runs.length * 2);
                        }

                        runs[runCount++] = y;
                        runs[runCount++] = start + shift;
                        runs[runCount++] = x + shift;
                        runs[runCount++] = color;
                    }

                    start = x;
                    color = now;
                }
            }
        }

        if (beat != lastBeat) {
            // The pose of the previous beat lingers as an afterimage.
            if (ghostRuns.length < lastRunCount) {
                ghostRuns = new int[lastRuns.length];
            }

            System.arraycopy(lastRuns, 0, ghostRuns, 0, lastRunCount);
            ghostRunCount = lastRunCount;
            ghostCell = cell;
            lastBeat = beat;
        }

        if (runCount == 0 && ghostRunCount == 0) {
            lastRunCount = 0;
            return;
        }

        context.draw();
        batchMatrix = context.getMatrices().peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        batch = Tessellator.getInstance().getBuffer();
        batch.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        drawRuns(ghostRuns, ghostRunCount, ghostCell, 0, 0, 70);
        // Colour ghosts either side, wider when the picture is being hit.
        int split = 1 + (int)(g * 3.0F);
        drawRuns(runs, runCount, cell, -split, MAGENTA, 120);
        drawRuns(runs, runCount, cell, split, CYAN, 120);
        drawRuns(runs, runCount, cell, 0, 0, 255);
        // A few dead blocks of colour stuck to the picture.
        int junk = (int)(g * 7.0F);

        for (int i = 0; i < junk && runCount > 0; i++) {
            int at = (int)((hash(91L, i, tickAge) + 0.5) * (double)(runCount / 4)) * 4;
            float x = (float)(runs[at + 1] * cell) + (float)hash(13L, i, tickAge) * 60.0F;
            float y = (float)(runs[at] * cell);
            float w = (float)(cell * (2 + (int)((hash(17L, i, tickAge) + 0.5) * 9.0)));
            int tint = i % 3 == 0 ? 0xFFFFFF : (i % 3 == 1 ? MAGENTA : CYAN);
            quad(x, y, x + w, y + (float)(cell * (1 + i % 2)), tint | 200 << 24);
        }

        Tessellator.getInstance().draw();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        batch = null;
        if (lastRuns.length < runCount) {
            lastRuns = new int[runs.length];
        }

        System.arraycopy(runs, 0, lastRuns, 0, runCount);
        lastRunCount = runCount;
    }

    // ------------------------------------------------------------------ screen

    public static void renderHud(DrawContext context, float tickDelta, int width, int height) {
        MinecraftClient client = MinecraftClient.getInstance();
        screenW = width;
        screenH = height;
        if (client.world == null || ACTIVE.isEmpty() && FLECKS.isEmpty() && WORDS.isEmpty() && impactTicks <= 0 && ghostRunCount == 0 && lastRunCount == 0) {
            return;
        }

        int now = (int)(client.world.getTime() % 100000L);
        // Poses are held: nothing here moves more often than once a tick, and most of it on twos.
        int beat = now / 2;

        for (Fx fx : ACTIVE) {
            if (fx.kind == HostNetworking.FX_BOLT && fx.slot == 0) {
                face(context, fx, width, height, tickDelta);
            }
        }

        if (impactTicks == 3) {
            // An impact frame: one tick of nothing but white.
            context.fill(0, 0, width, height, 0xE6FFFFFF);
        } else if (impactTicks > 0) {
            context.fill(0, 0, width, height, 0x8C0B1030);
        }

        if (!captured) {
            return;
        }

        canvas(width, height);
        ICONS.clear();
        float sc = scale();
        if (impactTicks > 0 && impactTicks < 3) {
            // ...then two of hard lines flying out of the hit.
            for (int i = 0; i < 22; i++) {
                double a = (double)i / 22.0 * Math.PI * 2.0 + hash(5L, i, now) * 0.2;
                float near = (40.0F + (float)(hash(7L, i, now) + 0.5) * 60.0F) * sc;
                float far = (float)Math.max(width, height);
                stroke(
                    impactX + (float)Math.cos(a) * near, impactY + (float)Math.sin(a) * near, impactX + (float)Math.cos(a) * far, impactY + (float)Math.sin(a) * far, (1.5F + (float)(i % 3)) * sc, BARE | (i % 4 == 0 ? impactColor : 0xFFFFFF)
                );
            }
        }

        for (Fx fx : ACTIVE) {
            switch (fx.kind) {
                case HostNetworking.FX_SELF -> frame(fx, beat, width, height);
                case HostNetworking.FX_PULSE -> pulse(fx, width, height);
                case HostNetworking.FX_NODE -> node(fx, beat);
                case HostNetworking.FX_ARC -> arc(fx, now);
                case HostNetworking.FX_TENDRIL -> arm(client, fx, now, beat, width, height);
                case HostNetworking.FX_GRAB -> grab(fx, now, beat, width, height);
                case HostNetworking.FX_SWAP -> swap(client, fx, now, beat, width, height);
                case HostNetworking.FX_STRING -> strings(client, fx, now, beat);
                case HostNetworking.FX_RUSH -> rush(fx, now, width, height);
                case HostNetworking.FX_TAG -> tag(client, fx, now, beat);
                case HostNetworking.FX_STAMP -> stamp(fx, now, beat, width, height);
                case HostNetworking.FX_BOLT -> shot(client, fx, now, beat, width, height);
                default -> {
                }
            }
        }

        for (Fleck fleck : FLECKS) {
            float s = (float)(fleck.size * cell);
            box(fleck.x, fleck.y, fleck.x + s - 1.0F, fleck.y + s - 1.0F, (fleck.size > 1 ? SOLID : BARE) | fleck.color);
        }

        flush(context, beat, now);

        for (float[] icon : ICONS) {
            // The real thing rides the arm: the item's own picture, big, crooked and held on twos.
            net.minecraft.item.Item item = net.minecraft.registry.Registries.ITEM.get((int)icon[0]);
            MatrixStack matrices = context.getMatrices();
            matrices.push();
            matrices.translate(icon[1], icon[2], 150.0F);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(icon[4]));
            matrices.scale(icon[3], icon[3], 1.0F);
            context.drawItem(new net.minecraft.item.ItemStack(item), -8, -8);
            matrices.pop();
        }

        TextRenderer font = client.textRenderer;

        for (Word word : WORDS) {
            // Lettering slams in oversized and judders in place.
            float size = (word.age == 0 ? 3.4F : (word.age == 1 ? 2.2F : 2.6F)) * sc;
            float jx = (float)hash(word.life, word.age, 1) * 5.0F * sc;
            float jy = (float)hash(word.life, word.age, 2) * 5.0F * sc;
            if (word.age > word.life - 2 && word.age % 2 == 0) {
                continue;
            }

            MatrixStack matrices = context.getMatrices();
            matrices.push();
            matrices.translate(word.x + jx, word.y + jy, 0.0F);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(word.turn));
            matrices.scale(size, size, 1.0F);
            int half = font.getWidth(word.text) / 2;

            for (int ox = -1; ox <= 1; ox++) {
                for (int oy = -1; oy <= 1; oy++) {
                    if (ox != 0 || oy != 0) {
                        context.drawText(font, word.text, -half + ox, -4 + oy, SOLID | INK, false);
                    }
                }
            }

            context.drawText(font, word.text, -half + 1, -3, SOLID | MAGENTA, false);
            context.drawText(font, word.text, -half, -4, SOLID | word.color, false);
            matrices.pop();
        }
    }

    /**
     * Lays the mass out from a point off the edge of the screen to a point on it and paints it: a sagging,
     * lumpy limb whose pose is held on the beat. The joints are left in JX/JY/JR and the curve is remembered.
     */
    private static void chain(Fx fx, float ox, float oy, float tx, float ty, float reach, float thick, float flow, int now, int beat, int color, int salt, float side) {
        float sc = scale();
        float dx = tx - ox;
        float dy = ty - oy;
        float len = Math.max(1.0F, (float)Math.sqrt((double)(dx * dx + dy * dy)));
        float nx = -dy / len;
        float ny = dx / len;
        // It hangs like something with weight, and the hang changes pose on the beat.
        float bend = len * (0.14F + 0.07F * (float)hash(fx.seed + (long)salt, beat / 2, 1)) * side;
        curveOx = ox;
        curveOy = oy;
        curveCx = ox + dx * 0.5F + nx * bend;
        curveCy = oy + dy * 0.5F + ny * bend + 18.0F * sc;
        curveTx = tx;
        curveTy = ty;
        curveNx = nx;
        curveNy = ny;
        // Sometimes a stretch of the arm simply is not there for a beat.
        int torn = hash(fx.seed + (long)salt, beat, 9) > 0.36 ? 4 + (int)((hash(fx.seed + (long)salt, beat, 10) + 0.5) * 12.0) : -9;

        for (int i = 0; i < JOINTS; i++) {
            float s = (float)i / (float)(JOINTS - 1) * reach;
            float u = 1.0F - s;
            float sway = (float)hash(fx.seed + (long)salt, i, beat) * 9.0F * sc * (float)Math.sin(Math.PI * (double)s);
            JX[i] = u * u * ox + 2.0F * u * s * curveCx + s * s * tx + nx * sway;
            JY[i] = u * u * oy + 2.0F * u * s * curveCy + s * s * ty + ny * sway;
            float base = MathHelper.lerp((float)Math.pow((double)s, 0.7), 26.0F, 9.0F) * sc * thick;
            // Lumps travel along it: mass pumped out to the work, or drunk back.
            int phase = Math.floorMod(i + (int)flow * now, 5);
            JR[i] = base + (phase == 0 ? 8.0F : (phase == 1 ? 4.0F : 0.0F)) * sc * thick;
        }

        for (int i = 0; i + 1 < JOINTS; i++) {
            if (i == torn || i == torn + 1) {
                continue;
            }

            limb(JX[i], JY[i], JR[i], JX[i + 1], JY[i + 1], JR[i + 1], color);
        }

        // A drip off its belly.
        int mid = JOINTS / 2;
        blob(JX[mid], JY[mid] + JR[mid] * (1.0F + 0.5F * (float)(beat % 3)), JR[mid] * 0.4F, color);
    }

    private static float curveX(float s, float off) {
        float u = 1.0F - s;
        return u * u * curveOx + 2.0F * u * s * curveCx + s * s * curveTx + curveNx * off;
    }

    private static float curveY(float s, float off) {
        float u = 1.0F - s;
        return u * u * curveOy + 2.0F * u * s * curveCy + s * s * curveTy + curveNy * off;
    }

    /** The fist on the end of the chain just drawn: it balloons when it hits, and its fingers never sit still. */
    private static void fist(Fx fx, float size, int hit, int color, int now, int beat) {
        int last = JOINTS - 1;
        float swell = hit == 0 ? 2.1F : (hit == 1 ? 1.5F : 1.0F);
        float fist = size * swell * (beat % 2 == 0 ? 1.0F : 1.12F);
        float heading = (float)Math.atan2((double)(JY[last] - JY[last - 1]), (double)(JX[last] - JX[last - 1]));
        if (hit == 0 || hit == 1) {
            spike(JX[last], JY[last], fist * (hit == 0 ? 2.6F : 3.4F), fist * 1.1F, 9, (float)now * 0.9F, SOLID | 0xFFFFFF);
        }

        blob(JX[last], JY[last], fist, color);

        for (int f = 0; f < 3; f++) {
            float spread = ((float)f - 1.0F) * 0.8F + (float)hash(fx.seed, f, now) * 0.7F;
            float out = fist * (1.0F + 0.4F * (float)Math.abs(hash(fx.seed + 5L, f, now)));
            blob(JX[last] + (float)Math.cos((double)(heading + spread)) * out, JY[last] + (float)Math.sin((double)(heading + spread)) * out, fist * 0.42F, color);
        }
    }

    /** Whatever is travelling along the chain just drawn. */
    private static void pellets(int slot, float reach) {
        float sc = scale();

        for (Pellet pellet : PELLETS) {
            if (pellet.slot != slot) {
                continue;
            }

            float s = MathHelper.clamp(pellet.along, 0.0F, 1.0F) * reach;
            float px = curveX(s, pellet.side * 30.0F * sc);
            float py = curveY(s, pellet.side * 30.0F * sc);
            float size = (float)(cell * 2);
            box(px - size * 0.5F, py - size * 0.5F, px + size * 0.5F, py + size * 0.5F, SOLID | pellet.color);
        }
    }

    /** The arm of mass that comes out of the edge of the screen and grips its work. */
    private static void arm(MinecraftClient client, Fx fx, int now, int beat, int width, int height) {
        float sc = scale();
        int slot = fx.slot;
        boolean drain = slot == HostNetworking.SLOT_DRAIN;
        boolean point = slot == HostNetworking.SLOT_POINT;
        boolean trace = slot >= HostNetworking.SLOT_TRACE;
        boolean cable = slot == HostNetworking.SLOT_CABLE;
        boolean heavy = slot == HostNetworking.SLOT_SHOVE || slot == HostNetworking.SLOT_YANK;
        float ox = (float)width + 14.0F * sc;
        float oy = (float)height * (drain ? 0.72F : 0.5F + 0.11F * (float)(slot % 3));
        float side = 1.0F;
        if (heavy) {
            // The big one comes up from under the picture, like a punch.
            ox = (float)width * 0.5F + (float)hash(fx.seed, 1, 1) * (float)width * 0.2F;
            oy = (float)height + 16.0F * sc;
            side = -0.5F;
        } else if (slot == HostNetworking.SLOT_SLAP) {
            // From the other side, so it does not look like every other arm.
            ox = -14.0F * sc;
            oy = (float)height * 0.42F;
            side = -1.0F;
        } else if (trace) {
            // A fan of threads from under the middle of the picture.
            ox = (float)width * 0.5F + ((float)(slot - HostNetworking.SLOT_TRACE) - 3.5F) * 26.0F * sc;
            oy = (float)height + 8.0F * sc;
            side = slot % 2 == 0 ? 0.4F : -0.4F;
        } else if (cable) {
            ox = (float)width * 0.86F;
            oy = (float)height + 10.0F * sc;
            side = -0.7F;
        }

        float[] tip = tipOf(fx, client);
        if (tip[2] == 0.0F && (trace || cable || point)) {
            return;
        }

        float reach = fx.age >= 100 ? 1.0F : Math.min(1.0F, (float)(fx.age + 1) / 2.0F);
        if (fx.life <= 2) {
            reach = Math.min(reach, (float)fx.life / 3.0F);
        }

        if (reach <= 0.02F || (point || trace) && fx.life < 8 && fx.life % 2 == 0) {
            return;
        }

        float thick = point ? 0.4F
            : trace ? 0.26F
            : cable ? 0.3F
            : heavy ? 1.4F
            : slot == HostNetworking.SLOT_POKE ? 0.7F
            : slot == HostNetworking.SLOT_INJECT ? 0.5F
            : slot == HostNetworking.SLOT_SLAP ? 0.9F
            : 1.0F;
        chain(fx, ox, oy, tip[0], tip[1], reach, thick, drain || slot == HostNetworking.SLOT_YANK ? 1.0F : -1.0F, now, beat, fx.color, 0, side);
        int last = JOINTS - 1;
        if (point) {
            // No fist: a finger, and rings going out from what it points at.
            float ring = (float)Math.floorMod(now, 8) / 8.0F;
            hoop(JX[last], JY[last], (10.0F + 26.0F * ring) * sc, 1.0F, 3.0F * sc, SOLID | fx.color);
            hoop(JX[last], JY[last], (10.0F + 26.0F * ((ring + 0.5F) % 1.0F)) * sc, 1.0F, 2.0F * sc, SOLID | 0xFFFFFF);
            spike(JX[last], JY[last], 13.0F * sc, 4.0F * sc, 4, (float)(beat % 2) * 0.785F, SOLID | 0xFFFFFF);
            blob(JX[last], JY[last], 5.0F * sc, fx.color);
            return;
        }

        if (trace) {
            // A thread and a bracket snapped shut on what it found.
            float open = (reach < 1.0F ? 16.0F : 9.0F) * sc;
            float t = 1.5F * sc;
            int tone = SOLID | (beat % 2 == 0 ? 0xFFFFFF : fx.color);

            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sy = -1; sy <= 1; sy += 2) {
                    float px = JX[last] + (float)sx * open;
                    float py = JY[last] + (float)sy * open;
                    box(Math.min(px, px - (float)sx * 5.0F * sc), py - t, Math.max(px, px - (float)sx * 5.0F * sc), py + t, tone);
                    box(px - t, Math.min(py, py - (float)sy * 5.0F * sc), px + t, Math.max(py, py - (float)sy * 5.0F * sc), tone);
                }
            }

            return;
        }

        if (cable) {
            // The plug, with a spark on the tick a pulse goes through.
            if (fx.punch >= 0 && fx.punch <= 1) {
                spike(JX[last], JY[last], 20.0F * sc, 6.0F * sc, 6, (float)now, SOLID | 0xFFFFFF);
            }

            blob(JX[last], JY[last], 5.0F * sc, fx.color);
            pellets(slot, reach);
            return;
        }

        int hit = fx.punch >= 0 && fx.punch <= 1 && reach >= 1.0F ? fx.punch : 2;
        if (slot == HostNetworking.SLOT_INJECT) {
            // A needle instead of a fist; the barrel behind it empties after the plunger goes in.
            float heading = (float)Math.atan2((double)(JY[last] - JY[last - 2]), (double)(JX[last] - JX[last - 2]));
            float nx = (float)Math.cos((double)heading);
            float ny = (float)Math.sin((double)heading);
            boolean pushed = fx.age >= 6;
            float barrel = (pushed ? 8.0F : 13.0F) * sc;
            if (hit <= 1) {
                spike(JX[last], JY[last], 26.0F * sc, 8.0F * sc, 8, (float)now * 0.9F, SOLID | 0xFFFFFF);
            }

            limb(JX[last] - nx * 34.0F * sc, JY[last] - ny * 34.0F * sc, barrel, JX[last] - nx * 12.0F * sc, JY[last] - ny * 12.0F * sc, barrel, pushed ? 0xFFFFFF : fx.color);
            stroke(JX[last] - nx * 12.0F * sc, JY[last] - ny * 12.0F * sc, JX[last] + nx * 10.0F * sc, JY[last] + ny * 10.0F * sc, 1.6F * sc, SOLID | 0xFFFFFF);
            return;
        }

        fist(fx, 15.0F * sc * thick * Math.min(1.0F, reach * 1.2F), hit, fx.color, now, beat);
        pellets(slot, reach);
    }

    /** A mark pinned on a creature for as long as something of hers is at work in it. */
    private static void tag(MinecraftClient client, Fx fx, int now, int beat) {
        Entity entity = fx.entityId >= 0 && client.world != null ? client.world.getEntityById(fx.entityId) : null;
        if (entity == null || fx.life < 8 && fx.life % 2 == 0 || fx.age < 1) {
            return;
        }

        boolean blindfold = fx.slot == HostNetworking.TAG_BLINDFOLD;
        Vec3d where = entity.getPos().add(0.0, (double)entity.getHeight() * (blindfold ? 0.86 : 1.0) + (blindfold ? 0.0 : 0.5), 0.0);
        float[] at = project(where);
        if (at[2] == 0.0F) {
            return;
        }

        float sc = scale();
        double distance = Math.max(1.5, Math.sqrt(where.squaredDistanceTo(cameraPos)));
        float focal = (float)screenH / 1.4F;
        float u = MathHelper.clamp((float)((double)entity.getWidth() * 0.62 * (double)focal / distance), 5.0F * sc, 70.0F * sc);
        float x = at[0];
        float y = at[1];
        if (blindfold) {
            // A band across the eyes with two crosses on it, and the ends of the knot flapping.
            float slip = beat % 5 == 0 ? (float)cell : 0.0F;
            float half = Math.max(u * 0.32F, 2.0F * sc);
            box(x - u + slip, y - half, x + u + slip, y + half, SOLID | INK);
            float d = Math.max(half * 0.6F, 1.5F * sc);

            for (int eye = -1; eye <= 1; eye += 2) {
                float ex = x + (float)eye * u * 0.45F + slip;
                stroke(ex - d, y - d, ex + d, y + d, Math.max(1.0F, d * 0.35F), BARE | 0xFFFFFF);
                stroke(ex - d, y + d, ex + d, y - d, Math.max(1.0F, d * 0.35F), BARE | 0xFFFFFF);
            }

            float flap = (float)hash(fx.seed, beat, 1) * u * 0.5F;
            stroke(x + u, y, x + u * 1.5F, y + u * 0.5F + flap, Math.max(1.2F, half * 0.5F), SOLID | INK);
            stroke(x + u, y, x + u * 1.35F, y + u * 0.9F - flap, Math.max(1.2F, half * 0.5F), SOLID | INK);
            return;
        }

        // The germ: a spiked knot of the mass riding above its carrier, dripping into it.
        float r = Math.max(u * 0.5F, 4.0F * sc) * (beat % 2 == 0 ? 1.0F : 1.18F);
        float fall = (float)Math.floorMod(now, 8) / 8.0F;
        blob(x + (float)hash(fx.seed, now / 8, 2) * r, y + r + fall * u * 1.2F, Math.max(1.5F, r * 0.28F), fx.color);
        spike(x, y, r * 2.0F, r * 0.9F, 7, (float)beat * 0.45F, SOLID | fx.color);
        blob(x, y, r, fx.color);
        float eye = Math.max((float)cell * 0.5F, r * 0.2F);
        box(x - r * 0.45F - eye, y - eye, x - r * 0.45F + eye, y + eye, BARE | INK);
        box(x + r * 0.45F - eye, y - eye, x + r * 0.45F + eye, y + eye, BARE | INK);
    }

    /** The pieces drawn on the Host's own screen. */
    private static void stamp(Fx fx, int now, int beat, int width, int height) {
        float sc = scale();
        float cx = (float)width * 0.5F;
        boolean fading = fx.life < 6 && fx.life % 2 == 0;
        switch (fx.slot) {
            case HostNetworking.STAMP_PALM -> {
                // An open hand thrown up from under the picture: the thing that stops a blow, or deals one.
                float rise = Math.min(1.0F, (float)(fx.age + 1) / 2.0F) * Math.min(1.0F, (float)fx.life / 2.0F);
                float py = (float)height * (1.25F - 0.6F * rise) + (beat % 2 == 0 ? 0.0F : 4.0F * sc);
                float palm = (float)height * 0.2F * (fx.age == 2 ? 1.25F : 1.0F);
                if (fx.age >= 2 && fx.age <= 3) {
                    spike(cx, py - palm * 0.3F, palm * 3.2F, palm * 1.2F, 11, (float)now * 0.7F, SOLID | 0xFFFFFF);
                }

                for (int f = 0; f < 4; f++) {
                    float lean = ((float)f - 1.5F) * 0.26F + (float)hash(fx.seed, f, beat) * 0.05F;
                    float length = palm * (f == 1 || f == 2 ? 1.75F : 1.45F);
                    float bx = cx + ((float)f - 1.5F) * palm * 0.48F;
                    limb(bx, py - palm * 0.5F, palm * 0.27F, bx + (float)Math.sin((double)lean) * length, py - palm * 0.5F - (float)Math.cos((double)lean) * length, palm * 0.22F, fx.color);
                }

                limb(cx - palm * 0.8F, py + palm * 0.1F, palm * 0.3F, cx - palm * 1.5F, py - palm * 0.6F, palm * 0.23F, fx.color);
                blob(cx, py, palm, fx.color);
                // The wrist runs off the bottom of the screen.
                limb(cx, py + palm * 0.6F, palm * 0.7F, cx + (float)hash(fx.seed, 3, 3) * palm, (float)height + palm, palm * 0.6F, fx.color);
            }
            case HostNetworking.STAMP_CLOCK -> {
                if (fading) {
                    return;
                }

                // A stopwatch nailed to the top of the picture, its hand dragging and now and then slipping back.
                float x = cx;
                float y = 44.0F * sc;
                float r = 30.0F * sc * Math.min(1.0F, (float)(fx.age + 1) / 3.0F);
                blob(x - r * 0.75F, y - r * 0.95F, r * 0.24F, fx.color);
                blob(x + r * 0.75F, y - r * 0.95F, r * 0.24F, fx.color);
                hoop(x, y, r, 1.0F, 5.0F * sc, SOLID | fx.color);

                for (int m = 0; m < 4; m++) {
                    double a = (double)m * Math.PI * 0.5;
                    box(x + (float)Math.cos(a) * r * 0.62F - (float)cell * 0.5F, y + (float)Math.sin(a) * r * 0.62F - (float)cell * 0.5F, x + (float)Math.cos(a) * r * 0.62F + (float)cell * 0.5F, y + (float)Math.sin(a) * r * 0.62F + (float)cell * 0.5F, BARE | 0xFFFFFF);
                }

                int step = fx.age / 5 - (hash(fx.seed, fx.age / 5, 4) > 0.3 ? 1 : 0);
                double hand = (double)step * Math.PI / 6.0 - Math.PI * 0.5;
                stroke(x, y, x + (float)Math.cos(hand) * r * 0.7F, y + (float)Math.sin(hand) * r * 0.7F, 2.6F * sc, SOLID | 0xFFFFFF);
                stroke(x, y, x + r * 0.34F, y + r * 0.18F, 2.6F * sc, SOLID | MAGENTA);
                blob(x, y, 3.5F * sc, fx.color);
            }
            case HostNetworking.STAMP_MEND -> {
                // Two thin arms come in from the sides and feed the hearts; crosses float up off them.
                float reach = Math.min(Math.min(1.0F, (float)(fx.age + 1) / 3.0F), Math.min(1.0F, (float)fx.life / 4.0F));
                if (reach <= 0.02F) {
                    return;
                }

                float hy = (float)height - 34.0F;
                float left = cx - 91.0F;
                float right = cx - 10.0F;
                chain(fx, -14.0F * sc, (float)height * 0.7F, left, hy, reach, 0.45F, -1.0F, now, beat, fx.color, 1, -0.6F);
                blob(JX[JOINTS - 1], JY[JOINTS - 1], 6.0F * sc, fx.color);
                pellets(SLOT_HAUL + 1, reach);
                chain(fx, (float)width + 14.0F * sc, (float)height * 0.7F, right, hy, reach, 0.45F, -1.0F, now, beat, fx.color, 2, 0.6F);
                blob(JX[JOINTS - 1], JY[JOINTS - 1], 6.0F * sc, fx.color);
                pellets(SLOT_HAUL + 2, reach);

                for (int k = 0; k < 4; k++) {
                    float up = (float)Math.floorMod(now * 2 + k * 9, 28);
                    float px = left + (float)(hash(fx.seed, k, now / 14) + 0.5) * (right - left);
                    float py = hy - 10.0F - up * 1.6F * sc;
                    float arm = 4.0F * sc;
                    box(px - arm, py - (float)cell * 0.5F, px + arm, py + (float)cell * 0.5F, SOLID | (k % 2 == 0 ? 0xFFFFFF : fx.color));
                    box(px - (float)cell * 0.5F, py - arm, px + (float)cell * 0.5F, py + arm, SOLID | (k % 2 == 0 ? 0xFFFFFF : fx.color));
                }
            }
            case HostNetworking.STAMP_EYES -> {
                if (fading) {
                    return;
                }

                // Her eyes, lent: they open across the picture, blink once, and go.
                float open = fx.age < 4 ? (float)fx.age / 4.0F : (fx.age == 9 || fx.age == 10 ? 0.15F : 1.0F);
                float y = (float)height * 0.42F;
                float w = 46.0F * sc;
                float h = 26.0F * sc * open;
                if (h < 2.0F) {
                    h = 2.0F;
                }

                for (int eye = -1; eye <= 1; eye += 2) {
                    float x = cx + (float)eye * 78.0F * sc;
                    limb(x - w, y, h, x + w, y, h, 0xFFFFFF);
                    if (open > 0.3F) {
                        float look = (float)hash(fx.seed, beat / 2, 6) * 14.0F * sc;
                        blob(x + look, y, Math.min(h * 0.82F, 19.0F * sc), MAGENTA);
                        blob(x + look, y, Math.min(h * 0.4F, 8.0F * sc), INK);
                        box(x + look - 9.0F * sc, y - 10.0F * sc, x + look - 4.0F * sc, y - 5.0F * sc, BARE | 0xFFFFFF);
                    }

                    // Lashes.
                    stroke(x - w, y - h, x - w - 10.0F * sc, y - h - 10.0F * sc, 2.0F * sc, SOLID | INK);
                    stroke(x + w, y - h, x + w + 10.0F * sc, y - h - 10.0F * sc, 2.0F * sc, SOLID | INK);
                }
            }
            case HostNetworking.STAMP_CRACK -> {
                if (fx.life < 5 && fx.life % 2 == 0) {
                    return;
                }

                // The chip goes: cracks run out across the glass from the temple.
                float x0 = (float)width * 0.82F;
                float y0 = (float)height * 0.2F;
                float grown = Math.min(1.0F, (float)(fx.age + 1) / 3.0F);

                for (int k = 0; k < 7; k++) {
                    double a = Math.PI * (0.35 + 0.16 * (double)k) + hash(fx.seed, k, 1) * 0.2;
                    float length = (float)Math.max(width, height) * (0.25F + 0.45F * (float)(hash(fx.seed, k, 2) + 0.5)) * grown;
                    float px = x0;
                    float py = y0;

                    for (int seg = 1; seg <= 5; seg++) {
                        float t = (float)seg / 5.0F;
                        float jag = (float)hash(fx.seed, k * 7 + seg, 3) * 26.0F * sc;
                        float x = x0 + (float)Math.cos(a) * length * t - (float)Math.sin(a) * jag;
                        float y = y0 + (float)Math.sin(a) * length * t + (float)Math.cos(a) * jag;
                        stroke(px, py, x, y, (2.6F - 0.4F * (float)seg) * sc, SOLID | (k % 3 == 0 ? CYAN : 0xFFFFFF));
                        px = x;
                        py = y;
                    }
                }

                spike(x0, y0, 34.0F * sc, 10.0F * sc, 9, (float)now * 0.5F, SOLID | 0xFFFFFF);
            }
            case HostNetworking.STAMP_HANDS -> {
                // Two hands of the mass close over the Host's eyes from either side.
                float close = fx.age < 3 ? (float)(fx.age + 1) / 3.0F : Math.min(1.0F, (float)fx.life / 3.0F);
                float r = (float)height * 0.62F;
                float y = (float)height * 0.5F + (beat % 2 == 0 ? 0.0F : 5.0F * sc);
                float lx = -r + close * ((float)width * 0.5F + r * 0.35F) - r * 0.35F;
                float rx = (float)width + r - close * ((float)width * 0.5F + r * 0.35F) + r * 0.35F;

                for (int f = 0; f < 4; f++) {
                    float fy = y + ((float)f - 1.5F) * r * 0.42F;
                    limb(lx, fy, r * 0.24F, lx + r * 1.25F, fy + (float)hash(fx.seed, f, beat) * 8.0F * sc, r * 0.19F, fx.color);
                    limb(rx, fy, r * 0.24F, rx - r * 1.25F, fy + (float)hash(fx.seed, f + 9, beat) * 8.0F * sc, r * 0.19F, MAGENTA);
                }

                blob(lx, y, r, fx.color);
                blob(rx, y, r, MAGENTA);
            }
            default -> {
            }
        }
    }

    /** Fetching from a container: the arm takes hold of it, and the item itself comes back along the arm. */
    private static void grab(Fx fx, int now, int beat, int width, int height) {
        float sc = scale();
        float[] tip = project(fx.pos);
        float reach = fx.age < 3 ? (float)(fx.age + 1) / 3.0F : 1.0F;
        if (fx.life <= 3) {
            reach = Math.min(reach, (float)fx.life / 4.0F);
        }

        if (reach <= 0.02F) {
            return;
        }

        boolean hauling = fx.age >= 4 && fx.age <= 15;
        chain(fx, (float)width + 14.0F * sc, (float)height * 0.62F, tip[0], tip[1], reach, 1.0F, fx.age >= 4 ? 1.0F : -1.0F, now, beat, fx.color, 0, 1.0F);
        fist(fx, 15.0F * sc * Math.min(1.0F, reach * 1.2F), fx.age == 3 ? 0 : (fx.age == 4 ? 1 : 2), fx.color, now, beat);
        pellets(SLOT_HAUL, reach);
        if (hauling && fx.entityId >= 0) {
            // On twos, like everything else here.
            float s = 1.0F - (float)((fx.age - 4) / 2 * 2) / 11.0F;
            float x = curveX(s, (float)hash(fx.seed, fx.age / 2, 3) * 10.0F * sc);
            float y = curveY(s, (float)hash(fx.seed, fx.age / 2, 4) * 10.0F * sc);
            spike(x, y, 30.0F * sc, 11.0F * sc, 7, (float)now * 0.7F, SOLID | 0xFFFFFF);
            blob(x, y, 15.0F * sc, GOLD);
            ICONS.add(new float[]{(float)fx.entityId, x, y, 2.4F * sc, (float)hash(fx.seed, fx.age / 2, 5) * 50.0F});
        }
    }

    /** Trading places: an arm from each side closes on the target; on the tick of the trade they cross the whole picture. */
    private static void swap(MinecraftClient client, Fx fx, int now, int beat, int width, int height) {
        float sc = scale();
        float[] tip = project(current(fx, client));
        float reach = fx.age < 2 ? (float)(fx.age + 1) / 2.0F : 1.0F;
        if (fx.life <= 3) {
            reach = Math.min(reach, (float)fx.life / 4.0F);
        }

        int since = fx.age - fx.slot;
        if (since == 0 || since == 1) {
            // The wrench itself: two bands of the mass thrown corner to corner.
            float wide = (since == 0 ? 30.0F : 18.0F) * sc;
            limb(-20.0F, -20.0F, wide, (float)width + 20.0F, (float)height + 20.0F, wide, MAGENTA);
            limb(-20.0F, (float)height + 20.0F, wide, (float)width + 20.0F, -20.0F, wide, CYAN);
            stroke(-20.0F, -20.0F, (float)width + 20.0F, (float)height + 20.0F, wide * 0.25F, SOLID | 0xFFFFFF);
            stroke(-20.0F, (float)height + 20.0F, (float)width + 20.0F, -20.0F, wide * 0.25F, SOLID | 0xFFFFFF);
        }

        if (reach <= 0.02F) {
            return;
        }

        boolean gripping = fx.age >= 2;
        int hit = fx.age == 2 || since == 0 ? 0 : (fx.age == 3 || since == 1 ? 1 : 2);
        float squeeze = gripping ? 4.0F * sc : 18.0F * sc;
        chain(fx, -14.0F * sc, (float)height * 0.6F, tip[0] - squeeze, tip[1], reach, 1.15F, -1.0F, now, beat, MAGENTA, 1, -1.0F);
        fist(fx, 15.0F * sc * Math.min(1.0F, reach * 1.2F), hit, MAGENTA, now, beat);
        chain(fx, (float)width + 14.0F * sc, (float)height * 0.6F, tip[0] + squeeze, tip[1], reach, 1.15F, -1.0F, now, beat, CYAN, 2, 1.0F);
        fist(fx, 15.0F * sc * Math.min(1.0F, reach * 1.2F), hit, CYAN, now, beat);
    }

    /** Strings let down from above onto a creature: a puppet's three with their bar, or a single hook that lifts. */
    private static void strings(MinecraftClient client, Fx fx, int now, int beat) {
        Entity entity = fx.entityId >= 0 && client.world != null ? client.world.getEntityById(fx.entityId) : null;
        Vec3d top = entity != null ? entity.getPos().add(0.0, (double)entity.getHeight(), 0.0) : fx.pos;
        if (entity != null) {
            fx.pos = top;
        }

        float[] head = project(top);
        if (head[2] == 0.0F || fx.life < 8 && fx.life % 2 == 0) {
            return;
        }

        float sc = scale();
        int count = Math.max(1, fx.slot);
        double distance = Math.sqrt(top.squaredDistanceTo(cameraPos));
        float spread = MathHelper.clamp((float)(170.0 / Math.max(2.0, distance)), 5.0F, 34.0F) * sc;
        float grown = Math.min(1.0F, (float)(fx.age + 1) / 3.0F);
        float barX = head[0] + (float)hash(fx.seed, beat / 3, 1) * 16.0F * sc;
        float barY = 7.0F * sc;
        float tilt = (float)hash(fx.seed, beat / 2, 2) * 9.0F * sc;
        if (count > 1) {
            // The control bar, rocking in a hand that is not shown.
            stroke(barX - 38.0F * sc, barY - tilt, barX + 38.0F * sc, barY + tilt, 3.5F * sc, SOLID | fx.color);
            stroke(barX, barY - 10.0F * sc, barX, barY + 12.0F * sc, 3.0F * sc, SOLID | fx.color);
        }

        for (int i = 0; i < count; i++) {
            float off = count == 1 ? 0.0F : (float)i - (float)(count - 1) * 0.5F;
            if (hash(fx.seed + (long)i, beat, 5) > 0.42) {
                // A string that is not there this beat.
                continue;
            }

            float sx = barX + off * 34.0F * sc;
            float sy = count > 1 ? barY + off * tilt : -6.0F;
            float ex = head[0] + off * spread;
            float ey = head[1] + (off == 0.0F ? 0.0F : spread * 0.9F);
            ex = sx + (ex - sx) * grown;
            ey = sy + (ey - sy) * grown;
            float mx = (sx + ex) * 0.5F + (float)hash(fx.seed + (long)i, beat, 3) * 6.0F * sc;
            float my = (sy + ey) * 0.5F;
            stroke(sx, sy, mx, my, 1.2F * sc, SOLID | fx.color);
            stroke(mx, my, ex, ey, 1.2F * sc, SOLID | fx.color);
            // A bead of the mass running down each string.
            float bead = (float)Math.floorMod(now * 2 + i * 7, 16) / 16.0F;
            float bx = bead < 0.5F ? MathHelper.lerp(bead * 2.0F, sx, mx) : MathHelper.lerp(bead * 2.0F - 1.0F, mx, ex);
            float by = bead < 0.5F ? MathHelper.lerp(bead * 2.0F, sy, my) : MathHelper.lerp(bead * 2.0F - 1.0F, my, ey);
            blob(bx, by, 3.2F * sc, 0xFFFFFF);
            // The hook.
            spike(ex, ey, 8.0F * sc, 2.5F * sc, 4, (float)(beat % 2) * 0.785F, SOLID | 0xFFFFFF);
            blob(ex, ey, 3.5F * sc, fx.color);
        }
    }

    /** Speed lines: everything streaks past for a few ticks. */
    private static void rush(Fx fx, int now, int width, int height) {
        float sc = scale();
        if (fx.life <= 2 && fx.life % 2 == 0) {
            return;
        }

        float cx = (float)width * 0.5F;
        float cy = (float)height * 0.5F;
        if (fx.slot == 0) {
            for (int i = 0; i < 30; i++) {
                double a = (double)i / 30.0 * Math.PI * 2.0 + hash(fx.seed, i, now) * 0.25;
                float near = (55.0F + (float)(hash(fx.seed, i + 40, now) + 0.5) * 110.0F + (float)fx.age * 18.0F) * sc;
                float far = (float)Math.max(width, height);
                stroke(cx + (float)Math.cos(a) * near, cy + (float)Math.sin(a) * near, cx + (float)Math.cos(a) * far, cy + (float)Math.sin(a) * far, (1.2F + (float)(i % 3)) * sc, BARE | (i % 3 == 0 ? fx.color : 0xFFFFFF));
            }
        } else {
            // Wound back like tape: bars tearing sideways across the picture.
            for (int i = 0; i < 14; i++) {
                float y = (float)(hash(fx.seed, i, now) + 0.5) * (float)height;
                float from = (float)(hash(fx.seed, i + 20, now) + 0.5) * (float)width * 0.6F;
                float thickness = (1.5F + (float)(i % 4) * 1.5F) * sc;
                stroke(from, y, from + (float)width * (0.3F + 0.5F * (float)(hash(fx.seed, i + 60, now) + 0.5)), y, thickness, BARE | (i % 3 == 0 ? 0xFFFFFF : (i % 3 == 1 ? MAGENTA : fx.color)));
            }

            // Two chevrons pointing back the way the Host came.
            for (int k = 0; k < 2; k++) {
                float x = cx - 20.0F * sc + (float)k * 46.0F * sc - (float)(now % 2) * 8.0F * sc;
                stroke(x + 26.0F * sc, cy - 30.0F * sc, x, cy, 6.0F * sc, SOLID | 0xFFFFFF);
                stroke(x, cy, x + 26.0F * sc, cy + 30.0F * sc, 6.0F * sc, SOLID | 0xFFFFFF);
            }
        }
    }

    /** The shot: a knot of mass at the bottom of the screen, a lock on the target, then a bolt that is simply there. */
    private static void shot(MinecraftClient client, Fx fx, int now, int beat, int width, int height) {
        float sc = scale();
        int launch = fx.launch();
        boolean lash = fx.slot != 0;
        float ox = lash ? (float)width + 14.0F * sc : (float)width * 0.5F;
        float oy = lash ? (float)height * 0.55F : (float)height + 10.0F * sc;
        float[] tip = project(current(fx, client));
        if (fx.age < launch) {
            float charge = (float)(fx.age + 1) / (float)Math.max(1, launch);
            blob(ox + (float)hash(fx.seed, now, 1) * 8.0F * sc, oy - 22.0F * sc * charge, (12.0F + 34.0F * charge) * sc, fx.color);

            for (int i = 0; i < 5; i++) {
                double a = Math.PI + (hash(fx.seed, i, now) + 0.5) * Math.PI;
                float away = (30.0F + 60.0F * (1.0F - charge)) * sc;
                blob(ox + (float)Math.cos(a) * away, oy - 22.0F * sc * charge + (float)Math.sin(a) * away * 0.6F, (4.0F + 5.0F * charge) * sc, i % 2 == 0 ? fx.color : 0xFFFFFF);
            }

            if (!lash && tip[2] != 0.0F) {
                // Lock-on: four corners slam shut around the target.
                float open = (70.0F - 50.0F * charge) * sc;
                float arm = 12.0F * sc;
                float t = 2.5F * sc;
                int tone = fx.age % 2 == 0 ? SOLID | 0xFFFFFF : SOLID | MAGENTA;

                for (int sx = -1; sx <= 1; sx += 2) {
                    for (int sy = -1; sy <= 1; sy += 2) {
                        float px = tip[0] + (float)sx * open;
                        float py = tip[1] + (float)sy * open;
                        box(Math.min(px, px - (float)sx * arm), py - t, Math.max(px, px - (float)sx * arm), py + t, tone);
                        box(px - t, Math.min(py, py - (float)sy * arm), px + t, Math.max(py, py - (float)sy * arm), tone);
                    }
                }
            }

            return;
        }

        int since = fx.age - launch;
        int after = fx.age - fx.total;
        // No travel to speak of: half way on the first tick, there on the second.
        float head = since <= 0 ? 0.55F : 1.0F;
        if (after >= 2 && fx.age % 2 == 1 || after >= 4) {
            return;
        }

        float dx = tip[0] - ox;
        float dy = tip[1] - oy;
        float len = Math.max(1.0F, (float)Math.sqrt((double)(dx * dx + dy * dy)));
        float nx = -dy / len;
        float ny = dx / len;
        int joints = 9;
        float[] xs = new float[joints];
        float[] ys = new float[joints];
        float thin = after > 0 ? 0.55F : 1.0F;

        for (int i = 0; i < joints; i++) {
            float s = (float)i / (float)(joints - 1) * head;
            // A fresh zigzag every tick.
            float jag = i == 0 || i == joints - 1 ? 0.0F : (float)hash(fx.seed, i, now) * (lash ? 34.0F : 60.0F) * sc;
            xs[i] = ox + dx * s + nx * jag;
            ys[i] = oy + dy * s + ny * jag;
        }

        float width0 = (lash ? 9.0F : 17.0F) * sc * thin;

        for (int i = 0; i + 1 < joints; i++) {
            float w = width0 * (1.0F - 0.5F * (float)i / (float)joints);
            limb(xs[i], ys[i], w, xs[i + 1], ys[i + 1], w * 0.9F, fx.color);
        }

        for (int i = 0; i + 1 < joints; i++) {
            stroke(xs[i], ys[i], xs[i + 1], ys[i + 1], width0 * 0.28F, SOLID | 0xFFFFFF);
        }

        // Forks kicking off the main bolt.
        for (int k = 0; k < 3; k++) {
            int at = 2 + k * 2;
            if (at >= joints) {
                break;
            }

            double a = hash(fx.seed + (long)k, now, 3) * Math.PI * 2.0;
            float out = (30.0F + (float)(hash(fx.seed + (long)k, now, 4) + 0.5) * 50.0F) * sc;
            stroke(xs[at], ys[at], xs[at] + (float)Math.cos(a) * out, ys[at] + (float)Math.sin(a) * out, 2.5F * sc, SOLID | (k % 2 == 0 ? 0xFFFFFF : fx.color));
        }

        float fist = (lash ? 13.0F : 24.0F) * sc * thin;
        if (head >= 1.0F) {
            int turn = now * 37 % 360;
            spike(tip[0], tip[1], (after <= 0 ? 70.0F : 100.0F) * sc * (lash ? 0.6F : 1.0F), fist * 0.9F, 10, (float)Math.toRadians((double)turn), SOLID | 0xFFFFFF);
            spike(tip[0], tip[1], (after <= 0 ? 42.0F : 60.0F) * sc * (lash ? 0.6F : 1.0F), fist * 0.6F, 7, -(float)Math.toRadians((double)turn), SOLID | fx.color);
        }

        blob(xs[joints - 1], ys[joints - 1], fist, fx.color);
    }

    /** A jump of the mass between two things in the world: a hard zigzag, different every tick. */
    private static void arc(Fx fx, int now) {
        float[] a = project(fx.source);
        float[] b = project(fx.pos);
        if (a[2] == 0.0F && b[2] == 0.0F || fx.life <= 2 && fx.life % 2 == 0) {
            return;
        }

        float sc = scale();
        int joints = 7;
        float dx = b[0] - a[0];
        float dy = b[1] - a[1];
        float len = Math.max(1.0F, (float)Math.sqrt((double)(dx * dx + dy * dy)));
        float px = a[0];
        float py = a[1];

        for (int i = 1; i < joints; i++) {
            float s = (float)i / (float)(joints - 1);
            float jag = i == joints - 1 ? 0.0F : (float)hash(fx.seed, i, now) * 34.0F * sc;
            float x = a[0] + dx * s - dy / len * jag;
            float y = a[1] + dy * s + dx / len * jag;
            limb(px, py, 6.0F * sc, x, y, 5.0F * sc, fx.color);
            stroke(px, py, x, y, 1.6F * sc, SOLID | 0xFFFFFF);
            px = x;
            py = y;
        }

        spike(b[0], b[1], 28.0F * sc, 9.0F * sc, 7, (float)now, SOLID | 0xFFFFFF);
        blob(b[0], b[1], 9.0F * sc, fx.color);
    }

    /** A shockwave across the ground, in steps, with an echo behind it. */
    private static void pulse(Fx fx, int width, int height) {
        float sc = scale();
        float t = MathHelper.clamp((float)fx.age / (float)Math.max(1, fx.total), 0.0F, 1.0F);
        float ease = 1.0F - (1.0F - t) * (1.0F - t);
        if (t > 0.7F && fx.age % 2 == 0) {
            return;
        }

        if (fx.slot == 0) {
            float x = (float)width * 0.5F;
            float y = (float)height * 1.02F;
            hoop(x, y, ease * (float)width * 0.74F, 0.42F, (10.0F + 26.0F * (1.0F - t)) * sc, SOLID | fx.color);
            hoop(x, y, ease * (float)width * 0.74F - 8.0F * sc, 0.42F, 4.0F * sc, SOLID | 0xFFFFFF);
            hoop(x, y, ease * (float)width * 0.5F, 0.42F, (5.0F + 10.0F * (1.0F - t)) * sc, SOLID | MAGENTA);
        } else {
            float[] at = project(fx.pos.add(0.0, 0.1, 0.0));
            if (at[2] != 0.0F) {
                hoop(at[0], at[1], ease * 80.0F * sc, 0.45F, (5.0F + 12.0F * (1.0F - t)) * sc, SOLID | fx.color);
                hoop(at[0], at[1], ease * 50.0F * sc, 0.45F, 4.0F * sc, SOLID | 0xFFFFFF);
            }
        }
    }

    /** A lasting state (barrier, slowed time, a mended chip): the mass crowds in around the frame. */
    private static void frame(Fx fx, int beat, int width, int height) {
        float show = Math.min(Math.min(1.0F, (float)(fx.age + 1) / 3.0F), Math.min(1.0F, (float)fx.life / 3.0F));
        if (show <= 0.02F) {
            return;
        }

        float unit = (float)height * 0.085F;
        int bottom = 11;
        float px = 0.0F;
        float py = 0.0F;
        float pr = 0.0F;

        for (int i = 0; i < bottom; i++) {
            float r = unit * (0.75F + 0.5F * (float)Math.abs(hash(fx.seed, i, beat / 2)));
            float x = (float)width * ((float)i + 0.5F) / (float)bottom;
            float y = (float)height + r * (1.0F - 0.75F * show) - (float)hash(fx.seed, i, beat) * r * 0.4F;
            if (i > 0) {
                limb(px, py, pr, x, y, r, fx.color);
            } else {
                blob(x, y, r, fx.color);
            }

            px = x;
            py = y;
            pr = r;
        }

        for (int i = 0; i < 5; i++) {
            float r = unit * (0.6F + 0.4F * (float)Math.abs(hash(fx.seed, i, 7))) * (1.0F - (float)i / 5.0F * 0.55F);
            float y = (float)height - unit * 0.6F - (float)i * unit * 1.25F;
            float bob = (float)hash(fx.seed, i + 20, beat) * r * 0.5F;
            blob(-r * (1.0F - 0.8F * show) + bob, y, r, fx.color);
            blob((float)width + r * (1.0F - 0.8F * show) - bob, y, r, fx.color);
        }

        // Loose squares of it climbing the edges.
        for (int i = 0; i < 8; i++) {
            float x = i % 2 == 0 ? (float)(cell * (1 + i)) : (float)width - (float)(cell * (2 + i));
            float y = (float)height - (float)Math.floorMod(beat * 13 + i * 47, Math.max(1, height));
            box(x, y, x + (float)(cell * 2) - 1.0F, y + (float)(cell * 2) - 1.0F, SOLID | (i % 3 == 0 ? 0xFFFFFF : fx.color));
        }
    }

    /** The turret node: a knot of the mass hanging in the air inside a snapping bracket. */
    private static void node(Fx fx, int beat) {
        float[] at = project(fx.pos);
        if (at[2] == 0.0F || fx.life < 6 && fx.life % 2 == 0) {
            return;
        }

        float sc = scale();
        double distance = Math.sqrt(fx.pos.squaredDistanceTo(cameraPos));
        float r = MathHelper.clamp((float)(150.0 / Math.max(2.0, distance)), 5.0F, 22.0F) * sc * Math.min(1.0F, (float)(fx.age + 1) / 2.0F);
        spike(at[0], at[1], r * 2.3F, r * 0.9F, 4, (float)(beat % 2) * 0.785F, SOLID | 0xFFFFFF);
        blob(at[0], at[1], r * (beat % 2 == 0 ? 1.0F : 1.15F), fx.color);

        for (int i = 0; i < 3; i++) {
            double a = (double)beat * 0.9 + (double)i * Math.PI * 2.0 / 3.0;
            blob(at[0] + (float)Math.cos(a) * r * 2.0F, at[1] + (float)Math.sin(a) * r * 1.2F, r * 0.32F, i == 0 ? MAGENTA : fx.color);
        }
    }

    /**
     * Volta's face before a shot. Not a portrait held on screen: a few ticks of strobing, each one a
     * different size, place and expression, torn into strips, with the odd frame blown out to white.
     */
    private static void face(DrawContext context, Fx fx, int width, int height, float tickDelta) {
        int t = fx.age;
        if (t > fx.total + 1) {
            return;
        }

        int rgb = fx.color & 0xFFFFFF;
        int cy = height / 2 + (int)(hash(fx.seed, t, 6) * (double)height * 0.08);
        int band = (int)((float)Math.min(width, height) * (0.2F + 0.14F * (float)Math.abs(hash(fx.seed, t, 7))));
        boolean blown = hash(fx.seed, t, 5) > 0.24;
        boolean shown = t <= 1 || hash(fx.seed, t, 11) > -0.3;
        context.fill(0, cy - band - 3, width, cy - band, SOLID | (t % 2 == 0 ? rgb : MAGENTA));
        context.fill(0, cy + band, width, cy + band + 3, SOLID | (t % 2 == 0 ? MAGENTA : rgb));
        if (!shown) {
            // A dropped frame: only the bars and a wash of static.
            context.fill(0, cy - band, width, cy + band, 0x500B1030);
            return;
        }

        context.fill(0, cy - band, width, cy + band, blown ? 0xF2FFFFFF : 0xE60B1030);
        int[] looks = new int[]{1, 4, 1, 7, 6, 1, 4, 7};
        VoltaRig.forced = looks[Math.floorMod(t + (int)fx.seed, looks.length)];
        float size = (float)Math.min(width, height) * (0.74F + 0.3F * (float)(hash(fx.seed, t, 2) + 0.5));
        float x = (float)width * 0.5F - size * 0.5F + (float)hash(fx.seed, t, 3) * (float)width * 0.2F;
        float y = (float)cy - size * 0.47F;
        int strips = 6;

        for (int i = 0; i < strips; i++) {
            int y0 = cy - band + 2 * band * i / strips;
            int y1 = cy - band + 2 * band * (i + 1) / strips;
            float slip = hash(fx.seed, t * 7 + i, 8) > -0.1 ? (float)hash(fx.seed, t * 13 + i, 9) * (float)width * 0.14F : 0.0F;
            context.enableScissor(0, y0, width, y1);
            VoltaRig.draw(context, x + slip - 10.0F, y, size, 0.0F, 0.4F, tickDelta);
            VoltaRig.draw(context, x + slip + 10.0F, y + 3.0F, size, 0.0F, 0.4F, tickDelta);
            VoltaRig.draw(context, x + slip, y, size, t % 3 == 0 ? -6.0F : 0.0F, 1.0F, tickDelta);
            context.disableScissor();
        }

        VoltaRig.forced = -1;

        for (int line = cy - band; line < cy + band; line += 3) {
            context.fill(0, line, width, line + 1, (blown ? 0x30 : 0x48) << 24 | INK);
        }

        // Blocks of dead signal and streaks, different every tick.
        for (int i = 0; i < 9; i++) {
            int bx = (int)((hash(fx.seed, t * 5 + i, 12) + 0.5) * (double)width);
            int by = cy - band + (int)((hash(fx.seed, t * 5 + i, 13) + 0.5) * (double)(band * 2));
            int bw = 12 + (int)((hash(fx.seed, t * 5 + i, 14) + 0.5) * (double)width * 0.22);
            int tone = i % 3 == 0 ? 0xFFFFFF : (i % 3 == 1 ? MAGENTA : rgb);
            context.fill(bx, by, bx + bw, by + 1 + i % 4, SOLID | tone);
        }
    }
}
