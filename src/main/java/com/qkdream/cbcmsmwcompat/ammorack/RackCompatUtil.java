package com.qkdream.cbcmsmwcompat.ammorack;

import com.cainiao1053.cbcmoreshells.CBCMSBlocks;
import com.cainiao1053.cbcmoreshells.blocks.ammo_rack.AmmoRackBlockEntity;
import com.qkdream.cbcmsmwcompat.CBCMSMWCompat;
import com.qkdream.cbcmsmwcompat.config.CompatConfig;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.foundation.item.SmartInventory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.munitions.big_cannon.ProjectileBlockItem;
import rbasamoyai.createbigcannons.munitions.big_cannon.propellant.BigCartridgeBlockItem;
import rbasamoyai.createbigcannons.munitions.big_cannon.propellant.PowderChargeItem;
import riftyboi.cbcmodernwarfare.munitions.medium_cannon.MediumcannonAmmoItem;
import riftyboi.cbcmodernwarfare.munitions.medium_cannon.MediumcannonRoundItem;

public final class RackCompatUtil {

    private RackCompatUtil() {
    }

    /** Ammunition that cooks off and can be handed to loading arms. */
    public static boolean isLoadableAmmo(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof ProjectileBlockItem
                || item instanceof BigCartridgeBlockItem
                || item instanceof PowderChargeItem
                || item instanceof MediumcannonAmmoItem
                || item instanceof MediumcannonRoundItem;
    }

    public static boolean isAmmoRack(BlockState state) {
        return CBCMSBlocks.AMMO_RACK.has(state) || CBCMSBlocks.STEEL_AMMO_RACK.has(state);
    }

    public static boolean isAmmoRack(Level level, BlockPos pos) {
        return isAmmoRack(level.getBlockState(pos));
    }

    /** True when the rack holds ammunition that can actually cook off. */
    public static boolean hasCookOffAmmo(Level level, AmmoRackBlockEntity rack) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        SmartInventory inventory = rack.getInventory();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty() && CookOffYield.of(stack, serverLevel.getServer()).weight() > 0.0) {
                return true;
            }
        }
        return false;
    }

    /** A Create depot (置物台) whose held item is CBC-family ammunition. */
    public static boolean isDepotWithAmmo(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof DepotBlockEntity depot)) {
            return false;
        }
        ItemStack held = depot.getHeldItem();
        return !held.isEmpty() && isLoadableAmmo(held);
    }

    /**
     * Starts a cook off: the stored ammunition is converted into a yield (shell types
     * and quantity decide the explosion power, up to the configured maximum), then it is
     * consumed (no drops, no self re-trigger) and a burst of explosions is queued.
     * Returns false and does nothing when the rack holds no cook-off-capable ammunition
     * (inert AP/APFSDS/mortar stone warheads never cook off).
     */
    public static boolean cookOff(Level level, BlockPos pos, AmmoRackBlockEntity rack) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        SmartInventory inventory = rack.getInventory();
        double totalWeight = 0.0;
        boolean smoke = false;
        boolean fire = false;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            CookOffYield yield = CookOffYield.of(stack, serverLevel.getServer());
            if (yield.weight() > 0.0) {
                totalWeight += yield.weight() * stack.getCount();
                smoke |= yield.smoke();
                fire |= yield.fire();
            }
        }
        if (totalWeight <= 0.0) {
            return false;
        }
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
        rack.setChanged();
        CookOffHandler.scheduleCookOffExplosions(
                serverLevel, Vec3.atCenterOf(pos), CookOffYield.powerScale(totalWeight), smoke, fire);
        if (CompatConfig.DEBUG_LOGGING.get()) {
            CBCMSMWCompat.LOGGER.info("[cbcmsmwcompat] Cook off: ammo rack at {} with yield {}",
                    pos, String.format("%.2f", totalWeight));
        }
        return true;
    }

    /** Cooks off a Create depot holding CBC-family ammunition. */
    public static boolean cookOffDepot(Level level, BlockPos pos, DepotBlockEntity depot) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        ItemStack held = depot.getHeldItem();
        CookOffYield yield = held.isEmpty()
                ? CookOffYield.NONE
                : CookOffYield.of(held, serverLevel.getServer());
        if (yield.weight() <= 0.0) {
            return false;
        }
        double totalWeight = yield.weight() * held.getCount();
        depot.clearContent();
        CookOffHandler.scheduleCookOffExplosions(
                serverLevel, Vec3.atCenterOf(pos), CookOffYield.powerScale(totalWeight), yield.smoke(), yield.fire());
        if (CompatConfig.DEBUG_LOGGING.get()) {
            CBCMSMWCompat.LOGGER.info("[cbcmsmwcompat] Cook off: depot at {} with yield {}",
                    pos, String.format("%.2f", totalWeight));
        }
        return true;
    }

    /**
     * Merges the rack's selectable projectile rounds by item (ignoring NBT), preserving
     * slot order, exactly like CBCMS's mergeStacksIgnoreNBT but also covering CBCMW
     * medium cannon projectile rounds. Used by the redstone filter switching mixin.
     */
    public static Map<ItemStack, Integer> mergeSelectableAmmoIgnoreNBT(SmartInventory inv) {
        Map<ItemStack, Integer> merged = new LinkedHashMap<>();
        for (int slot = 0; slot < inv.getSlots(); slot++) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof ProjectileBlockItem
                    || stack.getItem() instanceof MediumcannonRoundItem
                    || stack.getItem() instanceof MediumcannonAmmoItem)) {
                continue;
            }
            Optional<Map.Entry<ItemStack, Integer>> same = merged.entrySet()
                    .stream()
                    .filter(entry -> ItemStack.isSameItem(stack, entry.getKey()))
                    .findFirst();
            if (same.isPresent()) {
                same.get().setValue(same.get().getValue() + stack.getCount());
            } else {
                merged.put(stack.copyWithCount(1), stack.getCount());
            }
        }
        return merged;
    }
}