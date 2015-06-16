package net.karpelevitch.l2;

/**
 * Created by fedor on 6/10/15.
 */
public interface EnergyField {
    int MAX_ENERGY = 10000;

    void putEnergy(int coords, int e);

    int readEnergy(int coords);

    void diffuse(World world);
}
