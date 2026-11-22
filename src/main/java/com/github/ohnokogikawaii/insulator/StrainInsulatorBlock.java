
package com.github.ohnokogikawaii.insulator;

import com.github.ohnokogikawaii.wire.WireConnectionPointProvider;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Map;

public class StrainInsulatorBlock extends BaseEntityBlock
        implements WireConnectionPointProvider {

    public static final DirectionProperty FACING =
            BlockStateProperties.HORIZONTAL_FACING;

    /*
     * strain_insulator.json の17個のelementを反映した形状。
     * Blockbenchの座標は0～16スケール。
     */
    private static final VoxelShape BASE_SHAPE = Shapes.or(
            // 奥側の碍子本体
            Block.box(6, 5, 11.75, 10, 9, 15.75),

            // 奥側の接続部
            Block.box(6.5, 5.5, 11, 9.5, 8.5, 11.75),
            Block.box(7.25, 6.25, 10.25, 8.75, 7.75, 11.25),
            Block.box(7.25, 6.25, 15.25, 8.75, 7.75, 16),

            // 手前側の碍子本体
            Block.box(6, 5, 6.25, 10, 9, 10.25),

            // 手前側の接続部
            Block.box(6.5, 5.5, 5.5, 9.5, 8.5, 6.25),
            Block.box(7.5, 6.5, 4.75, 8.5, 7.5, 5.75),

            // 端部の結線金具
            Block.box(7, 6, 0, 7.75, 8, 0.75),
            Block.box(7.75, 6.75, 0.5, 8.25, 7.25, 0.75),
            Block.box(8.25, 6, 0, 9, 8, 0.75),
            Block.box(7.75, 6, 0, 8.25, 6.75, 0.75),
            Block.box(7.75, 7.25, 0, 8.25, 8, 0.75),

            // 本体と段差部分
            Block.box(7, 6, 0.75, 9, 8, 4.75),
            Block.box(7, 8, 0.75, 9, 9, 1.75),
            Block.box(7, 8, 1.75, 9, 10, 2.75),
            Block.box(7, 8, 2.75, 9, 11, 3.75),
            Block.box(7, 8, 3.75, 9, 11.5, 4.75)
    );

    /*
     * ブロックの向きに合わせた当たり判定。
     */
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.NORTH, BASE_SHAPE,
            Direction.SOUTH, rotateShape(BASE_SHAPE, Direction.SOUTH),
            Direction.EAST, rotateShape(BASE_SHAPE, Direction.EAST),
            Direction.WEST, rotateShape(BASE_SHAPE, Direction.WEST)
    );

    public StrainInsulatorBlock(Properties properties) {
        super(properties);

        registerDefaultState(
                stateDefinition.any().setValue(
                        FACING,
                        Direction.NORTH
                )
        );
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder
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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StrainInsulatorBlockEntity(pos, state);
    }

    @Override
    public List<ConnectionPoint> getConnectionPoints() {
        return getConnectionPoints(defaultBlockState());
    }

    @Override
    public List<ConnectionPoint> getConnectionPoints(BlockState state) {
        Vec3 local = rotatePosition(
                new Vec3(
                        8.0 / 16.0,
                        7.0 / 16.0,
                        0.625 / 16.0
                ),
                state.getValue(FACING)
        );

        return List.of(
                new ConnectionPoint(
                        "retained_wire",
                        local,
                        new Vec3(
                                3.5 / 16.0,
                                3.5 / 16.0,
                                3.5 / 16.0
                        )
                )
        );
    }

    /**
     * 引き留め電線の接続位置。
     */
    public Vec3 getRetainedWirePosition(
            BlockState state,
            BlockPos pos
    ) {
        return getConnectionPoints(state)
                .get(0)
                .worldPosition(pos);
    }

    /**
     * ジャンパ線の接続位置。
     * strain_insulator.json の "jumper" element の中心を使用する。
     */
    public Vec3 getJumperWirePosition(
            BlockState state,
            BlockPos pos
    ) {
        Vec3 local = rotatePosition(
                new Vec3(
                        8.0 / 16.0,
                        10.5 / 16.0,
                        4.0 / 16.0
                ),
                state.getValue(FACING)
        );

        return Vec3.atLowerCornerOf(pos).add(local);
    }

    /**
     * 接続点をブロックの向きに合わせて回転する。
     */
    private static Vec3 rotatePosition(
            Vec3 position,
            Direction facing
    ) {
        return switch (facing) {
            case SOUTH -> new Vec3(
                    1.0 - position.x,
                    position.y,
                    1.0 - position.z
            );
            case EAST -> new Vec3(
                    1.0 - position.z,
                    position.y,
                    position.x
            );
            case WEST -> new Vec3(
                    position.z,
                    position.y,
                    1.0 - position.x
            );
            default -> position;
        };
    }

    /**
     * 北向き基準のVoxelShapeを、接続点と同じ規則で回転する。
     */
    private static VoxelShape rotateShape(
            VoxelShape source,
            Direction facing
    ) {
        VoxelShape result = Shapes.empty();

        for (AABB box : source.toAabbs()) {
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
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return MapCodec.unit(this);
    }
}
