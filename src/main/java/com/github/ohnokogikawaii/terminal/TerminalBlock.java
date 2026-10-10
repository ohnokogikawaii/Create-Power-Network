
package com.github.ohnokogikawaii.terminal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Physical wire terminal.
 *
 * The outline and collision shapes match the cuboid dimensions
 * in assets/powernetwork/models/block/connector/terminal.json.
 */
public class TerminalBlock extends Block {

    private static final VoxelShape TERMINAL_SHAPE = Shapes.or(
            // gaisi1: [6, 0, 6] - [10, 1, 10]
            Block.box(6, 0, 6, 10, 1, 10),

            // gaisi2: [6, 2, 6] - [10, 3, 10]
            Block.box(6, 2, 6, 10, 3, 10),

            // gaisi3: [6, 4, 6] - [10, 5, 10]
            Block.box(6, 4, 6, 10, 5, 10),

            // tansi1: [6.5, 1, 6.5] - [9.5, 2, 9.5]
            Block.box(6.5, 1, 6.5, 9.5, 2, 9.5),

            // tansi2: [6.5, 3, 6.5] - [9.5, 4, 9.5]
            Block.box(6.5, 3, 6.5, 9.5, 4, 9.5),

            // tansi3: [6.5, 5, 6.5] - [9.5, 6, 9.5]
            Block.box(6.5, 5, 6.5, 9.5, 6, 9.5)
    );

    public TerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return TERMINAL_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return TERMINAL_SHAPE;
    }
}