package acidglow.fluidtanks.tank;

import acidglow.fluidtanks.Config;
import net.neoforged.neoforge.fluids.FluidType;

public enum FluidTankTier {
    COPPER("copper_fluid_tank", 8),
    IRON("iron_fluid_tank", 16),
    GOLD("gold_fluid_tank", 32),
    DIAMOND("diamond_fluid_tank", 64),
    EMERALD("emerald_fluid_tank", 128),
    NETHERITE("netherite_fluid_tank", 256);

    private final String id;
    private final int buckets;

    FluidTankTier(String id, int buckets) {
        this.id = id;
        this.buckets = buckets;
    }

    public String id() {
        return id;
    }

    public int capacity() {
        return buckets * FluidType.BUCKET_VOLUME * Config.CAPACITY_MULTIPLIER.getAsInt();
    }
}
