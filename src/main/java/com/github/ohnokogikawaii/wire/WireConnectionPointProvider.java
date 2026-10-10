
package com.github.ohnokogikawaii.wire;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public interface WireConnectionPointProvider {

    record ConnectionPoint(
            String id,
            Vec3 position,
            Vec3 highlightHalfSize
    ) {
        public ConnectionPoint {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException(
                        "Connection point id cannot be blank"
                );
            }

            if (position == null || highlightHalfSize == null) {
                throw new IllegalArgumentException(
                        "Connection point position and highlight size are required"
                );
            }
        }

        public Vec3 worldPosition(BlockPos blockPos) {
            return position.add(
                    blockPos.getX(),
                    blockPos.getY(),
                    blockPos.getZ()
            );
        }

        public AABB createWorldHighlightBox(BlockPos blockPos) {
            Vec3 center = worldPosition(blockPos);

            return new AABB(
                    center.x - highlightHalfSize.x,
                    center.y - highlightHalfSize.y,
                    center.z - highlightHalfSize.z,
                    center.x + highlightHalfSize.x,
                    center.y + highlightHalfSize.y,
                    center.z + highlightHalfSize.z
            );
        }
    }

    static ConnectionPoint connectionPoint(
            String id,
            double x,
            double y,
            double z
    ) {
        return new ConnectionPoint(
                id,
                new Vec3(x, y, z),
                new Vec3(
                        3.5 / 16.0,
                        1.5 / 16.0,
                        3.5 / 16.0
                )
        );
    }

    List<ConnectionPoint> getConnectionPoints();

    /**
     * 向きを必要とするブロックは、このメソッドを実装する。
     * 従来の端子は従来どおりの接続点を返す。
     */
    default List<ConnectionPoint> getConnectionPoints(
            BlockState state
    ) {
        return getConnectionPoints();
    }

    default ConnectionPoint getNearestConnectionPoint(
            BlockPos pos,
            Vec3 hitLocation
    ) {
        return getNearestConnectionPoint(
                pos,
                hitLocation,
                null
        );
    }

    default ConnectionPoint getNearestConnectionPoint(
            BlockPos pos,
            Vec3 hitLocation,
            BlockState state
    ) {
        Vec3 localHit = hitLocation.subtract(
                pos.getX(),
                pos.getY(),
                pos.getZ()
        );

        ConnectionPoint nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        List<ConnectionPoint> points = state == null
                ? getConnectionPoints()
                : getConnectionPoints(state);

        for (ConnectionPoint point : points) {
            double distance =
                    point.position().distanceToSqr(localHit);

            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = point;
            }
        }

        return nearest;
    }

    default boolean isWireAnchorValid(
            BlockGetter level,
            BlockPos pos
    ) {
        return level.getBlockState(pos).getBlock()
                instanceof WireConnectionPointProvider;
    }
}