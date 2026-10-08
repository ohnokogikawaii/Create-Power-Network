package com.github.ohnokogikawaii.wire;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Physical attachment point.
 *
 * No electrical/network state belongs here.
 */
public record WireEndpoint(BlockPos blockPos) {

    public Vec3 worldPosition(Level level) {
        return Vec3.atLowerCornerOf(blockPos).add(0.5, 0.5, 0.5);
    }
}
