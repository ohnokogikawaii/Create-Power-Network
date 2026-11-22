
package com.github.ohnokogikawaii.pas;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PASOperatingRopeBlock extends Block {

    public static final int MAX_ROPE_LENGTH = 16;

    private static final VoxelShape SHAPE =
            Block.box(7, 0, 7, 9, 16, 9);

    public PASOperatingRopeBlock(Properties properties) {
        super(properties);

        registerDefaultState(
                stateDefinition.any().setValue(OPEN, false)
        );
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder
    ) {
        builder.add(OPEN);
    }

    public static final BooleanProperty OPEN =
            BooleanProperty.create("open");

    /**
     * レバーまたは既存の紐の真下にのみ設置可能。
     * 紐の最大長は16ブロック。
     */
    @Override
    public BlockState getStateForPlacement(
            net.minecraft.world.item.context.BlockPlaceContext context
    ) {
        if (context.getClickedFace() != Direction.DOWN) {
            return null;
        }

        BlockPos placementPos = context.getClickedPos();
        BlockPos abovePos = placementPos.above();

        int ropeCount = 0;
        BlockPos checkPos = abovePos;

        for (int i = 0; i <= MAX_ROPE_LENGTH; i++) {
            BlockState checkState =
                    context.getLevel().getBlockState(checkPos);

            if (checkState.getBlock() instanceof PASOperatingLeverBlock) {
                return ropeCount < MAX_ROPE_LENGTH
                        ? defaultBlockState()
                        : null;
            }

            if (checkState.getBlock() instanceof PASOperatingRopeBlock) {
                ropeCount++;
                checkPos = checkPos.above();
                continue;
            }

            return null;
        }

        return null;
    }

    /**
     * 上側にレバーまたは紐があり、
     * その先にPAS本体が存在する場合のみ維持する。
     */
    @Override
    public boolean canSurvive(
            BlockState state,
            LevelReader level,
            BlockPos pos
    ) {
        return findLeverAbove(level, pos) != null;
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
        if (!canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
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

    /**
     * 紐を右クリックするとPASを開閉する。
     */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockPos leverPos = findLeverAbove(level, pos);

        if (leverPos == null) {
            return InteractionResult.PASS;
        }

        BlockState leverState = level.getBlockState(leverPos);

        if (!(leverState.getBlock() instanceof PASOperatingLeverBlock)) {
            return InteractionResult.PASS;
        }

        BlockPos pasPos =
                leverPos.relative(
                        leverState.getValue(PASOperatingLeverBlock.FACING)
                );

        if (!(level.getBlockEntity(pasPos) instanceof PASBlockEntity pas)) {
            return InteractionResult.PASS;
        }

        pas.toggle();

        return InteractionResult.SUCCESS;
    }

    /**
     * 紐の上方向をたどり、接続されているレバーを探す。
     */
    private static BlockPos findLeverAbove(
            LevelReader level,
            BlockPos ropePos
    ) {
        BlockPos checkPos = ropePos.above();

        for (int i = 0; i <= MAX_ROPE_LENGTH; i++) {
            BlockState checkState = level.getBlockState(checkPos);

            if (checkState.getBlock() instanceof PASOperatingLeverBlock) {
                return checkPos;
            }

            if (checkState.getBlock() instanceof PASOperatingRopeBlock) {
                checkPos = checkPos.above();
                continue;
            }

            return null;
        }

        return null;
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
    protected MapCodec<? extends Block> codec() {
        return MapCodec.unit(this);
    }
}
