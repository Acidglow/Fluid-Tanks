package acidglow.fluidtanks.client;

import acidglow.fluidtanks.AcidglowsFluidTanks;
import acidglow.fluidtanks.tank.CopperConnectedTextures;
import acidglow.fluidtanks.tank.FluidTankBlock;
import acidglow.fluidtanks.tank.FluidTankBlockEntity;
import acidglow.fluidtanks.tank.FluidTankTier;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

public class FluidTankRenderer implements BlockEntityRenderer<FluidTankBlockEntity, FluidTankRenderState> {
    static final Identifier WATER_TEXTURE = Identifier.withDefaultNamespace("textures/block/water_still.png");
    static final Identifier LAVA_TEXTURE = Identifier.withDefaultNamespace("textures/block/lava_still.png");
    static final float MIN = 0.1875F;
    static final float MAX = 0.8125F;
    static final float BOTTOM = 0.0F;
    static final float TOP = 1.0F;
    private static final float LIQUID_INSET = 0.0625F;
    private static final float INNER_INSET = 0.002F;
    private static final boolean DEBUG_CONNECTED_TEXTURES = Boolean.getBoolean(AcidglowsFluidTanks.MODID + ".debugConnectedTextures");

    public FluidTankRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public FluidTankRenderState createRenderState() {
        return new FluidTankRenderState();
    }

    @Override
    public void extractRenderState(FluidTankBlockEntity blockEntity, FluidTankRenderState state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        extractShellRenderState(blockEntity, state);
        FluidStack stack = blockEntity.networkFluid();
        state.fluid = stack.isEmpty() ? Fluids.EMPTY : stack.getFluid();
        state.fillRatio = blockEntity.blockFillRatio();
        state.color = colorFor(state.fluid);
        FluidTankBlockEntity.FluidConnections connections = blockEntity.fluidConnections();
        state.north = connections.north();
        state.south = connections.south();
        state.east = connections.east();
        state.west = connections.west();
        state.northEast = connections.northEast();
        state.northWest = connections.northWest();
        state.southEast = connections.southEast();
        state.southWest = connections.southWest();
        state.upTank = connectsToTank(blockEntity, Direction.UP);
        state.downTank = connectsToTank(blockEntity, Direction.DOWN);
        state.upFluid = connectsToFluid(blockEntity, Direction.UP, stack.getFluid(), true);
        state.downFluid = connectsToFluid(blockEntity, Direction.DOWN, stack.getFluid(), false);
        state.wrenchOutlineColor = blockEntity.wrenchOutlineColor();
    }

    @Override
    public void submit(FluidTankRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        if (state.renderShell) {
            submitShell(state, poseStack, submitNodeCollector);
        }

        submitWrenchOutline(state, poseStack, submitNodeCollector);

        if (state.fluid == Fluids.EMPTY || state.fillRatio <= 0.0F) {
            return;
        }

        Identifier texture = state.fluid == Fluids.LAVA || state.fluid == Fluids.FLOWING_LAVA ? LAVA_TEXTURE : WATER_TEXTURE;
        submitNodeCollector.submitCustomGeometry(
                poseStack,
                RenderTypes.entityTranslucent(texture),
                (pose, buffer) -> renderConnectedFluid(pose, buffer, state)
        );
    }

    private static void submitWrenchOutline(FluidTankRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector) {
        if (state.wrenchOutlineColor != 0) {
            submitNodeCollector.submitShapeOutline(poseStack, Shapes.block(), RenderTypes.lines(), state.wrenchOutlineColor, 4.0F, false);
        }
    }

