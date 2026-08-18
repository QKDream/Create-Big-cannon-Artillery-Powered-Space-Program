package com.qkdream.cbcmsmwcompat.mixin;

import com.qkdream.cbcmsmwcompat.ammorack.CookOffHandler;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.ProjectileContext;

/**
 * Hooks the universal block-impact path of Create Big Cannons projectiles. The
 * penetration call is intercepted (the INVOKE inject callback cannot carry the
 * parameterised call signature) so the exact hit position is available: an ammo
 * rack or depot hit this way always cooks off first, even when the projectile
 * itself would otherwise just destroy the block.
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

    @Invoker("calculateBlockPenetration")
    abstract AbstractCannonProjectile.ImpactResult cbcmsmwcompat$invokeCalculateBlockPenetration(
            ProjectileContext context, BlockState state, BlockHitResult hitResult);
}