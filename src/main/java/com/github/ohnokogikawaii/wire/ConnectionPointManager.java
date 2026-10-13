package com.github.ohnokogikawaii.wire;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class ConnectionPointManager {

    private ConnectionPointManager() {
    }

    public static List<WireConnectionPointProvider.ConnectionPoint>
    getConnectionPoints(BlockState state) {
        if (!(state.getBlock()
                instanceof WireConnectionPointProvider provider)) {
            return List.of();
        }

        return getConnectionPoints(provider, state);
    }

    public static List<WireConnectionPointProvider.ConnectionPoint>
    getConnectionPoints(
            WireConnectionPointProvider provider,
            BlockState state
    ) {
        if (provider == null) {
            return List.of();
        }

        List<WireConnectionPointProvider.ConnectionPoint> points =
                state == null
                        ? provider.getConnectionPoints()
                        : provider.getConnectionPoints(state);

        return points == null ? List.of() : points;
    }

    public static WireConnectionPointProvider.ConnectionPoint
    getConnectionPoint(BlockState state, String id) {
        if (id == null || id.isBlank()) {
            return null;
        }

        for (var point : getConnectionPoints(state)) {
            if (point.id().equals(id)) {
                return point;
            }
        }

        return null;
    }

    public static WireConnectionPointProvider.ConnectionPoint
    findNearest(
            BlockState state,
            BlockPos blockPos,
            Vec3 hitLocation
    ) {
        if (state == null
                || !(state.getBlock()
                instanceof WireConnectionPointProvider provider)) {
            return null;
        }

        return findNearest(
                provider,
                state,
                blockPos,
                hitLocation
        );
    }

    public static WireConnectionPointProvider.ConnectionPoint
    findNearest(
            WireConnectionPointProvider provider,
            BlockState state,
            BlockPos blockPos,
            Vec3 hitLocation
    ) {
        if (provider == null || blockPos == null || hitLocation == null) {
            return null;
        }

        Vec3 localHit = toLocalPosition(blockPos, hitLocation);

        WireConnectionPointProvider.ConnectionPoint nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (var point : getConnectionPoints(provider, state)) {
            if (point == null) {
                continue;
            }

            double distance = point.position().distanceToSqr(localHit);

            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = point;
            }
        }

        return nearest;
    }

    /**
     * 表示方式に応じたハイライト箱を取得する。
     */
    public static AABB getHighlightBox(
            WireConnectionPointProvider provider,
            BlockState state,
            BlockGetter level,
            BlockPos blockPos,
            WireConnectionPointProvider.ConnectionPoint point
    ) {
        if (provider == null
                || state == null
                || level == null
                || blockPos == null) {
            return null;
        }

        if (provider.getHighlightMode(state)
                == WireConnectionPointProvider.HighlightMode.BLOCK) {
            return provider.getBlockHighlightBox(
                    state,
                    level,
                    blockPos
            );
        }

        if (point == null) {
            return null;
        }

        return point.createWorldHighlightBox(blockPos);
    }

    /**
     * 表示方式に応じたハイライト色を取得する。
     */
    public static int getHighlightColor(
            WireConnectionPointProvider provider,
            BlockState state,
            WireConnectionPointProvider.ConnectionPoint point
    ) {
        if (provider == null || state == null) {
            return WireConnectionPointProvider.DEFAULT_HIGHLIGHT_COLOR;
        }

        int color;

        if (provider.getHighlightMode(state)
                == WireConnectionPointProvider.HighlightMode.BLOCK) {
            color = provider.getBlockHighlightColor(state);
        } else {
            color = provider.getConnectionPointHighlightColor(
                    state,
                    point
            );
        }

        return color & 0xFFFFFF;
    }

    public static Vec3 toWorldPosition(
            BlockPos blockPos,
            WireConnectionPointProvider.ConnectionPoint point
    ) {
        if (blockPos == null || point == null) {
            throw new IllegalArgumentException(
                    "Block position and connection point are required"
            );
        }

        return point.worldPosition(blockPos);
    }

    public static Vec3 toLocalPosition(
            BlockPos blockPos,
            Vec3 worldPosition
    ) {
        return worldPosition.subtract(
                blockPos.getX(),
                blockPos.getY(),
                blockPos.getZ()
        );
    }

    /**
     * 既存コードとの互換性用。
     * 接続点単体のハイライト箱を返す。
     */
    public static AABB getHighlightBox(
            BlockPos blockPos,
            WireConnectionPointProvider.ConnectionPoint point
    ) {
        return point == null || blockPos == null
                ? null
                : point.createWorldHighlightBox(blockPos);
    }

    public static boolean isConnectionPointProvider(
            BlockGetter level,
            BlockPos blockPos
    ) {
        return level.getBlockState(blockPos).getBlock()
                instanceof WireConnectionPointProvider;
    }
}