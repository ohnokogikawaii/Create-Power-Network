
package com.github.ohnokogikawaii.insulator;

import com.github.ohnokogikawaii.wire.WireConnectionPointProvider;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import com.github.ohnokogikawaii.wire.WireEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.RenderShape;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import java.util.List;

public class InsulatorBlock extends BaseEntityBlock
        implements WireConnectionPointProvider {

    public enum Type {
        LOW_VOLTAGE(5.0),
        HIGH_VOLTAGE(5.0),
        EXTRA_HIGH_VOLTAGE(5.0),
        TRANSMISSION_LINE(5.0);

        private final double maxDeflectionDegrees;

        Type(double maxDeflectionDegrees) {
            this.maxDeflectionDegrees = maxDeflectionDegrees;
        }

        public double getMaxDeflectionDegrees() {
            return maxDeflectionDegrees;
        }
    }

    private final Type type;

    public Type getInsulatorType() {
        return type;
    }

    private static final VoxelShape LOW_SHAPE = Shapes.or(
            // 最下部の金具
            Block.box(7, 0, 7, 9, 1, 9),

            // 上部の溝：左側
            Block.box(7, 4.5, 7.125, 7.75, 5.25, 8.875),

            // 上部の溝：中央底面
            Block.box(7.75, 4.5, 7, 8.25, 4.75, 9),

            // 上部の溝：右側
            Block.box(8.25, 4.5, 7.125, 9, 5.25, 8.875),

            // 下部台座
            Block.box(6.5, 1, 6.5, 9.5, 2, 9.5),

            // 中央の碍子本体
            Block.box(6.5, 2, 6.5, 9.5, 4, 9.5),

            // 上部の首
            Block.box(6.75, 4, 6.75, 9.25, 4.5, 9.25)
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

    /**
     * 碍子の種類・向きごとの回転済み形状キャッシュ。
     *
     * 起動時に各形状を一度だけ生成し、
     * getShape() ではキャッシュ済みの形状を返す。
     */
    private static final Map<Type, Map<Direction, VoxelShape>> SHAPE_CACHE =
            createShapeCache();

    private static Map<Type, Map<Direction, VoxelShape>> createShapeCache() {
        Map<Type, Map<Direction, VoxelShape>> cache =
                new EnumMap<>(Type.class);

        for (Type type : Type.values()) {
            VoxelShape baseShape = switch (type) {
                case LOW_VOLTAGE -> LOW_SHAPE;
                case HIGH_VOLTAGE -> HIGH_SHAPE;
                case EXTRA_HIGH_VOLTAGE -> EXTRA_HIGH_SHAPE;
                case TRANSMISSION_LINE -> TRANSMISSION_SHAPE;
            };

            Map<Direction, VoxelShape> directions =
                    new EnumMap<>(Direction.class);

            directions.put(Direction.NORTH, baseShape);

            directions.put(
                    Direction.SOUTH,
                    rotateShape(baseShape, Direction.SOUTH)
            );

            directions.put(
                    Direction.EAST,
                    rotateShape(baseShape, Direction.EAST)
            );

            directions.put(
                    Direction.WEST,
                    rotateShape(baseShape, Direction.WEST)
            );

            cache.put(type, Map.copyOf(directions));
        }

        return Map.copyOf(cache);
    }

    public static final DirectionProperty FACING =
            BlockStateProperties.HORIZONTAL_FACING;

    public InsulatorBlock(Properties properties, Type type) {
        super(properties);
        this.type = type;

        registerDefaultState(
                stateDefinition.any().setValue(
                        FACING,
                        Direction.NORTH
                )
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
    public BlockEntity newBlockEntity(
            BlockPos pos,
            BlockState state
    ) {
        return new InsulatorBlockEntity(pos, state);
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
                    new Vec3(
                            3.5 / 16.0,
                            1.5 / 16.0,
                            3.5 / 16.0
                    )
            ));
        }

        double height = switch (type) {
            case LOW_VOLTAGE -> 5.0 / 16.0;
            case HIGH_VOLTAGE -> 12.5 / 16.0;
            case EXTRA_HIGH_VOLTAGE -> 14.5 / 16.0;
            case TRANSMISSION_LINE -> 0.5;
        };

        return List.of(
                WireConnectionPointProvider.connectionPoint(
                        "wire",
                        0.5,
                        height,
                        0.5
                )
        );
    }

    /**
     * 固定式碍子の支持位置をワールド座標で取得する。
     */
    public Vec3 getSupportPosition(BlockState state, BlockPos pos) {
        ConnectionPoint point = getConnectionPoints(state).get(0);
        return point.worldPosition(pos);
    }

    private static Vec3 rotatePosition(
            Vec3 p,
            Direction facing
    ) {
        return switch (facing) {
            case SOUTH -> new Vec3(
                    1 - p.x,
                    p.y,
                    1 - p.z
            );
            case EAST -> new Vec3(
                    1 - p.z,
                    p.y,
                    p.x
            );
            case WEST -> new Vec3(
                    p.z,
                    p.y,
                    1 - p.x
            );
            default -> p;
        };
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
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
        return SHAPE_CACHE
                .get(type)
                .get(state.getValue(FACING));
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

    /**
     * 碍子が撤去されたとき、支持している電線から支持点を解除する。
     *
     * 電線自体は切断しない。
     * 未ロードの電線については、WireEntity側で後から検証する。
     */
    @Override
    public void onRemove(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState newState,
            boolean movedByPiston
    ) {
        // 同じブロックの状態変化では支持関係を解除しない。
        if (state.getBlock() != newState.getBlock()
                && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos)
                instanceof InsulatorBlockEntity insulatorEntity) {

            for (UUID wireId : insulatorEntity.getSupportedWireIds()) {
                if (serverLevel.getEntity(wireId)
                        instanceof WireEntity wire) {

                    wire.removeSupportPoint(pos);
                }
            }

            insulatorEntity.clearSupportedWires();
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return MapCodec.unit(this);
    }
}
