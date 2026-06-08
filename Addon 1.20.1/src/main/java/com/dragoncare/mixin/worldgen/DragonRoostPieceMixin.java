package com.dragoncare.mixin.worldgen;

import com.dragoncare.mechanics.DragonFamilyManager;
import com.dragoncare.mechanics.DragonFamilyState;

import com.iafenvoy.iceandfire.entity.EntityDragonBase;
import com.iafenvoy.iceandfire.entity.EntityFireDragon;
import com.iafenvoy.iceandfire.entity.EntityIceDragon;
import com.iafenvoy.iceandfire.entity.EntityLightningDragon;
import com.iafenvoy.iceandfire.world.structure.DragonRoostStructure;
import net.minecraft.entity.EntityType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.iafenvoy.iceandfire.world.structure.DragonRoostStructure$DragonRoostPiece")
public abstract class DragonRoostPieceMixin {

    @Shadow
    protected abstract EntityType<? extends EntityDragonBase> getDragonType();

    @Inject(method = "spawnDragon", at = @At("TAIL"), remap = false, locals = org.spongepowered.asm.mixin.injection.callback.LocalCapture.CAPTURE_FAILSOFT)
    private void dragoncare$onSpawnRoostDragon(StructureWorldAccess world, BlockPos origin, Random random, int ageOffset, boolean isMale, CallbackInfo ci, EntityDragonBase mother) {
        if (com.dragoncare.config.AddonConfig.DISABLE_WILD_BABY_SPAWNS.get()) return;

        MinecraftServer server = world.getServer();
        if (server == null || mother == null) return;
        int typeIndex = 0; // 0=Fire, 1=Ice, 2=Lightning
                if (mother instanceof EntityFireDragon) { typeIndex = 0; }
        else if (mother instanceof EntityIceDragon) { typeIndex = 1; }
        else if (mother instanceof EntityLightningDragon) { typeIndex = 2; }
        else return;

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
                String.valueOf(typeIndex), babiesToSpawn, mother.getX(), mother.getY(), mother.getZ());

            int successfulSpawns = 0;
            for (int i = 0; i < babiesToSpawn; i++) {
                EntityDragonBase baby = this.getDragonType().create(world.toServerWorld());
                if (baby != null) {
                    baby.setGender(random.nextBoolean());
                    baby.growDragon(1 + random.nextInt(24)); // Stage 1 (days 1-24)
                    baby.setAgingDisabled(true); // Don't age naturally
                    if (com.dragoncare.config.AddonConfig.WILD_DRAGONS_AGE.get()) {
                        baby.setAgingDisabled(false);
                    }
                    baby.setHealth(baby.getMaxHealth());
                    java.util.List<com.iafenvoy.iceandfire.data.DragonColor> colors = com.iafenvoy.iceandfire.data.DragonColor.getColorsByType(baby.dragonType);
                    baby.setVariant(colors.get(random.nextInt(colors.size())).name());
                    
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
            com.mojang.logging.LogUtils.getLogger().debug("[DragonCare] Roost counter for {} is {}/{}", String.valueOf(typeIndex), state.roostCounters[typeIndex], target);
            state.markDirty();
        }
    }
}



