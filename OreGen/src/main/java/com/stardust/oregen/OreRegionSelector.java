package com.stardust.oregen;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.stardust.oregen.utils.ConstantManager;
import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.*;

import static com.stardust.oregen.OreGen.OreRegion.region1;

public class OreRegionSelector implements Listener {
    UUID activePlayer;
    OreGen.OreRegion activeRegion;
    Location pos1;

    ItemStack selectionWand;
    Location pos2;
    char faceDir;
    Location bankPos1;
    Location bankPos2;

    /** used to store the player's last held item when it gets replaced with wand */
    ItemStack oldHandItem;
    boolean cuiEnabled;

    Map<OreGen.OreRegion, DBActions.SavedRegion> boundaries;

    Map<Long, List<BlockLoc>> restoreBlockMap;

    OreRegionSelector() {
        restoreBlockMap = new HashMap<>();

        selectionWand = new ItemStack(Material.WOODEN_AXE);
        ItemMeta meta = selectionWand.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "Bank Region Selector");
        List<String> loreList = new ArrayList<>();
        for (String str : ConstantManager.inst().selection_wand_lore.split("\n"))
            loreList.add(str);
        meta.setLore(loreList);
        selectionWand.setItemMeta(meta);
        cuiEnabled = false;
        boundaries = new HashMap<>();
        for (OreGen.OreRegion reg : OreGen.OreRegion.values()) {
            DBActions.SavedRegion loc = DBActions.loadRegion(reg);
            if (loc != null) {
                boundaries.put(reg, loc);
            }
        }
    }

    public void onCommandSelectRegion(Player player, OreGen.OreRegion region) {
        switch (region) {
            case region1 -> ConstantManager.sendFormattedMessage(player, ConstantManager.inst().selection_prompt_region);
        }
        ConstantManager.sendFormattedMessage(player, ConstantManager.inst().selection_prompt_usage);
        activePlayer = player.getUniqueId();
        activeRegion = region;
        pos1 = null;
        pos2 = null;
        oldHandItem = player.getInventory().getItemInMainHand();
        player.getInventory().setItemInMainHand(selectionWand);
    }

    private void enableCUI(Player player, boolean enable) {
        com.sk89q.worldedit.entity.Player actor = BukkitAdapter.adapt(player);
        LocalSession session = WorldEdit.getInstance().getSessionManager().getIfPresent(actor);
        if (session != null) {
            if (enable)
                session.setUseServerCUI(true);
            else
                session.setUseServerCUI(false);
            session.updateServerCUI(actor);
            cuiEnabled = enable;
        }
    }

    private void clearWESelection(Player player) {
        com.sk89q.worldedit.entity.Player actor = BukkitAdapter.adapt(player);
        LocalSession session = WorldEdit.getInstance().getSessionManager().getIfPresent(actor);
        if (session != null) {
            session.getRegionSelector(BukkitAdapter.adapt(player.getWorld())).clear();
            session.dispatchCUISelection(actor);
            session.updateServerCUI(actor);
        }
    }

    void finishSel(Player player) {
        if (pos1 != null && pos2 != null) {
            DBActions.SavedRegion savedRegion = new DBActions.SavedRegion(pos1, pos2, player.getFacing().getDirection());
            DBActions.saveRegion(activeRegion, savedRegion);
            boundaries.put(activeRegion, savedRegion);
            ConstantManager.sendFormattedMessage(player, ConstantManager.inst().selection_wand_confirmed);
            clearWESelection(player);
            resetSelection(player);
            DBActions.saveOresInRegion(player, savedRegion);
        }
        else {
            ConstantManager.sendFormattedMessage(player, ConstantManager.inst().selection_wand_failed);
        }
    }

    /** Selection wand events */
    @EventHandler
    public void onPlayerInteractEvent(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (activePlayer == player.getUniqueId()) {
            if (!cuiEnabled) enableCUI(player, true);

            event.setCancelled(true);
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                if (event.getClickedBlock() != null) {
                    pos2 = event.getClickedBlock().getLocation();
                }
            }
            else if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                if (player.isSneaking()) {
                    // finish selection
                    finishSel(player);
                }
                else {
                    if (event.getClickedBlock() != null) {
                        pos1 = event.getClickedBlock().getLocation();
                    }
                }
            }
            else if (event.getAction() == Action.LEFT_CLICK_AIR) {
                if (player.isSneaking()) finishSel(player);
            }
        }
    }

    void resetSelection(Player player) {
        activePlayer = null;
        pos1 = null;
        pos2 = null;
        player.getInventory().setItemInMainHand(oldHandItem);
        enableCUI(player, false);
    }

    /** If player changes away from selection wand, end base selection */
    @EventHandler
    public void onPlayerItemChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (activePlayer == player.getUniqueId()) {
            Inventory inventory = player.getInventory();
            if (inventory.contains(selectionWand)) {
                inventory.remove(selectionWand);
                resetSelection(player);
            }
        }
    }

    /** If player drops selection wand, send base selection */
    @EventHandler
    public void onDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (activePlayer == player.getUniqueId()) {
            event.getItemDrop().remove();
            resetSelection(player);
        }
    }

    public static boolean inRegion(Location testpoint, DBActions.SavedRegion boundary) {
        if (boundary == null) {
            Bukkit.getLogger().severe("inRegion boundary == null");
            return false;
        }
        int xMin = Math.min(boundary.pos1.getBlockX(), boundary.pos2.getBlockX());
        int xMax = Math.max(boundary.pos1.getBlockX(), boundary.pos2.getBlockX());
        int zMin = Math.min(boundary.pos1.getBlockZ(), boundary.pos2.getBlockZ());
        int zMax = Math.max(boundary.pos1.getBlockZ(), boundary.pos2.getBlockZ());

        return testpoint.getBlockX() >= xMin && testpoint.getBlockX() <= xMax
                && testpoint.getBlockZ() >= zMin && testpoint.getBlockZ() <= zMax;
    }

    Location blockLoc;

    public class BlockLoc {
        public int x, y, z;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType().toString().contains("_ORE")) {
            blockLoc = event.getBlock().getLocation();
            if (inRegion(blockLoc, DBActions.activeRegion)) {
                DBActions.OreGenTableEntry entry = DBActions.lookupOreGenTableEntry(blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());
                if (entry != null) {
                    DBActions.updateDestroyedTime(blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ(), System.currentTimeMillis());
                    BlockLoc loc = new BlockLoc();
                    loc.x = blockLoc.getBlockX();
                    loc.y = blockLoc.getBlockY();
                    loc.z = blockLoc.getBlockZ();

                    String typ = entry.type.toString().replace("DEEPSLATE_", "");
                    Integer cooldown = ConstantManager.inst().getIntegerByFieldName("minuten_cooldown_" + typ) * 60;
                    Long timeKey = System.currentTimeMillis() / 1000 + cooldown;
                    if (restoreBlockMap.containsKey(timeKey)) {
                        restoreBlockMap.get(timeKey).add(loc);
                    } else {
                        List<BlockLoc> arr = new ArrayList<>();
                        arr.add(loc);
                        restoreBlockMap.put(timeKey, arr);
                    }
                    Bukkit.getScheduler().runTaskLater(OreGen.getInstance(),
                            () -> blockLoc.getBlock().setType(Material.BEDROCK), 2);
                }
            }
        }
    }

    @EventHandler
    public void onBlockClick(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getHand() == EquipmentSlot.HAND) {
            Player player = (Player) event.getPlayer();
            Block block = event.getClickedBlock();
            if (block != null && block.getType() == Material.BEDROCK) {
                DBActions.OreGenTableEntry entry = DBActions.lookupOreGenTableEntry(block.getX(), block.getY(), block.getZ());
                if (entry != null) {
                    String typ = entry.type.toString().replace("DEEPSLATE_", "");
                    String message = ConstantManager.inst().getStringByFieldName("msg_" + typ);
                    Integer cooldown = ConstantManager.inst().getIntegerByFieldName("minuten_cooldown_" + typ) * 60;

                    long timeLeft = entry.destroyedAt / 1000 + cooldown  - System.currentTimeMillis() / 1000;
                    long minutes = timeLeft / 60;
                    long seconds = timeLeft % 60;

                    ConstantManager.sendFormattedActionBarMessage(player, message.replace("SECONDS", "" + seconds).replace("MINUTES", "" + minutes));
                }
            }
        }
    }
}
