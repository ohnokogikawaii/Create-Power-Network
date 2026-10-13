package com.github.ohnokogikawaii.client;

import com.github.ohnokogikawaii.PowerNetwork;
import com.github.ohnokogikawaii.wire.ConnectionPointManager;
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

    private static final int DEFAULT_HIGHLIGHT_COLOR = 0xA6A6A6;

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
         * ポートが狙われている場合はブロック側を描画しない。
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
         * 接続点を検索する。
         *
         * BLOCK モードの場合も接続点の存在は必要。
         * これは電線接続先として定義されたブロックだけを
         * ハイライトするため。
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
                provider,
                state,
                minecraft.level,
                blockPos,
                point
        );

        if (highlightBox == null) {
            return;
        }

        int color = ConnectionPointManager.getHighlightColor(
                provider,
                state,
                point
        );

        renderOutline(
                event,
                highlightBox,
                color
        );
    }

    /**
     * 分岐コネクタの接続ポートをハイライトする。
     */
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
                DEFAULT_HIGHLIGHT_COLOR
        );
    }

    private static boolean isHoldingWire(Minecraft minecraft) {
        ItemStack mainHand = minecraft.player.getMainHandItem();
        ItemStack offHand = minecraft.player.getOffhandItem();

        return mainHand.getItem() instanceof WireItem
                || offHand.getItem() instanceof WireItem;
    }

    /**
     * RGB形式の色を使ってワールド内に輪郭を描画する。
     */
    private static void renderOutline(
            RenderLevelStageEvent event,
            AABB worldBox,
            int rgb
    ) {
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