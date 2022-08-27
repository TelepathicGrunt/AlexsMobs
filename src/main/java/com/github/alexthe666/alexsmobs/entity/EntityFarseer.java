package com.github.alexthe666.alexsmobs.entity;

import com.github.alexthe666.alexsmobs.client.particle.AMParticleRegistry;
import com.github.alexthe666.alexsmobs.entity.ai.EntityAINearestTarget3D;
import com.github.alexthe666.alexsmobs.entity.ai.FlightMoveController;
import com.github.alexthe666.citadel.animation.Animation;
import com.github.alexthe666.citadel.animation.AnimationHandler;
import com.github.alexthe666.citadel.animation.IAnimatedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.EnumSet;

public class EntityFarseer extends Monster implements IAnimatedEntity {

    public static final Animation ANIMATION_EMERGE = Animation.create(50);
    private static final int HANDS = 4;
    private static final EntityDataAccessor<Boolean> ANGRY = SynchedEntityData.defineId(EntityFarseer.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> HAS_EMERGED = SynchedEntityData.defineId(EntityFarseer.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> MELEEING = SynchedEntityData.defineId(EntityFarseer.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> LASER_ENTITY_ID = SynchedEntityData.defineId(EntityFarseer.class, EntityDataSerializers.INT);
    public final double[][] positions = new double[64][4];
    public final float[] claspProgress = new float[HANDS];
    public final float[] prevClaspProgress = new float[HANDS];
    public final float[] strikeProgress = new float[HANDS];
    public final float[] prevStrikeProgress = new float[HANDS];
    public final boolean[] isStriking = new boolean[HANDS];
    public int posPointer = -1;
    public float angryProgress;
    public float prevAngryProgress;
    public Vec3 angryShakeVec = Vec3.ZERO;
    private float faceCameraProgress;
    private float prevFaceCameraProgress;
    private LivingEntity laserTargetEntity;
    private int laserTime = 0;
    public double laserDistance = 0;
    private int claspingHand = -1;
    private int animationTick;
    private Animation currentAnimation;
    private int meleeCooldown = 0;

    protected EntityFarseer(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
    }

    public static AttributeSupplier.Builder bakeAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 24D).add(Attributes.ARMOR, 2.0D).add(Attributes.FLYING_SPEED, (double)0.1F).add(Attributes.ATTACK_DAMAGE, 4.5D).add(Attributes.MOVEMENT_SPEED, 0.3F);
    }

    public boolean isNoGravity() {
        return true;
    }

    public boolean causeFallDamage(float distance, float damageMultiplier) {
        return false;
    }

    protected void checkFallDamage(double y, boolean onGroundIn, BlockState state, BlockPos pos) {
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation flyingpathnavigation = new FlyingPathNavigation(this, level);
        flyingpathnavigation.setCanOpenDoors(false);
        flyingpathnavigation.setCanFloat(true);
        flyingpathnavigation.setCanPassDoors(true);
        return flyingpathnavigation;
    }

    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new AttackGoal());
        this.goalSelector.addGoal(3, new RandomFlyGoal(this));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 10));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new EntityAINearestTarget3D(this, Pig.class, 15, false, false, null));
    }

    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(HAS_EMERGED, false);
        this.entityData.define(MELEEING, false);
        this.entityData.define(ANGRY, false);
        this.entityData.define(LASER_ENTITY_ID, -1);
    }

    public boolean isAngry() {
        return this.entityData.get(ANGRY).booleanValue();
    }

    public void setAngry(boolean angry) {
        this.entityData.set(ANGRY, Boolean.valueOf(angry));
    }

    public boolean hasLaser() {

        return this.entityData.get(LASER_ENTITY_ID) != -1 && this.getAnimation() != EntityFarseer.ANIMATION_EMERGE;
    }

    @Nullable
    public LivingEntity getLaserTarget() {
        if (!this.hasLaser()) {
            return null;
        } else if (this.level.isClientSide) {
            if (this.laserTargetEntity != null) {
                return this.laserTargetEntity;
            } else {
                Entity lvt_1_1_ = this.level.getEntity(this.entityData.get(LASER_ENTITY_ID));
                if (lvt_1_1_ instanceof LivingEntity) {
                    this.laserTargetEntity = (LivingEntity) lvt_1_1_;
                    return this.laserTargetEntity;
                } else {
                    return null;
                }
            }
        } else {
            return this.getTarget();
        }
    }

    public boolean hasEmerged() {
        return this.entityData.get(HAS_EMERGED).booleanValue();
    }

    public void setHasEmerged(boolean emerged) {
        this.entityData.set(HAS_EMERGED, Boolean.valueOf(emerged));
    }

    public void tick() {
        super.tick();
        prevFaceCameraProgress = faceCameraProgress;
        if (this.getAnimation() == ANIMATION_EMERGE) {
            this.setHasEmerged(true);
            faceCameraProgress = 1F;
        } else if (faceCameraProgress > 0.0F) {
            faceCameraProgress = Math.max(0, faceCameraProgress - 0.2F);
        }
        prevAngryProgress = angryProgress;
        for (int i = 0; i < HANDS; i++) {
            prevClaspProgress[i] = claspProgress[i];
            prevStrikeProgress[i] = strikeProgress[i];
        }
        if (this.posPointer < 0) {
            for (int i = 0; i < this.positions.length; ++i) {
                this.positions[i][0] = this.getX();
                this.positions[i][1] = this.getY();
                this.positions[i][2] = this.getZ();
                this.positions[i][3] = this.yBodyRot;
            }
        }
        if (++this.posPointer == this.positions.length) {
            this.posPointer = 0;
        }
        this.positions[this.posPointer][0] = this.getX();
        this.positions[this.posPointer][1] = this.getY();
        this.positions[this.posPointer][2] = this.getZ();
        this.positions[this.posPointer][3] = this.yBodyRot;
        if (this.isAngry() && angryProgress < 5F) {
            angryProgress++;
        }
        if (!this.isAngry() && angryProgress > 0F) {
            angryProgress--;
        }
        if (random.nextInt(isAngry() ? 12 : 40) == 0 && claspingHand == -1) {
            int i = Mth.clamp(random.nextInt(HANDS), 0, 3);
            if (claspProgress[i] == 0) {
                claspingHand = i;
            }
        }
        if (claspingHand >= 0) {
            if (claspProgress[claspingHand] < 5F) {
                claspProgress[claspingHand]++;
            } else {
                claspingHand = -1;
            }
        } else {
            for (int i = 0; i < HANDS; i++) {
                if (claspProgress[i] > 0) {
                    claspProgress[i]--;
                }
            }
        }

        if (this.isAngry()) {
            angryShakeVec = new Vec3(random.nextFloat() - 0.5F, random.nextFloat() - 0.5F, random.nextFloat() - 0.5F);
        } else {
            angryShakeVec = Vec3.ZERO;
        }
        if (!this.hasEmerged()) {
            this.setAnimation(ANIMATION_EMERGE);
        }
        AnimationHandler.INSTANCE.updateAnimations(this);
        if (this.getAnimation() == ANIMATION_EMERGE && level.isClientSide) {
            this.level.addParticle(AMParticleRegistry.STATIC_SPARK.get(), this.getRandomX(0.75F), this.getRandomY(), this.getRandomZ(0.75F), (this.getRandom().nextFloat() - 0.5F) * 0.2F, this.getRandom().nextFloat() * 0.2F, (this.getRandom().nextFloat() - 0.5F) * 0.2F);
        }
        LivingEntity target = this.getTarget();
        if (target != null) {
            if(this.entityData.get(MELEEING)){
                if(meleeCooldown == 0){
                    meleeCooldown = 5;
                    int i = random.nextInt(HANDS);
                    this.isStriking[i] = true;
                    this.level.broadcastEntityEvent(this, (byte)(40 + i));
                }
            }
        }

        for(int i = 0; i < HANDS; i++){
            if(!this.isStriking[i] || this.entityData.get(MELEEING)){
                if(strikeProgress[i] > 0F){
                    strikeProgress[i]--;
                }
            }else if(this.isStriking[i]){
                if(strikeProgress[i] < 5F){
                    strikeProgress[i]++;
                }
                if(strikeProgress[i] == 5F){
                    isStriking[i] = false;
                    this.level.broadcastEntityEvent(this, (byte)(44 + i));
                    if(distanceTo(target) <= 4F){
                        target.hurt(DamageSource.mobAttack(this), 4);
                    }
                }
            }
        }

        if (this.hasLaser()) {
            LivingEntity livingentity = this.getLaserTarget();
            if (livingentity != null) {
                Vec3 hit = this.calculateLaserHit(livingentity.getEyePosition());
                laserDistance = hit.distanceTo(this.getEyePosition());
                this.getLookControl().setLookAt(livingentity, 90.0F, 90.0F);
                this.getLookControl().tick();
                double d5 = 1F;
                double d0 = hit.x;
                double d1 = hit.y;
                double d2 = hit.z;
                double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
                d0 = d0 / d3;
                d1 = d1 / d3;
                d2 = d2 / d3;
                double d4 = this.random.nextDouble();
                while (d4 < d3) {
                    d4 += 2F + this.random.nextDouble();
                    this.level.addParticle(AMParticleRegistry.STATIC_SPARK.get(), this.getX() + d0 * d4, this.getEyeY() + d1 * d4, this.getZ() + d2 * d4, (this.getRandom().nextFloat() - 0.5F) * 0.2F, this.getRandom().nextFloat() * 0.2F, (this.getRandom().nextFloat() - 0.5F) * 0.2F);
                }
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    public void handleEntityEvent(byte id) {
        if (id >= 40 && id <= 43) {
            int i = id - 40;
            isStriking[i] = true;
        }else if (id >= 44 && id <= 48) {
            int i = id - 44;
            isStriking[i] = false;
        }else{
            super.handleEntityEvent(id);
        }
    }

    public double getLatencyVar(int pointer, int index, float partialTick) {
        if (this.isDeadOrDying()) {
            partialTick = 1.0F;
        }
        int i = this.posPointer - pointer & 63;
        int j = this.posPointer - pointer - 1 & 63;
        double d0 = this.positions[j][index];
        double d1 = Mth.wrapDegrees(this.positions[i][index] - d0);
        return d0 + d1 * partialTick;
    }

    public Vec3 getLatencyOffsetVec(int offset, float partialTick) {
        double d0 = Mth.lerp(partialTick, this.xOld, this.getX());
        double d1 = Mth.lerp(partialTick, this.yOld, this.getY());
        double d2 = Mth.lerp(partialTick, this.zOld, this.getZ());
        float renderYaw = (float) this.getLatencyVar(0, 3, partialTick);
        return new Vec3(this.getLatencyVar(offset, 0, partialTick) - d0, this.getLatencyVar(offset, 1, partialTick) - d1, this.getLatencyVar(offset, 2, partialTick) - d2).yRot(renderYaw * ((float) Math.PI / 180F));
    }

    public Vec3 calculateAfterimagePos(float partialTick, boolean flip) {
        float f = (partialTick + this.tickCount) * 0.3F;
        float f1 = 0.1F;
        Vec3 v = new Vec3((float) Math.sin(f) * f1, (float) Math.cos(f - Math.PI / 2) * f1, -(float) Math.cos(f) * f1);
        if (flip) {
            return new Vec3(v.z, -v.y, v.x);
        }
        return v;
    }

    @Override
    public int getAnimationTick() {
        return animationTick;
    }

    @Override
    public void setAnimationTick(int i) {
        animationTick = i;
    }

    @Override
    public Animation getAnimation() {
        return currentAnimation;
    }

    @Override
    public void setAnimation(Animation animation) {
        this.currentAnimation = animation;
    }


    @Override
    public Animation[] getAnimations() {
        return new Animation[]{ANIMATION_EMERGE};
    }

    public int getPortalFrame() {
        if (this.getAnimation() == ANIMATION_EMERGE) {
            if (this.getAnimationTick() < 10) {
                return 0;
            } else if (this.getAnimationTick() < 20) {
                return 1;
            } else if (this.getAnimationTick() < 30) {
                return 2;
            } else if (this.getAnimationTick() > 40) {
                int i = 50 - this.getAnimationTick();
                return i < 6 ? i < 3 ? 0 : 1 : 2;
            } else {
                return 3;
            }
        }
        return 0;
    }

    public float getPortalOpacity(float partialTicks) {
        if (this.getAnimation() == ANIMATION_EMERGE) {
            float tick = this.getAnimationTick() - 1 + partialTicks;
            if (tick < 5F) {
                return tick / 5F;
            }
            return 1.0F;
        }
        return 0.0F;
    }

    public float getFarseerOpacity(float partialTicks) {
        if (this.getAnimation() == ANIMATION_EMERGE) {
            float tick = this.getAnimationTick() - 1 + partialTicks;
            float prog = tick / (float) ANIMATION_EMERGE.getDuration();
            return prog > 0.5F ? (prog - 0.5F) / 0.5F : 0F;
        }
        return 1.0F;
    }

    public float getFacingCameraAmount(float partialTicks) {
        return prevFaceCameraProgress + (faceCameraProgress - prevFaceCameraProgress) * partialTicks;
    }

    public boolean isEffectiveAi() {
        return super.isEffectiveAi() && this.getAnimation() != ANIMATION_EMERGE && this.hasEmerged();
    }

    private Vec3 calculateLaserHit(Vec3 target){
        Vec3 eyes = new Vec3(this.getX(), this.getEyeY(), this.getZ());
        HitResult hitResult = this.level.clip(new ClipContext(eyes, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hitResult.getLocation();
    }

    public boolean isTargetBlocked(Vec3 target) {
        Vec3 Vector3d = new Vec3(this.getX(), this.getEyeY(), this.getZ());
        return this.level.clip(new ClipContext(Vector3d, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS;
    }

    public void travel(Vec3 vec3) {
        if (this.isEffectiveAi() || this.isControlledByLocalInstance()) {
            if (this.isInWater()) {
                this.moveRelative(0.02F, vec3);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale((double)0.8F));
            } else if (this.isInLava()) {
                this.moveRelative(0.02F, vec3);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.5D));
            } else {
                this.moveRelative(this.getSpeed(), vec3);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale((double)0.91F));
            }
        }

        this.calculateEntityAnimation(this, false);
    }

    private class RandomFlyGoal extends Goal {
        private final EntityFarseer parentEntity;
        private BlockPos target = null;

        public RandomFlyGoal(EntityFarseer mosquito) {
            this.parentEntity = mosquito;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        public boolean canUse() {
            if (this.parentEntity.getNavigation().isDone() && this.parentEntity.getTarget() == null && this.parentEntity.getRandom().nextInt(4) == 0) {
                target = getBlockInViewFarseer();
                if (target != null) {
                    this.parentEntity.getMoveControl().setWantedPosition(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D, 1.0D);
                    return true;
                }
            }
            return false;
        }

        public boolean canContinueToUse() {
            return target != null && parentEntity.getTarget() == null;
        }

        public void stop() {
            target = null;
        }

        public void tick() {
            if (target != null) {
                this.parentEntity.getMoveControl().setWantedPosition(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D, 1.0D);
                if (parentEntity.distanceToSqr(Vec3.atCenterOf(target)) < 4D || this.parentEntity.horizontalCollision) {
                    target = null;
                }
            }
        }

        private BlockPos getFarseerGround(BlockPos in) {
            BlockPos position = new BlockPos(in.getX(), parentEntity.getY(), in.getZ());
            while (position.getY() < 256 && !parentEntity.level.getFluidState(position).isEmpty()) {
                position = position.above();
            }
            while (position.getY() > 1 && parentEntity.level.isEmptyBlock(position)) {
                position = position.below();
            }
            return position;
        }

        public BlockPos getBlockInViewFarseer() {
            float radius = 5 + parentEntity.getRandom().nextInt(10);
            float neg = parentEntity.getRandom().nextBoolean() ? 1 : -1;
            float renderYawOffset = parentEntity.getYRot();
            float angle = (0.01745329251F * renderYawOffset) + 3.15F * (parentEntity.getRandom().nextFloat() * neg);
            double extraX = radius * Mth.sin((float) (Math.PI + angle));
            double extraZ = radius * Mth.cos(angle);
            BlockPos radialPos = new BlockPos(parentEntity.getX() + extraX, parentEntity.getY(), parentEntity.getZ() + extraZ);
            BlockPos ground = getFarseerGround(radialPos);
            if (ground.getY() <= 1) {
                ground = ground.above(70 + parentEntity.random.nextInt(4));
            } else {
                ground = ground.above(2 + parentEntity.random.nextInt(2));
            }
            if (!parentEntity.isTargetBlocked(Vec3.atCenterOf(ground.above()))) {
                return ground;
            }
            return null;
        }

    }

    private class AttackGoal extends Goal {

        private boolean attackDecision;
        private int timeSinceLastSuccessfulAttack = 0;
        private int laserCooldown = 0;
        private int laserUseTime = 0;

        public AttackGoal() {
        }

        @Override
        public boolean canUse() {
            return EntityFarseer.this.getTarget() != null && EntityFarseer.this.getTarget().isAlive();
        }

        public void stop(){
            this.laserCooldown = 0;
            this.laserUseTime = 0;
            attackDecision = EntityFarseer.this.getRandom().nextBoolean();
            EntityFarseer.this.entityData.set(LASER_ENTITY_ID, -1);
            timeSinceLastSuccessfulAttack = 0;
            EntityFarseer.this.setAngry(false);
        }

        public void tick(){
            super.tick();
            LivingEntity target = EntityFarseer.this.getTarget();
            if(laserCooldown > 0){
                laserCooldown--;
            }
            timeSinceLastSuccessfulAttack++;
            if(timeSinceLastSuccessfulAttack > 200){
                timeSinceLastSuccessfulAttack = 0;
                attackDecision = !attackDecision;
            }
            if(target != null){
                double dist = EntityFarseer.this.distanceToSqr(target);
                boolean canSee = EntityFarseer.this.hasLineOfSight(target);
                if(this.laserCooldown == 0 && attackDecision){
                    EntityFarseer.this.setAngry(true);
                    EntityFarseer.this.entityData.set(LASER_ENTITY_ID, target.getId());
                    laserUseTime++;
                    if(laserUseTime > 40){
                        laserUseTime = 0;
                        laserCooldown = 40 + random.nextInt(40);
                        EntityFarseer.this.entityData.set(LASER_ENTITY_ID, -1);
                        attackDecision = EntityFarseer.this.getRandom().nextBoolean();
                    }
                    if(laserUseTime % 10 == 0 && canSee){
                        target.hurt(DamageSource.MAGIC, 2);
                    }
                    EntityFarseer.this.lookAt(target, 180F, 10F);
                    if(dist < 10F){
                        EntityFarseer.this.getNavigation().stop();
                        EntityFarseer.this.moveControl.strafe(-0.3F, 0);
                    }else{
                        EntityFarseer.this.getNavigation().moveTo(target, 1F);
                    }
                }else{
                    if(!canSee && dist > 10){
                        EntityFarseer.this.setAngry(false);
                    }
                    if(EntityFarseer.this.hasLaser()){
                        EntityFarseer.this.entityData.set(LASER_ENTITY_ID, -1);
                    }
                    EntityFarseer.this.entityData.set(MELEEING, dist < 4F);
                    EntityFarseer.this.getNavigation().moveTo(target, 1F);
                    if(dist < 4F){
                        EntityFarseer.this.lookAt(target, 180F, 10F);
                        attackDecision = EntityFarseer.this.getRandom().nextBoolean();
                    }
                }
            }
        }
    }
}
