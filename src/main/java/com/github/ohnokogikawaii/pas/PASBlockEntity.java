package com.github.ohnokogikawaii.pas;

import com.github.ohnokogikawaii.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class PASBlockEntity extends BlockEntity {

    public PASBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PAS.get(), pos, state);
    }

    /**
     * PASが投入状態かどうか。
     *
     * 3極は一つの状態で管理するため、
     * R・S・T相が個別に開閉することはない。
     */
    public boolean isClosed() {
        return !getBlockState().getValue(PASBlock.OPEN);
    }

    /**
     * PASが開放状態かどうか。
     */
    public boolean isOpen() {
        return getBlockState().getValue(PASBlock.OPEN);
    }

    /**
     * PASの開閉操作を行う共通処理。
     *
     * 手動レバー、操作紐、SOGなどは、
     * 将来的にこのメソッドを呼び出す。
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

        setChanged();
    }

    public void setOpen(boolean open) {
        setClosed(!open);
    }

    public void toggle() {
        setClosed(!isClosed());
    }
}