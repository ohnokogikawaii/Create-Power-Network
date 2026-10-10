package com.github.ohnokogikawaii.wire;

import com.github.ohnokogikawaii.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class WireBranchConnectorItem extends Item {

    private static final double REACH = 6.0;
    private static final double MAX_HIT_DISTANCE = 0.30;
    private static final double ENDPOINT_MARGIN = 0.03;

    private final boolean downType;

    public WireBranchConnectorItem(
            Properties properties,
            boolean downType
    ) {
        super(properties);
        this.downType = downType;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            InteractionHand hand
    ) {
        ItemStack stack = player.getItemInHand(hand);

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F).normalize();
        Vec3 rayEnd = eye.add(look.scale(REACH));

        AABB searchArea = new AABB(eye, rayEnd).inflate(64.0);

        WireEntity nearestWire = null;
        double nearestWireT = 0.5;
        double nearestDistance = Double.MAX_VALUE;

        for (WireEntity wire : level.getEntitiesOfClass(
                WireEntity.class,
                searchArea,
                Entity::isAlive
        )) {
            Vec3 a = wire.getEndpointAWorldPosition();
            Vec3 b = wire.getEndpointBWorldPosition();

            // ワイヤー上の位置をサンプリングして、
            // 視線に最も近い位置を探す。
            final int samples = 100;

            for (int i = 0; i <= samples; i++) {
                double t = (double) i / samples;

                // 端子のすぐ近くには取り付けない。
                if (t < ENDPOINT_MARGIN
                        || t > 1.0 - ENDPOINT_MARGIN) {
                    continue;
                }

                Vec3 point = a.lerp(b, t);
                Vec3 fromEye = point.subtract(eye);

                double rayDistance = fromEye.dot(look);

                if (rayDistance < 0.0 || rayDistance > REACH) {
                    continue;
                }

                Vec3 closestOnRay = eye.add(
                        look.scale(rayDistance)
                );

                double distance = point.distanceTo(closestOnRay);

                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestWire = wire;
                    nearestWireT = t;
                }
            }
        }

        if (nearestWire == null
                || nearestDistance > MAX_HIT_DISTANCE) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.literal(
                                "ワイヤーを狙って右クリックしてください。"
                        ).withStyle(ChatFormatting.RED),
                        true
                );
            }

            return InteractionResultHolder.pass(stack);
        }

        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(
                    stack,
                    true
            );
        }

        // 同じワイヤーのほぼ同じ位置への重複設置を防ぐ。
        Vec3 a = nearestWire.getEndpointAWorldPosition();
        Vec3 b = nearestWire.getEndpointBWorldPosition();

        Vec3 targetPosition = a.lerp(b, nearestWireT);

        AABB duplicateSearch = new AABB(
                targetPosition,
                targetPosition
        ).inflate(0.15);

        for (WireBranchConnectorEntity existing :
                level.getEntitiesOfClass(
                        WireBranchConnectorEntity.class,
                        duplicateSearch,
                        Entity::isAlive
                )) {

            if (nearestWire.getUUID().equals(
                    existing.getParentWireUuid()
            ) && Math.abs(
                    existing.getWireT() - nearestWireT
            ) < 0.025) {
                player.displayClientMessage(
                        Component.literal(
                                "この位置には既に分岐コネクタがあります。"
                        ).withStyle(ChatFormatting.RED),
                        true
                );

                return InteractionResultHolder.success(stack);
            }
        }

        WireBranchConnectorEntity connector =
                new WireBranchConnectorEntity(
                        ModEntities.WIRE_BRANCH_CONNECTOR.get(),
                        level,
                        nearestWire,
                        nearestWireT,
                        downType,
                        0
                );

        if (!level.addFreshEntity(connector)) {
            player.displayClientMessage(
                    Component.literal(
                            "分岐コネクタを設置できませんでした。"
                    ).withStyle(ChatFormatting.RED),
                    true
            );

            return InteractionResultHolder.fail(stack);
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

        return InteractionResultHolder.success(stack);
    }
}

