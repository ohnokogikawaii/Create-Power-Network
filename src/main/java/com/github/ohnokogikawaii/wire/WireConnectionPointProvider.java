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

            if (highlightHalfSize.x < 0.0
                    || highlightHalfSize.y < 0.0
                    || highlightHalfSize.z < 0.0) {
                throw new IllegalArgumentException(
                        "Connection point highlight size cannot be negative"
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

    default List<ConnectionPoint> getConnectionPoints(
            BlockState state
    ) {
        return getConnectionPoints();
    }

    /**
     * BlockState に依存しない接続点の検索。
     * 既存コードとの互換性を維持する。
     */
    default ConnectionPoint getNearestConnectionPoint(
            BlockPos pos,
            Vec3 hitLocation
    ) {
        return ConnectionPointManager.findNearest(
                this,
                null,
                pos,
                hitLocation
        );
    }

    /**
     * BlockState に依存する接続点の検索。
     */
    default ConnectionPoint getNearestConnectionPoint(
            BlockPos pos,
            Vec3 hitLocation,
            BlockState state
    ) {
        return ConnectionPointManager.findNearest(
                this,
                state,
                pos,
                hitLocation
        );
    }

    default boolean isWireAnchorValid(
            BlockGetter level,
            BlockPos pos
    ) {
        return ConnectionPointManager.isConnectionPointProvider(
                level,
                pos
        );
    }
}
