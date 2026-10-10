package com.github.ohnokogikawaii.wire;

import net.minecraft.world.phys.Vec3;

/**
 * 電線途中に取り付ける分岐コネクタの方向・位置計算。
 *
 * directionIndex:
 *   0～7 の8方向
 *
 * wireT:
 *   電線 A 側を 0.0、B 側を 1.0 とする取り付け位置
 */
public final class WireBranchGeometry {

    private static final double EPSILON = 1.0E-8;

    private WireBranchGeometry() {
    }

    /**
     * 電線上の位置を求める。
     *
     * 現段階では端点間の直線上を使用する。
     * 電線のたるみ曲線と完全に一致させる処理は、
     * WireEntity の描画曲線を共通化する段階で統合する。
     */
    public static Vec3 pointOnWire(
            Vec3 endpointA,
            Vec3 endpointB,
            double wireT
    ) {
        double t = clamp(wireT, 0.0, 1.0);
        return endpointA.lerp(endpointB, t);
    }

    /**
     * 電線の接線方向を求める。
     * 端点の順番が逆でも、同じ電線に対して一貫した方向を返す。
     */
    public static Vec3 tangent(
            Vec3 endpointA,
            Vec3 endpointB
    ) {
        Vec3 direction = endpointB.subtract(endpointA);

        if (direction.lengthSqr() < EPSILON) {
            return new Vec3(1.0, 0.0, 0.0);
        }

        return direction.normalize();
    }

    /**
     * 電線に対して垂直な、安定した基準ベクトルを求める。
     *
     * 垂直に近い電線でも、参照ベクトルを切り替えて
     * 基底が不安定にならないようにする。
     */
    public static Vec3 referencePerpendicular(Vec3 tangent) {
        Vec3 normalized = normalizeOrDefault(tangent);

        Vec3 reference;

        if (Math.abs(normalized.y) < 0.85) {
            reference = new Vec3(0.0, 1.0, 0.0);
        } else {
            reference = new Vec3(1.0, 0.0, 0.0);
        }

        Vec3 perpendicular = normalized.cross(reference);

        if (perpendicular.lengthSqr() < EPSILON) {
            reference = new Vec3(0.0, 0.0, 1.0);
            perpendicular = normalized.cross(reference);
        }

        return normalizeOrDefault(perpendicular);
    }

    /**
     * 電線を軸にした8方向の分岐ベクトルを求める。
     *
     * 方向番号は電線のローカル平面上で45度ずつ回転する。
     * 0～7以外の値も0～7に正規化する。
     */
    public static Vec3 branchDirection(
            Vec3 endpointA,
            Vec3 endpointB,
            int directionIndex
    ) {
        Vec3 axis = tangent(endpointA, endpointB);
        Vec3 right = referencePerpendicular(axis);
        Vec3 up = normalizeOrDefault(axis.cross(right));

        int index = Math.floorMod(directionIndex, 8);
        double angle = index * (Math.PI / 4.0);

        return normalizeOrDefault(
                right.scale(Math.cos(angle))
                        .add(up.scale(Math.sin(angle)))
        );
    }

    /**
     * 電線上の取り付け位置から分岐端子 C の座標を求める。
     *
     * offset はブロック単位。
     */
    public static Vec3 branchPortPosition(
            Vec3 endpointA,
            Vec3 endpointB,
            double wireT,
            int directionIndex,
            double offset
    ) {
        Vec3 center = pointOnWire(
                endpointA,
                endpointB,
                wireT
        );

        Vec3 direction = branchDirection(
                endpointA,
                endpointB,
                directionIndex
        );

        return center.add(direction.scale(offset));
    }

    /**
     * 電線の真下に端子を置くための位置。
     */
    public static Vec3 downwardPortPosition(
            Vec3 endpointA,
            Vec3 endpointB,
            double wireT,
            double offset
    ) {
        return pointOnWire(
                endpointA,
                endpointB,
                wireT
        ).add(0.0, -offset, 0.0);
    }

    private static Vec3 normalizeOrDefault(Vec3 vector) {
        if (vector.lengthSqr() < EPSILON) {
            return new Vec3(1.0, 0.0, 0.0);
        }

        return vector.normalize();
    }

    private static double clamp(
            double value,
            double minimum,
            double maximum
    ) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}

