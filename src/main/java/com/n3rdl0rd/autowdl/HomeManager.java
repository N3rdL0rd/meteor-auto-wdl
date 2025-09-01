package com.n3rdl0rd.autowdl;

import meteordevelopment.meteorclient.utils.player.ChatUtils;
import static meteordevelopment.meteorclient.MeteorClient.mc;

import com.n3rdl0rd.autowdl.modules.AutoWDLModule;

public class HomeManager {
    private final int setHomeIntervalTicks;
    private int setHomeTimer;
    private boolean isWaitingForSetHome = false;
    private int setHomeWaitTicks = 0;

    private boolean isWaitingForHomeTeleport = false;
    private int homeTeleportTimer = 0;

    private AutoWDLModule module;

    public HomeManager(int intervalSeconds, AutoWDLModule module) {
        this.setHomeIntervalTicks = intervalSeconds * 20;
        this.setHomeTimer = this.setHomeIntervalTicks;
        this.module = module;
    }

    /**
     * This method should be called every client tick.
     * It manages the timers for both /sethome and /home.
     */
    public void onTick() {
        setHomeTimer++;

        if (isWaitingForSetHome) {
            setHomeWaitTicks--;
            if (setHomeWaitTicks <= 0) {
                ChatUtils.sendPlayerMsg("/sethome");
                isWaitingForSetHome = false;
                module.info("Set home.");
            }
        }

        if (setHomeTimer >= setHomeIntervalTicks) {
            if (mc.player != null && mc.world != null && mc.world.isSkyVisible(mc.player.getBlockPos())) {
                module.info("Player is under open sky.");
                ChatUtils.sendPlayerMsg("/w nonexistentuser135246 hello");

                this.isWaitingForSetHome = true;
                this.setHomeWaitTicks = 5 * 20;
                this.setHomeTimer = 0;
            } else {
                module.warning("Player is not under open sky.");
                setHomeTimer = setHomeIntervalTicks - (setHomeIntervalTicks/4);
            }
        }

        if (isWaitingForHomeTeleport) {
            homeTeleportTimer--;
            if (homeTeleportTimer <= 0) {
                isWaitingForHomeTeleport = false;
                module.info("Home.");
            }
        }
    }

    /**
     * Sends the /home command and starts the 17-second teleport timer.
     */
    public void goToHome() {
        if (!isWaitingForHomeTeleport) {
            ChatUtils.sendPlayerMsg("/home");
            module.info("Waiting...");

            this.isWaitingForHomeTeleport = true;
            this.homeTeleportTimer = 17 * 20;
        }
    }

    public boolean isHome() {
        return !isWaitingForHomeTeleport;
    }
}