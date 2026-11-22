
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
import com.github.ohnokogikawaii.insulator.InsulatorBlock;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class WireEntityRenderer extends EntityRenderer<WireEntity> {

    // 円周方向の分割数
    private static final int RADIAL_SEGMENTS = 12;

    // テクスチャの繰り返し間隔（ブロック単位）
    private static final double TEXTURE_REPEAT_LENGTH = 0.1;

    // 仮の標準余長。直線距離に対して2%長いワイヤーとして垂れを計算する。
    // 実際の設置長を導入した段階で、その値から垂れを計算する方式に変更する。
    private static final double DEFAULT_SLACK_RATIO = 1.005;

    private record PathPoint(double t, Vec3 position) {}

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
        Vec3 origin = entity.getPosition(partialTick);

        Vec3 endpointA = entity.getEndpointAPosition();
        Vec3 endpointB = entity.getEndpointBPosition();

        /*
         * ジャンパ線は耐張碍子の接続位置から
         * 0.5ブロック上に持ち上げて描画する。
         *
         * 実際の接続座標・ブロック位置は変更しない。
         */
        if (entity.isJumperWire()) {
            endpointA = endpointA.add(0.0, 0.5, 0.0);
            endpointB = endpointB.add(0.0, 0.5, 0.0);
        }

        Vec3 a = endpointA.subtract(origin);
        Vec3 b = endpointB.subtract(origin);

        double chordLength = a.distanceTo(b);

        if (chordLength < 1.0E-8) {
            return;
        }

        WireType wireType = entity.getWireType();

        double radius = wireType.getRenderThickness() / 2.0;

        if (radius <= 0.0) {
            return;
        }

        /*
         * A・B間にある支持点を含めた中心線を生成する。
         */
        List<Vec3> centers;

        if (entity.isJumperWire()) {

            /*
             * テスト段階では弛みを付けず、
             * 2点間を最短距離の直線で接続する。
             */
            centers = List.of(a, b);

        } else {

            centers = createSupportedCenterline(
                    entity,
                    a,
                    b,
                    origin,
                    wireType.canSag()
            );
        }

        if (centers.size() < 2) {
            return;
        }

        ResourceLocation texture = getTextureLocation(entity);

        VertexConsumer buffer = bufferSource.getBuffer(
                RenderType.entityCutoutNoCull(texture)
        );

        PoseStack.Pose pose = poseStack.last();

        /*
         * 中心線に沿って円形の電線メッシュを生成する。
         */
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

                Vec3 a0 = startCenter.add(
                        normalStart0.scale(radius)
                );

                Vec3 a1 = startCenter.add(
                        normalStart1.scale(radius)
                );

                Vec3 b0 = endCenter.add(
                        normalEnd0.scale(radius)
                );

                Vec3 b1 = endCenter.add(
                        normalEnd1.scale(radius)
                );

                float v0 = (float) radial / RADIAL_SEGMENTS;
                float v1 = (float) (radial + 1) / RADIAL_SEGMENTS;

                addVertex(
                        buffer, pose, a0, normalStart0,
                        u0, v0, packedLight
                );

                addVertex(
                        buffer, pose, a1, normalStart1,
                        u0, v1, packedLight
                );

                addVertex(
                        buffer, pose, b1, normalEnd1,
                        u1, v1, packedLight
                );

                addVertex(
                        buffer, pose, b0, normalEnd0,
                        u1, v0, packedLight
                );
            }
        }
        /*
         * 碍子に接続された端点へ端末金具を描画する。
         */
        ResourceLocation metalTexture =
                ResourceLocation.fromNamespaceAndPath(
                        "minecraft",
                        "textures/block/iron_block.png"
                );

        VertexConsumer metalBuffer = bufferSource.getBuffer(
                RenderType.entityCutoutNoCull(metalTexture)
        );

        if (entity.isTerminalA()) {
            InsulatorBlock.Type type =
                    getEndpointInsulatorType(
                            entity,
                            entity.getEndpointA()
                    );

            if (type != null) {
                Vec3 tangent = getTangent(centers, 0);

                renderTerminalFitting(
                        metalBuffer,
                        pose,
                        a,
                        tangent,
                        radius,
                        type,
                        packedLight
                );
            }
        }

        if (entity.isTerminalB()) {
            InsulatorBlock.Type type =
                    getEndpointInsulatorType(
                            entity,
                            entity.getEndpointB()
                    );

            if (type != null) {
                Vec3 tangent = getTangent(
                        centers,
                        centers.size() - 1
                ).scale(-1.0);

                renderTerminalFitting(
                        metalBuffer,
                        pose,
                        b,
                        tangent,
                        radius,
                        type,
                        packedLight
                );
            }
        }
    }

    /**
     * 指定された端点が接続している碍子の種類を取得する。
     */
    private static InsulatorBlock.Type getEndpointInsulatorType(
            WireEntity entity,
            BlockPos endpoint
    ) {
        if (!entity.level().hasChunkAt(endpoint)) {
            return null;
        }

        if (entity.level().getBlockState(endpoint).getBlock()
                instanceof InsulatorBlock insulator) {
            return insulator.getInsulatorType();
        }

        return null;
    }

    /**
     * 碍子の種類に応じた端末金具を描画する。
     *
     * directionは端点から電線本体へ向かう方向。
     */
    private static void renderTerminalFitting(
            VertexConsumer buffer,
            PoseStack.Pose pose,
            Vec3 endpoint,
            Vec3 direction,
            double wireRadius,
            InsulatorBlock.Type type,
            int packedLight
    ) {
        double length;
        double radiusMultiplier;
        double minimumRadius;

        switch (type) {
            case LOW_VOLTAGE -> {
                length = 0.14;
                radiusMultiplier = 1.8;
                minimumRadius = 0.035;
            }
            case HIGH_VOLTAGE -> {
                length = 0.18;
                radiusMultiplier = 2.0;
                minimumRadius = 0.045;
            }
            case EXTRA_HIGH_VOLTAGE -> {
                length = 0.23;
                radiusMultiplier = 2.3;
                minimumRadius = 0.055;
            }
            case TRANSMISSION_LINE -> {
                length = 0.30;
                radiusMultiplier = 2.6;
                minimumRadius = 0.065;
            }
            default -> {
                return;
            }
        }

        double fittingRadius = Math.max(
                wireRadius * radiusMultiplier,
                minimumRadius
        );

        Vec3 axis = direction.normalize();

        if (axis.lengthSqr() < 1.0E-8) {
            return;
        }

        /*
         * 金具は端点から電線側へ配置する。
         */
        Vec3 center = endpoint.add(
                axis.scale(length * 0.5)
        );

        renderCylinder(
                buffer,
                pose,
                center,
                axis,
                fittingRadius,
                length,
                packedLight
        );
    }

    /**
     * 任意方向の円筒形状を描画する。
     */
    private static void renderCylinder(
            VertexConsumer buffer,
            PoseStack.Pose pose,
            Vec3 center,
            Vec3 axis,
            double radius,
            double length,
            int packedLight
    ) {
        Vec3 reference = Math.abs(axis.y) < 0.95
                ? new Vec3(0, 1, 0)
                : new Vec3(1, 0, 0);

        Vec3 side = axis.cross(reference).normalize();
        Vec3 up = axis.cross(side).normalize();

        Vec3 start = center.subtract(
                axis.scale(length * 0.5)
        );

        Vec3 end = center.add(
                axis.scale(length * 0.5)
        );

        for (int i = 0; i < RADIAL_SEGMENTS; i++) {
            double angle0 =
                    2.0 * Math.PI * i / RADIAL_SEGMENTS;

            double angle1 =
                    2.0 * Math.PI * (i + 1) / RADIAL_SEGMENTS;

            Vec3 normal0 = ringNormal(side, up, angle0);
            Vec3 normal1 = ringNormal(side, up, angle1);

            Vec3 start0 = start.add(
                    normal0.scale(radius)
            );

            Vec3 start1 = start.add(
                    normal1.scale(radius)
            );

            Vec3 end0 = end.add(
                    normal0.scale(radius)
            );

            Vec3 end1 = end.add(
                    normal1.scale(radius)
            );

            float u0 = (float) i / RADIAL_SEGMENTS;
            float u1 = (float) (i + 1) / RADIAL_SEGMENTS;

            addVertex(
                    buffer, pose, start0, normal0,
                    u0, 1.0F, packedLight
            );

            addVertex(
                    buffer, pose, start1, normal1,
                    u1, 1.0F, packedLight
            );

            addVertex(
                    buffer, pose, end1, normal1,
                    u1, 0.0F, packedLight
            );

            addVertex(
                    buffer, pose, end0, normal0,
                    u0, 0.0F, packedLight
            );
        }
    }

    /**
     * 支持点を通る中心線を生成する。
     *
     * 電気的な接続はA-Bのまま維持し、
     * 描画上だけ支持点ごとに区間を分割する。
     */
    private static List<Vec3> createSupportedCenterline(
            WireEntity entity,
            Vec3 a,
            Vec3 b,
            Vec3 origin,
            boolean canSag
    ) {
        Vec3 direction = b.subtract(a);

        double lengthSquared = direction.lengthSqr();

        if (lengthSquared < 1.0E-8) {
            return List.of(a, b);
        }

        List<PathPoint> pathPoints = new ArrayList<>();

        pathPoints.add(new PathPoint(0.0, a));

        /*
         * 支持点をA-B間の位置に変換する。
         * 登録順ではなく、AからBへの投影距離で並べる。
         */
        for (WireEntity.SupportPoint support :
                entity.getSupportPoints()) {

            Vec3 position = support.position().subtract(origin);

            double t = position.subtract(a)
                    .dot(direction) / lengthSquared;

            /*
             * Aより前、Bより後ろの点は除外する。
             */
            if (t <= 0.0 || t >= 1.0) {
                continue;
            }

            pathPoints.add(new PathPoint(t, position));
        }

        pathPoints.add(new PathPoint(1.0, b));

        pathPoints.sort(
                Comparator.comparingDouble(PathPoint::t)
        );

        List<Vec3> result = new ArrayList<>();

        /*
         * 各支持点間を独立した垂れ曲線で接続する。
         */
        for (int i = 0; i < pathPoints.size() - 1; i++) {
            Vec3 start = pathPoints.get(i).position();
            Vec3 end = pathPoints.get(i + 1).position();

            double spanLength = start.distanceTo(end);

            if (spanLength < 1.0E-8) {
                continue;
            }

            double sag = canSag
                    ? calculateSag(start, end, spanLength)
                    : 0.0;

            List<Vec3> span = createCenterline(
                    start,
                    end,
                    sag,
                    spanLength
            );

            /*
             * 前区間の終点と次区間の始点が重複しないようにする。
             */
            int startIndex = result.isEmpty() ? 0 : 1;

            for (int j = startIndex; j < span.size(); j++) {
                result.add(span.get(j));
            }
        }

        return result;
    }

    /**
     * 目標とするワイヤー長になるように、
     * 放物線の垂れ量を二分探索する。
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

        if (horizontalSpan < 1.0E-4
                || chordLength < 1.0E-4) {
            return 0.0;
        }

        double targetLength =
                chordLength * DEFAULT_SLACK_RATIO;

        double low = 0.0;
        double high = Math.max(0.05, horizontalSpan);

        for (int i = 0; i < 32; i++) {
            double mid = (low + high) * 0.5;

            double curveLength = estimateCurveLength(
                    a,
                    b,
                    mid,
                    64
            );

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
                    a,
                    b,
                    sag,
                    (double) i / samples
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
                    curvePoint(
                            a,
                            b,
                            sag,
                            (double) i / segments
                    )
            );
        }

        return points;
    }

    /**
     * 両端を結ぶ直線から中央が下がる放物線を作る。
     */
    private static Vec3 curvePoint(
            Vec3 a,
            Vec3 b,
            double sag,
            double t
    ) {
        Vec3 linear = a.lerp(b, t);

        double verticalOffset =
                -4.0 * sag * t * (1.0 - t);

        return linear.add(
                0.0,
                verticalOffset,
                0.0
        );
    }

    private static Vec3 getTangent(
            List<Vec3> points,
            int index
    ) {
        Vec3 tangent;

        if (index == 0) {
            tangent = points.get(1)
                    .subtract(points.get(0));
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
        ResourceLocation texture =
                entity.getWireType().getTexture();

        return ResourceLocation.fromNamespaceAndPath(
                texture.getNamespace(),
                "textures/" + texture.getPath() + ".png"
        );
    }
}
