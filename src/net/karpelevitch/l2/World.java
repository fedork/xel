package net.karpelevitch.l2;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.System.arraycopy;
import static java.util.Arrays.copyOf;
import static net.karpelevitch.l2.EnergyField.MAX_ENERGY;

public class World {
    public static final int DIFFUSE_FACTOR = 3;
    public static final int SOURCE_EXPECTANCY_BASE = 2500;
    static final int MAX_MEM = 3000;
    static final Random RANDOM = new Random(System.currentTimeMillis());
    private static final int MUTATION_RATE = 100;
    private static final int VERSION = 2;
    public final LinkedList<P> list = new LinkedList<>();
    final long MAX_P = Runtime.getRuntime().maxMemory() / MAX_MEM / 3;
    private final int ENERGY_PLUS = 3000;
    private final Cell[] map;
    private final LinkedList<P> tmplist = new LinkedList<>();
    private final LinkedList<ESource> sources = new LinkedList<>();
    private final EnergyField ef;
    public int maxgen;
    private int size_x;
    private int size_y;
    private int colorCount;
    private byte[] scratchpad = new byte[MAX_MEM * 100];


    public World(int size_x, int size_y, int colorCount, DataInputStream in) {
        this.size_x = size_x;
        this.size_y = size_y;
        this.colorCount = colorCount;
        map = new Cell[size_x * size_y];
        for (int i = 0; i < map.length; i++) {
            map[i] = new Cell();
        }
        ef = createEnergyField(size_x, size_y);
        if (in == null) {
            randomize();
        } else {
            try {
                read(in);
                in.close();
            } catch (IOException e) {
                System.err.println("Failed to restore from file - starting from scratch");
                e.printStackTrace();
                randomize();
            }
        }

        System.out.println("MAX_P = " + MAX_P);
    }

    public void read(DataInputStream in) throws IOException {
        synchronized (World.class) {
            @SuppressWarnings("unused")
            int tag = in.readInt();
            int sourceCount;
            int version = in.readInt();
            System.out.println("version = " + version);
            if (version >= 2) {
//                size_x = in.readInt();
                in.readInt();
//                size_y = in.readInt();
                in.readInt();
//                colorCount = in.readInt();
                in.readInt();
            }
            sourceCount = in.readInt();
            System.out.println("sourceCount = " + sourceCount);

            for (int i = 0; i < sourceCount; i++) {
                int coord = in.readInt();
                int dir = in.readInt();
                sources.add(new ESource(coord, dir));
            }
            int scratchpadSize = in.readInt();
            in.readFully(scratchpad, 0, min(scratchpadSize, scratchpad.length));
            if (scratchpadSize > scratchpad.length) {
                in.skipBytes(scratchpadSize - scratchpad.length);
            }
            int mapsize = in.readInt();
            System.out.println("mapsize = " + mapsize);
            System.out.println("map.length = " + map.length);
            byte[] devnull = new byte[256];
            for (int i = 0; i < mapsize; i++) {
                in.readFully(i < map.length ? map[i].info : devnull);
            }
            int pCount = in.readInt();
            System.out.println("pCount = " + pCount);
            for (int i = 0; i < pCount; i++) {
                int coords = in.readInt();
                int memSize = in.readInt();
//                System.out.println("memSize = " + memSize);
                byte[] mem = new byte[memSize];
                in.readFully(mem);
                int ip = in.readInt();
                byte c = in.readByte();
                int e = in.readInt();
                int gen = in.readInt();
                if (coords > 0 && coords < map.length) {
                    P p = new P(this, coords, mem, memSize, ip, c, e, gen);
                    list.add(p);
                    map[p.coords].p = p;
                }
            }
            ef.read(in);
        }
    }

