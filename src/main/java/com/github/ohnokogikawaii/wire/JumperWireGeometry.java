
package com.github.ohnokogikawaii.wire;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * ジャンパ線の描画・当たり判定で共通使用する曲線生成クラス。
 *
 * 始点・制御点・終点を通るCatmull-Romスプラインを生成する。
 * 座標はワールド座標で扱う。
 */
public final class JumperWireGeometry {

    private static final int SAMPLES_PER_BLOCK = 16;
    private static final int MIN_SAMPLES = 8;
    private static final int MAX_SAMPLES = 128;

    private JumperWireGeometry() {
    }

    /**
     * 登録済みの制御点を使用してジャンパ線の曲線を生成する。
     */
    public static List<Vec3> getPath(WireEntity wire) {
        return getPath(wire, null);
    }

    /**
     * 登録済みの制御点に加えて、仮の制御点を含めた曲線を生成する。
     *
     * additionalControlPoint は保存時と同じワールド座標で受け取る。
     */
    public static List<Vec3> getPath(
            WireEntity wire,
            Vec3 additionalControlPoint
    ) {
        if (wire == null || !wire.isJumperWire()) {
            return List.of();
        }

        List<Vec3> anchors = new ArrayList<>();

        anchors.add(
                wire.getEndpointAWorldPosition()
                        .add(0.0, 0.5, 0.0)
        );

        for (Vec3 point : wire.getJumperControlPoints()) {
            anchors.add(point.add(0.0, 0.5, 0.0));
        }

        if (additionalControlPoint != null) {
            anchors.add(
                    additionalControlPoint.add(0.0, 0.5, 0.0)
            );
        }

        anchors.add(
                wire.getEndpointBWorldPosition()
                        .add(0.0, 0.5, 0.0)
        );

        if (anchors.size() < 2) {
            return List.copyOf(anchors);
        }

        List<Vec3> result = new ArrayList<>();

        for (int i = 0; i < anchors.size() - 1; i++) {
            Vec3 p0 = anchors.get(Math.max(0, i - 1));
            Vec3 p1 = anchors.get(i);
            Vec3 p2 = anchors.get(i + 1);
            Vec3 p3 = anchors.get(
                    Math.min(anchors.size() - 1, i + 2)
            );

            double distance = p1.distanceTo(p2);

            int samples = Math.max(
                    MIN_SAMPLES,
                    Math.min(
                            MAX_SAMPLES,
                            (int) Math.ceil(
                                    distance * SAMPLES_PER_BLOCK
                            )
                    )
            );

            for (int j = 0; j <= samples; j++) {
                if (i > 0 && j == 0) {
                    continue;
                }

                double t = (double) j / samples;

                result.add(
                        catmullRom(p0, p1, p2, p3, t)
                );
            }
        }

        return List.copyOf(result);
    }

    /**
     * Catmull-Romスプライン上の座標を計算する。
     */
    private static Vec3 catmullRom(
            Vec3 p0,
            Vec3 p1,
            Vec3 p2,
            Vec3 p3,
            double t
    ) {
        double t2 = t * t;
        double t3 = t2 * t;

        double x = calculateCoordinate(
                p0.x, p1.x, p2.x, p3.x, t, t2, t3
        );

        double y = calculateCoordinate(
                p0.y, p1.y, p2.y, p3.y, t, t2, t3
        );

        double z = calculateCoordinate(
                p0.z, p1.z, p2.z, p3.z, t, t2, t3
        );

        return new Vec3(x, y, z);
    }

    private static double calculateCoordinate(
            double p0,
            double p1,
            double p2,
            double p3,
            double t,
            double t2,
            double t3
    ) {
        return 0.5 * (
                2.0 * p1
                        + (-p0 + p2) * t
                        + (2.0 * p0 - 5.0 * p1
                        + 4.0 * p2 - p3) * t2
                        + (-p0 + 3.0 * p1
                        - 3.0 * p2 + p3) * t3
        );
    }
}
