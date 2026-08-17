package com.qkdream.cbcmsmwcompat;


import com.qkdream.cbcmsmwcompat.ammorack.RackCompatInteractionPointType;
import com.qkdream.cbcmsmwcompat.config.CompatConfig;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(CBCMSMWCompat.MOD_ID)
public class CBCMSMWCompat {

    public static final String MOD_ID = "cbcmsmwcompat";

    public static final Logger LOGGER = LoggerFactory.getLogger(CBCMSMWCompat.class);

    private static boolean interactionPointRegistered = false;

    public CBCMSMWCompat(IEventBus modEventBus) {
        CompatConfig.register();

        // Create's custom registries are already frozen while mods are being
        // constructed, so the replacement interaction point must be registered
        // when RegisterEvent is dispatched - the same flow CBCMS uses for its
        // own ammo rack interaction point.
        modEventBus.addListener(this::onRegister);
    }

    private void onRegister(RegisterEvent event) {
        if (interactionPointRegistered) {
            return;
        }
        interactionPointRegistered = true;
        // Replaces CBCMS's ammo rack arm interaction point with one that also accepts
        // CBC Modern Warfare medium cannon ammunition. Higher priority wins in Create.
        Registry.register(CreateBuiltInRegistries.ARM_INTERACTION_POINT_TYPE,
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "ammo_rack"),
                RackCompatInteractionPointType.INSTANCE);
    }
}



