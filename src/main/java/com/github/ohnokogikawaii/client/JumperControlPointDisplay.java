
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

        List<WireEntity> wires =
                minecraft.level.getEntitiesOfClass(
                        WireEntity.class,
                        player.getBoundingBox().inflate(DISPLAY_RANGE),
                        wire -> wire.isAlive() && wire.isJumperWire()
                );

        if (wires.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();

        if (poseStack == null) {
            return;
        }

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

                // Geometryと同じ描画座標に合わせる。
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

                // 制御点の箱
                VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());

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
        /*
         * 編集機を持った状態で照準が合っている制御点だけ、
         * 箱の上に固有番号を表示する。
         */
        JumperControlPointPlacement.ControlPointAimTarget aimTarget =
                JumperControlPointPlacement.findAimTarget(
                        minecraft.level,
                        player
                );

        if (aimTarget != null) {

            WireEntity aimedWire = aimTarget.wire();
            int aimedIndex = aimTarget.index();

            List<Integer> aimedIds =
                    aimedWire.getJumperControlPointIds();

            if (aimedIndex >= 0
                    && aimedIndex < aimedIds.size()) {

                Vec3 position = aimTarget.position();

                DebugRenderer.renderFloatingText(
                        poseStack,
                        bufferSource,
                        String.valueOf(aimedIds.get(aimedIndex)),
                        position.x - cameraPosition.x,
                        position.y + 0.25 - cameraPosition.y,
                        position.z - cameraPosition.z,
                        0xFFFFFF,
                        0.04F,
                        true,
                        0.0F,
                        false
                );
            }
        }

        poseStack.popPose();

        bufferSource.endBatch(RenderType.lines());
    }
}
