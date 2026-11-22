
package com.github.ohnokogikawaii.wire;

import com.github.ohnokogikawaii.insulator.StrainInsulatorBlock;
import com.github.ohnokogikawaii.insulator.StrainInsulatorBlockEntity;
import com.github.ohnokogikawaii.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class JumperWireItem extends WireItem {

    private record Selection(
            BlockPos pos,
            Vec3 connection
    ) {}

    private static final Map<UUID, Selection> STARTS =
            new ConcurrentHashMap<>();

    public JumperWireItem(
            Properties properties,
            net.minecraft.resources.ResourceLocation wireTypeId
    ) {
        super(properties, wireTypeId);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {

        Level level = context.getLevel();
        Player player = context.getPlayer();

        if (player == null) {
            return InteractionResult.PASS;
        }

        UUID playerId = player.getUUID();

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                STARTS.remove(playerId);

                player.displayClientMessage(
                        Component.literal(
                                "ジャンパ線の敷設をキャンセルしました。"
                        ).withStyle(ChatFormatting.YELLOW),
                        true
                );
            }

            return InteractionResult.SUCCESS;
        }

        BlockPos clickedPos =
                context.getClickedPos().immutable();

        if (!(level.getBlockState(clickedPos).getBlock()
                instanceof StrainInsulatorBlock strainInsulator)) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!(level.getBlockEntity(clickedPos)
                instanceof StrainInsulatorBlockEntity clickedEntity)) {
            return InteractionResult.SUCCESS;
        }

        Vec3 connection =
                strainInsulator.getRetainedWirePosition(
                        level.getBlockState(clickedPos),
                        clickedPos
                );

        Selection start = STARTS.get(playerId);

        // 1か所目を選択
        if (start == null) {

            if (!clickedEntity.canAddJumperWire()) {
                player.displayClientMessage(
                        Component.literal(
                                "この耐張碍子には、これ以上ジャンパ線を接続できません。"
                        ).withStyle(ChatFormatting.RED),
                        true
                );

                return InteractionResult.SUCCESS;
            }

            STARTS.put(
                    playerId,
                    new Selection(clickedPos, connection)
            );

            player.displayClientMessage(
                    Component.literal(
                            "ジャンパ線の始点を設定しました。次の耐張碍子を選択してください。"
                    ).withStyle(ChatFormatting.YELLOW),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        // 同じ耐張碍子は選択できない
        if (start.pos().equals(clickedPos)) {
            player.displayClientMessage(
                    Component.literal(
                            "始点とは別の耐張碍子を選択してください。"
                    ).withStyle(ChatFormatting.RED),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        if (!(level.getBlockEntity(start.pos())
                instanceof StrainInsulatorBlockEntity startEntity)
                || !startEntity.canAddJumperWire()) {

            STARTS.remove(playerId);

            player.displayClientMessage(
                    Component.literal(
                            "始点の耐張碍子が無効、または接続数が上限に達しています。"
                    ).withStyle(ChatFormatting.RED),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        if (!clickedEntity.canAddJumperWire()) {
            player.displayClientMessage(
                    Component.literal(
                            "終点の耐張碍子はジャンパ線の接続数が上限に達しています。"
                    ).withStyle(ChatFormatting.RED),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        // 既に同じ2か所を接続しているジャンパ線があるか確認
        if (level instanceof ServerLevel serverLevel) {
            for (UUID wireId : startEntity.getJumperWireIds()) {

                if (!clickedEntity.getJumperWireIds().contains(wireId)) {
                    continue;
                }

                if (serverLevel.getEntity(wireId)
                        instanceof WireEntity existing
                        && existing.isAlive()
                        && existing.isJumperWire()) {

                    player.displayClientMessage(
                            Component.literal(
                                    "この耐張碍子間には既にジャンパ線があります。"
                            ).withStyle(ChatFormatting.RED),
                            true
                    );

                    return InteractionResult.SUCCESS;
                }
            }
        }

        // 電線の長さを確認
        WireType wireType =
                WireTypeManager.get(getWireTypeId());

        if (wireType == null) {
            return InteractionResult.SUCCESS;
        }

        double distance =
                start.connection().distanceTo(connection);

        if (distance < wireType.getMinimumLength()
                || distance > wireType.getMaximumLength()) {

            player.displayClientMessage(
                    Component.literal(
                            "ジャンパ線の長さが設定範囲外です。"
                    ).withStyle(ChatFormatting.RED),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        // ジャンパ線エンティティを生成
        WireEntity wire = new WireEntity(
                level,
                start.connection(),
                connection,
                start.pos(),
                clickedPos,
                false,
                false,
                getWireTypeId()
        );

        wire.setJumperWire(true);

        if (!level.addFreshEntity(wire)) {
            player.displayClientMessage(
                    Component.literal(
                            "ジャンパ線の生成に失敗しました。"
                    ).withStyle(ChatFormatting.RED),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        // 両側の耐張碍子にジャンパ線UUIDを登録
        boolean startRegistered =
                startEntity.addJumperWire(wire.getUUID());

        boolean endRegistered =
                startRegistered
                        && clickedEntity.addJumperWire(wire.getUUID());

        if (!startRegistered || !endRegistered) {

            if (startRegistered) {
                startEntity.removeJumperWire(wire.getUUID());
            }

            clickedEntity.removeJumperWire(wire.getUUID());

            wire.discard();

            player.displayClientMessage(
                    Component.literal(
                            "ジャンパ線の接続登録に失敗しました。"
                    ).withStyle(ChatFormatting.RED),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        STARTS.remove(playerId);

        player.displayClientMessage(
                Component.literal(
                        "ジャンパ線を接続しました。"
                ).withStyle(ChatFormatting.GREEN),
                true
        );

        return InteractionResult.SUCCESS;
    }
}
