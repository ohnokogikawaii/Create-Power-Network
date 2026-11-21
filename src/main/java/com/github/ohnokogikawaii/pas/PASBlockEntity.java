
package com.github.ohnokogikawaii.pas;

import com.github.ohnokogikawaii.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class PASBlockEntity extends BlockEntity {

    public PASBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PAS.get(), pos, state);
    }

    public boolean isClosed() {
        return !getBlockState().getValue(PASBlock.OPEN);
    }

    public boolean isOpen() {
        return getBlockState().getValue(PASBlock.OPEN);
    }

    /**
     * PASの共通開閉処理。
     * 手動レバー・操作紐・SOGから共通して呼び出す。
     */
    public void setClosed(boolean closed) {
        if (level == null || level.isClientSide) {
            return;
        }

        BlockState currentState = level.getBlockState(worldPosition);

        if (!(currentState.getBlock() instanceof PASBlock)) {
            return;
        }

        boolean shouldBeOpen = !closed;

        if (currentState.getValue(PASBlock.OPEN) == shouldBeOpen) {
            return;
        }

        level.setBlock(
                worldPosition,
                currentState.setValue(PASBlock.OPEN, shouldBeOpen),
                3
        );

        syncOperatingLevers(shouldBeOpen);
        setChanged();
    }

    /**
     * PAS本体に取り付けられた操作レバーの状態を同期する。
     */
    private void syncOperatingLevers(boolean open) {
        if (level == null || level.isClientSide) {
            return;
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos leverPos =
                    worldPosition.relative(direction.getOpposite());

            BlockState leverState = level.getBlockState(leverPos);

            if (!(leverState.getBlock() instanceof PASOperatingLeverBlock)) {
                continue;
            }

            if (leverState.getValue(PASOperatingLeverBlock.FACING) != direction) {
                continue;
            }

            if (leverState.getValue(PASOperatingLeverBlock.OPEN) == open) {
                continue;
            }

            level.setBlock(
                    leverPos,
                    leverState.setValue(PASOperatingLeverBlock.OPEN, open),
                    3
            );
        }
    }

    public void setOpen(boolean open) {
        setClosed(!open);
    }

    public void toggle() {
        setClosed(!isClosed());
    }
}
