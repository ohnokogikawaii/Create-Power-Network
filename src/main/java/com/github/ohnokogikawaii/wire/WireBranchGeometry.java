
package com.github.ohnokogikawaii.wire;

import net.minecraft.world.phys.Vec3;

public final class WireBranchGeometry {

    private static final double EPSILON = 1.0E-8;
    private static final double DEFAULT_SLACK_RATIO = 1.005;

    private WireBranchGeometry() {
    }

    /**
     * ワイヤー上の位置を計算する。
     *
     * @param a ワイヤー始点
     * @param b ワイヤー終点
     * @param t 0.0～1.0 の位置
     * @param canSag ワイヤーがたるむか
     */
    public static Vec3 pointOnWire(
            Vec3 a,
            Vec3 b,
            double t,
            boolean canSag
    ) {
        t = clamp(t, 0.0, 1.0);

        if (!canSag) {
            return a.lerp(b, t);
        }

        double chordLength = a.distanceTo(b);
        double sag = calculateSag(a, b, chordLength);
        Vec3 linear = a.lerp(b, t);

        return linear.add(
                0.0,
                -4.0 * sag * t * (1.0 - t),
                0.0
        );
    }

    public static Vec3 pointOnWire(
            Vec3 a,
            Vec3 b,
            double t
    ) {
        return pointOnWire(a, b, t, false);
    }

    /**
     * ワイヤー上の接線方向を近似計算する。
     */
    public static Vec3 tangent(
            Vec3 a,
            Vec3 b,
            double t,
            boolean canSag
    ) {
        double delta = 0.001;

        Vec3 before = pointOnWire(
                a,
                b,
                Math.max(0.0, t - delta),
                canSag
        );

        Vec3 after = pointOnWire(
                a,
                b,
                Math.min(1.0, t + delta),
                canSag
        );

        return normalizeOrDefault(after.subtract(before));
    }

    public static Vec3 tangent(
            Vec3 a,
            Vec3 b
    ) {
        return tangent(a, b, 0.5, false);
    }

    /**
     * ワイヤーの接線に対する基準垂直方向を求める。
     */
    public static Vec3 referencePerpendicular(Vec3 tangent) {
        Vec3 axis = normalizeOrDefault(tangent);

        Vec3 reference = Math.abs(axis.y) < 0.85
                ? new Vec3(0, 1, 0)
                : new Vec3(1, 0, 0);

        Vec3 perpendicular = axis.cross(reference);

        if (perpendicular.lengthSqr() < EPSILON) {
            perpendicular = axis.cross(
                    new Vec3(0, 0, 1)
            );
        }

        return normalizeOrDefault(perpendicular);
    }

    /**
     * ワイヤーの周囲にある8方向の分岐方向を求める。
     */
    public static Vec3 branchDirection(
            Vec3 a,
            Vec3 b,
            int directionIndex
    ) {
        return branchDirection(
                a,
                b,
                0.5,
                false,
                directionIndex
        );
    }

    public static Vec3 branchDirection(
            Vec3 a,
            Vec3 b,
            double t,
            boolean canSag,
            int directionIndex
    ) {
        Vec3 axis = tangent(a, b, t, canSag);
        Vec3 right = referencePerpendicular(axis);
        Vec3 up = normalizeOrDefault(axis.cross(right));

        double angle =
                Math.floorMod(directionIndex, 8)
                        * Math.PI / 4.0;

        return normalizeOrDefault(
                right.scale(Math.cos(angle))
                        .add(up.scale(Math.sin(angle)))
        );
    }

    /**
     * 8方向分岐ポートのワールド座標を求める。
     */
    public static Vec3 branchPortPosition(
            Vec3 a,
            Vec3 b,
            double t,
            int directionIndex,
            double offset
    ) {
        return branchPortPosition(
                a,
                b,
                t,
                false,
                directionIndex,
                offset
        );
    }

    public static Vec3 branchPortPosition(
            Vec3 a,
            Vec3 b,
            double t,
            boolean canSag,
            int directionIndex,
            double offset
    ) {
        Vec3 center = pointOnWire(
                a,
                b,
                t,
                canSag
        );

        Vec3 direction = branchDirection(
                a,
                b,
                t,
                canSag,
                directionIndex
        );

        return center.add(direction.scale(offset));
    }

    /**
     * 下向き分岐ポートのワールド座標を求める。
     */
    public static Vec3 downwardPortPosition(
            Vec3 a,
            Vec3 b,
            double t,
            double offset
    ) {
        return downwardPortPosition(
                a,
                b,
                t,
                false,
                offset
        );
    }

    public static Vec3 downwardPortPosition(
            Vec3 a,
            Vec3 b,
            double t,
            boolean canSag,
            double offset
    ) {
        return pointOnWire(a, b, t, canSag)
                .add(0.0, -offset, 0.0);
    }

    /**
     * ワイヤーのたるみ量を求める。
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

            double length = estimateCurveLength(
                    a,
                    b,
                    mid
            );

            if (length < targetLength) {
                low = mid;
            } else {
                high = mid;
            }
        }

        return (low + high) * 0.5;
    }

    /**
     * 指定したたるみ量における曲線長を近似する。
     */
    private static double estimateCurveLength(
            Vec3 a,
            Vec3 b,
            double sag
    ) {
        Vec3 previous = curvePoint(
                a,
                b,
                sag,
                0.0
        );

        double length = 0.0;

        for (int i = 1; i <= 64; i++) {
            double t = i / 64.0;

            Vec3 current = curvePoint(
                    a,
                    b,
                    sag,
                    t
            );

            length += previous.distanceTo(current);
            previous = current;
        }

        return length;
    }

    /**
     * たるみ量を指定した曲線上の位置。
     *
     * calculateSag() の長さ推定に使用する。
     */
    private static Vec3 curvePoint(
            Vec3 a,
            Vec3 b,
            double sag,
            double t
    ) {
        return a.lerp(b, t).add(
                0.0,
                -4.5 * sag * t * (1.0 - t),
                0.0
        );
    }

    private static Vec3 normalizeOrDefault(Vec3 vector) {
        if (vector.lengthSqr() < EPSILON) {
            return new Vec3(1, 0, 0);
        }

        return vector.normalize();
    }

    private static double clamp(
            double value,
            double minimum,
            double maximum
    ) {
        return Math.max(
                minimum,
                Math.min(maximum, value)
        );
    }
}
