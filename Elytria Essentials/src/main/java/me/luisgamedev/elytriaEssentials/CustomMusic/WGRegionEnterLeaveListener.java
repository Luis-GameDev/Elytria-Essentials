package me.luisgamedev.elytriaEssentials.CustomMusic;

import com.github.NetzkroneHD.WGRegionEvents.events.RegionEnterEvent;
import com.github.NetzkroneHD.WGRegionEvents.events.RegionLeaveEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class WGRegionEnterLeaveListener implements Listener {

    private final WGRegionMusicPlugin plugin;
    private final MusicService music;

    public WGRegionEnterLeaveListener(WGRegionMusicPlugin plugin, MusicService music) {
        this.plugin = plugin;
        this.music = music;
    }

    @EventHandler
    public void onEnter(RegionEnterEvent e) {
        String id = e.getRegion().getId();
        if (!music.isAllowedRegion(id)) return;
        music.startFor(e.getPlayer(), id);
    }

    @EventHandler
    public void onLeave(RegionLeaveEvent e) {
        String id = e.getRegion().getId();
        if (!music.isAllowedRegion(id)) return;
        music.stopIfLeftRegion(e.getPlayer(), null);
    }
}
