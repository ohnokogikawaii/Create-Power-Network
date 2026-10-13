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

        return findNearest(provider, state, blockPos, hitLocation);
    }

    public static WireConnectionPointProvider.ConnectionPoint
    findNearest(
            WireConnectionPointProvider provider,
            BlockState state,
            BlockPos blockPos,
            Vec3 hitLocation
    ) {
        if (provider == null || hitLocation == null) {
            return null;
        }

        Vec3 localHit = toLocalPosition(blockPos, hitLocation);

        WireConnectionPointProvider.ConnectionPoint nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (var point : getConnectionPoints(provider, state)) {
            double distance = point.position().distanceToSqr(localHit);

            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = point;
            }
        }

        return nearest;
    }

    public static Vec3 toWorldPosition(
            BlockPos blockPos,
            WireConnectionPointProvider.ConnectionPoint point
    ) {
        if (point == null) {
            throw new IllegalArgumentException(
                    "Connection point cannot be null"
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

    public static AABB getHighlightBox(
            BlockPos blockPos,
            WireConnectionPointProvider.ConnectionPoint point
    ) {
        return point == null
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
