package com.example.pointpligin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

public class PointManager {
    public static final String OBJECTIVE_NAME = "points";

    public void addPoints(Player player, int amount) {
        if (amount <= 0) {
            return;
        }
        int current = getPoints(player);
        setPoints(player, current + amount);
    }

    public boolean removePoints(Player player, int amount) {
        if (amount <= 0) {
            return true;
        }
        int current = getPoints(player);
        if (current < amount) {
            return false;
        }
        setPoints(player, current - amount);
        return true;
    }

    public int getPoints(Player player) {
        return getPoints((OfflinePlayer) player);
    }

    public int getPoints(OfflinePlayer player) {
        Objective objective = getOrCreateObjective();
        return objective.getScore(resolveEntryName(player)).getScore();
    }

    public void setPoints(OfflinePlayer player, int amount) {
        Objective objective = getOrCreateObjective();
        objective.getScore(resolveEntryName(player)).setScore(Math.max(0, amount));
    }

    public Objective getOrCreateObjective() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective objective = board.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            objective = board.registerNewObjective(OBJECTIVE_NAME, "dummy", "Points");
        }
        return objective;
    }

    private String resolveEntryName(OfflinePlayer player) {
        return player.getName() != null ? player.getName() : player.getUniqueId().toString();
    }
}
