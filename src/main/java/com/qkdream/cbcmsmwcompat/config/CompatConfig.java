package com.qkdream.cbcmsmwcompat.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class CompatConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ARM_LOADING_FIX;
    public static final ModConfigSpec.BooleanValue DIRECT_HIT_COOK_OFF;
    public static final ModConfigSpec.BooleanValue BLAST_COOK_OFF;
    public static final ModConfigSpec.BooleanValue DEPOT_COOK_OFF;
    public static final ModConfigSpec.IntValue COOK_OFF_EXPLOSION_COUNT;
    public static final ModConfigSpec.IntValue COOK_OFF_EXPLOSION_INTERVAL;
    public static final ModConfigSpec.DoubleValue COOK_OFF_EXPLOSION_JITTER;
    public static final ModConfigSpec.DoubleValue COOK_OFF_BLOCK_RADIUS;
    public static final ModConfigSpec.DoubleValue COOK_OFF_ENTITY_RADIUS;
    public static final ModConfigSpec.BooleanValue COOK_OFF_FIRE;
    public static final ModConfigSpec.BooleanValue MIANBAOS_COOK_OFF;
    public static final ModConfigSpec.DoubleValue MIANBAOS_POWER_SCALE;
    public static final ModConfigSpec.DoubleValue MISSILE_POWER_SCALE;
    public static final ModConfigSpec.BooleanValue VESTALIHY_COOK_OFF;
    public static final ModConfigSpec.DoubleValue VESTALIHY_POWER_SCALE;
    public static final ModConfigSpec.BooleanValue PLAYER_DEATH_COOK_OFF;
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
        BLAST_COOK_OFF = builder
                .comment("An ammo rack holding ammunition always cooks off when it is caught",
                        "in an explosion blast.")
                .define("blastEnabled", true);
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
        COOK_OFF_BLOCK_RADIUS = builder
                .comment("Block radius of each cook off explosion.")
                .defineInRange("explosionBlockRadius", 7.0, 1.0, 64.0);
        COOK_OFF_ENTITY_RADIUS = builder
                .comment("Entity damage radius of each cook off explosion.")
                .defineInRange("explosionEntityRadius", 7.0, 1.0, 64.0);
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
        PLAYER_DEATH_COOK_OFF = builder
                .comment("Players carrying CBC ammunition cook off when they die, at ammo rack power.")
                .define("playerDeathEnabled", true);
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
