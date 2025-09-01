package com.n3rdl0rd.autowdl;

import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import meteordevelopment.meteorclient.MeteorClient;

public class SpiralPathfinder {
    public int x = 0;
    public int z = 0;
    private int legLength = 1;
    private int stepsOnLeg = 0;
    private int direction = 0; // 0=E, 1=S, 2=W, 3=N

    private final transient int spacing;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File STATE_FILE = new File(MeteorClient.mc.runDirectory, "autowdl-state.json");

    public SpiralPathfinder(int renderDistanceInChunks) {
        this.spacing = renderDistanceInChunks * 2 * 16;
    }

    private SpiralPathfinder(int renderDistanceInChunks, int x, int z, int legLength, int stepsOnLeg, int direction) {
        this(renderDistanceInChunks);
        this.x = x;
        this.z = z;
        this.legLength = legLength;
        this.stepsOnLeg = stepsOnLeg;
        this.direction = direction;
    }

    public static SpiralPathfinder load(int renderDistanceInChunks) {
        if (!STATE_FILE.exists()) {
            return new SpiralPathfinder(renderDistanceInChunks);
        }
        try (FileReader reader = new FileReader(STATE_FILE)) {
            SpiralPathfinder loaded = GSON.fromJson(reader, SpiralPathfinder.class);
            SpiralPathfinder n = new SpiralPathfinder(renderDistanceInChunks, loaded.x, loaded.z, loaded.legLength, loaded.stepsOnLeg, loaded.direction);
            n.stepBackwards(); // to prevent State.IDLE in AutoWDLModule from stepping too far forward
            return n;
        } catch (Exception e) {
            System.err.println("Failed to load AutoWDL state, creating a new one.");
            e.printStackTrace();
            return new SpiralPathfinder(renderDistanceInChunks);
        }
    }

    public void save() {
        try (FileWriter writer = new FileWriter(STATE_FILE)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            System.err.println("Failed to save AutoWDL state.");
            e.printStackTrace();
        }
    }

    public BlockPos getNextTarget() {
        switch (direction) {
            case 0: x += spacing; break; // East
            case 1: z += spacing; break; // South
            case 2: x -= spacing; break; // West
            case 3: z -= spacing; break; // North
        }
        stepsOnLeg++;

        if (stepsOnLeg == legLength) {
            stepsOnLeg = 0;
            direction = (direction + 1) % 4;
            if (direction == 0 || direction == 2) {
                legLength++;
            }
        }

        return new BlockPos(x, 0, z);
    }

    /**
     * Reverts the spiral's state to its previous position.
     * This method effectively undoes the last call to getNextTarget().
     * @return The BlockPos of the state that was reverted to.
     */
    public BlockPos stepBackwards() {
        if (x == 0 && z == 0 && legLength == 1 && stepsOnLeg == 0 && direction == 0) {
            return new BlockPos(x, 0, z);
        }

        if (stepsOnLeg == 0) {
            int directionAfterTurn = this.direction;

            direction = (direction + 3) % 4;

            if (directionAfterTurn == 0 || directionAfterTurn == 2) {
                legLength--;
            }

            stepsOnLeg = legLength;
        }

        stepsOnLeg--;

        switch (direction) {
            case 0: x -= spacing; break; // Was East, move back West
            case 1: z -= spacing; break; // Was South, move back North
            case 2: x += spacing; break; // Was West, move back East
            case 3: z += spacing; break; // Was North, move back South
        }

        return new BlockPos(x, 0, z);
    }

}