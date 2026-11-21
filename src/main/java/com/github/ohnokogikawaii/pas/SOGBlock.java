
package com.github.ohnokogikawaii.pas;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SOGBlock extends BaseEntityBlock {

    public static final MapCodec<SOGBlock> CODEC = simpleCodec(SOGBlock::new);

    public static final DirectionProperty FACING =
            DirectionProperty.create("facing", Direction.Plane.HORIZONTAL);

    private static final VoxelShape NORTH_SHAPE =
            Block.box(2, 2, 0, 14, 14, 8);
    private static final VoxelShape SOUTH_SHAPE =
            Block.box(2, 2, 8, 14, 14, 16);
    private static final VoxelShape EAST_SHAPE =
            Block.box(8, 2, 2, 16, 14, 14);
    private static final VoxelShape WEST_SHAPE =
            Block.box(0, 2, 2, 8, 14, 14);

    public SOGBlock(Properties properties) {
        super(properties);
        registerDefaultState(
                stateDefinition.any().setValue(FACING, Direction.NORTH)
        );
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /**
     * 水平面の側面をクリックした場合だけ設置する。
     * FACINGはSOG本体が向く方向。
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();

        if (!clickedFace.getAxis().isHorizontal()) {
            return null;
        }

        return defaultBlockState().setValue(FACING, clickedFace);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos supportPos = pos.relative(facing.getOpposite());

        return level.getBlockState(supportPos)
                .isFaceSturdy(level, supportPos, facing);
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos
    ) {
        if (direction == state.getValue(FACING).getOpposite()
                && !canSurvive(state, level, pos)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }

        return super.updateShape(
                state,
                direction,
                neighborState,
                level,
                pos,
                neighborPos
        );
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder
    ) {
        builder.add(FACING);
    }

    /**
     * テスト用：
     * SOGを素手で右クリックするとPASへ開放指令を送る。
     */
    @Override
    public InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            net.minecraft.world.phys.BlockHitResult hit
    ) {
        BlockEntity entity = level.getBlockEntity(pos);

        if (!(entity instanceof SOGBlockEntity sog)) {
            return InteractionResult.PASS;
        }

// 未リンクの場合は開放指令を実行しない。
// PASControlCableItemによる接続操作を優先する。
        if (sog.getLinkedPasPos() == null) {
            return InteractionResult.PASS;
        }

        boolean success = sog.requestTrip();

        if (success) {
            player.displayClientMessage(
                    Component.literal("SOG: PASへの開放指令に成功しました。"),
                    true
            );
        } else {
            player.displayClientMessage(
                    Component.literal("SOG: 開放指令に失敗しました。接続状態を確認してください。"),
                    true
            );
        }

        return InteractionResult.CONSUME;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SOGBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
