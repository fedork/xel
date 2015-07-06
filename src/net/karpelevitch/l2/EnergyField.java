package net.karpelevitch.l2;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Created by fedor on 6/10/15.
 */
public interface EnergyField {
    int MAX_ENERGY = 10000;

    void putEnergy(int coords, int e);

    int readEnergy(int coords);

    void diffuse(World world);

    void read(DataInputStream in) throws IOException;

    void write(DataOutputStream out) throws IOException;
}
