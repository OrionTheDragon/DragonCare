package com.dragoncare.mechanics;

import com.iafenvoy.iceandfire.entity.EntityDragonBase;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DragonFamilyManager {

    /** Creates a two-way family link between a mother and a baby. */
    public static void linkFamily(EntityDragonBase mother, EntityDragonBase baby) {
        MinecraftServer server = mother.getServer();
        if (server == null || mother.getWorld().isClient) return;

        DragonFamilyState state = DragonFamilyState.get(server);
        
        DragonFamilyState.FamilyData motherData = state.getOrCreate(mother.getUuid());
        if (!motherData.babyIds.contains(baby.getUuid())) {
            motherData.babyIds.add(baby.getUuid());
        }

        DragonFamilyState.FamilyData babyData = state.getOrCreate(baby.getUuid());
        babyData.motherId = mother.getUuid();

        state.markDirty();
    }

    /** Returns the mother of the given baby, if she exists and is loaded in the same world. */
    public static EntityDragonBase getMother(EntityDragonBase baby) {
        MinecraftServer server = baby.getServer();
        if (server == null || baby.getWorld().isClient) return null;

        DragonFamilyState state = DragonFamilyState.get(server);
        DragonFamilyState.FamilyData babyData = state.peek(baby.getUuid());
        
        if (babyData == null || babyData.motherId == null) return null;

        ServerWorld world = (ServerWorld) baby.getWorld();
        net.minecraft.entity.Entity entity = world.getEntity(babyData.motherId);
        
        if (entity instanceof EntityDragonBase mother) {
            return mother;
        }
        return null;
    }

    /** Returns a list of loaded babies belonging to the given mother. */
    public static List<EntityDragonBase> getLoadedBabies(EntityDragonBase mother) {
        List<EntityDragonBase> loadedBabies = new ArrayList<>();
        MinecraftServer server = mother.getServer();
        if (server == null || mother.getWorld().isClient) return loadedBabies;

        DragonFamilyState state = DragonFamilyState.get(server);
        DragonFamilyState.FamilyData motherData = state.peek(mother.getUuid());
        if (motherData == null) return loadedBabies;
        
        ServerWorld world = (ServerWorld) mother.getWorld();
        for (UUID babyId : motherData.babyIds) {
            net.minecraft.entity.Entity entity = world.getEntity(babyId);
            if (entity instanceof EntityDragonBase baby) {
                loadedBabies.add(baby);
            }
        }
        
        return loadedBabies;
    }

    /** Checks if two dragons are directly related (mother-baby or siblings). */
    public static boolean isFamily(EntityDragonBase d1, EntityDragonBase d2) {
        MinecraftServer server = d1.getServer();
        if (server == null || d1.getWorld().isClient) return false;

        DragonFamilyState state = DragonFamilyState.get(server);
        DragonFamilyState.FamilyData fd1 = state.peek(d1.getUuid());
        DragonFamilyState.FamilyData fd2 = state.peek(d2.getUuid());

        if (fd1 != null && fd1.babyIds.contains(d2.getUuid())) return true; // d1 is mother, d2 is baby
        if (fd2 != null && fd2.babyIds.contains(d1.getUuid())) return true; // d2 is mother, d1 is baby
        
        // Sibling check
        if (fd1 != null && fd2 != null && fd1.motherId != null && fd1.motherId.equals(fd2.motherId)) {
            return true;
        }

        return false;
    }

    /** 
     * Called when a baby panics. It looks for its mother nearby and wakes her up/sets the target.
     */
    public static void callMotherForHelp(EntityDragonBase baby, LivingEntity threat) {
        EntityDragonBase mother = getMother(baby);
        if (mother != null && mother.squaredDistanceTo(baby) < 4096.0D) { // 64 blocks range
            
            // Prevent attacking an owner or befriended player
            if (threat instanceof net.minecraft.entity.player.PlayerEntity player) {
                boolean motherIsFriendly = false;
                if (mother.isTamed() && mother.getOwnerUuid() != null && mother.getOwnerUuid().equals(player.getUuid())) {
                    motherIsFriendly = true;
                } else {
                    MinecraftServer server = mother.getServer();
                    if (server != null && com.dragoncare.taming.DragonTamingManager.isBefriended(server, mother.getUuid(), player.getUuid())) {
                        motherIsFriendly = true;
                    }
                }
                
                if (motherIsFriendly) {
                    // Mother ignores the player. Baby learns to trust them.
                    MinecraftServer server = mother.getServer();
                    if (server != null && !baby.isTamed()) {
                        DragonFamilyState state = DragonFamilyState.get(server);
                        DragonFamilyState.FamilyData babyData = state.getOrCreate(baby.getUuid());
                        int count = babyData.ignoredPanicsCount.getOrDefault(player.getUuid(), 0) + 1;
                        if (count >= 3) {
                            // Baby learned that this player is safe!
                            com.dragoncare.taming.DragonTamingManager.forceBefriend(server, baby, player);
                        } else {
                            babyData.ignoredPanicsCount.put(player.getUuid(), count);
                        }
                    }
                    return; // Mother ignores the threat
                }
            }

            // Wake up mother if she's sleeping
            if (mother instanceof com.dragoncare.mechanics.ai.DragonSleepPreventer preventer) {
                 preventer.dragoncare$setSleepCooldown(400); // Prevent sleep for 20s
            }
            
            // If mother doesn't have a target, she attacks the threat
            if (mother.getTarget() == null || !mother.getTarget().isAlive()) {
                 mother.setTarget(threat);
            }
        }
    }

    /** Cleanup memory and cross-references when a dragon dies. */
    public static void onDragonDeath(EntityDragonBase dragon) {
        MinecraftServer server = dragon.getServer();
        if (server == null || dragon.getWorld().isClient) return;

        DragonFamilyState state = DragonFamilyState.get(server);
        DragonFamilyState.FamilyData data = state.peek(dragon.getUuid());
        if (data != null) {
            // If the dead dragon is a baby, remove it from its mother's baby list
            if (data.motherId != null) {
                DragonFamilyState.FamilyData motherData = state.peek(data.motherId);
                if (motherData != null) {
                    motherData.babyIds.remove(dragon.getUuid());
                }
            }

            // If the dead dragon is a mother, clear the motherId from all its babies
            for (UUID babyId : data.babyIds) {
                DragonFamilyState.FamilyData babyData = state.peek(babyId);
                if (babyData != null) {
                    babyData.motherId = null;
                }
            }
        }

        state.remove(dragon.getUuid());
    }
}



