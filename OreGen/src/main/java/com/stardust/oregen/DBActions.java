package com.stardust.oregen;

import com.stardust.oregen.utils.ConstantManager;
import net.kyori.adventure.text.Component;
import org.bukkit.*;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.util.Vector;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import static org.bukkit.Bukkit.getServer;

public class DBActions {

    static Connection connection;
    public static class SavedRegion {
        Location pos1;
        Location pos2;
        Vector facing;

        SavedRegion(Location pos1, Location pos2, Vector facing) {
            this.pos1 = pos1.getBlock().getLocation();
            this.pos2 = pos2.getBlock().getLocation();
            this.facing = facing;
        }

        SavedRegion() {}

        @Override
        public String toString() {
            return pos1.getWorld().getUID() +
                    "," +
                    pos1.getBlockX() +
                    "," +
                    pos1.getBlockY() +
                    "," +
                    pos1.getBlockZ() +
                    "," +
                    pos2.getBlockX() +
                    "," +
                    pos2.getBlockY() +
                    "," +
                    pos2.getBlockZ() +
                    "," +
                    facing.getX() +
                    "," +
                    facing.getY() +
                    "," +
                    facing.getZ();
        }

        public static SavedRegion fromString(String str) {
            SavedRegion savedRegion = new SavedRegion();
            String[] parts = str.split(",");
            World world = Bukkit.getWorld(UUID.fromString(parts[0]));
            savedRegion.pos1 = new Location(world, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            savedRegion.pos2 = new Location(world, Integer.parseInt(parts[4]), Integer.parseInt(parts[5]), Integer.parseInt(parts[6]));
            savedRegion.facing = new Vector(Double.parseDouble(parts[7]), Double.parseDouble(parts[8]), Double.parseDouble(parts[9]));
            return savedRegion;
        }
    }

    public static class OreGenTableEntry {
        public Location location;
        public Material type;
        public Long destroyedAt;
    }

    static SavedRegion activeRegion;

    static boolean enableDB() {
        try {
            connect();
        } catch (ClassNotFoundException e) {
            Bukkit.getLogger().log(Level.SEVERE, "[" + OreGen.getInstance().getName() + "] Class Not Found: " + e.getMessage());
            return false;
        } catch (SQLException e) {
            Bukkit.getLogger().log(Level.SEVERE, "[" + OreGen.getInstance().getName() + "] SQLException: " + e.getMessage());
            return false;
        }
        activeRegion = loadRegion(OreGen.OreRegion.region1);
        return true;
    }

    static void saveRegion(OreGen.OreRegion region, SavedRegion savedRegion) {
        FileConfiguration config = OreGen.getOreGenConfig();
        ConfigurationSection section = config.getConfigurationSection("regions");
        if (section == null)
            section = config.createSection("regions");
        section.set(region.toString(), savedRegion.toString());
        OreGen.getInstance().saveConfig();
        activeRegion = savedRegion;
    }

    static SavedRegion loadRegion(OreGen.OreRegion region) {
        FileConfiguration config = OreGen.getOreGenConfig();
        ConfigurationSection section = config.getConfigurationSection("regions");
        if (section == null) return null;
        String s = section.getString(region.toString());
        if (s == null) return null;
        return SavedRegion.fromString(s);
    }

    public static OreGenTableEntry lookupOreGenTableEntry(int x, int y, int z) {
        if (!connectionOk("lookupOreGenTableEntry")) return null;
        try {
            String query = "SELECT Type, destroyedTime FROM OreGenTable WHERE X = ? AND Y = ? AND Z = ?";
            PreparedStatement pstmt = connection.prepareStatement(query);

            // Setting the parameters for the query
            pstmt.setInt(1, x);
            pstmt.setInt(2, y);
            pstmt.setInt(3, z);

            ResultSet rs = pstmt.executeQuery();

            // Check if a result is found
            if (rs.next()) {
                OreGenTableEntry entry = new OreGenTableEntry();
                String type = rs.getString("Type");
                entry.type = Material.valueOf(type);
                entry.destroyedAt = rs.getLong("destroyedTime");
                entry.location = new Location(activeRegion.pos1.getWorld(), (double)x, (double)y, (double)z);
                return entry;
            } else {
                //Bukkit.getLogger().info("No entry found for given X, Y, Z coordinates.");
            }

            pstmt.close();
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[" + OreGen.getInstance().getName() + "] Failed to lookup OreGenTable: " + e.getMessage());
        }
        return null;
    }

