package acidglow.fluidtanks.client;

import acidglow.fluidtanks.AcidglowsFluidTanks;
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
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

public class FluidTankRenderer implements BlockEntityRenderer<FluidTankBlockEntity, FluidTankRenderState> {
    static final Identifier WATER_TEXTURE = Identifier.withDefaultNamespace("textures/block/water_still.png");
    static final Identifier LAVA_TEXTURE = Identifier.withDefaultNamespace("textures/block/lava_still.png");
    static final float MIN = 0.1875F;
    static final float MAX = 0.8125F;
    static final float BOTTOM = 0.0F;
    static final float TOP = 1.0F;
    private static final Identifier COPPER_BASE = texture("copper_fluidtank");
    private static final Identifier COPPER_UP = texture("copper_fluidtank_up");
    private static final Identifier COPPER_DOWN = texture("copper_fluidtank_down");
    private static final Identifier COPPER_LEFT = texture("copper_fluidtank_left");
    private static final Identifier COPPER_RIGHT = texture("copper_fluidtank_right");
    private static final Identifier COPPER_UP_DOWN = texture("copper_fluidtank_up_down");
    private static final Identifier COPPER_LEFT_RIGHT = texture("copper_fluidtank_left_right");
    private static final Identifier COPPER_UP_LEFT = texture("copper_fluidtank_up_left");
    private static final Identifier COPPER_UP_RIGHT = texture("copper_fluidtank_up_right");
    private static final Identifier COPPER_DOWN_LEFT = texture("copper_fluidtank_down_left");
    private static final Identifier COPPER_DOWN_RIGHT = texture("copper_fluidtank_down_right");
    private static final Identifier COPPER_CENTER = texture("copper_fluidtank_center");

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
    }

    @Override
    public void submit(FluidTankRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        if (state.renderShell) {
            submitShell(state, poseStack, submitNodeCollector);
        }

        if (state.fluid == Fluids.EMPTY || state.fillRatio <= 0.0F) {
            return;
        }

        float top = BOTTOM + (TOP - BOTTOM) * Math.min(1.0F, state.fillRatio);
        Identifier texture = state.fluid == Fluids.LAVA || state.fluid == Fluids.FLOWING_LAVA ? LAVA_TEXTURE : WATER_TEXTURE;
        submitNodeCollector.submitCustomGeometry(
                poseStack,
                RenderTypes.entityTranslucent(texture),
                (pose, buffer) -> renderConnectedFluid(pose, buffer, state, top)
        );
    }

    private static void extractShellRenderState(FluidTankBlockEntity blockEntity, FluidTankRenderState state) {
        state.renderShell = false;
        state.downTexture = COPPER_BASE;
        state.upTexture = COPPER_BASE;
        state.northTexture = COPPER_BASE;
        state.southTexture = COPPER_BASE;
        state.westTexture = COPPER_BASE;
        state.eastTexture = COPPER_BASE;
        state.downVisible = false;
        state.upVisible = false;
        state.northVisible = false;
        state.southVisible = false;
        state.westVisible = false;
        state.eastVisible = false;

        if (!(blockEntity.getBlockState().getBlock() instanceof FluidTankBlock tankBlock) || tankBlock.tier() != FluidTankTier.COPPER) {
            return;
        }

        Level level = blockEntity.getLevel();
        if (level == null) {
            return;
        }

        BlockPos pos = blockEntity.getBlockPos();
        state.renderShell = true;
        state.downVisible = !hasTank(level, pos, Direction.DOWN);
        state.upVisible = !hasTank(level, pos, Direction.UP);
        state.northVisible = !hasTank(level, pos, Direction.NORTH);
        state.southVisible = !hasTank(level, pos, Direction.SOUTH);
        state.westVisible = !hasTank(level, pos, Direction.WEST);
        state.eastVisible = !hasTank(level, pos, Direction.EAST);
        state.downTexture = textureForFace(level, pos, Direction.DOWN);
        state.upTexture = textureForFace(level, pos, Direction.UP);
        state.northTexture = textureForFace(level, pos, Direction.NORTH);
        state.southTexture = textureForFace(level, pos, Direction.SOUTH);
        state.westTexture = textureForFace(level, pos, Direction.WEST);
        state.eastTexture = textureForFace(level, pos, Direction.EAST);
    }

    private static void submitShell(FluidTankRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector) {
        if (state.upVisible) {
            submitNodeCollector.submitCustomGeometry(
                    poseStack,
                    RenderTypes.entityTranslucent(state.upTexture),
                    (pose, buffer) -> renderUpFace(pose, buffer, -1)
            );
        }
        if (state.downVisible) {
            submitNodeCollector.submitCustomGeometry(
                    poseStack,
                    RenderTypes.entityTranslucent(state.downTexture),
                    (pose, buffer) -> renderDownFace(pose, buffer, -1)
            );
        }
        if (state.northVisible) {
            submitNodeCollector.submitCustomGeometry(
                    poseStack,
                    RenderTypes.entityTranslucent(state.northTexture),
                    (pose, buffer) -> renderNorthFace(pose, buffer, -1)
            );
        }
        if (state.southVisible) {
            submitNodeCollector.submitCustomGeometry(
                    poseStack,
                    RenderTypes.entityTranslucent(state.southTexture),
                    (pose, buffer) -> renderSouthFace(pose, buffer, -1)
            );
        }
        if (state.westVisible) {
            submitNodeCollector.submitCustomGeometry(
                    poseStack,
                    RenderTypes.entityTranslucent(state.westTexture),
                    (pose, buffer) -> renderWestFace(pose, buffer, -1)
            );
        }
        if (state.eastVisible) {
            submitNodeCollector.submitCustomGeometry(
                    poseStack,
                    RenderTypes.entityTranslucent(state.eastTexture),
                    (pose, buffer) -> renderEastFace(pose, buffer, -1)
            );
        }
    }

    private static void renderConnectedFluid(PoseStack.Pose pose, VertexConsumer buffer, FluidTankRenderState state, float top) {
        renderCuboid(pose, buffer, state.color, MIN, BOTTOM, MIN, MAX, top, MAX);
        if (state.north) {
            renderCuboid(pose, buffer, state.color, MIN, BOTTOM, 0.0F, MAX, top, MIN);
        }
        if (state.south) {
            renderCuboid(pose, buffer, state.color, MIN, BOTTOM, MAX, MAX, top, 1.0F);
        }
        if (state.east) {
            renderCuboid(pose, buffer, state.color, MAX, BOTTOM, MIN, 1.0F, top, MAX);
        }
        if (state.west) {
            renderCuboid(pose, buffer, state.color, 0.0F, BOTTOM, MIN, MIN, top, MAX);
        }
        if (state.north && state.east && state.northEast) {
            renderCuboid(pose, buffer, state.color, MAX, BOTTOM, 0.0F, 1.0F, top, MIN);
        }
        if (state.north && state.west && state.northWest) {
            renderCuboid(pose, buffer, state.color, 0.0F, BOTTOM, 0.0F, MIN, top, MIN);
        }
        if (state.south && state.east && state.southEast) {
            renderCuboid(pose, buffer, state.color, MAX, BOTTOM, MAX, 1.0F, top, 1.0F);
        }
        if (state.south && state.west && state.southWest) {
            renderCuboid(pose, buffer, state.color, 0.0F, BOTTOM, MAX, MIN, top, 1.0F);
        }
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

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(AcidglowsFluidTanks.MODID, "textures/block/" + name + ".png");
    }

    private static Identifier textureForFace(Level level, BlockPos pos, Direction face) {
        FaceConnections connections = connectionsForFace(level, pos, face);
        return textureForConnections(connections.up(), connections.down(), connections.left(), connections.right());
    }

    private static FaceConnections connectionsForFace(Level level, BlockPos pos, Direction face) {
        return switch (face) {
            case NORTH -> new FaceConnections(
                    hasTank(level, pos, Direction.UP),
                    hasTank(level, pos, Direction.DOWN),
                    hasTank(level, pos, Direction.EAST),
                    hasTank(level, pos, Direction.WEST)
            );
            case SOUTH -> new FaceConnections(
                    hasTank(level, pos, Direction.UP),
                    hasTank(level, pos, Direction.DOWN),
                    hasTank(level, pos, Direction.WEST),
                    hasTank(level, pos, Direction.EAST)
            );
            case EAST -> new FaceConnections(
                    hasTank(level, pos, Direction.UP),
                    hasTank(level, pos, Direction.DOWN),
                    hasTank(level, pos, Direction.SOUTH),
                    hasTank(level, pos, Direction.NORTH)
            );
            case WEST -> new FaceConnections(
                    hasTank(level, pos, Direction.UP),
                    hasTank(level, pos, Direction.DOWN),
                    hasTank(level, pos, Direction.NORTH),
                    hasTank(level, pos, Direction.SOUTH)
            );
            case UP -> new FaceConnections(
                    hasTank(level, pos, Direction.NORTH),
                    hasTank(level, pos, Direction.SOUTH),
                    hasTank(level, pos, Direction.WEST),
                    hasTank(level, pos, Direction.EAST)
            );
            case DOWN -> new FaceConnections(
                    hasTank(level, pos, Direction.SOUTH),
                    hasTank(level, pos, Direction.NORTH),
                    hasTank(level, pos, Direction.WEST),
                    hasTank(level, pos, Direction.EAST)
            );
        };
    }

    private static Identifier textureForConnections(boolean up, boolean down, boolean left, boolean right) {
        if (up && down && left && right) {
            return COPPER_CENTER;
        }
        if (up && down) {
            return COPPER_UP_DOWN;
        }
        if (left && right) {
            return COPPER_LEFT_RIGHT;
        }
        if (up && left) {
            return COPPER_UP_LEFT;
        }
        if (up && right) {
            return COPPER_UP_RIGHT;
        }
        if (down && left) {
            return COPPER_DOWN_LEFT;
        }
        if (down && right) {
            return COPPER_DOWN_RIGHT;
        }
        if (up) {
            return COPPER_UP;
        }
        if (down) {
            return COPPER_DOWN;
        }
        if (left) {
            return COPPER_LEFT;
        }
        return right ? COPPER_RIGHT : COPPER_BASE;
    }

    private static boolean hasTank(Level level, BlockPos pos, Direction direction) {
        return level.getBlockState(pos.relative(direction)).getBlock() instanceof FluidTankBlock;
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

    private static void renderUpFace(PoseStack.Pose pose, VertexConsumer buffer, int color) {
        quad(pose, buffer, color, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F, 0.0F);
    }

    private static void renderDownFace(PoseStack.Pose pose, VertexConsumer buffer, int color) {
        quad(pose, buffer, color, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.0F, 0.0F);
    }

    private static void renderNorthFace(PoseStack.Pose pose, VertexConsumer buffer, int color) {
        quad(pose, buffer, color, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, -1.0F);
    }

    private static void renderSouthFace(PoseStack.Pose pose, VertexConsumer buffer, int color) {
        quad(pose, buffer, color, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 1.0F);
    }

    private static void renderWestFace(PoseStack.Pose pose, VertexConsumer buffer, int color) {
        quad(pose, buffer, color, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F, 0.0F, -1.0F, 0.0F, 0.0F);
    }

    private static void renderEastFace(PoseStack.Pose pose, VertexConsumer buffer, int color) {
        quad(pose, buffer, color, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F);
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

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int color, float x, float y, float z, float u, float v, float normalX, float normalY, float normalZ) {
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(pose, normalX, normalY, normalZ);
    }

    private record FaceConnections(boolean up, boolean down, boolean left, boolean right) {
    }
}