    private static void extractShellRenderState(FluidTankBlockEntity blockEntity, FluidTankRenderState state) {
        state.renderShell = false;
        state.downTexture = CopperConnectedTextures.COPPER_TOP_BOTTOM_SOLO;
        state.upTexture = CopperConnectedTextures.COPPER_TOP_BOTTOM_SOLO;
        state.northTexture = CopperConnectedTextures.COPPER_SIDE_SOLO;
        state.southTexture = CopperConnectedTextures.COPPER_SIDE_SOLO;
        state.westTexture = CopperConnectedTextures.COPPER_SIDE_SOLO;
        state.eastTexture = CopperConnectedTextures.COPPER_SIDE_SOLO;
        state.downVisible = false;
        state.upVisible = false;
        state.northVisible = false;
        state.southVisible = false;
        state.westVisible = false;
        state.eastVisible = false;

        if (!(blockEntity.getBlockState().getBlock() instanceof FluidTankBlock tankBlock)
                || !CopperConnectedTextures.supportsTier(tankBlock.tier())) {
            return;
        }

        Level level = blockEntity.getLevel();
        if (level == null) {
            return;
        }

        BlockPos pos = blockEntity.getBlockPos();
        FluidTankTier tier = tankBlock.tier();
        state.renderShell = true;
        state.downVisible = !canConnect(level, pos, Direction.DOWN);
        state.upVisible = !canConnect(level, pos, Direction.UP);
        state.northVisible = !canConnect(level, pos, Direction.NORTH);
        state.southVisible = !canConnect(level, pos, Direction.SOUTH);
        state.westVisible = !canConnect(level, pos, Direction.WEST);
        state.eastVisible = !canConnect(level, pos, Direction.EAST);
        state.downTexture = CopperConnectedTextures.topBottomTexture(tier);
        state.upTexture = CopperConnectedTextures.topBottomTexture(tier);
        state.northTexture = textureForFace(level, pos, Direction.NORTH, tier);
        state.southTexture = textureForFace(level, pos, Direction.SOUTH, tier);
        state.westTexture = textureForFace(level, pos, Direction.WEST, tier);
        state.eastTexture = textureForFace(level, pos, Direction.EAST, tier);
    }

    private static void submitShell(FluidTankRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector) {
        if (state.upVisible) {
            submitNodeCollector.submitCustomGeometry(
                    poseStack,
                    RenderTypes.entityCutout(state.upTexture),
                    (pose, buffer) -> renderUpCap(pose, buffer, -1)
            );
        }
        if (state.downVisible) {
            submitNodeCollector.submitCustomGeometry(
                    poseStack,
                    RenderTypes.entityCutout(state.downTexture),
                    (pose, buffer) -> renderDownCap(pose, buffer, -1)
            );
        }
        if (state.northVisible) {
            submitNodeCollector.submitCustomGeometry(
                    poseStack,
                    RenderTypes.entityCutout(state.northTexture),
                    (pose, buffer) -> renderNorthWall(pose, buffer, -1)
            );
        }
        if (state.southVisible) {
            submitNodeCollector.submitCustomGeometry(
                    poseStack,
                    RenderTypes.entityCutout(state.southTexture),
                    (pose, buffer) -> renderSouthWall(pose, buffer, -1)
            );
        }
        if (state.westVisible) {
            submitNodeCollector.submitCustomGeometry(
                    poseStack,
                    RenderTypes.entityCutout(state.westTexture),
                    (pose, buffer) -> renderWestWall(pose, buffer, -1)
            );
        }
        if (state.eastVisible) {
            submitNodeCollector.submitCustomGeometry(
                    poseStack,
                    RenderTypes.entityCutout(state.eastTexture),
                    (pose, buffer) -> renderEastWall(pose, buffer, -1)
            );
        }
    }

    private static void renderConnectedFluid(PoseStack.Pose pose, VertexConsumer buffer, FluidTankRenderState state) {
        float x0 = state.west ? 0.0F : LIQUID_INSET;
        float x1 = state.east ? 1.0F : 1.0F - LIQUID_INSET;
        float y0 = state.downTank ? BOTTOM : LIQUID_INSET;
        float yLimit = state.upTank ? TOP : TOP - LIQUID_INSET;
        float y1 = y0 + (yLimit - y0) * Math.min(1.0F, state.fillRatio);
        float z0 = state.north ? 0.0F : LIQUID_INSET;
        float z1 = state.south ? 1.0F : 1.0F - LIQUID_INSET;
        renderFluidBox(
                pose,
                buffer,
                state.color,
                x0,
                y0,
                z0,
                x1,
                y1,
                z1,
                y1 < TOP || !state.upFluid,
                !state.downFluid,
                !state.north,
                !state.south,
                !state.west,
                !state.east
        );
    }

