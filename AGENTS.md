# AGENTS.md

This file is the short Codebase Memory entrypoint for Codex and future agents working in this
workspace. Keep it concise. Put deep architecture notes in `docs/`.

## Codebase Memory MCP

This workspace uses `codebase-memory-mcp`. Prefer graph tools over text search for Java/code discovery:

1. `search_graph`
2. `trace_path`
3. `get_code_snippet`
4. `query_graph`
5. `get_architecture`

Use grep/ripgrep for string literals, configs, resources, localization, JSON/TOML, shell scripts, and cases
where graph results are insufficient.

Indexed project names:

- `DragonCare_1_21_1` for `Addon/`
- `DragonCare_1_20_1` for `Addon 1.20.1/`
- `D-MyAddon-IceAndFire1.21.1-MoreColorDragon` for `MoreColorDragon/`

Local MCP binary folder:

- `D:\codebase-memory-mcp-windows-amd64`

## Workspace Map

This workspace contains three active mod projects:

| Directory | Project | MC version | Loader | Java | Mod id |
| --- | --- | --- | --- | --- | --- |
| `Addon/` | DragonCare | 1.21.1 | NeoForge 21.1.230 | 21 | `dragoncare` |
| `Addon 1.20.1/` | DragonCare | 1.20.1 | Forge 47.3.3 | 17 | `dragoncare` |
| `MoreColorDragon/` | More Color Dragon | 1.21.1 | NeoForge 21.1.230 | 21 | `morecolordragon` |

Reference-only upstream sources:

- `IceAndFire-CE-master/` is Ice and Fire CE 1.21.1 source for reading internals.
- `IceAndFire-CE-1.20.1/` is Ice and Fire CE 1.20.1 source for reading internals.
- Do not treat either Ice and Fire CE directory as the addon being edited.

Detailed memory:

- `docs/CODEBASE_MEMORY_DRAGONCARE.md`
- `docs/CODEBASE_MEMORY_MORECOLORDRAGON.md`
- `docs/CODEBASE_MEMORY_MCP.md`
- `docs/PUBLISHING_AND_RELEASES.md`

## DragonCare Rule

DragonCare is maintained as two parallel copies. `Addon/` is the primary active target, but most Java,
recipe, config, localization, and gameplay changes must be mirrored into `Addon 1.20.1/` at the same
relative path when a twin exists.

Port logic, not bytes:

- NeoForge 1.21.1 uses `net.neoforged.*`, Java 21, and 1.21 resource conventions such as
  `data/<modid>/recipe`.
- Forge 1.20.1 uses `net.minecraftforge.*`, Java 17, and 1.20.1 resource conventions such as
  `data/<modid>/recipes`.
- Event, networking, registry, and JSON formats can differ between the two versions.

Always quote `"Addon 1.20.1"` in shell commands because the path contains a space.

## MoreColorDragon Rule

`MoreColorDragon/` is a separate addon, not a DragonCare version. Do not mirror MoreColorDragon changes
into DragonCare, and do not mirror DragonCare changes into MoreColorDragon unless the user explicitly asks
for a cross-addon integration.

MoreColorDragon has its own GitHub repository and release flow. Its current mod version is `0.5.0`.

## Build Commands

Run builds from inside each module:

```powershell
cd Addon
.\gradlew.bat build
```

```powershell
cd "Addon 1.20.1"
.\gradlew.bat build
```

```powershell
cd MoreColorDragon
.\gradlew.bat build
```

There are no dedicated unit tests in these addon modules; `build` is the main compile/package check.

## Never Publish Secrets Or Private Files

Never commit, publish, quote, or paste:

- `token_modrinth.txt`, `token_curseforge.txt`, or any `token*.txt`
- logs, crash reports, dumps, decompiled sources, temporary folders, `.class` files, generated jars
- personal notes, prompt dumps, helper scripts, local test images, workspace archives

Tokens may only be read when the user explicitly asks for platform/API actions, and their values must never
be shown in responses or written into repo files.

Do not publish the whole root workspace. It contains reference sources, dumps, logs, scratch files, and
private artifacts. Publish only the clean files for the specific mod being released.

## Key Gotchas

- Static caches keyed by dragon/player UUID must be cleared on death/logout or periodic cleanup.
- DragonCare simplified recipes are gated by `SimplifyCraftsCondition`; config toggles can require recipe
  reload behavior.
- Client texture caches must destroy dynamic textures before clearing when Minecraft owns GPU resources.
- For release work, verify localization keys and jar contents before uploading.
