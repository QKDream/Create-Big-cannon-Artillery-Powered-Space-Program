package com.qkdream.cbcmsmwcompat.mixin;

import com.qkdream.cbcmsmwcompat.ammorack.CookOffHandler;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.ProjectileContext;

/**
 * Hooks Create Big Cannons projectiles for ammo cook offs:
 *
 * 1. The universal block-impact path is intercepted (the INVOKE inject callback cannot
 *    carry the parameterised call signature) so the exact hit position is available.
 * 2. After every projectile tick the mod performs its own contact check plus a swept
 *    segment check between the previous and current position. CBC projectiles use their
 *    own collision pipeline and never fire vanilla projectile impact events, so a
 *    projectile that destroys a rack without calling the penetration path still cooks
 *    the rack off the moment it touches it.
 */
@Mixin(AbstractCannonProjectile.class)
public abstract class AbstractCannonProjectileMixin {

    @Redirect(method = "clipAndDamage", at = @At(value = "INVOKE",
            target = "Lrbasamoyai/createbigcannons/munitions/AbstractCannonProjectile;calculateBlockPenetration("
                    + "Lrbasamoyai/createbigcannons/munitions/ProjectileContext;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;"
                    + "Lnet/minecraft/world/phys/BlockHitResult;)"
                    + "Lrbasamoyai/createbigcannons/munitions/AbstractCannonProjectile$ImpactResult;"))
    private AbstractCannonProjectile.ImpactResult cbcmsmwcompat$beforeBlockImpact(
            AbstractCannonProjectile projectile, ProjectileContext context, BlockState state, BlockHitResult hitResult) {
        if (!projectile.level().isClientSide()) {
            CookOffHandler.onProjectileBlockHit(projectile.level(), hitResult.getBlockPos());
        }
        return cbcmsmwcompat$invokeCalculateBlockPenetration(context, state, hitResult);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void cbcmsmwcompat$afterTick(CallbackInfo ci) {
        AbstractCannonProjectile projectile = (AbstractCannonProjectile) (Object) this;
        if (!projectile.level().isClientSide()) {
            CookOffHandler.onProjectileTick(projectile);
        }
    }

    @Invoker("calculateBlockPenetration")
    abstract AbstractCannonProjectile.ImpactResult cbcmsmwcompat$invokeCalculateBlockPenetration(
            ProjectileContext context, BlockState state, BlockHitResult hitResult);
}