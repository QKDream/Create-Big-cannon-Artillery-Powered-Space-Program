package com.qkdream.cbcmsmwcompat.ammorack;

import com.cainiao1053.cbcmoreshells.blocks.ammo_rack.AmmoRackBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import com.simibubi.create.foundation.item.SmartInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import riftyboi.cbcmodernwarfare.munitions.medium_cannon.MediumcannonAmmoItem;
import riftyboi.cbcmodernwarfare.munitions.medium_cannon.MediumcannonRoundItem;
import rbasamoyai.createbigcannons.munitions.big_cannon.ProjectileBlockItem;
import rbasamoyai.createbigcannons.munitions.big_cannon.propellant.BigCartridgeBlockItem;

public class RackCompatInteractionPoint extends ArmInteractionPoint {

    public RackCompatInteractionPoint(ArmInteractionPointType type, Level level, BlockPos pos, BlockState state) {
        super(type, level, pos, state);
        this.mode = Mode.TAKE;
    }

    @Override
    public ItemStack extract(ArmBlockEntity armBlockEntity, int slot, int amount, boolean simulate) {
        if (!(this.level.getBlockEntity(this.pos) instanceof AmmoRackBlockEntity rack)) {
            return ItemStack.EMPTY;
        }
        SmartInventory inventory = rack.getInventory();
        ItemStack stack = inventory.getStackInSlot(slot);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Item item = stack.getItem();
        // CBC propellant cartridges bypass the rack filter, mirroring CBCMS behaviour.
        if (item instanceof BigCartridgeBlockItem) {
            return inventory.extractItem(slot, amount, simulate);
        }
        // Projectile rounds (CBC and CBCMW) and CBCMW cartridges respect the rack
        // filter so the redstone shell selection picks the type the arm is allowed to take.
        if (item instanceof ProjectileBlockItem
                || item instanceof MediumcannonRoundItem
                || item instanceof MediumcannonAmmoItem) {
            return inventory.isItemValid(slot, stack) ? inventory.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void cycleMode() {
    }

    @Override
    public ItemStack insert(ArmBlockEntity armBlockEntity, ItemStack stack, boolean simulate) {
        return stack;
    }
}

