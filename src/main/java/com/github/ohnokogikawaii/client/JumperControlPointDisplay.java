
package com.github.ohnokogikawaii.client;

import com.github.ohnokogikawaii.PowerNetwork;
import com.github.ohnokogikawaii.registry.ModItems;
import com.github.ohnokogikawaii.wire.WireEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
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

        /*
         * 照準が合っている制御点を取得する。
         * 箱のハイライトと番号表示の両方で同じ判定を使用する。
         */
        JumperControlPointPlacement.ControlPointAimTarget aimTarget =
                JumperControlPointPlacement.findAimTarget(
                        minecraft.level,
                        player
                );

        List<WireEntity> wires =
                minecraft.level.getEntitiesOfClass(
                        WireEntity.class,
                        player.getBoundingBox().inflate(DISPLAY_RANGE),
                        wire -> wire.isAlive() && wire.isJumperWire()
                );

        Vec3 cameraPosition = event.getCamera().getPosition();

        poseStack.pushPose();

        poseStack.translate(
                -cameraPosition.x,
                -cameraPosition.y,
                -cameraPosition.z
        );

        var bufferSource =
                minecraft.renderBuffers().bufferSource();

        for (WireEntity wire : wires) {

            List<Vec3> points = wire.getJumperControlPoints();
            List<Integer> pointIds = wire.getJumperControlPointIds();

            for (int i = 0; i < points.size(); i++) {

                // 現在移動中の制御点は元の場所に描画しない。
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

                boolean highlighted =
                        aimTarget != null
                                && aimTarget.wire() == wire
                                && aimTarget.index() == i;

                VertexConsumer consumer =
                        bufferSource.getBuffer(RenderType.lines());

                /*
                 * 照準が合っている箱は黄色、
                 * それ以外は従来どおり水色。
                 */
                if (highlighted) {

                    LevelRenderer.renderLineBox(
                            poseStack,
                            consumer,
                            box,
                            1.0F,
                            0.9F,
                            0.1F,
                            1.0F
                    );

                    /*
                     * ハイライトされた箱と同時に番号を表示する。
                     * poseStackですでにカメラ移動しているため、
                     * ここにはワールド座標をそのまま渡す。
                     */
                    if (i < pointIds.size()) {

                        DebugRenderer.renderFloatingText(
                                poseStack,
                                bufferSource,
                                String.valueOf(pointIds.get(i)),
                                position.x,
                                position.y + 0.25,
                                position.z,
                                0xFFFFFF,
                                0.04F,
                                true,
                                0.0F,
                                false
                        );
                    }

                } else {

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

        poseStack.popPose();

        /*
         * 箱と番号の描画バッファを確定する。
         */
        bufferSource.endBatch();
    }
}
