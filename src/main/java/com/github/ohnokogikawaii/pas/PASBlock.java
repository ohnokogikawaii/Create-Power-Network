package com.github.ohnokogikawaii.pas;

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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

public class PASBlock extends BaseEntityBlock
        implements WireConnectionPointProvider {

    /**
     * FACINGは負荷側の向きを示す。
     * 例えばNORTHなら、負荷側が北、電源側が南になる。
     */
    public static final DirectionProperty FACING =
            BlockStateProperties.HORIZONTAL_FACING;

    /**
     * true = 開放、false = 投入
     */
    public static final BooleanProperty OPEN =
            BooleanProperty.create("open");

    private static final VoxelShape SHAPE =
            Block.box(1, 0, 1, 15, 16, 15);

    public PASBlock(Properties properties) {
        super(properties);

        registerDefaultState(
                stateDefinition.any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(OPEN, false)
        );
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder
    ) {
        builder.add(FACING, OPEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(
                        FACING,
                        context.getHorizontalDirection().getOpposite()
                )
                .setValue(OPEN, false);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PASBlockEntity(pos, state);
    }

    @Override
    public List<ConnectionPoint> getConnectionPoints() {
        return getConnectionPoints(defaultBlockState());
    }

    @Override
    public List<ConnectionPoint> getConnectionPoints(BlockState state) {
        Direction facing = state.getValue(FACING);

        /*
         * R・S・T相を横並びに配置。
         *
         * NORTH向きの場合：
         *   電源側 = 南側
         *   負荷側 = 北側
         *
         * FACINGに応じて6端子すべてを回転させる。
         */
        return List.of(
                createPoint("R_SOURCE", 0.22, 0.68, 0.90, facing),
                createPoint("S_SOURCE", 0.50, 0.68, 0.90, facing),
                createPoint("T_SOURCE", 0.78, 0.68, 0.90, facing),

                createPoint("R_LOAD", 0.22, 0.68, 0.10, facing),
                createPoint("S_LOAD", 0.50, 0.68, 0.10, facing),
                createPoint("T_LOAD", 0.78, 0.68, 0.10, facing)
        );
    }

    private static ConnectionPoint createPoint(
            String id,
            double x,
            double y,
            double z,
            Direction facing
    ) {
        Vec3 position = rotatePosition(
                new Vec3(x, y, z),
                facing
        );

        return new ConnectionPoint(
                id,
                position,
                new Vec3(
                        3.5 / 16.0,
                        3.5 / 16.0,
                        3.5 / 16.0
                )
        );
    }

    private static Vec3 rotatePosition(
            Vec3 position,
            Direction facing
    ) {
        return switch (facing) {
            case SOUTH -> new Vec3(
                    1 - position.x,
                    position.y,
                    1 - position.z
            );

            case EAST -> new Vec3(
                    1 - position.z,
                    position.y,
                    position.x
            );

            case WEST -> new Vec3(
                    position.z,
                    position.y,
                    1 - position.x
            );

            default -> position;
        };
    }

    @Override
    public HighlightMode getHighlightMode(BlockState state) {
        return HighlightMode.CONNECTION_POINT;
    }

    @Override
    public VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return SHAPE;
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