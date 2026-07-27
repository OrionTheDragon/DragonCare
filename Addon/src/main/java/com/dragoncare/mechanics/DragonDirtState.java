package com.dragoncare.mechanics;

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
 * World-level persistent storage for dragon dirtiness.
 */
public class DragonDirtState extends PersistentState {

    private static final String STORAGE_KEY = "dragoncare_dirt";
    private static final String LIST_TAG = "dirts";

    private final Map<UUID, DirtData> data = new HashMap<>();

    public static DragonDirtState get(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        return overworld.getPersistentStateManager().getOrCreate(
                new PersistentState.Type<>(DragonDirtState::new, DragonDirtState::fromNbt, null),
                STORAGE_KEY
        );
    }

    public DirtData peek(UUID dragonId) {
        return data.get(dragonId);
    }

    public DirtData getOrCreate(UUID dragonId) {
        return data.computeIfAbsent(dragonId, k -> new DirtData());
    }

    public void markDirty() {
        setDirty(true);
    }

    /** Удаляет запись грязи дракона (вызывается при его гибели — прунинг по смерти). */
    public void remove(UUID dragonId) {
        if (data.remove(dragonId) != null) {
            markDirty();
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtList list = new NbtList();
        for (Map.Entry<UUID, DirtData> e : data.entrySet()) {
            NbtCompound c = new NbtCompound();
            c.putUuid("dragon", e.getKey());
            DirtData d = e.getValue();
            c.putInt("level", d.dirtLevel);
            c.putLong("lastUpdateTick", d.lastUpdateTick);
            list.add(c);
        }
        nbt.put(LIST_TAG, list);
        return nbt;
    }

    public static DragonDirtState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        DragonDirtState s = new DragonDirtState();
        NbtList list = nbt.getList(LIST_TAG, NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            try {
                UUID id = c.getUuid("dragon");
                DirtData d = new DirtData();
                d.dirtLevel = c.getInt("level");
                d.lastUpdateTick = c.getLong("lastUpdateTick");
                s.data.put(id, d);
            } catch (Exception ignored) { com.dragoncare.DragonCare.LOGGER.debug("Swallowed exception", ignored); }
        }
        return s;
    }

    public static class DirtData {
        public int dirtLevel = 0;
        public long lastUpdateTick = 0;
    }
}
