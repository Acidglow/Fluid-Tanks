package acidglow.fluidtanks;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue CAPACITY_MULTIPLIER = BUILDER
            .comment("Multiplier applied to every tank tier capacity, measured in millibuckets.")
            .defineInRange("capacityMultiplier", 1, 1, 1024);

    public static final ModConfigSpec.BooleanValue TANK_TIERS_CAN_CONNECT = BUILDER
            .comment("Whether tanks from different tiers can connect to the same tank network.")
            .define("tankTiersCanConnect", true);

    static final ModConfigSpec SPEC = BUILDER.build();
}
