
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

    private static final String CONTROL_POWER_TAG =
            "ControlPowerAvailable";

    private static final String ZCT_TAG =
            "ZeroSequenceCurrentDetected";

    private static final String ZPD_TAG =
            "ZeroSequenceVoltageDetected";

    private static final String OVERCURRENT_TAG =
            "OvercurrentDetected";

    private BlockPos linkedSogPos;

    /*
     * 制御電源は仮想的に利用可能な状態を初期値とする。
     * 実際のVT電源計算は後の段階で実装する。
     */
    private boolean controlPowerAvailable = true;

    private boolean zeroSequenceCurrentDetected;
    private boolean zeroSequenceVoltageDetected;
    private boolean overcurrentDetected;

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
        publishControlSignals();
    }

    public void clearLinkedSogIfMatches(BlockPos sogPos) {
        if (linkedSogPos == null
                || !linkedSogPos.equals(sogPos)) {
            return;
        }

        linkedSogPos = null;
        setChanged();
    }

    public PASControlSignals getControlSignals() {
        return new PASControlSignals(
                controlPowerAvailable,
                zeroSequenceCurrentDetected,
                zeroSequenceVoltageDetected,
                overcurrentDetected,
                isOpen()
        );
    }

    public void setControlPowerAvailable(boolean available) {
        if (!isServerSide()) {
            return;
        }

        if (controlPowerAvailable == available) {
            return;
        }

        controlPowerAvailable = available;
        publishControlSignals();
        setChanged();
    }

    public void setZeroSequenceCurrentDetected(boolean detected) {
        if (!isServerSide()) {
            return;
        }

        if (zeroSequenceCurrentDetected == detected) {
            return;
        }

        zeroSequenceCurrentDetected = detected;
        publishControlSignals();
        setChanged();
    }

    public void setZeroSequenceVoltageDetected(boolean detected) {
        if (!isServerSide()) {
            return;
        }

        if (zeroSequenceVoltageDetected == detected) {
            return;
        }

        zeroSequenceVoltageDetected = detected;
        publishControlSignals();
        setChanged();
    }

    public void setOvercurrentDetected(boolean detected) {
        if (!isServerSide()) {
            return;
        }

        if (overcurrentDetected == detected) {
            return;
        }

        overcurrentDetected = detected;
        publishControlSignals();
        setChanged();
    }

    private boolean isServerSide() {
        return level != null && !level.isClientSide;
    }

    /**
     * 接続されているSOGへPASの制御情報を渡す。
     */
    public void publishControlSignals() {
        if (!isServerSide() || linkedSogPos == null) {
            return;
        }

        if (!level.hasChunkAt(linkedSogPos)) {
            return;
        }

        BlockEntity entity = level.getBlockEntity(linkedSogPos);

        if (!(entity instanceof SOGBlockEntity sog)) {
            return;
        }

        if (!worldPosition.equals(sog.getLinkedPasPos())) {
            return;
        }

        sog.receiveControlSignals(getControlSignals());
    }

    public void setClosed(boolean closed) {
        if (!isServerSide()) {
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
        publishControlSignals();
    }

    private void syncOperatingLevers(boolean open) {
        if (!isServerSide()) {
            return;
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos leverPos =
                    worldPosition.relative(direction.getOpposite());

            BlockState leverState = level.getBlockState(leverPos);

            if (!(leverState.getBlock()
                    instanceof PASOperatingLeverBlock)) {
                continue;
            }

            if (leverState.getValue(
                    PASOperatingLeverBlock.FACING
            ) != direction) {
                continue;
            }

            if (leverState.getValue(
                    PASOperatingLeverBlock.OPEN
            ) == open) {
                continue;
            }

            level.setBlock(
                    leverPos,
                    leverState.setValue(
                            PASOperatingLeverBlock.OPEN,
                            open
                    ),
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
            tag.putLong(
                    LINKED_SOG_TAG,
                    linkedSogPos.asLong()
            );
        }

        tag.putBoolean(
                CONTROL_POWER_TAG,
                controlPowerAvailable
        );

        tag.putBoolean(
                ZCT_TAG,
                zeroSequenceCurrentDetected
        );

        tag.putBoolean(
                ZPD_TAG,
                zeroSequenceVoltageDetected
        );

        tag.putBoolean(
                OVERCURRENT_TAG,
                overcurrentDetected
        );
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

        controlPowerAvailable =
                !tag.contains(CONTROL_POWER_TAG)
                        || tag.getBoolean(CONTROL_POWER_TAG);

        zeroSequenceCurrentDetected =
                tag.getBoolean(ZCT_TAG);

        zeroSequenceVoltageDetected =
                tag.getBoolean(ZPD_TAG);

        overcurrentDetected =
                tag.getBoolean(OVERCURRENT_TAG);
    }
}
