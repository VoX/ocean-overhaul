package me.tinyclaw.oceanstarter.item;

import me.tinyclaw.oceanstarter.OceanStarter;
import me.tinyclaw.oceanstarter.entity.HarpoonEntity;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * The Harpoon item — Feature 3, Part A.
 *
 * <p><b>INSTANT throw, no draw-back.</b> Overriding {@link #use} (and ONLY {@code use}) launches a
 * {@link HarpoonEntity} immediately on right-click. This is a deliberate, lower-risk choice over a
 * trident-style charge/draw: javap shows {@code Item.getMaxUseTime} is the 2-arg form
 * {@code getMaxUseTime(ItemStack, LivingEntity)} in 1.21.1 build.3, so the conventional 1-arg
 * override (used by both source proposals) would NOT actually override and the draw-back would
 * silently never fire. {@code getMaxUseTime}/{@code getUseAction}/{@code onStoppedUsing} are
 * therefore left untouched on purpose, and the instant path is directly gametest-drivable by
 * calling {@code use()}.</p>
 *
 * <p>The thrown harpoon returns to the player via the loyalty loop in {@link HarpoonEntity#tick()},
 * so decrementing the held stack in survival is not a loss — the physical harpoon simply becomes the
 * entity and comes back.</p>
 */
public class HarpoonItem extends Item {

	public HarpoonItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);

		// Client side: report success so the swing/animation plays; the entity is spawned on the
		// server (where world.spawnEntity actually adds it).
		if (world.isClient) {
			return TypedActionResult.success(stack);
		}

		HarpoonEntity harpoon = new HarpoonEntity(world, player, stack);
		// ProjectileEntity.setVelocity(shooter, pitch, yaw, roll, speed, divergence): throw along the
		// player's look direction (speed 2.5, divergence 1.0 — same shape as a thrown trident).
		harpoon.setVelocity(player, player.getPitch(), player.getYaw(), 0.0F, 2.5F, 1.0F);
		world.spawnEntity(harpoon);

		world.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ITEM_TRIDENT_THROW, player.getSoundCategory(), 1.0F, 1.0F);

		// Survival-safe durability tick on the thrown harpoon (mirrors a thrown trident taking wear).
		stack.damage(1, player, EquipmentSlot.MAINHAND);

		// In survival the held harpoon becomes the entity (and returns via the loyalty loop); in
		// creative the abilities flag means we leave the stack untouched.
		if (!player.getAbilities().creativeMode) {
			stack.decrement(1);
		}

		return TypedActionResult.success(stack);
	}
}
