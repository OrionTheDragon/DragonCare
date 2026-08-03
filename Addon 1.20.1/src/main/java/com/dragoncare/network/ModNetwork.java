package com.dragoncare.network;

import com.dragoncare.DragonCare;
import com.dragoncare.dragonphone.net.DragonListPayload;
import com.dragoncare.dragonphone.net.PhoneHudPayload;
import com.dragoncare.dragonphone.net.RefreshListPayload;
import com.dragoncare.dragonphone.net.SelectDragonPayload;
import net.minecraft.util.Identifier;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {

    private static final String PROTOCOL_VERSION = "2";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new Identifier(DragonCare.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int id = 0;

    private ModNetwork() {}

    public static void register() {
        INSTANCE.registerMessage(id++, BondSyncPayload.class, BondSyncPayload::encode, BondSyncPayload::new, BondSyncPayload::handle);
        INSTANCE.registerMessage(id++, DragonDirtSyncPayload.class, DragonDirtSyncPayload::encode, DragonDirtSyncPayload::new, DragonDirtSyncPayload::handle);
        INSTANCE.registerMessage(id++, DragonListPayload.class, DragonListPayload::encode, DragonListPayload::new, DragonListPayload::handle);
        INSTANCE.registerMessage(id++, PhoneHudPayload.class, PhoneHudPayload::encode, PhoneHudPayload::new, PhoneHudPayload::handle);
        INSTANCE.registerMessage(id++, RefreshListPayload.class, RefreshListPayload::encode, RefreshListPayload::new, RefreshListPayload::handle);
        INSTANCE.registerMessage(id++, SelectDragonPayload.class, SelectDragonPayload::encode, SelectDragonPayload::new, SelectDragonPayload::handle);
        INSTANCE.registerMessage(id++, DragonBrushCleanPayload.class, DragonBrushCleanPayload::encode, DragonBrushCleanPayload::new, DragonBrushCleanPayload::handle);
        INSTANCE.registerMessage(id++, WoundDebugPayload.class, WoundDebugPayload::encode, WoundDebugPayload::new, WoundDebugPayload::handle);
    }
}


