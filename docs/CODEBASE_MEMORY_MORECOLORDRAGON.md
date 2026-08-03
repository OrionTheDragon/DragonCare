# Codebase Memory: MoreColorDragon

More Color Dragon is a separate addon for Ice and Fire CE 1.21.1. It is not a DragonCare version and should
not be mirrored into DragonCare. It adds persistent per-dragon color data, dynamic texture generation, custom
eye rendering, a Paint Gun, and simple genetic inheritance.

## Project Facts

| Directory | MC version | Loader | Java | Mod id | Current version |
| --- | --- | --- | --- | --- | --- |
| `MoreColorDragon/` | 1.21.1 | NeoForge 21.1.230 | 21 | `morecolordragon` | `0.5.0` |

Gradle properties:

- `maven_group=com.morecolordragon`
- `archives_name=Ice and Fire - More Color Dragon`
- `minecraft_version=1.21.1`
- `neoforge_version=21.1.230`

Public GitHub repository:

- `https://github.com/OrionTheDragon/MoreColorDragon`

## Color System

The addon colors five visual channels per dragon:

- abdomen
- back
- sides / base
- left eye
- right eye

Body colors and eye colors are independent, so heterochromia is supported naturally.

When `disableElementCheck` / cross-element mixing is enabled, the full palette can be used across dragon
elements. The known marketing/statistics number is:

- `15` body colors across abdomen, back, and sides/base: `15^3 = 3,375`
- `27` eye colors for left and right eyes: `27^2 = 729`
- total per dragon: `3,375 * 729 = 2,460,375`

Use `2,460,375 color variations per dragon` for English marketing text and `2 460 375 вариаций окраса` for
Russian text.

## State And Sync

Server state:

- `DragonColorState` stores color data in world persistent state under `morecolordragon_colors`.
- Records are keyed by dragon UUID.
- Saved data includes active body/eye colors and body-color alleles used by breeding.

Color assignment:

- `DragonColorManager` checks whether a dragon already has saved color data.
- New dragons receive generated colors based on their element unless cross-element mixing is enabled.
- `DragonVariantConfig` owns palettes and validation helpers.

Sync:

- `DragonColorSyncPayload` sends server color data to clients.
- `ClientColorCache` stores synced data client-side.
- Gameplay changes must remain server-authoritative.

## Rendering

The client rendering pipeline:

- `DragonTextureProviderMixin` intercepts Ice and Fire CE dragon texture resolution.
- `DragonColorTextureBlender` blends the base texture with selected overlay PNGs.
- The generated texture is registered as a dynamic texture and cached.
- `DragonCustomEyesFeatureRenderer` renders left and right eye overlays separately.
- `EyeTextureNormalizer` prepares eye textures to avoid bright additive artifacts.
- Texture caches must destroy dynamic textures before clearing.

Body overlay folders:

- `option_abdomen`
- `option_back`
- `option_sides_and_base`

Cross-element overlays resolve by the element that owns the chosen color. `TextureMorpher` can adapt overlays
when the chosen color's source element differs from the dragon's current type.

## Paint Gun

The Paint Gun lets players manually recolor dragons.

Expected flow:

- Client opens the GUI through `PaintGunClientHelper` / `PaintGunScreen`.
- Selected colors are sent using `PaintDragonPayload`.
- Server validates held item, distance, ammo, target dragon, and color IDs.
- One ammo is consumed when paint is applied.
- A glass bottle is returned when the gun becomes empty.
- Washing requires a water bucket and restores genetic colors.

Important rule: client choices are input only. The server decides whether a paint action is valid.

## Genetics

Breeding integration is built around Ice and Fire CE egg creation:

- `DragonAIMateGoalMixin` attaches parent UUIDs to eggs.
- Color alleles are rolled and associated with the egg UUID.
- `DragonEggNbtMixin` persists parent UUIDs on the egg entity.
- `DragonEggEntityMixin` transfers stored egg genetics to the hatched baby.

Body colors use simple dominance values inside palettes. Eye colors are inherited separately and can produce
heterochromia.

## DragonCare Compatibility

DragonCare compatibility is soft. If DragonCare is installed, MoreColorDragon can reflectively read the client
dirt cache and blend DragonCare dirt overlays into generated dragon textures. MoreColorDragon must still work
when DragonCare is absent.

## Do Not Publish Dev Files

`MoreColorDragon/` contains local dev artifacts near real source files. Do not publish or commit these as mod
source:

- `.gradle/`, `build/`, `run/`, `temp_decomp/`
- `bestiary.txt`, `context.txt`, `egg_bytecode.txt`, `prompts.txt`
- `blended_test_*.png`
- `ImageInfo.java`, `ImageInfo.class`, `TestContext.java`
- `update_colors.py`, `update_langs.py`

Publish only clean mod files: `src/`, `gradle/`, `libs/` if intentionally required, `mappings-patch/`, Gradle
files, metadata, README, license, and `.gitignore`.

## Build Check

Run from the module directory:

```powershell
cd MoreColorDragon
.\gradlew.bat build
```

The jar is generated under `build/libs/`.
