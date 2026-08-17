package com.qkdream.cbcmsmwcompat.ammorack;

import com.cainiao1053.cbcmoreshells.blocks.ammo_rack.AmmoRackBlockEntity;
import com.qkdream.cbcmsmwcompat.CBCMSMWCompat;
import com.qkdream.cbcmsmwcompat.config.CompatConfig;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import rbasamoyai.createbigcannons.CreateBigCannons;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.ShellExplosion;

@EventBusSubscriber(modid = CBCMSMWCompat.MOD_ID)
public final class CookOffHandler {

    private static final Map<ServerLevel, Set<AbstractCannonProjectile>> TRACKED = new HashMap<>();
    private static final Map<AbstractCannonProjectile, Vec3> LAST_POS = new HashMap<>();
    private static final Map<AbstractCannonProjectile, BlockPos> LAST_HIT = new HashMap<>();
    private static final Map<ServerLevel, Set<Entity>> FRAGMENTS = new HashMap<>();
    private static final Map<Entity, Vec3> FRAG_LAST_POS = new HashMap<>();

    /** Cook off explosions waiting to detonate, so bursts spread over several ticks. */
    private static final List<PendingExplosion> PENDING = new ArrayList<>();

    private CookOffHandler() {
    }

    private record PendingExplosion(ServerLevel level, Vec3 center, int ticksLeft, double scale) {
    }

    /** Queues the multi-explosion burst for one cook off at full ammo rack power. */
    public static void scheduleCookOffExplosions(ServerLevel level, BlockPos pos) {
        scheduleCookOffExplosions(level, Vec3.atCenterOf(pos), 1.0);
    }

    /** Queues the multi-explosion burst for one cook off, scaled by {@code scale}. */
    public static void scheduleCookOffExplosions(ServerLevel level, Vec3 center, double scale) {
        int count = CompatConfig.COOK_OFF_EXPLOSION_COUNT.get();
        int interval = Math.max(1, CompatConfig.COOK_OFF_EXPLOSION_INTERVAL.get());
        for (int i = 0; i < count; i++) {
            PENDING.add(new PendingExplosion(level, center, 1 + i * interval, scale));
        }
    }