    static long lastReconnectAttempt = 0;
    private static boolean connectionOk(String name) {
        try {
            if (!connection.isClosed())
                return true;
        } catch (Exception ignored) {}
        if (System.currentTimeMillis() - lastReconnectAttempt > 3000) {
            Bukkit.getLogger().log(Level.SEVERE, "[BankSys] [" + name + "] Connection to DB lost, trying to re-connect...");
            return enableDB();
        }
        return false;
    }

    public static void insertOreGenEntry(int x, int y, int z, String type, long destroyedTime) {
        if (!connectionOk("insertOreGenEntry")) return;
        try {
            // Prepare the SQL statement with placeholders for values to insert
            String sql = "INSERT INTO OreGenTable (X, Y, Z, Type, destroyedTime) VALUES (?, ?, ?, ?, ?)";
            PreparedStatement pstmt = connection.prepareStatement(sql);

            // Set the values for the placeholders based on the method parameters
            pstmt.setInt(1, x);
            pstmt.setInt(2, y);
            pstmt.setInt(3, z);
            pstmt.setString(4, type);
            pstmt.setLong(5, destroyedTime);

            // Execute the update
            pstmt.executeUpdate();

            // Close the PreparedStatement
            pstmt.close();
        } catch (Exception e) {
            //Bukkit.getLogger().log(Level.SEVERE, "[" + OreGen.getInstance().getName() + "] Failed to insert entry into OreGenTable: " + e.getMessage());
        }
    }

    public static void updateDestroyedTime(int x, int y, int z, long newDestroyedTime) {
        if (!connectionOk("updateDestroyedTime")) return;
        try {
            // Prepare the SQL statement to update the destroyedTime
            String sql = "UPDATE OreGenTable SET destroyedTime = ? WHERE X = ? AND Y = ? AND Z = ?";
            PreparedStatement pstmt = connection.prepareStatement(sql);

            // Set the new destroyedTime value and the X, Y, Z coordinates in the query
            pstmt.setLong(1, newDestroyedTime);
            pstmt.setInt(2, x);
            pstmt.setInt(3, y);
            pstmt.setInt(4, z);

            // Execute the update
            int affectedRows = pstmt.executeUpdate();

            // Close the PreparedStatement
            pstmt.close();
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[" + OreGen.getInstance().getName() + "] Failed to update destroyedTime: " + e.getMessage());
        }
    }

    public static List<OreGenTableEntry> getAllOreGenEntries() {
        if (!connectionOk("getAllOreGenEntries")) return null;
        List<OreGenTableEntry> entries = new ArrayList<>();
        try {
            Statement stmt = connection.createStatement();
            String sql = "SELECT X, Y, Z, Type, destroyedTime FROM OreGenTable";
            ResultSet rs = stmt.executeQuery(sql);

            while (rs.next()) {
                int x = rs.getInt("X");
                int y = rs.getInt("Y");
                int z = rs.getInt("Z");
                long destroyedTime = rs.getLong("destroyedTime");
                OreGenTableEntry entry = new OreGenTableEntry();
                String type = rs.getString("Type");
                entry.type = Material.valueOf(type);
                entry.destroyedAt = destroyedTime;
                entry.location = new Location(activeRegion.pos1.getWorld(), (double)x, (double)y, (double)z);
                entries.add(entry);
            }

            rs.close();
            stmt.close();
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[" + OreGen.getInstance().getName() + "] Failed to retrieve OreGenTable entries: " + e.getMessage());
        }
        return entries;
    }

