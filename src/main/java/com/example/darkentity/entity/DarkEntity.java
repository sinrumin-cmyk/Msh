package com.example.darkentity.entity;

import com.example.darkentity.network.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Чёрный босс (GeckoLib). Три фазы по здоровью (>66%, >33%, остальное).
 * Способности: световые столбы, бирюзовая сфера, красный лазер, глитч-телепорт, катаклизм.
 */
public class DarkEntity extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Integer> SKY_MODE =
            SynchedEntityData.defineId(DarkEntity.class, EntityDataSerializers.INT);

    private static final DustParticleOptions CYAN_DUST =
            new DustParticleOptions(new Vector3f(0.0f, 0.9f, 1.0f), 1.6f);
    private static final DustParticleOptions BLACK_DUST =
            new DustParticleOptions(new Vector3f(0.02f, 0.02f, 0.05f), 1.8f);

    private static final int C_CYAN = 0x00E5FF;
    private static final int C_RED = 0xFF2020;
    private static final int C_WHITE = 0xFFFFFF;

    // анимации (имена из assets/darkentity/animations/entity/dark_entity.animation.json)
    private static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("animation.dark_entity.idle");
    private static final RawAnimation ANIM_WALK = RawAnimation.begin().thenLoop("animation.dark_entity.walk");
    private static final RawAnimation ANIM_CAST = RawAnimation.begin().thenPlay("animation.dark_entity.cast");
    private static final RawAnimation ANIM_LASER = RawAnimation.begin().thenPlay("animation.dark_entity.laser");
    private static final RawAnimation ANIM_CATACLYSM = RawAnimation.begin().thenPlay("animation.dark_entity.cataclysm");

    private enum Ability { NONE, PILLARS, ORB, LASER, CATACLYSM, TELEPORT }

    private static class Pillar {
        final Vec3 pos;
        int age;
        Pillar(Vec3 pos) { this.pos = pos; }
    }

    private static class Orb {
        Vec3 pos;
        Vec3 vel;
        int age;
        Orb(Vec3 pos, Vec3 vel) { this.pos = pos; this.vel = vel; }
    }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.darkentity.dark_entity"),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);

    private final List<Pillar> pillars = new ArrayList<>();
    private final List<Orb> orbs = new ArrayList<>();
    private Ability current = Ability.NONE;
    private int abilityTick = 0;
    private int cooldown = 60;
    private int cataclysmCooldown = 400;
    private boolean channeling = false;
    private Vec3 laserAim = Vec3.ZERO;

    public DarkEntity(EntityType<? extends DarkEntity> type, Level level) {
        super(type, level);
        this.xpReward = 500;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 400.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.ATTACK_DAMAGE, 14.0)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    // ------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "move", 6,
                state -> state.setAndContinue(state.isMoving() ? ANIM_WALK : ANIM_IDLE)));
        controllers.add(new AnimationController<>(this, "action", 4, state -> PlayState.STOP)
                .triggerableAnim("cast", ANIM_CAST)
                .triggerableAnim("laser", ANIM_LASER)
                .triggerableAnim("cataclysm", ANIM_CATACLYSM));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // --------------------------------------------------------- synced data

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKY_MODE, 0);
    }

    /** 0 - ничего, 1 - красное небо, 2 - белая вспышка. */
    public int getSkyMode() {
        return this.entityData.get(SKY_MODE);
    }

    private void setSkyMode(int mode) {
        this.entityData.set(SKY_MODE, mode);
    }

    // ------------------------------------------------------------- basics

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0f));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (channeling && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        this.spawnAtLocation(Items.NETHER_STAR);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        // клиентские частицы: чёрная "гарь" и бирюзовые искры, как будто тело распадается
        if (level().isClientSide) {
            for (int i = 0; i < 2; i++) {
                double x = getX() + (random.nextDouble() - 0.5) * 2.0;
                double y = getY() + random.nextDouble() * 4.0;
                double z = getZ() + (random.nextDouble() - 0.5) * 2.0;
                level().addParticle(BLACK_DUST, x, y, z, 0, 0.04, 0);
            }
            if (random.nextInt(3) == 0) {
                level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                        getX() + (random.nextDouble() - 0.5) * 2.5, getY() + random.nextDouble() * 4.0,
                        getZ() + (random.nextDouble() - 0.5) * 2.5, 0, 0.02, 0);
            }
        }
    }

    private int phase() {
        float r = getHealth() / getMaxHealth();
        return r > 0.66f ? 1 : (r > 0.33f ? 2 : 3);
    }

    // ------------------------------------------------------------------ AI

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        bossEvent.setProgress(getHealth() / getMaxHealth());
        if (!(level() instanceof ServerLevel sl)) return;

        if (cataclysmCooldown > 0) cataclysmCooldown--;
        tickPillars(sl);
        tickOrbs(sl);

        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) {
            if (current != Ability.NONE) endAbility(false);
            return;
        }

        if (current == Ability.NONE) {
            if (--cooldown <= 0) pickAbility();
            return;
        }

        abilityTick++;
        switch (current) {
            case PILLARS -> doPillars(sl, target);
            case ORB -> doOrb(sl, target);
            case LASER -> doLaser(sl, target);
            case CATACLYSM -> doCataclysm(sl, target);
            case TELEPORT -> doTeleport(sl, target);
            default -> endAbility(true);
        }
    }

    private void pickAbility() {
        int phase = phase();
        List<Ability> pool = new ArrayList<>(List.of(Ability.PILLARS, Ability.ORB, Ability.TELEPORT));
        if (phase >= 2) {
            pool.add(Ability.LASER);
            pool.add(Ability.PILLARS);
        }
        if (phase >= 3 && cataclysmCooldown <= 0) {
            pool = new ArrayList<>(List.of(Ability.CATACLYSM));
            cataclysmCooldown = 900;
        }
        current = pool.get(random.nextInt(pool.size()));
        abilityTick = 0;
    }

    private void endAbility(boolean applyCooldown) {
        current = Ability.NONE;
        abilityTick = 0;
        channeling = false;
        setSkyMode(0);
        if (applyCooldown) cooldown = Math.max(15, 50 - 10 * phase());
    }

    private boolean griefing(ServerLevel sl) {
        return sl.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    // ------------------------------------------------------- Световые столбы

    private void doPillars(ServerLevel sl, LivingEntity target) {
        if (abilityTick == 1) {
            triggerAnim("action", "cast");
            int n = 3 + phase() * 2;
            for (int i = 0; i < n; i++) {
                double ox = i == 0 ? 0 : (random.nextDouble() - 0.5) * 16;
                double oz = i == 0 ? 0 : (random.nextDouble() - 0.5) * 16;
                double x = target.getX() + ox;
                double z = target.getZ() + oz;
                double h = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
                double y = Math.abs(h - target.getY()) < 6 ? h : target.getY();
                pillars.add(new Pillar(new Vec3(x, y, z)));
            }
            sl.playSound(null, blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3f, 0.6f);
        }
        if (abilityTick >= 20) endAbility(true);
    }

    private void tickPillars(ServerLevel sl) {
        for (Iterator<Pillar> it = pillars.iterator(); it.hasNext(); ) {
            Pillar p = it.next();
            p.age++;
            Vec3 top = p.pos.add(0, 70, 0);
            if (p.age < 30) {
                for (int i = 0; i < 12; i++) {
                    double a = i * Math.PI / 6 + p.age * 0.2;
                    sl.sendParticles(ParticleTypes.END_ROD,
                            p.pos.x + Math.cos(a) * 3, p.pos.y + 0.1, p.pos.z + Math.sin(a) * 3,
                            1, 0, 0, 0, 0);
                }
                if (p.age >= 8 && p.age % 3 == 0) { // тонкий луч-предупреждение
                    Fx.beam(sl, p.pos, top, 0.2f, C_WHITE, 4);
                }
            } else if (p.age == 30) {
                AABB box = new AABB(p.pos.x - 3, p.pos.y - 2, p.pos.z - 3, p.pos.x + 3, p.pos.y + 40, p.pos.z + 3);
                for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box,
                        e -> e != this && !(e instanceof DarkEntity))) {
                    e.hurt(damageSources().mobAttack(this), 14f);
                    e.push(0, 0.8, 0);
                }
                sl.explode(this, p.pos.x, p.pos.y, p.pos.z, 2.5f, Level.ExplosionInteraction.MOB);
                sl.playSound(null, BlockPos.containing(p.pos), SoundEvents.LIGHTNING_BOLT_THUNDER,
                        SoundSource.HOSTILE, 4f, 1.4f);
                Fx.beam(sl, p.pos, top, 3.0f, C_WHITE, 14);
                Fx.shake(sl, p.pos, 1.2f, 12);
            }
            if (p.age >= 42) it.remove();
        }
    }

    // ---------------------------------------------------- Бирюзовая сфера

    private void doOrb(ServerLevel sl, LivingEntity target) {
        if (abilityTick == 1 || (abilityTick == 8 && phase() >= 3)) {
            triggerAnim("action", "cast");
            Vec3 start = getEyePosition().add(getLookAngle().scale(2));
            Vec3 vel = target.getEyePosition().subtract(start).normalize();
            orbs.add(new Orb(start, vel));
            sl.playSound(null, blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 3f, 0.7f);
        }
        if (abilityTick >= 12) endAbility(true);
    }

    private void tickOrbs(ServerLevel sl) {
        for (Iterator<Orb> it = orbs.iterator(); it.hasNext(); ) {
            Orb o = it.next();
            o.age++;
            LivingEntity t = getTarget();
            if (phase() >= 2 && t != null) {
                Vec3 want = t.getEyePosition().subtract(o.pos).normalize();
                o.vel = o.vel.add(want.scale(0.06)).normalize();
            }
            Vec3 next = o.pos.add(o.vel);
            BlockHitResult hit = sl.clip(new ClipContext(o.pos, next,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            boolean boom = hit.getType() != HitResult.Type.MISS;
            if (boom) next = hit.getLocation();
            AABB box = new AABB(o.pos, next).inflate(1.0);
            if (!boom && !sl.getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != this && !(e instanceof DarkEntity)).isEmpty()) {
                boom = true;
            }
            sl.sendParticles(CYAN_DUST, next.x, next.y, next.z, 6, 0.3, 0.3, 0.3, 0);
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, next.x, next.y, next.z, 4, 0.5, 0.5, 0.5, 0.2);
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, next.x, next.y, next.z, 3, 0.2, 0.2, 0.2, 0.02);
            Fx.beam(sl, o.pos, next, 0.55f, C_CYAN, 5); // светящийся шлейф
            o.pos = next;
            if (boom || o.age > 120) {
                sl.explode(this, next.x, next.y, next.z, 3.0f, Level.ExplosionInteraction.MOB);
                Fx.shake(sl, next, 1.5f, 10);
                it.remove();
            }
        }
    }

    // ------------------------------------------------------- Красный лазер

    private void doLaser(ServerLevel sl, LivingEntity target) {
        getNavigation().stop();
        getLookControl().setLookAt(target, 30f, 30f);
        if (abilityTick == 1) {
            setSkyMode(1);
            triggerAnim("action", "laser");
            laserAim = target.getEyePosition();
            sl.playSound(null, blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3f, 0.6f);
        }
        Vec3 start = getEyePosition();
        laserAim = laserAim.add(target.getEyePosition().subtract(laserAim).scale(0.07));
        Vec3 dir = laserAim.subtract(start).normalize();
        double range = 60;
        Vec3 far = start.add(dir.scale(range));
        BlockHitResult hit = sl.clip(new ClipContext(start, far,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = hit.getType() == HitResult.Type.MISS ? far : hit.getLocation();
        double len = end.subtract(start).length();
        Vec3 from = start.add(dir.scale(1.5));

        if (abilityTick <= 15) { // прицеливание: тонкая красная линия
            Fx.beam(sl, from, end, 0.12f, C_RED, 2);
        } else {
            Fx.beam(sl, from, end, 1.4f, C_RED, 2);
            if (abilityTick % 2 == 0) Fx.shake(sl, end, 0.5f, 3);
            sl.sendParticles(ParticleTypes.EXPLOSION, end.x, end.y, end.z, 1, 1, 1, 1, 0);
            sl.sendParticles(ParticleTypes.FLAME, end.x, end.y, end.z, 8, 1.2, 0.4, 1.2, 0.05);

            if (abilityTick % 10 == 0) {
                for (double d = 2; d < len; d += 2.0) {
                    Vec3 p = start.add(dir.scale(d));
                    for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class,
                            new AABB(p, p).inflate(1.3), e -> e != this && !(e instanceof DarkEntity))) {
                        e.hurt(damageSources().mobAttack(this), 6f);
                    }
                }
            }
            if (abilityTick % 4 == 0 && griefing(sl) && hit.getType() != HitResult.Type.MISS) {
                BlockPos c = BlockPos.containing(end);
                for (BlockPos bp : BlockPos.betweenClosed(c.offset(-2, -2, -2), c.offset(2, 2, 2))) {
                    BlockState st = sl.getBlockState(bp);
                    float hardness = st.getDestroySpeed(sl, bp);
                    if (!st.isAir() && hardness >= 0 && hardness < 50 && bp.distSqr(c) <= 5) {
                        sl.destroyBlock(bp, false);
                    }
                }
            }
        }
        if (abilityTick >= 90) endAbility(true);
    }

    // --------------------------------------------------------- Катаклизм

    private void doCataclysm(ServerLevel sl, LivingEntity target) {
        getNavigation().stop();
        getLookControl().setLookAt(target, 30f, 30f);
        channeling = true;
        if (abilityTick == 1) {
            setSkyMode(1);
            triggerAnim("action", "cataclysm");
            sl.playSound(null, blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 5f, 0.5f);
        }
        if (abilityTick < 80) {
            for (int i = 0; i < 6; i++) {
                double a = abilityTick * 0.4 + i * Math.PI / 3;
                double r = 4.0 * (1.0 - abilityTick / 80.0) + 0.5;
                sl.sendParticles(CYAN_DUST, getX() + Math.cos(a) * r, getY() + 2.5 + i * 0.2,
                        getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
            if (abilityTick % 4 == 0) { // растущий столб энергии в небо
                Vec3 base = position();
                Fx.beam(sl, base, base.add(0, 90, 0), 0.4f + abilityTick * 0.03f, C_CYAN, 6);
            }
            if (abilityTick % 5 == 0) Fx.shake(sl, position(), 0.3f + abilityTick * 0.012f, 8);
        }
        if (abilityTick == 80) {
            setSkyMode(2); // белая вспышка
            sl.playSound(null, blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 8f, 0.5f);
        }
        if (abilityTick == 85) {
            Vec3 c = target.position();
            sl.explode(this, c.x, c.y, c.z, 10f, true, Level.ExplosionInteraction.MOB);
            Fx.beam(sl, c, c.add(0, 120, 0), 7.0f, C_WHITE, 30);
            Fx.shake(sl, c, 5.0f, 50);
            for (int i = 0; i < 5; i++) {
                double x = c.x + (random.nextDouble() - 0.5) * 36;
                double z = c.z + (random.nextDouble() - 0.5) * 36;
                sl.explode(this, x, c.y, z, 6f, true, Level.ExplosionInteraction.MOB);
                Fx.beam(sl, new Vec3(x, c.y, z), new Vec3(x, c.y + 100, z), 3.0f, C_CYAN, 20);
            }
        }
        if (abilityTick == 92) setSkyMode(1);
        if (abilityTick >= 105) endAbility(true);
    }

    // ----------------------------------------------------- Глитч-телепорт

    private void doTeleport(ServerLevel sl, LivingEntity target) {
        if (abilityTick == 1) {
            sl.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1.5, getZ(), 60, 0.6, 1.2, 0.6, 0.5);
            Vec3 behind = target.position().subtract(target.getLookAngle().multiply(3, 0, 3));
            if (randomTeleport(behind.x, target.getY(), behind.z, true)) {
                sl.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1.5, getZ(), 60, 0.6, 1.2, 0.6, 0.5);
                sl.playSound(null, blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 2f, 0.5f);
            }
        }
        if (abilityTick >= 5) endAbility(true);
    }
}
