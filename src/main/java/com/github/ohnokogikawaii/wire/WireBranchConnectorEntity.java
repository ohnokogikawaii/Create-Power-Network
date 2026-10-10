package com.github.ohnokogikawaii.wire;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class WireBranchConnectorEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_PARENT_ID =
            SynchedEntityData.defineId(
                    WireBranchConnectorEntity.class,
                    EntityDataSerializers.INT
            );

    private static final EntityDataAccessor<Float> DATA_WIRE_T =
            SynchedEntityData.defineId(
                    WireBranchConnectorEntity.class,
                    EntityDataSerializers.FLOAT
            );

    private static final EntityDataAccessor<Integer> DATA_DIRECTION =
            SynchedEntityData.defineId(
                    WireBranchConnectorEntity.class,
                    EntityDataSerializers.INT
            );

    private static final EntityDataAccessor<Boolean> DATA_DOWN_TYPE =
            SynchedEntityData.defineId(
                    WireBranchConnectorEntity.class,
                    EntityDataSerializers.BOOLEAN
            );

    private static final double PORT_OFFSET = 0.45;
    private static final double CULL_RADIUS = 0.65;

    private UUID parentWireUuid;

    public WireBranchConnectorEntity(
            EntityType<? extends WireBranchConnectorEntity> type,
            Level level
    ) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public WireBranchConnectorEntity(
            EntityType<? extends WireBranchConnectorEntity> type,
            Level level,
            WireEntity parentWire,
            double wireT,
            boolean downType,
            int directionIndex
    ) {
        this(type, level);

        setParentWire(parentWire);
        setWireT(wireT);
        setDownType(downType);
        setDirectionIndex(directionIndex);
        updatePositionFromParent();
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder
    ) {
        builder.define(DATA_PARENT_ID, -1);
        builder.define(DATA_WIRE_T, 0.5F);
        builder.define(DATA_DIRECTION, 0);
        builder.define(DATA_DOWN_TYPE, true);
    }

    public void setParentWire(WireEntity wire) {
        if (wire == null) {
            parentWireUuid = null;
            entityData.set(DATA_PARENT_ID, -1);
            return;
        }

        parentWireUuid = wire.getUUID();
        entityData.set(DATA_PARENT_ID, wire.getId());
    }

    public UUID getParentWireUuid() {
        return parentWireUuid;
    }

    public int getParentWireId() {
        return entityData.get(DATA_PARENT_ID);
    }

    public void setWireT(double wireT) {
        entityData.set(
                DATA_WIRE_T,
                (float) Math.max(0.0, Math.min(1.0, wireT))
        );
    }

    public double getWireT() {
        return entityData.get(DATA_WIRE_T);
    }

    public void setDirectionIndex(int directionIndex) {
        entityData.set(
                DATA_DIRECTION,
                Math.floorMod(directionIndex, 8)
        );
    }

    public int getDirectionIndex() {
        return entityData.get(DATA_DIRECTION);
    }

    public void setDownType(boolean downType) {
        entityData.set(DATA_DOWN_TYPE, downType);
    }

    public boolean isDownType() {
        return entityData.get(DATA_DOWN_TYPE);
    }

    public WireEntity getParentWire() {
        if (level().isClientSide()) {
            Entity entity = level().getEntity(getParentWireId());

            return entity instanceof WireEntity wire ? wire : null;
        }

        if (!(level() instanceof ServerLevel serverLevel)
                || parentWireUuid == null) {
            return null;
        }

        Entity entity = serverLevel.getEntity(parentWireUuid);
        return entity instanceof WireEntity wire ? wire : null;
    }

    private boolean parentCanSag(WireEntity parent) {
        return parent.getWireType() != null
                && parent.getWireType().canSag();
    }

    public Vec3 getBranchPortWorldPosition() {
        WireEntity parent = getParentWire();

        if (parent == null) {
            return position();
        }

        Vec3 a = parent.getEndpointAWorldPosition();
        Vec3 b = parent.getEndpointBWorldPosition();
        boolean canSag = parentCanSag(parent);

        if (isDownType()) {
            return WireBranchGeometry.downwardPortPosition(
                    a, b, getWireT(), canSag, PORT_OFFSET
            );
        }

        return WireBranchGeometry.branchPortPosition(
                a, b, getWireT(), canSag,
                getDirectionIndex(), PORT_OFFSET
        );
    }

    private void updatePositionFromParent() {
        WireEntity parent = getParentWire();

        if (parent == null) {
            return;
        }

        Vec3 center = WireBranchGeometry.pointOnWire(
                parent.getEndpointAWorldPosition(),
                parent.getEndpointBWorldPosition(),
                getWireT(),
                parentCanSag(parent)
        );

        setPos(center.x, center.y, center.z);
    }

    @Override
    public void tick() {
        super.tick();

        // 親ワイヤーが一時的に未ロードでも、コネクタは削除しない。
        updatePositionFromParent();
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        // 分岐端子とアームも描画範囲に含める。
        return new AABB(
                getX() - CULL_RADIUS,
                getY() - CULL_RADIUS,
                getZ() - CULL_RADIUS,
                getX() + CULL_RADIUS,
                getY() + CULL_RADIUS,
                getZ() + CULL_RADIUS
        );
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        parentWireUuid = tag.hasUUID("ParentWire")
                ? tag.getUUID("ParentWire")
                : null;

        entityData.set(DATA_WIRE_T, tag.getFloat("WireT"));
        entityData.set(
                DATA_DIRECTION,
                Math.floorMod(tag.getInt("Direction"), 8)
        );
        entityData.set(
                DATA_DOWN_TYPE,
                !tag.contains("DownType") || tag.getBoolean("DownType")
        );

        // Entity ID は保存せず、ワールド内の UUID で親を特定する。
        entityData.set(DATA_PARENT_ID, -1);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (parentWireUuid != null) {
            tag.putUUID("ParentWire", parentWireUuid);
        }

        tag.putFloat("WireT", entityData.get(DATA_WIRE_T));
        tag.putInt("Direction", entityData.get(DATA_DIRECTION));
        tag.putBoolean("DownType", entityData.get(DATA_DOWN_TYPE));
    }
}
