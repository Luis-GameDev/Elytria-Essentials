package me.luisgamedev.elytriaEssentials.ArrowSkillHandler;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.ProjectileHitEvent;

public class ArrowSkillHandler {
    public Entity[] arrows;

    public void onCommand(Player player, String[] args) {
        switch (args[0]) {
            // enchant particle
            case "arcaneshot":
            // flame particle
            case "flamethorn":
            // small ghast particle
            case "webtrap":
            // compost particles
            case "toxic_arrows":
            // soul particle
            case "doomshot":
            // damage indicator particle
            case "bloodarrow":
            // wax off particle
            case "thundershot":
        }
    }

    @EventHandler
    public void onArrowHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Arrow) {
            if(arrows.has(event.getEntity())) {
                switch (ability) {
                    case "arcaneshot":
                        event.getHitEntity().teleport(event.getEntity().getShooter());
                    case "flamethorn":
                        //make all arrows for the next 5sec burning
                    case "webtrap":
                        // spawn a 4x3x3 cobweb around the hit position that disappear after 5 sec and cant be broken
                    case "toxic_arrows":
                        // make all arrows poisoned for the next 5 sec
                    case "doomshot":
                        // the arrow knocks the target away very far and deals double damage
                    case "bloodarrow":
                        // the arrow deals 8 HP extra damage
                    case "thundershot":
                        // the arrow has massive impact and spawns multiple lightnings on the impact location that do not ignite the ground
                }
            }
        }
    }
}
