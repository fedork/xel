package net.karpelevitch.l2;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

import static java.lang.Integer.signum;
import static java.lang.Math.*;
import static java.lang.System.arraycopy;
import static java.util.Arrays.copyOf;

public class Main {

    public static final int SIZE = 300;
    public static final int ENERGY_PLUS = 10000;
    public static final int SOURCE_DENSITY = 30000;
    public static final int DIFFUSE_FACTOR = 10;
    public static final Color[] BASE_COLORS = new Color[]{Color.GREEN, Color.BLUE, Color.CYAN, Color.MAGENTA, Color.ORANGE, Color.PINK, Color.RED, Color.YELLOW};
    @SuppressWarnings("PointlessArithmeticExpression")
    public static final Random RANDOM = new Random(0L + System.currentTimeMillis());
    public static final int[] dx = {0, 1, 1, 1, 0, -1, -1, -1, 0, 2, 2, 2, 0, -2, -2, -2};
    public static final int[] dy = {-1, -1, 0, 1, 1, 1, 0, -1, -2, -2, 0, 2, 2, 2, 0, -2};
    private static final int MAX_ENERGY = 100000;
    private static final int MAX_MEM = 50000;
    private static final int COLOR_COUNT;
    private static final int[] COLORS;

    static {
        COLOR_COUNT = BASE_COLORS.length * 3;
        COLORS = new int[COLOR_COUNT];
        for (int i = 0; i < BASE_COLORS.length; i++) {
            Color color = BASE_COLORS[i];
            COLORS[i * 3] = color.getRGB();
            COLORS[i * 3 + 1] = color.darker().getRGB();
            COLORS[i * 3 + 2] = color.darker().darker().getRGB();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        JFrame frame = new JFrame("l2");
        frame.setBounds(0, 0, SIZE, SIZE);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        frame.setVisible(true);
        int rgb = Color.GREEN.getRGB();
        System.out.println("rgb = " + Integer.toHexString(rgb));

        frame.setContentPane(new JLabel(new ImageIcon(image)));
        final int[] mode = {0};
        frame.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent keyEvent) {

            }

            @Override
            public void keyPressed(KeyEvent keyEvent) {
                if (keyEvent.getKeyChar() == ' ') {
                    mode[0] ^= 1;
                }
            }

            @Override
            public void keyReleased(KeyEvent keyEvent) {

            }
        });
        frame.pack();

        World world = new World(SIZE);

        world.createRandom(100);

        world.randomizeEnergy();

        long startTime = System.currentTimeMillis();
        int gen = 0;
        int frames = 0;
        //noinspection InfiniteLoopStatement
        while (true) {

            int maxage = world.update();

            int totalEnergy = world.draw(mode[0] == 0, image);

            frame.repaint();
            gen++;
            frames++;
            if (frames >= 100) {
                long now = System.currentTimeMillis();
                System.out.printf("%d\t%d\t%d\t%d\t%d\t%d\n", gen, frames * 1000 / (now - startTime), world.list.size(), totalEnergy, maxage, world.maxgen);
                startTime = now;
                frames = 0;
            }
        }
    }

    static class P {
        private static final int INC = 0;
        private static final int DEC = 1;
        private static final int SHIFT = 2;
        private static final int ADD = 3;
        private static final int COPY = 4;
        private static final int DELETE = 5;
        private static final int JUMP = 6;
        private static final int SETCOLOR = 7;
        private static final int READCOLOR = 8;
        private static final int MOVE = 9;
        private static final int READ = 10;
        private static final int WRITE = 11;
        private static final int EAT = 12;
        private static final int CLONE = 13;
        private static final int RND = 14;
        private static final int SHOOT = 15;
        final int generation;
        private final World world;
        public int coords;
        byte[] mem;
        int memlength;
        int ip;
        byte color;
        int energy;
        int age = 0;

        public P(World world, int coords, byte[] mem, int memlength, int ip, byte color, int energy, int generation) {
            this.world = world;
            this.coords = coords;
            this.mem = mem;
            this.memlength = memlength;
            this.ip = ip;
            this.color = color;
            this.energy = energy;
            this.generation = generation;
        }

        void execOne() {
            if (energy <= 0 || memlength == 0) {
                die();
                return;
            }
            age++;
            energy--;
            int cmd = nextByte();
            int cmdExtra = cmd & 0xf;
            switch (cmd >>> 4) {
                case INC:
                    mem[getIndex(nextByte())] += cmdExtra;
                    break;
                case DEC:
                    mem[getIndex(nextByte())] -= cmdExtra;
                    break;
                case SHIFT:
                    if (cmdExtra > 7) {
                        mem[getIndex(nextByte())] <<= (cmdExtra & 7);
                    } else {
                        mem[getIndex(nextByte())] >>>= cmdExtra;
                    }
                    break;
                case ADD: {
                    int offset1 = nextByte();
                    int offset2 = nextByte();
                    if (cmdExtra < 8) {
                        mem[getIndex(offset1)] += mem[getIndex(offset2)];
                    } else {
                        mem[getIndex(offset1)] -= mem[getIndex(offset2)];
                    }
                    break;
                }
                case COPY: {
                    int from = nextByte();
                    int to = nextByte();
                    int length = nextByte();
                    from = getIndex(from);
                    to = getIndex(to);
                    if (from != to) {
                        if (cmdExtra > 7) {
                            int maxlen = min(min(memlength - from, memlength - to), abs(from - to));
                            length = min(length, maxlen);
                            arraycopy(mem, from, mem, to, length);
                        } else {
                            int maxlen = min(min(memlength - from, memlength - to), MAX_MEM - memlength);
                            if (to > from) {
                                maxlen = min(maxlen, to - from);
                            }
                            length = min(length, maxlen);
                            if (length > 0) {
                                byte[] newmem;
                                if (memlength + length > mem.length) {
                                    newmem = new byte[memlength + length];
                                    arraycopy(mem, 0, newmem, 0, to);
                                } else {
                                    newmem = mem;
                                }
                                arraycopy(mem, from, newmem, to, length);
                                arraycopy(mem, to, newmem, to + length, memlength - to);
                                memlength += length;
                                mem = newmem;
                            }
                        }
                    }
                    break;
                }
                case DELETE: {
                    int length = nextByte();
                    int index = getIndex(nextByte());
                    length = min(length, memlength - index);
                    if (length > 0) {
                        int remlen = memlength - index - length;
                        if (remlen > 0) {
                            arraycopy(mem, index + length, mem, index, remlen);
                        }
                        memlength -= length;
                        if (ip >= memlength) ip = 0;
                    }
                    break;
                }
                case JUMP: {
                    int length = nextByte() + 1;
                    int multiplier = cmdExtra > 7 ? -(1 << (cmdExtra - 8)) : 1 << cmdExtra;
                    ip = getIndex(multiplier * length);
                    break;
                }
                case SETCOLOR: {
                    color = (byte) (nextByte() % COLOR_COUNT);
                    energy -= 3;
                    break;
                }

                case READCOLOR: {
                    int index = getIndex(nextByte());
                    mem[index] = getColor(dx[cmdExtra], dy[cmdExtra]);
                    break;
                }

                case MOVE: {
                    move(dx[cmdExtra], dy[cmdExtra]);
                    energy -= 10;
                    break;
                }
                case READ: {
                    int to = nextByte();
                    int length = nextByte();
                    if (length > 0) {
                        byte[] msg = read(dx[cmdExtra & 7], dy[cmdExtra & 7]);
                        int msglen = 0xff & msg[0];
                        to = getIndex(to);
                        if (cmdExtra > 7) {
                            length = min(msglen, min(length, memlength - to));
                            arraycopy(msg, 1, mem, to, length);
                        } else {
                            length = min(MAX_MEM - memlength, min(length, msglen));
                            if (length > 0) {
                                byte[] newmem;
                                if (memlength + length > mem.length) {
                                    newmem = new byte[memlength + length];
                                    arraycopy(mem, 0, newmem, 0, to);
                                } else {
                                    newmem = mem;
                                }
                                arraycopy(msg, 1, newmem, to, length);
                                arraycopy(mem, to, newmem, to + length, memlength - to);
                                memlength += length;
                                mem = newmem;
                            }
                        }
                    }
                    break;
                }
                case WRITE: {
                    int length = nextByte();
                    int from = getIndex(nextByte());
                    length = min(length, memlength - from);
                    write(mem, from, length);
                    break;
                }
                case EAT: {
                    if (cmdExtra == 0) {
                        int to = getIndex(nextByte());
                        mem[to] = (byte) Integer.toBinaryString(energy).length();
                    } else if (cmdExtra == 8) {
                        int to = getIndex(nextByte());
                        mem[to] = (byte) Integer.toBinaryString(readEnergy()).length();
                    } else if (cmdExtra < 8) {
                        energy -= (1 << cmdExtra) >>> 1;
                        energy = min(energy + getEnergy(1 << cmdExtra), MAX_ENERGY);
                    } else {
                        int de = min(energy, 1 << (cmdExtra & 7));
                        putEnergy(de);
                        energy -= de;
                    }
                    break;
                }
                case CLONE: {
                    int i = min(nextByte(), memlength - 1);
                    byte c = (byte) (nextByte() % COLOR_COUNT);
                    int e = energy * (cmdExtra + 1) / 17;
                    clone(dx[cmdExtra], dy[cmdExtra], mem, memlength, i, c, e);
                    energy -= e;
                    energy -= 20;
                    break;
                }
                case RND: {
                    int index = getIndex(nextByte());
                    mem[index] = (byte) RANDOM.nextInt(0x100);
                    break;
                }
                case SHOOT: {
                    int e = min(energy, nextByte());
                    shoot(dx[cmdExtra], dy[cmdExtra], e);
                    energy -= e;
                }
            }
        }

        private void shoot(int dx, int dy, int e) {
            world.shoot(this, dx, dy, e);
        }

        private void clone(int dx, int dy, byte[] mem, int memlength, int ip, byte color, int e) {
            world.clone(this, dx, dy, mem, memlength, ip, color, e);
        }

        private void putEnergy(int e) {
            world.putEnergy(this, e);
        }

        private int getEnergy(int e) {
            return world.getEnergy(this, e);
        }

        private int readEnergy() {
            return world.readEnergy(this);
        }

        private void write(byte[] mem, int from, int length) {
            world.write(this, mem, from, length);
        }

        private byte[] read(int dx, int dy) {
            return world.read(this, dx, dy);
        }

        private void move(int dx, int dy) {
            world.move(this, dx, dy);
        }

        private void die() {
            world.dead(this);
        }

        private byte getColor(int dx, int dy) {
            return world.getColor(this, dx, dy);
        }

        private int getIndex(int offset) {
            int index = (ip + offset) % memlength;
            if (index < 0) index += memlength;
            return index;
        }

        private int nextByte() {
            int cmd = 0xff & mem[ip];
            incip();
            return cmd;
        }

        private void incip() {
            ip++;
            ip %= memlength;
        }
    }

    private static class World {
        private final int size;
        private final Cell[] map;
        private final LinkedList<P> list = new LinkedList<>();
        private final LinkedList<P> tmplist = new LinkedList<>();
        private final int[] sources;
        private int maxgen;
        private int maxenergy = ENERGY_PLUS;
        private byte[] scratchpad = new byte[MAX_MEM * 100];

        public World(int size) {
            this.size = size;
            map = new Cell[size * size];
            for (int i = 0; i < map.length; i++) {
                map[i] = new Cell();
            }
            sources = new int[size * size / SOURCE_DENSITY];
            for (int i = 0; i < sources.length; i++) {
                sources[i] = RANDOM.nextInt(map.length);
            }
            RANDOM.nextBytes(scratchpad);
        }

        void createRandom(double count) {
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
                    create(coords, mem, mem.length, ip, (byte) RANDOM.nextInt(COLOR_COUNT), RANDOM.nextInt(ENERGY_PLUS * 10), 0);
                }
            }
            addTempList();
        }

        void randomizeEnergy() {
            for (Cell cell : map) {
                cell.energy = RANDOM.nextInt(ENERGY_PLUS);
            }
        }

        public int draw(boolean mode, BufferedImage image) {
            int totale = 0;
//            int maxe = 0;
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    int coords = getCoords(i, j);
                    Cell cell = map[coords];
                    int e = cell.p == null ? cell.energy : (cell.energy + cell.p.energy);
                    totale += e;
//                    if (cell.energy > maxe) maxe = cell.energy;
                    int b = min(255, e * 256 / maxenergy);
                    int rgb = 0xff000000 | (b << 16) | (b << 8) | b;
                    if (mode) {
                        if (cell.p == null) {
                            image.setRGB(i, j, rgb);
                        } else {
                            image.setRGB(i, j, COLORS[cell.p.color]);
                        }
                    } else {
                        image.setRGB(i, j, rgb);
                    }
                }
            }
