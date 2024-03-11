package com.stardust.oregen.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;


/** Super enhanced switch statement with nested switching support.
 *  This class lets you register switch cases, then give it an input and then it
 *  will run the correct case that was registered that corresponds to that input.
 *  You can also register CmdSwitch as a case, so that you can tie multiple
 *  CmdSwitch together to create nested switches.
 */
public class Cmd {
    /** Table to keep track of command cases <-> Command function */
    private final Map<String, Object> caseCommands;

    /** Table to keep track of default command executer */
    private CmdRunnable defaultSwitchRunner;

    private final Map<String, String> perms;

    /** Factory build mehtod. Just returns an instance of this class */
    public static Cmd build() { return new Cmd(null); }
    public static Cmd build(Map<String, String> perms) { return new Cmd(perms); }

    /** Default constructor */
    public Cmd(Map<String, String> perms) { caseCommands = new HashMap<>(); this.perms = perms; }

    /** Converts command enum to it's typed name
     * i.e. ZOOM_ALERTS_TOGGLE -> "toggle"
     */
    private String parseCaseName(Object caseName) {
        String name = caseName.toString();
        if (!name.contains("_")) return "";
        name = name.replaceAll("[A-Z]", "");
        if (name.endsWith("_"))
            name = name.substring(0, name.length()-1);
        return name.substring(name.lastIndexOf('_') + 1);
    }

    /**
     * Adds a case to the switch statement.
     * @param caseName THe string name pertaining to this case.
     * @param value A simple runnable.
     */
    public Cmd addCase(Object caseName, CmdRunnable value) {
        caseCommands.put(parseCaseName(caseName), value);
        return this;
    }

    /**
     * Adds a case to the switch statement.
     * @param caseName THe string name pertaining to this case.
     * @param value Another CmdSwitch that will be nested within this one at execution.
     */
    public Cmd addCase(Object caseName, Cmd value) {
        // check if a case with this casename already exists. This is then the special fall-through case.
        String parsedName = parseCaseName(caseName);

        Object caseExec = value.caseCommands.getOrDefault(parsedName, null);
        if (caseExec != null) value.caseCommands.put("", caseExec);
        caseCommands.put(parsedName, value);
        return this;
    }

    /**
     * Adds a case to the switch statement.
     * @param caseName THe string name pertaining to this case.
     * @param value A function reference.
     */
    public Cmd addCase(Object caseName, BiFunction<Player, String[], Boolean> value) {
        caseCommands.put(parseCaseName(caseName), value);
        return this;
    }

    /**
     * Adds a defualt case value.
     */
    public Cmd addDefaultCase(CmdRunnable defaultSwitchRunner) {
        this.defaultSwitchRunner = defaultSwitchRunner;
        return this;
    }

    /** Executes on a list of arguments, starting at the first one */
    public void execute(Player sender, String[] args) {
        execute(sender, args, 0, null);
    }

    /** Executes on a list of arguments, specifically checkig the condition on argument at idx */
    public void execute(Player sender, String[] args, int idx, CmdRunnable defaultParentRunner) {

        // Get the condition value (get the argument at index idx) or default to ""
        String condition = idx >= args.length? "" : args[idx];

        // Lookup the condition, ie. the nane of the object in the hashtable of cases to get the case value
        Object runner = caseCommands.getOrDefault(condition, defaultSwitchRunner);

        if (runner == null) {
            if (defaultParentRunner != null)
                defaultParentRunner.run();
            return;
        }

        if (args.length > 0) {
            String fullcmd = "oregen_" + args[0];
            if (this.perms.containsKey(fullcmd)) {
                if (!sender.hasPermission(this.perms.get(fullcmd))) {
                    sender.sendMessage("You do not have access to that command!");
                    return;
                }
            }
        }

        // Matching case found. Check type
        if (runner instanceof Cmd) {
            // If it's another command switch, run it again but on the next argument
            ((Cmd) runner).execute(sender, args, idx + 1, this.defaultSwitchRunner);
        }
        else if (runner instanceof BiFunction) {
            // If it's a function, run it with the arguments we have
            ((BiFunction<Player, String[], Boolean>) runner).apply(sender, args);
        }
        else {
            // If it's a CMDrunnable / lambda, just run it.
            ((CmdRunnable) runner).run();
        }
    }
}