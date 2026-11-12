# Windy Origins

An **Origins** add-on for Minecraft **1.20.1** (Fabric) with nine origins. It began as
*Mirrored Origin*, a mod with a single origin; the Mirrored is still here, joined by eight more.

| Origin | In one line |
|---|---|
| **Mirrored** | Lives in an empty reflection of the world and can bear reality only seconds at a time. A pacifist: the mirror returns every blow it deals. |
| **Stalker** (bounty hunter) | Marks one target and is deadly to it alone; slow and fragile without a contract. |
| **Frostborn** | Carries the cold: freezes water underfoot (and under a boat), chills enemies, seals itself in ice. |
| **Luminian** | A living light. Cannot hide, burns the undead, rises once from death at dawn. |
| **Folded** | Born in a pocket of space. Hovers, and carries a hitscan rifle that pierces armour. |
| **Sanguine** | A vampire. Blood is its hunger bar, and every kind of blood grants a different trait. |
| **Revenant** | A soul that will not leave. Possesses creatures, sends wisps on errands, takes ten seconds of vengeance after death. |
| **Host** | Has a chip in the temple and Volta in the chip: a companion who fights, scouts and works on request, overheats, plays pranks and can break. |
| **Dreamwalker** | Puts creatures to sleep and walks into their dreams. |

Every power is described in game on the origin selection screen. Texts are complete in English
and Russian; the other bundled languages fall back to English where a power has changed.

## Requirements

| Dependency | Version |
|---|---|
| Minecraft | 1.20.1 |
| Fabric Loader | 0.15.0+ |
| Fabric API | any for 1.20.1 |
| Origins | 1.10.0+ |
| Java | 17+ |

Apoli and Calio come bundled with Origins. The mod is needed on the server and on every client.

## Building

```
./gradlew build
```

The mod jar is written to `build/libs/WindyOrigins-<version>.jar`. All dependencies are resolved
from Maven repositories; nothing has to be placed in `libs/`.

## About this source tree

From version 1.1.0 on, the sources were rebuilt from the released 1.0.0 jar with a decompiler
(Vineflower, Yarn names) and developed further from there. Code that dates from 1.0.0 therefore
has machine-generated local variable names (`var1`, `var2`); code written since has ordinary
names. The original, hand-written sources of the first Mirrored Origin release are kept in this
repository's history, and its README is preserved as
[`docs/MIRRORED_ORIGIN_1.0.md`](docs/MIRRORED_ORIGIN_1.0.md), which still explains how the two
phases of the world work.
