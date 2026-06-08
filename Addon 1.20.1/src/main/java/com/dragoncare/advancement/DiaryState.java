package com.dragoncare.advancement;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks which hunter diaries each player has discovered.
 */
public class DiaryState extends PersistentState {

    private static final String STORAGE_KEY = "dragoncare_diaries";
    private static final String LIST_TAG = "players";

    private final Map<UUID, Set<String>> data = new HashMap<>();

    public static DiaryState get(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        return overworld.getPersistentStateManager().getOrCreate(DiaryState::fromNbt, DiaryState::new, STORAGE_KEY
        );
    }

    public boolean addDiary(UUID playerId, String title) {
        Set<String> diaries = data.computeIfAbsent(playerId, k -> new HashSet<>());
        if (diaries.add(title)) {
            markDirty();
            return true;
        }
        return false;
    }

    public int getCount(UUID playerId) {
        Set<String> diaries = data.get(playerId);
        return diaries == null ? 0 : diaries.size();
    }

    public void markDirty() {
        setDirty(true);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (Map.Entry<UUID, Set<String>> e : data.entrySet()) {
            NbtCompound c = new NbtCompound();
            c.putUuid("player", e.getKey());
            NbtList titles = new NbtList();
            for (String title : e.getValue()) {
                titles.add(net.minecraft.nbt.NbtString.of(title));
            }
            c.put("titles", titles);
            list.add(c);
        }
        nbt.put(LIST_TAG, list);
        return nbt;
    }

    public static DiaryState fromNbt(NbtCompound nbt) {
        DiaryState s = new DiaryState();
        NbtList list = nbt.getList(LIST_TAG, NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            try {
                UUID id = c.getUuid("player");
                Set<String> titles = new HashSet<>();
                NbtList titlesList = c.getList("titles", NbtElement.STRING_TYPE);
                for (int j = 0; j < titlesList.size(); j++) {
                    titles.add(titlesList.getString(j));
                }
                s.data.put(id, titles);
            } catch (Exception ignored) { com.dragoncare.DragonCare.LOGGER.debug("Swallowed exception", ignored); }
        }
        return s;
    }
}


