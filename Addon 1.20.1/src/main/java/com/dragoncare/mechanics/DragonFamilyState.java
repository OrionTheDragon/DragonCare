package com.dragoncare.mechanics;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * World-level persistent storage for dragon family bonds (Mother <-> Babies).
 */
public class DragonFamilyState extends PersistentState {

    private static final String STORAGE_KEY = "dragoncare_family";
    private static final String LIST_TAG = "families";
    private static final String COUNTERS_TAG = "spawn_counters";

    private final Map<UUID, FamilyData> data = new HashMap<>();
    
    // Roost and Cave counters per dragon type (0=Fire, 1=Ice, 2=Lightning)
    public final int[] roostCounters = new int[3];
    public final int[] caveCounters = new int[3];

    public static DragonFamilyState get(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        return overworld.getPersistentStateManager().getOrCreate(DragonFamilyState::fromNbt, DragonFamilyState::new, STORAGE_KEY
        );
    }

    public FamilyData peek(UUID dragonId) {
        return data.get(dragonId);
    }

    public FamilyData getOrCreate(UUID dragonId) {
        return data.computeIfAbsent(dragonId, k -> new FamilyData());
    }

    public void remove(UUID dragonId) {
        data.remove(dragonId);
        markDirty();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (Map.Entry<UUID, FamilyData> e : data.entrySet()) {
            NbtCompound c = new NbtCompound();
            c.putUuid("dragon", e.getKey());
            FamilyData d = e.getValue();
            
            if (d.motherId != null) {
                c.putUuid("motherId", d.motherId);
            }
            
            if (!d.babyIds.isEmpty()) {
                NbtList babiesList = new NbtList();
                for (UUID babyId : d.babyIds) {
                    NbtCompound bc = new NbtCompound();
                    bc.putUuid("id", babyId);
                    babiesList.add(bc);
                }
                c.put("babies", babiesList);
            }

            if (!d.ignoredPanicsCount.isEmpty()) {
                NbtList panicsList = new NbtList();
                for (Map.Entry<UUID, Integer> entry : d.ignoredPanicsCount.entrySet()) {
                    NbtCompound pc = new NbtCompound();
                    pc.putUuid("player", entry.getKey());
                    pc.putInt("count", entry.getValue());
                    panicsList.add(pc);
                }
                c.put("ignoredPanics", panicsList);
            }
            
            list.add(c);
        }
        nbt.put(LIST_TAG, list);
        
        NbtCompound counters = new NbtCompound();
        counters.putIntArray("roosts", roostCounters);
        counters.putIntArray("caves", caveCounters);
        nbt.put(COUNTERS_TAG, counters);
        
        return nbt;
    }

    public static DragonFamilyState fromNbt(NbtCompound nbt) {
        DragonFamilyState s = new DragonFamilyState();
        
        if (nbt.contains(COUNTERS_TAG, NbtElement.COMPOUND_TYPE)) {
            NbtCompound counters = nbt.getCompound(COUNTERS_TAG);
            int[] r = counters.getIntArray("roosts");
            int[] c = counters.getIntArray("caves");
            if (r.length == 3) System.arraycopy(r, 0, s.roostCounters, 0, 3);
            if (c.length == 3) System.arraycopy(c, 0, s.caveCounters, 0, 3);
        }

        NbtList list = nbt.getList(LIST_TAG, NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            try {
                UUID id = c.getUuid("dragon");
                FamilyData d = new FamilyData();
                
                if (c.contains("motherId")) {
                    d.motherId = c.getUuid("motherId");
                }
                
                if (c.contains("babies", NbtElement.LIST_TYPE)) {
                    NbtList babiesList = c.getList("babies", NbtElement.COMPOUND_TYPE);
                    for (int j = 0; j < babiesList.size(); j++) {
                        NbtCompound bc = babiesList.getCompound(j);
                        d.babyIds.add(bc.getUuid("id"));
                    }
                }

                if (c.contains("ignoredPanics", NbtElement.LIST_TYPE)) {
                    NbtList panicsList = c.getList("ignoredPanics", NbtElement.COMPOUND_TYPE);
                    for (int j = 0; j < panicsList.size(); j++) {
                        NbtCompound pc = panicsList.getCompound(j);
                        d.ignoredPanicsCount.put(pc.getUuid("player"), pc.getInt("count"));
                    }
                }
                
                s.data.put(id, d);
            } catch (Exception ignored) { com.dragoncare.DragonCare.LOGGER.debug("Swallowed exception", ignored); }
        }
        return s;
    }

    /**
     * Removes entries for dragons that no longer exist in any loaded world.
     * Should be called periodically (e.g. on server tick or world load).
     */
    public static void cleanupStaleEntries(MinecraftServer server) {
        DragonFamilyState state = get(server);
        Iterator<Map.Entry<UUID, FamilyData>> it = state.data.entrySet().iterator();
        boolean changed = false;
        while (it.hasNext()) {
            Map.Entry<UUID, FamilyData> entry = it.next();
            UUID dragonId = entry.getKey();
            boolean found = false;
            for (ServerWorld world : server.getWorlds()) {
                if (world.getEntity(dragonId) != null) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            state.markDirty();
        }
    }

    public static class FamilyData {
        public UUID motherId = null;
        public final List<UUID> babyIds = new ArrayList<>();
        // Counter for learning to trust players (persisted to NBT)
        public final Map<UUID, Integer> ignoredPanicsCount = new HashMap<>();
    }
}


