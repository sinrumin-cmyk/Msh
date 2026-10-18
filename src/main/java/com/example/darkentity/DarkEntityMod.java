package com.example.darkentity;

import com.example.darkentity.entity.DarkEntity;
import com.example.darkentity.network.ClientPayloads;
import com.example.darkentity.network.FxPayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(DarkEntityMod.MODID)
public class DarkEntityMod {
    public static final String MODID = "darkentity";

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<DarkEntity>> DARK_ENTITY =
            ENTITIES.register("dark_entity", () -> EntityType.Builder
                    .of(DarkEntity::new, MobCategory.MONSTER)
                    .sized(1.2f, 4.0f)
                    .fireImmune()
                    .clientTrackingRange(16)
                    .build("dark_entity"));

    public static final DeferredHolder<Item, Item> DARK_ENTITY_SPAWN_EGG =
            ITEMS.register("dark_entity_spawn_egg",
                    () -> new DeferredSpawnEggItem(DARK_ENTITY, 0x000000, 0x00E5FF, new Item.Properties()));

    public DarkEntityMod(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(this::onAttributes);
        modBus.addListener(this::onCreativeTab);
        modBus.addListener(this::onPayloads);
    }

    private void onAttributes(EntityAttributeCreationEvent event) {
        event.put(DARK_ENTITY.get(), DarkEntity.createAttributes().build());
    }

    private void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(DARK_ENTITY_SPAWN_EGG.get());
        }
    }

    private void onPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(FxPayload.TYPE, FxPayload.CODEC, ClientPayloads::handle);
    }
}
