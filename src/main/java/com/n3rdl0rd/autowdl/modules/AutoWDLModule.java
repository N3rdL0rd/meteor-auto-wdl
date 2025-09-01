package com.n3rdl0rd.autowdl.modules;

import com.n3rdl0rd.autowdl.HomeManager;
import com.n3rdl0rd.autowdl.SpiralPathfinder;
import com.n3rdl0rd.autowdl.DiscordWebhook;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.DeathMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.util.math.BlockPos;

public class AutoWDLModule extends Module {
    private final int renderDistance = 6;
    private final int setHomeInterval = 80;

    private int stuckCheckTimer = 0;
    private BlockPos lastPosition = null;

    private SpiralPathfinder spiral;
    private HomeManager homeManager;

    private enum State { IDLE, MOVING, AWAIT_RESPAWN, AWAIT_HOME, UNSTICKING }
    private State currentState = State.IDLE;

    private BlockPos currentTarget;

    // Hardcoded Discord Webhook URL
    private final DiscordWebhook webhook = new DiscordWebhook("https://discord.com/api/webhooks/1411401135629008907/jwh1zxuFrZ5FO-wz-tvyXl52bV5qBbddvi6EKLgI3Nf8NiMG9WnLHwrm9R4v4op91X2Z");

    public AutoWDLModule() {
        super(Categories.World, "auto-world-download", "Starting at (0, 0), walks in a spiral around the world with Baritone to download the world.");
    }

    private void logInfo(String message) {
        info(message);
        webhook.sendMessage("[AutoWDL - INFO] " + message);
    }

    private void logWarning(String message) {
        warning(message);
        webhook.sendMessage("[AutoWDL - WARNING] " + message);
    }

    private void logError(String message) {
        error(message);
        webhook.sendMessage("[AutoWDL - ERROR] " + message);
    }

    @Override
    public void onActivate() {
        if (!BaritoneUtils.IS_AVAILABLE) {
            logError("AutoWDL doesn't work without Baritone! Disabled module.");
            toggle();
            return;
        }

        this.spiral = SpiralPathfinder.load(renderDistance);
        logInfo(String.format("Loaded spiral state at (X: %d, Z: %d)", spiral.x, spiral.z));

        this.homeManager = new HomeManager(setHomeInterval, this);
        this.currentState = State.IDLE;
        this.currentTarget = null;

        this.stuckCheckTimer = 0;
        this.lastPosition = mc.player != null ? mc.player.getBlockPos() : BlockPos.ORIGIN;
        logInfo("AutoWDL Module Activated.");
    }

    @Override
    public void onDeactivate() {
        PathManagers.get().stop();

        if (spiral != null) {
            spiral.save();
            logInfo("Saved spiral state. Module deactivated.");
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (isActive() && spiral != null) {
            spiral.save();
            logInfo("Game closing, saved AutoWDL state.");
        }
    }

    private void handleStuck() {
        logWarning("Player is stuck for 40 seconds! Initiating recovery procedure.");
        PathManagers.get().stop();
        homeManager.goToHome();
        currentState = State.UNSTICKING;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;
        homeManager.onTick();

        switch (currentState) {
            case IDLE:
                currentTarget = spiral.getNextTarget();
                logInfo(String.format("Moving to next spiral point: (X: %d, Z: %d)", currentTarget.getX(), currentTarget.getZ()));
                setBaritoneTarget(currentTarget.getX(), currentTarget.getZ());
                currentState = State.MOVING;
                stuckCheckTimer = 0;
                lastPosition = mc.player.getBlockPos();
                break;

            case MOVING:
                if (mc.player.getBlockPos().equals(lastPosition)) {
                    stuckCheckTimer++;
                } else {
                    stuckCheckTimer = 0;
                    lastPosition = mc.player.getBlockPos();
                }

                if (stuckCheckTimer > 40 * 20) { // 40 seconds, 20tps
                    handleStuck();
                    break;
                }

                if (hasArrived()) {
                    logInfo("Arrived at target.");
                    spiral.save();
                    logInfo("Saved progress. Calculating next point.");
                    currentState = State.IDLE;
                }
                break;

            case AWAIT_RESPAWN:
                if (mc.player != null && mc.player.isAlive()) {
                    logInfo("Respawned! Going home...");
                    homeManager.goToHome();
                    currentState = State.AWAIT_HOME;
                }
                break;

            case AWAIT_HOME:
                if (homeManager.isHome()) {
                    logInfo("Teleport complete. Resuming world download process.");
                    currentState = State.IDLE;
                }
                break;

            case UNSTICKING:
                if (homeManager.isHome()) {
                    logInfo("Teleported home. Re-attempting path to target.");
                    currentState = State.IDLE;
                }
                break;
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event)  {
        if (event.packet instanceof DeathMessageS2CPacket packet) {
            Entity entity = mc.world.getEntityById(packet.playerId());
            if (entity == mc.player) {
                handlePlayerDeath();
            }
        }
        else if (event.packet instanceof GameMessageS2CPacket packet) {
            String message = packet.content().getString();
            webhook.sendMessage("[CHAT] " + message); // TODO: heads, embeds, and more, plus r2s and responding from discord
        }
    }

    private void handlePlayerDeath() {
        logError("You died! Stopping all movement and awaiting respawn.");
        PathManagers.get().stop();
        currentState = State.AWAIT_RESPAWN;
    }

    private boolean hasArrived() {
        if (currentTarget == null || mc.player == null) return false;
        return getSquaredDistanceTo(currentTarget) <= 225; // 15*15 block radius
    }

    private double getSquaredDistanceTo(BlockPos target) {
        if (target == null || mc.player == null) return Double.MAX_VALUE;

        double dx = mc.player.getX() - (target.getX() + 0.5);
        double dz = mc.player.getZ() - (target.getZ() + 0.5);

        return dx * dx + dz * dz;
    }

    private void setBaritoneTarget(int x, int z) {
        PathManagers.get().moveTo(new BlockPos(x, 120, z), false);
    }
}