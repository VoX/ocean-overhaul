package me.tinyclaw.oceanoverhaul.entity;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnLocation;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.control.AquaticMoveControl;
import net.minecraft.entity.ai.goal.AnimalMateGoal;
import net.minecraft.entity.ai.goal.EscapeDangerGoal;
import net.minecraft.entity.ai.goal.FollowParentGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.TemptGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.ai.pathing.AmphibiousSwimNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

/**
 * The Shore Crab — the mod's first walking and first breedable mob.
 *
 * <p>A small amphibious {@link AnimalEntity} that scuttles on beach sand and the
 * adjacent shallow seafloor. The locomotion is the axolotl wiring on a goal-based
 * brain (the turtle precedent): {@link AmphibiousSwimNavigation} + an
 * {@link AquaticMoveControl} with the exact axolotl numbers (85, 10, 0.10F, 0.50F,
 * buoyant=false), a zero {@link PathNodeType#WATER} penalty, and deliberately NO
 * SwimGoal — the crab sinks and walks the submerged floor instead of bobbing at the
 * surface. Water breathing is data, not code: {@code LivingEntity.canBreatheInWater()}
 * is final in 1.21.1 and reads the {@code minecraft:can_breathe_under_water}
 * entity-type tag, which the mod's datapack appends this type to.</p>
 *
 * <p>The shoreline loiter is {@link #getPathfindingFavor(BlockPos, WorldView)}
 * favoring sand floors. Wander's primary mechanism ({@code FuzzyTargeting.validate})
 * refuses positions inside water, so those picks are always dry; only the rare
 * {@code NoPenaltyTargeting} fallback (p=0.001 on land; always when in water with no
 * dry target within 15 blocks) can select a submerged target, because its sole gate
 * is a nonzero path penalty and this class zeroes the WATER penalty. In practice the
 * crab all but never walks into the sea by choice — it ends up wading via spawn
 * placement, kelp temptation, panic, leads or shoves — and a wading crab's next
 * wander pick is almost always a dry sand target, so it climbs back ashore on its
 * own. Breeding is vanilla {@code AnimalEntity} plumbing keyed on kelp (its first
 * husbandry use); babies drop nothing and pay no XP for free
 * ({@code shouldDropLoot()}/{@code shouldDropXp()} are both {@code !isBaby()}).</p>
 *
 * <p>No custom NBT, no DataTracker, no variants, not bucketable — the entire point
 * of riding {@code AnimalEntity} is that everything persists/inherits.</p>
 */
public class ShoreCrab extends AnimalEntity {

	public ShoreCrab(EntityType<? extends ShoreCrab> entityType, World world) {
		super(entityType, world);
		this.setPathfindingPenalty(PathNodeType.WATER, 0.0F);   // water is not an obstacle
		this.moveControl = new AquaticMoveControl(this, 85, 10, 0.10F, 0.50F, false);
	}

