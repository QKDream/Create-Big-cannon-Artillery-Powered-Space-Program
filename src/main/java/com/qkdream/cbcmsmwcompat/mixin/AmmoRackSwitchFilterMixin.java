package com.qkdream.cbcmsmwcompat.mixin;

import com.cainiao1053.cbcmoreshells.blocks.ammo_rack.AmmoRackBlockEntity;
import com.qkdream.cbcmsmwcompat.ammorack.RackCompatUtil;
import com.simibubi.create.foundation.item.SmartInventory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AmmoRackBlockEntity.class)
public abstract class AmmoRackSwitchFilterMixin {

    @Shadow
    private int index;

    /**
     * CBCMS's switchFilter only cycles Create Big Cannons projectiles, so the redstone
     * shell selection never lands on CBC Modern Warfare medium cannon ammunition. This
     * replaces the cycle with one that also includes CBCMW projectile rounds and
     * cartridges.
     */
    @Inject(method = "switchFilter", at = @At("HEAD"), cancellable = true)
    private void cbcmsmwcompat$switchFilter(CallbackInfo ci) {
        AmmoRackBlockEntity self = (AmmoRackBlockEntity) (Object) this;
        SmartInventory inventory = self.getInventory();
        if (inventory.isEmpty()) {
            ci.cancel();
            return;
        }
        Map<ItemStack, Integer> merged = RackCompatUtil.mergeSelectableAmmoIgnoreNBT(inventory);
        if (merged.isEmpty()) {
            ci.cancel();
            return;
        }
        int size = merged.size();
        if (this.index < size - 1) {
            this.index++;
        } else {
            this.index = 0;
        }
        List<Map.Entry<ItemStack, Integer>> entries = new ArrayList<>(merged.entrySet());
        self.getFilter().setFilter(entries.get(this.index).getKey());
        ci.cancel();
    }
}
