package com.dragoncare.mixin.injection;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;

import java.util.Collection;

/**
 * Finds the structure-world call which adds the generated adult dragon.
 *
 * <p>IaF is remapped to Yarn in the development runtime but uses Mojmap names
 * in a published NeoForge installation. Matching both bytecode spellings here
 * avoids a refmap round-trip while still selecting the exact Entity -> boolean
 * interface invocation. The strict redirect check ensures exactly one call is
 * found in {@code spawnDragon}.</p>
 */
@InjectionPoint.AtCode(value = "SPAWN_ENTITY", namespace = "dragoncare")
public final class BeforeDragonSpawnEntity extends InjectionPoint {
    private static final String YARN_OWNER = "net/minecraft/world/StructureWorldAccess";
    private static final String YARN_DESCRIPTOR = "(Lnet/minecraft/entity/Entity;)Z";
    private static final String MOJMAP_OWNER = "net/minecraft/world/level/WorldGenLevel";
    private static final String MOJMAP_DESCRIPTOR = "(Lnet/minecraft/world/entity/Entity;)Z";

    public BeforeDragonSpawnEntity(InjectionPointData data) {
        super(data);
    }

    @Override
    public boolean find(String methodDescriptor, InsnList instructions,
                        Collection<AbstractInsnNode> nodes) {
        boolean found = false;
        for (AbstractInsnNode instruction : instructions) {
            if (!(instruction instanceof MethodInsnNode invocation)
                    || invocation.getOpcode() != Opcodes.INVOKEINTERFACE) {
                continue;
            }

            boolean yarn = YARN_OWNER.equals(invocation.owner)
                    && "spawnEntity".equals(invocation.name)
                    && YARN_DESCRIPTOR.equals(invocation.desc);
            boolean mojmap = MOJMAP_OWNER.equals(invocation.owner)
                    && "addFreshEntity".equals(invocation.name)
                    && MOJMAP_DESCRIPTOR.equals(invocation.desc);
            if (yarn || mojmap) {
                nodes.add(instruction);
                found = true;
            }
        }
        return found;
    }
}
