package com.dragoncare.dragonphone;

import com.iafenvoy.iceandfire.entity.EntityDragonBase;
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
 * Р“Р»РѕР±Р°Р»СЊРЅС‹Р№ СЂРµРµСЃС‚СЂ РїСЂРёСЂСѓС‡РµРЅРЅС‹С… РґСЂР°РєРѕРЅРѕРІ, С…СЂР°РЅРёС‚СЃСЏ РІ overworld С‡РµСЂРµР· {@link PersistentState}.
 * РСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ В«Р”СЂР°РєРѕРЅСЊРёРј С‚РµР»РµС„РѕРЅРѕРјВ», С‡С‚РѕР±С‹ РІРёРґРµС‚СЊ РґСЂР°РєРѕРЅРѕРІ РґР°Р¶Рµ РІ РІС‹РіСЂСѓР¶РµРЅРЅС‹С…
 * С‡Р°РЅРєР°С… РёР»Рё РґСЂСѓРіРёС… РёР·РјРµСЂРµРЅРёСЏС….
 *
 * <p>Р—Р°РїРёСЃРё РѕР±РЅРѕРІР»СЏСЋС‚СЃСЏ РїРµСЂРёРѕРґРёС‡РµСЃРєРё РёР· {@link DragonTrackingHandler}: РїРѕРєР° СЃСѓС‰РЅРѕСЃС‚СЊ
 * Р¶РёРІР° Рё С‚РёРєР°РµС‚, РїРѕСЃР»РµРґРЅРёРµ РёР·РІРµСЃС‚РЅС‹Рµ РєРѕРѕСЂРґРёРЅР°С‚С‹ (+ РёРјСЏ, СЃС‚Р°РґРёСЏ) СЃРѕС…СЂР°РЅСЏСЋС‚СЃСЏ РІ СЌС‚РѕРј
 * С…СЂР°РЅРёР»РёС‰Рµ. Р—Р°РїРёСЃСЊ СѓРґР°Р»СЏРµС‚СЃСЏ С‚РѕР»СЊРєРѕ РїСЂРё СЃРјРµСЂС‚Рё РґСЂР°РєРѕРЅР°.</p>
 */
public class DragonRegistryState extends PersistentState {

    private static final String STATE_KEY = "dragoncare_dragon_registry";



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

    /** РџСЂРѕСЃС‚Р°РІР»СЏРµС‚/РѕР±РЅРѕРІР»СЏРµС‚ Р·Р°РїРёСЃСЊ Рё РїРѕРјРµС‡Р°РµС‚ state В«РіСЂСЏР·РЅС‹РјВ» С‚РѕР»СЊРєРѕ РїСЂРё РёР·РјРµРЅРµРЅРёРё. */
    public void putOrUpdate(EntityDragonBase dragon, long tick) {
        if (dragon == null) return;
        UUID owner = dragon.getOwnerUuid();
        if (owner == null) return; // С‚РѕР»СЊРєРѕ РїСЂРёСЂСѓС‡РµРЅРЅС‹Рµ

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
            // РЎСЂР°РІРЅРёРІР°РµРј РіСЂСѓР±Рѕ, С‡С‚РѕР±С‹ РЅРµ РґС‘СЂРіР°С‚СЊ markDirty РєР°Р¶РґС‹Р№ С‚РёРє СЂР°РґРё РґРµР»СЊС‚С‹ РІ 0.001 Р±Р»РѕРєР°.
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
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (DragonRecord r : records.values()) list.add(r.writeNbt());
        nbt.put("Dragons", list);
        return nbt;
    }

    private static DragonRegistryState fromNbt(NbtCompound nbt) {
        Map<UUID, DragonRecord> map = new HashMap<>();
        NbtList list = nbt.getList("Dragons", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            DragonRecord r = DragonRecord.readNbt(list.getCompound(i));
            map.put(r.dragonId(), r);
        }
        return new DragonRegistryState(map);
    }

    /** РџРѕР»СѓС‡РёС‚СЊ (РёР»Рё СЃРѕР·РґР°С‚СЊ) СЂРµРµСЃС‚СЂ; РІСЃРµРіРґР° Р¶РёРІС‘С‚ РІ overworld. */
    public static DragonRegistryState get(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        PersistentStateManager mgr = overworld.getPersistentStateManager();
        return mgr.getOrCreate(DragonRegistryState::fromNbt, DragonRegistryState::new, STATE_KEY);
    }
}



