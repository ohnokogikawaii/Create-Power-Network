package com.github.ohnokogikawaii.client;

import com.github.ohnokogikawaii.pas.PASControlCableEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class PASControlCableRenderer
        extends EntityRenderer<PASControlCableEntity> {

    private static final int RADIAL_SEGMENTS = 8;
    private static final int LENGTH_SEGMENTS = 32;

    private static final double RADIUS = 0.012;

    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace(
                    "textures/block/black_concrete.png"
            );

    public PASControlCableRenderer(
            EntityRendererProvider.Context context
    ) {
        super(context);
    }

    @Override
    public void render(
            PASControlCableEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight
    ) {
        Vec3 origin = entity.getPosition(partialTick);

        Vec3 start = Vec3.atCenterOf(
                entity.getPASPos()
        ).subtract(origin);

        Vec3 end = Vec3.atCenterOf(
                entity.getSOGPos()
        ).subtract(origin);

        double length = start.distanceTo(end);

        if (length < 0.001) {
            return;
        }

        double sag = Math.min(0.25, length * 0.025);

        List<Vec3> points = new ArrayList<>(
                LENGTH_SEGMENTS + 1
        );

        for (int i = 0; i <= LENGTH_SEGMENTS; i++) {
            double t = (double) i / LENGTH_SEGMENTS;

            Vec3 point = start.lerp(end, t);

            point = point.add(
                    0,
                    -4.0 * sag * t * (1.0 - t),
                    0
            );

            points.add(point);
        }

        VertexConsumer buffer = bufferSource.getBuffer(
                RenderType.entityCutoutNoCull(TEXTURE)
        );

        PoseStack.Pose pose = poseStack.last();

        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 a = points.get(i);
            Vec3 b = points.get(i + 1);

            Vec3 tangent = b.subtract(a).normalize();

            Vec3 reference = Math.abs(tangent.y) < 0.95
                    ? new Vec3(0, 1, 0)
                    : new Vec3(1, 0, 0);

            Vec3 side = tangent.cross(reference).normalize();
            Vec3 up = tangent.cross(side).normalize();

            for (int j = 0; j < RADIAL_SEGMENTS; j++) {
                double angle0 =
                        2.0 * Math.PI * j / RADIAL_SEGMENTS;

                double angle1 =
                        2.0 * Math.PI * (j + 1)
                                / RADIAL_SEGMENTS;

                Vec3 normal0 = side.scale(Math.cos(angle0))
                        .add(up.scale(Math.sin(angle0)))
                        .normalize();

                Vec3 normal1 = side.scale(Math.cos(angle1))
                        .add(up.scale(Math.sin(angle1)))
                        .normalize();

                Vec3 a0 = a.add(normal0.scale(RADIUS));
                Vec3 a1 = a.add(normal1.scale(RADIUS));
                Vec3 b0 = b.add(normal0.scale(RADIUS));
                Vec3 b1 = b.add(normal1.scale(RADIUS));

                addVertex(
                        buffer, pose, a0, normal0,
                        0, 0, packedLight
                );

                addVertex(
                        buffer, pose, a1, normal1,
                        0, 1, packedLight
                );

                addVertex(
                        buffer, pose, b1, normal1,
                        1, 1, packedLight
                );

                addVertex(
                        buffer, pose, b0, normal0,
                        1, 0, packedLight
                );
            }
        }

        super.render(
                entity,
                entityYaw,
                partialTick,
                poseStack,
                bufferSource,
                packedLight
        );
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
    public ResourceLocation getTextureLocation(
            PASControlCableEntity entity
    ) {
        return TEXTURE;
    }
}