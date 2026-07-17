package acidglow.fluidtanks.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

public class FluidTankRenderState extends BlockEntityRenderState {
    public Fluid fluid = Fluids.EMPTY;
    public float fillRatio;
    public int color;
    public boolean north;
    public boolean south;
    public boolean east;
    public boolean west;
    public boolean northEast;
    public boolean northWest;
    public boolean southEast;
    public boolean southWest;
}
