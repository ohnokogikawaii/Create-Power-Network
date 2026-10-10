
package com.github.ohnokogikawaii.client;

import com.github.ohnokogikawaii.PowerNetwork;
import com.github.ohnokogikawaii.hub_connector.HubConnectorBlock;
import com.github.ohnokogikawaii.terminal.TerminalBlock;
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
        BlockState state = minecraft.level.getBlockState(blockPos);

        if (state.getBlock() instanceof TerminalBlock) {
            renderOutline(
                    event,
                    TerminalBlock.getHighlightBox(blockPos),
                    0.65F,
                    0.65F,
                    0.65F
            );
            return;
        }

        if (!(state.getBlock() instanceof HubConnectorBlock)
                || !(state.getBlock() instanceof WireConnectionPointProvider provider)) {
            return;
        }

        WireConnectionPointProvider.ConnectionPoint point =
                provider.getNearestConnectionPoint(
                        blockPos,
                        blockHit.getLocation(),
                        state
                );

        if (point == null) {
            return;
        }

        renderOutline(
                event,
                point.createWorldHighlightBox(blockPos),
                0.65F,
                0.65F,
                0.65F
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