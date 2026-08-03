# Publishing And Releases

This workspace contains source projects, upstream references, logs, dumps, and local helper files. Never push
the whole root workspace as a public repository.

## Core Rules

- Publish only the clean files for the specific mod being released.
- Never publish tokens, logs, crash reports, dumps, decompiled files, `.class` files, temporary folders, or
  generated jars in git history.
- Do not store release jars in git. Upload jars through GitHub Releases, CurseForge, and Modrinth.
- Keep DragonCare and MoreColorDragon release flows separate unless the user explicitly asks for a combined
  release.

Secrets:

- `token_modrinth.txt` and `token_curseforge.txt` may exist outside the workspace, for example on Desktop.
- Read tokens only when the user explicitly asks for platform/API work.
- Never print token values, include them in docs, or commit them.

## DragonCare Publishing

DragonCare has two maintained versions:

- `Addon/`: Minecraft 1.21.1, NeoForge, Java 21.
- `Addon 1.20.1/`: Minecraft 1.20.1, Forge, Java 17.

Before release:

- Update the version in the correct `gradle.properties`.
- Mirror relevant gameplay/resource/localization changes across both DragonCare versions.
- Build the changed version, and preferably both versions when shared logic changed.
- Check that jar contents do not contain dev classes, logs, token files, or local notes.
- Prepare bilingual changelog text when uploading to public platforms.

Current public platform facts as of 2026-07-28:

- Modrinth slug: `ice-and-fire-dragon-care`
- Modrinth project id: `Hlm7REws`
- CurseForge page: `https://www.curseforge.com/minecraft/mc-mods/ice-and-fire-dragon-care`
- CurseForge project id observed from the public listing: `1548726`
- Combined public downloads were around 10k+, but this number is time-sensitive and must be rechecked before
  using it in current marketing.

## MoreColorDragon Publishing

MoreColorDragon is a separate addon and has its own repository:

- `https://github.com/OrionTheDragon/MoreColorDragon`

Use a clean publish copy rather than pushing the raw working folder. The previous safe pattern was to copy only
selected mod files into a separate publish clone, build from that clone, then commit and push there.

Safe source set:

- `src/`
- `gradle/`
- `libs/` only if intentionally required by the build
- `mappings-patch/`
- `build.gradle`
- `gradle.properties`
- `settings.gradle`
- `gradlew`
- `gradlew.bat`
- `README.md`
- `.gitignore`
- `morecolordragon.refmap.json`

Exclude local/dev artifacts:

- `.gradle/`, `build/`, `run/`, `temp_decomp/`
- logs, `.class`, `*.tmp`, `*.jar`
- `bestiary.txt`, `context.txt`, `egg_bytecode.txt`, `prompts.txt`
- `ImageInfo.java`, `TestContext.java`, helper scripts, local test images

If CurseForge only offers `Resource Packs`, `Scenarios`, and `Worlds` as a required main category for
MoreColorDragon, choose `Resource Packs` as the least-wrong fallback.

## Release Checklist

- Confirm target mod and MC version.
- Confirm version number in `gradle.properties`.
- Run the module build.
- Check localization keys for English, Russian, and Ukrainian where relevant.
- Check jar contents for accidental dev files.
- Prepare platform summary and changelog.
- Upload jar to Modrinth/CurseForge/GitHub Releases as appropriate.
- After publishing, verify platform pages and download links.

## Git Safety

- Do not use destructive git commands unless explicitly requested.
- Do not revert user changes you did not make.
- Stage only files that belong to the requested mod/release.
- For MoreColorDragon, use its publish clone/repository, not the DragonCare root repo, unless the user asks for
  local-only documentation changes.
