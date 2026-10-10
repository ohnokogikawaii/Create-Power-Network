
package com.github.ohnokogikawaii.wire;

import com.github.ohnokogikawaii.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.UUID;

public class WireEntity extends Entity {

    private static final EntityDataAccessor<BlockPos> ENDPOINT_A =
            SynchedEntityData.defineId(WireEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<BlockPos> ENDPOINT_B =
            SynchedEntityData.defineId(WireEntity.class, EntityDataSerializers.BLOCK_POS);

    private static final EntityDataAccessor<Float> ENDPOINT_A_X =
            SynchedEntityData.defineId(WireEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ENDPOINT_A_Y =
            SynchedEntityData.defineId(WireEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ENDPOINT_A_Z =
            SynchedEntityData.defineId(WireEntity.class, EntityDataSerializers.FLOAT);

    private static final EntityDataAccessor<Float> ENDPOINT_B_X =
            SynchedEntityData.defineId(WireEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ENDPOINT_B_Y =
            SynchedEntityData.defineId(WireEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ENDPOINT_B_Z =
            SynchedEntityData.defineId(WireEntity.class, EntityDataSerializers.FLOAT);

    private static final EntityDataAccessor<Boolean> FREE_ENDPOINT_A =
            SynchedEntityData.defineId(WireEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FREE_ENDPOINT_B =
            SynchedEntityData.defineId(WireEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<String> WIRE_TYPE =
            SynchedEntityData.defineId(WireEntity.class, EntityDataSerializers.STRING);

    // クライアント側で追従対象を取得するための Entity ID。
    private static final EntityDataAccessor<Integer> BRANCH_A_ID =
            SynchedEntityData.defineId(WireEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BRANCH_B_ID =
            SynchedEntityData.defineId(WireEntity.class, EntityDataSerializers.INT);

    // 保存・再読み込みに使用する UUID。
    private UUID branchAUuid;
    private UUID branchBUuid;

    public WireEntity(EntityType<? extends WireEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    public WireEntity(
            Level level,
            BlockPos endpointA,
            BlockPos endpointB,
            ResourceLocation wireTypeId
    ) {
        this(ModEntities.WIRE.get(), level);
        setEndpoints(endpointA, endpointB);
        setWireTypeId(wireTypeId);
        updatePosition();
    }

    public WireEntity(
            Level level,
            Vec3 endpointA,
            Vec3 endpointB,
            BlockPos anchorA,
            BlockPos anchorB,
            boolean freeA,
            boolean freeB,
            ResourceLocation wireTypeId
    ) {
        this(ModEntities.WIRE.get(), level);

        entityData.set(ENDPOINT_A, anchorA.immutable());
        entityData.set(ENDPOINT_B, anchorB.immutable());

        setExactEndpointA(endpointA.toVector3f());
        setExactEndpointB(endpointB.toVector3f());

        entityData.set(FREE_ENDPOINT_A, freeA);
        entityData.set(FREE_ENDPOINT_B, freeB);

        setWireTypeId(wireTypeId);
        updatePosition();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ENDPOINT_A, BlockPos.ZERO);
        builder.define(ENDPOINT_B, BlockPos.ZERO);

        builder.define(ENDPOINT_A_X, 0.0F);
        builder.define(ENDPOINT_A_Y, 0.0F);
        builder.define(ENDPOINT_A_Z, 0.0F);

        builder.define(ENDPOINT_B_X, 0.0F);
        builder.define(ENDPOINT_B_Y, 0.0F);
        builder.define(ENDPOINT_B_Z, 0.0F);

        builder.define(FREE_ENDPOINT_A, false);
        builder.define(FREE_ENDPOINT_B, false);
        builder.define(WIRE_TYPE, "powernetwork:copper");

        builder.define(BRANCH_A_ID, -1);
        builder.define(BRANCH_B_ID, -1);
    }

    public BlockPos getEndpointA() {
        return entityData.get(ENDPOINT_A);
    }

    public BlockPos getEndpointB() {
        return entityData.get(ENDPOINT_B);
    }

    public boolean isEndpointAFree() {
        return entityData.get(FREE_ENDPOINT_A);
    }

    public boolean isEndpointBFree() {
        return entityData.get(FREE_ENDPOINT_B);
    }

    public void setEndpoints(BlockPos endpointA, BlockPos endpointB) {
        entityData.set(ENDPOINT_A, endpointA.immutable());
        entityData.set(ENDPOINT_B, endpointB.immutable());

        entityData.set(FREE_ENDPOINT_A, false);
        entityData.set(FREE_ENDPOINT_B, false);

        branchAUuid = null;
        branchBUuid = null;
        entityData.set(BRANCH_A_ID, -1);
        entityData.set(BRANCH_B_ID, -1);

        setExactEndpointA(Vec3.atCenterOf(endpointA).toVector3f());
        setExactEndpointB(Vec3.atCenterOf(endpointB).toVector3f());
    }

    public void setExactEndpointA(Vector3f position) {
        entityData.set(ENDPOINT_A_X, position.x());
        entityData.set(ENDPOINT_A_Y, position.y());
        entityData.set(ENDPOINT_A_Z, position.z());
    }

    public void setExactEndpointB(Vector3f position) {
        entityData.set(ENDPOINT_B_X, position.x());
        entityData.set(ENDPOINT_B_Y, position.y());
        entityData.set(ENDPOINT_B_Z, position.z());
    }

    public Vec3 getEndpointAWorldPosition() {
        return new Vec3(
                entityData.get(ENDPOINT_A_X),
                entityData.get(ENDPOINT_A_Y),
                entityData.get(ENDPOINT_A_Z)
        );
    }

    public Vec3 getEndpointBWorldPosition() {
        return new Vec3(
                entityData.get(ENDPOINT_B_X),
                entityData.get(ENDPOINT_B_Y),
                entityData.get(ENDPOINT_B_Z)
        );
    }

    public Vec3 getEndpointWorldPosition(BlockPos endpoint) {
        if (endpoint.equals(getEndpointA())) {
            return getEndpointAWorldPosition();
        }
        if (endpoint.equals(getEndpointB())) {
            return getEndpointBWorldPosition();
        }
        return Vec3.atCenterOf(endpoint);
    }

    public Vec3 getEndpointAPosition() {
        return getEndpointAWorldPosition();
    }

    public Vec3 getEndpointBPosition() {
        return getEndpointBWorldPosition();
    }

    public ResourceLocation getWireTypeId() {
        return ResourceLocation.parse(entityData.get(WIRE_TYPE));
    }

    public void setWireTypeId(ResourceLocation wireTypeId) {
        entityData.set(WIRE_TYPE, wireTypeId.toString());
    }

    public WireType getWireType() {
        return WireTypeManager.get(getWireTypeId());
    }

    /**
     * 端点 A を分岐コネクタの接続点 C に接続する。
     */
    public void attachEndpointAToBranch(WireBranchConnectorEntity branch) {
        branchAUuid = branch.getUUID();
        entityData.set(BRANCH_A_ID, branch.getId());
        entityData.set(FREE_ENDPOINT_A, true);
        setExactEndpointA(branch.getBranchPortWorldPosition().toVector3f());
    }

    /**
     * 端点 B を分岐コネクタの接続点 C に接続する。
     */
    public void attachEndpointBToBranch(WireBranchConnectorEntity branch) {
        branchBUuid = branch.getUUID();
        entityData.set(BRANCH_B_ID, branch.getId());
        entityData.set(FREE_ENDPOINT_B, true);
        setExactEndpointB(branch.getBranchPortWorldPosition().toVector3f());
    }

    public UUID getBranchAUuid() {
        return branchAUuid;
    }

    public UUID getBranchBUuid() {
        return branchBUuid;
    }

    private WireBranchConnectorEntity getBranch(boolean endpointA) {
        if (level().isClientSide) {
            int id = entityData.get(endpointA ? BRANCH_A_ID : BRANCH_B_ID);
            Entity entity = id < 0 ? null : level().getEntity(id);
            return entity instanceof WireBranchConnectorEntity branch ? branch : null;
        }

        UUID uuid = endpointA ? branchAUuid : branchBUuid;
        if (uuid == null || !(level() instanceof ServerLevel serverLevel)) {
            return null;
        }

        Entity entity = serverLevel.getEntity(uuid);
        return entity instanceof WireBranchConnectorEntity branch ? branch : null;
    }

    private boolean isValidAnchor(BlockPos pos) {
        // ブロックがまだロードされていない場合も false になるため、
        // チャンク未ロード判定は tick 側で先に行う。
        return level().getBlockState(pos).getBlock()
                instanceof WireConnectionPointProvider;
    }

    private void updateAttachedEndpoints() {
        WireBranchConnectorEntity branchA = getBranch(true);
        if (branchA != null && branchA.isAlive()) {
            setExactEndpointA(
                    branchA.getBranchPortWorldPosition().toVector3f()
            );
        }

        WireBranchConnectorEntity branchB = getBranch(false);
        if (branchB != null && branchB.isAlive()) {
            setExactEndpointB(
                    branchB.getBranchPortWorldPosition().toVector3f()
            );
        }
    }

    private void updatePosition() {
        Vec3 a = getEndpointAWorldPosition();
        Vec3 b = getEndpointBWorldPosition();
        Vec3 center = a.add(b).scale(0.5);
        setPos(center.x, center.y, center.z);
    }

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide) {
            // 接続先が分岐コネクタなら、ブロック端子として検証しない。
            if (branchAUuid == null && !isEndpointAFree()) {
                BlockPos pos = getEndpointA();
                if (level().hasChunkAt(pos) && !isValidAnchor(pos)) {
                    discard();
                    return;
                }
            }

            if (branchBUuid == null && !isEndpointBFree()) {
                BlockPos pos = getEndpointB();
                if (level().hasChunkAt(pos) && !isValidAnchor(pos)) {
                    discard();
                    return;
                }
            }
        }

        // 分岐コネクタが一時的に見つからなくても、
        // 最後に保存された座標は維持する。
        updateAttachedEndpoints();
        updatePosition();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("EndpointA", getEndpointA().asLong());
        tag.putLong("EndpointB", getEndpointB().asLong());

        Vec3 a = getEndpointAWorldPosition();
        Vec3 b = getEndpointBWorldPosition();

        tag.putDouble("EndpointAX", a.x);
        tag.putDouble("EndpointAY", a.y);
        tag.putDouble("EndpointAZ", a.z);

        tag.putDouble("EndpointBX", b.x);
        tag.putDouble("EndpointBY", b.y);
        tag.putDouble("EndpointBZ", b.z);

        tag.putBoolean("FreeEndpointA", isEndpointAFree());
        tag.putBoolean("FreeEndpointB", isEndpointBFree());
        tag.putString("WireType", getWireTypeId().toString());

        if (branchAUuid != null) {
            tag.putUUID("BranchA", branchAUuid);
        }
        if (branchBUuid != null) {
            tag.putUUID("BranchB", branchBUuid);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        BlockPos a = BlockPos.of(tag.getLong("EndpointA"));
        BlockPos b = BlockPos.of(tag.getLong("EndpointB"));

        entityData.set(ENDPOINT_A, a);
        entityData.set(ENDPOINT_B, b);

        if (tag.contains("EndpointAX")) {
            setExactEndpointA(new Vec3(
                    tag.getDouble("EndpointAX"),
                    tag.getDouble("EndpointAY"),
                    tag.getDouble("EndpointAZ")
            ).toVector3f());

            setExactEndpointB(new Vec3(
                    tag.getDouble("EndpointBX"),
                    tag.getDouble("EndpointBY"),
                    tag.getDouble("EndpointBZ")
            ).toVector3f());
        } else {
            setExactEndpointA(Vec3.atCenterOf(a).toVector3f());
            setExactEndpointB(Vec3.atCenterOf(b).toVector3f());
        }

        entityData.set(FREE_ENDPOINT_A, tag.getBoolean("FreeEndpointA"));
        entityData.set(FREE_ENDPOINT_B, tag.getBoolean("FreeEndpointB"));

        branchAUuid = tag.hasUUID("BranchA") ? tag.getUUID("BranchA") : null;
        branchBUuid = tag.hasUUID("BranchB") ? tag.getUUID("BranchB") : null;

        // Entity ID はセーブデータから復元せず、追従対象を再取得する。
        entityData.set(BRANCH_A_ID, -1);
        entityData.set(BRANCH_B_ID, -1);

        String wireType = tag.getString("WireType");
        if (!wireType.isBlank()) {
            setWireTypeId(ResourceLocation.parse(wireType));
        }

        updatePosition();
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return new AABB(
                getEndpointAWorldPosition(),
                getEndpointBWorldPosition()
        ).inflate(0.25);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double size = Math.max(getBoundingBoxForCulling().getSize(), 1.0);
        return distance < size * size * 64.0;
    }
}