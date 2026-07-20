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
        if (argument == null || argument.fillRatio() <= 0.0F) {
            return;
        }

        submitNodeCollector.submitCustomGeometry(
                poseStack,
                RenderTypes.entityTranslucent(argument.appearance().texture()),
                (pose, buffer) -> FluidTankRenderer.renderStandaloneFluid(
                        pose,
                        buffer,
                        argument.appearance().color(),
                        argument.fillRatio(),
                        argument.appearance().u0(),
                        argument.appearance().v0(),
                        argument.appearance().u1(),
                        argument.appearance().v1()
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
        return new TankItemFluid(
                fillRatio,
                FluidTankRenderer.fluidAppearance(fluid)
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

    public record TankItemFluid(float fillRatio, FluidTankRenderer.FluidAppearance appearance) {
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
