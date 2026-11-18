
package com.github.ohnokogikawaii.registry;

import com.github.ohnokogikawaii.PowerNetwork;
import com.github.ohnokogikawaii.insulator.InsulatorBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(
                    Registries.BLOCK_ENTITY_TYPE,
                    PowerNetwork.MODID
            );

    /**
     * 固定式碍子の BlockEntity。
     */
    public static final Supplier<BlockEntityType<InsulatorBlockEntity>> INSULATOR =
            BLOCK_ENTITY_TYPES.register(
                    "insulator",
                    () -> BlockEntityType.Builder.of(
                            InsulatorBlockEntity::new,
                            ModBlocks.LOW_VOLTAGE_INSULATOR.get(),
                            ModBlocks.HIGH_VOLTAGE_INSULATOR.get(),
                            ModBlocks.EXTRA_HIGH_VOLTAGE_INSULATOR.get(),
                            ModBlocks.TRANSMISSION_LINE_INSULATOR.get()
                    ).build(null)
            );

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
