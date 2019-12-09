package com.simibubi.create.modules.logistics.block.belts;

import com.simibubi.create.AllTileEntities;
import com.simibubi.create.foundation.block.SyncedTileEntity;
import com.simibubi.create.foundation.utility.VecHelper;
import com.simibubi.create.modules.contraptions.relays.belt.ItemHandlerBeltSegment;
import com.simibubi.create.modules.logistics.block.IHaveFilter;
import com.simibubi.create.modules.logistics.block.IInventoryManipulator;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.particles.ItemParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

public class BeltFunnelTileEntity extends SyncedTileEntity
		implements ITickableTileEntity, IInventoryManipulator, IHaveFilter {

	private LazyOptional<IItemHandler> inventory;
	protected boolean waitingForInventorySpace;
	private boolean initialize;
	private ItemStack filter;

	private ItemStack justEaten;

	public BeltFunnelTileEntity() {
		super(AllTileEntities.BELT_FUNNEL.type);
		inventory = LazyOptional.empty();
		filter = ItemStack.EMPTY;
	}

	@Override
	public void read(CompoundNBT compound) {
		filter = ItemStack.read(compound.getCompound("Filter"));
		waitingForInventorySpace = compound.getBoolean("Waiting");
		super.read(compound);
	}

	@Override
	public CompoundNBT write(CompoundNBT compound) {
		compound.put("Filter", filter.serializeNBT());
		compound.putBoolean("Waiting", waitingForInventorySpace);
		return super.write(compound);
	}

	@Override
	public void onLoad() {
		initialize = true;
	}

	@Override
	public CompoundNBT writeToClient(CompoundNBT tag) {
		if (justEaten != null) {
			tag.put("Nom", justEaten.serializeNBT());
			justEaten = null;
		}
		return super.writeToClient(tag);
	}

	@Override
	public void readClientUpdate(CompoundNBT tag) {
		super.readClientUpdate(tag);
		if (!waitingForInventorySpace)
			neighborChanged();
		if (tag.contains("Nom"))
			justEaten = ItemStack.read(tag.getCompound("Nom"));
	}

	@Override
	public BlockPos getInventoryPos() {
		return pos.offset(getBlockState().get(BlockStateProperties.HORIZONTAL_FACING));
	}

	@Override
	public LazyOptional<IItemHandler> getInventory() {
		return inventory;
	}

	@Override
	public void tick() {
		if (initialize && hasWorld()) {
			neighborChanged();
			initialize = false;
		}
		if (world.isRemote && justEaten != null) {
			spawnParticles(justEaten);
			justEaten = null;
		}
	}

	@Override
	public void setInventory(LazyOptional<IItemHandler> inventory) {
		this.inventory = inventory;
	}

	@Override
	public void neighborChanged() {
		IInventoryManipulator.super.neighborChanged();
		waitingForInventorySpace = false;
		if (!world.isRemote)
			sendData();
	}

	public ItemStack tryToInsert(ItemStack stack) {
		if (!inventory.isPresent())
			return stack;
		if (waitingForInventorySpace && !(inventory.orElse(null) instanceof ItemHandlerBeltSegment))
			return stack;
		if (!filter.isEmpty() && !ItemStack.areItemsEqual(filter, stack))
			return stack;

		IItemHandler inv = inventory.orElse(null);
		ItemStack inserted = stack.copy();
		int amountToExtract = Math.min(filter.isEmpty() ? 64 : filter.getCount(), stack.getCount());
		inserted.setCount(amountToExtract);

		ItemStack remainder = ItemHandlerHelper.insertItemStacked(inv, inserted, false);

		if (remainder.isEmpty()) {
			if (!world.isRemote)
				world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.BLOCKS, .125f, 1f);
			justEaten = stack.copy();
			remainder = stack.copy();
			remainder.setCount(stack.getCount() - amountToExtract);

		} else {
			waitingForInventorySpace = true;
			remainder.grow(stack.getCount() - amountToExtract);
		}

		sendData();
		return remainder;
	}

	public void spawnParticles(ItemStack stack) {
		Vec3i directionVec = getBlockState().get(BlockStateProperties.HORIZONTAL_FACING).getDirectionVec();
		float xSpeed = directionVec.getX() * 1 / 8f;
		float zSpeed = directionVec.getZ() * 1 / 8f;
		Vec3d vec = VecHelper.getCenterOf(pos);
		world.addParticle(new ItemParticleData(ParticleTypes.ITEM, stack), vec.x, vec.y - 9 / 16f, vec.z, xSpeed,
				1 / 6f, zSpeed);
	}

	@Override
	public void setFilter(ItemStack stack) {
		filter = stack.copy();
		markDirty();
		sendData();
		neighborChanged();
	}

	@Override
	public ItemStack getFilter() {
		return filter.copy();
	}

}