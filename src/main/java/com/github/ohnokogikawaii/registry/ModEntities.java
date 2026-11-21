
package com.github.ohnokogikawaii.registry;

import com.github.ohnokogikawaii.PowerNetwork;
import com.github.ohnokogikawaii.pas.PASBlockEntity;
import com.github.ohnokogikawaii.pas.PASControlCableEntity;
import com.github.ohnokogikawaii.wire.WireBranchConnectorEntity;
import com.github.ohnokogikawaii.wire.WireEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(
                    BuiltInRegistries.ENTITY_TYPE,
                    PowerNetwork.MODID
            );

    /**
     * 通常の電線。
     */
    public static final Supplier<EntityType<WireEntity>> WIRE =
            ENTITY_TYPES.register("wire", () ->
                    EntityType.Builder.<WireEntity>of(
                                    WireEntity::new,
                                    MobCategory.MISC
                            )
                            .sized(0.1F, 0.1F)
                            .clientTrackingRange(32)
                            .updateInterval(10)
                            .build("wire")
            );

    /**
     * 電線途中に取り付ける分岐コネクタ。
     */
    public static final Supplier<EntityType<WireBranchConnectorEntity>>
            WIRE_BRANCH_CONNECTOR =
            ENTITY_TYPES.register("wire_branch_connector", () ->
                    EntityType.Builder.<WireBranchConnectorEntity>of(
                                    WireBranchConnectorEntity::new,
                                    MobCategory.MISC
                            )
                            .sized(0.2F, 0.2F)
                            .clientTrackingRange(32)
                            .updateInterval(1)
                            .build("wire_branch_connector")
            );

    /**
     * PASとSOGを接続する専用制御線。
     */
    public static final Supplier<EntityType<PASControlCableEntity>>
            PAS_CONTROL_CABLE =
            ENTITY_TYPES.register("pas_control_cable", () ->
                    EntityType.Builder.<PASControlCableEntity>of(
                                    PASControlCableEntity::new,
                                    MobCategory.MISC
                            )
                            .sized(0.1F, 0.1F)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build("pas_control_cable")
            );


    private ModEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}