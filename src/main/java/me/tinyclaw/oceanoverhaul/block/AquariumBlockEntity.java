package me.tinyclaw.oceanoverhaul.block;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * BlockEntity backing the {@link AquariumBlock} — stores the single creature captured inside
 * the tank as an {@link EntityType} + an {@code int} variant. {@code storedType == null} means
 * the tank is empty.
 *
 * <p><b>NBT contract (load-bearing).</b> The two keys {@code StoredEntity} (string entity id) and
 * {@code StoredVariant} (int) are the public on-disk/sync contract: the client {@code
 * AquariumBlockEntityRenderer} reads them after sync, and the render-probe {@code /data merge}
 * scene writes them directly to stock a tank for screenshots. Don't rename them without updating
 * both. An empty tank writes neither key.</p>
 *
 * <p><b>1.21.1 NBT API.</b> Persistence uses {@code writeNbt(NbtCompound, RegistryWrapper.WrapperLookup)}
 * / {@code readNbt(NbtCompound, RegistryWrapper.WrapperLookup)} — the pre-1.21.5 API (there is no
 * {@code ReadView}/{@code WriteView} here). Client sync rides the vanilla BlockEntity update path:
 * {@link #toUpdatePacket()} returns the 1-arg {@code BlockEntityUpdateS2CPacket.create(this)} and
 * {@link #toInitialChunkDataNbt} returns {@link #createNbt} (which calls {@link #writeNbt}).</p>
 *
 * <p><b>Defensive read.</b> {@code Registries.ENTITY_TYPE.get(Identifier)} returns the registry's
 * DEFAULT entry (NOT null) for an unknown id, so a foreign/garbage {@code StoredEntity} would
 * otherwise render as a default entity (e.g. a pig) inside the tank. {@link #readNbt} therefore
 * treats any resolved type that isn't one of this mod's two bucketable mobs (Reef Fish / Jellyfish)
 * as empty.</p>
 */
public class AquariumBlockEntity extends BlockEntity {

	/** NBT key for the stored entity id (string). Part of the public sync/probe contract. */
	private static final String KEY_STORED_ENTITY = "StoredEntity";
	/** NBT key for the stored variant (int). Part of the public sync/probe contract. */
	private static final String KEY_STORED_VARIANT = "StoredVariant";

	/** The captured creature's type, or {@code null} when the tank is empty. */
	private EntityType<?> storedType;
	/** The captured creature's variant index (0 for variantless creatures like the reef fish). */
	private int storedVariant;

	public AquariumBlockEntity(BlockPos pos, BlockState state) {
		super(OceanOverhaul.AQUARIUM_BLOCK_ENTITY, pos, state);
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		if (storedType != null) {
			nbt.putString(KEY_STORED_ENTITY, EntityType.getId(storedType).toString());
			nbt.putInt(KEY_STORED_VARIANT, storedVariant);
		}
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		if (nbt.contains(KEY_STORED_ENTITY, NbtElement.STRING_TYPE)) {
			EntityType<?> resolved =
					Registries.ENTITY_TYPE.get(Identifier.of(nbt.getString(KEY_STORED_ENTITY)));
			// Registries.ENTITY_TYPE.get returns the DEFAULT entry (not null) for an unknown id,
			// so only accept this mod's two bucketable mobs; anything else => empty tank.
			storedType = (resolved == OceanOverhaul.REEF_FISH || resolved == OceanOverhaul.JELLYFISH)
					? resolved : null;
			storedVariant = nbt.getInt(KEY_STORED_VARIANT);
		} else {
			storedType = null;
			storedVariant = 0;
		}
	}

	/** @return the captured creature's type, or {@code null} when empty. */
	public EntityType<?> storedType() {
		return storedType;
	}

	/** @return the captured creature's variant index (0 when empty or variantless). */
	public int storedVariant() {
		return storedVariant;
	}

	/** Store a creature in the tank (overwrites any current contents) and sync to clients. */
	public void setStored(EntityType<?> type, int variant) {
		this.storedType = type;
		this.storedVariant = variant;
		sync();
	}

	/** Empty the tank and sync to clients. */
	public void clear() {
		this.storedType = null;
		this.storedVariant = 0;
		sync();
	}

	/** markDirty + a block-update so the new BE state reaches the client renderer immediately. */
	private void sync() {
		markDirty();
		if (this.world != null) {
			this.world.updateListeners(this.pos, getCachedState(), getCachedState(), 3);
		}
	}

	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
		return createNbt(registries);
	}
}
