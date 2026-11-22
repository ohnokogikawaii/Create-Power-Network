package com.github.ohnokogikawaii.client;

import com.github.ohnokogikawaii.PowerNetwork;
import com.github.ohnokogikawaii.network.JumperControlPointNetwork;
import com.github.ohnokogikawaii.wire.WireEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
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

    private static float lockedYaw;
    private static float lockedPitch;

    private static long firstClickTime = -1L;

    private static final long DOUBLE_CLICK_INTERVAL = 350L;

    private static final double HEIGHT_SENSITIVITY = 0.025;

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

        lockedYaw = player.getYRot();
        lockedPitch = player.getXRot();

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

                lockedYaw = player.getYRot();
                lockedPitch = player.getXRot();

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
         */
        Vec3 currentPosition = player.position();

        if (previousPlayerPosition != null) {
            Vec3 movement = currentPosition.subtract(
                    previousPlayerPosition
            );

            previewPosition = previewPosition.add(movement);
        }

        previousPlayerPosition = currentPosition;

        /*
         * マウス上下による視点の変化量を制御点の高さへ反映する。
         */
        float currentPitch = player.getXRot();

        float pitchDifference = lockedPitch - currentPitch;

        previewPosition = previewPosition.add(
                0.0,
                pitchDifference * HEIGHT_SENSITIVITY,
                0.0
        );

        /*
         * 視点を固定する。
         */
        player.setYRot(lockedYaw);
        player.setYHeadRot(lockedYaw);
        player.setXRot(lockedPitch);
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
     * ワールド上にプレビュー箱を描画する。
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

        double halfSize = 0.10;

        AABB box = new AABB(
                previewPosition.x - halfSize,
                previewPosition.y - halfSize,
                previewPosition.z - halfSize,
                previewPosition.x + halfSize,
                previewPosition.y + halfSize,
                previewPosition.z + halfSize
        );

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

        bufferSource.endBatch(RenderType.lines());
    }

    public static boolean isActive() {
        return active;
    }

    public static Vec3 getPreviewPosition() {
        return previewPosition;
    }
}