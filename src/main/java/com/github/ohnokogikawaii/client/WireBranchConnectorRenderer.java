
package com.github.ohnokogikawaii.client;

import com.github.ohnokogikawaii.PowerNetwork;
import com.github.ohnokogikawaii.PowerNetworkClient;
import com.github.ohnokogikawaii.wire.WireBranchConnectorEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class WireBranchConnectorRenderer
        extends EntityRenderer<WireBranchConnectorEntity> {

    /*
     * down_c_connector.json の connector パーツの中心:
     *
     * X = 8 / 16 = 0.5
     * Y = (2.4 + 4) / 2 / 16 = 0.2
     * Z = 8 / 16 = 0.5
     *
     * ブロック中央 (0.5, 0.5, 0.5) を原点にすると、
     * 端子中心は (0, -0.3, 0) になる。
     */
    private static final double MODEL_PORT_DISTANCE = 0.30;

    /*
     * JSONモデルの端子中心から、実際の分岐ポートまでの距離。
     * Entity側のPORT_OFFSETは0.45なので差分は0.15。
     */
    private static final double MODEL_OFFSET_CORRECTION = 0.15;

    private static final Vector3f MODEL_PORT_AXIS =
            new Vector3f(0.0F, -1.0F, 0.0F);

    private static final ResourceLocation MODEL_LOCATION =
            PowerNetworkClient.DOWN_C_CONNECTOR_MODEL;

    public WireBranchConnectorRenderer(
            EntityRendererProvider.Context context
    ) {
        super(context);
    }

    @Override
    public void render(
            WireBranchConnectorEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        BakedModel model = minecraft.getModelManager()
                .getModel(
                        ModelResourceLocation.standalone(
                                PowerNetworkClient.DOWN_C_CONNECTOR_MODEL
                        )
                );

        Vec3 origin = entity.position();

        Vec3 portWorld = entity.getBranchPortWorldPosition();
        Vec3 portDirection = portWorld.subtract(origin);

        if (portDirection.lengthSqr() < 1.0E-8) {
            portDirection = new Vec3(0.0, -1.0, 0.0);
        } else {
            portDirection = portDirection.normalize();
        }

        Quaternionf rotation = new Quaternionf().rotationTo(
                MODEL_PORT_AXIS,
                new Vector3f(
                        (float) portDirection.x,
                        (float) portDirection.y,
                        (float) portDirection.z
                )
        );

        poseStack.pushPose();

        /*
         * 端子中心を実際の分岐ポートに合わせるため、
         * モデルの回転方向に0.15ブロック補正する。
         */
        poseStack.translate(
                portDirection.x * MODEL_OFFSET_CORRECTION,
                portDirection.y * MODEL_OFFSET_CORRECTION,
                portDirection.z * MODEL_OFFSET_CORRECTION
        );

        poseStack.mulPose(rotation);

        /*
         * ブロックモデルは通常 0～1 の座標なので、
         * ブロック中央がエンティティ原点になるように移動。
         */
        poseStack.translate(-0.5, -0.5, -0.5);

        BlockRenderDispatcher blockRenderer =
                minecraft.getBlockRenderer();

        blockRenderer.getModelRenderer().tesselateBlock(
                entity.level(),
                model,
                Blocks.WHITE_WOOL.defaultBlockState(),
                BlockPos.containing(origin),
                poseStack,
                bufferSource.getBuffer(RenderType.cutoutMipped()),
                false,
                RandomSource.create(entity.getId()),
                entity.getId(),
                OverlayTexture.NO_OVERLAY
        );

        poseStack.popPose();

        super.render(
                entity,
                entityYaw,
                partialTick,
                poseStack,
                bufferSource,
                packedLight
        );
    }

    @Override
    public ResourceLocation getTextureLocation(
            WireBranchConnectorEntity entity
    ) {
        /*
         * テクスチャはベイク済みモデルの各面から参照される。
         * この戻り値はEntityRendererの必須実装として指定する。
         */
        return ResourceLocation.fromNamespaceAndPath(
                PowerNetwork.MODID,
                "textures/block/connector.png"
        );
    }
}
