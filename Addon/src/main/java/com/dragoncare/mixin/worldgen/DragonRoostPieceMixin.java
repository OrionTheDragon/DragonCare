package com.dragoncare.mixin.worldgen;

import com.dragoncare.mechanics.DragonFamilyManager;
import com.dragoncare.mechanics.DragonFamilyState;
import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.iafenvoy.iceandfire.entity.FireDragonEntity;
import com.iafenvoy.iceandfire.entity.IceDragonEntity;
import com.iafenvoy.iceandfire.entity.LightningDragonEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// DragonRoostPiece is a protected nested class. A class-valued target would require
// placing this mixin in IaF's package, which creates an illegal JPMS split package.
@Mixin(targets = "com.iafenvoy.iceandfire.world.structure.DragonRoostStructure$DragonRoostPiece")
public abstract class DragonRoostPieceMixin {

    @Shadow
    protected abstract EntityType<? extends DragonBaseEntity> getDragonType();

    @Redirect(
            method = "spawnDragon",
            remap = false,
            at = @At(value = "DRAGONCARE:SPAWN_ENTITY", remap = false)
    )
    private boolean dragoncare$onSpawnRoostDragon(StructureWorldAccess world, Entity entity,
                                                   StructureWorldAccess methodWorld,
                                                   BlockPos origin, Random random,
                                                   int ageOffset, boolean isMale) {
        boolean spawnedMother = world.spawnEntity(entity);
        if (!spawnedMother || !(entity instanceof DragonBaseEntity mother)) {
            return spawnedMother;
        }
        if (com.dragoncare.config.AddonConfig.DISABLE_WILD_BABY_SPAWNS.get()) {
            return true;
        }

        MinecraftServer server = world.getServer();
        if (server == null) return true;
        int typeIndex = 0; // 0=Fire, 1=Ice, 2=Lightning
        String typeName;
        if (mother instanceof FireDragonEntity) { typeIndex = 0; typeName = "fire"; }
        else if (mother instanceof IceDragonEntity) { typeIndex = 1; typeName = "ice"; }
        else if (mother instanceof LightningDragonEntity) { typeIndex = 2; typeName = "lightning"; }
        else return true;

        DragonFamilyState state = DragonFamilyState.get(server);
        state.roostCounters[typeIndex]++;

        // Roosts trigger every N instances based on config
        int target = com.dragoncare.config.AddonConfig.ROOST_SPAWN_RATE.get();
        if (state.roostCounters[typeIndex] >= target) {
            state.roostCounters[typeIndex] = 0;
            state.markDirty();

            // Force gender to female
            mother.setGender(false);

            // Determine how many babies to spawn
            int babiesToSpawn = 1;
            if (random.nextInt(100) < 33) {
                babiesToSpawn = 2;
            }
            
            com.mojang.logging.LogUtils.getLogger().debug("[DragonCare] Roost trigger reached for {} dragon! Attempting to spawn {} babies at mother pos: {}, {}, {}", 
                typeName, babiesToSpawn, mother.getX(), mother.getY(), mother.getZ());

            int successfulSpawns = 0;
            for (int i = 0; i < babiesToSpawn; i++) {
                DragonBaseEntity baby = this.getDragonType().create(world.toServerWorld());
                if (baby != null) {
                    baby.setGender(random.nextBoolean());
                    baby.growDragon(1 + random.nextInt(24)); // Stage 1 (days 1-24)
                    baby.setAgingDisabled(true); // Don't age naturally
                    if (com.dragoncare.config.AddonConfig.WILD_DRAGONS_AGE.get()) {
                        baby.setAgingDisabled(false);
                    }
                    baby.setHealth(baby.getMaxHealth());
                    baby.setVariant(com.dragoncare.compat.IafDragonVariants.randomVariant(
                            baby, random.nextInt(4)));
                    
                    // Spawn baby near mother, finding a safe surface height
                    double offsetX = (random.nextDouble() - 0.5) * 8.0; // Slightly larger radius for roosts
                    double offsetZ = (random.nextDouble() - 0.5) * 8.0;
                    
                    int spawnX = (int) (mother.getX() + offsetX);
                    int spawnZ = (int) (mother.getZ() + offsetZ);
                    
                    // Safe spawn check: find surface at X/Z
                    BlockPos safePos = world.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(spawnX, 0, spawnZ));
                    
                    // Fallback to mother's Y if the top position is absurdly high/low (e.g. spawned over a deep ravine)
                    double spawnY = safePos.getY();
                    if (Math.abs(spawnY - mother.getY()) > 10) {
                        spawnY = mother.getY() + 1.0;
                        com.mojang.logging.LogUtils.getLogger().debug("[DragonCare] Roost baby surface was too far ({} vs mother {}), falling back to mother Y.", safePos.getY(), mother.getY());
                    }
                    
                    baby.updatePositionAndAngles(spawnX + 0.5, spawnY, spawnZ + 0.5, random.nextFloat() * 360, 0);
                    baby.setHunger(50);
                    
                    if (mother.hasHomePosition && mother.homePos != null) {
                        baby.homePos = new com.iafenvoy.iceandfire.entity.util.HomePosition(mother.homePos.getPosition(), world.toServerWorld());
                        baby.hasHomePosition = true;
                    }
                    
                    if (world.spawnEntity(baby)) {
                        DragonFamilyManager.linkFamily(mother, baby);
                        successfulSpawns++;
                    } else {
                        com.mojang.logging.LogUtils.getLogger().error("[DragonCare] Failed to spawn roost baby at {}, {}, {}", spawnX, spawnY, spawnZ);
                    }
                }
            }
            com.mojang.logging.LogUtils.getLogger().debug("[DragonCare] Successfully spawned {}/{} roost babies.", successfulSpawns, babiesToSpawn);
        } else {
            com.mojang.logging.LogUtils.getLogger().debug("[DragonCare] Roost counter for {} is {}/{}", typeName, state.roostCounters[typeIndex], target);
            state.markDirty();
        }
        return true;
    }
}
