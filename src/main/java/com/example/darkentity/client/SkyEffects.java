package com.example.darkentity.client;

import com.example.darkentity.DarkEntityMod;
import com.example.darkentity.entity.DarkEntity;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.post.PostProcessingManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Красное небо / белая вспышка: красим и сгущаем туман. Параллельно включаем глитч-пайплайн Veil
 * (assets/darkentity/pinwheel/post/glitch.json), пока рядом босс в нужной фазе.
 */
@EventBusSubscriber(modid = DarkEntityMod.MODID, value = Dist.CLIENT)
public class SkyEffects {
    private static final Logger LOGGER = LoggerFactory.getLogger("darkentity");
    private static final ResourceLocation GLITCH =
            ResourceLocation.fromNamespaceAndPath(DarkEntityMod.MODID, "glitch");

    private static int mode = 0;
    private static boolean glitchOn = false;
    private static boolean glitchFailed = false;

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        int best = 0;
        if (mc.level != null && mc.player != null) {
            for (DarkEntity e : mc.level.getEntitiesOfClass(DarkEntity.class,
                    mc.player.getBoundingBox().inflate(160))) {
                best = Math.max(best, e.getSkyMode());
            }
        }
        mode = best;
        updateGlitch(mode != 0);
    }

    private static void updateGlitch(boolean want) {
        if (glitchFailed || want == glitchOn) return;
        try {
            PostProcessingManager manager = VeilRenderSystem.renderer().getPostProcessingManager();
            if (want) {
                manager.add(GLITCH);
            } else {
                manager.remove(GLITCH);
            }
            glitchOn = want;
        } catch (Throwable t) {
            glitchFailed = true;
            LOGGER.error("Не удалось переключить Veil-глитч, отключаю эффект", t);
        }
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (mode == 1) {
            event.setRed(0.65f);
            event.setGreen(0.05f);
            event.setBlue(0.08f);
        } else if (mode == 2) {
            event.setRed(1f);
            event.setGreen(1f);
            event.setBlue(1f);
        }
    }

    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog event) {
        if (mode == 0) return;
        event.setNearPlaneDistance(0f);
        event.setFarPlaneDistance(mode == 2 ? 6f : 70f);
        event.setCanceled(true);
    }
}
