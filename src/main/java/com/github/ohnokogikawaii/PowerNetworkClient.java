
package com.github.ohnokogikawaii;

import com.github.ohnokogikawaii.client.WireBranchConnectorRenderer;
import com.github.ohnokogikawaii.client.WireEntityRenderer;
import com.github.ohnokogikawaii.registry.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(
        modid = PowerNetwork.MODID,
        bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public final class PowerNetworkClient {

    private PowerNetworkClient() {
    }

    @SubscribeEvent
    public static void registerEntityRenderers(
            EntityRenderersEvent.RegisterRenderers event
    ) {
        event.registerEntityRenderer(
                ModEntities.WIRE.get(),
                WireEntityRenderer::new
        );

        event.registerEntityRenderer(
                ModEntities.WIRE_BRANCH_CONNECTOR.get(),
                WireBranchConnectorRenderer::new
        );
    }
}