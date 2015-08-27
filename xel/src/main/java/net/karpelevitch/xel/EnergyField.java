package net.karpelevitch.xel;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public interface EnergyField {
    int MAX_ENERGY = 10000;

    void putEnergy(int coords, int e);

    int readEnergy(int coords);

    void diffuse(World world);

    void read(DataInputStream in, int version) throws IOException;

    void write(DataOutputStream out) throws IOException;
}
