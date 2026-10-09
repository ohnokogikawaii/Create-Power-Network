package com.github.ohnokogikawaii.wire;

import com.github.ohnokogikawaii.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class WireEntity extends Entity {

    private static final EntityDataAccessor<BlockPos> ENDPOINT_A =
            SynchedEntityData.defineId(
                    WireEntity.class,
                    EntityDataSerializers.BLOCK_POS
            );

    private static final EntityDataAccessor<BlockPos> ENDPOINT_B =
            SynchedEntityData.defineId(
                    WireEntity.class,
                    EntityDataSerializers.BLOCK_POS
            );

    private static final EntityDataAccessor<Float> ENDPOINT_A_X =
            SynchedEntityData.defineId(
                    WireEntity.class,
                    EntityDataSerializers.FLOAT
            );

    private static final EntityDataAccessor<Float> ENDPOINT_A_Y =
            SynchedEntityData.defineId(
                    WireEntity.class,
                    EntityDataSerializers.FLOAT
            );

    private static final EntityDataAccessor<Float> ENDPOINT_A_Z =
            SynchedEntityData.defineId(
                    WireEntity.class,
                    EntityDataSerializers.FLOAT
            );

    private static final EntityDataAccessor<Float> ENDPOINT_B_X =
            SynchedEntityData.defineId(
                    WireEntity.class,
                    EntityDataSerializers.FLOAT
            );

    private static final EntityDataAccessor<Float> ENDPOINT_B_Y =
            SynchedEntityData.defineId(
                    WireEntity.class,
                    EntityDataSerializers.FLOAT
            );

    private static final EntityDataAccessor<Float> ENDPOINT_B_Z =
            SynchedEntityData.defineId(
                    WireEntity.class,
                    EntityDataSerializers.FLOAT
            );

    private static final EntityDataAccessor<Boolean> FREE_ENDPOINT_A =
            SynchedEntityData.defineId(
                    WireEntity.class,
                    EntityDataSerializers.BOOLEAN
            );

    private static final EntityDataAccessor<Boolean> FREE_ENDPOINT_B =
            SynchedEntityData.defineId(
                    WireEntity.class,
                    EntityDataSerializers.BOOLEAN
            );

    private static final EntityDataAccessor<String> WIRE_TYPE =
            SynchedEntityData.defineId(
                    WireEntity.class,
                    EntityDataSerializers.STRING
            );

    public WireEntity(
            EntityType<? extends WireEntity> entityType,
            Level level
    ) {
        super(entityType, level);
        this.noPhysics = true;
    }

    /**
     * 従来のブロック端子同士を接続するコンストラクタ。
     * 既存の WireItem との互換性を維持する。
     */
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

    /**
     * 正確なワールド座標を使うコンストラクタ。
     *
     * freeEndpoint が false の端点は通常のブロック端子として検証する。
     * true の端点は分岐コネクタなどの自由座標端点として扱う。
     */
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
    protected void defineSynchedData(
            SynchedEntityData.Builder builder
    ) {
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

    /**
     * 従来のブロック端子接続。
     * 端点はブロックの中心に設定する。
     */
    public void setEndpoints(
            BlockPos endpointA,
            BlockPos endpointB
    ) {
        entityData.set(ENDPOINT_A, endpointA.immutable());
        entityData.set(ENDPOINT_B, endpointB.immutable());

        entityData.set(FREE_ENDPOINT_A, false);
        entityData.set(FREE_ENDPOINT_B, false);

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

    /**
     * 既存コードとの互換性を維持する。
     */
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
        return ResourceLocation.parse(
                entityData.get(WIRE_TYPE)
        );
    }

    public void setWireTypeId(ResourceLocation wireTypeId) {
        entityData.set(
                WIRE_TYPE,
                wireTypeId.toString()
        );
    }

    public WireType getWireType() {
        return WireTypeManager.get(getWireTypeId());
    }

    private void updatePosition() {
        Vec3 a = getEndpointAWorldPosition();
        Vec3 b = getEndpointBWorldPosition();

        Vec3 center = a.add(b).scale(0.5);

        setPos(center.x, center.y, center.z);
    }

    private boolean isValidAnchor(BlockPos pos) {
        return level().getBlockState(pos).getBlock()
                instanceof TerminalBlock;
    }

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide) {
            if (!isEndpointAFree()
                    && !isValidAnchor(getEndpointA())) {
                discard();
                return;
            }

            if (!isEndpointBFree()
                    && !isValidAnchor(getEndpointB())) {
                discard();
                return;
            }
        }

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
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        BlockPos a = BlockPos.of(tag.getLong("EndpointA"));
        BlockPos b = BlockPos.of(tag.getLong("EndpointB"));

        entityData.set(ENDPOINT_A, a);
        entityData.set(ENDPOINT_B, b);

        // 古いセーブデータでは座標が保存されていないため、
        // 従来どおりブロック中心を使用する。
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

        entityData.set(
                FREE_ENDPOINT_A,
                tag.getBoolean("FreeEndpointA")
        );

        entityData.set(
                FREE_ENDPOINT_B,
                tag.getBoolean("FreeEndpointB")
        );

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
        double size = getBoundingBoxForCulling().getSize();
        size = Math.max(size, 1.0);

        return distance < size * size * 64.0;
    }
}

