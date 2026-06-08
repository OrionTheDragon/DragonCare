package com.dragoncare.dragonphone;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Запись об одном прирученном драконе для «Драконьего телефона».
 * Хранит UUID, владельца, координаты + измерение, кастомное имя (если переименован),
 * стадию роста и временную метку последнего обновления (в игровых тиках, для отладки).
 *
 * <p>Запись создаётся/обновляется при тике сущности и при выгрузке чанка, чтобы
 * в телефоне отображались даже драконы из выгруженных регионов мира.</p>
 */
public final class DragonRecord {

    private final UUID dragonId;
    private final UUID ownerId;
    private Identifier dimension;
    private double x;
    private double y;
    private double z;
    /** Имя из бирки (Custom Name), {@code null} если дракон не переименован. */
    @Nullable
    private String customName;
    private int stage;
    private long lastSeenTick;
    /** Translation key типа сущности (например {@code entity.iceandfire.fire_dragon}). Может быть {@code null}/пустым для legacy-записей. */
    @Nullable
    private String species;

    public DragonRecord(UUID dragonId, UUID ownerId, Identifier dimension,
                        double x, double y, double z, @Nullable String customName,
                        int stage, long lastSeenTick, @Nullable String species) {
        this.dragonId = dragonId;
        this.ownerId = ownerId;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.customName = customName;
        this.stage = stage;
        this.lastSeenTick = lastSeenTick;
        this.species = species;
    }

    public UUID dragonId() { return dragonId; }
    public UUID ownerId() { return ownerId; }
    public Identifier dimension() { return dimension; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public Vec3d pos() { return new Vec3d(x, y, z); }
    @Nullable public String customName() { return customName; }
    public int stage() { return stage; }
    public long lastSeenTick() { return lastSeenTick; }
    @Nullable public String species() { return species; }

    public void update(Identifier dim, double nx, double ny, double nz,
                       @Nullable String name, int newStage, long tick, @Nullable String species) {
        this.dimension = dim;
        this.x = nx;
        this.y = ny;
        this.z = nz;
        this.customName = name;
        this.stage = newStage;
        this.lastSeenTick = tick;
        if (species != null && !species.isEmpty()) this.species = species;
    }

    public NbtCompound writeNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("DragonId", dragonId);
        nbt.putUuid("OwnerId", ownerId);
        nbt.putString("Dim", dimension.toString());
        nbt.putDouble("X", x);
        nbt.putDouble("Y", y);
        nbt.putDouble("Z", z);
        if (customName != null) nbt.putString("Name", customName);
        nbt.putInt("Stage", stage);
        nbt.putLong("Tick", lastSeenTick);
        if (species != null && !species.isEmpty()) nbt.putString("Species", species);
        return nbt;
    }

    public static DragonRecord readNbt(NbtCompound nbt) {
        UUID id = nbt.getUuid("DragonId");
        UUID owner = nbt.getUuid("OwnerId");
        Identifier dim = Identifier.tryParse(nbt.getString("Dim"));
        if (dim == null) dim = Identifier.of("minecraft", "overworld");
        String name = nbt.contains("Name") ? nbt.getString("Name") : null;
        String species = nbt.contains("Species") ? nbt.getString("Species") : null;
        return new DragonRecord(id, owner, dim,
                nbt.getDouble("X"), nbt.getDouble("Y"), nbt.getDouble("Z"),
                name, nbt.getInt("Stage"), nbt.getLong("Tick"), species);
    }
}


