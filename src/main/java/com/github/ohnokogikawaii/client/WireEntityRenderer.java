
package com.github.ohnokogikawaii.client;

import com.github.ohnokogikawaii.wire.WireEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public class WireEntityRenderer extends EntityRenderer<WireEntity> {

    // 円周を分割する数。増やすほど滑らかな円になる。
    private static final int RADIAL_SEGMENTS = 12;

    // テクスチャを繰り返す間隔（ブロック単位）。
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

        Vec3 axis = b.subtract(a);
        double length = axis.length();

        if (length < 1.0E-8) {
            return;
        }

        axis = axis.scale(1.0 / length);

        // ワイヤーの軸に垂直な2方向を作る。
        Vec3 reference = Math.abs(axis.y) < 0.9
                ? new Vec3(0, 1, 0)
                : new Vec3(1, 0, 0);

        Vec3 basis1 = axis.cross(reference).normalize();
        Vec3 basis2 = axis.cross(basis1).normalize();

        double radius =
                entity.getWireType().getRenderThickness() / 2.0;

        ResourceLocation texture = getTextureLocation(entity);

        VertexConsumer buffer = bufferSource.getBuffer(
                RenderType.entityCutoutNoCull(texture)
        );

        PoseStack.Pose pose = poseStack.last();

        double distance = 0.0;

        while (distance < length) {
            double segmentLength = Math.min(
                    TEXTURE_REPEAT_LENGTH,
                    length - distance
            );

            double startDistance = distance;
            double endDistance = distance + segmentLength;

            Vec3 startCenter = a.add(axis.scale(startDistance));
            Vec3 endCenter = a.add(axis.scale(endDistance));

            float u0 = 0.0F;
            float u1 = (float) (
                    segmentLength / TEXTURE_REPEAT_LENGTH
            );

            for (int i = 0; i < RADIAL_SEGMENTS; i++) {
                double angle0 =
                        2.0 * Math.PI * i / RADIAL_SEGMENTS;
                double angle1 =
                        2.0 * Math.PI * (i + 1) / RADIAL_SEGMENTS;

                Vec3 normal0 = basis1.scale(Math.cos(angle0))
                        .add(basis2.scale(Math.sin(angle0)))
                        .normalize();

                Vec3 normal1 = basis1.scale(Math.cos(angle1))
                        .add(basis2.scale(Math.sin(angle1)))
                        .normalize();

                Vec3 a0 = startCenter.add(normal0.scale(radius));
                Vec3 a1 = startCenter.add(normal1.scale(radius));
                Vec3 b0 = endCenter.add(normal0.scale(radius));
                Vec3 b1 = endCenter.add(normal1.scale(radius));

                float v0 = (float) i / RADIAL_SEGMENTS;
                float v1 = (float) (i + 1) / RADIAL_SEGMENTS;

                // 1面分の四角形。Uは長さ方向、Vは円周方向。
                addVertex(buffer, pose, a0, normal0,
                        u0, v0, packedLight);
                addVertex(buffer, pose, a1, normal1,
                        u0, v1, packedLight);
                addVertex(buffer, pose, b1, normal1,
                        u1, v1, packedLight);
                addVertex(buffer, pose, b0, normal0,
                        u1, v0, packedLight);
            }

            distance = endDistance;
        }
    }

    private static void addVertex(
            VertexConsumer buffer,
            PoseStack.Pose pose,
            Vec3 position,
            Vec3 normal,
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
                .setNormal(
                        pose,
                        (float) normal.x,
                        (float) normal.y,
                        (float) normal.z
                );
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