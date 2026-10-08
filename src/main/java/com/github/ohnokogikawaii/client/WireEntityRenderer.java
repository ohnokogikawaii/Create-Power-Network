package com.github.ohnokogikawaii.client;

import com.github.ohnokogikawaii.wire.WireEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * First-stage wire renderer.
 *
 * It renders a straight line between the two physical endpoints.
 * Catenary/sag will be added later.
 */
public class WireEntityRenderer extends EntityRenderer<WireEntity> {

    public WireEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(
            WireEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight
    ) {
        Vec3 origin = entity.position();
        Vec3 a = entity.getEndpointAPosition().subtract(origin);
        Vec3 b = entity.getEndpointBPosition().subtract(origin);

        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lines());
        PoseStack.Pose pose = poseStack.last();

        buffer.addVertex(pose, (float) a.x, (float) a.y, (float) a.z)
                .setColor(40, 40, 40, 255)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);

        buffer.addVertex(pose, (float) b.x, (float) b.y, (float) b.z)
                .setColor(40, 40, 40, 255)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(WireEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(
                "minecraft",
                "textures/block/iron_block.png"
        );
    }
}
