
package com.github.ohnokogikawaii.insulator;

import com.github.ohnokogikawaii.wire.WireConnectionPointProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

public class InsulatorBlock extends HorizontalDirectionalBlock
        implements WireConnectionPointProvider {

    public enum Type {
        LOW_VOLTAGE,
        HIGH_VOLTAGE,
        EXTRA_HIGH_VOLTAGE,
        TRANSMISSION_LINE
    }

    private final Type type;

    private static final VoxelShape LOW_SHAPE = Shapes.or(
            Block.box(4, 0, 4, 12, 2, 12),
            Block.box(6, 2, 6, 10, 8, 10),
            Block.box(4, 3, 4, 12, 4, 12),
            Block.box(5, 5, 5, 11, 6, 11),
            Block.box(4, 7, 4, 12, 8, 12),
            Block.box(5, 8, 5, 11, 10, 11),
            Block.box(7, 10, 7, 9, 12, 9)
    );

    private static final VoxelShape HIGH_SHAPE = Shapes.or(
            Block.box(3, 0, 3, 13, 2, 13),
            Block.box(6, 2, 6, 10, 9, 10),
            Block.box(3, 3, 3, 13, 4, 13),
            Block.box(4, 5, 4, 12, 6, 12),
            Block.box(3, 7, 3, 13, 8, 13),
            Block.box(5, 9, 5, 11, 11, 11),
            Block.box(7, 11, 7, 9, 13, 9)
    );

    private static final VoxelShape EXTRA_HIGH_SHAPE = Shapes.or(
            Block.box(2, 0, 2, 14, 2, 14),
            Block.box(6, 2, 6, 10, 11, 10),
            Block.box(2, 3, 2, 14, 4, 14),
            Block.box(3, 5, 3, 13, 6, 13),
            Block.box(2, 7, 2, 14, 8, 14),
            Block.box(3, 9, 3, 13, 10, 13),
            Block.box(2, 11, 2, 14, 12, 14),
            Block.box(4, 12, 4, 12, 14, 12),
            Block.box(7, 14, 7, 9, 15, 9)
    );

    private static final VoxelShape TRANSMISSION_SHAPE = Shapes.or(
            Block.box(6, 6, 1, 10, 10, 4),
            Block.box(4, 5, 4, 12, 11, 12),
            Block.box(3, 4, 5, 13, 12, 7),
            Block.box(4, 5, 7, 12, 11, 9),
            Block.box(3, 4, 9, 13, 12, 11),
            Block.box(4, 5, 11, 12, 11, 13),
            Block.box(6, 6, 13, 10, 10, 15)
    );

    public InsulatorBlock(Properties properties, Type type) {
        super(properties);
        this.type = type;
        registerDefaultState(
                stateDefinition.any().setValue(FACING, Direction.NORTH)
        );
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder
    ) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(
                FACING,
                context.getHorizontalDirection().getOpposite()
        );
    }

    @Override
    public List<ConnectionPoint> getConnectionPoints() {
        return getConnectionPoints(defaultBlockState());
    }

    @Override
    public List<ConnectionPoint> getConnectionPoints(BlockState state) {
        if (type == Type.TRANSMISSION_LINE) {
            Vec3 point = rotatePosition(
                    new Vec3(0.5, 0.5, 1.5 / 16.0),
                    state.getValue(FACING)
            );
            return List.of(new ConnectionPoint(
                    "wire",
                    point,
                    new Vec3(3.5 / 16.0, 1.5 / 16.0, 3.5 / 16.0)
            ));
        }

        double height = switch (type) {
            case LOW_VOLTAGE -> 11.5 / 16.0;
            case HIGH_VOLTAGE -> 12.5 / 16.0;
            case EXTRA_HIGH_VOLTAGE -> 14.5 / 16.0;
            case TRANSMISSION_LINE -> 0.5;
        };

        return List.of(WireConnectionPointProvider.connectionPoint(
                "wire", 0.5, height, 0.5
        ));
    }

    private static Vec3 rotatePosition(Vec3 p, Direction facing) {
        return switch (facing) {
            case SOUTH -> new Vec3(1 - p.x, p.y, 1 - p.z);
            case EAST -> new Vec3(1 - p.z, p.y, p.x);
            case WEST -> new Vec3(p.z, p.y, 1 - p.x);
            default -> p;
        };
    }

    private VoxelShape getBaseShape() {
        return switch (type) {
            case LOW_VOLTAGE -> LOW_SHAPE;
            case HIGH_VOLTAGE -> HIGH_SHAPE;
            case EXTRA_HIGH_VOLTAGE -> EXTRA_HIGH_SHAPE;
            case TRANSMISSION_LINE -> TRANSMISSION_SHAPE;
        };
    }

    private static VoxelShape rotateShape(
            VoxelShape shape,
            Direction facing
    ) {
        if (facing == Direction.NORTH) {
            return shape;
        }

        VoxelShape result = Shapes.empty();

        for (AABB box : shape.toAabbs()) {
            double minX;
            double maxX;
            double minZ;
            double maxZ;

            switch (facing) {
                case SOUTH -> {
                    minX = 1 - box.maxX;
                    maxX = 1 - box.minX;
                    minZ = 1 - box.maxZ;
                    maxZ = 1 - box.minZ;
                }
                case EAST -> {
                    minX = 1 - box.maxZ;
                    maxX = 1 - box.minZ;
                    minZ = box.minX;
                    maxZ = box.maxX;
                }
                case WEST -> {
                    minX = box.minZ;
                    maxX = box.maxZ;
                    minZ = 1 - box.maxX;
                    maxZ = 1 - box.minX;
                }
                default -> {
                    minX = box.minX;
                    maxX = box.maxX;
                    minZ = box.minZ;
                    maxZ = box.maxZ;
                }
            }

            result = Shapes.or(result, Shapes.box(
                    minX, box.minY, minZ,
                    maxX, box.maxY, maxZ
            ));
        }

        return result;
    }

    @Override
    public VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return rotateShape(getBaseShape(), state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return getShape(state, level, pos, context);
    }
}
