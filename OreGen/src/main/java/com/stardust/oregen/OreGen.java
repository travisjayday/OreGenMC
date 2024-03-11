package com.stardust.oregen;

import com.stardust.oregen.utils.Cmd;
import com.stardust.oregen.utils.ConstantManager;
import com.stardust.oregen.utils.Tabinator;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.DecimalFormat;
import java.util.*;
import java.util.logging.Level;
import static com.stardust.oregen.OreGen.PluginCommands.*;

public final class OreGen extends JavaPlugin {

    protected enum OreRegion {
        region1,
    }

    private OreRegionSelector selector;
    private static OreGen instance;

    private ConstantManager constantManager;

    private static FileConfiguration config;
    protected enum PluginCommands {
        oregen,
        oregen_region,
    }

    public Map<PluginCommands, String> requiredPermMap = new HashMap<>();

    Tabinator tabinator;
    boolean failed = false;
    String failureReason = "";

    private boolean checkDependency(String name) {
        if (getServer().getPluginManager().getPlugin(name) == null
                || !getServer().getPluginManager().getPlugin(name).isEnabled()) {
            getLogger().log(Level.SEVERE, name + " not found or not enabled");
            getLogger().log(Level.SEVERE, "Disabling...");
            failed = true;
            failureReason = name;
            return false;
        }
        return true;
    }

    public static OreGen getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {

        requiredPermMap.put(oregen_region, "oregen.admin");
        // Plugin startup logic
        config = getConfig();
        Bukkit.getLogger().info("[" + this.getName() + "] Enabling....");
        getCommand("oregen").setExecutor(this);

        checkDependency("WorldEdit");

        if (failed) return;

        config.options().copyDefaults(true);
        constantManager = new ConstantManager(this);
        tabinator = new Tabinator(PluginCommands.class);
        tabinator.setReqPerms(requiredPermMap);

        saveConfig();
        instance = this;

        if (!DBActions.enableDB()) {
            Bukkit.getServer().broadcastMessage("[" + this.getName() + "] Could not connect to MySQL DB!");
            getLogger().log(Level.SEVERE, "Failed to connect to SQL DB! Please edit the config.yaml to set up the right credentials!");
            failed = true;
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                Long time = System.currentTimeMillis() / 1000;
                if (selector.restoreBlockMap.containsKey(time)) {
                    for (OreRegionSelector.BlockLoc blockLoc : selector.restoreBlockMap.get(time)) {
                        DBActions.OreGenTableEntry entry = DBActions.lookupOreGenTableEntry(blockLoc.x, blockLoc.y, blockLoc.z);
                        if (entry != null)
                            entry.location.getBlock().setType(entry.type);
                        else
                            Bukkit.getLogger().severe("Warning OreGen failed to restore block!");
                    }
                }
            }
        }.runTaskTimer(this, 20, 20);


        selector = new OreRegionSelector();
        getServer().getPluginManager().registerEvents(selector, this);

        Bukkit.getLogger().info("[" + this.getName() + "] Restoring Ores...");
        for (DBActions.OreGenTableEntry entry : DBActions.getAllOreGenEntries()) {
            entry.location.getBlock().setType(entry.type);
        }
        Bukkit.getLogger().info("[" + this.getName() + "] All Ores restored...");
    }


    public static FileConfiguration getOreGenConfig() {
        return config;
    }


    /** Called when tab completion is required. Given a list of current arguments, returns a list
     * of suggested strings for tab completion. Just let Tabinator auto handle the logic for this */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        return tabinator.onTabComplete(sender, cmd, alias, args);
    }

    /**
     * Base Command Handler
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return false;

        final Player player = (Player) sender;

        if (failed) {
            player.sendMessage(this.getName() +  " failed to load. Please check server logs for dependency issues!");
            player.sendMessage("Plugin \"" + failureReason + "\" is probably the reason");
            return true;
        }

        Cmd.build(tabinator.getReqPerms())
                .addCase(oregen,              () -> messageGeneralUsage(player))
                .addCase(oregen_region,    () -> selector.onCommandSelectRegion(player, OreRegion.region1))
                .addDefaultCase(                    () -> messageGeneralUsage(player))
                .execute(player, args);
        return true;
    }

    private void messageGeneralUsage(Player player) {
        ConstantManager.sendFormattedMessage(player, ConstantManager.inst().plugin_usage);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        DBActions.cleanup();
        instance = null;
    }
}
