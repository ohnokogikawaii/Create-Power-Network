package com.github.ohnokogikawaii.wire;

import com.github.ohnokogikawaii.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class WireBranchConnectorItem extends Item {
    private static final double REACH = 6.0;
    private static final double MAX_HIT_DISTANCE = 0.40;
    private static final double ENDPOINT_MARGIN = 0.04;
    private static final int TRACE_SAMPLES = 300;

    private final boolean downType;

    private record WireHit(
            WireEntity wire, double wireT, double distance
    ) {}

    public WireBranchConnectorItem(
            Properties properties, boolean downType
    ) {
        super(properties);
        this.downType = downType;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();

        if (player == null) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        WireHit hit = findWireHit(level, player);

        if (hit == null) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        return placeConnector(
                level, player, context.getItemInHand(), hit
        );
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level, Player player, InteractionHand hand
    ) {
        ItemStack stack = player.getItemInHand(hand);
        WireHit hit = findWireHit(level, player);

        if (hit == null) {
            return InteractionResultHolder.pass(stack);
        }

        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }

        InteractionResult result = placeConnector(
                level, player, stack, hit
        );

        return new InteractionResultHolder<>(result, stack);
    }

    private WireHit findWireHit(Level level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F).normalize();
        Vec3 rayEnd = eye.add(look.scale(REACH));

        // WireEntity の中心だけでなく、長いワイヤーも検索する。
        AABB searchArea = new AABB(eye, rayEnd).inflate(64.0);
        WireHit bestHit = null;

        for (WireEntity wire : level.getEntitiesOfClass(
                WireEntity.class, searchArea, Entity::isAlive
        )) {
            Vec3 a = wire.getEndpointAWorldPosition();
            Vec3 b = wire.getEndpointBWorldPosition();

            if (a.distanceToSqr(b) < 1.0E-8) {
                continue;
            }

            boolean canSag = wire.getWireType() != null
                    && wire.getWireType().canSag();

            for (int i = 0; i <= TRACE_SAMPLES; i++) {
                double t = (double) i / TRACE_SAMPLES;

                if (t < ENDPOINT_MARGIN
                        || t > 1.0 - ENDPOINT_MARGIN) {
                    continue;
                }

                Vec3 point = WireBranchGeometry.pointOnWire(
                        a, b, t, canSag
                );

                Vec3 fromEye = point.subtract(eye);
                double alongRay = fromEye.dot(look);

                if (alongRay < 0.0 || alongRay > REACH) {
                    continue;
                }

                Vec3 closestOnRay = eye.add(
                        look.scale(alongRay)
                );

                double distance = point.distanceTo(closestOnRay);

                if (distance > MAX_HIT_DISTANCE) {
                    continue;
                }

                if (bestHit == null
                        || distance < bestHit.distance()) {
                    bestHit = new WireHit(wire, t, distance);
                }
            }
        }

        return bestHit;
    }

    private InteractionResult placeConnector(
            Level level,
            Player player,
            ItemStack stack,
            WireHit hit
    ) {
        WireEntity wire = hit.wire();

        if (!wire.isAlive()) {
            return InteractionResult.PASS;
        }

        Vec3 targetPosition = WireBranchGeometry.pointOnWire(
                wire.getEndpointAWorldPosition(),
                wire.getEndpointBWorldPosition(),
                hit.wireT(),
                wire.getWireType() != null
                        && wire.getWireType().canSag()
        );

        AABB duplicateSearch = new AABB(
                targetPosition, targetPosition
        ).inflate(0.65);

        for (WireBranchConnectorEntity existing :
                level.getEntitiesOfClass(
                        WireBranchConnectorEntity.class,
                        duplicateSearch,
                        Entity::isAlive
                )) {
            if (wire.getUUID().equals(existing.getParentWireUuid())
                    && Math.abs(
                    existing.getWireT() - hit.wireT()
            ) < 0.025) {
                player.displayClientMessage(
                        Component.literal(
                                "この位置には既に分岐コネクタがあります。"
                        ).withStyle(ChatFormatting.RED),
                        true
                );

                return InteractionResult.SUCCESS;
            }
        }

        WireBranchConnectorEntity connector =
                new WireBranchConnectorEntity(
                        ModEntities.WIRE_BRANCH_CONNECTOR.get(),
                        level,
                        wire,
                        hit.wireT(),
                        downType,
                        0
                );

        if (!level.addFreshEntity(connector)) {
            player.displayClientMessage(
                    Component.literal(
                            "分岐コネクタを生成できませんでした。"
                    ).withStyle(ChatFormatting.RED),
                    true
            );

            return InteractionResult.FAIL;
        }

        player.displayClientMessage(
                Component.literal(
                        downType
                                ? "下向き分岐コネクタを設置しました。"
                                : "斜め分岐コネクタを設置しました。"
                ).withStyle(ChatFormatting.GREEN),
                true
        );

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        return InteractionResult.SUCCESS;
    }
}

