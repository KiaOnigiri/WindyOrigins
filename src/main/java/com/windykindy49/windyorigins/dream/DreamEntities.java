package com.windykindy49.windyorigins.dream;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class DreamEntities {
    public static final EntityType<NightmareEntity> NIGHTMARE = Registry.register(
        Registries.ENTITY_TYPE,
        new Identifier("windyorigins", "nightmare"),
        FabricEntityTypeBuilder.create(SpawnGroup.MISC, NightmareEntity::new).dimensions(EntityDimensions.fixed(0.6F, 1.95F)).trackRangeBlocks(64).build()
    );
    public static final EntityType<DreamBodyEntity> BODY = Registry.register(
        Registries.ENTITY_TYPE,
        new Identifier("windyorigins", "dream_body"),
        FabricEntityTypeBuilder.create(SpawnGroup.MISC, DreamBodyEntity::new).dimensions(EntityDimensions.fixed(1.6F, 0.5F)).trackRangeBlocks(64).build()
    );

    private DreamEntities() {
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(NIGHTMARE, NightmareEntity.createAttributes());
    }
}