    public void write(DataOutputStream out) throws IOException {
        synchronized (World.class) {
            out.writeInt(0);
            out.writeInt(VERSION);
            out.writeInt(size_x);
            out.writeInt(size_y);
            out.writeInt(colorCount);
            out.writeInt(sources.size());
            for (ESource source : sources) {
                out.writeInt(source.coord);
                out.writeInt(source.dir);
            }
            out.writeInt(scratchpad.length);
            out.write(scratchpad);
            out.writeInt(map.length);
            for (Cell c : map) {
                out.write(c.info);
            }
            out.writeInt(list.size());
            for (P p : list) {
                out.writeInt(p.coords);
                out.writeInt(p.memlength);
                out.write(p.mem, 0, p.memlength);
                out.writeInt(p.ip);
                out.writeByte(p.color);
                out.writeInt(p.energy);
                out.writeInt(p.generation);
            }
            ef.write(out);
            out.flush();
        }
    }

    public void randomize() {
        int sourceCount = 2;
        for (int i = 0; i < sourceCount; i++) {
            addRandomSource();
        }
        RANDOM.nextBytes(scratchpad);

        createRandom(MAX_P / 5);

        randomizeEnergy();
    }

    void addRandomSource() {
        addSource(RANDOM.nextInt(map.length));
    }

    private boolean addSource(int coord) {
        return sources.add(new ESource(coord, getRandomDirection()));
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

            @Override
            public void read(DataInputStream in) throws IOException {
                int size = in.readInt();
                for (int i = 0; i < size; i++) {
                    int e = in.readInt();
                    if (i < energy.length) {
                        energy[i] = e;
                    }
                }
            }

            @Override
            public void write(DataOutputStream out) throws IOException {
                out.writeInt(energy.length);
                for (int e : energy) {
                    out.writeInt(e);
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

    public void draw(boolean mode, RGBDraw rgbDraw) {
        draw(mode, rgbDraw, this.size_x, this.size_y, 0, 0);
    }

    public void draw(boolean mode, RGBDraw rgbDraw, int width, int height, int offsetX, int offsetY) {
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                int coords = getCoords((offsetX + i + size_x) % size_x, (offsetY + j + size_y) % size_y);
                Cell cell = map[coords];
                int e = ef.readEnergy(coords);
                int b = min(255, e * 1000 / (MAX_ENERGY + 1));
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
                switch (RANDOM.nextInt(3 + MUTATION_RATE)) {
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
        if (sources.size() < 8 && RANDOM.nextInt(sources.size() * 10 + SOURCE_EXPECTANCY_BASE) == 3) {
            addRandomSource();
        } else if (sources.size() > 2 && RANDOM.nextInt((10 - sources.size()) * 10 + SOURCE_EXPECTANCY_BASE) == 3) {
            sources.remove(RANDOM.nextInt(sources.size()));
        }
        for (ESource source : sources) {
            if (RANDOM.nextInt(50) == 3) {
                if (RANDOM.nextInt(100) == 5) {
                    source.dir = getRandomDirection();
                }
                source.coord = (source.coord + source.dir) % map.length;
            }
            ef.putEnergy(getCoords(source.coord, RANDOM.nextInt(10), RANDOM.nextInt(10)), ENERGY_PLUS);
//            ef.putEnergy(RANDOM.nextInt(map.length), -RANDOM.nextInt(ENERGY_PLUS));
        }
    }

    private int getRandomDirection() {
        return map.length + size_x * (RANDOM.nextInt(5) - 2) + (RANDOM.nextInt(5) - 2);
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
            if (cell.p != null && cell.p.energy > 0 && RANDOM.nextInt(max(1, MAX_ENERGY / e)) < RANDOM.nextInt(max(1, MAX_ENERGY / cell.p.energy))) {
                dead(cell.p);
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
            cell.p = new P(this, coords, copyOf(mem, memlength), memlength, ip, color, e / 5, generation);
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
            cell.p.energy = min(MAX_ENERGY, cell.p.energy + e / 10);
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
            ef.putEnergy(p.coords, p.energy / 2 + 10);
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
        public final byte[] info = new byte[256];
        public P p;
    }

    private class ESource {
        private int coord;
        private int dir;

        public ESource(int coord, int dir) {
            this.coord = coord;
            this.dir = dir;
        }
    }
}
