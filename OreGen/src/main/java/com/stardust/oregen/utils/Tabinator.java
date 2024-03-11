package com.stardust.oregen.utils;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


/** Helps implement tab completion. Works by providing an enum with defiend commands.
 * Parses that enum to create provide tab completion recommenations.
 */
public final class Tabinator {

    /** Parsed table of commands. Maps the "depth" of the command to a set of commands at that "depth"
     * Note: depth is which argument that command can appear at. For instance, in /claim unclaim now,  unclaim
     * has a depth of 0, now has a depth of 1
     */
    private final Map<Integer, List<String>> depthMap;

    /** Same as depthMap but instead of placeholders like ONLINEPLAYER it holds evaluated names */
    private final Map<Integer, List<String>> evaluatedDepthMap;

    /** The list of enum command values as string */
    private final List<String> values;

    /** This list provides custom recommendations. For eample, if you have an enum called clans_join_CLANNAME, then you
     * would add ("CLANNAME", ["clan 1", "clan name 2", ... ]) into this map.
     */
    public Map<String, List<String>> customRecommendationMap;

    private Map<String, Integer> perms;
    private Map<String, String> permMap;
    private Function<UUID, Integer> getPermLevel;

    public Tabinator(Class enumCmd) {
        // Genearte mapping of enum label to value
        values = getEnumStrings(enumCmd);

        // Generate table of how deep which command is
        this.depthMap = new HashMap<>();
        for (String value : values) {
            int depth = getArgIndexFromEnum(value);
            List<String> cmds = this.depthMap.getOrDefault(depth, new ArrayList<>());
            cmds.add(value);
            this.depthMap.put(depth, cmds);
        }

        evaluatedDepthMap = new HashMap<>();
        customRecommendationMap = new HashMap<>();

        perms = null;
    }

    public void setPermissionManager(Map<String, Integer> perms, Function<UUID, Integer> getPermLevel) {
       this.perms = perms;
       this.getPermLevel = getPermLevel;
    }

    public Map<String, String> getReqPerms() {
        return this.permMap;
    }

    public void setReqPerms(Map<?, String> perms) {
        this.permMap = perms.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> ((Enum)entry.getKey()).name(), // Replace key with key.name()
                        Map.Entry::getValue // Keep the original value
                ));
    }

    /** Gets a list of raw names from an enum class and transforms them
     *  For instance, if an enum is base_alerts_toggle, the returned list will contain alerts_toggle
     */
    List<String> getEnumStrings(Class enumClass) {
        return Arrays.stream(enumClass.getFields()).map(Field::getName).toList();
    }

    /** Returns the depth of a command enum */
    public int getArgIndexFromEnum(Object enumVal) {
        return enumVal.toString().split("_", -1).length - 2;
    }

    /** Converts command enum to it's typed name
     * i.e. ZOOM_ALERTS_TOGGLE -> "toggle"
     */
    private String enumFieldToValue(String field) {
        return field.substring(field.lastIndexOf('_') + 1);
    }

    /** API: Get an integer from a command arg ending in _NUMBER */
    public Integer getIntFromCommand(String[] args, Object command) {
        int argIdx = getArgIndexFromEnum(command);
        if (argIdx >= args.length) {
            return null;
        }
        Integer i = null;
        try {
            i = Integer.valueOf(args[argIdx]);
        } catch (Exception ignored) {}
        return i;
    }

    /** API: Get an String from a command arg ending enum such as _CUSTOMENUM */
    public String getStringFromCommand(String[] args, Object command) {
        int argIdx = getArgIndexFromEnum(command);
        if (argIdx >= args.length) {
            return null;
        }
        return args[argIdx];
    }

    public UUID getPlayerFromCommand(Player player, String[] args, Object command) {
        int argIdx = getArgIndexFromEnum(command);
        if (argIdx >= args.length) {
            player.sendMessage("Please supply a player name as arg");
            return null;
        }
        return Bukkit.getOfflinePlayer(args[argIdx]).getUniqueId();
    }


    private void bakeEvaledCommands(int depth, String prefix, List<String> commandEndings) {
        List<String> evaledComms =  evaluatedDepthMap.getOrDefault(depth, new ArrayList<>());
        prefix = prefix.replaceAll("[A-Z]", "");
        for (String ending : commandEndings)
            evaledComms.add(prefix + ending);
        evaluatedDepthMap.put(depth, evaledComms);
    }

    /** API: Auto Tab Complete based on the provided enum at construction */
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] _args) {

        // List of recommendations to suggest
        List<String> recs = new ArrayList<>();


        // Remove spaces / emtpy args
        List<String> argsFiltered = new ArrayList<>(_args.length);
        for (int i = 0; i < _args.length - 1; i++) {
            if (_args[i].equals("")) continue;
            argsFiltered.add(_args[i]);
        }
        // Always add current arg regardless o fspaces
        argsFiltered.add(_args[_args.length-1]);
        String[] args = argsFiltered.toArray(String[]::new);


        // Build up a string of currently typed commands.
        // for example, if the user types /claim zoom, then this loop will set
        // command = "zoom_". /claim zoom plus, then command = "zoom_plus_
        String command = cmd.getLabel() + "_" + String.join("_", args);

        // Gives recommendations based on which enum starts with comamnd
        for (String comm : this.depthMap.getOrDefault(args.length-1, new ArrayList<>())) {

            // Check if the last arg was a player name. If so replace the placehodler with the actual player name
            if (args.length > 2)
                comm = comm.replaceAll("ONLINEPLAYER+?_", args[args.length-2] + "_");

            // if currently typed command is the beginning of an enum, match with it
            if (comm.startsWith(command)) {
                if (perms != null) {
                    int reqPerm = perms.getOrDefault(comm, 100);
                    int perm = getPermLevel.apply(((Player) sender).getUniqueId());
                    // Player doesn't have access
                    if (reqPerm < 0 && perm >= 100) {
                        // players who don't have access are allowed to run this command
                    }
                    else if (!(perm <= reqPerm)) {
                        continue;
                    }
                }

                if (permMap != null && permMap.containsKey(comm)) {
                    if (!sender.hasPermission(permMap.get(comm))) {
                        continue;
                    }
                }

                // if the currrent arg is a player name, get all the names of palyers and put it into a separate
                // depth map to create new command suggestions
                if (comm.endsWith("ONLINEPLAYER")) {
                    bakeEvaledCommands(args.length-1, comm, Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
                } else if (comm.endsWith("NUMBER")) {
                    // show number place hodler
                    recs.add("[number]");
                }
                else if (comm.endsWith("STRING")) {
                    comm = comm.replace("STRING", "");
                    comm = comm.substring(comm.lastIndexOf('_')+1);
                    recs.add('[' + comm + ']');
                }
                // add custom recommendations if registered
                else if (customRecommendationMap.containsKey(enumFieldToValue(comm))) {
                    bakeEvaledCommands(args.length-1, comm, customRecommendationMap.get(enumFieldToValue(comm)));
                }
                else {
                    // show the next command argument placehodler
                    recs.add(enumFieldToValue(comm));
                }
            }
        }

        // This loop is to suggest evaluated commands such as list of playe rnames. It works like the other loop
        for (String evaledComm : this.evaluatedDepthMap.getOrDefault(args.length-1, new ArrayList<>())) {
            if (evaledComm.startsWith(command)) {
                recs.add(enumFieldToValue(evaledComm));
            }
        }
        return recs;
    }
}
