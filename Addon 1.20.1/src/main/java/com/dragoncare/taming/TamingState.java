package com.dragoncare.taming;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * World-level persistent state for dragon-player friendship/taming penalties.
 * Stored in the overworld's data folder so it survives server restarts.
 *
 * <p>Friendship is per-(dragon, player) pair. Each entry tracks:
 * <ul>
 *   <li>{@code feedingsNeeded} — how many feedings are required to make this
 *       specific dragon passive to this specific player (1 by default, raised
 *       after a betrayal).</li>
 *   <li>{@code feedingsDone} — feedings accumulated since the last reset.</li>
 *   <li>{@code active} — true when the dragon is currently passive to the player.</li>
 *   <li>{@code tamingDurationTicks} — duration of the next taming session (doubles
 *       each time the player hits the dragon during taming).</li>
 * </ul>
 */
public class TamingState extends PersistentState {

    public static final long BASE_TAMING_DURATION_TICKS = 20L * 60L * 3L; // 3 minutes
    private static final String STORAGE_KEY = "dragoncare_taming";
    private static final String LIST_TAG = "friendships";

    private final Map<String, FriendshipData> data = new HashMap<>();

    public static TamingState get(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        return overworld.getPersistentStateManager().getOrCreate(TamingState::fromNbt, TamingState::new, STORAGE_KEY
        );
    }

    private static String pairKey(UUID dragonId, UUID playerId) {
        return dragonId.toString() + "@" + playerId.toString();
    }

    public FriendshipData peek(UUID dragonId, UUID playerId) {
        return data.get(pairKey(dragonId, playerId));
    }

    public FriendshipData getOrCreate(UUID dragonId, UUID playerId) {
        return data.computeIfAbsent(pairKey(dragonId, playerId), k -> new FriendshipData());
    }

    public void remove(UUID dragonId, UUID playerId) {
        if (data.remove(pairKey(dragonId, playerId)) != null) {
            markDirty();
        }
    }

    public void markDirty() {
        setDirty(true);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (Map.Entry<String, FriendshipData> e : data.entrySet()) {
            String[] parts = e.getKey().split("@");
            if (parts.length != 2) continue;
            FriendshipData fd = e.getValue();
            NbtCompound entry = new NbtCompound();
            entry.putUuid("dragon", UUID.fromString(parts[0]));
            entry.putUuid("player", UUID.fromString(parts[1]));
            entry.putInt("feedingsNeeded", fd.feedingsNeeded);
            entry.putInt("feedingsDone", fd.feedingsDone);
            entry.putBoolean("active", fd.active);
            entry.putLong("tamingTicks", fd.tamingDurationTicks);
            entry.putLong("grudgeUntil", fd.grudgeUntilTick);
            entry.putInt("betrayalCount", fd.betrayalCount);
            list.add(entry);
        }
        nbt.put(LIST_TAG, list);
        return nbt;
    }

    public static TamingState fromNbt(NbtCompound nbt) {
        TamingState state = new TamingState();
        NbtList list = nbt.getList(LIST_TAG, NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound entry = list.getCompound(i);
            try {
                UUID dragon = entry.getUuid("dragon");
                UUID player = entry.getUuid("player");
                FriendshipData fd = new FriendshipData();
                if (entry.contains("feedingsNeeded")) fd.feedingsNeeded = Math.max(1, entry.getInt("feedingsNeeded"));
                if (entry.contains("feedingsDone"))   fd.feedingsDone   = Math.max(0, entry.getInt("feedingsDone"));
                if (entry.contains("active"))         fd.active         = entry.getBoolean("active");
                if (entry.contains("tamingTicks"))    fd.tamingDurationTicks = Math.max(20L, entry.getLong("tamingTicks"));
                if (entry.contains("grudgeUntil"))    fd.grudgeUntilTick = entry.getLong("grudgeUntil");
                if (entry.contains("betrayalCount"))  fd.betrayalCount = entry.getInt("betrayalCount");
                state.data.put(pairKey(dragon, player), fd);
            } catch (Exception ignored) {
                // skip malformed entries
            }
        }
        return state;
    }

    /** Per-(dragon, player) friendship record. */
    public static class FriendshipData {
        public int feedingsNeeded = 1;
        public int feedingsDone = 0;
        public boolean active = false;
        public long tamingDurationTicks = -1L; // -1 means uninitialized, will fall back to config
        public long grudgeUntilTick = 0;
        public int betrayalCount = 0;
        
        // Transient tracking for passive damage forgiveness
        public transient final java.util.List<DamageRecord> recentDamage = new java.util.ArrayList<>();
    }
    
    public static class DamageRecord {
        public final float amount;
        public final long tick;
        
        public DamageRecord(float amount, long tick) {
            this.amount = amount;
            this.tick = tick;
        }
    }
}