	@Override
	protected EntityNavigation createNavigation(World world) {
		return new AmphibiousSwimNavigation(this, world);       // (MobEntity, World) ctor verified
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		// MOVEMENT_SPEED 0.5: AquaticMoveControl.tick MULTIPLIES this attribute by its
		// land/water factors (bytecode: movementSpeed = goalSpeed × attribute × factor),
		// and the axolotl's 0.10/0.50 factors are tuned against its attribute of 1.0.
		// 0.5 composes to 0.25 effective on land (the chicken's land speed — the
		// turtle/chicken attribute class the design targets) and 0.05 in water (half
		// the axolotl's 0.10 swim — the visibly slower wade).
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 8.0)        // 4 hearts
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.5)    // ×0.50 land / ×0.10 water (§3.4)
				.add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.0)       // turtle value, beach terraces
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 16.0);
	}

	@Override
	protected void initGoals() {
		// No SwimGoal (bottom-walker by design) and no water-seeking goal of any kind:
		// FuzzyTargeting.validate rejects water positions, so wander picks are dry —
		// except the rare NoPenaltyTargeting fallback (p=0.001 on land; always when in
		// water with no dry target within 15 blocks), which CAN select water because
		// the WATER penalty is zeroed. Waterline traffic is therefore one-directional
		// by AI in all but those edge cases, and player/world driven into the water.
		// No targetSelector entries: passive.
		this.goalSelector.add(0, new EscapeDangerGoal(this, 1.4));                 // panic when hurt
		this.goalSelector.add(1, new AnimalMateGoal(this, 1.0));                   // love-mode pairing
		this.goalSelector.add(2, new TemptGoal(this, 1.1, stack -> stack.isOf(Items.KELP), false));
		this.goalSelector.add(3, new FollowParentGoal(this, 1.1));                 // babies trail adults
		this.goalSelector.add(4, new WanderAroundFarGoal(this, 1.0));              // beach wandering
		this.goalSelector.add(5, new LookAtEntityGoal(this, PlayerEntity.class, 6.0F));
		this.goalSelector.add(6, new LookAroundGoal(this));
	}

	@Override
	public float getPathfindingFavor(BlockPos pos, WorldView world) {
		// AnimalEntity's version favors GRASS_BLOCK 10.0 (bytecode); the crab's terrain is sand.
		// Wander keeps the best-of-10 candidates by this favor (FuzzyPositions.guessBest),
		// so sand-floored spots beat grass/stone (and water) floors, pinning wandering
		// to the beach. FuzzyTargeting candidates are always dry (see class javadoc).
		return world.getBlockState(pos.down()).isIn(BlockTags.SAND) ? 10.0F : 0.0F;
	}

	/**
	 * The axolotl's spawn-gate override: {@code MobEntity}'s default body is
	 * {@code !world.containsFluid(getBoundingBox()) && world.doesNotIntersectEntities(this)}
	 * (bytecode) and runs as the LAST gate of every natural/chunk-gen spawn — the default
	 * fluid veto would discard every submerged success of {@link #SPAWN_LOCATION}'s
	 * underwater branch. The axolotl's bytecode drops the fluid clause; so does this.
	 */
	@Override
	public boolean canSpawn(WorldView world) {
		return world.doesNotIntersectEntities(this);
	}

	@Override
	public boolean isPushedByFluids() {
		// Axolotl bytecode: iconst_0 — currents and bubble columns don't shove a crab
		// gripping the floor.
		return false;
	}

	// =====================================================================
	// Breeding — kelp, live young, no eggs (everything else is inherited
	// AnimalEntity/PassiveEntity plumbing: love mode, the 5-min re-breed
	// cooldown, baby auto-halved hitbox, feed-baby-to-grow).
	// =====================================================================

	@Override
	public boolean isBreedingItem(ItemStack stack) {
		return stack.isOf(Items.KELP);
	}

	@Override
	public ShoreCrab createChild(ServerWorld world, PassiveEntity entity) {
		return OceanOverhaul.SHORE_CRAB.create(world);
	}

	// =====================================================================
	// Voice — turtle shell-scrape family + spider-step legs, vanilla constants
	// only (no new sound files). Baby variants follow the turtle's own
	// baby-switch pattern. No getAmbientSound override: crabs are quiet
	// (MobEntity's default returns null — the jellyfish silence precedent).
	// =====================================================================

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		// Skittery many-legs tick; quieter + higher than a spider so it reads small
		// (the turtle plays its step at 0.15F — same idea).
		this.playSound(SoundEvents.ENTITY_SPIDER_STEP, 0.10F, 1.4F);
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return this.isBaby() ? SoundEvents.ENTITY_TURTLE_HURT_BABY : SoundEvents.ENTITY_TURTLE_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return this.isBaby() ? SoundEvents.ENTITY_TURTLE_DEATH_BABY : SoundEvents.ENTITY_TURTLE_DEATH;
	}

	// =====================================================================
	// Spawning — custom SpawnLocation (ON_GROUND ∪ shallow-water-floor) +
	// the tide-band predicate. SpawnLocationTypes.ON_GROUND can never pass a
	// submerged position (SpawnHelper.isClearForSpawn returns false for any
	// non-empty FluidState) and IN_WATER never passes a dry one, so "beach
	// sand + submerged shore sand" needs this union.
	// =====================================================================

	/** ON_GROUND for dry sand, IN_WATER-style for the submerged shore floor — both branches are
	 *  the bytecode-verified bodies of the corresponding SpawnLocationTypes lambdas, unioned.
	 *  Keeps SpawnLocation's default adjustPosition (identity): vanilla ON_GROUND's override
	 *  nudges the pos DOWN when the block below is LAND-pathable, which a sand floor never is,
	 *  so identity is equivalent here. */
	public static final SpawnLocation SPAWN_LOCATION = (world, pos, type) -> {
		if (type == null || !world.getWorldBorder().contains(pos)) {
			return false;   // vanilla bytecode: BOTH branches return false on a null type too
		}                   // (proceeding would NPE inside allowsSpawning/isClearForSpawn)
		BlockPos down = pos.down();
		if (!world.getBlockState(down).allowsSpawning(world, down, type)) {
			return false;                                    // solid spawnable floor (ON_GROUND clause)
		}
		BlockState state = world.getBlockState(pos);
		FluidState fluid = state.getFluidState();
		if (fluid.isIn(FluidTags.WATER)) {
			// submerged branch = vanilla IN_WATER: water here, no solid lid overhead
			return !world.getBlockState(pos.up()).isSolidBlock(world, pos.up());
		}
		// dry branch = vanilla ON_GROUND: 2-block clearance via the public SpawnHelper check
		BlockPos up = pos.up();
		return SpawnHelper.isClearForSpawn(world, pos, state, fluid, type)
				&& SpawnHelper.isClearForSpawn(world, up, world.getBlockState(up),
						world.getBlockState(up).getFluidState(), type);
	};

	/** Sand-or-gravel floor, tide-line Y band, dry or ≤~3-block-shallow water. No light gate —
	 *  crabs skitter at night (lurker precedent; CREATURE group, so no monster-cap interplay). */
	public static boolean canSpawn(EntityType<ShoreCrab> type, ServerWorldAccess world,
			SpawnReason reason, BlockPos pos, Random random) {
		BlockState floor = world.getBlockState(pos.down());
		boolean floorOk = floor.isIn(BlockTags.SAND) || floor.isOf(Blocks.GRAVEL);
		// Tide band: from 3 below sea level (shallow shelf) to 4 above (turtle's exact upper
		// bound — TurtleEntity.canSpawn bytecode: getY() < getSeaLevel() + 4).
		boolean bandOk = pos.getY() >= world.getSeaLevel() - 3
				&& pos.getY() < world.getSeaLevel() + 4;
		return floorOk && bandOk;
	}
}
