package com.qkdream.cbcmsmwcompat.ammorack;

import com.qkdream.cbcmsmwcompat.CBCMSMWCompat;
import com.qkdream.cbcmsmwcompat.config.CompatConfig;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Reflection bridge to taov_weapons. Tau and Hellfire are not Minecraft entities:
 * they are launched through Shaolib's {@code ProjectileType} system, and the
 * in-flight projectiles stay attached to their rack block entities as
 * {@code ProjectileHandle}s. Everything here goes through reflection so this
 * mod compiles and runs without taov_weapons/shaolib on the classpath.
 *
 * <p>In-flight missiles are looked up through Shaolib's global
 * {@code ShaolibProjectiles.activeProjectiles()} registry rather than by
 * enumerating rack block entities: a missile can fly far away from its rack,
 * whose chunk (and block entity) may no longer be loaded.</p>
 */
public final class TaovCompat {

    private static final String TAU_RACK_NAME = "com.verr1.taov.weapons.content.tau.TauRackBlockEntity";
    private static final String HELLFIRE_RACK_NAME = "com.verr1.taov.weapons.content.hellfire.HellFireRackBlockEntity";
    private static final String GUIDED_MISSILE_STATE_NAME = "com.verr1.shaolib.munitions.projectile.guided.GuidedMissileState";
    private static final String TAU_PROJECTILES_NAME = "com.verr1.taov.weapons.munitions.TaovMunitionsProjectiles";
    private static final String SHAOLIB_PROJECTILES_NAME = "com.verr1.shaolib.api.projectile.ShaolibProjectiles";
    private static final String PROJECTILE_HANDLE_NAME = "com.verr1.shaolib.api.projectile.ProjectileHandle";
    private static final String PROJECTILE_INSTANCE_NAME = "com.verr1.shaolib.api.projectile.ProjectileInstance";
    private static final String PROJECTILE_TYPE_NAME = "com.verr1.shaolib.api.projectile.ProjectileType";
    private static final String PROJECTILE_SERVER_STATE_NAME = "com.verr1.shaolib.api.projectile.ProjectileServerState";
    private static final String PROJECTILE_SERVER_CONTEXT_NAME = "com.verr1.shaolib.api.projectile.ProjectileServerContext";
    private static final String PROJECTILE_SERVER_CONTEXTS_NAME = "com.verr1.shaolib.api.projectile.ProjectileServerContexts";
    private static final String GUIDED_MISSILE_BEHAVIOR_NAME = "com.verr1.shaolib.munitions.projectile.guided.GuidedMissileBehavior";
    private static final String GUIDED_MISSILE_PROPERTIES_NAME = "com.verr1.shaolib.munitions.config.properties.GuidedMissileMunitionProperties";
    private static final String CBC_MUNITION_EFFECT_PIPELINE_NAME = "com.verr1.shaolib.munitions.projectile.effects.CbcMunitionEffectPipeline";
    private static final String EFFECT_PROPERTIES_NAME = "com.verr1.shaolib.munitions.config.properties.MunitionPropertyComponents$EffectProperties";
    private static final String CBC_LIKE_MUNITION_PROPERTIES_NAME = "com.verr1.shaolib.munitions.config.properties.CbcLikeMunitionProperties";

    private static boolean resolved = false;
    private static boolean loaded = false;

    private static Class<?> tauRackClass;
    private static Class<?> hellfireRackClass;
    private static Class<?> guidedMissileStateClass;

