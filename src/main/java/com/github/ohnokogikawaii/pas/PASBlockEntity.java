
package com.github.ohnokogikawaii.pas;

import com.github.ohnokogikawaii.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class PASBlockEntity extends BlockEntity {

    private static final String LINKED_SOG_TAG = "LinkedSOG";

    private BlockPos linkedSogPos;

    public PASBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PAS.get(), pos, state);
    }

    public boolean isClosed() {
        return !getBlockState().getValue(PASBlock.OPEN);
    }

    public boolean isOpen() {
        return getBlockState().getValue(PASBlock.OPEN);
    }

    public BlockPos getLinkedSogPos() {
        return linkedSogPos;
    }

    public void setLinkedSogPos(BlockPos pos) {
        linkedSogPos = pos;
        setChanged();
    }

    public void clearLinkedSogIfMatches(BlockPos sogPos) {
        if (linkedSogPos == null || !linkedSogPos.equals(sogPos)) {
            return;
        }

        linkedSogPos = null;
        setChanged();
    }

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

    @Override
    protected void saveAdditional(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        super.saveAdditional(tag, registries);

        if (linkedSogPos != null) {
            tag.putLong(LINKED_SOG_TAG, linkedSogPos.asLong());
        }
    }

    @Override
    protected void loadAdditional(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        super.loadAdditional(tag, registries);

        linkedSogPos = tag.contains(LINKED_SOG_TAG)
                ? BlockPos.of(tag.getLong(LINKED_SOG_TAG))
                : null;
    }
}
