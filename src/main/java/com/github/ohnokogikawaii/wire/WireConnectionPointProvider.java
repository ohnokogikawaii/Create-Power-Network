
package com.github.ohnokogikawaii.wire;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Implemented by blocks that expose one or more wire connection points.
 *
 * Connection positions and highlight bounds are defined once by the block
 * and reused by wire placement and client highlighting.
 */
public interface WireConnectionPointProvider {

    /**
     * A connection point in block-local coordinates.
     *
     * @param id stable identifier for this point within the block
     * @param position local connection position, usually within 0.0..1.0
     * @param highlightHalfSize half-size of the highlight box on each axis
     */
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

    /**
     * Factory for a standard terminal connection point.
     * The block only needs to specify its ID and local position.
     */
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

    /**
     * Define all wire connection points in block-local coordinates.
     */
    List<ConnectionPoint> getConnectionPoints();

    /**
     * Find the nearest defined connection point to a world-space hit.
     * Returns null if the block defines no points.
     */
    default ConnectionPoint getNearestConnectionPoint(
            BlockPos pos,
            Vec3 hitLocation
    ) {
        Vec3 localHit = hitLocation.subtract(
                pos.getX(),
                pos.getY(),
                pos.getZ()
        );

        ConnectionPoint nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (ConnectionPoint point : getConnectionPoints()) {
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