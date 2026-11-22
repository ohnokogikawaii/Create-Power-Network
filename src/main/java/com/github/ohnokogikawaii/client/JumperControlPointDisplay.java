
package com.github.ohnokogikawaii.client;

import com.github.ohnokogikawaii.PowerNetwork;
import com.github.ohnokogikawaii.registry.ModItems;
import com.github.ohnokogikawaii.wire.WireEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;

@EventBusSubscriber(
        modid = PowerNetwork.MODID,
        bus = EventBusSubscriber.Bus.GAME,
        value = Dist.CLIENT
)
public final class JumperControlPointDisplay {

    private static final double DISPLAY_RANGE = 32.0;
    private static final double BOX_HALF_SIZE = 0.11;

    private JumperControlPointDisplay() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {

        if (event.getStage()
                != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;

        if (player == null || minecraft.level == null) {
            return;
        }

        // 編集機をメインハンドまたはオフハンドに持っているか
        boolean holdingEditor =
                player.getMainHandItem().is(
                        ModItems.JUMPER_WIRE_EDITOR.get()
                )
                        || player.getOffhandItem().is(
                        ModItems.JUMPER_WIRE_EDITOR.get()
                );

        if (!holdingEditor) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();

        if (poseStack == null) {
            return;
        }

        // 照準が合っている制御点を取得
        JumperControlPointPlacement.ControlPointAimTarget aimTarget =
                JumperControlPointPlacement.findAimTarget(
                        minecraft.level,
                        player
                );

        // 周囲のジャンパ線を取得
        List<WireEntity> wires =
                minecraft.level.getEntitiesOfClass(
                        WireEntity.class,
                        player.getBoundingBox().inflate(DISPLAY_RANGE),
                        wire -> wire.isAlive() && wire.isJumperWire()
                );

        if (wires.isEmpty()) {
            return;
        }

        Vec3 cameraPosition = event.getCamera().getPosition();

        poseStack.pushPose();

        // カメラ位置を基準に描画
        poseStack.translate(
                -cameraPosition.x,
                -cameraPosition.y,
                -cameraPosition.z
        );

        var bufferSource =
                minecraft.renderBuffers().bufferSource();

        // ==========================================
        // 制御点の箱を描画
        // ==========================================

        for (WireEntity wire : wires) {

            List<Vec3> points = wire.getJumperControlPoints();

            for (int i = 0; i < points.size(); i++) {

                // 移動中の制御点は元の位置に描画しない
                if (JumperControlPointPlacement.isEditing(wire, i)) {
                    continue;
                }

                Vec3 position = points.get(i).add(
                        0.0,
                        0.5,
                        0.0
                );

                AABB box = new AABB(
                        position.x - BOX_HALF_SIZE,
                        position.y - BOX_HALF_SIZE,
                        position.z - BOX_HALF_SIZE,
                        position.x + BOX_HALF_SIZE,
                        position.y + BOX_HALF_SIZE,
                        position.z + BOX_HALF_SIZE
                );

                // 現在照準が合っている箱か
                boolean highlighted =
                        aimTarget != null
                                && aimTarget.wire() == wire
                                && aimTarget.index() == i;

                VertexConsumer consumer =
                        bufferSource.getBuffer(RenderType.lines());

                if (highlighted) {

                    // 選択中の箱は黄色
                    LevelRenderer.renderLineBox(
                            poseStack,
                            consumer,
                            box,
                            1.0F,
                            0.9F,
                            0.1F,
                            1.0F
                    );

                } else {

                    // 通常の箱は水色
                    LevelRenderer.renderLineBox(
                            poseStack,
                            consumer,
                            box,
                            0.2F,
                            0.8F,
                            1.0F,
                            1.0F
                    );
                }
            }
        }

        // 箱の描画を先に確定
        bufferSource.endBatch(RenderType.lines());

        // ==========================================
        // 選択中の制御点番号を描画
        // ==========================================

        if (aimTarget != null) {

            WireEntity aimedWire = aimTarget.wire();
            int aimedIndex = aimTarget.index();

            List<Integer> pointIds =
                    aimedWire.getJumperControlPointIds();

            if (aimedWire.isAlive()
                    && aimedIndex >= 0
                    && aimedIndex < pointIds.size()
                    && !JumperControlPointPlacement.isEditing(
                    aimedWire,
                    aimedIndex
            )) {

                Vec3 position = aimTarget.position();

                String number =
                        String.valueOf(pointIds.get(aimedIndex));

                /*
                 * DebugRendererはPoseStackにカメラ移動が
                 * 適用されている状態で使用する。
                 * そのため座標はワールド座標を渡す。
                 */
                DebugRenderer.renderFloatingText(
                        poseStack,
                        bufferSource,
                        number,
                        position.x,
                        position.y + 0.30,
                        position.z,
                        0xFFFFFF,
                        0.05F,
                        true,
                        0.0F,
                        true
                );
            }
        }

        // 番号の描画を確定
        bufferSource.endBatch();

        poseStack.popPose();
    }
}
