package com.example.pointpligin.command;

import com.example.pointpligin.PointManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Objective;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RankingCommand implements CommandExecutor {
    private final PointManager pointManager;

    public RankingCommand(PointManager pointManager) {
        this.pointManager = pointManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Objective objective = pointManager.getOrCreateObjective();

        List<PlayerScore> scores = new ArrayList<>();
        for (String entry : objective.getScoreboard().getEntries()) {
            int score = objective.getScore(entry).getScore();
            scores.add(new PlayerScore(entry, score));
        }
        scores.sort(Comparator.comparingInt(PlayerScore::score).reversed());

        sender.sendMessage("§6--- Point Ranking Top 10 ---");
        int topSize = Math.min(10, scores.size());
        for (int i = 0; i < topSize; i++) {
            PlayerScore ps = scores.get(i);
            sender.sendMessage("§e" + (i + 1) + ". §f" + ps.name() + " §7- §a" + ps.score() + " pt");
        }

        if (sender instanceof Player player) {
            int myRank = -1;
            for (int i = 0; i < scores.size(); i++) {
                if (scores.get(i).name().equals(player.getName())) {
                    myRank = i + 1;
                    break;
                }
            }
            if (myRank > 10) {
                int myScore = pointManager.getPoints((OfflinePlayer) player);
                sender.sendMessage("§7...");
                sender.sendMessage("§bあなたの順位: §f" + myRank + " 位 §7- §a" + myScore + " pt");
            } else if (myRank == -1) {
                sender.sendMessage("§bあなたの順位: §f未登録");
            }
        }
        return true;
    }

    private record PlayerScore(String name, int score) {
    }
}
