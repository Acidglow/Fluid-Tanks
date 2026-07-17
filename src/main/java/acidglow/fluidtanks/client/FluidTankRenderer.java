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
    private static final Identifier WATER_TEXTURE = Identifier.withDefaultNamespace("textures/block/water_still.png");
    private static final Identifier LAVA_TEXTURE = Identifier.withDefaultNamespace("textures/block/lava_still.png");
    private static final float MIN = 0.1875F;
    private static final float MAX = 0.8125F;
    private static final float BOTTOM = 0.125F;
    private static final float TOP = 0.875F;

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
        state.fillRatio = blockEntity.networkFillRatio();
        state.color = colorFor(state.fluid);
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
                (pose, buffer) -> renderCuboid(pose, buffer, state.color, MIN, BOTTOM, MIN, MAX, top, MAX)
        );
    }

    private static int colorFor(Fluid fluid) {
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

    private static void renderCuboid(PoseStack.Pose pose, VertexConsumer buffer, int color, float x0, float y0, float z0, float x1, float y1, float z1) {
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