//            maxenergy = maxe;
            maxenergy += signum(4 * totale / map.length - maxenergy);
            if (maxenergy < 256) maxenergy = 256;
            return totale;
        }

        int update() {
            fountain();

            diffuse();

            createRandom(.1);

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

        private void diffuse() {
            for (int i = 0; i < map.length / 10; i++) {
                int coords1 = RANDOM.nextInt(map.length);
                int coords2 = getCoords(coords1, RANDOM.nextInt(11) - 5, RANDOM.nextInt(11) - 5);
                Cell cell1 = map[coords1];
                Cell cell2 = map[coords2];
                int de = (cell1.energy - cell2.energy) / DIFFUSE_FACTOR;
                cell1.energy -= de;
                cell2.energy += de;
            }
        }

        private void fountain() {
//            maxenergy = 0;
            for (int i = 0; i < sources.length; i++) {
                sources[i] = getCoords(sources[i], RANDOM.nextInt(3) - 1, RANDOM.nextInt(3) - 1);
                Cell cell = map[getCoords(sources[i], RANDOM.nextInt(10), RANDOM.nextInt(10))];
                cell.energy = min(MAX_ENERGY, cell.energy + RANDOM.nextInt(ENERGY_PLUS));
//                if (cell.energy>maxenergy) maxenergy = cell.energy;
                Cell cell2 = map[RANDOM.nextInt(map.length)];
                cell2.energy = max(0, cell2.energy - RANDOM.nextInt(ENERGY_PLUS));
            }
        }

        private int getY(int coords) {
            return coords / size;
        }

        private int getX(int coords) {
            return coords % size;
        }

        private int getCoords(int x, int y) {
            return y * size + x;
        }

        public void shoot(P p, int dx, int dy, int e) {
            if (e > 0) {
                Cell cell = map[getCoords(p.coords, dx, dy)];
                if (cell.p != null) {
                    cell.p.energy -= RANDOM.nextInt(e * 10);
                }
            }
        }

        public void clone(P p, int dx, int dy, byte[] mem, int memlength, int ip, byte color, int e) {
            int coords = getCoords(p.coords, dx, dy);
            byte[] mem1 = copyOf(mem, memlength);
            create(coords, mem1, memlength, ip, color, e, p.generation + 1);
        }

        private void create(int coords, byte[] mem, int memlength, int ip, byte color, int e, int generation) {
            Cell cell = map[coords];
            if (cell.p == null) {
                cell.p = new P(this, coords, mem, memlength, ip, color, e, generation);
                tmplist.add(cell.p);
            }
        }

        private int getCoords(int coords, int dx, int dy) {
            int x = (getX(coords) + dx + size) % size;
            int y = (getY(coords) + dy + size) % size;
            return getCoords(x, y);
        }

        public void putEnergy(P p, int e) {
            Cell cell = map[p.coords];
            cell.energy = min(cell.energy + e, MAX_ENERGY);
        }

        public int getEnergy(P p, int e) {
            Cell cell = map[p.coords];
            e = min(e, cell.energy);
            cell.energy -= e;
            return e;
        }

        public int readEnergy(P p) {
            return map[p.coords].energy;
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

        private class Cell {
            public P p;
            public int energy;
            public byte[] info = new byte[256];
        }
    }

}
