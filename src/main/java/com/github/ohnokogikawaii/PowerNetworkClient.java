
package com.github.ohnokogikawaii;

import com.github.ohnokogikawaii.client.WireBranchConnectorRenderer;
import com.github.ohnokogikawaii.client.WireEntityRenderer;
import com.github.ohnokogikawaii.registry.ModEntities;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

@EventBusSubscriber(
        modid = PowerNetwork.MODID,
        bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public final class PowerNetworkClient {

    /**
     * 分岐コネクタの単独モデル。
     *
     * 対応ファイル:
     * assets/powernetwork/models/block/down_c_connector.json
     */
    public static final ResourceLocation DOWN_C_CONNECTOR_MODEL =
            ResourceLocation.fromNamespaceAndPath(
                    PowerNetwork.MODID,
                    "block/down_c_connector"
            );

    private PowerNetworkClient() {
    }

    /**
     * エンティティレンダラーを登録する。
     */
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

    /**
     * 分岐コネクタのモデルを追加登録する。
     *
     * standalone として登録するため、
     * レンダラー側でも standalone のモデル位置を参照する。
     */
    @SubscribeEvent
    public static void registerAdditionalModels(
            ModelEvent.RegisterAdditional event
    ) {
        event.register(
                ModelResourceLocation.standalone(
                        DOWN_C_CONNECTOR_MODEL
                )
        );
    }
}
