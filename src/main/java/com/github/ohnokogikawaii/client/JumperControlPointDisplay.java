
package com.github.ohnokogikawaii.client;

import com.github.ohnokogikawaii.PowerNetwork;
import com.github.ohnokogikawaii.registry.ModItems;
import com.github.ohnokogikawaii.wire.WireEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
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

    /**
     * 編集機を持っているか確認する。
     */
    private static boolean isHoldingEditor(Player player) {
        return player.getMainHandItem().is(
                ModItems.JUMPER_WIRE_EDITOR.get()
        ) || player.getOffhandItem().is(
                ModItems.JUMPER_WIRE_EDITOR.get()
        );
    }

    /**
     * 表示範囲内のジャンパ線を取得する。
     */
    private static List<WireEntity> getDisplayWires(
            Minecraft minecraft,
            Player player
    ) {
        return minecraft.level.getEntitiesOfClass(
                WireEntity.class,
                player.getBoundingBox().inflate(DISPLAY_RANGE),
                wire -> wire.isAlive() && wire.isJumperWire()
        );
    }

    /**
     * ワールド上に制御点の箱を描画する。
     */
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

        if (!isHoldingEditor(player)) {
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
                getDisplayWires(minecraft, player);

        if (wires.isEmpty()) {
            return;
        }

        Vec3 cameraPosition = event.getCamera().getPosition();

        var bufferSource =
                minecraft.renderBuffers().bufferSource();

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
    }

    /**
     * HUD上に制御点番号を表示する。
     *
     * ワールド空間の文字描画を使わず、
     * GuiGraphicsで直接画面に描画する。
     */

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;

        if (player == null || minecraft.level == null) {
            return;
        }

        if (!isHoldingEditor(player)) {
            return;
        }

        List<WireEntity> wires =
                getDisplayWires(minecraft, player);

        if (wires.isEmpty()) {
            return;
        }

        List<String> displayLines = new ArrayList<>();

        for (WireEntity wire : wires) {

            List<Vec3> points = wire.getJumperControlPoints();

            if (points.isEmpty()) {
                continue;
            }

            StringBuilder line = new StringBuilder();

            line.append("ジャンパ線 ")
                    .append(wire.getId())
                    .append(" : 始点");

            int displayedNumber = 0;

            for (int i = 0; i < points.size(); i++) {

                if (JumperControlPointPlacement.isEditing(wire, i)) {
                    continue;
                }

                displayedNumber++;

                line.append(" → ")
                        .append(displayedNumber);
            }

            if (displayedNumber > 0) {
                line.append(" → 終点");
                displayLines.add(line.toString());
            }
        }

        if (displayLines.isEmpty()) {
            return;
        }

        var guiGraphics = event.getGuiGraphics();
        var font = minecraft.font;

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();

        int centerX = screenWidth / 2;
        int baseY = screenHeight - 70;

        for (int i = 0; i < displayLines.size(); i++) {

            String line = displayLines.get(i);

            int textWidth = font.width(line);
            int textHeight = 9;

            int x = centerX - textWidth / 2;
            int y = baseY - i * 13;

            guiGraphics.fill(
                    x - 5,
                    y - 3,
                    x + textWidth + 5,
                    y + textHeight + 3,
                    0xB0000000
            );

            guiGraphics.drawCenteredString(
                    font,
                    line,
                    centerX,
                    y,
                    0xFFFFFF
            );
        }
    }

}
