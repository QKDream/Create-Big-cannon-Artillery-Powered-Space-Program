package com.qkdream.cbcmsmwcompat.ammorack;

import java.util.Locale;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Detects mianbaos_modernwarfare (面包学) content without a hard dependency.
 * The mod is an MCreator mod whose classes live under net.mcreator.myfirstmod, so
 * launchers and missiles are recognised by class-name patterns.
 */
public final class MianbaosCompatUtil {

    private static final String BLOCK_ENTITY_PREFIX = "net.mcreator.myfirstmod.block.entity.";
    private static final String ENTITY_PREFIX = "net.mcreator.myfirstmod.entity.";

    private MianbaosCompatUtil() {
    }

    /** Missile and rocket launchers (missile racks are storage, not launchers, and are excluded). */
    public static boolean isLauncher(BlockEntity blockEntity) {
        if (blockEntity == null) {
            return false;
        }
        String name = blockEntity.getClass().getName();
        if (!name.startsWith(BLOCK_ENTITY_PREFIX)) {
            return false;
        }
        String simple = blockEntity.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (simple.contains("rack")) {
            return false;
        }
        return simple.contains("missile") || simple.contains("rocket");
    }

    /**
     * True when the launcher block still holds a missile round. Mianbaos launcher block
     * entities are containers whose inventory holds the loaded missile, so an empty
     * inventory means an empty launcher that must not cook off.
     */
    public static boolean hasMissile(BlockEntity blockEntity) {
        if (blockEntity instanceof Container container) {
            return !container.isEmpty();
        }
        return true;
    }

    /** Missiles and rockets in flight (tanshe = projectile in the mianbaos mod). */
    public static boolean isMissile(Entity entity) {
        return isWeaponEntity(entity, false);
    }

    /** Turret-style living launchers (missile/rocket platforms), which always carry missiles. */
    public static boolean isLauncherMob(Entity entity) {
        return isWeaponEntity(entity, true);
    }

    private static boolean isWeaponEntity(Entity entity, boolean living) {
        if (entity == null) {
            return false;
        }
        if (living != (entity instanceof LivingEntity)) {
            return false;
        }
        String name = entity.getClass().getName();
        if (!name.startsWith(ENTITY_PREFIX)) {
            return false;
        }
        String simple = entity.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return simple.contains("missile") || simple.contains("rocket") || simple.contains("agm");
    }
}