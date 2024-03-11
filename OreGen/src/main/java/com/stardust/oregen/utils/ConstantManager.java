package com.stardust.oregen.utils;

import java.lang.reflect.Field;

import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import net.kyori.adventure.audience.Audiences;

import net.kyori.adventure.text.Component;
import org.yaml.snakeyaml.scanner.Constant;

public class ConstantManager {
    public String mysql_host = "127.0.0.1";
    public String mysql_user = "minecraft";
    public String mysql_password = "minecraftIsCool123!";
    public String mysql_database = "mcsql";
    public Integer mysql_port = 3306;
    public String msg_REDSTONE_ORE = "&7[&l&fOreGen&r&7] &4Du hast &l&bRedstone&r&4 cool-down für &eMINUTESM SECONDSS";
    public String msg_NETHER_QUARTZ_ORE = "&7[&l&fOreGen&r&7] &4Du hast &l&bNether Quartz&r&4 cool-down für &eMINUTESM SECONDSS";
    public String msg_NETHER_GOLD_ORE = "&7[&l&fOreGen&r&7] &4Du hast &l&bNether Gold&r&4 cool-down für &eMINUTESM SECONDSS";
    public String msg_LAPIS_ORE = "&7[&l&fOreGen&r&7] &4Du hast &l&bLapiz&r&4 cool-down für &eMINUTESM SECONDSS";
    public String msg_IRON_ORE = "&7[&l&fOreGen&r&7] &4Du hast &l&bIron&r&4 cool-down für &eMINUTESM SECONDSS";
    public String msg_GOLD_ORE = "&7[&l&fOreGen&r&7] &4Du hast &l&bGold&r&4 cool-down für &eMINUTESM SECONDSS";
    public String msg_EMERALD_ORE = "&7[&l&fOreGen&r&7] &4Du hast &l&bEmerald&r&4 cool-down für &eMINUTESM SECONDSS";
    public String msg_DIAMOND_ORE = "&7[&l&fOreGen&r&7] &4Du hast &l&bDia&r&4 cool-down für &eMINUTESM SECONDSS";
    public String msg_COPPER_ORE = "&7[&l&fOreGen&r&7] &4Du hast &l&bKupfer&r&4 cool-down für &eMINUTESM SECONDSS";
    public String msg_COAL_ORE = "&7[&l&fOreGen&r&7] &4Du hast &l&bKohle&r&4 cool-down für &eMINUTESM SECONDSS";
    public Integer minuten_cooldown_REDSTONE_ORE = 1;
    public Integer minuten_cooldown_NETHER_QUARTZ_ORE = 2;
    public Integer minuten_cooldown_NETHER_GOLD_ORE = 5;
    public Integer minuten_cooldown_LAPIS_ORE = 1;
    public Integer minuten_cooldown_IRON_ORE = 2;
    public Integer minuten_cooldown_GOLD_ORE = 1;
    public Integer minuten_cooldown_EMERALD_ORE = 3;
    public Integer minuten_cooldown_DIAMOND_ORE = 10;
    public Integer minuten_cooldown_COPPER_ORE = 5;
    public Integer minuten_cooldown_COAL_ORE = 7;
    public String selection_prompt_region = "Wähle die Region aus, in der die Ores respawnen sollen.";
    public String selection_prompt_usage = "&5&lPosition1: &fLinksklick\n&5&lPosition2: &fRechtsklick\n&5&lFertig: &fShift + Linksklick";
    public String selection_wand_confirmed = "&5&lAuswahl bestätigt!";
    public String selection_wand_failed = "&c&lDu musst zuerst Positionen auswählen, bevor du bestätigst!";
    public String selection_wand_lore = "Linksklick für pos1\nRechtsklick für pos2\nShift+Linksklick zum Abschließen";
    public String plugin_usage = "Nutzung des OreGen-Plugins:\n" +
            "/oregen region - Wählen der region\n";


    public ConstantManager(Plugin plugin) {
        instance = this;
        initConstants(plugin);
    }
    public static ConstantManager instance;
    public static ConstantManager inst() {
        return instance;
    }

    public void initConstants(Plugin plugin) {
        try {
            final Class<ConstantManager> yourClass = ConstantManager.class;
            final Field[] fields = yourClass.getFields();

            for (Field f : fields) {
                String name = f.getName();
                Object value = f.get(this);

                switch (f.getType().getName()) {
                    case "java.lang.String":
                        value = plugin.getConfig().getString(name, (String) value);
                        break;
                    case "java.lang.Double":
                        value = plugin.getConfig().getDouble(name, (Double) value);
                        break;
                    case "java.lang.Integer":
                        value = plugin.getConfig().getInt(name, (Integer) value);
                        break;
                    default: continue;
                }
                plugin.getConfig().set(name, value);
                f.set(this, value);
            }
        } catch (IllegalAccessException e) {
            Bukkit.getLogger().info(e.toString());
        }
    }

    public String getStringByFieldName(String fieldName) {
        return getObjectByFieldName(fieldName).toString();
    }

    public Integer getIntegerByFieldName(String fieldName) {
        return (Integer) getObjectByFieldName(fieldName);
    }

    public Object getObjectByFieldName(String fieldName) {
        try {
            // Get the class of the object
            Class<?> objClass = this.getClass();

            // Access the field using reflection
            Field field = objClass.getDeclaredField(fieldName);
            // Make the field accessible if it is private
            field.setAccessible(true);

            // Get the value of the field for the given object

            // Return the value as a String
            return field.get(this);
        } catch (NoSuchFieldException e) {
            System.err.println("Field '" + fieldName + "' not found in the object of class " + this.getClass().getSimpleName());
        } catch (IllegalAccessException e) {
            System.err.println("Field '" + fieldName + "' is not accessible.");
        } catch (Exception e) {
            System.err.println("An unexpected error occurred: " + e.getMessage());
        }
        // Return null or an appropriate value in case of error
        return null;
    }

    public static void sendFormattedMessage(Player player, String message) {
        for (String s : message.split("\n"))
            player.sendMessage(s.replace('&', '§'));
    }

    public static void sendFormattedActionBarMessage(Player player, String message) {
        player.sendActionBar(message.replace('&', '§'));
    }


    public static void broadcastFormattedMessage(String message) {
        for (String s : message.split("\n"))
            Bukkit.getServer().broadcastMessage(s.replace('&', '§'));
    }
}
