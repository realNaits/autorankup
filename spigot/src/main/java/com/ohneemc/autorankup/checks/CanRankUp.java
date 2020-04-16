package com.ohneemc.autorankup.checks;

import com.ohneemc.autorankup.AutoRankUpSpigot;
import com.ohneemc.autorankup.bungee.Messages;
import com.ohneemc.autorankup.config.Config;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.ohneemc.autorankup.AutoRankUpSpigot.*;

public class CanRankUp {
    private static final Logger log = Logger.getLogger("Minecraft");

    // List
    private static HashMap<String, String> rankTime = new HashMap<>();
    private static HashMap<String, String> rankTo = new HashMap<>();

    enum PlanType{
        TOTAL, ACTIVE, AFK
    }
    PlanType planType;

    /***
     *
     * @param player The player to rank up
     */
    public static void rankUp(Player player){
        // Getting current group of player.
        String playerGroup = null;
        if (!getVault()){
            String groupPlaceholder = getPlaceholder();
            playerGroup = PlaceholderAPI.setPlaceholders(player, groupPlaceholder);
        }else{
            playerGroup = getPerms().getPrimaryGroup(player);
        }

        // Checking first if true, proceed with rank up.
        if (rankChecker(player, playerGroup)){
            log.info("[AutoRankUp] - " + player.getName() + " ranked up!");
            // What group the player should rank up to
            String toRank = rankTo.get(playerGroup);

            if (getVault()){
                if (rankUpVault(player, toRank)){
                    sendMessages(player,toRank);
                    log.log(Level.INFO, "[AutoRankUp] - Rankup successful");
                }else{
                    log.warning("[AutoRankUp] - Something wrong happened while ranking "
                            + player.getName() + " up with vault..");
                    return;
                }
                return;
            }
            // Getting command to execute on rank up
            String rawCommand = Config.getString("ranks."+playerGroup+".command");

            if (rawCommand == null){
                log.log(Level.INFO, "[AutoRankUp] - Command section is empty for: " + playerGroup);
                return;
            }

            String replacePlayer = rawCommand.replace("{player}", player.getName());
            String command = replacePlayer.replace("{group}", toRank);

            ConsoleCommandSender console = Bukkit.getConsoleSender();
            if (Bukkit.dispatchCommand(console, command)){
                log.log(Level.INFO, "[AutoRankUp] - Command successful");
            }else{
                log.log(Level.SEVERE, "[AutoRankUp] - Command failed");
            }
        }
    }

    /***
     *
     * @param player Which player to rank up
     * @param group What group the player should rank to.
     * @return True or false
     */
    private static boolean rankUpVault(Player player, String group){
        return getPerms().playerAddGroup(player, group);
    }

    /***
     *
     * @param player The player who ranked up
     * @param toRank What rank the player ranked up to
     */
    private static void sendMessages(Player player, String toRank){
        if (AutoRankUpSpigot.getPlayerEnb()){
            String rPlayer = AutoRankUpSpigot.getPlayerMsg().replace("{player}", player.getName());
            String rGroup = rPlayer.replace("{rank_to}", toRank);

            player.sendMessage(ChatColor.translateAlternateColorCodes('&', rGroup));
        }
        if (AutoRankUpSpigot.getBroadcastEnabled()){
            String rPlayer = AutoRankUpSpigot.getBroadcastMsg().replace("{player}", player.getName());
            String rGroup = rPlayer.replace("{rank_to}", toRank);

            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', rGroup));
        }

        if (AutoRankUpSpigot.getBungeeBroadcast()){
            String rPlayer = AutoRankUpSpigot.getBroadcastMsg().replace("{player}", player.getName());
            String rGroup = rPlayer.replace("{rank_to}", toRank);

            Messages.sendBroadcast(rGroup);
        }
    }

    /***
     *
     * @param player The player to check
     * @return True if eligible or false if not
     */
    private static boolean rankChecker(Player player, String group){
        // Grabbing ranks and time
        grabRankUps();
        if (!rankTo.containsKey(group)){
            return false;
        }
        // Getting how long the player has been active for
        String activeTime = getType(player, PlanType.ACTIVE);
        // Getting the time required for rank up
        String timeToRank = rankTime.get(group);

        if (timeToRank == null || activeTime == null){
            return false;
        }

        // Strings with only numbers
        String strippedActive = activeTime.replaceAll("([a-z])", "");
        String strippedTo = timeToRank.replaceAll("([a-z])", "");

        // Splitting the numbers
        String[] splitActive = strippedActive.split(" ");
        String[] splitTo = strippedTo.split(" ");

        boolean canRankUp = false;

        // Active play time the player has.
        int pDay = Integer.valueOf(splitActive[0]);
        int pHour = Integer.valueOf(splitActive[1]);
        int pMin = Integer.valueOf(splitActive[2]);

        // Values needed to rank up!
        int toDay = Integer.valueOf(splitTo[0]);
        int toHour = Integer.valueOf(splitTo[1]);
        int toMin = Integer.valueOf(splitTo[2]);

        // If day has passed -> rank up
        if (pDay > toDay){
            canRankUp = true;
        }

        // If the day is the same as the rank up day
        if (pDay == toDay){
            // If hour has passed -> rank up
            if (pHour > toHour){
                canRankUp = true;
            }
            // If the hours is the same as the rank up day
            if (pHour == toHour){
                // If the minute has passed -> rank up
                if (pMin > toMin){
                    canRankUp = true;
                }
                // If the minute is the same as the rank up day -> rank up
                if (pMin == toMin){
                    canRankUp = true;
                }
            }
        }
        // If true, the player ranks up!
        return canRankUp;
    }

    /***
     *
     * @param player What player to grab data from
     * @param planType What type of data you want
     * @return A string with the data you requested
     */
    private static String getType(Player player, PlanType planType){
        String plan = "No data";
        switch (planType) {
            case ACTIVE:
                plan = PlaceholderAPI.setPlaceholders(player, "%plan_player_time_active%");
                break;
            case AFK:
                plan = PlaceholderAPI.setPlaceholders(player, "%plan_player_time_afk%");
                break;
            case TOTAL:
                plan = PlaceholderAPI.setPlaceholders(player, "%plan_player_time_total%");
                break;
                default: return null;
        }
        return plan;
    }

    /***
     * Grabbing ranks and how long it will take to reach of them
     */
    private static void grabRankUps(){
        ConfigurationSection section = Config.getSection("ranks");
        if (section == null){
            Bukkit.getLogger().log(Level.SEVERE, "Could't not grab ranks from config.. Please check your config.");
            return;
        }

        for (String ranks : section.getKeys(false)){
            rankTime.put(ranks, Config.getString("ranks."+ranks+".time"));
            rankTo.put(ranks, Config.getString("ranks."+ranks+".to"));
        }
    }
}
