package com.qkdream.cbcmsmwcompat.mixin;

import com.qkdream.cbcmsmwcompat.ammorack.CookOffHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks the fragment (spall) code of CBC Terminal Ballistics. That mod does not spawn
 * fragment entities: it casts one ray per fragment and applies the damage itself, so its
 * fragments never reach the entity sweep this mod uses for shrapnel bursts. Every block
 * one of its rays hits is reported here instead, which lets the fragments cook off the
 * ammo storage they hit.
 *
 * Only the spall path is hooked; the direct projectile impact handled in the same class
 * keeps its plain terminal ballistics behaviour.
 */
@Mixin(targets = "com.cbc_terminal_ballistics.ballistics.TBImpactService", remap = false)
public class TerminalBallisticsSpallMixin {

    /** Number of spall calls this thread is currently inside of. */
    @Unique
    private static int cbcmsmwcompatSpallDepth;

    @Inject(method = "spawnSpall", at = @At("HEAD"), require = 0)
    private static void cbcmsmwcompatEnterSpall(CallbackInfoReturnable<Integer> cir) {
        cbcmsmwcompatSpallDepth++;
    }

    @Inject(method = "spawnSpall", at = @At("RETURN"), require = 0)
    private static void cbcmsmwcompatLeaveSpall(CallbackInfoReturnable<Integer> cir) {
        if (cbcmsmwcompatSpallDepth > 0) {
            cbcmsmwcompatSpallDepth--;
        }
    }

    @Inject(method = "triggerExplosiveBlockHit", at = @At("HEAD"), cancellable = true, require = 0)
    private static void cbcmsmwcompatFragmentBlockHit(ServerLevel level, BlockPos pos, BlockState state,
            Direction face, CallbackInfoReturnable<Boolean> cir) {
        if (cbcmsmwcompatSpallDepth > 0 && CookOffHandler.onFragmentBlockHit(level, pos)) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}
