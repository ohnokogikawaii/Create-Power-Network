package com.github.ohnokogikawaii.insulator;

import com.github.ohnokogikawaii.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class StrainInsulatorBlockEntity extends BlockEntity {

    public static final int MAX_RETAINED_WIRES = 1;
    public static final int MAX_JUMPER_WIRES = 3;

    private UUID retainedWireId;

    private final List<UUID> jumperWireIds = new ArrayList<>();

    public StrainInsulatorBlockEntity(
            BlockPos pos,
            BlockState state
    ) {
        super(ModBlockEntities.STRAIN_INSULATOR.get(), pos, state);
    }

    // 引き留め電線

    public UUID getRetainedWireId() {
        return retainedWireId;
    }

    public boolean canAddRetainedWire() {
        return retainedWireId == null;
    }

    public boolean addRetainedWire(UUID wireId) {
        if (wireId == null || !canAddRetainedWire()) {
            return false;
        }

        retainedWireId = wireId;
        syncBlockEntity();
        return true;
    }

    public boolean removeRetainedWire(UUID wireId) {
        if (wireId == null || !wireId.equals(retainedWireId)) {
            return false;
        }

        retainedWireId = null;
        syncBlockEntity();
        return true;
    }

    // ジャンパ線

    public List<UUID> getJumperWireIds() {
        return Collections.unmodifiableList(jumperWireIds);
    }

    public int getJumperWireCount() {
        return jumperWireIds.size();
    }

    public boolean canAddJumperWire() {
        return jumperWireIds.size() < MAX_JUMPER_WIRES;
    }

    public boolean addJumperWire(UUID wireId) {
        if (wireId == null
                || jumperWireIds.contains(wireId)
                || !canAddJumperWire()) {
            return false;
        }

        jumperWireIds.add(wireId);
        syncBlockEntity();
        return true;
    }

    public boolean removeJumperWire(UUID wireId) {
        if (wireId == null) {
            return false;
        }

        boolean removed = jumperWireIds.remove(wireId);

        if (removed) {
            syncBlockEntity();
        }

        return removed;
    }

    /**
     * この耐張碍子に接続されている全電線UUID。
     */
    public List<UUID> getAllConnectedWireIds() {
        List<UUID> result = new ArrayList<>();

        if (retainedWireId != null) {
            result.add(retainedWireId);
        }

        result.addAll(jumperWireIds);

        return Collections.unmodifiableList(result);
    }

    private void syncBlockEntity() {
        setChanged();

        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(
                    worldPosition,
                    getBlockState(),
                    getBlockState(),
                    3
            );
        }
    }

    @Override
    protected void saveAdditional(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        super.saveAdditional(tag, registries);

        if (retainedWireId != null) {
            tag.putUUID("RetainedWire", retainedWireId);
        }

        ListTag list = new ListTag();

        for (UUID wireId : jumperWireIds) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("WireUUID", wireId);
            list.add(entry);
        }

        tag.put("JumperWires", list);
    }

    @Override
    protected void loadAdditional(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        super.loadAdditional(tag, registries);

        retainedWireId = tag.hasUUID("RetainedWire")
                ? tag.getUUID("RetainedWire")
                : null;

        jumperWireIds.clear();

        if (tag.contains("JumperWires", Tag.TAG_LIST)) {
            ListTag list = tag.getList(
                    "JumperWires",
                    Tag.TAG_COMPOUND
            );

            for (int i = 0;
                 i < list.size() && jumperWireIds.size() < MAX_JUMPER_WIRES;
                 i++) {

                CompoundTag entry = list.getCompound(i);

                if (!entry.hasUUID("WireUUID")) {
                    continue;
                }

                UUID wireId = entry.getUUID("WireUUID");

                if (!jumperWireIds.contains(wireId)) {
                    jumperWireIds.add(wireId);
                }
            }
        }
    }
}