
package com.github.ohnokogikawaii.client;

import com.github.ohnokogikawaii.PowerNetwork;
import com.github.ohnokogikawaii.registry.ModItems;
import com.github.ohnokogikawaii.wire.WireEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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

        if (wires.isEmpty()) {
            return;
        }

        Vec3 cameraPosition = event.getCamera().getPosition();

        var bufferSource =
                minecraft.renderBuffers().bufferSource();

        // ==========================================
        // 制御点の箱を描画
        // ==========================================

        poseStack.pushPose();

        poseStack.translate(
                -cameraPosition.x,
                -cameraPosition.y,
                -cameraPosition.z
        );

        for (WireEntity wire : wires) {

            List<Vec3> points = wire.getJumperControlPoints();

            for (int i = 0; i < points.size(); i++) {

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

        bufferSource.endBatch(RenderType.lines());
        poseStack.popPose();

        // ==========================================
        // すべての制御点番号を描画
        // ==========================================

        Font font = minecraft.font;

        for (WireEntity wire : wires) {

            if (!wire.isAlive()) {
                continue;
            }

            List<Vec3> points = wire.getJumperControlPoints();
            List<Integer> pointIds =
                    wire.getJumperControlPointIds();

            int count = Math.min(points.size(), pointIds.size());

            for (int i = 0; i < count; i++) {

                if (JumperControlPointPlacement.isEditing(wire, i)) {
                    continue;
                }

                Vec3 position = points.get(i).add(
                        0.0,
                        0.5,
                        0.0
                );

                String number =
                        String.valueOf(pointIds.get(i));

                // 箱の中心から少し上に配置
                Vec3 textPosition = new Vec3(
                        position.x - cameraPosition.x,
                        position.y + 0.30 - cameraPosition.y,
                        position.z - cameraPosition.z
                );

                poseStack.pushPose();

                poseStack.translate(
                        textPosition.x,
                        textPosition.y,
                        textPosition.z
                );

                // カメラに正面を向ける
                poseStack.mulPose(
                        event.getCamera().rotation()
                );

                poseStack.scale(
                        -0.025F,
                        -0.025F,
                        0.025F
                );

                float textWidth = font.width(number);

                font.drawInBatch(
                        number,
                        -textWidth / 2.0F,
                        0.0F,
                        0xFFFFFFFF,
                        true,
                        poseStack.last().pose(),
                        bufferSource,
                        Font.DisplayMode.SEE_THROUGH,
                        0x00000000,
                        15728880
                );

                poseStack.popPose();
            }
        }

        // すべての番号を描画確定
        bufferSource.endBatch();
    }
}
