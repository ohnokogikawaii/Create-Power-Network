
package com.github.ohnokogikawaii.pas;

import com.github.ohnokogikawaii.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SOGBlockEntity extends BlockEntity {

    private static final String LINKED_PAS_TAG = "LinkedPAS";
    private static final String CONTROL_MODE_TAG = "ControlMode";

    private static final String SIGNAL_POWER_TAG =
            "SignalControlPower";

    private static final String SIGNAL_ZCT_TAG =
            "SignalZCT";

    private static final String SIGNAL_ZPD_TAG =
            "SignalZPD";

    private static final String SIGNAL_OVERCURRENT_TAG =
            "SignalOvercurrent";

    private static final String SIGNAL_PAS_OPEN_TAG =
            "SignalPASOpen";

    private BlockPos linkedPasPos;

    public enum ControlMode {
        DIRECT,
        CONTROL_CABLE
    }

    private ControlMode controlMode = ControlMode.DIRECT;

    private PASControlSignals latestSignals =
            PASControlSignals.NONE;

    public SOGBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOG.get(), pos, state);
    }

    public BlockPos getLinkedPasPos() {
        return linkedPasPos;
    }

    public ControlMode getControlMode() {
        return controlMode;
    }

    public PASControlSignals getLatestSignals() {
        return latestSignals;
    }

    public void setControlMode(ControlMode mode) {
        if (!isServerSide() || mode == null) {
            return;
        }

        if (controlMode == mode) {
            return;
        }

        controlMode = mode;

        // 制御線モードへ切り替えた場合、
        // 直接接続で受信していた古い信号を破棄する。
        if (mode == ControlMode.CONTROL_CABLE) {
            latestSignals = PASControlSignals.NONE;
        } else {
            refreshControlSignals();
        }

        setChanged();
    }

    private boolean isServerSide() {
        return level != null && !level.isClientSide;
    }

    /**
     * SOGとPASを相互に紐付ける。
     * 双方が別の機器に接続済みの場合は拒否する。
     */
    public boolean linkToPas(PASBlockEntity pas) {
        if (!isServerSide() || pas == null) {
            return false;
        }

        if (pas.getLevel() != level) {
            return false;
        }

        BlockPos newPasPos = pas.getBlockPos();

        if (newPasPos.equals(linkedPasPos)
                && worldPosition.equals(pas.getLinkedSogPos())) {
            pas.publishControlSignals();
            return true;
        }

        if (linkedPasPos != null
                && !linkedPasPos.equals(newPasPos)) {
            return false;
        }

        BlockPos existingSogPos = pas.getLinkedSogPos();

        if (existingSogPos != null
                && !existingSogPos.equals(worldPosition)) {
            return false;
        }

        linkedPasPos = newPasPos;
        pas.setLinkedSogPos(worldPosition);

        setChanged();
        pas.setChanged();

        pas.publishControlSignals();

        return true;
    }

    public void clearLinkedPasIfMatches(BlockPos pasPos) {
        if (linkedPasPos == null
                || !linkedPasPos.equals(pasPos)) {
            return;
        }

        linkedPasPos = null;
        latestSignals = PASControlSignals.NONE;
        setChanged();
    }

    /**
     * PASから制御情報を受信する。
     */
    public void receiveControlSignals(PASControlSignals signals) {
        if (!isServerSide() || signals == null) {
            return;
        }

        latestSignals = signals;
        setChanged();
    }

    /**
     * PASの現在の制御情報を再取得する。
     */
    /**
     * DIRECT接続時にPASの現在の制御情報を再取得する。
     *
     * CONTROL_CABLEの場合は専用制御線側から受信する。
     */
    public boolean refreshControlSignals() {
        if (!isServerSide()
                || linkedPasPos == null
                || controlMode != ControlMode.DIRECT) {
            return false;
        }

        if (!level.hasChunkAt(linkedPasPos)) {
            return false;
        }

        BlockEntity entity = level.getBlockEntity(linkedPasPos);

        if (!(entity instanceof PASBlockEntity pas)) {
            return false;
        }

        if (!worldPosition.equals(pas.getLinkedSogPos())) {
            return false;
        }

        latestSignals = pas.getControlSignals();
        setChanged();

        return true;
    }

    /**
     * SOGからPASへ開放指令を送る共通入口。
     *
     * 現段階ではDIRECTのみ動作する。
     * CONTROL_CABLEは専用制御線の実装後に有効化する。
     */
    public boolean requestTrip() {
        if (!isServerSide() || linkedPasPos == null) {
            return false;
        }

        if (controlMode != ControlMode.DIRECT) {
            return false;
        }

        if (!level.hasChunkAt(linkedPasPos)) {
            return false;
        }

        BlockEntity entity = level.getBlockEntity(linkedPasPos);

        if (!(entity instanceof PASBlockEntity pas)) {
            return false;
        }

        if (!worldPosition.equals(pas.getLinkedSogPos())) {
            return false;
        }

        pas.setOpen(true);

        refreshControlSignals();

        return pas.isOpen();
    }

    @Override
    protected void saveAdditional(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        super.saveAdditional(tag, registries);

        if (linkedPasPos != null) {
            tag.putLong(
                    LINKED_PAS_TAG,
                    linkedPasPos.asLong()
            );
        }

        tag.putString(
                CONTROL_MODE_TAG,
                controlMode.name()
        );

        tag.putBoolean(
                SIGNAL_POWER_TAG,
                latestSignals.controlPowerAvailable()
        );

        tag.putBoolean(
                SIGNAL_ZCT_TAG,
                latestSignals.zeroSequenceCurrentDetected()
        );

        tag.putBoolean(
                SIGNAL_ZPD_TAG,
                latestSignals.zeroSequenceVoltageDetected()
        );

        tag.putBoolean(
                SIGNAL_OVERCURRENT_TAG,
                latestSignals.overcurrentDetected()
        );

        tag.putBoolean(
                SIGNAL_PAS_OPEN_TAG,
                latestSignals.pasOpen()
        );
    }

    @Override
    protected void loadAdditional(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        super.loadAdditional(tag, registries);

        linkedPasPos = tag.contains(LINKED_PAS_TAG)
                ? BlockPos.of(tag.getLong(LINKED_PAS_TAG))
                : null;

        String modeName = tag.getString(CONTROL_MODE_TAG);

        try {
            controlMode = ControlMode.valueOf(modeName);
        } catch (IllegalArgumentException exception) {
            controlMode = ControlMode.DIRECT;
        }

        latestSignals = new PASControlSignals(
                tag.getBoolean(SIGNAL_POWER_TAG),
                tag.getBoolean(SIGNAL_ZCT_TAG),
                tag.getBoolean(SIGNAL_ZPD_TAG),
                tag.getBoolean(SIGNAL_OVERCURRENT_TAG),
                tag.getBoolean(SIGNAL_PAS_OPEN_TAG)
        );
    }
}
