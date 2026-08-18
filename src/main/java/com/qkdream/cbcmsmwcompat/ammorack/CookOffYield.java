package com.qkdream.cbcmsmwcompat.ammorack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.qkdream.cbcmsmwcompat.config.CompatConfig;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import rbasamoyai.createbigcannons.munitions.big_cannon.ProjectileBlockItem;
import rbasamoyai.createbigcannons.munitions.big_cannon.propellant.BigCartridgeBlockItem;
import rbasamoyai.createbigcannons.munitions.big_cannon.propellant.PowderChargeItem;
import riftyboi.cbcmodernwarfare.munitions.medium_cannon.MediumcannonAmmoItem;
import riftyboi.cbcmodernwarfare.munitions.medium_cannon.MediumcannonRoundItem;
import riftyboi.cbcmodernwarfare.munitions.medium_cannon.aphe.APHEMediumcannonCartridgeItem;
import riftyboi.cbcmodernwarfare.munitions.medium_cannon.aphe.APHEMediumcannonRoundItem;
import riftyboi.cbcmodernwarfare.munitions.medium_cannon.he.HEMediumcannonCartridgeItem;
import riftyboi.cbcmodernwarfare.munitions.medium_cannon.he.HEMediumcannonRoundItem;
import riftyboi.cbcmodernwarfare.munitions.medium_cannon.heap.HEAPMediumcannonCartridgeItem;
import riftyboi.cbcmodernwarfare.munitions.medium_cannon.heap.HEAPMediumcannonRoundItem;
import riftyboi.cbcmodernwarfare.munitions.medium_cannon.hefrag.HEFMediumcannonCartridgeItem;
import riftyboi.cbcmodernwarfare.munitions.medium_cannon.hefrag.HEFMediumcannonRoundItem;
import riftyboi.cbcmodernwarfare.munitions.medium_cannon.smoke.SmokeMediumcannonCartridgeItem;
import riftyboi.cbcmodernwarfare.munitions.medium_cannon.smoke.SmokeMediumcannonRoundItem;

/**
 * Cook off yield of one unit of ammunition: how much explosive power it adds and
 * which special effects (smoke, incendiary fire) it releases when it detonates.
 * Non-explosive warheads (AP shots, APFSDS, mortar stone, solid shot...) have zero
 * weight and do not cook off at all.
 */
public final class CookOffYield {

    public static final CookOffYield NONE = new CookOffYield(0.0, false, false);

    /** Cached data-driven projectile property files, keyed by block id. */
    private static final Map<ResourceLocation, Optional<JsonObject>> PROPERTIES_CACHE = new HashMap<>();

    private final double weight;
    private final boolean smoke;
    private final boolean fire;

    public CookOffYield(double weight, boolean smoke, boolean fire) {
        this.weight = weight;
        this.smoke = smoke;
        this.fire = fire;
    }

    public double weight() {
        return this.weight;
    }

    public boolean smoke() {
        return this.smoke;
    }

    public boolean fire() {
        return this.fire;
    }

    public CookOffYield merge(CookOffYield other) {
        return new CookOffYield(
                this.weight + other.weight,
                this.smoke || other.smoke,
                this.fire || other.fire);
    }

    /**
     * Converts a total yield weight into the explosion radius multiplier. The power
     * (energy) of an explosion grows with the cube of its radius, so a 16x yield
     * becomes a cuberoot(16) radius multiplier, capped at the configured maximum.
     */
    public static double powerScale(double totalWeight) {
        double multiplier = Mth.clamp(totalWeight, 1.0, CompatConfig.COOK_OFF_MAX_MULTIPLIER.get());
        return Math.cbrt(multiplier);
    }

    /** Yield of one item of the stack (the caller multiplies by the stack size). */
    public static CookOffYield of(ItemStack stack, MinecraftServer server) {
        if (stack.isEmpty()) {
            return NONE;
        }
        Item item = stack.getItem();

        // CBC Modern Warfare medium cannon ammunition always cooks off.
        if (item instanceof MediumcannonAmmoItem || item instanceof MediumcannonRoundItem) {
            double weight = isExplosiveMedium(item)
                    ? CompatConfig.COOK_OFF_WEIGHT_EXPLOSIVE.get()
                    : CompatConfig.COOK_OFF_WEIGHT_STANDARD.get();
            return new CookOffYield(weight, isSmokeMedium(item), false);
        }

        // Propellant: big cartridges and powder charges cook off like standard ammo.
        if (item instanceof BigCartridgeBlockItem || item instanceof PowderChargeItem) {
            return new CookOffYield(CompatConfig.COOK_OFF_WEIGHT_PROPELANT.get(), false, false);
        }

        // Create Big Cannons / CBC Military Supplement projectile blocks are data driven.
        if (item instanceof ProjectileBlockItem) {
            return ofBlockItem((BlockItem) item, server);
        }
        return NONE;
    }

    private static CookOffYield ofBlockItem(BlockItem item, MinecraftServer server) {
        Block block = item.getBlock();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null) {
            return NONE;
        }
        // The mortar stone is just a rock: no explosive filler, never cooks off.
        if ("mortar_stone".equals(id.getPath())) {
            return NONE;
        }
        JsonObject json = loadProperties(server, id);
        if (json == null) {
            return NONE;
        }
        boolean explosive = json.has("entity_damaging_explosive_power")
                || json.has("block_damaging_explosive_power")
                || json.has("explosive_power");
        boolean smoke = json.has("smoke_scale");
        boolean fire = json.has("fire_chance") || json.has("fire_range");
        if (!explosive && !smoke && !fire) {
            return NONE; // inert projectile (AP shot, APFSDS, solid shot, ...)
        }
        double weight = explosive
                ? CompatConfig.COOK_OFF_WEIGHT_EXPLOSIVE.get()
                : CompatConfig.COOK_OFF_WEIGHT_SPECIAL.get();
        return new CookOffYield(weight, smoke, fire);
    }

    private static JsonObject loadProperties(MinecraftServer server, ResourceLocation blockId) {
        Optional<JsonObject> cached = PROPERTIES_CACHE.get(blockId);
        if (cached != null) {
            return cached.orElse(null);
        }
        Optional<JsonObject> result = Optional.empty();
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(
                blockId.getNamespace(), "munition_properties/projectiles/" + blockId.getPath() + ".json");
        try {
            Optional<InputStream> stream = server.getResourceManager().getResource(file)
                    .map(resource -> {
                        try {
                            return resource.open();
                        } catch (Exception e) {
                            return null;
                        }
                    });
            if (stream.isPresent() && stream.get() != null) {
                try (InputStreamReader reader = new InputStreamReader(stream.get(), StandardCharsets.UTF_8)) {
                    result = Optional.ofNullable(JsonParser.parseReader(reader).getAsJsonObject());
                }
            }
        } catch (Exception e) {
            result = Optional.empty();
        }
        PROPERTIES_CACHE.put(blockId, result);
        return result.orElse(null);
    }

    private static boolean isExplosiveMedium(Item item) {
        return item instanceof HEMediumcannonRoundItem
                || item instanceof HEMediumcannonCartridgeItem
                || item instanceof HEAPMediumcannonRoundItem
                || item instanceof HEAPMediumcannonCartridgeItem
                || item instanceof HEFMediumcannonRoundItem
                || item instanceof HEFMediumcannonCartridgeItem
                || item instanceof APHEMediumcannonRoundItem
                || item instanceof APHEMediumcannonCartridgeItem;
    }

    private static boolean isSmokeMedium(Item item) {
        return item instanceof SmokeMediumcannonRoundItem
                || item instanceof SmokeMediumcannonCartridgeItem;
    }
}