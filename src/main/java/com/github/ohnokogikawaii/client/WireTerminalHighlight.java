package com.github.ohnokogikawaii.client;

import com.github.ohnokogikawaii.PowerNetwork;
import com.github.ohnokogikawaii.wire.ConnectionPointManager;
import com.github.ohnokogikawaii.wire.WireBranchConnectorEntity;
import com.github.ohnokogikawaii.wire.WireConnectionPointProvider;
import com.github.ohnokogikawaii.wire.WireItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
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

    private static final float HIGHLIGHT_RED = 0.65F;
    private static final float HIGHLIGHT_GREEN = 0.65F;
    private static final float HIGHLIGHT_BLUE = 0.65F;

    private static final double BRANCH_PORT_HALF_SIZE = 0.09;

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

        /*
         * 分岐コネクタのポートを優先する。
         * ポートが狙われている場合は、ブロック側の処理を行わない。
         */
        WireItem.BranchHit branchHit = WireItem.findBranchPortHit(
                minecraft.level,
                minecraft.player
        );

        if (branchHit != null) {
            renderBranchPort(event, branchHit.position());
            return;
        }

        HitResult hitResult = minecraft.hitResult;

        if (!(hitResult instanceof BlockHitResult blockHit)
                || hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos blockPos = blockHit.getBlockPos();
        BlockState state = minecraft.level.getBlockState(blockPos);

        if (!(state.getBlock()
                instanceof WireConnectionPointProvider provider)) {
            return;
        }

        /*
         * 端子・ハブ・碍子など、すべて同じ接続点検索を使用する。
         */
        WireConnectionPointProvider.ConnectionPoint point =
                ConnectionPointManager.findNearest(
                        provider,
                        state,
                        blockPos,
                        blockHit.getLocation()
                );

        if (point == null) {
            return;
        }

        AABB highlightBox = ConnectionPointManager.getHighlightBox(
                blockPos,
                point
        );

        if (highlightBox == null) {
            return;
        }

        renderOutline(
                event,
                highlightBox,
                HIGHLIGHT_RED,
                HIGHLIGHT_GREEN,
                HIGHLIGHT_BLUE
        );
    }

    private static void renderBranchPort(
            RenderLevelStageEvent event,
            Vec3 port
    ) {
        double halfSize = BRANCH_PORT_HALF_SIZE;

        AABB portBox = new AABB(
                port.x - halfSize,
                port.y - halfSize,
                port.z - halfSize,
                port.x + halfSize,
                port.y + halfSize,
                port.z + halfSize
        );

        renderOutline(
                event,
                portBox,
                HIGHLIGHT_RED,
                HIGHLIGHT_GREEN,
                HIGHLIGHT_BLUE
        );
    }

    private static boolean isHoldingWire(Minecraft minecraft) {
        ItemStack mainHand = minecraft.player.getMainHandItem();
        ItemStack offHand = minecraft.player.getOffhandItem();

        return mainHand.getItem() instanceof WireItem
                || offHand.getItem() instanceof WireItem;
    }

    private static void renderOutline(
            RenderLevelStageEvent event,
            AABB worldBox,
            float red,
            float green,
            float blue
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

        LevelRenderer.renderLineBox(
                poseStack,
                consumer,
                worldBox,
                red,
                green,
                blue,
                1.0F
        );

        poseStack.popPose();

        bufferSource.endBatch(RenderType.lines());
    }
}