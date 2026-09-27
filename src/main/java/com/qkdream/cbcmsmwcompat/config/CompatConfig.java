package com.qkdream.cbcmsmwcompat.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class CompatConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ARM_LOADING_FIX;
    public static final ModConfigSpec.BooleanValue DIRECT_HIT_COOK_OFF;
    public static final ModConfigSpec.BooleanValue FRAGMENT_COOK_OFF;
    public static final ModConfigSpec.BooleanValue BLAST_COOK_OFF;
    public static final ModConfigSpec.DoubleValue BLAST_COOK_OFF_GUARANTEED_RADIUS;
    public static final ModConfigSpec.DoubleValue BLAST_COOK_OFF_MIN_CHANCE;
    public static final ModConfigSpec.BooleanValue DEPOT_COOK_OFF;
    public static final ModConfigSpec.IntValue COOK_OFF_EXPLOSION_COUNT;
    public static final ModConfigSpec.IntValue COOK_OFF_EXPLOSION_INTERVAL;
    public static final ModConfigSpec.DoubleValue COOK_OFF_EXPLOSION_JITTER;
    public static final ModConfigSpec.DoubleValue COOK_OFF_BASE_RADIUS;
    public static final ModConfigSpec.DoubleValue COOK_OFF_MAX_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue COOK_OFF_WEIGHT_STANDARD;
    public static final ModConfigSpec.DoubleValue COOK_OFF_WEIGHT_EXPLOSIVE;
    public static final ModConfigSpec.DoubleValue COOK_OFF_WEIGHT_PROPELANT;
    public static final ModConfigSpec.DoubleValue COOK_OFF_WEIGHT_SPECIAL;
    public static final ModConfigSpec.BooleanValue COOK_OFF_SPECIAL_EFFECTS;
    public static final ModConfigSpec.BooleanValue COOK_OFF_FIRE;
    public static final ModConfigSpec.BooleanValue MIANBAOS_COOK_OFF;
    public static final ModConfigSpec.DoubleValue MIANBAOS_POWER_SCALE;
    public static final ModConfigSpec.DoubleValue MISSILE_POWER_SCALE;
    public static final ModConfigSpec.BooleanValue VESTALIHY_COOK_OFF;
    public static final ModConfigSpec.DoubleValue VESTALIHY_POWER_SCALE;
    public static final ModConfigSpec.BooleanValue MOB_DEATH_COOK_OFF;
    public static final ModConfigSpec.BooleanValue AIR_MUNITION_COOK_OFF;
    public static final ModConfigSpec.DoubleValue CBCMS_AIR_POWER_SCALE;
    public static final ModConfigSpec.BooleanValue TAOV_COOK_OFF;
    public static final ModConfigSpec.DoubleValue TAOV_RACK_POWER_SCALE;
    public static final ModConfigSpec.BooleanValue SABLE_COOK_OFF;
    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("CBCMS x CBCMW ammo rack compatibility and cook off settings");

        builder.push("loading_fix");
        ARM_LOADING_FIX = builder
                .comment("Allows the mechanical arm (power arm) to take CBC Modern Warfare medium cannon",
                        "ammunition out of CBC Military Supplement ammo racks.")
                .define("enabled", true);
        builder.pop();

        builder.push("cook_off");
        DIRECT_HIT_COOK_OFF = builder
                .comment("An ammo rack or depot holding ammunition always cooks off when it is",
                        "directly hit by any Create Big Cannons projectile (including CBCMW/CBCMS ammo).")
                .define("directHitEnabled", true);
        FRAGMENT_COOK_OFF = builder
                .comment("Ammo racks, depots and launcher platforms cook off when they are hit",
                        "by the fragments CBC Terminal Ballistics casts out of a block a shell",
                        "penetrates. Shrapnel bursts of Create Big Cannons are unaffected:",
                        "they still only detonate missiles in flight.")
                .define("fragmentEnabled", true);
        BLAST_COOK_OFF = builder
                .comment("An ammo rack holding ammunition cooks off when it is caught in an",
                        "explosion blast. The closer the storage sits to the center of the",
                        "explosion, the higher the chance: inside blastGuaranteedRadius it",
                        "always detonates, further out the chance falls off towards",
                        "blastMinChance at the edge (set blastMinChance to 1.0 for the old",
                        "always detonate behaviour).")
                .define("blastEnabled", true);
        BLAST_COOK_OFF_GUARANTEED_RADIUS = builder
                .comment("Distance from the center of a blast (in blocks) within which a",
                        "storage caught in it always cooks off.")
                .defineInRange("blastGuaranteedRadius", 0.5, 0.0, 12.0);
        BLAST_COOK_OFF_MIN_CHANCE = builder
                .comment("Cook off chance for a storage at the very edge of a blast. Between",
                        "blastGuaranteedRadius and the edge the chance scales quadratically",
                        "from 1.0 down to this value.")
                .defineInRange("blastMinChance", 0.1, 0.0, 1.0);
        DEPOT_COOK_OFF = builder
                .comment("Create depots holding CBC ammunition also cook off when hit or caught in a blast.")
                .define("depotEnabled", true);
        COOK_OFF_EXPLOSION_COUNT = builder
                .comment("Number of explosions created by a single cook off.")
                .defineInRange("explosionCount", 3, 1, 10);
        COOK_OFF_EXPLOSION_INTERVAL = builder
                .comment("Ticks between the explosions of a single cook off.")
                .defineInRange("explosionInterval", 4, 1, 40);
        COOK_OFF_EXPLOSION_JITTER = builder
                .comment("Maximum random horizontal offset (in blocks) applied to each cook off explosion.")
                .defineInRange("explosionJitter", 1.5, 0.0, 8.0);
        COOK_OFF_BASE_RADIUS = builder
                .comment("Base radius of each cook off explosion. The yield of the stored ammunition",
                        "(shell type and quantity, see the power.* settings below) scales this radius up.")
                .defineInRange("power.baseRadius", 3.0, 1.0, 64.0);
        COOK_OFF_MAX_MULTIPLIER = builder
                .comment("Maximum power multiplier a cook off can reach from the ammunition yield.",
                        "Power scales with the cube of the explosion radius, so a multiplier of 1",
                        "keeps the base radius (no stacking amplification).")
                .defineInRange("power.maxMultiplier", 1.0, 1.0, 64.0);
        COOK_OFF_WEIGHT_STANDARD = builder
                .comment("Yield weight of one standard shell (CBCMW medium ammunition, cartridges,",
                        "propellant and ordinary shells).")
                .defineInRange("power.weightStandard", 1.0, 0.0, 8.0);
        COOK_OFF_WEIGHT_EXPLOSIVE = builder
                .comment("Yield weight of one high explosive warhead (HE, HEAT/HESH, SAP, incendiary",
                        "and CBCMW medium HE-family rounds): they cook off at increased power.")
                .defineInRange("power.weightExplosive", 2.0, 0.0, 8.0);
        COOK_OFF_WEIGHT_PROPELANT = builder
                .comment("Yield weight of one propellant item (powder charge or cartridge).")
                .defineInRange("power.weightPropellant", 1.0, 0.0, 8.0);
        COOK_OFF_WEIGHT_SPECIAL = builder
                .comment("Yield weight of one special-effect shell with no HE filler (smoke shells).")
                .defineInRange("power.weightSpecial", 1.0, 0.0, 8.0);
        COOK_OFF_SPECIAL_EFFECTS = builder
                .comment("Cooking off ammunition with special effects also triggers those effects:",
                        "smoke shells release a smoke cloud, incendiary shells spread fire.")
                .define("power.specialEffects", true);
        COOK_OFF_FIRE = builder
                .comment("Whether the cook off explosions create fire.")
                .define("fire", false);
        MIANBAOS_COOK_OFF = builder
                .comment("mianbaos_modernwarfare missile/rocket launchers and missiles in flight",
                        "cook off when hit or caught in a blast, at slightly lower power than",
                        "an ammo rack.")
                .define("mianbaosEnabled", true);
        MIANBAOS_POWER_SCALE = builder
                .comment("Explosion radius multiplier for mianbaos launcher cook offs.")
                .defineInRange("mianbaosPowerScale", 0.8, 0.1, 2.0);
        MISSILE_POWER_SCALE = builder
                .comment("Explosion radius multiplier for cook offs of missiles in flight",
                        "(mianbaos and vestalihy), lower than launcher power.")
                .defineInRange("missilePowerScale", 0.5, 0.1, 2.0);
        VESTALIHY_COOK_OFF = builder
                .comment("vestalihy missile launchers (tubes/tripods) and guided missiles in",
                        "flight cook off when hit or caught in a blast, mirroring mianbaos.")
                .define("vestalihyEnabled", true);
        VESTALIHY_POWER_SCALE = builder
                .comment("Explosion radius multiplier for vestalihy launcher cook offs.")
                .defineInRange("vestalihyPowerScale", 0.8, 0.1, 2.0);
        MOB_DEATH_COOK_OFF = builder
                .comment("Any living entity (players included) carrying CBC-family ammunition or",
                        "propellant cooks off when it dies. Power follows the same type and",
                        "quantity rules as an ammo rack. Touhou Little Maid maids also count",
                        "the ammunition stored in their maid inventory.")
                .define("mobDeathEnabled", true);
        AIR_MUNITION_COOK_OFF = builder
                .comment("Passive detonation of in-flight air munitions: CBC Military Supplement",
                        "torpedoes, rockets, depth charges and bombs, and the in-flight Tau/Hellfire",
                        "missiles of taov_weapons, detonate when directly hit by a projectile, hit",
                        "by fragments (shrapnel bursts) or caught in an explosion blast.",
                        "No proximity fuze is provided: proximity airbursts are handled by other mods.")
                .define("airMunitionCookOffEnabled", true);
        CBCMS_AIR_POWER_SCALE = builder
                .comment("Explosion radius multiplier for the detonation of CBC Military Supplement",
                        "air munitions in flight (torpedoes, rockets, depth charges and bombs).",
                        "A destroyed air munition detonates once at this reduced power.")
                .defineInRange("cbcmsAirPowerScale", 0.5, 0.1, 4.0);
        TAOV_COOK_OFF = builder
                .comment("Tau and Hellfire racks of taov_weapons holding ammunition cook off when",
                        "directly hit or caught in a blast, and their in-flight missiles detonate",
                        "when hit by a projectile, fragments or an explosion blast.")
                .define("taovEnabled", true);
        TAOV_RACK_POWER_SCALE = builder
                .comment("Explosion radius multiplier for Tau/Hellfire rack cook offs.")
                .defineInRange("taovRackPowerScale", 1.0, 0.1, 4.0);
        SABLE_COOK_OFF = builder
                .comment("Sable sub-level structures caught in a cook off blast take structural",
                        "damage: blocks inside the blast radius are destroyed with the usual",
                        "explosion resistance rules and their drops appear at the structure.")
                .define("sableEnabled", true);
        DEBUG_LOGGING = builder
                .comment("Log cook off triggers to the game log.")
                .define("debugLogging", true);
        builder.pop();

        SPEC = builder.build();
    }

    private CompatConfig() {
    }

    public static void register() {
        ModContainer container = ModLoadingContext.get().getActiveContainer();
        container.registerConfig(ModConfig.Type.SERVER, SPEC);
    }
}