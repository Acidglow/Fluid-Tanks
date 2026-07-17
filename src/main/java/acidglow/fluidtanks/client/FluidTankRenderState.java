package acidglow.fluidtanks.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

public class FluidTankRenderState extends BlockEntityRenderState {
    public boolean renderShell;
    public Identifier downTexture;
    public Identifier upTexture;
    public Identifier northTexture;
    public Identifier southTexture;
    public Identifier westTexture;
    public Identifier eastTexture;
    public boolean downVisible;
    public boolean upVisible;
    public boolean northVisible;
    public boolean southVisible;
    public boolean westVisible;
    public boolean eastVisible;
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
