package acidglow.fluidtanks.client;

import acidglow.fluidtanks.tank.FluidTankBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
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

    public FluidTankRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public FluidTankRenderState createRenderState() {
        return new FluidTankRenderState();
    }

    @Override
    public void extractRenderState(FluidTankBlockEntity blockEntity, FluidTankRenderState state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
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
}
