
package com.github.ohnokogikawaii.pas;

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

public class PASControlCableEntity extends Entity {

    private static final EntityDataAccessor<BlockPos> PAS_POS =
            SynchedEntityData.defineId(
                    PASControlCableEntity.class,
                    EntityDataSerializers.BLOCK_POS
            );

    private static final EntityDataAccessor<BlockPos> SOG_POS =
            SynchedEntityData.defineId(
                    PASControlCableEntity.class,
                    EntityDataSerializers.BLOCK_POS
            );

    private static final String PAS_TAG = "PASPos";
    private static final String SOG_TAG = "SOGPos";

    public PASControlCableEntity(
            EntityType<? extends PASControlCableEntity> type,
            Level level
    ) {
        super(type, level);
        noPhysics = true;
    }

    public PASControlCableEntity(
            Level level,
            BlockPos pasPos,
            BlockPos sogPos
    ) {
        this(ModEntities.PAS_CONTROL_CABLE.get(), level);

        entityData.set(PAS_POS, pasPos.immutable());
        entityData.set(SOG_POS, sogPos.immutable());

        updatePosition();
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder
    ) {
        builder.define(PAS_POS, BlockPos.ZERO);
        builder.define(SOG_POS, BlockPos.ZERO);
    }

    public BlockPos getPASPos() {
        return entityData.get(PAS_POS);
    }

    public BlockPos getSOGPos() {
        return entityData.get(SOG_POS);
    }

    private Vec3 getPASCenter() {
        return Vec3.atCenterOf(getPASPos());
    }

    private Vec3 getSOGCenter() {
        return Vec3.atCenterOf(getSOGPos());
    }

    private void updatePosition() {
        Vec3 center = getPASCenter()
                .add(getSOGCenter())
                .scale(0.5);

        setPos(center.x, center.y, center.z);
    }

    @Override
    public void tick() {
        super.tick();

        updatePosition();

        if (level().isClientSide()) {
            return;
        }

        BlockPos pasPos = getPASPos();
        BlockPos sogPos = getSOGPos();

        /*
         * チャンクが未ロードの場合は判定を保留する。
         * アンロードだけを理由に制御線を削除しない。
         */
        if (!level().hasChunkAt(pasPos)
                || !level().hasChunkAt(sogPos)) {
            return;
        }

        if (!(level().getBlockEntity(pasPos)
                instanceof PASBlockEntity pas)) {
            disconnect();
            return;
        }

        if (!(level().getBlockEntity(sogPos)
                instanceof SOGBlockEntity sog)) {
            disconnect();
            return;
        }

        /*
         * PASとSOGの相互接続が一致しているか確認する。
         */
        if (!sogPos.equals(pas.getLinkedSogPos())
                || !pasPos.equals(sog.getLinkedPasPos())) {
            disconnect();
            return;
        }

        /*
         * 制御線モードの場合だけ信号を伝送する。
         */
        if (sog.getControlMode()
                == SOGBlockEntity.ControlMode.CONTROL_CABLE) {

            sog.receiveControlSignalsFromCable(
                    this,
                    pas.getControlSignals()
            );
        }
    }

    /**
     * SOGからの開放指令をPASへ伝える。
     */
    public boolean requestTripFromSog(SOGBlockEntity source) {
        if (level().isClientSide()
                || source == null
                || !isAlive()) {
            return false;
        }

        if (!getSOGPos().equals(source.getBlockPos())) {
            return false;
        }

        if (!level().hasChunkAt(getPASPos())) {
            return false;
        }

        if (!(level().getBlockEntity(getPASPos())
                instanceof PASBlockEntity pas)) {
            return false;
        }

        if (!getSOGPos().equals(pas.getLinkedSogPos())
                || !getPASPos().equals(source.getLinkedPasPos())) {
            return false;
        }

        if (source.getControlMode()
                != SOGBlockEntity.ControlMode.CONTROL_CABLE) {
            return false;
        }

        pas.setOpen(true);

        return pas.isOpen();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong(PAS_TAG, getPASPos().asLong());
        tag.putLong(SOG_TAG, getSOGPos().asLong());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(
                PAS_POS,
                BlockPos.of(tag.getLong(PAS_TAG))
        );

        entityData.set(
                SOG_POS,
                BlockPos.of(tag.getLong(SOG_TAG))
        );

        updatePosition();
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        Vec3 pas = getPASCenter();
        Vec3 sog = getSOGCenter();

        return new AABB(
                pas.x, pas.y, pas.z,
                sog.x, sog.y, sog.z
        ).inflate(1.0);
    }

    /**
     * 制御ケーブルを切断し、PASとSOGのリンクを解除する。
     */
    public void disconnect() {
        if (level().isClientSide()) {
            return;
        }

        BlockPos pasPos = getPASPos();
        BlockPos sogPos = getSOGPos();

        // PAS側のリンクを解除
        if (level().hasChunkAt(pasPos)) {
            if (level().getBlockEntity(pasPos)
                    instanceof PASBlockEntity pas) {
                pas.clearLinkedSogIfMatches(sogPos);
            }
        }

        // SOG側のリンクと受信信号を解除
        if (level().hasChunkAt(sogPos)) {
            if (level().getBlockEntity(sogPos)
                    instanceof SOGBlockEntity sog) {
                if (pasPos.equals(sog.getLinkedPasPos())) {
                    sog.clearLinkedPasIfMatches(pasPos);
                    sog.setControlMode(
                            SOGBlockEntity.ControlMode.DIRECT
                    );
                }
            }
        }

        discard();
    }
}
