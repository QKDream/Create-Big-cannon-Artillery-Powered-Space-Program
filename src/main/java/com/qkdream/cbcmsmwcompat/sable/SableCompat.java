package com.qkdream.cbcmsmwcompat.sable;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * Optional Sable integration. Every Sable API reference lives in this class so the
 * rest of the mod only depends on it; all calls are guarded and must never break
 * cook offs when Sable is absent or misbehaving.
 *
 * Sable stores sub-level blocks in "plots": reserved regions of the main world. The
 * sub-level's logical pose maps plot coordinates (where the blocks physically live)
 * to the structure's displayed world position. Positions reported by block hits or
 * block entities inside a sub-level are therefore plot coordinates and must be
 * projected out to world space before creating an explosion, otherwise the blast
 * damages the plot area instead of the structure's displayed location, the main
 * world around it and neighbouring structures.
 */
public final class SableCompat {

    private SableCompat() {
    }

    public static boolean isSableLoaded() {
        return ModList.get().isLoaded("sable");
    }

    /** Maps a plot position inside a sub-level to the structure's world position. */
    public static Vec3 projectOutOfSubLevel(Level level, Vec3 pos) {
        if (!isSableLoaded()) {
            return pos;
        }
        try {
            return Sable.HELPER.projectOutOfSubLevel(level, pos);
        } catch (Throwable t) {
            return pos;
        }
    }

    /**
     * Destroys blocks of every sub-level whose displayed position intersects the blast
     * sphere, following the same explosion resistance rule Sable itself uses. Drops are
     * popped at the structure's displayed world position.
     */
    public static void damageStructuresInBlast(ServerLevel level, Vec3 worldCenter, double radius) {
        if (radius <= 0.0) {
            return;
        }
        try {
            BoundingBox3d bounds = new BoundingBox3d(
                    worldCenter.x - radius, worldCenter.y - radius, worldCenter.z - radius,
                    worldCenter.x + radius, worldCenter.y + radius, worldCenter.z + radius);
            for (SubLevel subLevel : Sable.HELPER.getAllIntersecting(level, bounds)) {
                if (!subLevel.isRemoved()) {
                    damageSubLevel(level, subLevel, worldCenter, radius);
                }
            }
        } catch (Throwable t) {
            // A broken integration must never break cook off explosions.
        }
    }

    /**
     * Invokes the action for every sub-level block inside the blast sphere, passing
     * plot coordinates (main level block positions where the storage lives). Used by
     * the blast handlers so chain cook offs keep working for storages stored inside
     * sub-levels now that explosions are projected to world space.
     */
    public static void forEachBlockInBlast(ServerLevel level, Vec3 worldCenter, double radius,
            BiConsumer<ServerLevel, BlockPos> action) {
        if (!isSableLoaded() || radius <= 0.0) {
            return;
        }
        try {
            BoundingBox3d bounds = new BoundingBox3d(
                    worldCenter.x - radius, worldCenter.y - radius, worldCenter.z - radius,
                    worldCenter.x + radius, worldCenter.y + radius, worldCenter.z + radius);
            for (SubLevel subLevel : Sable.HELPER.getAllIntersecting(level, bounds)) {
                if (subLevel.isRemoved()) {
                    continue;
                }
                Pose3d pose = subLevel.logicalPose();
                LevelPlot plot = subLevel.getPlot();
                if (plot == null) {
                    continue;
                }
                Vec3 localMin = pose.transformPositionInverse(new Vec3(
                        worldCenter.x - radius, worldCenter.y - radius, worldCenter.z - radius));
                Vec3 localMax = pose.transformPositionInverse(new Vec3(
                        worldCenter.x + radius, worldCenter.y + radius, worldCenter.z + radius));
                int minX = floor(Math.min(localMin.x, localMax.x));
                int minY = floor(Math.min(localMin.y, localMax.y));
                int minZ = floor(Math.min(localMin.z, localMax.z));
                int maxX = floor(Math.max(localMin.x, localMax.x));
                int maxY = floor(Math.max(localMin.y, localMax.y));
                int maxZ = floor(Math.max(localMin.z, localMax.z));

                BoundingBox3ic plotBounds = plot.getBoundingBox();
                minX = Math.max(minX, plotBounds.minX());
                minY = Math.max(minY, plotBounds.minY());
                minZ = Math.max(minZ, plotBounds.minZ());
                maxX = Math.min(maxX, plotBounds.maxX());
                maxY = Math.min(maxY, plotBounds.maxY());
                maxZ = Math.min(maxZ, plotBounds.maxZ());

                double radiusSqr = radius * radius;
                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            BlockPos localPos = new BlockPos(x, y, z);
                            if (pose.transformPosition(localPos.getCenter()).distanceToSqr(worldCenter) > radiusSqr) {
                                continue;
                            }
                            action.accept(level, localPos);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // A broken integration must never break cook off explosions.
        }
    }

    private static void damageSubLevel(ServerLevel level, SubLevel subLevel, Vec3 worldCenter, double radius) {
        Pose3d pose = subLevel.logicalPose();
        LevelPlot plot = subLevel.getPlot();
        if (plot == null) {
            return;
        }
        Vec3 localMin = pose.transformPositionInverse(new Vec3(
                worldCenter.x - radius, worldCenter.y - radius, worldCenter.z - radius));
        Vec3 localMax = pose.transformPositionInverse(new Vec3(
                worldCenter.x + radius, worldCenter.y + radius, worldCenter.z + radius));
        int minX = floor(Math.min(localMin.x, localMax.x));
        int minY = floor(Math.min(localMin.y, localMax.y));
        int minZ = floor(Math.min(localMin.z, localMax.z));
        int maxX = floor(Math.max(localMin.x, localMax.x));
        int maxY = floor(Math.max(localMin.y, localMax.y));
        int maxZ = floor(Math.max(localMin.z, localMax.z));

        BoundingBox3ic plotBounds = plot.getBoundingBox();
        minX = Math.max(minX, plotBounds.minX());
        minY = Math.max(minY, plotBounds.minY());
        minZ = Math.max(minZ, plotBounds.minZ());
        maxX = Math.min(maxX, plotBounds.maxX());
        maxY = Math.min(maxY, plotBounds.maxY());
        maxZ = Math.min(maxZ, plotBounds.maxZ());

        double radiusSqr = radius * radius;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos localPos = new BlockPos(x, y, z);
                    Vec3 worldPos = pose.transformPosition(localPos.getCenter());
                    if (worldPos.distanceToSqr(worldCenter) > radiusSqr) {
                        continue;
                    }
                    BlockState state = level.getBlockState(localPos);
                    if (state.isAir()) {
                        continue;
                    }
                    double power = radius - (state.getBlock().getExplosionResistance() + 0.3) * 0.3;
                    if (power > 0.0) {
                        destroySubLevelBlock(level, localPos, worldPos, state);
                    }
                }
            }
        }
    }

    private static void destroySubLevelBlock(ServerLevel level, BlockPos localPos, Vec3 worldPos, BlockState state) {
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(localPos) : null;
        List<ItemStack> drops = Block.getDrops(state, level, localPos, blockEntity);
        level.destroyBlock(localPos, false, null, 512);
        BlockPos dropPos = BlockPos.containing(worldPos);
        for (ItemStack drop : drops) {
            Block.popResource(level, dropPos, drop);
        }
    }

    private static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }
}