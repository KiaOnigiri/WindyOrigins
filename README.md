# Mirrored Origin

An **Origins** add-on for Minecraft **1.20.1** (Fabric). It adds the **Mirrored** origin — a
being that does not live in the world, but in its reflection.

The mod creates no second dimension. It splits **one and the same world** into two phases,
normal and mirror. They share every block, but no entities. Two players can stand on the same
block and build the same house without ever seeing, hearing or touching one another.

---

## Requirements

| Dependency | Version |
|---|---|
| Minecraft | 1.20.1 |
| Fabric Loader | 0.15.0+ |
| Fabric API | any for 1.20.1 |
| Origins | 1.10.0+ |
| Apoli | 2.9.0+ |
| Calio | 1.11.0+ |
| Java | 17+ |

Apoli and Calio come bundled with Origins. The mod works in single-player and on a dedicated
server alike; on a server every client needs it as well.

---

## How the phases work

It all comes down to a single rule.

> **Blocks are shared. Entities are not.**

The world you build is one world for both phases. A placed block is seen by everyone, a chest
is opened by everyone, redstone fires for everyone. A pressure plate sinks under a player of
either phase, a beacon feeds both, a piston pushes all.

Everything alive or falling, however, is fully separated:

* they cannot see or hear each other;
* they cannot hit, push, shove or catch each other in a blast;
* they cannot be ridden, leashed, fed, traded with or sheared;
* arrows pass straight through, splash potions miss, status effects do not carry over;
* items and experience orbs are neither picked up nor merged across the divide;
* mobs do not target them, wardens do not hear them, pets do not follow them.

### Where an entity gets its phase

* **Natural spawns** are always in the normal phase. The Mirror World is empty — no zombies at
  night, no cows in the fields.
* **Spawned by something** — inherits the phase of whatever produced it: a bred baby, the
  children of a splitting slime, a chicken from a thrown egg, zombie reinforcements, a golem
  built by a player, a zombified piglin struck out of a pig by lightning.
* **Created by a player** — takes their phase: a placement, a shot, a thrown potion, `/summon`.
* **Commands from elsewhere** — the console, a command block or a datapack function carry no
  phase and land in the normal one.

The phase lives in NBT, so it survives chunk unloading, relogging and portal travel.

### Exceptions

**A thunderstorm strikes both phases.** A bolt sent by the weather — including one caught by a
lightning rod — belongs to nobody, so both worlds see it, hear it and suffer it. Lightning from
a Channeling trident or from `/summon`, on the other hand, stays in the phase of whoever called
it down.

**Turtle eggs remember the clutch.** The egg block has no block entity, so the phase is kept
separately in saved world state. A hatchling emerges in its mother's phase — or in the phase of
whoever placed the egg by hand.

**Pets sit down.** When their owner leaves for the other phase, a tamed mob sits as if the owner
had stepped through a nether portal, and stands up again on their return. If you ordered it to
sit before leaving, it stays seated afterwards.

---

## The Mirrored origin

Impact: **3** (highest).

### Powers

**Dead Reflection**
Seven hearts instead of ten, and you are undead — a reflection was never alive to begin with.
That carries the usual consequences: Potions of Harming heal you and Potions of Healing hurt
you, poison cannot touch you, and Regeneration has no effect at all, which makes regeneration
potions useless and leaves a golden apple with nothing but its absorption.

**Glass Skin**
A translucent, glassy tint to your model. Purely cosmetic.

**Mirror Spawn**
You are born and reborn in the Mirror World.

**Phase Shift** *(primary active)*
Switches your phase. Six second cooldown.
Returning to the Mirror World is free. Leaving for the Normal World costs **6 hunger** and all
saturation, and requires at least 6 hunger to begin with.

**Reality Toxicity**
The Normal World is poison. The count runs only while you are in it, and resets the moment you
return:

| Time in the Normal World | What happens |
|---|---|
| 10 seconds | Slowness II and Weakness II |
| 20 seconds | 3 magic damage per second |
| 25 seconds | Death |

In practice that is ten seconds of useful action per trip — beyond it you can barely move and
barely swing.

**Hydrophobia**
Water severs your tie to the Normal World. Any contact throws you instantly back into the
Mirror World, and while you are in it your active powers will not work. Seas and rivers in the
normal phase are closed to you.

**Banishment Punch** *(secondary active)*
Charges your fist for three seconds. A bare-handed strike within that window banishes the
target to the opposite phase.
Cooldown: ten minutes on a hit, five on a miss. Death does not reset it.

