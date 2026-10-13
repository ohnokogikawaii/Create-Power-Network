
package com.github.ohnokogikawaii.wire;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class WireCutterItem extends Item {

    private static final double REACH = 3.0;
    private static final double MAX_HIT_DISTANCE = 0.30;
    private static final int TRACE_SAMPLES = 300;

    private record WireHit(
            WireEntity wire,
            double distanceAlongRay,
            double distanceToRay
    ) {}

    public WireCutterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            net.minecraft.world.InteractionHand hand
    ) {
        ItemStack stack = player.getItemInHand(hand);
        WireHit hit = findWireHit(level, player);

        if (hit == null) {
            return InteractionResultHolder.pass(stack);
        }

        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }

        WireEntity wire = hit.wire();

        if (wire.isAlive()) {
            wire.discard();

            player.displayClientMessage(
                    Component.translatable(
                            "message.powernetwork.wire.cut"
                    ).withStyle(ChatFormatting.GREEN),
                    true
            );
        }

        return InteractionResultHolder.success(stack);
    }

    private WireHit findWireHit(Level level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F).normalize();
        Vec3 rayEnd = eye.add(look.scale(REACH));

        AABB searchArea = new AABB(eye, rayEnd).inflate(64.0);

        WireHit bestHit = null;

        for (WireEntity wire : level.getEntitiesOfClass(
                WireEntity.class,
                searchArea,
                Entity::isAlive
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
                        || alongRay < bestHit.distanceAlongRay()
                        || (Math.abs(
                        alongRay - bestHit.distanceAlongRay()
                ) < 1.0E-6
                        && distance < bestHit.distanceToRay())) {
                    bestHit = new WireHit(
                            wire,
                            alongRay,
                            distance
                    );
                }
            }
        }

        return bestHit;
    }
}
