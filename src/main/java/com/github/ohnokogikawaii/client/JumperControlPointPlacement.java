package com.github.ohnokogikawaii.client;

import com.github.ohnokogikawaii.PowerNetwork;
import com.github.ohnokogikawaii.network.JumperControlPointNetwork;
import com.github.ohnokogikawaii.wire.JumperWireGeometry;
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
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(
        modid = PowerNetwork.MODID,
        bus = EventBusSubscriber.Bus.GAME,
        value = Dist.CLIENT
)
public final class JumperControlPointPlacement {

    private static WireEntity targetWire;

    private static Vec3 previewPosition = Vec3.ZERO;

    private static boolean active = false;
    private static boolean moving = false;

    private static Vec3 previousPlayerPosition;

    private static long firstClickTime = -1L;

    private static final long DOUBLE_CLICK_INTERVAL = 350L;

    /*
     * マウス移動1ピクセルあたりの高さ変化量。
     * 100ピクセルの移動で約1ブロック。
     */
    private static final double HEIGHT_SENSITIVITY = 0.007;

    private JumperControlPointPlacement() {
    }

    /**
     * 制御点配置モードを開始する。
     */
    public static void start(WireEntity wire) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;

        if (player == null
                || wire == null
                || wire.isRemoved()
                || !wire.isJumperWire()) {
            return;
        }

        if (wire.getJumperControlPoints().size() >= 16) {
            return;
        }

        targetWire = wire;

        previewPosition = player.getEyePosition()
                .add(player.getLookAngle().scale(2.5));

        active = true;
        moving = false;

        previousPlayerPosition = player.position();

