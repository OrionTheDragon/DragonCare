package com.dragoncare.mixin.worldgen;

import com.dragoncare.mechanics.DragonFamilyManager;
import com.dragoncare.mechanics.DragonFamilyState;
import com.iafenvoy.iceandfire.data.DragonType;
import com.iafenvoy.iceandfire.entity.EntityDragonBase;
import com.iafenvoy.iceandfire.entity.EntityFireDragon;
import com.iafenvoy.iceandfire.entity.EntityIceDragon;
import com.iafenvoy.iceandfire.entity.EntityLightningDragon;
import net.minecraft.entity.EntityType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import com.iafenvoy.iceandfire.world.structure.DragonCaveStructure;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.iafenvoy.iceandfire.world.structure.DragonCaveStructure$DragonCavePiece")
public abstract class DragonCavePieceMixin {

    @Shadow
    protected abstract EntityType<? extends EntityDragonBase> getDragonType();

    @Inject(method = "createDragon", at = @At("RETURN"), remap = false)
    private void dragoncare$onCreateCaveDragon(StructureWorldAccess worldGen, Random random, BlockPos position, int dragonAge, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<EntityDragonBase> cir) {
        if (com.dragoncare.config.AddonConfig.DISABLE_WILD_BABY_SPAWNS.get()) return;

        MinecraftServer server = worldGen.getServer();
        if (server == null) return;

        EntityDragonBase mother = cir.getReturnValue();
        if (mother == null) return;
        int typeIndex = 0; // 0=Fire, 1=Ice, 2=Lightning
                if (mother instanceof EntityFireDragon) { typeIndex = 0; }
        else if (mother instanceof EntityIceDragon) { typeIndex = 1; }
        else if (mother instanceof EntityLightningDragon) { typeIndex = 2; }
        else return;

        DragonFamilyState state = DragonFamilyState.get(server);
        state.caveCounters[typeIndex]++;

        // Caves trigger every 2 to 3 instances
        int target = 2 + random.nextInt(2); // 2 or 3
        if (state.caveCounters[typeIndex] >= target) {
            state.caveCounters[typeIndex] = 0;
            state.markDirty();

            // Force gender to female
            mother.setGender(false);

            // Determine how many babies to spawn
            int babiesToSpawn = 1;
            int r = random.nextInt(100);
            if (r < 33) {
                babiesToSpawn = 3;
            } else if (r < 83) { // 50% chance for 2nd
                babiesToSpawn = 2;
            }

            for (int i = 0; i < babiesToSpawn; i++) {
                EntityDragonBase baby = this.getDragonType().create(worldGen.toServerWorld());
                if (baby != null) {
                    baby.setGender(random.nextBoolean());
                    
                    // 50/50 chance for Stage 1 or Stage 2
                    if (random.nextBoolean()) {
                        baby.growDragon(1 + random.nextInt(24)); // Stage 1
                    } else {
                        baby.growDragon(25 + random.nextInt(24)); // Stage 2
                    }
                    
                    baby.setAgingDisabled(true);
                    if (com.dragoncare.config.AddonConfig.WILD_DRAGONS_AGE.get()) {
                        baby.setAgingDisabled(false);
                    }
                    baby.setHealth(baby.getMaxHealth());
                    java.util.List<com.iafenvoy.iceandfire.data.DragonColor> colors = com.iafenvoy.iceandfire.data.DragonColor.getColorsByType(baby.dragonType);
                    baby.setVariant(colors.get(random.nextInt(colors.size())).name());
                    
                    double offsetX = (random.nextDouble() - 0.5) * 6.0;
                    double offsetZ = (random.nextDouble() - 0.5) * 6.0;
                    
                    int spawnX = (int) (position.getX() + offsetX);
                    int spawnZ = (int) (position.getZ() + offsetZ);
                    int spawnY = position.getY() + 1;
                    
                    // Safe spawn check: try to find air above a solid block
                    for (int dy = 3; dy >= -3; dy--) {
                        BlockPos checkPos = new BlockPos(spawnX, position.getY() + dy, spawnZ);
                        if (worldGen.isAir(checkPos) && worldGen.getBlockState(checkPos.down()).isSolidBlock(worldGen, checkPos.down())) {
                            spawnY = checkPos.getY();
                            break;
                        }
                    }

                    baby.updatePositionAndAngles(spawnX + 0.5, spawnY, spawnZ + 0.5, random.nextFloat() * 360, 0);
                    baby.setHunger(50);
                    
                    if (mother.hasHomePosition && mother.homePos != null) {
                        baby.homePos = new com.iafenvoy.iceandfire.entity.util.HomePosition(mother.homePos.getPosition(), worldGen.toServerWorld());
                        baby.hasHomePosition = true;
                    }
                    
                    // We must wait for the mother to be spawned before babies are linked, 
                    // or just spawn babies now. They will be in the world.
                    if (worldGen.spawnEntity(baby)) {
                        DragonFamilyManager.linkFamily(mother, baby);
                    }
                }
            }
        } else {
            state.markDirty();
        }
    }
}



