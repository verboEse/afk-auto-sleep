package dev.spagurder.afkautosleep.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class EventHandler {

    private static final long AFK_DELAY_TICKS = 20 * 30;
    private static final int LOGOUT_DELAY_TICKS = 20 * 5;
    private static final String LOGOUT_WARNING = "No bed in reach, logging out in 5 seconds.";
    private static int pendingLogoutTicks;
    private static boolean wasSleeping;
    private static long inactiveTicks;
    private static Vec3 lastPosition;
    private static float lastXRotation;
    private static float lastYRotation;

    public static void onClientJoin() {
        pendingLogoutTicks = 0;
        wasSleeping = false;
        resetActivity();
        State.disable();
        State.logState();
    }

    public static void onClientTick(Minecraft client) {
        if (client.level == null || client.player == null) {
            wasSleeping = false;
        } else {
            boolean isSleeping = client.player.isSleeping();
            if (isSleeping && !wasSleeping && State.isEnabled()) {
                SleepMessageSelector.select(
                        ClientConfig.sleepMessages(),
                        hasOtherPlayer(client),
                        ClientConfig.onlyWhenOtherPlayersOnline()
                ).ifPresent(message -> Util.sendChat(client, message));
            }
            wasSleeping = isSleeping;
        }

        while (KeyMappings.OPEN_SETTINGS.consumeClick()) {
            ConfigScreen.open(client);
        }

        while (KeyMappings.SELECT_BED.consumeClick()) {
            if (State.isEnabled()) {
                State.disable(client);
            } else {
                State.tryEnable(client);
            }
        }

        if (pendingLogoutTicks > 0) {
            pendingLogoutTicks--;
            if (pendingLogoutTicks == 0) {
                disconnect(client);
            }
            return;
        }

        if (!State.isEnabled()) {
            resetActivity();
            return;
        }
        if (client.level == null || client.player == null) {
            resetActivity();
            return;
        }
        updateActivity(client);
        if (inactiveTicks < AFK_DELAY_TICKS) return;
        if (client.level.getGameTime() % 100 == 0) {
            if (BedUtils.isSleepTime(client) && !client.player.isSleeping()) {
                BlockPos bedPos = State.getSelectedBed();
                String errorMessage = BedUtils.checkBedError(client, bedPos);
                if (errorMessage != null) {
                    Util.messageClient(client, errorMessage, false);
                    State.disable(client);
                    return;
                }
                BedUtils.SleepAttemptResult result = BedUtils.trySleep(client, State.getSelectedBed());
                if (result != BedUtils.SleepAttemptResult.OUT_OF_RANGE) return;
                boolean otherPlayerOnline = hasOtherPlayer(client);
                if (SleepFallbackPolicy.shouldDisconnect(
                    result,
                    ClientConfig.logoutWhenNoBed(),
                    otherPlayerOnline
                )) {
                    Util.sendChat(client, ClientConfig.logoutMessage());
                    Util.messageClient(client, LOGOUT_WARNING, false);
                    pendingLogoutTicks = LOGOUT_DELAY_TICKS;
                    return;
                }
                Util.messageClient(client, "Too far to interact with bed!", false);
            }
        }
    }

    private static void updateActivity(Minecraft client) {
        Vec3 position = client.player.position();
        boolean positionChanged = lastPosition == null || position.distanceToSqr(lastPosition) > 0;
        boolean viewChanged = lastPosition == null
                || client.player.getXRot() != lastXRotation
                || client.player.getYRot() != lastYRotation;
        lastPosition = position;
        lastXRotation = client.player.getXRot();
        lastYRotation = client.player.getYRot();
        if (positionChanged || viewChanged || hasInputActivity(client)) {
            inactiveTicks = 0;
            return;
        }
        inactiveTicks++;
    }

    private static boolean hasInputActivity(Minecraft client) {
        return client.options.keyUp.isDown()
                || client.options.keyDown.isDown()
                || client.options.keyLeft.isDown()
                || client.options.keyRight.isDown()
                || client.options.keyJump.isDown()
                || client.options.keyShift.isDown()
                || client.options.keySprint.isDown()
                || client.options.keyUse.isDown()
                || client.options.keyAttack.isDown();
    }

    private static void resetActivity() {
        inactiveTicks = 0;
        lastPosition = null;
    }

    private static boolean hasOtherPlayer(Minecraft client) {
        if (client.level == null || client.player == null) return false;
        return client.getConnection() != null && client.getConnection().getOnlinePlayers().size() > 1;
    }

    private static void disconnect(Minecraft client) {
        //? if >1.21.8 {
        client.disconnect(new TitleScreen(), false);
        //?} else {
        /*client.disconnect();
        *///?}
    }

}
