
package com.github.ohnokogikawaii.insulator;

import com.github.ohnokogikawaii.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class InsulatorBlockEntity extends BlockEntity {

    public static final int MAX_SUPPORTED_WIRES = 2;

    private final List<UUID> supportedWireIds = new ArrayList<>();

    // 水平面における溝の角度（度）
    private float grooveYaw = 0.0F;

    private boolean hasGrooveDirection = false;

    public InsulatorBlockEntity(
            BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state
    ) {
        super(ModBlockEntities.INSULATOR.get(), pos, state);
    }

    public List<UUID> getSupportedWireIds() {
        return Collections.unmodifiableList(supportedWireIds);
    }

    public boolean isSupporting(UUID wireId) {
        return supportedWireIds.contains(wireId);
    }

    public boolean addSupportedWire(UUID wireId) {
        if (wireId == null
                || supportedWireIds.contains(wireId)
                || supportedWireIds.size() >= MAX_SUPPORTED_WIRES) {
            return false;
        }

        supportedWireIds.add(wireId);
        setChanged();
        return true;
    }

    public boolean removeSupportedWire(UUID wireId) {
        if (wireId == null) {
            return false;
        }

        boolean removed = supportedWireIds.remove(wireId);

        if (removed) {
            // 支持する電線がなくなった場合は溝方向をリセット
            if (supportedWireIds.isEmpty()) {
                grooveYaw = 0.0F;
                hasGrooveDirection = false;
            }

            syncBlockEntity();
        }

        return removed;
    }

    public void clearSupportedWires() {
        if (supportedWireIds.isEmpty()
                && !hasGrooveDirection) {
            return;
        }

        supportedWireIds.clear();

        grooveYaw = 0.0F;
        hasGrooveDirection = false;

        syncBlockEntity();
    }

    public int getSupportedWireCount() {
        return supportedWireIds.size();
    }

    public boolean canSupportMoreWires() {
        return supportedWireIds.size() < MAX_SUPPORTED_WIRES;
    }

    public float getGrooveYaw() {
        return grooveYaw;
    }

    public boolean hasGrooveDirection() {
        return hasGrooveDirection;
    }

    public void setGrooveYaw(float yaw) {
        this.grooveYaw = normalizeAxisAngle(yaw);
        this.hasGrooveDirection = true;
        syncBlockEntity();
    }

    public void clearGrooveDirection() {
        grooveYaw = 0.0F;
        hasGrooveDirection = false;
        syncBlockEntity();
    }

    private static float normalizeAxisAngle(float angle) {
        angle %= 180.0F;

        if (angle < 0.0F) {
            angle += 180.0F;
        }

        return angle;
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

        ListTag list = new ListTag();

        for (UUID wireId : supportedWireIds) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("WireUUID", wireId);
            list.add(entry);
        }

        tag.put("SupportedWires", list);
        tag.putFloat("GrooveYaw", grooveYaw);
        tag.putBoolean("HasGrooveDirection", hasGrooveDirection);
    }

    @Override
    protected void loadAdditional(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        super.loadAdditional(tag, registries);

        supportedWireIds.clear();

        if (tag.contains("SupportedWires", Tag.TAG_LIST)) {
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

        grooveYaw = normalizeAxisAngle(
                tag.getFloat("GrooveYaw")
        );

        hasGrooveDirection =
                tag.getBoolean("HasGrooveDirection");
    }

    @Override
    public CompoundTag getUpdateTag(
            HolderLookup.Provider registries
    ) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
