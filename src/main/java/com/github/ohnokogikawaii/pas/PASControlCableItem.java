package com.github.ohnokogikawaii.pas;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

public class PASControlCableItem extends Item {

    private static final String SELECTED_SOG_POS = "SelectedSOGPos";
    private static final String SELECTED_SOG_DIMENSION =
            "SelectedSOGDimension";

    public PASControlCableItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (context.getPlayer() == null) {
            return InteractionResult.PASS;
        }

        BlockPos clickedPos = context.getClickedPos();
        var clickedEntity = level.getBlockEntity(clickedPos);

        if (clickedEntity instanceof SOGBlockEntity) {
            selectSog(context, clickedPos);
            return InteractionResult.SUCCESS;
        }

        if (clickedEntity instanceof PASBlockEntity pas) {
            return connectPas(context, pas);
        }

        return InteractionResult.PASS;
    }

    private void selectSog(
            UseOnContext context,
            BlockPos sogPos
    ) {
        var stack = context.getItemInHand();

        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                tag -> {
                    tag.putLong(
                            SELECTED_SOG_POS,
                            sogPos.asLong()
                    );

                    tag.putString(
                            SELECTED_SOG_DIMENSION,
                            context.getLevel()
                                    .dimension()
                                    .location()
                                    .toString()
                    );
                }
        );

        context.getPlayer().displayClientMessage(
                Component.literal(
                        "SOGを選択しました。接続先のPASを右クリックしてください。"
                ),
                false
        );
    }

    private InteractionResult connectPas(
            UseOnContext context,
            PASBlockEntity pas
    ) {
        var player = context.getPlayer();
        var level = context.getLevel();
        var stack = context.getItemInHand();

        CompoundTag tag = stack
                .getOrDefault(
                        DataComponents.CUSTOM_DATA,
                        CustomData.EMPTY
                )
                .copyTag();

        if (!tag.contains(SELECTED_SOG_POS)
                || !tag.contains(SELECTED_SOG_DIMENSION)) {
            player.displayClientMessage(
                    Component.literal("先にSOGを選択してください。"),
                    false
            );
            return InteractionResult.FAIL;
        }

        String selectedDimension =
                tag.getString(SELECTED_SOG_DIMENSION);

        String currentDimension =
                level.dimension().location().toString();

        if (!selectedDimension.equals(currentDimension)) {
            player.displayClientMessage(
                    Component.literal(
                            "別のディメンションにあるSOGは接続できません。"
                    ),
                    false
            );
            return InteractionResult.FAIL;
        }

        BlockPos sogPos = BlockPos.of(
                tag.getLong(SELECTED_SOG_POS)
        );

        if (!level.hasChunkAt(sogPos)) {
            player.displayClientMessage(
                    Component.literal(
                            "選択したSOGのチャンクが読み込まれていません。"
                    ),
                    false
            );
            return InteractionResult.FAIL;
        }

        if (!(level.getBlockEntity(sogPos)
                instanceof SOGBlockEntity sog)) {
            player.displayClientMessage(
                    Component.literal(
                            "選択した位置にSOGがありません。"
                    ),
                    false
            );
            return InteractionResult.FAIL;
        }

        BlockPos pasPos = pas.getBlockPos();

        // 既に別の機器と接続されていないか確認する。
        if ((sog.getLinkedPasPos() != null
                && !pasPos.equals(sog.getLinkedPasPos()))
                || (pas.getLinkedSogPos() != null
                && !sogPos.equals(pas.getLinkedSogPos()))) {

            player.displayClientMessage(
                    Component.literal(
                            "SOGまたはPASが別の機器に接続されています。"
                    ),
                    false
            );
            return InteractionResult.FAIL;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.FAIL;
        }

        // 同じ組み合わせの制御線が存在するか確認する。
        Vec3 sogCenter = Vec3.atCenterOf(sogPos);
        Vec3 pasCenter = Vec3.atCenterOf(pasPos);

        AABB searchArea = new AABB(
                sogCenter,
                pasCenter
        ).inflate(1.0);

        List<PASControlCableEntity> existing =
                serverLevel.getEntitiesOfClass(
                        PASControlCableEntity.class,
                        searchArea,
                        cable -> cable.isAlive()
                                && sogPos.equals(cable.getSOGPos())
                                && pasPos.equals(cable.getPASPos())
                );

        // 制御線がまだ存在しない場合だけ生成する。
        if (existing.isEmpty()) {
            PASControlCableEntity cable =
                    new PASControlCableEntity(
                            level,
                            pasPos,
                            sogPos
                    );

            if (!serverLevel.addFreshEntity(cable)) {
                player.displayClientMessage(
                        Component.literal(
                                "制御線を設置できませんでした。"
                        ),
                        false
                );
                return InteractionResult.FAIL;
            }
        }

        /*
         * 制御線の接続を成立させる。
         * これによりPASとSOGの論理リンクも自動的に作成される。
         */
        if (!sog.linkToPas(pas)) {
            // 接続に失敗した場合は、今回生成した制御線を削除する。
            List<PASControlCableEntity> cables =
                    serverLevel.getEntitiesOfClass(
                            PASControlCableEntity.class,
                            searchArea,
                            cable -> cable.isAlive()
                                    && sogPos.equals(cable.getSOGPos())
                                    && pasPos.equals(cable.getPASPos())
                    );

            for (PASControlCableEntity cable : cables) {
                cable.discard();
            }

            player.displayClientMessage(
                    Component.literal("PASとSOGを接続できませんでした。"),
                    false
            );
            return InteractionResult.FAIL;
        }

        sog.setControlMode(
                SOGBlockEntity.ControlMode.CONTROL_CABLE
        );

        // 選択状態を解除する。
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                data -> {
                    data.remove(SELECTED_SOG_POS);
                    data.remove(SELECTED_SOG_DIMENSION);
                }
        );

        player.displayClientMessage(
                Component.literal(
                        "PASとSOGを制御線で接続しました。"
                ),
                false
        );

        return InteractionResult.SUCCESS;
    }
}