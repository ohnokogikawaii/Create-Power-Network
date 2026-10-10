package com.github.ohnokogikawaii.terminal;

import com.github.ohnokogikawaii.wire.WireConnectionPointProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

public class TerminalBlock extends Block
        implements WireConnectionPointProvider {

    private static final VoxelShape TERMINAL_SHAPE = Shapes.or(
            // gaisi1
            Block.box(6, 0, 6, 10, 1, 10),

            // gaisi2
            Block.box(6, 2, 6, 10, 3, 10),

            // gaisi3
            Block.box(6, 4, 6, 10, 5, 10),

            // tansi1
            Block.box(6.5, 1, 6.5, 9.5, 2, 9.5),

            // tansi2
            Block.box(6.5, 3, 6.5, 9.5, 4, 9.5),

            // tansi3: the only wire connection point
            Block.box(6.5, 5, 6.5, 9.5, 6, 9.5)
    );

    private static final List<ConnectionPoint> CONNECTION_POINTS =
            List.of(
                    WireConnectionPointProvider.connectionPoint(
                            "tansi3",
                            0.5,
                            5.5 / 16.0,
                            0.5
                    )
            );

    public TerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    public List<ConnectionPoint> getConnectionPoints() {
        return CONNECTION_POINTS;
    }

    /**
     * Returns one highlight box covering the complete terminal shape.
     */
    public static AABB getHighlightBox(BlockPos pos) {
        return TERMINAL_SHAPE.bounds().move(
                pos.getX(),
                pos.getY(),
                pos.getZ()
        );
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

