package acidglow.fluidtanks;

import acidglow.fluidtanks.tank.FluidTankBlock;
import acidglow.fluidtanks.tank.FluidTankBlockItem;
import acidglow.fluidtanks.tank.FluidTankBlockEntity;
import acidglow.fluidtanks.tank.FluidTankTier;
import acidglow.fluidtanks.tank.CopperConnectedTextureDebug;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(AcidglowsFluidTanks.MODID)
public class AcidglowsFluidTanks {
    public static final String MODID = "acidglowsfluidtanks";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);

    public static final Map<FluidTankTier, DeferredBlock<FluidTankBlock>> TANK_BLOCKS = new EnumMap<>(FluidTankTier.class);
    public static final Map<FluidTankTier, DeferredItem<BlockItem>> TANK_ITEMS = new EnumMap<>(FluidTankTier.class);
    public static final DeferredItem<Item> WRENCH = ITEMS.registerSimpleItem("wrench");

    static {
        for (FluidTankTier tier : FluidTankTier.values()) {
            DeferredBlock<FluidTankBlock> block = BLOCKS.registerBlock(tier.id(), properties -> new FluidTankBlock(
                    tier,
                    properties
                            .mapColor(MapColor.COLOR_LIGHT_BLUE)
                            .strength(0.6F)
                            .sound(SoundType.GLASS)
                            .noOcclusion()
                            .isValidSpawn((state, level, pos, entityType) -> false)
                            .isRedstoneConductor((state, level, pos) -> false)
                            .isSuffocating((state, level, pos) -> false)
                            .isViewBlocking((state, level, pos) -> false)
            ));
            TANK_BLOCKS.put(tier, block);
            TANK_ITEMS.put(tier, ITEMS.registerItem(tier.id(), properties -> new FluidTankBlockItem(block.get(), properties.useBlockDescriptionPrefix())));
        }
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidTankBlockEntity>> FLUID_TANK_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("fluid_tank", () -> new BlockEntityType<>(
                    FluidTankBlockEntity::new,
                    Set.copyOf(TANK_BLOCKS.values().stream().map(DeferredBlock::get).map(Block.class::cast).toList())
            ));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FLUID_TANKS_TAB = CREATIVE_MODE_TABS.register("fluid_tanks", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.acidglowsfluidtanks"))
            .withTabsBefore(CreativeModeTabs.FUNCTIONAL_BLOCKS)
            .icon(() -> TANK_ITEMS.get(FluidTankTier.IRON).get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(WRENCH.get());
                Arrays.stream(FluidTankTier.values()).forEach(tier -> output.accept(TANK_ITEMS.get(tier).get()));
            })
            .build());

    public AcidglowsFluidTanks(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        modEventBus.addListener(this::registerCapabilities);
        NeoForge.EVENT_BUS.addListener(CopperConnectedTextureDebug::registerCommands);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                FLUID_TANK_BLOCK_ENTITY.get(),
                (tank, direction) -> tank.fluidHandler()
        );
    }
}
