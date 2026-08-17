package com.qkdream.cbcmsmwcompat.ammorack;

import com.cainiao1053.cbcmoreshells.CBCMSBlocks;
import com.cainiao1053.cbcmoreshells.blocks.ammo_rack.AmmoRackBlockEntity;
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
import rbasamoyai.createbigcannons.munitions.big_cannon.ProjectileBlockItem;
import rbasamoyai.createbigcannons.munitions.big_cannon.propellant.BigCartridgeBlockItem;
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
                || item instanceof MediumcannonAmmoItem
                || item instanceof MediumcannonRoundItem;
    }

    public static boolean isAmmoRack(BlockState state) {
        return CBCMSBlocks.AMMO_RACK.has(state) || CBCMSBlocks.STEEL_AMMO_RACK.has(state);
    }

    public static boolean isAmmoRack(Level level, BlockPos pos) {
        return isAmmoRack(level.getBlockState(pos));
    }

    public static boolean hasAmmo(AmmoRackBlockEntity rack) {
        SmartInventory inventory = rack.getInventory();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty() && isLoadableAmmo(stack)) {
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
     * Starts a cook off: the ammunition is consumed first (no drops, no self re-trigger),
     * then a burst of explosions is scheduled on the cook off queue.
     */
    public static void cookOff(Level level, BlockPos pos, AmmoRackBlockEntity rack) {
        SmartInventory inventory = rack.getInventory();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
        rack.setChanged();
        if (level instanceof ServerLevel serverLevel) {
            CookOffHandler.scheduleCookOffExplosions(serverLevel, pos);
        }
    }

    /** Cooks off a Create depot holding CBC-family ammunition. */
    public static void cookOffDepot(Level level, BlockPos pos, DepotBlockEntity depot) {
        depot.clearContent();
        if (level instanceof ServerLevel serverLevel) {
            CookOffHandler.scheduleCookOffExplosions(serverLevel, pos);
        }
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

