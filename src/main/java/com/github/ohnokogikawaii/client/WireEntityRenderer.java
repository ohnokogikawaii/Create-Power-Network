
package com.github.ohnokogikawaii.client;

import com.github.ohnokogikawaii.wire.WireEntity;
import com.github.ohnokogikawaii.wire.WireType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class WireEntityRenderer extends EntityRenderer<WireEntity> {

    // 円周方向の分割数
    private static final int RADIAL_SEGMENTS = 12;

    // テクスチャの繰り返し間隔（ブロック単位）
    private static final double TEXTURE_REPEAT_LENGTH = 0.1;

    // 仮の標準余長。直線距離に対して2%長いワイヤーとして垂れを計算する。
    // 実際の設置長を導入した段階で、その値から垂れを計算する方式に変更する。
    private static final double DEFAULT_SLACK_RATIO = 1.02;

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

        double chordLength = a.distanceTo(b);
        if (chordLength < 1.0E-8) {
            return;
        }

        WireType wireType = entity.getWireType();

        double radius = wireType.getRenderThickness() / 2.0;
        if (radius <= 0.0) {
            return;
        }

        double sag = wireType.canSag()
                ? calculateSag(a, b, chordLength)
                : 0.0;

        List<Vec3> centers =
                createCenterline(a, b, sag, chordLength);

        if (centers.size() < 2) {
            return;
        }

        ResourceLocation texture = getTextureLocation(entity);
        VertexConsumer buffer = bufferSource.getBuffer(
                RenderType.entityCutoutNoCull(texture)
        );

        PoseStack.Pose pose = poseStack.last();

        for (int segment = 0; segment < centers.size() - 1; segment++) {
            Vec3 startCenter = centers.get(segment);
            Vec3 endCenter = centers.get(segment + 1);

            double segmentLength = startCenter.distanceTo(endCenter);
            if (segmentLength < 1.0E-8) {
                continue;
            }

            Vec3 tangentStart = getTangent(centers, segment);
            Vec3 tangentEnd = getTangent(centers, segment + 1);

            Vec3 referenceStart = Math.abs(tangentStart.y) < 0.95
                    ? new Vec3(0, 1, 0)
                    : new Vec3(1, 0, 0);

            Vec3 sideStart =
                    tangentStart.cross(referenceStart).normalize();
            Vec3 upStart =
                    tangentStart.cross(sideStart).normalize();

            Vec3 referenceEnd = Math.abs(tangentEnd.y) < 0.95
                    ? new Vec3(0, 1, 0)
                    : new Vec3(1, 0, 0);

            Vec3 sideEnd =
                    tangentEnd.cross(referenceEnd).normalize();
            Vec3 upEnd =
                    tangentEnd.cross(sideEnd).normalize();

            float u0 = 0.0F;
            float u1 = (float) Math.min(
                    1.0,
                    segmentLength / TEXTURE_REPEAT_LENGTH
            );

            for (int radial = 0; radial < RADIAL_SEGMENTS; radial++) {
                double angle0 =
                        2.0 * Math.PI * radial / RADIAL_SEGMENTS;
                double angle1 =
                        2.0 * Math.PI * (radial + 1) / RADIAL_SEGMENTS;

                Vec3 normalStart0 =
                        ringNormal(sideStart, upStart, angle0);
                Vec3 normalStart1 =
                        ringNormal(sideStart, upStart, angle1);
                Vec3 normalEnd0 =
                        ringNormal(sideEnd, upEnd, angle0);
                Vec3 normalEnd1 =
                        ringNormal(sideEnd, upEnd, angle1);

                Vec3 a0 = startCenter.add(normalStart0.scale(radius));
                Vec3 a1 = startCenter.add(normalStart1.scale(radius));
                Vec3 b0 = endCenter.add(normalEnd0.scale(radius));
                Vec3 b1 = endCenter.add(normalEnd1.scale(radius));

                float v0 = (float) radial / RADIAL_SEGMENTS;
                float v1 = (float) (radial + 1) / RADIAL_SEGMENTS;

                addVertex(buffer, pose, a0, normalStart0,
                        u0, v0, packedLight);
                addVertex(buffer, pose, a1, normalStart1,
                        u0, v1, packedLight);
                addVertex(buffer, pose, b1, normalEnd1,
                        u1, v1, packedLight);
                addVertex(buffer, pose, b0, normalEnd0,
                        u1, v0, packedLight);
            }
        }
    }

    /**
     * 目標とするワイヤー長になるように、放物線の垂れ量を二分探索する。
     */
    private static double calculateSag(
            Vec3 a,
            Vec3 b,
            double chordLength
    ) {
        double horizontalSpan = Math.sqrt(
                Math.pow(b.x - a.x, 2)
                        + Math.pow(b.z - a.z, 2)
        );

        // 垂直に近い配線は不自然に横へ垂れさせない。
        if (horizontalSpan < 1.0E-4 || chordLength < 1.0E-4) {
            return 0.0;
        }

        double targetLength = chordLength * DEFAULT_SLACK_RATIO;
        double low = 0.0;
        double high = Math.max(0.05, horizontalSpan);

        for (int i = 0; i < 32; i++) {
            double mid = (low + high) * 0.5;
            double curveLength =
                    estimateCurveLength(a, b, mid, 64);

            if (curveLength < targetLength) {
                low = mid;
            } else {
                high = mid;
            }
        }

        return (low + high) * 0.5;
    }

    private static double estimateCurveLength(
            Vec3 a,
            Vec3 b,
            double sag,
            int samples
    ) {
        Vec3 previous = curvePoint(a, b, sag, 0.0);
        double length = 0.0;

        for (int i = 1; i <= samples; i++) {
            Vec3 current = curvePoint(
                    a, b, sag, (double) i / samples
            );

            length += previous.distanceTo(current);
            previous = current;
        }

        return length;
    }

    private static List<Vec3> createCenterline(
            Vec3 a,
            Vec3 b,
            double sag,
            double chordLength
    ) {
        int segments = Math.max(
                1,
                (int) Math.ceil(
                        chordLength * DEFAULT_SLACK_RATIO
                                / TEXTURE_REPEAT_LENGTH
                )
        );

        List<Vec3> points = new ArrayList<>(segments + 1);

        for (int i = 0; i <= segments; i++) {
            points.add(
                    curvePoint(a, b, sag, (double) i / segments)
            );
        }

        return points;
    }

    /**
     * 両端を結ぶ直線から、中央が下がる放物線を作る。
     */
    private static Vec3 curvePoint(
            Vec3 a,
            Vec3 b,
            double sag,
            double t
    ) {
        Vec3 linear = a.lerp(b, t);
        double verticalOffset = -4.0 * sag * t * (1.0 - t);

        return linear.add(0.0, verticalOffset, 0.0);
    }

    private static Vec3 getTangent(
            List<Vec3> points,
            int index
    ) {
        Vec3 tangent;

        if (index == 0) {
            tangent = points.get(1).subtract(points.get(0));
        } else if (index == points.size() - 1) {
            tangent = points.get(index)
                    .subtract(points.get(index - 1));
        } else {
            tangent = points.get(index + 1)
                    .subtract(points.get(index - 1));
        }

        return tangent.normalize();
    }

    private static Vec3 ringNormal(
            Vec3 side,
            Vec3 up,
            double angle
    ) {
        return side.scale(Math.cos(angle))
                .add(up.scale(Math.sin(angle)))
                .normalize();
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