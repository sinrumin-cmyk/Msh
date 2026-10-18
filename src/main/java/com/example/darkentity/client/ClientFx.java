package com.example.darkentity.client;

import com.example.darkentity.DarkEntityMod;
import com.example.darkentity.network.FxPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.ArrayList;
import java.util.List;

/** Клиентская часть эффектов: толстые светящиеся лучи (столбы, лазер, шлейф сферы) и тряска камеры. */
@EventBusSubscriber(modid = DarkEntityMod.MODID, value = Dist.CLIENT)
public class ClientFx {
    private static final ResourceLocation BEAM_TEX =
            ResourceLocation.withDefaultNamespace("textures/entity/beacon_beam.png");
    private static final RandomSource RNG = RandomSource.create();

    private static final List<Beam> BEAMS = new ArrayList<>();
    private static final List<Shake> SHAKES = new ArrayList<>();

    private static class Beam {
        Vec3 a, b;
        float radius;
        int color, life, maxLife;
    }

    private static class Shake {
        Vec3 pos;
        float intensity;
        int life, maxLife;
    }

    public static void add(FxPayload p) {
        if (p.kind() == 0) {
            Beam b = new Beam();
            b.a = p.a();
            b.b = p.b();
            b.radius = p.radius();
            b.color = p.color();
            b.life = b.maxLife = Math.max(1, p.life());
            BEAMS.add(b);
        } else {
            Shake s = new Shake();
            s.pos = p.a();
            s.intensity = p.radius();
            s.life = s.maxLife = Math.max(1, p.life());
            SHAKES.add(s);
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        BEAMS.removeIf(b -> --b.life <= 0);
        SHAKES.removeIf(s -> --s.life <= 0);
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (SHAKES.isEmpty()) return;
        Vec3 cam = event.getCamera().getPosition();
        float amp = 0f;
        for (Shake s : SHAKES) {
            float falloff = (float) Math.max(0.0, 1.0 - cam.distanceTo(s.pos) / 90.0);
            amp += s.intensity * falloff * ((float) s.life / s.maxLife);
        }
        if (amp <= 0.001f) return;
        event.setYaw(event.getYaw() + (RNG.nextFloat() - 0.5f) * amp * 2f);
        event.setPitch(event.getPitch() + (RNG.nextFloat() - 0.5f) * amp * 2f);
        event.setRoll(event.getRoll() + (RNG.nextFloat() - 0.5f) * amp);
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || BEAMS.isEmpty()) return;

        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        RenderType type = RenderType.beaconBeam(BEAM_TEX, true);
        VertexConsumer vc = buffers.getBuffer(type);
        float scroll = (System.nanoTime() / 1_000_000L % 1500L) / 1500f;

        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        PoseStack.Pose pose = ps.last();
        for (Beam b : BEAMS) {
            float fade = Math.min(1f, (float) b.life / Math.min(6, b.maxLife));
            int r = (b.color >> 16) & 0xFF, g = (b.color >> 8) & 0xFF, bl = b.color & 0xFF;
            // внешнее свечение цветом и белое горячее ядро
            drawBeam(pose, vc, b.a, b.b, b.radius, r, g, bl, (int) (120 * fade), scroll);
            drawBeam(pose, vc, b.a, b.b, b.radius * 0.4f, 255, 255, 255, (int) (255 * fade), -scroll);
        }
        ps.popPose();
        buffers.endBatch(type);
    }

    private static void drawBeam(PoseStack.Pose pose, VertexConsumer vc, Vec3 a, Vec3 b, float radius,
                                 int r, int g, int bl, int alpha, float scroll) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 0.01 || alpha <= 0) return;
        Vec3 dn = d.scale(1.0 / len);
        Vec3 ref = Math.abs(dn.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = dn.cross(ref).normalize();
        Vec3 w = dn.cross(u).normalize();
        float v0 = scroll;
        float v1 = scroll + (float) (len * 0.15);

        for (int i = 0; i < 2; i++) {
            Vec3 side = i == 0 ? u : w;
            Vec3 normal = i == 0 ? w : u;
            Vec3 o = side.scale(radius);
            Vec3 p0 = a.add(o), p1 = a.subtract(o), p2 = b.subtract(o), p3 = b.add(o);
            quad(pose, vc, p0, p1, p2, p3, v0, v1, r, g, bl, alpha, normal);
            quad(pose, vc, p3, p2, p1, p0, v1, v0, r, g, bl, alpha, normal);
        }
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vc, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
                             float v0, float v1, int r, int g, int b, int a, Vec3 n) {
        vertex(pose, vc, p0, 0f, v0, r, g, b, a, n);
        vertex(pose, vc, p1, 1f, v0, r, g, b, a, n);
        vertex(pose, vc, p2, 1f, v1, r, g, b, a, n);
        vertex(pose, vc, p3, 0f, v1, r, g, b, a, n);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, Vec3 p, float u, float v,
                               int r, int g, int b, int a, Vec3 n) {
        vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
    }
}