    static int colorFor(Fluid fluid) {
        if (fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA) {
            return argb(210, 255, 96, 16);
        }
        if (fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER) {
            return argb(170, 64, 128, 255);
        }
        return argb(180, 72, 210, 180);
    }

    private static Identifier textureForFace(Level level, BlockPos pos, Direction face, FluidTankTier tier) {
        CopperConnectedTextures.Connections connections = CopperConnectedTextures.readConnections(level, pos, face, tier);
        Identifier texture = CopperConnectedTextures.selectSideTexture(tier, connections);
        if (DEBUG_CONNECTED_TEXTURES) {
            CopperConnectedTextures.Connections normalized = connections.normalized();
            AcidglowsFluidTanks.LOGGER.debug(
                    "{} connected texture pos={} face={} cardinalMask={} missingCornerMask={} texture={}",
                    tier,
                    pos,
                    face,
                    normalized.cardinalMask(),
                    normalized.missingCornerMask(),
                    texture
            );
        }
        return texture;
    }

    private static boolean canConnect(Level level, BlockPos pos, Direction direction) {
        return CopperConnectedTextures.canConnect(level, pos, pos.relative(direction));
    }

    private static boolean connectsToTank(FluidTankBlockEntity blockEntity, Direction direction) {
        Level level = blockEntity.getLevel();
        if (level == null) {
            return false;
        }

        BlockPos pos = blockEntity.getBlockPos();
        BlockPos neighborPos = pos.relative(direction);
        return CopperConnectedTextures.canConnect(level, pos, neighborPos);
    }

