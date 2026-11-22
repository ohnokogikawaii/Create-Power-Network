
package com.github.ohnokogikawaii.client;

import com.github.ohnokogikawaii.PowerNetwork;
import com.github.ohnokogikawaii.wire.WireCutterItem;
import com.github.ohnokogikawaii.wire.WireEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(
        modid = PowerNetwork.MODID,
        value = Dist.CLIENT
)
public final class WireCutterAimHighlight {

    private static final int CUTTABLE_COLOR = 0x35E65A;
    private static final int UNCUTTABLE_COLOR = 0xF04444;

    // 当たり判定の半径
    private static final double HITBOX_RADIUS = 0.06;

    // 輪郭の分割数
    private static final int RADIAL_SEGMENTS = 12;

    private WireCutterAimHighlight() {
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {

        if (event.getStage()
                != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        if (!isHoldingWireCutter(minecraft)) {
            return;
        }

        WireCutterItem.WireAimTarget target =
                WireCutterItem.findAimTarget(
                        minecraft.level,
                        minecraft.player
                );

        if (target == null || !target.wire().isAlive()) {
            return;
        }

        int color = target.canCut()
                ? CUTTABLE_COLOR
                : UNCUTTABLE_COLOR;

        renderWireHitbox(
                event,
                target.wire(),
                color
        );
    }

    private static boolean isHoldingWireCutter(
            Minecraft minecraft
    ) {
        ItemStack mainHand =
                minecraft.player.getMainHandItem();

        ItemStack offHand =
                minecraft.player.getOffhandItem();

        return mainHand.getItem() instanceof WireCutterItem
                || offHand.getItem() instanceof WireCutterItem;
    }

    /**
     * 電線の経路に沿って丸みのある輪郭を描画する。
     */
    private static void renderWireHitbox(
            RenderLevelStageEvent event,
            WireEntity wire,
            int rgb
    ) {
        List<Vec3> points =
                WireCutterItem.getAimPath(wire);

        if (points.size() < 2) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        Camera camera = event.getCamera();
        Vec3 cameraPosition = camera.getPosition();

        PoseStack poseStack = event.getPoseStack();

        float red = ((rgb >> 16) & 0xFF) / 255.0F;
        float green = ((rgb >> 8) & 0xFF) / 255.0F;
        float blue = (rgb & 0xFF) / 255.0F;

        poseStack.pushPose();

        poseStack.translate(
                -cameraPosition.x,
                -cameraPosition.y,
                -cameraPosition.z
        );

        var bufferSource =
                minecraft.renderBuffers().bufferSource();

        VertexConsumer consumer =
                bufferSource.getBuffer(RenderType.lines());

        /*
         * 各中心点の周囲に8角形の輪郭を生成する。
         */
        List<List<Vec3>> rings = new ArrayList<>();

        for (int i = 0; i < points.size(); i++) {

            Vec3 tangent = getTangent(points, i);

            Vec3 reference = Math.abs(tangent.y) < 0.95
                    ? new Vec3(0, 1, 0)
                    : new Vec3(1, 0, 0);

            Vec3 side = tangent.cross(reference).normalize();
            Vec3 up = tangent.cross(side).normalize();

            List<Vec3> ring = new ArrayList<>();

            for (int j = 0; j < RADIAL_SEGMENTS; j++) {

                double angle =
                        2.0 * Math.PI * j / RADIAL_SEGMENTS;

                Vec3 offset = side.scale(
                        Math.cos(angle) * HITBOX_RADIUS
                ).add(
                        up.scale(
                                Math.sin(angle) * HITBOX_RADIUS
                        )
                );

                ring.add(points.get(i).add(offset));
            }

            rings.add(ring);
        }

        /*
         * 電線に沿った縦方向の輪郭を描画する。
         */
        for (int i = 0; i < rings.size() - 1; i++) {

            List<Vec3> current = rings.get(i);
            List<Vec3> next = rings.get(i + 1);

            for (int j = 0; j < RADIAL_SEGMENTS; j++) {

                drawLine(
                        consumer,
                        poseStack.last(),
                        current.get(j),
                        next.get(j),
                        red,
                        green,
                        blue
                );
            }
        }

        /*
         * 輪切り方向の輪郭を一定間隔で描画する。
         */
        for (int i = 0; i < rings.size(); i++) {

            List<Vec3> ring = rings.get(i);

            // 輪郭線が多くなりすぎないように間引く。
            if (i != 0 && i != rings.size() - 1
                    && i % 4 != 0) {
                continue;
            }

            for (int j = 0; j < RADIAL_SEGMENTS; j++) {

                drawLine(
                        consumer,
                        poseStack.last(),
                        ring.get(j),
                        ring.get((j + 1) % RADIAL_SEGMENTS),
                        red,
                        green,
                        blue
                );
            }
        }

        poseStack.popPose();

        bufferSource.endBatch(RenderType.lines());
    }

    /**
     * 経路上の接線方向を取得する。
     */
    private static Vec3 getTangent(
            List<Vec3> points,
            int index
    ) {
        Vec3 tangent;

        if (index == 0) {
            tangent = points.get(1).subtract(points.get(0));
        } else if (index == points.size() - 1) {
            tangent = points.get(index)
                    .subtract(points.get(index - 1));
        } else {
            tangent = points.get(index + 1)
                    .subtract(points.get(index - 1));
        }

        if (tangent.lengthSqr() < 1.0E-8) {
            return new Vec3(1, 0, 0);
        }

        return tangent.normalize();
    }

    /**
     * 1本の線を描画する。
     */
    private static void drawLine(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vec3 start,
            Vec3 end,
            float red,
            float green,
            float blue
    ) {
        consumer.addVertex(
                        pose,
                        (float) start.x,
                        (float) start.y,
                        (float) start.z
                )
                .setColor(red, green, blue, 1.0F)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);

        consumer.addVertex(
                        pose,
                        (float) end.x,
                        (float) end.y,
                        (float) end.z
                )
                .setColor(red, green, blue, 1.0F)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
