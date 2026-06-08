package com.dragoncare.dragonphone;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.*;

/**
 * Глобальный реестр прирученных драконов, хранится в overworld через {@link PersistentState}.
 * Используется «Драконьим телефоном», чтобы видеть драконов даже в выгруженных
 * чанках или других измерениях.
 *
 * <p>Записи обновляются периодически из {@link DragonTrackingHandler}: пока сущность
 * жива и тикает, последние известные координаты (+ имя, стадия) сохраняются в этом
 * хранилище. Запись удаляется только при смерти дракона.</p>
 */
public class DragonRegistryState extends PersistentState {

    private static final String STATE_KEY = "dragoncare_dragon_registry";

    private static final Type<DragonRegistryState> TYPE = new Type<>(
            DragonRegistryState::new,
            DragonRegistryState::fromNbt,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE // нейтральный fix-type, у CE используется CHUNK — обоим всё равно
    );

    private final Map<UUID, DragonRecord> records = new HashMap<>();

    public DragonRegistryState() {
        super();
    }

    private DragonRegistryState(Map<UUID, DragonRecord> initial) {
        this.records.putAll(initial);
    }

    public Collection<DragonRecord> all() {
        return Collections.unmodifiableCollection(records.values());
    }

    public List<DragonRecord> ownedBy(UUID owner) {
        List<DragonRecord> out = new ArrayList<>();
        for (DragonRecord r : records.values()) {
            if (owner.equals(r.ownerId())) out.add(r);
        }
        return out;
    }

    /** Проставляет/обновляет запись и помечает state «грязным» только при изменении. */
    public void putOrUpdate(DragonBaseEntity dragon, long tick) {
        if (dragon == null) return;
        UUID owner = dragon.getOwnerUuid();
        if (owner == null) return; // только прирученные

        UUID id = dragon.getUuid();
        World world = dragon.getWorld();
        net.minecraft.util.Identifier dim = world.getRegistryKey().getValue();
        Vec3d p = dragon.getPos();
        Text custom = dragon.getCustomName();
        String name = custom != null ? custom.getString() : null;
        int stage = dragon.getDragonStage();
        String species = dragon.getType().getTranslationKey();

        DragonRecord existing = records.get(id);
        if (existing == null) {
            records.put(id, new DragonRecord(id, owner, dim, p.x, p.y, p.z, name, stage, tick, species));
            markDirty();
        } else {
            // Сравниваем грубо, чтобы не дёргать markDirty каждый тик ради дельты в 0.001 блока.
            boolean changed = !existing.ownerId().equals(owner)
                    || !existing.dimension().equals(dim)
                    || Math.abs(existing.x() - p.x) > 0.5
                    || Math.abs(existing.y() - p.y) > 0.5
                    || Math.abs(existing.z() - p.z) > 0.5
                    || existing.stage() != stage
                    || !Objects.equals(existing.customName(), name)
                    || !Objects.equals(existing.species(), species);
            existing.update(dim, p.x, p.y, p.z, name, stage, tick, species);
            if (changed) markDirty();
        }
    }

    public void remove(UUID dragonId) {
        if (records.remove(dragonId) != null) markDirty();
    }

    // ------------------------ NBT ------------------------

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtList list = new NbtList();
        for (DragonRecord r : records.values()) list.add(r.writeNbt());
        nbt.put("Dragons", list);
        return nbt;
    }

    private static DragonRegistryState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        Map<UUID, DragonRecord> map = new HashMap<>();
        NbtList list = nbt.getList("Dragons", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            DragonRecord r = DragonRecord.readNbt(list.getCompound(i));
            map.put(r.dragonId(), r);
        }
        return new DragonRegistryState(map);
    }

    /** Получить (или создать) реестр; всегда живёт в overworld. */
    public static DragonRegistryState get(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        PersistentStateManager mgr = overworld.getPersistentStateManager();
        return mgr.getOrCreate(TYPE, STATE_KEY);
    }
}
