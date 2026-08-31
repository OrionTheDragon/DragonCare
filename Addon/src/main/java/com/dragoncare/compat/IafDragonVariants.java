package com.dragoncare.compat;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.iafenvoy.iceandfire.entity.FireDragonEntity;
import com.iafenvoy.iceandfire.entity.IceDragonEntity;
import com.iafenvoy.iceandfire.entity.LightningDragonEntity;

/** Version-neutral access to the variant ids shared by every supported IaF 2.x build. */
public final class IafDragonVariants {
    private static final String[] FIRE = {"red", "green", "bronze", "gray"};
    private static final String[] ICE = {"blue", "white", "sapphire", "silver"};
    private static final String[] LIGHTNING = {"electric", "amethyst", "copper", "black"};

    private IafDragonVariants() {
    }

    public static String randomVariant(DragonBaseEntity dragon, int index) {
        String[] variants;
        if (dragon instanceof FireDragonEntity) {
            variants = FIRE;
        } else if (dragon instanceof IceDragonEntity) {
            variants = ICE;
        } else if (dragon instanceof LightningDragonEntity) {
            variants = LIGHTNING;
        } else {
            return dragon.getVariant();
        }
        return variants[Math.floorMod(index, variants.length)];
    }
}
