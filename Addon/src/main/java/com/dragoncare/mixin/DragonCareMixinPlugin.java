package com.dragoncare.mixin;

import com.dragoncare.DragonCare;
import com.dragoncare.mixin.injection.BeforeDragonSpawnEntity;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Selects the renderer/animation integration from the actual IaF target bytecode. */
public final class DragonCareMixinPlugin implements IMixinConfigPlugin {
    private static final String DRAGON_CLASS = "com.iafenvoy.iceandfire.entity.DragonBaseEntity";
    private static final AtomicBoolean INJECTION_POINT_REGISTERED = new AtomicBoolean();
    private static volatile ApiFamily detectedApiFamily;

    @Override
    public void onLoad(String mixinPackage) {
        if (INJECTION_POINT_REGISTERED.compareAndSet(false, true)) {
            InjectionPoint.register(BeforeDragonSpawnEntity.class, "dragoncare");
        }
        getApiFamily();
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        ApiFamily detected = getApiFamily();
        if (mixinClassName.contains(".compat.gecko.")) {
            return detected == ApiFamily.GECKO;
        }
        if (mixinClassName.contains(".compat.tabula.")) {
            return detected == ApiFamily.TABULA;
        }
        return true;
    }

    private static ApiFamily detectApiFamily() {
        try {
            ClassNode dragon = MixinService.getService().getBytecodeProvider().getClassNode(DRAGON_CLASS);
            for (MethodNode method : dragon.methods) {
                if ("registerControllers".equals(method.name)
                        && method.desc.contains("software/bernie/geckolib")) {
                    return ApiFamily.GECKO;
                }
            }
            return ApiFamily.TABULA;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot inspect Ice and Fire CE DragonBaseEntity bytecode", exception);
        }
    }

    private static ApiFamily getApiFamily() {
        ApiFamily detected = detectedApiFamily;
        if (detected != null) {
            return detected;
        }
        synchronized (DragonCareMixinPlugin.class) {
            detected = detectedApiFamily;
            if (detected == null) {
                detected = detectApiFamily();
                detectedApiFamily = detected;
                DragonCare.LOGGER.info("Detected Ice and Fire CE {} API family", detected);
            }
            return detected;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                         IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                          IMixinInfo mixinInfo) {
    }

    private enum ApiFamily {
        TABULA,
        GECKO
    }
}