    private static Field tauActiveProjectileField;
    private static Field hellfireActiveProjectilesField;
    private static Method tauRemainingAmmoMethod;
    private static Method hellfireRemainingAmmoMethod;
    private static Method handleAliveMethod;
    private static Method handleProjectileMethod;
    private static Method handleStateMethod;
    private static Method handleRequestDataSyncMethod;
    private static Method handleLevelMethod;
    private static Method handleTypeMethod;
    private static Method guidedRequestDetonateMethod;
    private static Method stateDetonateIfArmedMethod;
    private static Method instanceAliveMethod;
    private static Method instancePositionMethod;
    private static Method instancePreviousPositionMethod;
    private static Method instanceTypeMethod;
    private static Method instanceDimensionMethod;
    private static Method instanceIdMethod;
    private static Method instanceAgeMethod;
    private static Method typeIdMethod;
    private static Method typeBehaviorMethod;
    private static Method shaolibActiveProjectilesMethod;
    private static Method shaolibGetHandleMethod;
    private static Method contextsCreateMethod;
    private static Method behaviorPropertiesMethod;
    private static Method behaviorDetonateMethod;
    private static Method propertiesCooldownMethod;
    private static Method effectPipelineDetonateShellMethod;
    private static Method propertiesEffectsMethod;
    private static Method handleDiscardMethod;
    private static Class<?> guidedMissilePropertiesClass;
    private static Class<?> projectileServerContextClass;
    private static Class<?> effectPropertiesClass;
    private static Class<?> cbcLikeMunitionPropertiesClass;
    private static Object tauMissileType;
    private static Object hellfireMissileType;
    private static long lastDiagnosticTick = Long.MIN_VALUE;
    private static final Map<Long, Long> DETONATED_IDS = new HashMap<>();

    private TaovCompat() {
    }

    /** True when taov_weapons (and therefore shaolib) is present at runtime. */
    public static boolean isLoaded() {
        resolve();
        return loaded;
    }

    /** True when the block entity is a Tau or Hellfire rack. */
    public static boolean isRackBlockEntity(BlockEntity be) {
        if (be == null) {
            return false;
        }
        resolve();
        if (!loaded) {
            return false;
        }
        Class<?> cls = be.getClass();
        return (tauRackClass != null && tauRackClass.isAssignableFrom(cls))
                || (hellfireRackClass != null && hellfireRackClass.isAssignableFrom(cls));
    }