    @SubscribeEvent
    public static void onProjectileJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (event.getEntity() instanceof AbstractCannonProjectile projectile) {
            TRACKED.computeIfAbsent(serverLevel, k -> new HashSet<>()).add(projectile);
            LAST_POS.put(projectile, projectile.position());
        } else if (isFragmentEntity(event.getEntity())) {
            FRAGMENTS.computeIfAbsent(serverLevel, k -> new HashSet<>()).add(event.getEntity());
            FRAG_LAST_POS.put(event.getEntity(), event.getEntity().position());
        }
    }

    @SubscribeEvent
    public static void onProjectileLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof AbstractCannonProjectile projectile) {
            LAST_POS.remove(projectile);
            LAST_HIT.remove(projectile);
            Set<AbstractCannonProjectile> set = TRACKED.get(event.getLevel());
            if (set != null) {
                set.remove(projectile);
            }
        } else if (FRAG_LAST_POS.remove(event.getEntity()) != null) {
            Set<Entity> set = FRAGMENTS.get(event.getLevel());
            if (set != null) {
                set.remove(event.getEntity());
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        processPendingExplosions();
        if (CompatConfig.DIRECT_HIT_COOK_OFF.get()) {
            sweepProjectiles(event);
        }
        sweepFragments(event);
    }

    /**
     * Works on a snapshot so explosions detonated during this pass (and the chain
     * cook offs they schedule) never mutate the list being iterated.
     */
    private static void processPendingExplosions() {
        if (PENDING.isEmpty()) {
            return;
        }
        List<PendingExplosion> snapshot = new ArrayList<>(PENDING);
        PENDING.clear();
        List<PendingExplosion> remaining = new ArrayList<>();
        for (PendingExplosion pending : snapshot) {
            if (pending.ticksLeft() > 1) {
                remaining.add(new PendingExplosion(
                        pending.level(), pending.center(), pending.ticksLeft() - 1, pending.scale()));
            } else {
                explodeCookOff(pending.level(), pending.center(), pending.scale());
            }
        }
        PENDING.addAll(0, remaining);
    }

    private static void sweepProjectiles(ServerTickEvent.Post event) {
        for (ServerLevel serverLevel : event.getServer().getAllLevels()) {
            Set<AbstractCannonProjectile> set = TRACKED.get(serverLevel);
            if (set == null || set.isEmpty()) {
                continue;
            }
            Iterator<AbstractCannonProjectile> iterator = set.iterator();
            while (iterator.hasNext()) {
                AbstractCannonProjectile projectile = iterator.next();
                if (projectile.isRemoved()) {
                    iterator.remove();
                    LAST_POS.remove(projectile);
                    LAST_HIT.remove(projectile);
                    continue;
                }
                Vec3 current = projectile.position();
                Vec3 previous = LAST_POS.put(projectile, current);
                if (previous == null) {
                    continue;
                }
                BlockPos hit = findCookOffTarget(serverLevel, projectile, previous, current);
                if (hit == null) {
                    LAST_HIT.remove(projectile);
                } else if (!hit.equals(LAST_HIT.put(projectile, hit))) {
                    // Any CBC-family projectile hit always cooks the storage off.
                    triggerCookOff(serverLevel, hit);
                }
                sweepMissiles(serverLevel, projectile, previous, current);
                sweepLauncherEntities(serverLevel, projectile, previous, current);
            }
        }
    }

    /** Cooks off mianbaos and vestalihy missiles the projectile sweeps through mid-flight. */
    private static void sweepMissiles(ServerLevel level, AbstractCannonProjectile projectile, Vec3 a, Vec3 b) {
        if (!CompatConfig.MIANBAOS_COOK_OFF.get() && !CompatConfig.VESTALIHY_COOK_OFF.get()) {
            return;
        }
        double margin = projectile.getBbWidth() * 0.5 + 0.5;
        AABB area = new AABB(a, b).inflate(margin);
        for (Entity entity : level.getEntities(null, area)) {
            if (entity == projectile || entity.isRemoved() || !isCookOffMissile(entity)) {
                continue;
            }
            if (segmentIntersectsBox(a, b, entity.getBoundingBox().inflate(projectile.getBbWidth() * 0.5 + 0.1))) {
                cookOffMissile(level, entity);
            }
        }
    }

    /** Cooks off launcher entities (vestalihy tubes, mianbaos turrets) the projectile sweeps through. */
    private static void sweepLauncherEntities(ServerLevel level, AbstractCannonProjectile projectile, Vec3 a, Vec3 b) {
        if (!CompatConfig.MIANBAOS_COOK_OFF.get() && !CompatConfig.VESTALIHY_COOK_OFF.get()) {
            return;
        }
        double margin = projectile.getBbWidth() * 0.5 + 0.5;
        AABB area = new AABB(a, b).inflate(margin);
        for (Entity entity : level.getEntities(null, area)) {
            if (entity == projectile || entity.isRemoved() || !isCookOffLauncherEntity(entity)) {
                continue;
            }
            if (segmentIntersectsBox(a, b, entity.getBoundingBox().inflate(projectile.getBbWidth() * 0.5 + 0.1))) {
                cookOffLauncherEntity(level, entity);
            }
        }
    }

    /**
     * Fragments (shrapnel bursts) only cook off missiles in flight. Ammo racks and
     * missile launchers are never triggered by fragments.
     */
    private static void sweepFragments(ServerTickEvent.Post event) {
        if (!CompatConfig.MIANBAOS_COOK_OFF.get() && !CompatConfig.VESTALIHY_COOK_OFF.get()) {
            return;
        }
        for (ServerLevel serverLevel : event.getServer().getAllLevels()) {
            Set<Entity> set = FRAGMENTS.get(serverLevel);
            if (set == null || set.isEmpty()) {
                continue;
            }
            Iterator<Entity> iterator = set.iterator();
            while (iterator.hasNext()) {
                Entity fragment = iterator.next();
                if (fragment.isRemoved()) {
                    iterator.remove();
                    FRAG_LAST_POS.remove(fragment);
                    continue;
                }
                Vec3 current = fragment.position();
                Vec3 previous = FRAG_LAST_POS.put(fragment, current);
                if (previous == null) {
                    continue;
                }
                AABB area = new AABB(previous, current).inflate(1.0);
                for (Entity entity : serverLevel.getEntities(null, area)) {
                    if (entity == fragment || entity.isRemoved() || !isCookOffMissile(entity)) {
                        continue;
                    }
                    if (segmentIntersectsBox(previous, current, entity.getBoundingBox().inflate(0.5))) {
                        cookOffMissile(serverLevel, entity);
                    }
                }
            }
        }
    }

    /** True when the entity is an in-flight missile of an enabled missile mod. */
    private static boolean isCookOffMissile(Entity entity) {
        if (CompatConfig.MIANBAOS_COOK_OFF.get() && MianbaosCompatUtil.isMissile(entity)) {
            return true;
        }
        return CompatConfig.VESTALIHY_COOK_OFF.get() && VestalihyCompatUtil.isMissile(entity);
    }

    /** True when the entity is a launcher platform of an enabled missile mod. */
    private static boolean isCookOffLauncherEntity(Entity entity) {
        if (CompatConfig.MIANBAOS_COOK_OFF.get() && MianbaosCompatUtil.isLauncherMob(entity)) {
            return true;
        }
        return CompatConfig.VESTALIHY_COOK_OFF.get() && VestalihyCompatUtil.isLauncher(entity);
    }

    /** True when the entity is a fragment (shrapnel) burst spawned by Ritchies Projectile Lib. */
    private static boolean isFragmentEntity(Entity entity) {
        if (entity == null) {
            return false;
        }
        for (Class<?> cls = entity.getClass(); cls != null; cls = cls.getSuperclass()) {
            if ("rbasamoyai.ritchiesprojectilelib.projectile_burst.ProjectileBurst".equals(cls.getName())) {
                return true;
            }
        }
        return false;
    }

    /** Triggers cook off on whichever ammo storage (rack, depot or launcher) is at the position. */
    private static void triggerCookOff(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof AmmoRackBlockEntity rack && RackCompatUtil.hasAmmo(rack)) {
            RackCompatUtil.cookOff(level, pos, rack);
        } else if (CompatConfig.DEPOT_COOK_OFF.get()
                && level.getBlockEntity(pos) instanceof DepotBlockEntity depot
                && !depot.getHeldItem().isEmpty()
                && RackCompatUtil.isLoadableAmmo(depot.getHeldItem())) {
            RackCompatUtil.cookOffDepot(level, pos, depot);
        } else if (CompatConfig.MIANBAOS_COOK_OFF.get() && isLauncherBlock(level, pos)) {
            cookOffLauncher(level, pos);
        }
    }

    private static BlockPos findCookOffTarget(ServerLevel level, AbstractCannonProjectile projectile, Vec3 a, Vec3 b) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double dz = b.z - a.z;
        double distanceSqr = dx * dx + dy * dy + dz * dz;
        if (distanceSqr > 4096.0) {
            return null; // teleport or chunk reload jump, ignore
        }

        double halfWidth = projectile.getBbWidth() * 0.5 + 0.1;
        double halfHeight = projectile.getBbHeight() * 0.5 + 0.1;
        int minX = Mth.floor(Math.min(a.x, b.x) - halfWidth);
        int maxX = Mth.floor(Math.max(a.x, b.x) + halfWidth);
        int minY = Mth.floor(Math.min(a.y, b.y) - halfHeight);
        int maxY = Mth.floor(Math.max(a.y, b.y) + halfHeight);
        int minZ = Mth.floor(Math.min(a.z, b.z) - halfWidth);
        int maxZ = Mth.floor(Math.max(a.z, b.z) + halfWidth);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    boolean rack = RackCompatUtil.isAmmoRack(level, pos);
                    boolean depot = CompatConfig.DEPOT_COOK_OFF.get() && isDepotBlock(level, pos);
                    boolean launcher = CompatConfig.MIANBAOS_COOK_OFF.get() && isLauncherBlock(level, pos);
                    if (!rack && !depot && !launcher) {
                        continue;
                    }
                    AABB swept = new AABB(
                            x - halfWidth, y - halfHeight, z - halfWidth,
                            x + 1 + halfWidth, y + 1 + halfHeight, z + 1 + halfWidth);
                    if (depot && !rack) {
                        // The held item renders above the depot plate, so treat the
                        // cell above the depot as part of its hit zone as well.
                        swept = swept.expandTowards(0.0, 1.0, 0.0);
                    }
                    if (segmentIntersectsBox(a, b, swept)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private static boolean isDepotBlock(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof com.simibubi.create.content.logistics.depot.DepotBlock;
    }

    private static boolean isLauncherBlock(Level level, BlockPos pos) {
        return level.getBlockState(pos).hasBlockEntity()
                && MianbaosCompatUtil.isLauncher(level.getBlockEntity(pos));
    }

    /** Destroys the launcher block and starts its cook off burst, but only when a missile is loaded. */
    public static void cookOffLauncher(ServerLevel level, BlockPos pos) {
        if (!MianbaosCompatUtil.hasMissile(level.getBlockEntity(pos))) {
            return;
        }
        level.destroyBlock(pos, false);
        scheduleCookOffExplosions(level, Vec3.atCenterOf(pos), CompatConfig.MIANBAOS_POWER_SCALE.get());
        if (CompatConfig.DEBUG_LOGGING.get()) {
            CBCMSMWCompat.LOGGER.info("[cbcmsmwcompat] Cook off: mianbaos launcher at {}", pos);
        }
    }

    /** Removes the missile entity and starts its cook off burst at the reduced missile power. */
    public static void cookOffMissile(ServerLevel level, Entity missile) {
        Vec3 center = missile.position();
        missile.discard();
        scheduleCookOffExplosions(level, center, CompatConfig.MISSILE_POWER_SCALE.get());
        if (CompatConfig.DEBUG_LOGGING.get()) {
            CBCMSMWCompat.LOGGER.info("[cbcmsmwcompat] Cook off: missile at {}", center);
        }
    }

    /**
     * Removes a launcher entity (vestalihy tube/tripod or mianbaos turret) and starts its
     * cook off burst at launcher power. Vestalihy launchers only cook off when loaded.
     */
    public static void cookOffLauncherEntity(ServerLevel level, Entity launcher) {
        Vec3 center = launcher.position();
        double scale;
        if (VestalihyCompatUtil.isLauncher(launcher)) {
            if (!VestalihyCompatUtil.hasMissile(launcher)) {
                return;
            }
            scale = CompatConfig.VESTALIHY_POWER_SCALE.get();
        } else {
            scale = CompatConfig.MIANBAOS_POWER_SCALE.get();
        }
        launcher.discard();
        scheduleCookOffExplosions(level, center, scale);
        if (CompatConfig.DEBUG_LOGGING.get()) {
            CBCMSMWCompat.LOGGER.info("[cbcmsmwcompat] Cook off: launcher entity at {}", center);
        }
    }

    /** A player dying while carrying CBC-family ammunition detonates at rack power. */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!CompatConfig.PLAYER_DEATH_COOK_OFF.get()
                || !(event.getEntity() instanceof ServerPlayer player)
                || player.level().isClientSide) {
            return;
        }
        boolean found = false;
        for (NonNullList<ItemStack> compartment : List.of(
                player.getInventory().items,
                player.getInventory().armor,
                player.getInventory().offhand)) {
            for (int slot = 0; slot < compartment.size(); slot++) {
                ItemStack stack = compartment.get(slot);
                if (!stack.isEmpty() && RackCompatUtil.isLoadableAmmo(stack)) {
                    compartment.set(slot, ItemStack.EMPTY);
                    found = true;
                }
            }
        }
        if (!found) {
            return;
        }
        scheduleCookOffExplosions(player.serverLevel(), player.blockPosition());
        if (CompatConfig.DEBUG_LOGGING.get()) {
            CBCMSMWCompat.LOGGER.info("[cbcmsmwcompat] Cook off: player {} died carrying CBC ammunition",
                    player.getName().getString());
        }
    }

    private static boolean segmentIntersectsBox(Vec3 a, Vec3 b, AABB box) {
        double tMin = 0.0;
        double tMax = 1.0;

        double[] result = slab(tMin, tMax, a.x, b.x - a.x, box.minX, box.maxX);
        if (result == null) {
            return false;
        }
        tMin = result[0];
        tMax = result[1];

        result = slab(tMin, tMax, a.y, b.y - a.y, box.minY, box.maxY);
        if (result == null) {
            return false;
        }
        tMin = result[0];
        tMax = result[1];

        result = slab(tMin, tMax, a.z, b.z - a.z, box.minZ, box.maxZ);
        if (result == null) {
            return false;
        }
        tMin = result[0];
        tMax = result[1];

        return tMax >= 0.0 && tMin <= 1.0;
    }

    /** Returns the updated {tMin, tMax} interval, or null when the ray misses the slab. */
    private static double[] slab(double tMin, double tMax, double origin, double dir, double slabMin, double slabMax) {
        if (Math.abs(dir) < 1.0E-7) {
            return (origin >= slabMin && origin <= slabMax) ? new double[]{tMin, tMax} : null;
        }
        double t1 = (slabMin - origin) / dir;
        double t2 = (slabMax - origin) / dir;
        if (t1 > t2) {
            double tmp = t1;
            t1 = t2;
            t2 = tmp;
        }
        tMin = Math.max(tMin, t1);
        tMax = Math.min(tMax, t2);
        if (tMin > tMax) {
            return null;
        }
        return new double[]{tMin, tMax};
    }

    /** Detonates one cook off explosion at the stored position. */
    private static void explodeCookOff(ServerLevel level, Vec3 center, double scale) {
        float blockRadius = (float) (CompatConfig.COOK_OFF_BLOCK_RADIUS.get() * scale);
        float entityRadius = (float) (CompatConfig.COOK_OFF_ENTITY_RADIUS.get() * scale);
        double jitter = CompatConfig.COOK_OFF_EXPLOSION_JITTER.get();
        double x = center.x;
        double z = center.z;
        if (jitter > 0.0) {
            x += (level.random.nextDouble() * 2.0 - 1.0) * jitter;
            z += (level.random.nextDouble() * 2.0 - 1.0) * jitter;
        }
        ShellExplosion explosion = new ShellExplosion(
                level,
                null,
                null,
                x,
                center.y,
                z,
                blockRadius,
                entityRadius,
                CompatConfig.COOK_OFF_FIRE.get(),
                getExplosiveInteraction());
        CreateBigCannons.handleCustomExplosion(level, explosion);
    }

    /**
     * Reads CBC's grief config (GriefState.explosiveInteraction) through reflection so
     * this mod compiles without catnip on the classpath. Falls back to DESTROY.
     */
    private static Explosion.BlockInteraction getExplosiveInteraction() {
        try {
            Class<?> configsClass = Class.forName("rbasamoyai.createbigcannons.config.CBCConfigs");
            Object serverConfig = configsClass.getMethod("server").invoke(null);
            Object munitionsConfig = serverConfig.getClass().getField("munitions").get(serverConfig);
            Object damageRestriction = munitionsConfig.getClass().getField("damageRestriction").get(munitionsConfig);
            Object griefState = damageRestriction.getClass().getMethod("get").invoke(damageRestriction);
            Method method = griefState.getClass().getMethod("explosiveInteraction");
            method.setAccessible(true);
            return (Explosion.BlockInteraction) method.invoke(griefState);
        } catch (Throwable t) {
            return Explosion.BlockInteraction.DESTROY;
        }
    }

    /**
     * Fires before the explosion damages anything: a storage detonated by the direct
     * impact of an exploding shell must cook off before the blast destroys it.
     */
    @SubscribeEvent
    public static void onExplosionStart(ExplosionEvent.Start event) {
        Level level = event.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Explosion explosion = event.getExplosion();
        double radius = explosion.radius();
        if (!(radius > 0.0) || radius > 24.0) {
            return;
        }
        for (BlockPos pos : spherePositions(explosion.center(), radius)) {
            cookOffBlockInBlast(serverLevel, pos);
        }

        // Missiles and launcher entities (vestalihy launchers are entities that do not
        // take explosion damage) also cook off when caught in the blast.
        if (CompatConfig.MIANBAOS_COOK_OFF.get() || CompatConfig.VESTALIHY_COOK_OFF.get()) {
            AABB blastArea = AABB.ofSize(explosion.center(), radius * 2.0, radius * 2.0, radius * 2.0);
            for (Entity entity : serverLevel.getEntities(null, blastArea)) {
                if (entity.isRemoved()) {
                    continue;
                }
                if (isCookOffMissile(entity)) {
                    cookOffMissile(serverLevel, entity);
                } else if (isCookOffLauncherEntity(entity)) {
                    cookOffLauncherEntity(serverLevel, entity);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        Explosion explosion = event.getExplosion();
        Set<BlockPos> candidates = new HashSet<>(event.getAffectedBlocks());

        // Storages that survive the blast but are still inside the blast radius
        // also cook off: being caught in the blast guarantees detonation.
        double radius = explosion.radius();
        if (radius > 0.0 && radius <= 24.0) {
            candidates.addAll(spherePositions(explosion.center(), radius));
        }

        for (BlockPos pos : candidates) {
            cookOffBlockInBlast(serverLevel, pos);
        }

        for (Entity entity : event.getAffectedEntities()) {
            if (entity.isRemoved()) {
                continue;
            }
            if (isCookOffMissile(entity)) {
                cookOffMissile(serverLevel, entity);
            } else if (isCookOffLauncherEntity(entity)) {
                cookOffLauncherEntity(serverLevel, entity);
            }
        }
    }

    /** Cooks off whichever cook-off-capable storage is at the position, if any. */
    private static void cookOffBlockInBlast(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.hasBlockEntity()) {
            return;
        }
        if (RackCompatUtil.isAmmoRack(state)) {
            if (!CompatConfig.BLAST_COOK_OFF.get()) {
                return;
            }
            if (level.getBlockEntity(pos) instanceof AmmoRackBlockEntity rack && RackCompatUtil.hasAmmo(rack)) {
                RackCompatUtil.cookOff(level, pos, rack);
            }
        } else if (CompatConfig.DEPOT_COOK_OFF.get() && RackCompatUtil.isDepotWithAmmo(level, pos)) {
            if (level.getBlockEntity(pos) instanceof DepotBlockEntity depot) {
                RackCompatUtil.cookOffDepot(level, pos, depot);
            }
        } else if (CompatConfig.MIANBAOS_COOK_OFF.get() && isLauncherBlock(level, pos)) {
            cookOffLauncher(level, pos);
        }
    }

    private static Set<BlockPos> spherePositions(Vec3 center, double radius) {
        int r = Mth.ceil(radius);
        int cx = Mth.floor(center.x);
        int cy = Mth.floor(center.y);
        int cz = Mth.floor(center.z);
        int rSqr = r * r;
        Set<BlockPos> positions = new HashSet<>();
        for (int x = cx - r; x <= cx + r; x++) {
            for (int y = cy - r; y <= cy + r; y++) {
                for (int z = cz - r; z <= cz + r; z++) {
                    int dx = x - cx;
                    int dy = y - cy;
                    int dz = z - cz;
                    if (dx * dx + dy * dy + dz * dz <= rSqr) {
                        positions.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return positions;
    }
}
