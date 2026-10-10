
package com.github.ohnokogikawaii.hub_connector;

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

import java.util.ArrayList;
import java.util.List;

public class HubConnectorBlock extends HorizontalDirectionalBlock
        implements WireConnectionPointProvider {

    private static final VoxelShape BASE_SHAPE = Shapes.or(
            // 中央の接続本体
            Block.box(5, 5, 5, 11, 11, 11),

            // 左端子
            Block.box(0, 6, 6, 6, 10, 10),

            // 右端子
            Block.box(10, 6, 6, 16, 10, 10),

            // 分岐端子（基準方向は北）
            Block.box(6, 6, 0, 10, 10, 6)
    );

    private static final List<ConnectionPoint> BASE_POINTS = List.of(
            // 左側
            WireConnectionPointProvider.connectionPoint(
                    "left",
                    0.5 / 16.0,
                    0.5,
                    0.5
            ),

            // 右側
            WireConnectionPointProvider.connectionPoint(
                    "right",
                    15.5 / 16.0,
                    0.5,
                    0.5
            ),

            // 分岐側
            WireConnectionPointProvider.connectionPoint(
                    "branch",
                    0.5,
                    0.5,
                    0.5 / 16.0
            )
    );

    public HubConnectorBlock(Properties properties) {
        super(properties);
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
    public BlockState getStateForPlacement(
            BlockPlaceContext context
    ) {
        return defaultBlockState().setValue(
                FACING,
                context.getHorizontalDirection().getOpposite()
        );
    }

    @Override
    public List<ConnectionPoint> getConnectionPoints() {
        return BASE_POINTS;
    }

    @Override
    public List<ConnectionPoint> getConnectionPoints(
            BlockState state
    ) {
        Direction facing = state.getValue(FACING);
        List<ConnectionPoint> result = new ArrayList<>(3);

        for (ConnectionPoint point : BASE_POINTS) {
            Vec3 rotated = rotatePosition(
                    point.position(),
                    facing
            );

            result.add(new ConnectionPoint(
                    point.id(),
                    rotated,
                    point.highlightHalfSize()
            ));
        }

        return List.copyOf(result);
    }

    private static Vec3 rotatePosition(
            Vec3 position,
            Direction facing
    ) {
        double x = position.x;
        double y = position.y;
        double z = position.z;

        return switch (facing) {
            case SOUTH -> new Vec3(1.0 - x, y, 1.0 - z);
            case EAST -> new Vec3(1.0 - z, y, x);
            case WEST -> new Vec3(z, y, 1.0 - x);
            default -> position;
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
                    minX = 1.0 - box.maxX;
                    maxX = 1.0 - box.minX;
                    minZ = 1.0 - box.maxZ;
                    maxZ = 1.0 - box.minZ;
                }
                case EAST -> {
                    minX = 1.0 - box.maxZ;
                    maxX = 1.0 - box.minZ;
                    minZ = box.minX;
                    maxZ = box.maxX;
                }
                case WEST -> {
                    minX = box.minZ;
                    maxX = box.maxZ;
                    minZ = 1.0 - box.maxX;
                    maxZ = 1.0 - box.minX;
                }
                default -> {
                    minX = box.minX;
                    maxX = box.maxX;
                    minZ = box.minZ;
                    maxZ = box.maxZ;
                }
            }

            result = Shapes.or(
                    result,
                    Shapes.box(
                            minX,
                            box.minY,
                            minZ,
                            maxX,
                            box.maxY,
                            maxZ
                    )
            );
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
        return rotateShape(
                BASE_SHAPE,
                state.getValue(FACING)
        );
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