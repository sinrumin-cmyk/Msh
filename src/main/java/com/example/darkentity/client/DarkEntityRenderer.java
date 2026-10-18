package com.example.darkentity.client;

import com.example.darkentity.DarkEntityMod;
import com.example.darkentity.entity.DarkEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Модель:      assets/darkentity/geo/entity/dark_entity.geo.json
 * Анимации:    assets/darkentity/animations/entity/dark_entity.animation.json
 * Текстура:    assets/darkentity/textures/entity/dark_entity.png
 * Свечение:    assets/darkentity/textures/entity/dark_entity_glowmask.png
 */
public class DarkEntityRenderer extends GeoEntityRenderer<DarkEntity> {
    public DarkEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(
                ResourceLocation.fromNamespaceAndPath(DarkEntityMod.MODID, "dark_entity")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 1.4f;
    }
}
