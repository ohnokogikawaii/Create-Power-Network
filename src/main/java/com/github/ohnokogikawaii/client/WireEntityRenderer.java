
package com.github.ohnokogikawaii.client;

import com.github.ohnokogikawaii.wire.WireEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public class WireEntityRenderer extends EntityRenderer<WireEntity> {

    /*
     * テクスチャを繰り返す間隔（ブロック単位）。
     * 1.0なら、ワイヤーの長さ1ブロックごとにテクスチャを繰り返す。
     */
    private static final double TEXTURE_REPEAT_LENGTH = 0.1;

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
        double length = direction.length();

        if (length < 1.0E-8) {
            return;
        }

        direction = direction.scale(1.0 / length);

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

        ResourceLocation texture = getTextureLocation(entity);

        VertexConsumer buffer = bufferSource.getBuffer(
                RenderType.entityCutoutNoCull(texture)
        );

        PoseStack.Pose pose = poseStack.last();

        /*
         * ワイヤーを複数の区間に分け、区間ごとにU座標を
         * 0～1へ戻すことでテクスチャを繰り返す。
         */
        double distance = 0.0;

        while (distance < length) {
            double segmentLength = Math.min(
                    TEXTURE_REPEAT_LENGTH,
                    length - distance
            );

            double t0 = distance / length;
            double t1 = (distance + segmentLength) / length;

            Vec3 segmentA = a.add(direction.scale(distance));
            Vec3 segmentB = a.add(
                    direction.scale(distance + segmentLength)
            );

            Vec3 a1 = segmentA.subtract(side);
            Vec3 a2 = segmentA.add(side);
            Vec3 b1 = segmentB.subtract(side);
            Vec3 b2 = segmentB.add(side);

            float u0 = 0.0F;
            float u1 = (float) (segmentLength / TEXTURE_REPEAT_LENGTH);

            addVertex(buffer, pose, a1, u0, 0.0F, packedLight);
            addVertex(buffer, pose, a2, u0, 1.0F, packedLight);
            addVertex(buffer, pose, b2, u1, 1.0F, packedLight);
            addVertex(buffer, pose, b1, u1, 0.0F, packedLight);

            distance += segmentLength;
        }
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