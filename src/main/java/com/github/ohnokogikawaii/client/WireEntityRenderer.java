package com.github.ohnokogikawaii.client;

import com.github.ohnokogikawaii.wire.WireEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OverlayTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

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

        Vec3 direction = b.subtract(a);

        if (direction.lengthSqr() < 1.0E-8) {
            return;
        }

        direction = direction.normalize();

        Vec3 cameraPosition = Minecraft.getInstance()
                .gameRenderer
                .getMainCamera()
                .getPosition();

        Vec3 view = cameraPosition.subtract(origin);
        Vec3 side = direction.cross(view);

        if (side.lengthSqr() < 1.0E-8) {
            side = direction.cross(new Vec3(0, 1, 0));
        }

        if (side.lengthSqr() < 1.0E-8) {
            side = direction.cross(new Vec3(1, 0, 0));
        }

        double halfWidth =
                entity.getWireType().getRenderThickness() / 2.0;

        side = side.normalize().scale(halfWidth);

        Vec3 a1 = a.subtract(side);
        Vec3 a2 = a.add(side);
        Vec3 b1 = b.subtract(side);
        Vec3 b2 = b.add(side);

        ResourceLocation texture = getTextureLocation(entity);

        VertexConsumer buffer = bufferSource.getBuffer(
                RenderType.entityCutoutNoCull(texture)
        );

        PoseStack.Pose pose = poseStack.last();

        addVertex(buffer, pose, a1, 0, 0, packedLight);
        addVertex(buffer, pose, a2, 0, 1, packedLight);
        addVertex(buffer, pose, b2, 1, 1, packedLight);
        addVertex(buffer, pose, b1, 1, 0, packedLight);
    }

    private static void addVertex(
            VertexConsumer buffer,
            PoseStack.Pose pose,
            Vec3 position,
            float u,
            float v,
            int packedLight
    ) {
        buffer.addVertex(
                        pose,
                        (float) position.x,
                        (float) position.y,
                        (float) position.z
                )
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, 0, 1, 0);
    }

    @Override
    public ResourceLocation getTextureLocation(WireEntity entity) {
        ResourceLocation texture = entity.getWireType().getTexture();

        return ResourceLocation.fromNamespaceAndPath(
                texture.getNamespace(),
                "textures/" + texture.getPath() + ".png"
        );
    }
}

