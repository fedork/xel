package net.karpelevitch.l2;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

import static java.lang.Integer.signum;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.System.arraycopy;
import static java.util.Arrays.copyOf;
import static net.karpelevitch.l2.EnergyField.MAX_ENERGY;

public class World {
    public static final int DIFFUSE_FACTOR = 3;
    static final int MAX_MEM = 3000;
    static final Random RANDOM = new Random(System.currentTimeMillis());
    private static final int MUTATION_RATE = 100;
    public final LinkedList<P> list = new LinkedList<>();
    final long MAX_P = Runtime.getRuntime().maxMemory() / MAX_MEM / 3;
    private final int ENERGY_PLUS = 30000;
    private final int size_x;
    private final int size_y;
    private final Cell[] map;
    private final LinkedList<P> tmplist = new LinkedList<>();
    private final int[] sources;
    private final EnergyField ef;
    public int maxgen;
    private int colorCount;
    private int maxenergy = ENERGY_PLUS;
    private byte[] scratchpad = new byte[MAX_MEM * 100];

    public World(int size_x, int size_y, int colorCount) {
        this.size_x = size_x;
        this.size_y = size_y;
        this.colorCount = colorCount;
        ef = createEnergyField(size_x, size_y);
        map = new Cell[size_x * size_y];
        for (int i = 0; i < map.length; i++) {
            map[i] = new Cell();
        }
        sources = new int[5];
        for (int i = 0; i < sources.length; i++) {
            sources[i] = RANDOM.nextInt(map.length);
        }
        RANDOM.nextBytes(scratchpad);

        createRandom(MAX_P / 5);

        randomizeEnergy();

        System.out.println("MAX_P = " + MAX_P);
    }

    protected EnergyField createEnergyField(final int size_x, final int size_y) {
        return new EnergyField() {
            private final int[] energy = new int[size_x * size_y];

            @Override
            public void putEnergy(int coords, int e) {
                energy[coords] = max(0, min(energy[coords] + e, MAX_ENERGY));
            }

            @Override
            public int readEnergy(int coords) {
                return energy[coords];
            }

            @Override
            public void diffuse(World world) {
                for (int i = 0; i < world.map.length / DIFFUSE_FACTOR; i++) {
                    int coords1 = RANDOM.nextInt(world.map.length);
                    int coords2 = world.getCoords(coords1, RANDOM.nextInt(11) - 5, RANDOM.nextInt(11) - 5);
                    int de = (readEnergy(coords1) - readEnergy(coords2)) / 2;
                    putEnergy(coords1, -de);
                    putEnergy(coords2, de);
                }
            }
        };
    }

    public int getColorCount() {
        return colorCount;
    }

    void createRandom(double count) {
//        if (Runtime.getRuntime().freeMemory() < 10 * MAX_MEM) return;
        for (int i = 0; i < count; i++) {
            if (RANDOM.nextInt((int) Math.ceil(1 / count)) == 0) {
                int coords = RANDOM.nextInt(map.length);

                int length = 1 + RANDOM.nextInt(MAX_MEM);
                byte[] mem;
                if (RANDOM.nextBoolean()) {
                    int from = RANDOM.nextInt(scratchpad.length - length);
                    mem = Arrays.copyOfRange(scratchpad, from, from + length);
                } else {
                    mem = new byte[length];
                    RANDOM.nextBytes(mem);
                }
                int ip = RANDOM.nextInt(mem.length);
                createOrAppend(coords, mem, mem.length, ip, (byte) RANDOM.nextInt(getColorCount()), RANDOM.nextInt(ENERGY_PLUS * 10), 0, RANDOM.nextBoolean());
            }
        }
        addTempList();
    }

    void randomizeEnergy() {
        for (int i = 0; i < map.length; i++) {
            ef.putEnergy(i, RANDOM.nextInt(ENERGY_PLUS));
        }
    }

    public int draw(boolean mode, RGBDraw rgbDraw) {
        int totale = 0;
//            int maxe = 0;
        for (int i = 0; i < size_x; i++) {
            for (int j = 0; j < size_y; j++) {
                int coords = getCoords(i, j);
                Cell cell = map[coords];
                int e = ef.readEnergy(coords);
                totale += e;
                int b = e * 256 / (MAX_ENERGY + 1);
                if (mode) {
                    if (cell.p == null) {
                        rgbDraw.drawMono(i, j, b);
                    } else {
                        rgbDraw.drawColor(i, j, cell.p.color);
                    }
                } else {
                    rgbDraw.drawMono(i, j, b);
                }
            }
        }
//            maxenergy = maxe;
        maxenergy += signum(4 * totale / map.length - maxenergy);
        if (maxenergy < 256) maxenergy = 256;
        return totale;
    }

    public int update() {
        fountain();

        ef.diffuse(this);

        createRandom(10.0 / (list.size() + 1));

        return liveOne();

    }