    public static void clearOreGenTable() {
        if (!connectionOk("clearOreGenTable")) return;
        try {
            Statement stmt = connection.createStatement();
            String sql = "DELETE FROM OreGenTable";
            stmt.executeUpdate(sql);
            Bukkit.getLogger().info("[" + OreGen.getInstance().getName() + "] OreGenTable cleared");
            stmt.close();
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[" + OreGen.getInstance().getName() + "] Failed to clear OreGenTable: " + e.getMessage());
        }
    }

    private static void connect() throws SQLException, ClassNotFoundException {
        String host = ConstantManager.inst().mysql_host;
        int port = ConstantManager.inst().mysql_port;
        String username = ConstantManager.inst().mysql_user;
        String password = ConstantManager.inst().mysql_password;
        String database = ConstantManager.inst().mysql_database;

        Class.forName("com.mysql.jdbc.Driver");
        String s = "jdbc:mysql://" + host+ ":" + port + "/" + database;
        Bukkit.getLogger().info("[" + OreGen.getInstance().getName() + ")] Connecting to MySQL database at " + s + " with credentials: " + username + " " + password);
        connection = DriverManager.getConnection(s, username, password);

        try {
            Statement stmt = connection.createStatement();

            String sql = "CREATE TABLE IF NOT EXISTS OreGenTable (" +
                    "X INT," +
                    "Y INT," +
                    "Z INT," +
                    "Type VARCHAR(32)," +
                    "destroyedTime BIGINT," +
                    "PRIMARY KEY (X, Y, Z)" + // Composite primary key using X, Y, Z
                    ");";

            stmt.executeUpdate(sql);
            Bukkit.getLogger().info("[" + OreGen.getInstance().getName() + "] Created SQL table");
            stmt.close();
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[" + OreGen.getInstance().getName() + "] Failed to create table: " + e.getMessage());
        }
    }

    private static byte[] uuidToBytes(UUID uuid) {
        long hi = uuid.getMostSignificantBits();
        long lo = uuid.getLeastSignificantBits();
        return ByteBuffer.allocate(16).putLong(hi).putLong(lo).array();
    }

    public static void cleanup() {
        try {
            connection.close();
        } catch (Exception e) {

        }
    }

    public static void saveOresInRegion(Player player, SavedRegion region) {
       Location pos1 = region.pos1.clone();
       Location pos2 = region.pos2.clone();

       int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
       int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
       int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
       int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

       assert pos1.getWorld() != null;

       clearOreGenTable();

       player.sendMessage("Ores werden gespeichert in einer %d x %d x %d Region".formatted(maxX-minX,256,maxZ-minZ));
       Bukkit.getScheduler().runTaskAsynchronously(OreGen.getInstance(), ()->{
           int maxY = 256;
           int totalBlocks = (maxX - minX) * (maxY) * (maxZ - minZ);
           int block_i = 0;
           long lastPercent = -1;
           long _lastPercent = -1;
           int saved = 0;

           for (int x = minX; x < maxX; x++) {
               for (int y = 0; y < maxY; y++) {
                   for (int z = minZ; z < maxZ; z++) {
                       String mat = pos1.getWorld().getBlockAt(x, y, z).getType().toString();
                       if (mat.contains("_ORE")) {
                           insertOreGenEntry(x, y, z, mat, System.currentTimeMillis());
                           saved++;
                       }
                       block_i++;

                       long percent = Math.round((double) block_i / totalBlocks * 100);
                       if (percent % 5 == 0 && lastPercent != percent) {
                           lastPercent = percent;
                           player.sendMessage(percent + "% Fertig...");
                       }
                       if (saved % 1000 == 0 && _lastPercent != percent) {
                           Bukkit.getLogger().info(percent + "% Fertig...");
                           _lastPercent = percent;
                       }
                   }
               }
           }
           Bukkit.getLogger().info("100% Fertig...");
           player.sendMessage(saved + " Ores Gespeichert!");
       });

    }
}
