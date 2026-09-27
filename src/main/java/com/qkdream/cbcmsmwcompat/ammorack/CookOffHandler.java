package com.qkdream.cbcmsmwcompat.ammorack;

import com.cainiao1053.cbcmoreshells.blocks.ammo_rack.AmmoRackBlockEntity;
import com.qkdream.cbcmsmwcompat.CBCMSMWCompat;
import com.qkdream.cbcmsmwcompat.config.CompatConfig;
import com.qkdream.cbcmsmwcompat.sable.SableCompat;
import com.cainiao1053.cbcmoreshells.munitions.big_cannon.AbstractCannonTorpedoProjectile;
import com.cainiao1053.cbcmoreshells.munitions.big_cannon.ShellessFuzedBigCannonProjectile;
import com.cainiao1053.cbcmoreshells.munitions.racked_projectile.AbstractRackedProjectile;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.entity.BlockEntity;

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
import net.neoforged.neoforge.items.IItemHandler;
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
    private static long lastTaovSweepLogSecond = Long.MIN_VALUE;

    /**
     * Storages that already rolled their blast cook off chance for one concrete
     * explosion. The start and the detonate event both enumerate the blast, so without
     * this every storage would roll twice. Weak keys collect finished explosions.
     */
    private static final Map<Explosion, Set<BlockPos>> BLAST_ROLLS = new WeakHashMap<>();

    /** Cook off explosions waiting to detonate, so bursts spread over several ticks. */
    private static final List<PendingExplosion> PENDING = new ArrayList<>();

    /** Smoke clouds released by cook offs of smoke ammunition, fading over a few seconds. */
    private static final List<PendingSmoke> PENDING_SMOKE = new ArrayList<>();

    private CookOffHandler() {
    }

    private record PendingExplosion(ServerLevel level, Vec3 center, int ticksLeft, double scale,
            boolean smoke, boolean fire) {
    }

    private record PendingSmoke(ServerLevel level, Vec3 center, int ticksLeft) {
    }

    /** Queues the multi-explosion burst for one cook off at full ammo rack power. */
    public static void scheduleCookOffExplosions(ServerLevel level, BlockPos pos) {
        scheduleCookOffExplosions(level, Vec3.atCenterOf(pos), 1.0);
    }

    /** Queues the multi-explosion burst for one cook off, scaled by {@code scale}. */
    public static void scheduleCookOffExplosions(ServerLevel level, Vec3 center, double scale) {
        scheduleCookOffExplosions(level, center, scale, false, false);
    }

    /** Queues the multi-explosion burst for one cook off, scaled by {@code scale}. */
    public static void scheduleCookOffExplosions(ServerLevel level, Vec3 center, double scale,
            boolean smoke, boolean fire) {
        scheduleCookOffExplosions(level, center, scale, smoke, fire,
                CompatConfig.COOK_OFF_EXPLOSION_COUNT.get());
    }

    /** Queues a cook off with an explicit number of explosions. */
    public static void scheduleCookOffExplosions(ServerLevel level, Vec3 center, double scale,
            boolean smoke, boolean fire, int count) {
        int interval = Math.max(1, CompatConfig.COOK_OFF_EXPLOSION_INTERVAL.get());
        // Block storages inside Sable sub-levels report plot coordinates. Project the
        // blast to the structure's displayed world position so the main world and any
        // neighbouring structures around it are damaged as well.
        Vec3 worldCenter = SableCompat.projectOutOfSubLevel(level, center);
        for (int i = 0; i < count; i++) {
            PENDING.add(new PendingExplosion(level, worldCenter, 1 + i * interval, scale, smoke, fire));
        }
        if (smoke && CompatConfig.COOK_OFF_SPECIAL_EFFECTS.get()) {
            PENDING_SMOKE.add(new PendingSmoke(level, worldCenter, 160));
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
        processPendingSmoke();
        sweepFragments(event);
    }

    /**
     * Called by the mixin right before a CBC projectile processes a block impact, while
     * the block still exists: a rack or depot hit this way always cooks off first, even
     * when the projectile itself would otherwise just destroy the block.
     */
    public static void onProjectileBlockHit(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel) || !CompatConfig.DIRECT_HIT_COOK_OFF.get()) {
            return;
        }
        triggerCookOff(serverLevel, pos);
    }

    /**
     * Called by the mixin after every CBC projectile tick. Runs the contact check and
     * the swept segment check so ammo storages detonate the moment a projectile
     * touches them, independent of CBC's internal penetration/impact pipeline.
     */
    public static void onProjectileTick(AbstractCannonProjectile projectile) {
        if (!(projectile.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (projectile.isRemoved()) {
            LAST_POS.remove(projectile);
            LAST_HIT.remove(projectile);
            Set<AbstractCannonProjectile> removedSet = TRACKED.get(serverLevel);
            if (removedSet != null) {
                removedSet.remove(projectile);
            }
            return;
        }
        TRACKED.computeIfAbsent(serverLevel, k -> new HashSet<>()).add(projectile);
        if (!CompatConfig.DIRECT_HIT_COOK_OFF.get()) {
            return;
        }
        Vec3 current = projectile.position();
        Vec3 previous = LAST_POS.put(projectile, current);

        // Contact detonation: the storage the projectile hitbox touches cooks off
        // even when CBC's penetration path never runs for that block.
        BlockPos contact = findContactTarget(serverLevel, projectile);
        if (contact != null && !contact.equals(LAST_HIT.get(projectile))) {
            LAST_HIT.put(projectile, contact);
            triggerCookOff(serverLevel, contact);
        }
        if (previous == null) {
            return;
        }
        BlockPos hit = findCookOffTarget(serverLevel, projectile, previous, current);
        if (hit == null) {
            LAST_HIT.remove(projectile);
        } else if (!hit.equals(LAST_HIT.put(projectile, hit))) {
            triggerCookOff(serverLevel, hit);
        }
        sweepMissiles(serverLevel, projectile, previous, current);
        sweepLauncherEntities(serverLevel, projectile, previous, current);
    }

    /** Returns the cook-off storage the projectile hitbox currently overlaps, if any. */
    private static BlockPos findContactTarget(ServerLevel level, AbstractCannonProjectile projectile) {
        AABB box = projectile.getBoundingBox().inflate(0.05);
        int minX = Mth.floor(box.minX);
        int maxX = Mth.floor(box.maxX);
        int minY = Mth.floor(box.minY);
        int maxY = Mth.floor(box.maxY);
        int minZ = Mth.floor(box.minZ);
        int maxZ = Mth.floor(box.maxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    boolean rack = RackCompatUtil.isAmmoRack(level, pos);
                    boolean depot = CompatConfig.DEPOT_COOK_OFF.get() && isDepotBlock(level, pos);
                    boolean launcher = CompatConfig.MIANBAOS_COOK_OFF.get() && isLauncherBlock(level, pos);
                    boolean taovRack = CompatConfig.TAOV_COOK_OFF.get() && TaovCompat.isRackBlockEntity(level.getBlockEntity(pos));
                    if (rack || depot || launcher || taovRack) {
                        return pos;
                    }
                }
            }
        }
        return null;
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
                        pending.level(), pending.center(), pending.ticksLeft() - 1,
                        pending.scale(), pending.smoke(), pending.fire()));
            } else {
                explodeCookOff(pending.level(), pending.center(), pending.scale(), pending.fire());
            }
        }
        PENDING.addAll(0, remaining);
    }

    /** Emits the smoke cloud of a smoke-shell cook off, a few particles every tick. */
    private static void processPendingSmoke() {
        if (PENDING_SMOKE.isEmpty()) {
            return;
        }
        List<PendingSmoke> snapshot = new ArrayList<>(PENDING_SMOKE);
        PENDING_SMOKE.clear();
        List<PendingSmoke> remaining = new ArrayList<>();
        for (PendingSmoke smoke : snapshot) {
            if (smoke.ticksLeft() > 0) {
                ServerLevel level = smoke.level();
                Vec3 center = smoke.center();
                double rise = (160 - smoke.ticksLeft()) * 0.02;
                for (int i = 0; i < 3; i++) {
                    double x = center.x + (level.random.nextDouble() * 2.0 - 1.0) * 2.5;
                    double y = center.y + 0.5 + rise + level.random.nextDouble() * 1.5;
                    double z = center.z + (level.random.nextDouble() * 2.0 - 1.0) * 2.5;
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 1, 0.05, 0.15, 0.05, 0.02);
                }
                remaining.add(new PendingSmoke(level, center, smoke.ticksLeft() - 1));
            }
        }
        PENDING_SMOKE.addAll(0, remaining);
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

    /** Cooks off in-flight missiles and air munitions the projectile sweeps through mid-flight. */
    private static void sweepMissiles(ServerLevel level, AbstractCannonProjectile projectile, Vec3 a, Vec3 b) {
        boolean missileMods = CompatConfig.MIANBAOS_COOK_OFF.get() || CompatConfig.VESTALIHY_COOK_OFF.get();
        boolean airMunitions = CompatConfig.AIR_MUNITION_COOK_OFF.get();
        if (!missileMods && !airMunitions) {
            return;
        }
        double margin = projectile.getBbWidth() * 0.5 + 0.5;
        AABB area = new AABB(a, b).inflate(margin);
        for (Entity entity : level.getEntities(null, area)) {
            if (entity == projectile || entity.isRemoved()) {
                continue;
            }
            boolean cookOff = missileMods && isCookOffMissile(entity);
            if (!cookOff && airMunitions) {
                cookOff = isCBCMSAirMunition(entity);
            }
            if (!cookOff) {
                continue;
            }
            if (segmentIntersectsBox(a, b, entity.getBoundingBox().inflate(projectile.getBbWidth() * 0.5 + 0.1))) {
                cookOffMissile(level, entity);
            }
        }
        if (airMunitions && CompatConfig.TAOV_COOK_OFF.get()) {
            AABB taovArea = area.inflate(3.0);
            logTaovSweep("direct hit sweep", level, taovArea);
            TaovCompat.detonateAirMunitionsIn(level, taovArea);
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
     * Shrapnel bursts (Ritchies Projectile Lib fragment entities) only cook off missiles
     * in flight: ammo racks and missile launchers are never triggered by those. The
     * fragments CBC Terminal Ballistics casts are no entities and are reported from the
     * block they hit instead, see onFragmentBlockHit.
     */
    private static void sweepFragments(ServerTickEvent.Post event) {
        boolean missileMods = CompatConfig.MIANBAOS_COOK_OFF.get() || CompatConfig.VESTALIHY_COOK_OFF.get();
        boolean airMunitions = CompatConfig.AIR_MUNITION_COOK_OFF.get();
        if (!missileMods && !airMunitions) {
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
                    if (entity == fragment || entity.isRemoved()) {
                        continue;
                    }
                    boolean cookOff = missileMods && isCookOffMissile(entity);
                    if (!cookOff && airMunitions) {
                        cookOff = isCBCMSAirMunition(entity);
                    }
                    if (!cookOff) {
                        continue;
                    }
                    if (segmentIntersectsBox(previous, current, entity.getBoundingBox().inflate(0.5))) {
                        cookOffMissile(serverLevel, entity);
                    }
                }
                if (airMunitions && CompatConfig.TAOV_COOK_OFF.get()) {
                    AABB taovArea = area.inflate(2.0);
                    logTaovSweep("fragment sweep", serverLevel, taovArea);
                    TaovCompat.detonateAirMunitionsIn(serverLevel, taovArea);
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

    /**
     * True when the entity is an in-flight CBC Military Supplement air munition:
     * torpedoes, racked rockets, bombs, depth charges and cannon rockets.
     */
    private static boolean isCBCMSAirMunition(Entity entity) {
        return entity instanceof AbstractCannonTorpedoProjectile
                || entity instanceof AbstractRackedProjectile
                || entity instanceof ShellessFuzedBigCannonProjectile;
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
    private static boolean triggerCookOff(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof AmmoRackBlockEntity rack) {
            return RackCompatUtil.cookOff(level, pos, rack);
        }
        if (CompatConfig.DEPOT_COOK_OFF.get()
                && level.getBlockEntity(pos) instanceof DepotBlockEntity depot
                && RackCompatUtil.isDepotWithAmmo(level, pos)) {
            return RackCompatUtil.cookOffDepot(level, pos, depot);
        }
        if (CompatConfig.MIANBAOS_COOK_OFF.get() && isLauncherBlock(level, pos)) {
            cookOffLauncher(level, pos);
            return true;
        }
        if (CompatConfig.TAOV_COOK_OFF.get()
                && TaovCompat.isRackBlockEntity(level.getBlockEntity(pos))) {
            cookOffTaovRack(level, pos, level.getBlockEntity(pos));
            return true;
        }
        return false;
    }

    /**
     * Cheap pre-filter of the blast and fragment triggers: true when the block at the
     * position may be a cook-off storage with that trigger enabled. Whether it actually
     * holds ammunition that can cook off is decided by the cook off itself.
     */
    private static boolean canCookOff(ServerLevel level, BlockPos pos, boolean fragment) {
        if (fragment && !CompatConfig.FRAGMENT_COOK_OFF.get()) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.hasBlockEntity()) {
            return false;
        }
        if (RackCompatUtil.isAmmoRack(state)) {
            return fragment || CompatConfig.BLAST_COOK_OFF.get();
        }
        if (CompatConfig.DEPOT_COOK_OFF.get() && isDepotBlock(level, pos)) {
            return true;
        }
        if (CompatConfig.MIANBAOS_COOK_OFF.get()
                && MianbaosCompatUtil.isCookOffBlock(level.getBlockEntity(pos))) {
            return true;
        }
        return CompatConfig.TAOV_COOK_OFF.get() && TaovCompat.isRackBlockEntity(level.getBlockEntity(pos));
    }

    /**
     * Called by the terminal ballistics mixin for every block one of its fragments hits.
     * CBC Terminal Ballistics spall is not made of entities (it casts plain rays and
     * applies their damage itself), so those fragments never reach the entity sweep used
     * for shrapnel bursts and are reported from the block they hit instead. Returns true
     * when an ammo storage cooked off there.
     */
    public static boolean onFragmentBlockHit(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel) || !canCookOff(serverLevel, pos, true)) {
            return false;
        }
        return triggerCookOff(serverLevel, pos);
    }

    private static BlockPos findCookOffTarget(
ServerLevel level, AbstractCannonProjectile projectile, Vec3 a, Vec3 b) {
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
                    boolean taovRack = CompatConfig.TAOV_COOK_OFF.get() && TaovCompat.isRackBlockEntity(level.getBlockEntity(pos));
                    if (!rack && !depot && !launcher && !taovRack) {
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

    /**
     * True when a loaded mianbaos launcher or pylon round is at or next to the position.
     * Multi-block launchers keep their rounds in a single part, so the neighbouring
     * blocks count as well.
     */
    private static boolean isLauncherBlock(Level level, BlockPos pos) {
        if (!level.getBlockState(pos).hasBlockEntity()) {
            return false;
        }
        if (!MianbaosCompatUtil.isCookOffBlock(level.getBlockEntity(pos))) {
            return false;
        }
        return MianbaosCompatUtil.findLoadedCookOffBlock(level, pos) != null;
    }

    /** Destroys the loaded launcher block and starts its cook off burst; empty ones are skipped. */
    public static void cookOffLauncher(ServerLevel level, BlockPos pos) {
        BlockPos target = MianbaosCompatUtil.findLoadedCookOffBlock(level, pos);
        if (target == null) {
            return;
        }
        level.destroyBlock(target, false);
        scheduleCookOffExplosions(level, Vec3.atCenterOf(target), CompatConfig.MIANBAOS_POWER_SCALE.get());
        if (CompatConfig.DEBUG_LOGGING.get()) {
            CBCMSMWCompat.LOGGER.info("[cbcmsmwcompat] Cook off: mianbaos launcher at {}", target);
        }
    }

    /** Removes the missile entity and starts its cook off at the reduced missile power. */
    public static void cookOffMissile(ServerLevel level, Entity missile) {
        Vec3 center = missile.position();
        boolean airMunition = isCBCMSAirMunition(missile);
        missile.discard();
        if (airMunition) {
            // Destroyed air ammunition pops once at reduced power instead of a full burst.
            scheduleCookOffExplosions(level, center, CompatConfig.CBCMS_AIR_POWER_SCALE.get(),
                    false, false, 1);
        } else {
            scheduleCookOffExplosions(level, center, CompatConfig.MISSILE_POWER_SCALE.get());
        }

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

    /**
     * Destroys a Tau/Hellfire rack holding ammunition and starts its cook off burst.
     */
    public static void cookOffTaovRack(ServerLevel level, BlockPos pos, BlockEntity rack) {
        if (TaovCompat.remainingAmmo(rack) <= 0) {
            return;
        }
        level.destroyBlock(pos, false);
        scheduleCookOffExplosions(level, Vec3.atCenterOf(pos), CompatConfig.TAOV_RACK_POWER_SCALE.get());
        if (CompatConfig.DEBUG_LOGGING.get()) {
            CBCMSMWCompat.LOGGER.info("[cbcmsmwcompat] Cook off: taov rack at {}", pos);
        }
    }

    /**
     * Any living entity (players included) dying while carrying CBC-family ammunition or
     * propellant detonates. The power follows the same shell type and quantity rules as
     * an ammo rack: more ammunition means more power, capped at the configured maximum.
     */

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!CompatConfig.MOB_DEATH_COOK_OFF.get()) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        if (!(living.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        List<ItemStack> carried = new ArrayList<>();
        living.getAllSlots().forEach(carried::add);
        if (living instanceof Player player) {
            carried.addAll(player.getInventory().items);
        } else {
            addMaidInventoryStacks(living, carried);
        }
        double totalWeight = 0.0;
        boolean smoke = false;
        boolean fire = false;
        for (ItemStack stack : carried) {
            if (stack.isEmpty()) {
                continue;
            }
            CookOffYield yield = CookOffYield.of(stack, serverLevel.getServer());
            if (yield.weight() > 0.0) {
                totalWeight += yield.weight() * stack.getCount();
                smoke |= yield.smoke();
                fire |= yield.fire();
                stack.setCount(0);
            }
        }
        if (totalWeight <= 0.0) {
            return;
        }
        scheduleCookOffExplosions(serverLevel, living.position(),
                CookOffYield.powerScale(totalWeight), smoke, fire);
        if (CompatConfig.DEBUG_LOGGING.get()) {
            CBCMSMWCompat.LOGGER.info("[cbcmsmwcompat] Cook off: {} died carrying CBC ammunition with yield {}",
                    living.getName().getString(), String.format("%.2f", totalWeight));
        }
    }

    /**
     * Touhou Little Maid maids carry items in their own inventory instead of vanilla
     * equipment slots, so their backpack is enumerated explicitly. Everything else is
     * read through reflection: the maid mod is optional and must never be required.
     */
    private static void addMaidInventoryStacks(LivingEntity living, List<ItemStack> carried) {
        Class<?> type = living.getClass();
        while (type != null) {
            if (type.getName().equals("com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid")) {
                break;
            }
            type = type.getSuperclass();
        }
        if (type == null) {
            return;
        }
        try {
            Object handler = living.getClass().getMethod("getMaidInv").invoke(living);
            if (handler instanceof IItemHandler inventory) {
                for (int slot = 0; slot < inventory.getSlots(); slot++) {
                    carried.add(inventory.getStackInSlot(slot));
                }
            }
        } catch (Throwable ignored) {
            // Cook off must never break because of an optional mod.
        }
    }

    /** Rate-limited diagnostic for the per-tick TaovCompat air munition sweeps. */
    private static void logTaovSweep(String source, ServerLevel level, AABB area) {
        if (!CompatConfig.DEBUG_LOGGING.get()) {
            return;
        }
        long second = System.nanoTime() / 1_000_000_000L;
        if (second == lastTaovSweepLogSecond) {
            return;
        }
        lastTaovSweepLogSecond = second;
        CBCMSMWCompat.LOGGER.info("[cbcmsmwcompat] taov sweep from {} in {}: area {}",
                source, level.dimension().location(), area);
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
    private static void explodeCookOff(ServerLevel level, Vec3 center, double scale, boolean fire) {
        float radius = (float) (CompatConfig.COOK_OFF_BASE_RADIUS.get() * scale);
        double jitter = CompatConfig.COOK_OFF_EXPLOSION_JITTER.get();
        double x = center.x;
        double z = center.z;
        if (jitter > 0.0) {
            x += (level.random.nextDouble() * 2.0 - 1.0) * jitter;
            z += (level.random.nextDouble() * 2.0 - 1.0) * jitter;
        }
        if (CompatConfig.SABLE_COOK_OFF.get() && SableCompat.isSableLoaded()) {
            SableCompat.damageStructuresInBlast(level, center, radius);
        }
        ShellExplosion explosion = new ShellExplosion(
                level,
                null,
                null,
                x,
                center.y,
                z,
                radius,
                radius,
                fire || CompatConfig.COOK_OFF_FIRE.get(),
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
        Vec3 worldCenter = SableCompat.projectOutOfSubLevel(serverLevel, explosion.center());
        for (BlockPos pos : spherePositions(worldCenter, radius)) {
            cookOffInBlast(serverLevel, explosion, worldCenter, pos);
        }
        if (CompatConfig.SABLE_COOK_OFF.get() && SableCompat.isSableLoaded()) {
            SableCompat.forEachBlockInBlast(serverLevel, worldCenter, radius,
                    (blastLevel, pos) -> cookOffInBlast(blastLevel, explosion, worldCenter, pos));
        }

        // Missiles and launcher entities (vestalihy launchers are entities that do not
        // take explosion damage) also cook off when caught in the blast.
        boolean missileMods = CompatConfig.MIANBAOS_COOK_OFF.get() || CompatConfig.VESTALIHY_COOK_OFF.get();
        boolean airMunitions = CompatConfig.AIR_MUNITION_COOK_OFF.get();
        if (missileMods || airMunitions) {
            AABB blastArea = AABB.ofSize(worldCenter, radius * 2.0, radius * 2.0, radius * 2.0);
            for (Entity entity : serverLevel.getEntities(null, blastArea)) {
                if (entity.isRemoved()) {
                    continue;
                }
                if (missileMods && isCookOffMissile(entity)) {
                    cookOffMissile(serverLevel, entity);
                } else if (airMunitions && isCBCMSAirMunition(entity)) {
                    cookOffMissile(serverLevel, entity);
                } else if (isCookOffLauncherEntity(entity)) {
                    cookOffLauncherEntity(serverLevel, entity);
                }
            }
            if (airMunitions && CompatConfig.TAOV_COOK_OFF.get()) {
                double taovHalf = taovBlastHalf(radius);
                CBCMSMWCompat.LOGGER.info(
                        "[cbcmsmwcompat] taov sweep from explosion start: radius {} half {} center {}",
                        radius, taovHalf, worldCenter);
                TaovCompat.detonateAirMunitionsIn(serverLevel,
                        AABB.ofSize(worldCenter, taovHalf * 2.0, taovHalf * 2.0, taovHalf * 2.0));
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

        // Storages that survive the blast but are still inside the blast radius also
        // cook off, judged by their distance to the center (see cookOffInBlast).
        // Sub-level storages are enumerated in plot coordinates as well so chain cook
        // offs work inside and between Sable structures.
        double radius = explosion.radius();
        Vec3 worldCenter = SableCompat.projectOutOfSubLevel(serverLevel, explosion.center());
        if (radius > 0.0 && radius <= 24.0) {
            candidates.addAll(spherePositions(worldCenter, radius));
        }

        for (BlockPos pos : candidates) {
            cookOffInBlast(serverLevel, explosion, worldCenter, pos);
        }
        if (radius > 0.0 && radius <= 24.0
                && CompatConfig.SABLE_COOK_OFF.get() && SableCompat.isSableLoaded()) {
            SableCompat.forEachBlockInBlast(serverLevel, worldCenter, radius,
                    (blastLevel, pos) -> cookOffInBlast(blastLevel, explosion, worldCenter, pos));
        }

        for (Entity entity : event.getAffectedEntities()) {
            if (entity.isRemoved()) {
                continue;
            }
            if (isCookOffMissile(entity)) {
                cookOffMissile(serverLevel, entity);
            } else if (CompatConfig.AIR_MUNITION_COOK_OFF.get() && isCBCMSAirMunition(entity)) {
                cookOffMissile(serverLevel, entity);
            } else if (isCookOffLauncherEntity(entity)) {
                cookOffLauncherEntity(serverLevel, entity);
            }
        }
        if (CompatConfig.AIR_MUNITION_COOK_OFF.get() && CompatConfig.TAOV_COOK_OFF.get()) {
            double taovHalf = taovBlastHalf(radius);
            CBCMSMWCompat.LOGGER.info(
                    "[cbcmsmwcompat] taov sweep from explosion detonate: radius {} half {} center {}",
                    radius, taovHalf, worldCenter);
            TaovCompat.detonateAirMunitionsIn(serverLevel,
                    AABB.ofSize(worldCenter, taovHalf * 2.0, taovHalf * 2.0, taovHalf * 2.0));
        }

    }

    /**
     * Half-size of the box in which taov air munitions cook off from a blast. Taov
     * projectiles are not entities and move several blocks per tick, so the nominal
     * explosion radius (the shell's power) is far too small to catch them; use a
     * generous box instead, capped to keep chain reactions bounded.
     */
    private static double taovBlastHalf(double radius) {
        return Math.min(24.0, Math.max(radius * 2.0, 6.0));
    }

    /**
     * Cooks off a storage caught in an explosion blast. The chance grows the closer the
     * storage sits to the center of the explosion: inside cook_off.blastGuaranteedRadius
     * (half a block by default) it always detonates, further out the chance falls off
     * towards cook_off.blastMinChance at the edge of the blast. Every storage rolls at
     * most once per explosion, so the start and the detonate event cannot roll twice.
     */
    private static void cookOffInBlast(ServerLevel level, Explosion explosion, Vec3 worldCenter, BlockPos pos) {
        if (!canCookOff(level, pos, false)) {
            return;
        }
        Vec3 blockCenter = SableCompat.projectOutOfSubLevel(level, Vec3.atCenterOf(pos));
        double chance = blastCookOffChance(blockCenter.distanceTo(worldCenter), explosion.radius());
        if (chance >= 1.0 || (claimBlastRoll(explosion, pos) && level.random.nextDouble() < chance)) {
            triggerCookOff(level, pos);
        }
    }

    /** Cook off chance of a storage at the given distance from the center of a blast. */
    private static double blastCookOffChance(double distance, double radius) {
        double guaranteed = CompatConfig.BLAST_COOK_OFF_GUARANTEED_RADIUS.get();
        double minChance = CompatConfig.BLAST_COOK_OFF_MIN_CHANCE.get();
        if (distance <= guaranteed || minChance >= 1.0) {
            return 1.0;
        }
        double span = Math.max(radius - guaranteed, 1.0E-3);
        double fallOff = Mth.clamp((radius - distance) / span, 0.0, 1.0);
        return minChance + (1.0 - minChance) * fallOff * fallOff;
    }

    /** True the first time a storage rolls its blast cook off chance for this explosion. */
    private static boolean claimBlastRoll(Explosion explosion, BlockPos pos) {
        return BLAST_ROLLS.computeIfAbsent(explosion, key -> new HashSet<>()).add(pos.immutable());
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