    private int liveOne() {
        int maxage = 0;
        maxgen = 0;
        for (Iterator<P> iterator = list.iterator(); iterator.hasNext(); ) {
            P p = iterator.next();
            // cleanup dead
            if (p.coords < 0) {
                iterator.remove();
                continue;
            }

            if (RANDOM.nextInt(100) == 0) {
                System.arraycopy(p.mem, 0, scratchpad, RANDOM.nextInt(scratchpad.length - p.memlength), p.memlength);
                scratchpad[RANDOM.nextInt(scratchpad.length)] = (byte) RANDOM.nextInt(256);
            }

            if (p.memlength > 0 && readEnergy(p) > 0) {
                switch (RANDOM.nextInt(3 + MUTATION_RATE * MAX_ENERGY / (readEnergy(p) + 1))) {
                    case 0:
                        p.mem[RANDOM.nextInt(p.memlength)] = (byte) RANDOM.nextInt(256);
                        break;
                    case 1:
                        p.copy(RANDOM.nextInt(p.memlength), RANDOM.nextInt(p.memlength), RANDOM.nextInt(1 + p.memlength / 2), RANDOM.nextBoolean());
                        break;
                    case 2:
                        p.delete(RANDOM.nextInt(p.memlength), 1 + RANDOM.nextInt(1 + p.memlength / 2));
                        break;
                }
            }


            p.execOne();

            if (p.age > maxage) {
                maxage = p.age;
            }
            if (p.generation > maxgen) maxgen = p.generation;
        }
        addTempList();
        return maxage;
    }

    private void addTempList() {
        list.addAll(tmplist);
        tmplist.clear();
    }

    private void fountain() {
//            maxenergy = 0;
        for (int i = 0; i < sources.length; i++) {
            if (RANDOM.nextInt(DIFFUSE_FACTOR) == 0) {
                sources[i] = getCoords(sources[i], RANDOM.nextInt(3) - 1, RANDOM.nextInt(3) - 1);
            }
            ef.putEnergy(getCoords(sources[i], RANDOM.nextInt(10), RANDOM.nextInt(10)), ENERGY_PLUS);
//            ef.putEnergy(RANDOM.nextInt(map.length), -RANDOM.nextInt(ENERGY_PLUS));
        }
    }

    private int getY(int coords) {
        return coords / size_x;
    }

    private int getX(int coords) {
        return coords % size_x;
    }

    private int getCoords(int x, int y) {
        return y * size_x + x;
    }

    public void shoot(P p, int dx, int dy, int e) {
        if (e > 0) {
            Cell cell = map[getCoords(p.coords, dx, dy)];
            if (cell.p != null) {
                cell.p.energy -= RANDOM.nextInt(e * 10);
            }
        }
    }

    public void clone(P p, int dx, int dy, byte[] mem, int memlength, int ip, byte color, int e, boolean okToAppend) {
        int coords = getCoords(p.coords, dx, dy);
        createOrAppend(coords, mem, memlength, ip, color, e, p.generation + 1, okToAppend);
    }

    private void createOrAppend(int coords, byte[] mem, int memlength, int ip, byte color, int e, int generation, boolean okToAppend) {
//        if (Runtime.getRuntime().freeMemory() < 10 * MAX_MEM || list.size() > MAX_P) return;
        Cell cell = map[coords];
        if (cell.p == null) {
            if (list.size() + tmplist.size() > MAX_P) return;
            cell.p = new P(this, coords, copyOf(mem, memlength), memlength, ip, color, e / 2, generation);
            tmplist.add(cell.p);
        } else if (okToAppend) {
            int length = min(memlength, MAX_MEM - cell.p.memlength);
            if (cell.p.memlength + length > cell.p.mem.length) {
                byte[] newmem = new byte[cell.p.memlength + length];
                arraycopy(cell.p.mem, 0, newmem, 0, cell.p.memlength);
                arraycopy(mem, 0, newmem, cell.p.memlength, length);
                cell.p.mem = newmem;
                cell.p.memlength += length;
            } else {
                arraycopy(mem, 0, cell.p.mem, cell.p.memlength, length);
                cell.p.memlength += length;
            }
            cell.p.energy = min(MAX_ENERGY, cell.p.energy + e / 2);
            if (cell.p.generation < generation) cell.p.generation = generation;
        }
    }

    private int getCoords(int coords, int dx, int dy) {
        int x = (getX(coords) + dx + size_x) % size_x;
        int y = (getY(coords) + dy + size_y) % size_y;
        return getCoords(x, y);
    }

    public void putEnergy(P p, int dx, int dy, int e) {
        ef.putEnergy(getCoords(p.coords, dx, dy), e);
    }

    public int getEnergy(P p, int e) {
        e = min(e, readEnergy(p));
        ef.putEnergy(p.coords, -e);
        return e;
    }

    public int readEnergy(P p) {
        return readEnergy(p.coords);
    }

    public int readEnergy(P p, int dx, int dy) {
        return readEnergy(getCoords(p.coords, dx, dy));
    }

    public int readEnergy(int coords) {
        return ef.readEnergy(coords);
    }

    public void write(P p, byte[] bytes, int from, int length) {
        map[p.coords].info[0] = (byte) length;
        System.arraycopy(bytes, from, map[p.coords].info, 1, length);
    }

    public byte[] read(P p, int dx, int dy) {
        return map[getCoords(p.coords, dx, dy)].info;
    }

    public void move(P p, int dx, int dy) {
        int coords = getCoords(p.coords, dx, dy);
        Cell oldCell = map[p.coords];
        Cell newCell = map[coords];
        if (newCell.p == null) {
            oldCell.p = null;
            newCell.p = p;
            p.coords = coords;
        }
    }

    public void dead(P p) {
        if (p.coords >= 0) {
            map[p.coords].p = null;
            p.coords = -1;
        }
    }

    public byte getColor(P p, int dx, int dy) {
        Cell cell = map[getCoords(p.coords, dx, dy)];
        return (byte) (cell.p == null ? 255 : cell.p.color);
    }

    public interface RGBDraw {

        void drawMono(int i, int j, int b);

        void drawColor(int i, int j, int color);

        void done();
    }

    private static class Cell {
        public P p;
        public byte[] info = new byte[256];
    }
}
