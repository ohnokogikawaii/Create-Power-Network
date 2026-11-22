package com.github.ohnokogikawaii.client;

import com.github.ohnokogikawaii.PowerNetwork;
import com.github.ohnokogikawaii.wire.WireCutterItem;
import com.github.ohnokogikawaii.wire.WireEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;

@EventBusSubscriber(
        modid = PowerNetwork.MODID,
        value = Dist.CLIENT
)
public final class WireCutterAimHighlight {

    private static final int CUTTABLE_COLOR = 0x35E65A;
    private static final int UNCUTTABLE_COLOR = 0xF04444;

    /*
     * 当たり判定の表示幅。
     * 配線の太さより少し広くして視認しやすくする。
     */
    private static final double HITBOX_RADIUS = 0.055;

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

    /**
     * メインハンド・オフハンドのどちらかに
     * ワイヤーカッターを持っているか確認する。
     */
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
     * 配線の経路に沿って当たり判定を表示する。
     */
    private static void renderWireHitbox(
            RenderLevelStageEvent event,
            WireEntity wire,
            int rgb
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        List<Vec3> points =
                WireCutterItem.getAimPath(wire);

        if (points.size() < 2) {
            return;
        }

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

        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 a = points.get(i);
            Vec3 b = points.get(i + 1);

            AABB hitbox = new AABB(
                    a,
                    b
            ).inflate(HITBOX_RADIUS);

            LevelRenderer.renderLineBox(
                    poseStack,
                    consumer,
                    hitbox,
                    red,
                    green,
                    blue,
                    1.0F
            );
        }

        poseStack.popPose();

        bufferSource.endBatch(RenderType.lines());
    }
}