package com.github.ohnokogikawaii.wire;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public interface WireConnectionPointProvider {

    /**
     * ブロック内の接続点をローカル座標で返す。
     * 座標の範囲は通常 0.0 ～ 1.0。
     */
    List<Vec3> getConnectionPoints();

    /**
     * ワールド座標の照準位置に最も近い接続点を返す。
     */
    default Vec3 getNearestConnectionPoint(
            BlockPos pos,
            Vec3 hitLocation
    ) {
        Vec3 localHit = hitLocation.subtract(
                pos.getX(),
                pos.getY(),
                pos.getZ()
        );

        Vec3 nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Vec3 point : getConnectionPoints()) {
            double distance = point.distanceToSqr(localHit);

            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = point;
            }
        }

        return nearest == null
                ? new Vec3(0.5, 0.5, 0.5)
                : nearest.add(
                pos.getX(),
                pos.getY(),
                pos.getZ()
        );
    }

    default boolean isWireAnchorValid(
            BlockGetter level,
            BlockPos pos
    ) {
        return level.getBlockState(pos).getBlock()
                instanceof WireConnectionPointProvider;
    }
}