
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

    private BlockPos linkedPasPos;

    public enum ControlMode {
        DIRECT,
        CONTROL_CABLE
    }

    private ControlMode controlMode = ControlMode.DIRECT;

    public SOGBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOG.get(), pos, state);
    }

    public BlockPos getLinkedPasPos() {
        return linkedPasPos;
    }

    public ControlMode getControlMode() {
        return controlMode;
    }

    public void setControlMode(ControlMode mode) {
        if (level == null || level.isClientSide || mode == null) {
            return;
        }

        controlMode = mode;
        setChanged();
    }

    /**
     * SOGとPASを相互に紐付ける。
     * 既存の接続先が読み込まれていない場合は変更しない。
     */

    public boolean linkToPas(PASBlockEntity pas) {
        if (level == null || level.isClientSide || pas == null) {
            return false;
        }

        if (pas.getLevel() != level) {
            return false;
        }

        BlockPos newPasPos = pas.getBlockPos();

        // 同じPASへの再接続は許可する。
        if (newPasPos.equals(linkedPasPos)
                && worldPosition.equals(pas.getLinkedSogPos())) {
            return true;
        }

        // SOGが既に別のPASに接続されている場合は拒否。
        if (linkedPasPos != null
                && !linkedPasPos.equals(newPasPos)) {
            return false;
        }

        // PASが既に別のSOGに接続されている場合は拒否。
        BlockPos existingSogPos = pas.getLinkedSogPos();

        if (existingSogPos != null
                && !existingSogPos.equals(worldPosition)) {
            return false;
        }

        // 双方の接続先を確認できた場合だけ登録する。
        linkedPasPos = newPasPos;
        pas.setLinkedSogPos(worldPosition);

        setChanged();
        pas.setChanged();

        return true;
    }


    public void clearLinkedPasIfMatches(BlockPos pasPos) {
        if (linkedPasPos == null || !linkedPasPos.equals(pasPos)) {
            return;
        }

        linkedPasPos = null;
        setChanged();
    }

    /**
     * SOGからPASへ開放指令を送る共通入口。
     *
     * DIRECT:
     *   現段階で使用する直接制御。
     *
     * CONTROL_CABLE:
     *   将来の制御線ネットワーク実装用。
     *   現段階では未実装なので動作させない。
     */
    public boolean requestTrip() {
        if (level == null || level.isClientSide || linkedPasPos == null) {
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

        pas.setOpen(true);
        return true;
    }

    @Override
    protected void saveAdditional(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        super.saveAdditional(tag, registries);

        if (linkedPasPos != null) {
            tag.putLong(LINKED_PAS_TAG, linkedPasPos.asLong());
        }

        tag.putString(CONTROL_MODE_TAG, controlMode.name());
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
    }
}
