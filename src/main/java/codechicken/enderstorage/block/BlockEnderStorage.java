package codechicken.enderstorage.block;

import codechicken.enderstorage.api.Frequency;
import codechicken.enderstorage.handler.ConfigurationHandler;
import codechicken.enderstorage.init.EnderStorageRecipe;
import codechicken.enderstorage.tile.TileEnderChest;
import codechicken.enderstorage.tile.TileEnderTank;
import codechicken.enderstorage.tile.TileFrequencyOwner;
import codechicken.lib.block.property.PropertyString;
import codechicken.lib.raytracer.RayTracer;
import codechicken.lib.util.ItemUtils;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static codechicken.enderstorage.reference.VariantReference.enderBlockNamesList;

/**
 * Created by covers1624 on 4/11/2016.
 */
public class BlockEnderStorage extends Block implements ITileEntityProvider {

    public static final PropertyString VARIANTS = new PropertyString("type", enderBlockNamesList);

    public BlockEnderStorage() {
        super(Material.ROCK);
        setHardness(20F);
        setResistance(100F);
        setCreativeTab(CreativeTabs.TRANSPORTATION);
        setUnlocalizedName("enderStorage");
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        switch (meta) {
        case 0:
            return new TileEnderChest();
        case 1:
            return new TileEnderTank();
        default:
            return null;
        }
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
    }

    @Override//TODO
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override//TODO
    public boolean isNormalCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean removedByPlayer(IBlockState state, World world, BlockPos pos, EntityPlayer player, boolean willHarvest) {
        return willHarvest || super.removedByPlayer(state, world, pos, player, false);
    }

    @Override
    public void harvestBlock(World worldIn, EntityPlayer player, BlockPos pos, IBlockState state, TileEntity te, ItemStack stack) {
        super.harvestBlock(worldIn, player, pos, state, te, stack);
        worldIn.setBlockToAir(pos);
    }

    public void breakBlock(World worldIn, BlockPos pos, IBlockState state) {
        super.breakBlock(worldIn, pos, state);
        worldIn.removeTileEntity(pos);
    }

    @Override
    public ArrayList<ItemStack> getDrops(IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        ArrayList<ItemStack> ret = new ArrayList<ItemStack>();

        TileFrequencyOwner tile = (TileFrequencyOwner) world.getTileEntity(pos);
        if (tile != null) {
            ret.add(createItem(state.getBlock().getMetaFromState(state), tile.frequency));
            if (ConfigurationHandler.anarchyMode && tile.frequency.hasOwner()) {
                ret.add(ConfigurationHandler.personalItem.copy());
            }
        }

        return ret;
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult rayTraceResult, World world, BlockPos pos, EntityPlayer player) {
        TileFrequencyOwner tile = (TileFrequencyOwner) world.getTileEntity(pos);
        return createItem(this.getMetaFromState(state), tile.frequency);
    }

    private ItemStack createItem(int meta, Frequency freq) {
        ItemStack stack = new ItemStack(this, 1, meta);
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        if (ConfigurationHandler.anarchyMode) {
            freq.setOwner(null);
        }
        stack.getTagCompound().setTag("Frequency", freq.toNBT());
        return stack;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, ItemStack heldItem, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        TileFrequencyOwner tile = (TileFrequencyOwner) world.getTileEntity(pos);

        //Normal block trace.
        RayTraceResult hit = RayTracer.retraceBlock(world, player, pos);
        if (hit == null) {
            return false;
        }
        if (hit.subHit == 4) {
            ItemStack item = player.inventory.getCurrentItem();
            if (player.isSneaking() && tile.frequency.hasOwner()) {
                if (!player.capabilities.isCreativeMode && !player.inventory.addItemStackToInventory(ConfigurationHandler.personalItem.copy())) {
                    return false;
                }

                tile.setFreq(tile.frequency.copy().setOwner(null));
                return true;
            } else if (item != null && ItemUtils.areStacksSameTypeCrafting(item, ConfigurationHandler.personalItem)) {
                if (!tile.frequency.hasOwner()) {
                    tile.setFreq(tile.frequency.copy().setOwner(player.getDisplayNameString()));
                    if (!player.capabilities.isCreativeMode) {
                        item.stackSize--;
                    }
                    return true;
                }
            }
        } else if (hit.subHit >= 1 && hit.subHit <= 3) {
            ItemStack item = player.inventory.getCurrentItem();
            int dye = EnderStorageRecipe.getDyeType(item);
            if (dye != -1) {
                int[] colours = tile.frequency.toArray();
                if (colours[hit.subHit - 1] == (~dye & 0xF)) {
                    return false;
                }
                colours[hit.subHit - 1] = ~dye & 0xF;
                tile.setFreq(Frequency.fromArray(colours));
                if (!player.capabilities.isCreativeMode) {
                    item.stackSize--;
                }
                return true;
            }
        }
        return tile.activate(player, hit.subHit);
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        ((TileFrequencyOwner) world.getTileEntity(pos)).onPlaced(placer);
    }

    @Override
    public RayTraceResult collisionRayTrace(IBlockState state, World world, BlockPos pos, Vec3d start, Vec3d end) {
        TileFrequencyOwner tile = (TileFrequencyOwner) world.getTileEntity(pos);
        if (tile != null) {
            return RayTracer.rayTraceCuboidsClosest(start, end, tile.getIndexedCuboids(), pos);
        }
        return super.collisionRayTrace(state, world, pos, start, end);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        TileFrequencyOwner tile = (TileFrequencyOwner) source.getTileEntity(pos);
        if (tile != null && !tile.getIndexedCuboids().isEmpty()) {
            return tile.getIndexedCuboids().get(0).aabb();
        }
        return super.getBoundingBox(state, source, pos);
    }

    @Override
    public void getSubBlocks(Item par1, CreativeTabs par2CreativeTabs, List<ItemStack> list) {
        list.add(new ItemStack(this, 1, 0));
        list.add(new ItemStack(this, 1, 1));
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileFrequencyOwner) {
            return ((TileFrequencyOwner) tile).getLightValue();
        }
        return 0;
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, VARIANTS);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return enderBlockNamesList.indexOf(String.valueOf(state.getValue(VARIANTS)));
    }

    @Override//TODO
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(VARIANTS, enderBlockNamesList.get(meta));
    }

    @Override
    public boolean canConnectRedstone(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        TileFrequencyOwner tile = (TileFrequencyOwner) world.getTileEntity(pos);
        return tile != null && tile.redstoneInteraction();
    }

    @Override//TODO
    public boolean hasComparatorInputOverride(IBlockState state) {
        return true;
    }

    @Override//TODO
    public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        TileFrequencyOwner tile = (TileFrequencyOwner) world.getTileEntity(pos);
        return tile.comparatorInput();
    }

    @Override
    public boolean rotateBlock(World world, BlockPos pos, EnumFacing axis) {
        TileFrequencyOwner tile = (TileFrequencyOwner) world.getTileEntity(pos);
        return tile.rotate();
    }

    @Override//TODO
    public boolean eventReceived(IBlockState state, World worldIn, BlockPos pos, int eventID, int eventParam) {
        super.eventReceived(state, worldIn, pos, eventID, eventParam);
        TileEntity tileentity = worldIn.getTileEntity(pos);
        return tileentity != null && tileentity.receiveClientEvent(eventID, eventParam);
    }
}
