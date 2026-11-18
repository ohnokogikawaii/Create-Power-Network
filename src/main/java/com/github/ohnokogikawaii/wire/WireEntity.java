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
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import com.github.ohnokogikawaii.insulator.InsulatorBlock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import java.util.UUID;

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

    /**
     * クライアント側で分岐コネクタを取得するための Entity ID。
     */
    private static final EntityDataAccessor<Integer> BRANCH_A_ID =
            SynchedEntityData.defineId(
                    WireEntity.class,
                    EntityDataSerializers.INT
            );

    private static final EntityDataAccessor<Integer> BRANCH_B_ID =
            SynchedEntityData.defineId(
                    WireEntity.class,
                    EntityDataSerializers.INT
            );

    /**
     * セーブ・再読み込み用の分岐コネクタ UUID。
     */
    private UUID branchAUuid;
    private UUID branchBUuid;
    /**
     * 固定式碍子による電線の支持点。
     *
     * insulatorPos:
     *   支持している碍子のブロック座標。
     *
     * position:
     *   電線が碍子を通過する正確なワールド座標。
     */
    public record SupportPoint(
            BlockPos insulatorPos,
            Vec3 position
    ) {}

    private static final int MAX_SUPPORT_POINTS = 64;

    private static final EntityDataAccessor<String> SUPPORT_POINTS =
            SynchedEntityData.defineId(
                    WireEntity.class,
                    EntityDataSerializers.STRING
            );

    /**
     * A/B端点が碍子上の端末処理になっているか。
     */
    private static final EntityDataAccessor<Boolean> TERMINAL_A =
            SynchedEntityData.defineId(
                    WireEntity.class,
                    EntityDataSerializers.BOOLEAN
            );

    private static final EntityDataAccessor<Boolean> TERMINAL_B =
            SynchedEntityData.defineId(
                    WireEntity.class,
                    EntityDataSerializers.BOOLEAN
            );

    /**
     * 端末から再接続できる状態か。
     * 現段階では端末の保存・同期に使用し、再接続処理は次段階で実装する。
     */
    private static final EntityDataAccessor<Boolean> RECONNECTABLE_A =
            SynchedEntityData.defineId(
                    WireEntity.class,
                    EntityDataSerializers.BOOLEAN
            );

    private static final EntityDataAccessor<Boolean> RECONNECTABLE_B =
            SynchedEntityData.defineId(
                    WireEntity.class,
                    EntityDataSerializers.BOOLEAN
            );

    public WireEntity(
            EntityType<? extends WireEntity> entityType,
            Level level
    ) {
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

        builder.define(BRANCH_A_ID, -1);
        builder.define(BRANCH_B_ID, -1);
        builder.define(SUPPORT_POINTS, "");
        builder.define(TERMINAL_A, false);
        builder.define(TERMINAL_B, false);

        builder.define(RECONNECTABLE_A, false);
        builder.define(RECONNECTABLE_B, false);
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
     * ブロック端子に接続するワイヤーの端点を設定する。
     */
    public void setEndpoints(
            BlockPos endpointA,
            BlockPos endpointB
    ) {
        entityData.set(ENDPOINT_A, endpointA.immutable());
        entityData.set(ENDPOINT_B, endpointB.immutable());

        entityData.set(FREE_ENDPOINT_A, false);
        entityData.set(FREE_ENDPOINT_B, false);

        branchAUuid = null;
        branchBUuid = null;

        entityData.set(BRANCH_A_ID, -1);
        entityData.set(BRANCH_B_ID, -1);

        setExactEndpointA(
                Vec3.atCenterOf(endpointA).toVector3f()
        );

        setExactEndpointB(
                Vec3.atCenterOf(endpointB).toVector3f()
        );
    }

    /**
     * 端点 A の正確なワールド座標を設定する。
     */
    public void setExactEndpointA(Vector3f position) {
        entityData.set(ENDPOINT_A_X, position.x());
        entityData.set(ENDPOINT_A_Y, position.y());
        entityData.set(ENDPOINT_A_Z, position.z());
    }

    /**
     * 端点 B の正確なワールド座標を設定する。
     */
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

    public boolean isTerminalA() {
        return entityData.get(TERMINAL_A);
    }

    public boolean isTerminalB() {
        return entityData.get(TERMINAL_B);
    }

    public boolean isReconnectableA() {
        return entityData.get(RECONNECTABLE_A);
    }

    public boolean isReconnectableB() {
        return entityData.get(RECONNECTABLE_B);
    }

    public void setTerminalA(boolean terminal) {
        entityData.set(TERMINAL_A, terminal);
        entityData.set(RECONNECTABLE_A, terminal);
    }

    public void setTerminalB(boolean terminal) {
        entityData.set(TERMINAL_B, terminal);
        entityData.set(RECONNECTABLE_B, terminal);
    }

    public void clearTerminalA() {
        entityData.set(TERMINAL_A, false);
        entityData.set(RECONNECTABLE_A, false);
    }

    public void clearTerminalB() {
        entityData.set(TERMINAL_B, false);
        entityData.set(RECONNECTABLE_B, false);
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

    /**
     * 端点 A を分岐コネクタに接続する。
     */
    public void attachEndpointAToBranch(
            WireBranchConnectorEntity branch
    ) {
        branchAUuid = branch.getUUID();

        entityData.set(BRANCH_A_ID, branch.getId());
        entityData.set(FREE_ENDPOINT_A, true);

        Vec3 port = branch.getResolvedBranchPortWorldPosition();

        // 接続時に親ワイヤーが未解決なら、
        // コネクタの現在位置を初期座標として使う。
        if (port == null) {
            port = branch.position();
        }

        setExactEndpointA(port.toVector3f());
    }

    /**
     * 端点 B を分岐コネクタに接続する。
     */
    public void attachEndpointBToBranch(
            WireBranchConnectorEntity branch
    ) {
        branchBUuid = branch.getUUID();

        entityData.set(BRANCH_B_ID, branch.getId());
        entityData.set(FREE_ENDPOINT_B, true);

        Vec3 port = branch.getResolvedBranchPortWorldPosition();

        if (port == null) {
            port = branch.position();
        }

        setExactEndpointB(port.toVector3f());
    }

    public UUID getBranchAUuid() {
        return branchAUuid;
    }

    public UUID getBranchBUuid() {
        return branchBUuid;
    }
    /**
     * 支持点一覧を取得する。
     */
    public List<SupportPoint> getSupportPoints() {
        String serialized = entityData.get(SUPPORT_POINTS);

        if (serialized.isBlank()) {
            return Collections.emptyList();
        }

        List<SupportPoint> result = new ArrayList<>();

        for (String entry : serialized.split(";")) {
            try {
                String[] values = entry.split(",");

                if (values.length != 6) {
                    continue;
                }

                BlockPos pos = new BlockPos(
                        Integer.parseInt(values[0]),
                        Integer.parseInt(values[1]),
                        Integer.parseInt(values[2])
                );

                Vec3 point = new Vec3(
                        Double.parseDouble(values[3]),
                        Double.parseDouble(values[4]),
                        Double.parseDouble(values[5])
                );

                result.add(new SupportPoint(pos, point));
            } catch (NumberFormatException ignored) {
                // 不正な同期データは読み飛ばす。
            }
        }

        return Collections.unmodifiableList(result);
    }

    /**
     * 固定式碍子の支持点を追加する。
     */
    public boolean addSupportPoint(BlockPos insulatorPos, Vec3 position) {
        if (insulatorPos == null || position == null) {
            return false;
        }

        List<SupportPoint> points = new ArrayList<>(getSupportPoints());

        for (SupportPoint point : points) {
            if (point.insulatorPos().equals(insulatorPos)) {
                return false;
            }
        }

        if (points.size() >= MAX_SUPPORT_POINTS) {
            return false;
        }

        points.add(new SupportPoint(insulatorPos.immutable(), position));

        syncSupportPoints(points);
        return true;
    }

    /**
     * 指定した碍子の支持点を解除する。
     */
    public boolean removeSupportPoint(BlockPos insulatorPos) {
        List<SupportPoint> points = new ArrayList<>(getSupportPoints());

        boolean removed = points.removeIf(
                point -> point.insulatorPos().equals(insulatorPos)
        );

        if (!removed) {
            return false;
        }

        syncSupportPoints(points);
        return true;
    }

    /**
     * 支持点一覧をクライアントへ同期する。
     */
    private void syncSupportPoints(List<SupportPoint> points) {
        StringBuilder builder = new StringBuilder();

        for (SupportPoint point : points) {
            if (!builder.isEmpty()) {
                builder.append(";");
            }

            BlockPos pos = point.insulatorPos();
            Vec3 location = point.position();

            builder.append(pos.getX()).append(",");
            builder.append(pos.getY()).append(",");
            builder.append(pos.getZ()).append(",");

            builder.append(location.x).append(",");
            builder.append(location.y).append(",");
            builder.append(location.z);
        }

        entityData.set(SUPPORT_POINTS, builder.toString());
    }

    /**
     * 指定された端点に接続されている分岐コネクタを取得する。
     */
    private WireBranchConnectorEntity getBranch(
            boolean endpointA
    ) {
        if (level().isClientSide()) {
            int id = entityData.get(
                    endpointA ? BRANCH_A_ID : BRANCH_B_ID
            );

            Entity entity = id < 0
                    ? null
                    : level().getEntity(id);

            return entity instanceof WireBranchConnectorEntity branch
                    ? branch
                    : null;
        }

        UUID uuid = endpointA ? branchAUuid : branchBUuid;

        if (uuid == null || !(level() instanceof ServerLevel serverLevel)) {
            return null;
        }

        Entity entity = serverLevel.getEntity(uuid);

        return entity instanceof WireBranchConnectorEntity branch
                ? branch
                : null;
    }

    /**
     * ブロック端子として有効な接続先か確認する。
     */
    private boolean isValidAnchor(BlockPos pos) {
        return level().getBlockState(pos).getBlock()
                instanceof WireConnectionPointProvider;
    }

    /**
     * 分岐コネクタに接続された端点を更新する。
     *
     * 親ワイヤーが未ロードの場合はコネクタの位置を推測せず、
     * 最後に保存された端点座標を維持する。
     */
    private void updateAttachedEndpoints() {
        WireBranchConnectorEntity branchA = getBranch(true);

        if (branchA != null && branchA.isAlive()) {
            Vec3 port =
                    branchA.getResolvedBranchPortWorldPosition();

            if (port != null) {
                setExactEndpointA(port.toVector3f());
            }
        }

        WireBranchConnectorEntity branchB = getBranch(false);

        if (branchB != null && branchB.isAlive()) {
            Vec3 port =
                    branchB.getResolvedBranchPortWorldPosition();

            if (port != null) {
                setExactEndpointB(port.toVector3f());
            }
        }
    }

    /**
     * ワイヤーエンティティ自身の位置を端点の中間に設定する。
     */
    private void updatePosition() {
        Vec3 endpointA = getEndpointAWorldPosition();
        Vec3 endpointB = getEndpointBWorldPosition();

        Vec3 center = endpointA.add(endpointB).scale(0.5);

        setPos(center.x, center.y, center.z);
    }

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide()) {

            // 碍子が撤去されている支持点を検証する。
            removeInvalidSupportPoints();

            /*
             * 分岐コネクタに接続されていない通常の端点だけを検証する。
             * チャンク未ロード中はブロック端子の有無を判定しない。
             */
            if (branchAUuid == null && !isEndpointAFree()) {
                BlockPos pos = getEndpointA();

                if (level().hasChunkAt(pos)
                        && !isValidAnchor(pos)) {
                    discard();
                    return;
                }
            }

            if (branchBUuid == null && !isEndpointBFree()) {
                BlockPos pos = getEndpointB();

                if (level().hasChunkAt(pos)
                        && !isValidAnchor(pos)) {
                    discard();
                    return;
                }
            }
        }

        updateAttachedEndpoints();
        updatePosition();
    }

    /**
     * 碍子が撤去された、または別のブロックに置き換わった
     * 支持点を電線側から削除する。
     *
     * チャンク未ロード中は判定せず、次回ロード時に確認する。
     */
    private void removeInvalidSupportPoints() {
        List<SupportPoint> points =
                new ArrayList<>(getSupportPoints());

        for (SupportPoint point : points) {
            BlockPos pos = point.insulatorPos();

            // チャンク未ロードなら、まだ判定しない。
            if (!level().hasChunkAt(pos)) {
                continue;
            }

            // 碍子が存在しない場合は支持点を削除する。
            if (!(level().getBlockState(pos).getBlock()
                    instanceof InsulatorBlock)) {

                removeSupportPoint(pos);
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("EndpointA", getEndpointA().asLong());
        tag.putLong("EndpointB", getEndpointB().asLong());

        Vec3 endpointA = getEndpointAWorldPosition();
        Vec3 endpointB = getEndpointBWorldPosition();

        tag.putDouble("EndpointAX", endpointA.x);
        tag.putDouble("EndpointAY", endpointA.y);
        tag.putDouble("EndpointAZ", endpointA.z);

        tag.putDouble("EndpointBX", endpointB.x);
        tag.putDouble("EndpointBY", endpointB.y);
        tag.putDouble("EndpointBZ", endpointB.z);

        tag.putBoolean(
                "FreeEndpointA",
                isEndpointAFree()
        );

        tag.putBoolean(
                "FreeEndpointB",
                isEndpointBFree()
        );

        tag.putString(
                "WireType",
                getWireTypeId().toString()
        );

        if (branchAUuid != null) {
            tag.putUUID("BranchA", branchAUuid);
        }

        if (branchBUuid != null) {
            tag.putUUID("BranchB", branchBUuid);
        }
        ListTag supportList = new ListTag();

        for (SupportPoint point : getSupportPoints()) {
            CompoundTag entry = new CompoundTag();

            entry.putLong(
                    "InsulatorPos",
                    point.insulatorPos().asLong()
            );

            entry.putDouble("X", point.position().x);
            entry.putDouble("Y", point.position().y);
            entry.putDouble("Z", point.position().z);

            supportList.add(entry);
        }

        tag.put("SupportPoints", supportList);
        tag.putBoolean("TerminalA", isTerminalA());
        tag.putBoolean("TerminalB", isTerminalB());

        tag.putBoolean("ReconnectableA", isReconnectableA());
        tag.putBoolean("ReconnectableB", isReconnectableB());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        BlockPos endpointA = BlockPos.of(
                tag.getLong("EndpointA")
        );

        BlockPos endpointB = BlockPos.of(
                tag.getLong("EndpointB")
        );

        entityData.set(ENDPOINT_A, endpointA);
        entityData.set(ENDPOINT_B, endpointB);

        /*
         * 新形式では小数座標を復元する。
         * 古いセーブデータにはブロック中央の座標を使用する。
         */
        if (tag.contains("EndpointAX")
                && tag.contains("EndpointAY")
                && tag.contains("EndpointAZ")
                && tag.contains("EndpointBX")
                && tag.contains("EndpointBY")
                && tag.contains("EndpointBZ")) {

            setExactEndpointA(
                    new Vec3(
                            tag.getDouble("EndpointAX"),
                            tag.getDouble("EndpointAY"),
                            tag.getDouble("EndpointAZ")
                    ).toVector3f()
            );

            setExactEndpointB(
                    new Vec3(
                            tag.getDouble("EndpointBX"),
                            tag.getDouble("EndpointBY"),
                            tag.getDouble("EndpointBZ")
                    ).toVector3f()
            );
        } else {
            setExactEndpointA(
                    Vec3.atCenterOf(endpointA).toVector3f()
            );

            setExactEndpointB(
                    Vec3.atCenterOf(endpointB).toVector3f()
            );
        }

        entityData.set(
                FREE_ENDPOINT_A,
                tag.getBoolean("FreeEndpointA")
        );

        entityData.set(
                FREE_ENDPOINT_B,
                tag.getBoolean("FreeEndpointB")
        );

        branchAUuid = tag.hasUUID("BranchA")
                ? tag.getUUID("BranchA")
                : null;

        branchBUuid = tag.hasUUID("BranchB")
                ? tag.getUUID("BranchB")
                : null;

        // Entity ID は保存データから復元しない。
        // クライアント側では同期後に取得する。
        entityData.set(BRANCH_A_ID, -1);
        entityData.set(BRANCH_B_ID, -1);

        String wireType = tag.getString("WireType");

        if (!wireType.isBlank()) {
            setWireTypeId(
                    ResourceLocation.parse(wireType)
            );
        }
        List<SupportPoint> supportPoints = new ArrayList<>();

        if (tag.contains("SupportPoints", Tag.TAG_LIST)) {
            ListTag supportList = tag.getList(
                    "SupportPoints",
                    Tag.TAG_COMPOUND
            );

            for (int i = 0; i < supportList.size()
                    && supportPoints.size() < MAX_SUPPORT_POINTS; i++) {

                CompoundTag entry = supportList.getCompound(i);

                BlockPos insulatorPos = BlockPos.of(
                        entry.getLong("InsulatorPos")
                );

                Vec3 position = new Vec3(
                        entry.getDouble("X"),
                        entry.getDouble("Y"),
                        entry.getDouble("Z")
                );

                supportPoints.add(
                        new SupportPoint(insulatorPos, position)
                );
            }
        }

        entityData.set(
                TERMINAL_A,
                tag.getBoolean("TerminalA")
        );

        entityData.set(
                TERMINAL_B,
                tag.getBoolean("TerminalB")
        );

        entityData.set(
                RECONNECTABLE_A,
                tag.getBoolean("ReconnectableA")
        );

        entityData.set(
                RECONNECTABLE_B,
                tag.getBoolean("ReconnectableB")
        );


        syncSupportPoints(supportPoints);

        updatePosition();
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        Vec3 a = getEndpointAWorldPosition();
        Vec3 b = getEndpointBWorldPosition();

        double minX = Math.min(a.x, b.x);
        double minY = Math.min(a.y, b.y);
        double minZ = Math.min(a.z, b.z);

        double maxX = Math.max(a.x, b.x);
        double maxY = Math.max(a.y, b.y);
        double maxZ = Math.max(a.z, b.z);

        for (SupportPoint point : getSupportPoints()) {
            Vec3 p = point.position();

            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            minZ = Math.min(minZ, p.z);

            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
            maxZ = Math.max(maxZ, p.z);
        }

        return new AABB(
                minX, minY, minZ,
                maxX, maxY, maxZ
        ).inflate(1.0);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(
            double distance
    ) {
        double size = Math.max(
                getBoundingBoxForCulling().getSize(),
                1.0
        );

        return distance < size * size * 64.0;
    }

    /**
     * 電線が削除される際に、碍子側の支持登録も解除する。
     *
     * チャンクのアンロードでは支持登録を維持する。
     * 実際に電線が破棄された場合だけ関連データを整理する。
     */
    @Override
    public void remove(RemovalReason reason) {

        if (!level().isClientSide()
                && (reason == RemovalReason.DISCARDED
                || reason == RemovalReason.KILLED)) {

            UUID wireId = getUUID();

            for (SupportPoint point :
                    new ArrayList<>(getSupportPoints())) {

                BlockPos insulatorPos = point.insulatorPos();

                // 碍子のチャンクが読み込まれている場合だけ処理する。
                if (!level().hasChunkAt(insulatorPos)) {
                    continue;
                }

                if (level().getBlockEntity(insulatorPos)
                        instanceof com.github.ohnokogikawaii.insulator.InsulatorBlockEntity insulatorEntity) {

                    insulatorEntity.removeSupportedWire(wireId);
                }
            }
        }

        super.remove(reason);
    }
}