### How it plays

The strength and the weakness are the same thing. The Mirror World is perfectly safe — no
hostile mob ever spawns there, and night is no different from day. For exactly that reason
there is nothing to kill in it: no gunpowder, no bone, no string, no pearls.

So everything the living world provides has to be taken in short raids into reality — ten
seconds at a time, six hunger apiece. Farming, meanwhile, works as usual: crops are blocks, and
blocks are shared.

---

## Installation

Drop `MirroredOrigin-1.0.0.jar` into your `mods` folder alongside Fabric API, Origins, Apoli
and Calio. On a server, do the same there and make sure every player runs the same jar.

Grant the origin manually:

```
/origin set <player> origins:origin mirroredorigin:mirrored
```

Force a phase:

```
/power grant  <player> mirroredorigin:in_mirror_world
/power revoke <player> mirroredorigin:in_mirror_world
```

Test the split **with two people**: most of what the mod does is only visible when the players
are standing in opposite phases.

---

## Building from source

You need JDK 17 and Gradle — or IntelliJ IDEA, which brings its own.

1. Put three jars **from your own modpack** into `libs/` so the versions match exactly:
   `origins-*.jar`, `apoli-*.jar`, `calio-*.jar`.
   Do **not** put the original `MirroredOrigin-*.jar` there — it is this same mod, and the mod
   id will collide.
2. Open the folder as a Gradle project.
3. Build:

   | Shell | Command |
   |---|---|
   | PowerShell | `.\gradlew.bat build` |
   | CMD | `gradlew.bat build` |
   | Git Bash / Linux / macOS | `./gradlew build` |

   In IntelliJ the Gradle panel is easier: `Tasks` → `build` → `build`.

The finished mod is `build/libs/MirroredOrigin-1.0.0.jar`. The file with the `-sources` suffix
does not belong in your mods folder.

To run in a development environment: `./gradlew runClient`. For two players it is easier to use
`runServer` with `online-mode=false` in `run/server.properties` — "Open to LAN" fails session
verification in dev.

> **About Loom.** It refuses to process a mod dependency built by a newer Loom than itself. This
> project uses `fabric-loom 1.6-SNAPSHOT` with Gradle 8.7, which is enough for Origins, Apoli
> and Calio on 1.20.1. If you raise the Loom version, mind the minimum Gradle it needs:
> 1.6 → 8.6, 1.7 → 8.8, 1.9 → 8.11.

---

## Inside the mod

For anyone who intends to extend it.

**Storing the phase.** Ordinary entities keep it in `TrackedData<Boolean>` plus an NBT tag.
Players keep it in the Apoli power `mirroredorigin:in_mirror_world`, mirrored into tracked data
so a client can read another player's phase.

**Three thread-local mechanisms** live in `util`, and all three heal themselves at the head of
every world tick:

* `PhaseScope` — publishes a phase for the duration of an action: one entity tick, one packet
  handler. It nests and restores, so ticking a passenger inside its vehicle's tick does not
  wipe the enclosing phase.
* `SpawnPhaseOverride` — a forced phase for something about to be created. Consulted before
  anything else.
* `SoundState` — the entity currently emitting a sound, plus a "this effect belongs to both
  phases" flag.

**Filtering.** The bulk of the work happens in `ServerPlayNetworkHandlerFilterMixin`, which
drops packets by the recipient's phase. Where a packet names its own entity, the phase is taken
from that entity — more reliable than the surrounding context.

Some sounds the server never sends at all: the item pickup sound and the sculk shrieker's
shriek are played by the client itself upon receiving a service packet. Those are handled by
filtering the packet.

**The `mirroredorigin:cross_phase_effect` action** exists for effects that must reach both
phases — the sound and particles of a phase shift, the feedback of a banishment. It is used from
the power JSON files.

**The `CrossPhaseEntity` interface** marks an entity belonging to no phase at all. So far that
is weather lightning and nothing else.

There are 65 mixins in total, 55 server-side and 10 client-side; the full list lives in
`mirroredorigin.mixins.json`.

---

## Languages

English is the source language. Translations shipped: Russian, Ukrainian, German, French,
Spanish, Portuguese (Brazil), Polish, Chinese (Simplified) and Japanese.

To add one, copy `assets/mirroredorigin/lang/en_us.json` under the appropriate locale code and
translate the values. All sixteen keys must be present.
