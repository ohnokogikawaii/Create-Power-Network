package com.github.ohnokogikawaii.wire;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
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

    /**
     * ワイヤー中心から分岐接続点までのオフセット。
     */
    private static final double PORT_OFFSET = 0.45;

    /**
     * 分岐コネクタの描画カリング範囲。
     */
    private static final double CULL_RADIUS = 0.65;

    /**
     * 親ワイヤーの UUID。
     *
     * Entity ID はワールドの再読み込みで変化するため、
     * 永続保存には UUID を使用する。
     */
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

    /**
     * 親ワイヤーを設定する。
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

    /**
     * 親ワイヤー上の位置を設定する。
     *
     * 0.0 = 端点 A
     * 0.5 = 中央付近
     * 1.0 = 端点 B
     */
    public void setWireT(double wireT) {
        entityData.set(
                DATA_WIRE_T,
                (float) Math.max(0.0, Math.min(1.0, wireT))
        );
    }

    public double getWireT() {
        return entityData.get(DATA_WIRE_T);
    }

    /**
     * 分岐方向を 0～7 の範囲に正規化する。
     */
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
     * 親ワイヤーを取得する。
     *
     * クライアント側では Entity ID、
     * サーバー側では保存された UUID を使用する。
     *
     * 親ワイヤーがまだ読み込まれていない場合は null を返す。
     */
    public WireEntity getParentWire() {
        if (level().isClientSide()) {
            int parentId = getParentWireId();

            if (parentId < 0) {
                return null;
            }

            Entity entity = level().getEntity(parentId);

            return entity instanceof WireEntity wire
                    ? wire
                    : null;
        }

        if (!(level() instanceof ServerLevel serverLevel)
                || parentWireUuid == null) {
            return null;
        }

        Entity entity = serverLevel.getEntity(parentWireUuid);

        return entity instanceof WireEntity wire
                ? wire
                : null;
    }

    private boolean parentCanSag(WireEntity parent) {
        WireType wireType = parent.getWireType();

        return wireType != null && wireType.canSag();
    }

    /**
     * 親ワイヤーが取得できる場合のみ、
     * 分岐接続ポートのワールド座標を計算する。
     *
     * 親ワイヤーが未ロード、または取得できない場合は
     * null を返し、保存済みの接続座標を変更させない。
     */
    public Vec3 getResolvedBranchPortWorldPosition() {
        WireEntity parent = getParentWire();

        if (parent == null || !parent.isAlive()) {
            return null;
        }

        Vec3 endpointA = parent.getEndpointAWorldPosition();
        Vec3 endpointB = parent.getEndpointBWorldPosition();

        boolean canSag = parentCanSag(parent);

        if (isDownType()) {
            return WireBranchGeometry.downwardPortPosition(
                    endpointA,
                    endpointB,
                    getWireT(),
                    canSag,
                    PORT_OFFSET
            );
        }

        return WireBranchGeometry.branchPortPosition(
                endpointA,
                endpointB,
                getWireT(),
                canSag,
                getDirectionIndex(),
                PORT_OFFSET
        );
    }

    /**
     * 分岐接続ポートの座標を取得する。
     *
     * 親ワイヤーが解決できない場合は、
     * コネクタ自身の現在位置を返す。
     *
     * 描画や操作時の位置取得向け。
     * 接続先ワイヤーの追従更新には、
     * getResolvedBranchPortWorldPosition() を使用する。
     */
    public Vec3 getBranchPortWorldPosition() {
        Vec3 resolved = getResolvedBranchPortWorldPosition();

        return resolved != null ? resolved : position();
    }

    /**
     * 親ワイヤーの中心位置にコネクタを追従させる。
     *
     * 親ワイヤーが取得できない場合は、
     * 現在の位置を維持する。
     */
    /**
     * 親ワイヤーの位置にコネクタを追従させる。
     *
     * サーバー側で親ワイヤーを再取得できた場合は、
     * Entity ID も更新してクライアントへ同期する。
     */
    private void updatePositionFromParent() {
        WireEntity parent = getParentWire();

        if (parent == null || !parent.isAlive()) {
            return;
        }

        // UUID で親を再取得できたら、現在の Entity ID を同期する。
        if (!level().isClientSide()) {
            entityData.set(DATA_PARENT_ID, parent.getId());
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

        // 親ワイヤーが一時的に未ロードでも削除しない。
        // 親が取得できたときだけ位置を更新する。
        updatePositionFromParent();
    }

    @Override
    public AABB getBoundingBoxForCulling() {
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

        // Entity ID は保存データから復元しない。
        // クライアント側の ID は同期後に取得する。
        entityData.set(DATA_PARENT_ID, -1);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (parentWireUuid != null) {
            tag.putUUID("ParentWire", parentWireUuid);
        }

        tag.putFloat(
                "WireT",
                entityData.get(DATA_WIRE_T)
        );

        tag.putInt(
                "Direction",
                entityData.get(DATA_DIRECTION)
        );

        tag.putBoolean(
                "DownType",
                entityData.get(DATA_DOWN_TYPE)
        );
    }

    @Override
    public InteractionResult interact(
            Player player,
            InteractionHand hand
    ) {
        if (player.getItemInHand(hand).getItem() instanceof WireItem) {
            return WireItem.selectBranchEndpoint(
                    player,
                    hand,
                    this
            );
        }

        return super.interact(player, hand);
    }
}