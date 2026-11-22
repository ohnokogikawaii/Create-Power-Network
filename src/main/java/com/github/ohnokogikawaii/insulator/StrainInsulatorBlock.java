package com.github.ohnokogikawaii.insulator;

import com.github.ohnokogikawaii.registry.ModBlockEntities;
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

public class StrainInsulatorBlock extends BaseEntityBlock
        implements WireConnectionPointProvider {

    public static final DirectionProperty FACING =
            BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(3, 6, 1, 13, 10, 15),
            Block.box(4, 5, 3, 12, 11, 5),
            Block.box(4, 5, 7, 12, 11, 9),
            Block.box(4, 5, 11, 12, 11, 13),
            Block.box(6, 4, 0, 10, 12, 2),
            Block.box(6, 4, 14, 10, 12, 16)
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
                new Vec3(0.5, 0.5, 1.5 / 16.0),
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
     * 向きに応じて接続点を回転する。
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
    public VoxelShape getCollisionShape(
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