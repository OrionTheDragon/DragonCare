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
 * World-level persistent storage for dragon bond/affection points.
 *
 * <p>Bond is per-dragon (since {@code TameableEntity} only allows one owner at a time).
 * Stored in the overworld's data folder so it survives server restarts.
 */
public class BondState extends PersistentState {

    private static final String STORAGE_KEY = "dragoncare_bond";
    private static final String LIST_TAG = "bonds";

    private final Map<UUID, BondData> data = new HashMap<>();

    public static BondState get(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        return overworld.getPersistentStateManager().getOrCreate(
                new PersistentState.Type<>(BondState::new, BondState::fromNbt, null),
                STORAGE_KEY
        );
    }

    public BondData peek(UUID dragonId) {
        return data.get(dragonId);
    }

    public BondData getOrCreate(UUID dragonId) {
        return data.computeIfAbsent(dragonId, k -> new BondData());
    }

    public void markDirty() {
        setDirty(true);
    }

    /** Удаляет запись связи дракона (вызывается при его гибели — прунинг по смерти). */
    public void remove(UUID dragonId) {
        if (data.remove(dragonId) != null) {
            markDirty();
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtList list = new NbtList();
        for (Map.Entry<UUID, BondData> e : data.entrySet()) {
            NbtCompound c = new NbtCompound();
            c.putUuid("dragon", e.getKey());
            BondData d = e.getValue();
            c.putInt("points", d.points);
            c.putLong("windowStart", d.windowStartTick);
            c.putInt("feedsInWindow", d.feedsInWindow);
            c.putLong("lastPassive", d.lastPassiveTick);
            list.add(c);
        }
        nbt.put(LIST_TAG, list);
        return nbt;
    }

    public static BondState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        BondState s = new BondState();
        NbtList list = nbt.getList(LIST_TAG, NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            try {
                UUID id = c.getUuid("dragon");
                BondData d = new BondData();
                d.points = c.getInt("points");
                d.windowStartTick = c.getLong("windowStart");
                d.feedsInWindow = c.getInt("feedsInWindow");
                d.lastPassiveTick = c.getLong("lastPassive");
                s.data.put(id, d);
            } catch (Exception ignored) {
                // skip malformed
            }
        }
        return s;
    }
}
