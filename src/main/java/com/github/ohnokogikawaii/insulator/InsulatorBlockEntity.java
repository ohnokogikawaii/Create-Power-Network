
package com.github.ohnokogikawaii.insulator;

import com.github.ohnokogikawaii.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 固定式碍子の支持電線を管理する BlockEntity。
 *
 * 1つの碍子が支持できる電線は最大2本。
 * 電線のUUIDを保存することで、チャンク再読み込み後も
 * 支持関係を復元できるようにする。
 */
public class InsulatorBlockEntity extends BlockEntity {

    public static final int MAX_SUPPORTED_WIRES = 2;

    private final List<UUID> supportedWireIds = new ArrayList<>();

    public InsulatorBlockEntity(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        super(ModBlockEntities.INSULATOR.get(), pos, state);
    }

    /**
     * 現在支持している電線UUIDの一覧を返す。
     */
    public List<UUID> getSupportedWireIds() {
        return Collections.unmodifiableList(supportedWireIds);
    }

    /**
     * 指定UUIDの電線が既に登録されているか確認する。
     */
    public boolean isSupporting(UUID wireId) {
        return supportedWireIds.contains(wireId);
    }

    /**
     * 新しい電線を支持対象として登録する。
     *
     * @return 登録できた場合 true
     */
    public boolean addSupportedWire(UUID wireId) {
        if (wireId == null) {
            return false;
        }

        if (supportedWireIds.contains(wireId)) {
            return false;
        }

        if (supportedWireIds.size() >= MAX_SUPPORTED_WIRES) {
            return false;
        }

        supportedWireIds.add(wireId);
        setChanged();

        return true;
    }

    /**
     * 支持している電線を解除する。
     *
     * @return 登録が解除された場合 true
     */
    public boolean removeSupportedWire(UUID wireId) {
        if (wireId == null) {
            return false;
        }

        boolean removed = supportedWireIds.remove(wireId);

        if (removed) {
            setChanged();
        }

        return removed;
    }

    /**
     * 支持関係をすべて解除する。
     */
    public void clearSupportedWires() {
        if (supportedWireIds.isEmpty()) {
            return;
        }

        supportedWireIds.clear();
        setChanged();
    }

    /**
     * 現在の支持電線数。
     */
    public int getSupportedWireCount() {
        return supportedWireIds.size();
    }

    /**
     * 追加登録が可能か確認する。
     */
    public boolean canSupportMoreWires() {
        return supportedWireIds.size() < MAX_SUPPORTED_WIRES;
    }

    @Override
    protected void saveAdditional(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        super.saveAdditional(tag, registries);

        ListTag list = new ListTag();

        for (UUID wireId : supportedWireIds) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("WireUUID", wireId);
            list.add(entry);
        }

        tag.put("SupportedWires", list);
    }

    @Override
    protected void loadAdditional(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        super.loadAdditional(tag, registries);

        supportedWireIds.clear();

        if (!tag.contains("SupportedWires", Tag.TAG_LIST)) {
            return;
        }

        ListTag list = tag.getList(
                "SupportedWires",
                Tag.TAG_COMPOUND
        );

        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);

            if (!entry.hasUUID("WireUUID")) {
                continue;
            }

            UUID wireId = entry.getUUID("WireUUID");

            if (!supportedWireIds.contains(wireId)
                    && supportedWireIds.size() < MAX_SUPPORTED_WIRES) {
                supportedWireIds.add(wireId);
            }
        }
    }
}
