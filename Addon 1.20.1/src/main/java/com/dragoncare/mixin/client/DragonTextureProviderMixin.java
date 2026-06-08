package com.dragoncare.mixin.client;

import com.dragoncare.client.ClientDirtCache;
import com.dragoncare.client.DirtTextureBlender;
import com.iafenvoy.iceandfire.entity.EntityDragonBase;
import com.iafenvoy.iceandfire.data.DragonColor;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DragonColor.class)
public class DragonTextureProviderMixin {

    @Inject(method = "getTextureByEntity", at = @At("RETURN"), cancellable = true, remap = false)
    private void dragoncare$applyDirtTexture(EntityDragonBase dragon, CallbackInfoReturnable<Identifier> cir) {
        if (dragon.isModelDead() && dragon.getDeathStage() >= dragon.getAgeInDays() / 10) {
            return; // Don't apply dirt to skeletons
        }

        if (dragon instanceof com.iafenvoy.iceandfire.entity.EntityFireDragon) {
            if (net.minecraftforge.fml.ModList.get().isLoaded("morecolordragon")) {
                return; // morecolordragon applies dirt to Fire Dragons in a single pass to prevent dynamic texture conflicts
            }
        }

        int dirtLevel = ClientDirtCache.get(dragon.getUuid());
        if (dirtLevel > 0) {
            Identifier original = cir.getReturnValue();
            Identifier blended = DirtTextureBlender.getOrCreateBlendedTexture(original, dirtLevel);
            cir.setReturnValue(blended);
        }
    }
}