    /** Returns the rack's remaining ammunition count, or 0 when unreadable/empty. */
    public static int remainingAmmo(BlockEntity be) {
        if (be == null) {
            return 0;
        }
        resolve();
        if (!loaded) {
            return 0;
        }
        Method method = null;
        Class<?> cls = be.getClass();
        if (tauRackClass != null && tauRackClass.isAssignableFrom(cls)) {
            method = tauRemainingAmmoMethod;
        } else if (hellfireRackClass != null && hellfireRackClass.isAssignableFrom(cls)) {
            method = hellfireRemainingAmmoMethod;
        }
        if (method == null) {
            return 0;
        }
        try {
            Object result = method.invoke(be);
            return result instanceof Number number ? number.intValue() : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Detonates every in-flight Tau/Hellfire missile whose current position lies
     * inside the given area. The missiles are found through Shaolib's global
     * projectile registry, so they are caught even when their rack's chunk is no
     * longer loaded. Falls back to enumerating loaded rack block entities when
     * the global registry is unavailable.
     */
    public static void detonateAirMunitionsIn(ServerLevel level, AABB area) {
        resolve();
        if (!loaded) {
            return;
        }
        if (shaolibActiveProjectilesMethod != null && instancePositionMethod != null) {
            sweepGlobalProjectiles(level, area);
        } else {
            detonateRackHandlesIn(level, area);
        }
    }

    /** Logs at most once per second to keep the game log readable during sweeps. */
    private static boolean diagnosticTick() {
        long tick = System.nanoTime() / 1_000_000_000L;
        if (tick != lastDiagnosticTick) {
            lastDiagnosticTick = tick;
            return true;
        }
        return false;
    }

    private static String typeIdOf(Object instance) {
        if (instanceTypeMethod == null || typeIdMethod == null) {
            return "?";
        }
        try {
            Object type = instanceTypeMethod.invoke(instance);
            if (type == null) {
                return "null";
            }
            Object id = typeIdMethod.invoke(type);
            return id == null ? "null" : id.toString();
        } catch (Throwable t) {
            return "error";
        }
    }

    /** Sweeps Shaolib's global projectile registry for Tau/Hellfire missiles in the area. */
    private static void sweepGlobalProjectiles(ServerLevel level, AABB area) {
        try {
            Object result = shaolibActiveProjectilesMethod.invoke(null);
            if (!(result instanceof Collection<?> instances)) {
                return;
            }
            String dimensionId = level.dimension().location().toString();
            int total = 0;
            int matched = 0;
            for (Object instance : instances) {
                if (instance == null || !instanceAlive(instance)) {
                    continue;
                }
                if (!isTauOrHellfire(instance)) {
                    continue;
                }
                total++;
                if (!inArea(instance, area)) {
                    logMiss(instance, "outside area", area);
                    continue;
                }
                if (!inDimension(instance, dimensionId)) {
                    logMiss(instance, "wrong dimension (want " + dimensionId + ")", area);
                    continue;
                }
                matched++;
                CBCMSMWCompat.LOGGER.info("[cbcmsmwcompat] taov sweep: detonating in-flight {} at {} in {}",
                        typeIdOf(instance), positionOf(instance), dimensionId);
                detonateInstance(instance);
            }
            if (matched == 0 && diagnosticTick() && (total > 0 || !instances.isEmpty())) {
                CBCMSMWCompat.LOGGER.info(
                        "[cbcmsmwcompat] taov sweep: {} active shaolib projectile(s), {} tau/hellfire, none in area {}",
                        instances.size(), total, area);
            }
        } catch (Throwable t) {
            CBCMSMWCompat.LOGGER.info("[cbcmsmwcompat] taov air munition sweep failed", t);
        }
    }

    private static void logMiss(Object instance, String reason, AABB area) {
        if (!diagnosticTick()) {
            return;
        }
        CBCMSMWCompat.LOGGER.info("[cbcmsmwcompat] taov sweep: skipped in-flight {} at {}: {} (area {})",
                typeIdOf(instance), positionOf(instance), reason, area);
    }

    private static Vec3 positionOf(Object instance) {
        if (instancePositionMethod == null) {
            return Vec3.ZERO;
        }
        try {
            Object pos = instancePositionMethod.invoke(instance);
            return pos instanceof Vec3 vec3 ? vec3 : Vec3.ZERO;
        } catch (Throwable t) {
            return Vec3.ZERO;
        }
    }

    /** Fallback: enumerates rack block entities in the loaded chunks covered by the area. */
    private static void detonateRackHandlesIn(ServerLevel level, AABB area) {
        int minChunkX = (int) Math.floor(area.minX) >> 4;
        int maxChunkX = (int) Math.floor(area.maxX) >> 4;
        int minChunkZ = (int) Math.floor(area.minZ) >> 4;
        int maxChunkZ = (int) Math.floor(area.maxZ) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be == null || be.isRemoved()) {
                        continue;
                    }
                    detonateHandlesOf(be, area);
                }
            }
        }
    }

    private static void detonateHandlesOf(BlockEntity be, AABB area) {
        Class<?> cls = be.getClass();
        if (tauRackClass != null && tauRackClass.isAssignableFrom(cls)) {
            Object handle = readField(tauActiveProjectileField, be);
            if (handle != null && handleAlive(handle) && area.contains(handlePosition(handle))) {
                detonateHandle(handle);
            }
        } else if (hellfireRackClass != null && hellfireRackClass.isAssignableFrom(cls)) {
            Object list = readField(hellfireActiveProjectilesField, be);
            if (list instanceof List<?> handles) {
                for (Object handle : handles) {
                    if (handle != null && handleAlive(handle) && area.contains(handlePosition(handle))) {
                        detonateHandle(handle);
                    }
                }
            }
        }
    }

    private static boolean instanceAlive(Object instance) {
        if (instanceAliveMethod == null) {
            return false;
        }
        try {
            Object result = instanceAliveMethod.invoke(instance);
            return result instanceof Boolean alive && alive;
        } catch (Throwable t) {
            return false;
        }
    }

    /** True when the projectile instance is a Tau or Hellfire missile type. */
    private static boolean isTauOrHellfire(Object instance) {
        if (instanceTypeMethod == null) {
            return false;
        }
        try {
            Object type = instanceTypeMethod.invoke(instance);
            if (type == null) {
                return false;
            }
            if ((tauMissileType != null && tauMissileType == type)
                    || (hellfireMissileType != null && hellfireMissileType == type)) {
                return true;
            }
            // Fallback when the registry fields are unavailable: match the type id.
            if (typeIdMethod != null) {
                Object id = typeIdMethod.invoke(type);
                if (id instanceof ResourceLocation rl) {
                    String path = rl.getPath();
                    return path.contains("tau_missile")
                            || path.contains("hellfire_missile")
                            || path.contains("hell_fire_missile");
                }
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean inArea(Object instance, AABB area) {
        if (instancePositionMethod == null) {
            return false;
        }
        try {
            Object pos = instancePositionMethod.invoke(instance);
            Vec3 current = pos instanceof Vec3 vec3 ? vec3 : null;
            if (current != null && area.contains(current)) {
                return true;
            }
            if (instancePreviousPositionMethod != null) {
                Object previous = instancePreviousPositionMethod.invoke(instance);
                if (previous instanceof Vec3 vec3) {
                    if (area.contains(vec3)) {
                        return true;
                    }
                    // Fast missiles can jump across the box between ticks; treat the
                    // swept segment as inside when it crosses the area.
                    if (current != null && segmentIntersectsAabb(current, vec3, area)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Slab test: true when the segment from {@code a} to {@code b} intersects the box. */
    private static boolean segmentIntersectsAabb(Vec3 a, Vec3 b, AABB box) {
        double tMin = 0.0;
        double tMax = 1.0;
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double dz = b.z - a.z;
        double[] origins = {a.x, a.y, a.z};
        double[] deltas = {dx, dy, dz};
        double[] mins = {box.minX, box.minY, box.minZ};
        double[] maxs = {box.maxX, box.maxY, box.maxZ};
        for (int axis = 0; axis < 3; axis++) {
            double d = deltas[axis];
            double o = origins[axis];
            double lo = mins[axis];
            double hi = maxs[axis];
            if (Math.abs(d) < 1.0E-7) {
                if (o < lo || o > hi) {
                    return false;
                }
            } else {
                double t1 = (lo - o) / d;
                double t2 = (hi - o) / d;
                if (t1 > t2) {
                    double tmp = t1;
                    t1 = t2;
                    t2 = tmp;
                }
                tMin = Math.max(tMin, t1);
                tMax = Math.min(tMax, t2);
                if (tMin > tMax) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean inDimension(Object instance, String dimensionId) {
        if (instanceDimensionMethod == null) {
            return true;
        }
        try {
            Object dim = instanceDimensionMethod.invoke(instance);
            return dimensionId.equals(dim);
        } catch (Throwable t) {
            return true;
        }
    }

    /** Detonates a projectile instance by looking up its live handle. */
    private static void detonateInstance(Object instance) {
        if (instanceIdMethod == null || shaolibGetHandleMethod == null) {
            CBCMSMWCompat.LOGGER.info("[cbcmsmwcompat] taov sweep: cannot detonate, id method {} / handle lookup method {}",
                    instanceIdMethod == null ? "missing" : "ok", shaolibGetHandleMethod == null ? "missing" : "ok");
            return;
        }
        try {
            Object id = instanceIdMethod.invoke(instance);
            if (!(id instanceof Number number)) {
                CBCMSMWCompat.LOGGER.info("[cbcmsmwcompat] taov sweep: projectile id is not a number: {}", id);
                return;
            }
            Object handleOptional = shaolibGetHandleMethod.invoke(null, number.longValue());
            if (handleOptional instanceof Optional<?> optional && optional.isPresent()) {
                CBCMSMWCompat.LOGGER.info("[cbcmsmwcompat] taov sweep: live handle found for id {}", number.longValue());
                detonateHandle(optional.get());
            } else {
                CBCMSMWCompat.LOGGER.info("[cbcmsmwcompat] taov sweep: no live handle for id {} (lookup result {})",
                        number.longValue(), handleOptional == null ? "null" : handleOptional.getClass().getSimpleName());
            }
        } catch (Throwable t) {
            CBCMSMWCompat.LOGGER.info("[cbcmsmwcompat] taov missile handle lookup failed", t);
        }
    }

    private static boolean handleAlive(Object handle) {
        if (handleAliveMethod == null) {
            return false;
        }
        try {
            Object result = handleAliveMethod.invoke(handle);
            return result instanceof Boolean alive && alive;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Vec3 handlePosition(Object handle) {
        if (handleProjectileMethod == null || instancePositionMethod == null) {
            return Vec3.ZERO;
        }
        try {
            Object instance = handleProjectileMethod.invoke(handle);
            if (instance == null) {
                return Vec3.ZERO;
            }
            Object result = instancePositionMethod.invoke(instance);
            return result instanceof Vec3 pos ? pos : Vec3.ZERO;
        } catch (Throwable t) {
            return Vec3.ZERO;
        }
    }

    /**
     * Detonates a live Shaolib handle. The primary path invokes the guided missile
     * behavior's own {@code detonate} method directly, which explodes and discards
     * armed missiles immediately. Because taov rejects detonations inside the guided
     * explosion cooldown window, a payload bypass then fires the missile's CBC
     * effects directly and discards it, so even freshly launched missiles cook off.
     * The {@code requestDetonateIfArmed} flag request is kept as a last-resort
     * fallback for states neither path can handle.
     */
    private static void detonateHandle(Object handle) {
        try {
            Object state = handleStateMethod == null ? null : handleStateMethod.invoke(handle);
            Object instance = handleProjectileMethod == null ? null : handleProjectileMethod.invoke(handle);
            Object level = handleLevelMethod == null ? null : handleLevelMethod.invoke(handle);
            Object type = handleTypeMethod == null ? null : handleTypeMethod.invoke(handle);
            long id = -1L;
            if (instance != null && instanceIdMethod != null) {
                Object idResult = instanceIdMethod.invoke(instance);
                if (idResult instanceof Number number) {
                    id = number.longValue();
                }
            }

            long gameTime = level instanceof ServerLevel serverLevel ? serverLevel.getGameTime() : 0L;
            Long detonatedAt = DETONATED_IDS.get(id);
            if (detonatedAt != null && gameTime - detonatedAt <= 1L) {
                // The explosion events fired by a detonation re-enter the sweep while the
                // instance is still registered; never detonate the same id twice per tick.
                CBCMSMWCompat.LOGGER.info("[cbcmsmwcompat] taov sweep: id {} already detonated this tick, skipping", id);
                return;
            }

            Integer age = readInt(instance, instanceAgeMethod);
            Boolean flag = readBoolean(state, stateDetonateIfArmedMethod);
            Integer cooldown = null;
            Object behavior = type == null || typeBehaviorMethod == null ? null : typeBehaviorMethod.invoke(type);
            Object context = null;
            if (contextsCreateMethod != null && level != null && instance != null && state != null) {
                context = contextsCreateMethod.invoke(null, level, instance, state);
            }
            Object properties = null;
            if (behaviorPropertiesMethod != null && behavior != null && context != null) {
                properties = behaviorPropertiesMethod.invoke(behavior, context);
                if (guidedMissilePropertiesClass != null
                        && guidedMissilePropertiesClass.isInstance(properties)
                        && propertiesCooldownMethod != null) {
                    Object cooldownResult = propertiesCooldownMethod.invoke(properties);
                    if (cooldownResult instanceof Number number) {
                        cooldown = number.intValue();
                    }
                }
            }
            CBCMSMWCompat.LOGGER.info(
                    "[cbcmsmwcompat] taov detonate: id={} age={} cooldown={} armed={} detonateIfArmed={}",
                    id, age, cooldown, age != null && cooldown != null && age > cooldown, flag);

            boolean detonated = false;
            if (behaviorDetonateMethod != null && behavior != null && context != null && instance != null) {
                DETONATED_IDS.put(id, gameTime);
                Object result = behaviorDetonateMethod.invoke(behavior, context, positionOf(instance));
                detonated = result instanceof Boolean value && value;
                CBCMSMWCompat.LOGGER.info("[cbcmsmwcompat] taov sweep: direct detonate invoked for id {}, result {}",
                        id, result);
            }
            if (!detonated) {
                detonated = detonatePayloadDirectly(handle, instance, context, properties, id, gameTime);
            }
            if (!detonated && state != null && guidedMissileStateClass != null
                    && guidedMissileStateClass.isInstance(state)
                    && guidedRequestDetonateMethod != null) {
                CBCMSMWCompat.LOGGER.info("[cbcmsmwcompat] taov sweep: requesting armed detonation as fallback for id {}",
                        id);
                guidedRequestDetonateMethod.invoke(state);
            }
            if (handleRequestDataSyncMethod != null) {
                handleRequestDataSyncMethod.invoke(handle);
            }
            if (DETONATED_IDS.size() > 64) {
                DETONATED_IDS.entrySet().removeIf(entry -> gameTime - entry.getValue() > 5L);
            }
        } catch (Throwable t) {
            CBCMSMWCompat.LOGGER.info("[cbcmsmwcompat] Shaolib handle detonation failed", t);
        }
    }

    /**
     * Bypasses taov's guided explosion cooldown by firing the missile's CBC payload
     * directly and discarding the projectile, mirroring exactly what taov's own
     * detonatePayload does once the cooldown has elapsed. Returns true when the
     * payload was fired.
     */
    private static boolean detonatePayloadDirectly(Object handle, Object instance, Object context,
            Object properties, long id, long gameTime) {
        try {
            if (effectPipelineDetonateShellMethod == null || context == null || properties == null
                    || cbcLikeMunitionPropertiesClass == null
                    || !cbcLikeMunitionPropertiesClass.isInstance(properties)
                    || propertiesEffectsMethod == null) {
                return false;
            }
            Object effects = propertiesEffectsMethod.invoke(properties);
            if (effectPropertiesClass == null || !effectPropertiesClass.isInstance(effects)) {
                CBCMSMWCompat.LOGGER.info(
                        "[cbcmsmwcompat] taov sweep: id {} has no CBC effect properties, no payload bypass", id);
                return false;
            }
            Vec3 position = positionOf(instance);
            DETONATED_IDS.put(id, gameTime);
            effectPipelineDetonateShellMethod.invoke(null, context, effects, position);
            CBCMSMWCompat.LOGGER.info(
                    "[cbcmsmwcompat] taov sweep: payload detonateShell invoked for id {} at {}", id, position);
            if (handleDiscardMethod != null && handle != null) {
                handleDiscardMethod.invoke(handle, "cbcmsmwcompat:cook_off");
                CBCMSMWCompat.LOGGER.info("[cbcmsmwcompat] taov sweep: handle discarded for id {}", id);
            }
            return true;
        } catch (Throwable t) {
            CBCMSMWCompat.LOGGER.info("[cbcmsmwcompat] taov sweep: direct payload detonation failed for id {}", id, t);
            return false;
        }
    }

    private static Integer readInt(Object target, Method method) {
        if (target == null || method == null) {
            return null;
        }
        try {
            Object result = method.invoke(target);
            return result instanceof Number number ? number.intValue() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Boolean readBoolean(Object target, Method method) {
        if (target == null || method == null) {
            return null;
        }
        try {
            Object result = method.invoke(target);
            return result instanceof Boolean value ? value : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object readField(Field field, Object target) {
        if (field == null) {
            return null;
        }
        try {
            return field.get(target);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            tauRackClass = Class.forName(TAU_RACK_NAME);
            hellfireRackClass = Class.forName(HELLFIRE_RACK_NAME);
            guidedMissileStateClass = Class.forName(GUIDED_MISSILE_STATE_NAME);

            tauActiveProjectileField = fieldOf(tauRackClass, "activeProjectile");
            hellfireActiveProjectilesField = fieldOf(hellfireRackClass, "activeProjectiles");
            tauRemainingAmmoMethod = methodOf(tauRackClass, "remainingAmmo");
            hellfireRemainingAmmoMethod = methodOf(hellfireRackClass, "remainingAmmo");

            Class<?> handleClass = Class.forName(PROJECTILE_HANDLE_NAME);
            handleAliveMethod = methodOf(handleClass, "alive");
            handleProjectileMethod = methodOf(handleClass, "projectile");
            handleStateMethod = methodOf(handleClass, "state");
            handleRequestDataSyncMethod = methodOf(handleClass, "requestDataSync");
            handleLevelMethod = methodOf(handleClass, "level");
            handleTypeMethod = methodOf(handleClass, "type");

            Class<?> instanceClass = Class.forName(PROJECTILE_INSTANCE_NAME);
            instanceAliveMethod = methodOf(instanceClass, "isAlive");
            instancePositionMethod = methodOf(instanceClass, "position");
            instancePreviousPositionMethod = methodOf(instanceClass, "previousPosition");
            instanceTypeMethod = methodOf(instanceClass, "type");
            instanceDimensionMethod = methodOf(instanceClass, "dimensionId");
            instanceIdMethod = methodOf(instanceClass, "id");
            instanceAgeMethod = methodOf(instanceClass, "ageTicks");

            Class<?> typeClass = Class.forName(PROJECTILE_TYPE_NAME);
            typeIdMethod = methodOf(typeClass, "id");
            typeBehaviorMethod = methodOf(typeClass, "behavior");

            Class<?> shaolibProjectiles = Class.forName(SHAOLIB_PROJECTILES_NAME);
            shaolibActiveProjectilesMethod = methodOf(shaolibProjectiles, "activeProjectiles");
            shaolibGetHandleMethod = methodOf(shaolibProjectiles, "getHandle", long.class);

            try {
                Class<?> taovProjectiles = Class.forName(TAU_PROJECTILES_NAME);
                tauMissileType = taovProjectiles.getField("TAU_MISSILE").get(null);
                hellfireMissileType = taovProjectiles.getField("HELLFIRE_MISSILE").get(null);
            } catch (Throwable t) {
                tauMissileType = null;
                hellfireMissileType = null;
            }

            if (guidedMissileStateClass != null) {
                guidedRequestDetonateMethod = methodOf(guidedMissileStateClass, "requestDetonateIfArmed");
                stateDetonateIfArmedMethod = methodOf(guidedMissileStateClass, "detonateIfArmed");
            }

            // Direct detonation path: build a ProjectileServerContext and invoke the
            // GuidedMissileBehavior's protected detonate method, exactly as its own
            // applyManualDetonation does when the detonate flag is consumed.
            projectileServerContextClass = Class.forName(PROJECTILE_SERVER_CONTEXT_NAME);
            Class<?> serverStateClass = Class.forName(PROJECTILE_SERVER_STATE_NAME);
            Class<?> contextsClass = Class.forName(PROJECTILE_SERVER_CONTEXTS_NAME);
            contextsCreateMethod = declaredMethodOf(contextsClass, "create",
                    ServerLevel.class, instanceClass, serverStateClass);

            Class<?> guidedBehaviorClass = Class.forName(GUIDED_MISSILE_BEHAVIOR_NAME);
            behaviorPropertiesMethod = declaredMethodInHierarchy(guidedBehaviorClass, "properties",
                    projectileServerContextClass);
            behaviorDetonateMethod = declaredMethodInHierarchy(guidedBehaviorClass, "detonate",
                    projectileServerContextClass, Vec3.class);

            guidedMissilePropertiesClass = Class.forName(GUIDED_MISSILE_PROPERTIES_NAME);
            propertiesCooldownMethod = methodOf(guidedMissilePropertiesClass, "guidedExplosionCooldownTicks");

            // Payload bypass: taov's GuidedMissileBehavior refuses to detonate while the
            // guided explosion cooldown has not elapsed (ageTicks <= cooldown). Its own
            // detonatePayload is nothing but a cooldown gate around exactly this call, so
            // invoking it here detonates freshly launched missiles too.
            Class<?> effectPipelineClass = Class.forName(CBC_MUNITION_EFFECT_PIPELINE_NAME);
            effectPropertiesClass = Class.forName(EFFECT_PROPERTIES_NAME);
            effectPipelineDetonateShellMethod = methodOf(effectPipelineClass, "detonateShell",
                    projectileServerContextClass, effectPropertiesClass, Vec3.class);
            cbcLikeMunitionPropertiesClass = Class.forName(CBC_LIKE_MUNITION_PROPERTIES_NAME);
            propertiesEffectsMethod = methodOf(cbcLikeMunitionPropertiesClass, "effects");
            handleDiscardMethod = methodOf(handleClass, "discard", String.class);

            loaded = true;
        } catch (Throwable t) {
            loaded = false;
            tauRackClass = null;
            hellfireRackClass = null;
            CBCMSMWCompat.LOGGER.info("[cbcmsmwcompat] taov compat failed to resolve", t);
        }
        if (CompatConfig.DEBUG_LOGGING.get()) {
            CBCMSMWCompat.LOGGER.info(
                    "[cbcmsmwcompat] taov compat resolved: loaded={} racks={}/{} registry={} types={}/{} "
                    + "requestDetonate={} context={} behaviorProperties={} behaviorDetonate={}",
                    loaded,
                    tauRackClass != null, hellfireRackClass != null,
                    shaolibActiveProjectilesMethod != null,
                    tauMissileType != null, hellfireMissileType != null,
                    guidedRequestDetonateMethod != null,
                    contextsCreateMethod != null,
                    behaviorPropertiesMethod != null,
                    behaviorDetonateMethod != null);
            CBCMSMWCompat.LOGGER.info(
                    "[cbcmsmwcompat] taov compat payload bypass: effectPipeline={} effectProperties={} "
                            + "cbcLikeProperties={} propertiesEffects={} handleDiscard={}",
                    effectPipelineDetonateShellMethod != null,
                    effectPropertiesClass != null,
                    cbcLikeMunitionPropertiesClass != null,
                    propertiesEffectsMethod != null,
                    handleDiscardMethod != null);
        }
    }

    private static Field fieldOf(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Throwable t) {
            CBCMSMWCompat.LOGGER.info("[cbcmsmwcompat] taov compat: field {}.{} unavailable: {}",
                    owner.getName(), name, t.toString());
            return null;
        }
    }

    private static Method methodOf(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            return owner.getMethod(name, parameterTypes);
        } catch (Throwable t) {
            CBCMSMWCompat.LOGGER.info("[cbcmsmwcompat] taov compat: method {}.{} unavailable: {}",
                    owner.getName(), name, t.toString());
            return null;
        }
    }

    /** Finds a declared (possibly non-public) method and makes it accessible. */
    private static Method declaredMethodOf(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            Method method = owner.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (Throwable t) {
            CBCMSMWCompat.LOGGER.info("[cbcmsmwcompat] taov compat: declared method {}.{} unavailable: {}",
                    owner.getName(), name, t.toString());
            return null;
        }
    }

    /** Walks the superclass chain for a declared method and makes it accessible. */
    private static Method declaredMethodInHierarchy(Class<?> start, String name, Class<?>... parameterTypes) {
        for (Class<?> owner = start; owner != null; owner = owner.getSuperclass()) {
            try {
                Method method = owner.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // Keep walking towards Object.
            } catch (Throwable t) {
                CBCMSMWCompat.LOGGER.info("[cbcmsmwcompat] taov compat: method {}.{} in {} unavailable: {}",
                        owner.getName(), name, start.getName(), t.toString());
                return null;
            }
        }
        CBCMSMWCompat.LOGGER.info("[cbcmsmwcompat] taov compat: method {}.{} not found in hierarchy of {}",
                start.getName(), name, start.getName());
        return null;
    }
}