    private static boolean connectsToFluid(FluidTankBlockEntity blockEntity, Direction direction, Fluid fluid, boolean above) {
        Level level = blockEntity.getLevel();
        if (level == null || fluid == Fluids.EMPTY) {
            return false;
        }

        BlockPos neighborPos = blockEntity.getBlockPos().relative(direction);
        if (!CopperConnectedTextures.canConnect(level, blockEntity.getBlockPos(), neighborPos)
                || !(level.getBlockEntity(neighborPos) instanceof FluidTankBlockEntity neighbor)) {
            return false;
        }

        FluidStack neighborFluid = neighbor.networkFluid();
        if (neighborFluid.isEmpty() || neighborFluid.getFluid() != fluid) {
            return false;
        }

        float neighborFillRatio = neighbor.blockFillRatio();
        return above ? neighborFillRatio > 0.0F : neighborFillRatio >= 1.0F;
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    static void renderCuboid(PoseStack.Pose pose, VertexConsumer buffer, int color, float x0, float y0, float z0, float x1, float y1, float z1) {
        quad(pose, buffer, color, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0.0F, 1.0F, 0.0F);
        quad(pose, buffer, color, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0.0F, -1.0F, 0.0F);
        quad(pose, buffer, color, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, -1.0F, 0.0F, 0.0F);
        quad(pose, buffer, color, x1, y0, z1, x1, y1, z1, x1, y1, z0, x1, y0, z0, 1.0F, 0.0F, 0.0F);
        quad(pose, buffer, color, x1, y0, z0, x1, y1, z0, x0, y1, z0, x0, y0, z0, 0.0F, 0.0F, -1.0F);
        quad(pose, buffer, color, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, 0.0F, 0.0F, 1.0F);
    }

    private static void renderFluidBox(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            int color,
            float x0,
            float y0,
            float z0,
            float x1,
            float y1,
            float z1,
            boolean top,
            boolean bottom,
            boolean north,
            boolean south,
            boolean west,
            boolean east
    ) {
        if (top) {
            quad(pose, buffer, color, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0.0F, 1.0F, 0.0F);
        }
        if (bottom) {
            quad(pose, buffer, color, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0.0F, -1.0F, 0.0F);
        }
        if (west) {
            quad(pose, buffer, color, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, -1.0F, 0.0F, 0.0F);
        }
        if (east) {
            quad(pose, buffer, color, x1, y0, z1, x1, y1, z1, x1, y1, z0, x1, y0, z0, 1.0F, 0.0F, 0.0F);
        }
        if (north) {
            quad(pose, buffer, color, x1, y0, z0, x1, y1, z0, x0, y1, z0, x0, y0, z0, 0.0F, 0.0F, -1.0F);
        }
        if (south) {
            quad(pose, buffer, color, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, 0.0F, 0.0F, 1.0F);
        }
    }

    private static void renderUpCap(PoseStack.Pose pose, VertexConsumer buffer, int color) {
        renderUpFace(pose, buffer, color);
        renderInsideUpFace(pose, buffer, color);
    }

    private static void renderDownCap(PoseStack.Pose pose, VertexConsumer buffer, int color) {
        renderDownFace(pose, buffer, color);
        renderInsideDownFace(pose, buffer, color);
    }

    private static void renderNorthWall(PoseStack.Pose pose, VertexConsumer buffer, int color) {
        renderNorthFace(pose, buffer, color);
        renderInsideNorthFace(pose, buffer, color);
    }

    private static void renderSouthWall(PoseStack.Pose pose, VertexConsumer buffer, int color) {
        renderSouthFace(pose, buffer, color);
        renderInsideSouthFace(pose, buffer, color);
    }

    private static void renderWestWall(PoseStack.Pose pose, VertexConsumer buffer, int color) {
        renderWestFace(pose, buffer, color);
        renderInsideWestFace(pose, buffer, color);
    }

    private static void renderEastWall(PoseStack.Pose pose, VertexConsumer buffer, int color) {
        renderEastFace(pose, buffer, color);
        renderInsideEastFace(pose, buffer, color);
    }

    private static void renderUpFace(PoseStack.Pose pose, VertexConsumer buffer, int color) {
        flippedVQuad(pose, buffer, color, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F, 0.0F);
    }

    private static void renderInsideUpFace(PoseStack.Pose pose, VertexConsumer buffer, int color) {
        float y = 1.0F - INNER_INSET;
        flippedVQuadReversed(pose, buffer, color, 0.0F, y, 0.0F, 1.0F, y, 0.0F, 1.0F, y, 1.0F, 0.0F, y, 1.0F, 0.0F, -1.0F, 0.0F);
    }

    private static void renderDownFace(PoseStack.Pose pose, VertexConsumer buffer, int color) {
        flippedVQuad(pose, buffer, color, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.0F, 0.0F);
    }

    private static void renderInsideDownFace(PoseStack.Pose pose, VertexConsumer buffer, int color) {
        float y = INNER_INSET;
        flippedVQuadReversed(pose, buffer, color, 0.0F, y, 1.0F, 1.0F, y, 1.0F, 1.0F, y, 0.0F, 0.0F, y, 0.0F, 0.0F, 1.0F, 0.0F);
    }

    private static void renderNorthFace(PoseStack.Pose pose, VertexConsumer buffer, int color) {
        flippedVQuad(pose, buffer, color, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, -1.0F);
    }

    private static void renderInsideNorthFace(PoseStack.Pose pose, VertexConsumer buffer, int color) {
        float z = INNER_INSET;
        flippedVQuadReversed(pose, buffer, color, 1.0F, 0.0F, z, 0.0F, 0.0F, z, 0.0F, 1.0F, z, 1.0F, 1.0F, z, 0.0F, 0.0F, 1.0F);
    }

    private static void renderSouthFace(PoseStack.Pose pose, VertexConsumer buffer, int color) {
        flippedVQuad(pose, buffer, color, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 1.0F);
    }

    private static void renderInsideSouthFace(PoseStack.Pose pose, VertexConsumer buffer, int color) {
        float z = 1.0F - INNER_INSET;
        flippedVQuadReversed(pose, buffer, color, 0.0F, 0.0F, z, 1.0F, 0.0F, z, 1.0F, 1.0F, z, 0.0F, 1.0F, z, 0.0F, 0.0F, -1.0F);
    }

    private static void renderWestFace(PoseStack.Pose pose, VertexConsumer buffer, int color) {
        flippedVQuad(pose, buffer, color, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F, 0.0F, -1.0F, 0.0F, 0.0F);
    }

    private static void renderInsideWestFace(PoseStack.Pose pose, VertexConsumer buffer, int color) {
        float x = INNER_INSET;
        flippedVQuadReversed(pose, buffer, color, x, 0.0F, 0.0F, x, 0.0F, 1.0F, x, 1.0F, 1.0F, x, 1.0F, 0.0F, 1.0F, 0.0F, 0.0F);
    }

    private static void renderEastFace(PoseStack.Pose pose, VertexConsumer buffer, int color) {
        flippedVQuad(pose, buffer, color, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F);
    }

    private static void renderInsideEastFace(PoseStack.Pose pose, VertexConsumer buffer, int color) {
        float x = 1.0F - INNER_INSET;
        flippedVQuadReversed(pose, buffer, color, x, 0.0F, 1.0F, x, 0.0F, 0.0F, x, 1.0F, 0.0F, x, 1.0F, 1.0F, -1.0F, 0.0F, 0.0F);
    }

    private static void quad(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            int color,
            float x0,
            float y0,
            float z0,
            float x1,
            float y1,
            float z1,
            float x2,
            float y2,
            float z2,
            float x3,
            float y3,
            float z3,
            float normalX,
            float normalY,
            float normalZ
    ) {
        vertex(pose, buffer, color, x0, y0, z0, 0.0F, 0.0F, normalX, normalY, normalZ);
        vertex(pose, buffer, color, x1, y1, z1, 1.0F, 0.0F, normalX, normalY, normalZ);
        vertex(pose, buffer, color, x2, y2, z2, 1.0F, 1.0F, normalX, normalY, normalZ);
        vertex(pose, buffer, color, x3, y3, z3, 0.0F, 1.0F, normalX, normalY, normalZ);
    }

    private static void flippedVQuad(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            int color,
            float x0,
            float y0,
            float z0,
            float x1,
            float y1,
            float z1,
            float x2,
            float y2,
            float z2,
            float x3,
            float y3,
            float z3,
            float normalX,
            float normalY,
            float normalZ
    ) {
        vertex(pose, buffer, color, x0, y0, z0, 0.0F, 1.0F, normalX, normalY, normalZ);
        vertex(pose, buffer, color, x1, y1, z1, 1.0F, 1.0F, normalX, normalY, normalZ);
        vertex(pose, buffer, color, x2, y2, z2, 1.0F, 0.0F, normalX, normalY, normalZ);
        vertex(pose, buffer, color, x3, y3, z3, 0.0F, 0.0F, normalX, normalY, normalZ);
    }

    private static void flippedVQuadReversed(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            int color,
            float x0,
            float y0,
            float z0,
            float x1,
            float y1,
            float z1,
            float x2,
            float y2,
            float z2,
            float x3,
            float y3,
            float z3,
            float normalX,
            float normalY,
            float normalZ
    ) {
        vertex(pose, buffer, color, x3, y3, z3, 0.0F, 0.0F, normalX, normalY, normalZ);
        vertex(pose, buffer, color, x2, y2, z2, 1.0F, 0.0F, normalX, normalY, normalZ);
        vertex(pose, buffer, color, x1, y1, z1, 1.0F, 1.0F, normalX, normalY, normalZ);
        vertex(pose, buffer, color, x0, y0, z0, 0.0F, 1.0F, normalX, normalY, normalZ);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int color, float x, float y, float z, float u, float v, float normalX, float normalY, float normalZ) {
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(pose, normalX, normalY, normalZ);
    }
}
