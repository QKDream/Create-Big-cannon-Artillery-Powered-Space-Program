package com.qkdream.cbcmsmwcompat.ammorack;

import java.lang.reflect.Method;
import net.minecraft.world.entity.Entity;

/**
 * Detects vestalihy content without a hard dependency. Vestalihy is a hand written
 * mod: launch tubes extend TubusEntity and guided missiles extend PturEntity, and
 * both kinds are entities rather than blocks.
 */
public final class VestalihyCompatUtil {

    private static final String LAUNCHER_BASE = "com.vestalihy.entity.TubusEntity";
    private static final String MISSILE_BASE = "com.vestalihy.entity.PturEntity";

    private VestalihyCompatUtil() {
    }

    /** Launch tubes and tripod launchers (they are entities in vestalihy). */
    public static boolean isLauncher(Entity entity) {
        return entity != null && isInstanceOf(entity.getClass(), LAUNCHER_BASE);
    }

    /** Guided missiles in flight (Malyutka, PTUR, TOW). */
    public static boolean isMissile(Entity entity) {
        return entity != null && isInstanceOf(entity.getClass(), MISSILE_BASE);
    }

    /** True when the launcher entity still holds a missile (TubusEntity.isEmpty()). */
    public static boolean hasMissile(Entity launcher) {
        if (launcher == null) {
            return false;
        }
        try {
            Method method = launcher.getClass().getMethod("isEmpty");
            Object result = method.invoke(launcher);
            return !(result instanceof Boolean empty) || !empty;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static boolean isInstanceOf(Class<?> type, String superName) {
        for (Class<?> cls = type; cls != null; cls = cls.getSuperclass()) {
            if (superName.equals(cls.getName())) {
                return true;
            }
        }
        return false;
    }
}