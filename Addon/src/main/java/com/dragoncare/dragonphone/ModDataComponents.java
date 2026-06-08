package com.dragoncare.dragonphone;

import com.dragoncare.DragonCare;
import com.mojang.serialization.Codec;
import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Uuids;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Data-компоненты для предметов аддона. Сейчас сюда попадают компоненты
 * «Драконьего телефона»: состояние вкл/выкл и UUID отслеживаемого дракона.
 */
public final class ModDataComponents {

    public static final DeferredRegister<ComponentType<?>> COMPONENTS =
            DeferredRegister.create(RegistryKeys.DATA_COMPONENT_TYPE, DragonCare.MOD_ID);

    /** Включён ли телефон (отображает интерфейс / тикает). */
    public static final DeferredHolder<ComponentType<?>, ComponentType<Boolean>> PHONE_ON = register("phone_on",
            builder -> builder.codec(Codec.BOOL).packetCodec(PacketCodecs.BOOL));

    /** UUID отслеживаемого дракона (выбранного в UI). Может отсутствовать. */
    public static final DeferredHolder<ComponentType<?>, ComponentType<UUID>> PHONE_TRACKED = register("phone_tracked",
            builder -> builder.codec(Uuids.CODEC).packetCodec(Uuids.PACKET_CODEC));

    /** Включен ли датчик пепла. */
    public static final DeferredHolder<ComponentType<?>, ComponentType<Boolean>> SENSOR_ON = register("sensor_on",
            builder -> builder.codec(Codec.BOOL).packetCodec(PacketCodecs.BOOL));

    /** Режим датчика пепла: true = по нажатию, false = постоянный (автоматический). */
    public static final DeferredHolder<ComponentType<?>, ComponentType<Boolean>> SENSOR_MANUAL = register("sensor_manual",
            builder -> builder.codec(Codec.BOOL).packetCodec(PacketCodecs.BOOL));

    /** Текущий уровень пепла, сохраненный в датчике (от 0.0 до 1.0). */
    public static final DeferredHolder<ComponentType<?>, ComponentType<Float>> SENSOR_LEVEL = register("sensor_level",
            builder -> builder.codec(Codec.FLOAT).packetCodec(PacketCodecs.FLOAT));

    private static <T> DeferredHolder<ComponentType<?>, ComponentType<T>> register(String name, UnaryOperator<ComponentType.Builder<T>> op) {
        return COMPONENTS.register(name, () -> op.apply(ComponentType.builder()).build());
    }

    private ModDataComponents() {}
}
