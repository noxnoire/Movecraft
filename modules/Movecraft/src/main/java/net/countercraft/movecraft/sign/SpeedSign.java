package net.countercraft.movecraft.sign;

import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.events.CraftDetectEvent;
import net.countercraft.movecraft.events.SignTranslateEvent;
import net.countercraft.movecraft.utils.SignUtils;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class SpeedSign implements Listener{
    @EventHandler
    public void onCraftDetect(CraftDetectEvent event){
        World world = event.getCraft().getWorld();
        for(MovecraftLocation location: event.getCraft().getHitBox()){
            Block block = location.toBukkit(world).getBlock();
            if(!SignUtils.isSign(block)) {
                return;
            }
            Sign sign = (Sign) block.getState();
            if (ChatColor.stripColor(sign.getLine(0)).equalsIgnoreCase("Speed:")) {
                sign.setLine(1, "0 m/s");
                sign.setLine(2, "0ms");
                sign.setLine(3, "0T");
                sign.update();
            }
        }
    }

    @EventHandler
    public final void onSignTranslate(SignTranslateEvent event) {
        Craft craft = event.getCraft();
        if (!ChatColor.stripColor(event.getLine(0)).equalsIgnoreCase("Speed:")) {
            return;
        }
        event.setLine(1,String.format("%.2f",craft.getSpeed()) + "m/s");
        event.setLine(2,String.format("%.2f",craft.getMeanCruiseTime() * 1000) + "ms");
        event.setLine(3,craft.getTickCooldown() + "T");
    }
}
