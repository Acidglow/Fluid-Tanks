package acidglow.fluidtanks.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
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
    public Identifier fluidTexture = TextureAtlas.LOCATION_BLOCKS;
    public float fluidU0;
    public float fluidU1 = 1.0F;
    public float fluidV0;
    public float fluidV1 = 1.0F;
    public float fillRatio;
    public int color;
    public boolean north;
    public boolean south;
    public boolean east;
    public boolean west;
    public boolean upTank;
    public boolean downTank;
    public boolean upFluid;
    public boolean downFluid;
    public boolean northEast;
    public boolean northWest;
    public boolean southEast;
    public boolean southWest;
    public int wrenchOutlineColor;
}