        firstClickTime = -1L;
    }

    /**
     * 配置モードを終了する。
     */
    private static void stop() {
        active = false;
        moving = false;

        targetWire = null;
        previousPlayerPosition = null;

        firstClickTime = -1L;
    }

    /**
     * Mixinから呼び出す。
     * マウスの上下移動量をプレビュー位置に反映する。
     */
    public static void applyMouseVerticalDelta(double deltaY) {
        if (!active || !moving || !Double.isFinite(deltaY)) {
            return;
        }

        previewPosition = previewPosition.add(
                0.0,
                -deltaY * HEIGHT_SENSITIVITY,
                0.0
        );
    }

    /**
     * 右クリック長押し・左クリックを処理する。
     */
    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (!active) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            event.setCanceled(true);

            if (event.getAction() == GLFW.GLFW_PRESS) {
                Player player = minecraft.player;

                if (player == null) {
                    return;
                }

                moving = true;
                previousPlayerPosition = player.position();
                firstClickTime = -1L;
            }

            if (event.getAction() == GLFW.GLFW_RELEASE) {
                moving = false;

                if (minecraft.player != null) {
                    previousPlayerPosition =
                            minecraft.player.position();
                }
            }

            return;
        }

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && event.getAction() == GLFW.GLFW_PRESS) {

            event.setCanceled(true);

            if (moving || targetWire == null || targetWire.isRemoved()) {
                return;
            }

            long now = System.currentTimeMillis();

            if (firstClickTime >= 0
                    && now - firstClickTime <= DOUBLE_CLICK_INTERVAL) {

                confirmPlacement();

            } else {
                firstClickTime = now;
            }
        }
    }

    /**
     * Escで配置モードをキャンセルする。
     */
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (!active) {
            return;
        }

        if (event.getKey() == GLFW.GLFW_KEY_ESCAPE
                && event.getAction() == GLFW.GLFW_PRESS) {

            stop();
        }
    }

    /**
     * プレイヤーの移動とプレビュー位置を同期する。
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!active) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;

        if (player == null
                || minecraft.level == null
                || targetWire == null
                || targetWire.isRemoved()) {

            stop();
            return;
        }

        if (!moving) {
            previousPlayerPosition = player.position();
            return;
        }

        /*
         * プレイヤーの実際の移動量を取得する。
         * 箱も同じ量だけ移動させる。
         */
        Vec3 currentPosition = player.position();

        if (previousPlayerPosition != null) {
            Vec3 movement = currentPosition.subtract(
                    previousPlayerPosition
            );

            previewPosition = previewPosition.add(movement);
        }

        previousPlayerPosition = currentPosition;
    }

    /**
     * 制御点をサーバーへ確定要求する。
     */
    private static void confirmPlacement() {
        if (!active
                || moving
                || targetWire == null
                || targetWire.isRemoved()) {
            return;
        }

        if (targetWire.getJumperControlPoints().size() >= 16) {
            stop();
            return;
        }

        JumperControlPointNetwork.requestAddControlPoint(
                targetWire.getId(),
                previewPosition
        );

        WireEntity wire = targetWire;

        stop();

        Minecraft.getInstance().setScreen(
                new JumperWireEditorScreen(wire)
        );
    }


    /**
     * ワールド上に制御点の箱とジャンパ線の仮プレビューを描画する。
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!active
                || event.getStage()
                != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();

        if (poseStack == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        Vec3 cameraPosition = event.getCamera().getPosition();

        poseStack.pushPose();

        /*
         * ワールド座標をカメラ基準の描画座標に変換する。
         */
        poseStack.translate(
                -cameraPosition.x,
                -cameraPosition.y,
                -cameraPosition.z
        );

        var bufferSource =
                minecraft.renderBuffers().bufferSource();

        VertexConsumer consumer =
                bufferSource.getBuffer(RenderType.lines());

        PoseStack.Pose pose = poseStack.last();

        /*
         * 右クリックを離している間は、
         * 仮の制御点を通るジャンパ線を描画する。
         */
        if (!moving
                && targetWire != null
                && !targetWire.isRemoved()) {

            /*
             * previewPositionは描画上の箱の中心位置。
             *
             * JumperWireGeometry側で+0.5Yするため、
             * ここでは0.5Y引いて保存時と同じ座標に戻す。
             */
            Vec3 storedPreviewPosition = previewPosition.add(
                    0.0,
                    -0.5,
                    0.0
            );

            /*
             * 既存の制御点と仮の制御点を使用して曲線を生成する。
             * この処理では実際のワイヤーデータは変更しない。
             */
            var previewPath = JumperWireGeometry.getPath(
                    targetWire,
                    storedPreviewPosition
            );

            /*
             * 曲線の各区間を半透明の水色で描画する。
             */
            for (int i = 0; i < previewPath.size() - 1; i++) {

                Vec3 start = previewPath.get(i);
                Vec3 end = previewPath.get(i + 1);

                consumer.addVertex(
                                pose,
                                (float) start.x,
                                (float) start.y,
                                (float) start.z
                        )
                        .setColor(100, 200, 255, 180)
                        .setNormal(pose, 0.0F, 1.0F, 0.0F);

                consumer.addVertex(
                                pose,
                                (float) end.x,
                                (float) end.y,
                                (float) end.z
                        )
                        .setColor(100, 200, 255, 180)
                        .setNormal(pose, 0.0F, 1.0F, 0.0F);
            }
        }

        /*
         * 制御点のプレビュー箱を描画する。
         */
        double halfSize = 0.10;

        AABB box = new AABB(
                previewPosition.x - halfSize,
                previewPosition.y - halfSize,
                previewPosition.z - halfSize,
                previewPosition.x + halfSize,
                previewPosition.y + halfSize,
                previewPosition.z + halfSize
        );

        float red = moving ? 1.0F : 0.2F;
        float green = moving ? 0.5F : 1.0F;
        float blue = 0.1F;

        LevelRenderer.renderLineBox(
                poseStack,
                consumer,
                box,
                red,
                green,
                blue,
                0.9F
        );

        poseStack.popPose();

        /*
         * 線と箱の描画をまとめて確定する。
         */
        bufferSource.endBatch(RenderType.lines());
    }


    public static boolean isActive() {
        return active;
    }

    public static boolean isMoving() {
        return active && moving;
    }

    public static Vec3 getPreviewPosition() {
        return previewPosition;
    }
}