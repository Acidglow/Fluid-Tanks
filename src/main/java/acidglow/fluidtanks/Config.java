package acidglow.fluidtanks;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue CAPACITY_MULTIPLIER = BUILDER
            .comment("Multiplier applied to every tank tier capacity, measured in millibuckets.")
            .defineInRange("capacityMultiplier", 1, 1, 1024);

    static final ModConfigSpec SPEC = BUILDER.build();
}
