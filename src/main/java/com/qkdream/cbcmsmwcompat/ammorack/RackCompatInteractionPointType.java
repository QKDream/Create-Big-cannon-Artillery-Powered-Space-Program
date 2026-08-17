package com.qkdream.cbcmsmwcompat.ammorack;

import com.cainiao1053.cbcmoreshells.blocks.ammo_rack.AmmoRackBlockEntity;
import com.qkdream.cbcmsmwcompat.config.CompatConfig;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class RackCompatInteractionPointType extends ArmInteractionPointType {

    public static final RackCompatInteractionPointType INSTANCE = new RackCompatInteractionPointType();

    private RackCompatInteractionPointType() {
    }

    @Override
    public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
        return CompatConfig.ARM_LOADING_FIX.get()
                && RackCompatUtil.isAmmoRack(state)
                && level.getBlockEntity(pos) instanceof AmmoRackBlockEntity;
    }

    @Override
    public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
        return new RackCompatInteractionPoint(this, level, pos, state);
    }

    // Must beat the CBCMS default priority (0) so mechanical arms use this point.
    @Override
    public int getPriority() {
        return 1000;
    }
}