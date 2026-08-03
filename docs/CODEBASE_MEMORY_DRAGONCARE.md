# Codebase Memory: DragonCare

DragonCare is an addon for Ice and Fire: Community Edition. It adds dragon taming, bonding, care,
veterinary tools, dirt/cleaning mechanics, ash poisoning, Dragon Phone tracking, structures, and AI behavior.

## Versions

| Directory | MC version | Loader | Java | Mod id | Current version |
| --- | --- | --- | --- | --- | --- |
| `Addon/` | 1.21.1 | NeoForge 21.1.230 | 21 | `dragoncare` | `1.2.6 - 1.21.1v` |
| `Addon 1.20.1/` | 1.20.1 | Forge 47.3.3 | 17 | `dragoncare` | `1.0.3 - 1.20.1v` |

`Addon/` is the primary target. `Addon 1.20.1/` is the legacy port, but it is still important because
Minecraft 1.20.1 remains a strong modded version.

## Dual-Version Maintenance

Most DragonCare changes must be applied to both versions:

- Mirror gameplay logic, Java classes, recipes, localization, config behavior, and user-facing mechanics when
  equivalent files exist.
- Do not copy files byte-for-byte across versions without checking loader and Minecraft API differences.
- Keep Java 17 compatibility in `Addon 1.20.1/`.
- Quote `"Addon 1.20.1"` in shell commands.

Common resource differences:

- 1.21.1 recipes live under `src/main/resources/data/dragoncare/recipe/`.
- 1.20.1 recipes live under `src/main/resources/data/dragoncare/recipes/`.
- 1.21.1 advancements and loot tables can use singular 1.21 conventions.
- 1.20.1 often uses plural folder names and older JSON result formats.

## Architecture

Entry point:

- `com.dragoncare.DragonCare`
- Registers deferred registries, config, items, blocks, effects, sounds, data components, loot modifiers, and
  recipe conditions.

Config:

- `config/AddonConfig.java` is the main tunable source.
- Stage-based values are loaded into stage maps at init.
- Structure toggles and spawn multipliers live here.

Ice and Fire integration:

- Mixins are the main integration layer.
- Important mixins include dragon base behavior, aging, attribute cap changes, dirt handling, sleep prevention,
  and worldgen hooks.
- Treat upstream Ice and Fire CE source directories as references only.

Major subsystems:

- `taming/`: bond state, bond levels, server-authoritative bond point logic, taming start flow, sync data.
- `mechanics/`: dirt accumulation/cleaning, ash poisoning, family behavior, and dragon AI goals.
- `worldgen/`: schematic loading and structure placers for hunter houses, guilds, and village forge ruins.
- `network/`: sync payloads for bond, dirt, Dragon Phone HUD/list, and client/server actions.
- `dragonphone/`: tracking item, GUI, data components, HUD, and telemetry-style dragon state display.
- `client/`: screens, client caches, dirt texture blending, bond/dirt cache handling, and sensor sound logic.
- `item/`: Dragon Brush, Dragon Phone, syringe, scale shears, painkiller, ash items, fruit, and registries.
- `recipe/`: simplified craft condition and custom recipe behavior.
- `event/`: gameplay hooks for damage, death cleanup, feeding, passive bond gain, dirt ticks, and ash scans.

## Gotchas

- Static caches must be cleaned. Cooldown maps, ash poisoning state, client bond caches, and dirt caches need
  death/logout/periodic cleanup hooks.
- Client dynamic textures must be released correctly when texture caches are cleared.
- Network handlers must be server-authoritative for gameplay effects. Client GUI input cannot be trusted.
- Recipe changes need both folder-name and JSON-format checks between 1.21.1 and 1.20.1.
- Config changes need localization keys where the config UI exposes labels/tooltips.
- Worldgen structure placement is sensitive to tick cost; avoid large synchronous placement spikes.
- Avoid Java 21-only syntax in the 1.20.1 copy.

## Build Checks

Run from the module directory:

```powershell
cd Addon
.\gradlew.bat build
```

```powershell
cd "Addon 1.20.1"
.\gradlew.bat build
```

There are no dedicated unit tests. A successful Gradle build is the primary verification gate.
