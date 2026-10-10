
package com.github.ohnokogikawaii.client;

import com.github.ohnokogikawaii.PowerNetwork;
import com.github.ohnokogikawaii.wire.TerminalBlock;
import com.github.ohnokogikawaii.wire.WireItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(
        modid = PowerNetwork.MODID,
        value = Dist.CLIENT
)
public final class WireTerminalHighlight {

    /*
     * TerminalBlock 内の3段の端子中心。
     * Minecraft のブロック座標は 1 ブロック = 16 ピクセル。
     */
    private static final double[] TERMINAL_Y = {
            1.5 / 16.0,
            3.5 / 16.0,
            5.5 / 16.0
    };

    /*
     * 端子の周囲に表示する枠の大きさ。
     */
    private static final double HALF_SIZE_XZ = 3.5 / 16.0;
    private static final double HALF_SIZE_Y = 1.5 / 16.0;

    private WireTerminalHighlight() {
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

        if (!isHoldingWire(minecraft)) {
            return;
        }

        HitResult hitResult = minecraft.hitResult;

        if (!(hitResult instanceof BlockHitResult blockHit)
                || hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos blockPos = blockHit.getBlockPos();

        if (!(minecraft.level.getBlockState(blockPos).getBlock()
                instanceof TerminalBlock)) {
            return;
        }

        /*
         * ヒット位置に最も近い端子を選択する。
         * ブロック全体ではなく、3つの端子を個別に強調する。
         */
        Vec3 hitLocation = blockHit.getLocation();

        int terminalIndex = findNearestTerminal(
                blockPos,
                hitLocation
        );

        double centerX = blockPos.getX() + 0.5;
        double centerY = blockPos.getY()
                + TERMINAL_Y[terminalIndex];
        double centerZ = blockPos.getZ() + 0.5;

        AABB box = new AABB(
                centerX - HALF_SIZE_XZ,
                centerY - HALF_SIZE_Y,
                centerZ - HALF_SIZE_XZ,
                centerX + HALF_SIZE_XZ,
                centerY + HALF_SIZE_Y,
                centerZ + HALF_SIZE_XZ
        );

        renderOutline(event, box);
    }

    private static boolean isHoldingWire(Minecraft minecraft) {
        ItemStack mainHand = minecraft.player.getMainHandItem();
        ItemStack offHand = minecraft.player.getOffhandItem();

        return mainHand.getItem() instanceof WireItem
                || offHand.getItem() instanceof WireItem;
    }

    private static int findNearestTerminal(
            BlockPos blockPos,
            Vec3 hitLocation
    ) {
        double localY = hitLocation.y - blockPos.getY();

        int nearestIndex = 0;
        double nearestDistance = Double.MAX_VALUE;

        for (int i = 0; i < TERMINAL_Y.length; i++) {
            double distance = Math.abs(localY - TERMINAL_Y[i]);

            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestIndex = i;
            }
        }

        return nearestIndex;
    }

    private static void renderOutline(
            RenderLevelStageEvent event,
            AABB worldBox
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        Camera camera = event.getCamera();
        Vec3 cameraPosition = camera.getPosition();

        PoseStack poseStack = event.getPoseStack();

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
         * 青色の枠。
         * 接続先として照準が合っている端子を示す。
         */
        LevelRenderer.renderLineBox(
                poseStack,
                consumer,
                worldBox,
                0.25F,
                0.85F,
                1.0F,
                1.0F
        );

        poseStack.popPose();

        bufferSource.endBatch(RenderType.lines());
    }
}