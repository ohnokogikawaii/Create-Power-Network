
package com.github.ohnokogikawaii.client;

import com.github.ohnokogikawaii.insulator.InsulatorBlock;
import com.github.ohnokogikawaii.insulator.InsulatorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

public class InsulatorBlockEntityRenderer
        implements BlockEntityRenderer<InsulatorBlockEntity> {

    public InsulatorBlockEntityRenderer(
            BlockEntityRendererProvider.Context context
    ) {
    }

    @Override
    public void render(
            InsulatorBlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        BlockState state = blockEntity.getBlockState();

        // 低圧用碍子以外は描画しない
        if (!(state.getBlock() instanceof InsulatorBlock insulator)
                || insulator.getInsulatorType()
                != InsulatorBlock.Type.LOW_VOLTAGE) {
            return;
        }

        // 溝の角度
        float grooveYaw = blockEntity.hasGrooveDirection()
                ? blockEntity.getGrooveYaw()
                : 0.0F;

        // ブロックの基本向き
        Direction facing = state.getValue(InsulatorBlock.FACING);

        float facingYaw = switch (facing) {
            case NORTH -> 0.0F;
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };

        // モデルの溝方向に合わせた回転角度
        float targetYaw = 90.0F - grooveYaw;
        float rotation = targetYaw - facingYaw;

        poseStack.pushPose();

        try {
            // ブロック中央を回転軸にする
            poseStack.translate(0.5, 0.0, 0.5);
            poseStack.mulPose(
                    Axis.YP.rotationDegrees(rotation)
            );
            poseStack.translate(-0.5, 0.0, -0.5);

            // ベイク済みブロックモデルを取得
            var blockRenderer =
                    Minecraft.getInstance().getBlockRenderer();

            BakedModel model =
                    blockRenderer.getBlockModel(state);

            // 不透明ブロック用の描画レイヤー
            RenderType renderType = RenderType.solid();

            // モデルを直接描画
            blockRenderer.getModelRenderer().renderModel(
                    poseStack.last(),
                    bufferSource.getBuffer(renderType),
                    state,
                    model,
                    1.0F,
                    1.0F,
                    1.0F,
                    packedLight,
                    packedOverlay,
                    ModelData.EMPTY,
                    renderType
            );

        } finally {
            poseStack.popPose();
        }
    }
}
