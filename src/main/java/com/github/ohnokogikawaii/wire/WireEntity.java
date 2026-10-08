package com.github.ohnokogikawaii.wire;

import com.github.ohnokogikawaii.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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

    /**
     * EntityTypeから生成されるコンストラクター。
     * EntityType.Builder.of(WireEntity::new, ...) から使用される。
     */
    public WireEntity(EntityType<? extends WireEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
    }

    /**
     * ワイヤーを新規作成するときに使用するコンストラクター。
     */
    public WireEntity(Level level, BlockPos endpointA, BlockPos endpointB) {
        this(ModEntities.WIRE.get(), level);

        setEndpoints(endpointA, endpointB);
        updatePosition();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ENDPOINT_A, BlockPos.ZERO);
        builder.define(ENDPOINT_B, BlockPos.ZERO);
    }

    public BlockPos getEndpointA() {
        return entityData.get(ENDPOINT_A);
    }

    public BlockPos getEndpointB() {
        return entityData.get(ENDPOINT_B);
    }

    public void setEndpoints(BlockPos endpointA, BlockPos endpointB) {
        entityData.set(ENDPOINT_A, endpointA.immutable());
        entityData.set(ENDPOINT_B, endpointB.immutable());
    }

    public Vec3 getEndpointWorldPosition(BlockPos endpoint) {
        return Vec3.atCenterOf(endpoint);
    }

    public Vec3 getEndpointAWorldPosition() {
        return getEndpointWorldPosition(getEndpointA());
    }

    public Vec3 getEndpointBWorldPosition() {
        return getEndpointWorldPosition(getEndpointB());
    }

    public Vec3 getEndpointAPosition() {
        return getEndpointAWorldPosition();
    }

    public Vec3 getEndpointBPosition() {
        return getEndpointBWorldPosition();
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
            if (!(level().getBlockState(getEndpointA()).getBlock()
                    instanceof TerminalBlock)
                    || !(level().getBlockState(getEndpointB()).getBlock()
                    instanceof TerminalBlock)) {

                discard();
                return;
            }
        }

        updatePosition();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        BlockPos a = getEndpointA();
        BlockPos b = getEndpointB();

        tag.putLong("EndpointA", a.asLong());
        tag.putLong("EndpointB", b.asLong());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        BlockPos a = BlockPos.of(tag.getLong("EndpointA"));
        BlockPos b = BlockPos.of(tag.getLong("EndpointB"));

        setEndpoints(a, b);
        updatePosition();
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        Vec3 a = getEndpointAWorldPosition();
        Vec3 b = getEndpointBWorldPosition();

        return new AABB(a, b).inflate(0.25);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double size = getBoundingBoxForCulling().getSize();
        size = Math.max(size, 1.0);

        return distance < size * size * 64.0;
    }
}