package com.qkdream.cbcmsmwcompat.ammorack;

import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Detects mianbaos_modernwarfare (面包学) content without a hard dependency.
 * The mod is an MCreator mod whose classes live under net.mcreator.myfirstmod, so
 * launchers and missiles are recognised by class-name patterns.
 */
public final class MianbaosCompatUtil {

    private static final String BLOCK_ENTITY_PREFIX = "net.mcreator.myfirstmod.block.entity.";
    private static final String ENTITY_PREFIX = "net.mcreator.myfirstmod.entity.";

    /**
     * Munitions that may cook off in flight. Every projectile of 面包学's is named
     * "<thing>tanshe" - the bombs, JDAM and glide bombs share that word with the cruise and
     * the guided missiles - but so does every round its guns fire. Only a whole munition
     * that carries a warhead of its own counts.
     */
    private static final String[] COOK_OFF_MUNITION_MARKERS = {
            "missile", "rocket", "bomb", "torpedo", "jdam", "agm"
    };

    /**
     * Small arms and the debris a warhead throws out. The rounds of the autocannon, the
     * heavy machine gun, the gun turret, the CIWS, the flamethrower, the mortar and the
     * grenade cannon are ammunition for a gun, not a warhead, so they never cook off -
     * even when the class name also carries a munition word (RocketShrapnelEntity).
     */
    private static final String[] NON_COOK_OFF_MARKERS = {
            "autocannon", "machinegun", "gatling", "gattling", "gunturret", "ciws",
            "fireball", "flame", "mortar", "grenade", "shrapnel", "fragment",
            "c4", "arrow", "bullet", "smoke", "countermeasure", "drone"
    };

    /**
     * Launchers made of several blocks (VLS, rocket artillery, torpedo tubes) keep the
     * rounds in one part only, so neighbouring blocks are searched for the loaded part.
     */
    private static final int NEIGHBOUR_RADIUS = 2;

    private MianbaosCompatUtil() {
    }

    /** Missile and rocket launchers. Mounted munitions are handled by isMountedMunition. */
    public static boolean isLauncher(BlockEntity blockEntity) {
        if (blockEntity == null) {
            return false;
        }
        if (!isMianbaosBlockEntity(blockEntity)) {
            return false;
        }
        String simple = blockEntity.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (simple.contains("rack")) {
            return false;
        }
        return simple.contains("missile") || simple.contains("rocket");
    }

    /**
     * Munitions carried on a pylon: air to air and air to ground missiles, cruise
     * missiles, guided bombs and torpedoes. The block itself is the round, so a loaded
     * rack cooks off while an empty one does not.
     */
    public static boolean isMountedMunition(BlockEntity blockEntity) {
        if (blockEntity == null) {
            return false;
        }
        if (!isMianbaosBlockEntity(blockEntity)) {
            return false;
        }
        String simple = blockEntity.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return simple.contains("rack");
    }

    /** True when the block is a mianbaos launcher or a munition mounted on a pylon. */
    public static boolean isCookOffBlock(BlockEntity blockEntity) {
        return isLauncher(blockEntity) || isMountedMunition(blockEntity);
    }

    private static boolean isMianbaosBlockEntity(BlockEntity blockEntity) {
        return blockEntity.getClass().getName().startsWith(BLOCK_ENTITY_PREFIX);
    }

    /**
     * True when the launcher or rack still holds a round. Mianbaos does not keep the
     * loaded missile in the block inventory: the reload procedures write ammo counters
     * (missileammo1, missile1ammo, loadmissile, ammo, ...) into the block entity's
     * persistent data, so those counters decide whether a launcher is loaded. Racks
     * keep the round as an item, which the inventory check covers.
     */
    public static boolean hasLoadableRound(BlockEntity blockEntity) {
        if (blockEntity == null) {
            return false;
        }
        if (hasLoadedAmmo(blockEntity.getPersistentData())) {
            return true;
        }
        if (blockEntity instanceof Container container) {
            return !container.isEmpty();
        }
        return false;
    }

    /** Reads the ammo counters mianbaos writes into the block entity persistent data. */
    private static boolean hasLoadedAmmo(CompoundTag data) {
        if (data == null || data.isEmpty()) {
            return false;
        }
        for (String key : data.getAllKeys()) {
            String lower = key.toLowerCase(Locale.ROOT);
            // "ammotype" selects which shell a turret uses; unloaded launchers set it too.
            if (lower.endsWith("type")) {
                continue;
            }
            if (!lower.contains("ammo") && !lower.startsWith("loadmissile") && !lower.equals("loaded")) {
                continue;
            }
            if (data.getDouble(key) >= 1.0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the loaded launcher or rack block to cook off for a hit at pos, or null
     * when nothing loaded is there. Neighbouring blocks are searched because
     * multi-block launchers store their rounds in a single part.
     */
    public static BlockPos findLoadedCookOffBlock(Level level, BlockPos pos) {
        if (isLoadedCookOffBlock(level, pos)) {
            return pos;
        }
        for (BlockPos candidate : BlockPos.betweenClosed(
                pos.offset(-NEIGHBOUR_RADIUS, -NEIGHBOUR_RADIUS, -NEIGHBOUR_RADIUS),
                pos.offset(NEIGHBOUR_RADIUS, NEIGHBOUR_RADIUS, NEIGHBOUR_RADIUS))) {
            if (isLoadedCookOffBlock(level, candidate)) {
                return candidate.immutable();
            }
        }
        return null;
    }

    private static boolean isLoadedCookOffBlock(Level level, BlockPos pos) {
        if (!level.getBlockState(pos).hasBlockEntity()) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return isCookOffBlock(blockEntity) && hasLoadableRound(blockEntity);
    }

    /**
     * Whole munitions in flight: the ones that carry a warhead of their own and therefore
     * cook off when they are hit (missiles, rockets, bombs, JDAM/glide bombs, torpedoes).
     */
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
        if (living) {
            // Launch platforms (missile turrets, rocket carriers) are whole weapons rather
            // than rounds: they keep the original missile/rocket match.
            return isLauncherPlatformName(simple);
        }
        return isCookOffMunition(simple);
    }

    /** True when the class name belongs to a launcher platform rather than to a round. */
    static boolean isLauncherPlatformName(String simple) {
        return !isSmallArmOrFragment(simple)
                && (simple.contains("missile") || simple.contains("rocket"));
    }

    /**
     * True when the class name belongs to a munition that carries its own warhead. Matching
     * on "tanshe" alone (2.1.3) made every cannon round, machine gun tracer, flame ball,
     * mortar shell and grenade a cook off source: one CBC shell passing a burst of cannon
     * fire lit up the whole stream, each round queued its own explosion burst, each burst
     * found the next rounds, and the resulting chain of explosions froze the server.
     */
    private static boolean isCookOffMunition(String simple) {
        if (isSmallArmOrFragment(simple)) {
            return false;
        }
        for (String marker : COOK_OFF_MUNITION_MARKERS) {
            if (simple.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSmallArmOrFragment(String simple) {
        for (String marker : NON_COOK_OFF_MARKERS) {
            if (simple.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}