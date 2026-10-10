
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

import java.util.Optional;
import java.util.UUID;

/**
 * 電線の途中に取り付ける分岐コネクタ。
 *
 * DOWN:
 *   端子 C を真下に配置する。
 *
 * ANGLED:
 *   電線の軸を基準に、端子 C の方向を8方向から選択する。
 *
 * この Entity 自体は電気回路を構築しない。
 * 親電線 A-B と端子 C の電気的な接続は、
 * 電気ネットワーク実装時に別途処理する。
 */
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
    private static final double PICK_RADIUS = 0.18;

    /**
     * 親電線の UUID。
     * Entity ID は再起動後に変わるため、保存用には UUID を使用する。
     */
    private UUID parentWireUuid;

    public WireBranchConnectorEntity(
            EntityType<? extends WireBranchConnectorEntity> entityType,
            Level level
    ) {
        super(entityType, level);

        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /**
     * 新規設置時のコンストラクタ。
     *
     * wireT:
     *   0.0 = 電線 A 側
     *   0.5 = 電線の中央
     *   1.0 = 電線 B 側
     *
     * directionIndex:
     *   斜め型で使用する方向番号 0～7
     */
    public WireBranchConnectorEntity(
            EntityType<? extends WireBranchConnectorEntity> entityType,
            Level level,
            WireEntity parentWire,
            double wireT,
            boolean downType,
            int directionIndex
    ) {
        this(entityType, level);

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

    /**
     * 親電線を設定する。
     */
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

    /**
     * 親電線を取得する。
     *
     * サーバー側では UUID、クライアント側では同期された
     * Entity ID を利用する。
     */
    public WireEntity getParentWire() {
        if (level().isClientSide()) {
            Entity entity = level().getEntity(getParentWireId());

            if (entity instanceof WireEntity wire) {
                return wire;
            }

            return null;
        }

        if (!(level() instanceof ServerLevel serverLevel)) {
            return null;
        }

        if (parentWireUuid == null) {
            return null;
        }

        Entity entity = serverLevel.getEntity(parentWireUuid);

        if (entity instanceof WireEntity wire) {
            return wire;
        }

        return null;
    }

    /**
     * 端子 C のワールド座標を取得する。
     */
    public Vec3 getBranchPortWorldPosition() {
        WireEntity parent = getParentWire();

        if (parent == null) {
            return position();
        }

        Vec3 a = parent.getEndpointAWorldPosition();
        Vec3 b = parent.getEndpointBWorldPosition();

        if (isDownType()) {
            return WireBranchGeometry.downwardPortPosition(
                    a,
                    b,
                    getWireT(),
                    PORT_OFFSET
            );
        }

        return WireBranchGeometry.branchPortPosition(
                a,
                b,
                getWireT(),
                getDirectionIndex(),
                PORT_OFFSET
        );
    }

    /**
     * コネクタ本体の位置を親電線に追従させる。
     */
    private void updatePositionFromParent() {
        WireEntity parent = getParentWire();

        if (parent == null) {
            return;
        }

        Vec3 a = parent.getEndpointAWorldPosition();
        Vec3 b = parent.getEndpointBWorldPosition();

        Vec3 center = WireBranchGeometry.pointOnWire(
                a,
                b,
                getWireT()
        );

        setPos(center.x, center.y, center.z);
    }

    @Override
    public void tick() {
        super.tick();

        WireEntity parent = getParentWire();

        /*
         * 親電線が見つからない場合、ここでは削除しない。
         * チャンクの読み込み順などで一時的に取得できない場合がある。
         */
        if (parent == null) {
            return;
        }

        updatePositionFromParent();
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        double r = PICK_RADIUS;

        return new AABB(
                getX() - r,
                getY() - r,
                getZ() - r,
                getX() + r,
                getY() + r,
                getZ() + r
        );
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("ParentWire")) {
            parentWireUuid = tag.getUUID("ParentWire");
        } else {
            parentWireUuid = null;
        }

        entityData.set(
                DATA_WIRE_T,
                tag.getFloat("WireT")
        );

        entityData.set(
                DATA_DIRECTION,
                Math.floorMod(tag.getInt("Direction"), 8)
        );

        entityData.set(
                DATA_DOWN_TYPE,
                !tag.contains("DownType")
                        || tag.getBoolean("DownType")
        );

        /*
         * Entity ID は保存しない。
         * ワールド読み込み後、親電線を UUID から再取得して更新する。
         */
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