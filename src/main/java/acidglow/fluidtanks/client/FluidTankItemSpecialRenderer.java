package acidglow.fluidtanks.client;

import acidglow.fluidtanks.AcidglowsFluidTanks;
import acidglow.fluidtanks.tank.FluidTankBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public class FluidTankItemSpecialRenderer implements SpecialModelRenderer<FluidTankItemSpecialRenderer.TankItemFluid> {
    @Override
    public void submit(
            @Nullable TankItemFluid argument,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            int overlayCoords,
            boolean hasFoil,
            int outlineColor
    ) {
        if (argument == null || argument.fluid() == Fluids.EMPTY || argument.fillRatio() <= 0.0F) {
            return;
        }

        float top = FluidTankRenderer.BOTTOM + (FluidTankRenderer.TOP - FluidTankRenderer.BOTTOM) * Math.min(1.0F, argument.fillRatio());
        submitNodeCollector.submitCustomGeometry(
                poseStack,
                RenderTypes.entityTranslucent(argument.texture()),
                (pose, buffer) -> FluidTankRenderer.renderCuboid(
                        pose,
                        buffer,
                        argument.color(),
                        FluidTankRenderer.MIN,
                        FluidTankRenderer.BOTTOM,
                        FluidTankRenderer.MIN,
                        FluidTankRenderer.MAX,
                        top,
                        FluidTankRenderer.MAX
                )
        );
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        output.accept(new Vector3f(0.0F, 0.0F, 0.0F));
        output.accept(new Vector3f(1.0F, 1.0F, 1.0F));
    }

    @Override
    public @Nullable TankItemFluid extractArgument(ItemStack stack) {
        FluidStack fluid = readStoredFluid(stack);
        if (fluid.isEmpty()) {
            return null;
        }

        int capacity = itemCapacity(stack);
        float fillRatio = capacity <= 0 ? 0.0F : (float) fluid.getAmount() / (float) capacity;
        Fluid fluidType = fluid.getFluid();
        return new TankItemFluid(
                fluidType,
                fillRatio,
                FluidTankRenderer.colorFor(fluidType),
                fluidType == Fluids.LAVA || fluidType == Fluids.FLOWING_LAVA ? FluidTankRenderer.LAVA_TEXTURE : FluidTankRenderer.WATER_TEXTURE
        );
    }

    private static FluidStack readStoredFluid(ItemStack stack) {
        TypedEntityData<BlockEntityType<?>> data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data == null || data.type() != AcidglowsFluidTanks.FLUID_TANK_BLOCK_ENTITY.get() || !data.contains("fluid")) {
            return FluidStack.EMPTY;
        }

        HolderLookup.Provider registries = Minecraft.getInstance().level == null
                ? RegistryAccess.EMPTY
                : Minecraft.getInstance().level.registryAccess();
        ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, registries, data.copyTagWithoutId());
        return input.read("fluid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
    }

    private static int itemCapacity(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof FluidTankBlock tankBlock) {
            return tankBlock.tier().capacity();
        }
        return 0;
    }

    public record TankItemFluid(Fluid fluid, float fillRatio, int color, net.minecraft.resources.Identifier texture) {
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked<TankItemFluid> {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public @Nullable SpecialModelRenderer<TankItemFluid> bake(SpecialModelRenderer.BakingContext context) {
            return new FluidTankItemSpecialRenderer();
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
