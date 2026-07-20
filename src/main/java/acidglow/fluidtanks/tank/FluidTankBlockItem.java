package acidglow.fluidtanks.tank;

import acidglow.fluidtanks.AcidglowsFluidTanks;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.neoforged.neoforge.fluids.FluidStack;

public class FluidTankBlockItem extends BlockItem {
    public FluidTankBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack itemStack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag tooltipFlag) {
        super.appendHoverText(itemStack, context, display, builder, tooltipFlag);

        FluidStack fluid = storedFluid(itemStack, context.registries());
        if (!fluid.isEmpty() && getBlock() instanceof FluidTankBlock tankBlock) {
            builder.accept(Component.empty()
                    .append(fluid.getHoverName())
                    .append(Component.literal(": " + fluid.getAmount() + "/" + tankBlock.tier().capacity() + "mb"))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static FluidStack storedFluid(ItemStack itemStack, HolderLookup.Provider registries) {
        if (registries == null) {
            return FluidStack.EMPTY;
        }

        TypedEntityData<BlockEntityType<?>> data = itemStack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data == null || data.type() != AcidglowsFluidTanks.FLUID_TANK_BLOCK_ENTITY.get() || !data.contains("fluid")) {
            return FluidStack.EMPTY;
        }

        ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, registries, data.copyTagWithoutId());
        return input.read("fluid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
    }
}
