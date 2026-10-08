package com.github.ohnokogikawaii;

import com.github.ohnokogikawaii.client.WireEntityRenderer;
import com.github.ohnokogikawaii.registry.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@Mod(value = PowerNetwork.MODID, dist = Dist.CLIENT)
public class PowerNetworkClient {

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.WIRE.get(), WireEntityRenderer::new);
    }
}
