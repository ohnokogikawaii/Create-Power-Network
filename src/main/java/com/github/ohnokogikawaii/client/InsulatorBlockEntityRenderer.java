
package com.github.ohnokogikawaii.client;

import com.github.ohnokogikawaii.insulator.InsulatorBlock;
import com.github.ohnokogikawaii.insulator.InsulatorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

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

        if (!(state.getBlock() instanceof InsulatorBlock insulator)
                || insulator.getInsulatorType()
                != InsulatorBlock.Type.LOW_VOLTAGE) {
            return;
        }

        float grooveYaw = blockEntity.hasGrooveDirection()
                ? blockEntity.getGrooveYaw()
                : 0.0F;

        Direction facing = state.getValue(InsulatorBlock.FACING);

        float facingYaw = switch (facing) {
            case NORTH -> 0.0F;
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };

        // モデルの溝は初期状態でZ方向を向いている。
        // 電線の方向に合わせた回転角度を求める。
        float targetYaw = 90.0F - grooveYaw;

        // ブロックの基本向きからの追加回転量。
        float rotation = targetYaw - facingYaw;

        poseStack.pushPose();

        poseStack.translate(0.5, 0.0, 0.5);

        poseStack.mulPose(
                Axis.YP.rotationDegrees(-rotation)
        );

        poseStack.translate(-0.5, 0.0, -0.5);

        Minecraft.getInstance()
                .getBlockRenderer()
                .renderSingleBlock(
                        state,
                        poseStack,
                        bufferSource,
                        packedLight,
                        packedOverlay
                );

        poseStack.popPose();
    }
}
