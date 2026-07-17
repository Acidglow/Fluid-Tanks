package acidglow.fluidtanks;

import acidglow.fluidtanks.client.FluidTankRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = AcidglowsFluidTanks.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = AcidglowsFluidTanks.MODID, value = Dist.CLIENT)
public class AcidglowsFluidTanksClient {
    public AcidglowsFluidTanksClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AcidglowsFluidTanks.FLUID_TANK_BLOCK_ENTITY.get(), FluidTankRenderer::new);
    }
}
