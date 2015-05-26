package net.karpelevitch.l2;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

import static java.lang.Math.abs;
import static java.lang.Math.min;
import static java.lang.System.arraycopy;
import static java.util.Arrays.copyOf;

public class Main {

    public static final Random RANDOM = new Random(0/*System.currentTimeMillis()*/);
    public static final int SIZE = 300;
    public static final int[] dx = {0, 1, 1, 1, 0, -1, -1, -1, 0, 2, 2, 2, 0, -2, -2, -2};
    public static final int[] dy = {-1, -1, 0, 1, 1, 1, 0, -1, -2, -2, 0, 2, 2, 2, 0, -2};
    private static final int MAX_ENERGY = 10000;
    private static final int MAX_MEM = 10000;
    private static final int COLOR_COUNT;
    private static final int[] COLORS;
    private static final byte[] EMPTY = {};

    static {
        Color[] colors = {Color.GREEN, Color.BLUE, Color.CYAN, Color.MAGENTA, Color.ORANGE, Color.PINK, Color.RED, Color.YELLOW};
        COLOR_COUNT = colors.length * 3;
        COLORS = new int[COLOR_COUNT];
        for (int i = 0; i < colors.length; i++) {
            Color color = colors[i];
            COLORS[i*3] = color.getRGB();
            COLORS[i*3+1] = color.darker().getRGB();
            COLORS[i*3+2] = color.darker().darker().getRGB();
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
        byte[] mem;
        int ip;
        byte color;
        int energy;
        private final World world;
        public int coords;
        int age = 0;

        public P(World world, int coords, byte[] mem, int ip, byte color, int energy) {
            this.world = world;
            this.coords = coords;
            this.mem = mem;
            this.ip = ip;
            this.color = color;
            this.energy = energy;
        }

        void execOne() {
            if (energy <= 0 || mem.length == 0) {
                die();
                return;
            }
            age++;
            energy--;
            int cmd = nextByte();
            int cmdExtra = cmd & 0xf;
            switch (cmd >> 4) {
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
                            int maxlen = min(min(mem.length - from, mem.length - to), abs(from - to));
                            length = min(length, maxlen);
                            arraycopy(mem, from, mem, to, length);
                        } else {
                            int maxlen = min(min(mem.length - from, mem.length - to), MAX_MEM);
                            if (to > from) {
                                maxlen = min(maxlen, to - from);
                            }
                            length = min(length, maxlen);
                            byte[] newmem = new byte[mem.length + length];
                            arraycopy(mem, 0, newmem, 0, to);
                            arraycopy(mem, from, newmem, to, length);
                            arraycopy(mem, to, newmem, to + length, mem.length - to);
                            mem = newmem;
                        }
                    }
                    break;
                }
                case DELETE: {
                    int length = nextByte();
                    int index = getIndex(nextByte());
                    length = min(length, mem.length - index);
                    if (length > 0) {
                        byte[] newmem = new byte[mem.length - length];
                        if (index > 0) {
                            arraycopy(mem, 0, newmem, 0, index);
                        }
                        int remlen = mem.length - index - length;
                        if (remlen > 0) {
                            arraycopy(mem, index + length, newmem, index, remlen);
                        }
                        mem = newmem;
                        if (ip >= newmem.length) ip = 0;
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
                    break;
                }

                case READCOLOR: {
                    int index = getIndex(nextByte());
                    mem[index] = getColor(dx[cmdExtra], dy[cmdExtra]);
                    break;
                }

                case MOVE: {
                    move(dx[cmdExtra], dy[cmdExtra]);
                    energy -= 3;
                    break;
                }
                case READ: {
                    int to = nextByte();
                    int length = nextByte();
                    if (length > 0) {
                        byte[] msg = read(dx[cmdExtra & 7], dy[cmdExtra & 7]);
                        to = getIndex(to);
                        if (cmdExtra > 7) {
                            length = min(msg.length, min(length, mem.length - to));
                            arraycopy(msg, 0, mem, to, length);
                        } else {
                            length = min(length, msg.length);
                            byte[] newmem = new byte[mem.length + length];
                            arraycopy(mem, 0, newmem, 0, to);
                            arraycopy(msg, 0, newmem, to, length);
                            arraycopy(mem, to, newmem, to + length, mem.length - to);
                            mem = newmem;
                        }
                    }
                    break;
                }
                case WRITE: {
                    int length = nextByte();
                    int from = getIndex(nextByte());
                    length = min(length, mem.length - from);
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
                        energy = min(energy + getEnergy(1 << cmdExtra), MAX_ENERGY);
                    } else {
                        int de = min(energy, 1 << (cmdExtra & 7));
                        putEnergy(de);
                        energy -= de;
                    }
                    break;
                }
                case CLONE: {
                    int i = min(nextByte(), mem.length - 1);
                    byte c = (byte) (nextByte() % COLOR_COUNT);
                    int e = energy * (cmdExtra + 1) / 17;
                    clone(dx[cmdExtra], dy[cmdExtra], mem, i, c, e);
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

        private void clone(int dx, int dy, byte[] mem, int ip, byte color, int e) {
            world.clone(this, dx, dy, mem, ip, color, e);
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
            world.write(this, Arrays.copyOfRange(mem, from, from + length));
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
            int index = (ip + offset) % mem.length;
            if (index < 0) index+= mem.length;
            return index;
        }

        private int nextByte() {
            int cmd = 0xff & mem[ip];
            incip();
            return cmd;
        }

        private void incip() {
            ip++;
            ip %= mem.length;
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
        frame.pack();

        World world = new World(SIZE);

        world.createRandom(SIZE*SIZE/10);

        world.randomizeEnergy();

        long startTime = System.currentTimeMillis();
        int gen = 0;
        while (true) {

            world.update();

            world.draw(image);

            frame.repaint();
            gen++;
            if (gen % 100 == 0) {
                System.out.println("gen = " + gen);
                System.out.println("fps =  " + gen * 1000.0 / (System.currentTimeMillis() - startTime));
            }
        }
    }

    private static class World {
        private final int size;
        private final Cell[] map;
        private final LinkedList<P> list = new LinkedList<>();
        private final LinkedList<P> tmplist = new LinkedList<>();

        public World(int size) {
            this.size = size;
            map = new Cell[size * size];
            for (int i = 0; i < map.length; i++) {
                map[i] = new Cell();
            }
        }

        void createRandom(int count) {
            for (int i = 0; i < count; i++) {
                int coords = RANDOM.nextInt(map.length);
                byte[] mem = new byte[RANDOM.nextInt(MAX_MEM)+1];
                RANDOM.nextBytes(mem);
                int ip = RANDOM.nextInt(mem.length);
                create(coords, mem, ip, (byte) RANDOM.nextInt(COLOR_COUNT), RANDOM.nextInt(MAX_ENERGY));
            }
        }

        void randomizeEnergy() {
            for (Cell cell : map) {
                cell.energy = RANDOM.nextInt(MAX_ENERGY);
            }
        }

        public void draw(BufferedImage image) {
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    int coords = getCoords(i, j);
                    Cell cell = map[coords];
                    if (cell.p == null) {
                        int b = cell.energy * 256 / MAX_ENERGY;
                        int rgb = 0xff000000 | (b<<16) | (b<<8) | b;
                        image.setRGB(i, j, rgb);
                    } else {
                        image.setRGB(i, j, COLORS[cell.p.color]);
                    }
                }
            }
        }

        void update() {
            fountain();

            diffuse();

            liveOne();
        }

        private void liveOne() {
            for (Iterator<P> iterator = list.iterator(); iterator.hasNext(); ) {
                P p = iterator.next();
                // cleanup dead
                if (p.coords < 0) iterator.remove();
                p.execOne();
            }
            list.addAll(tmplist);
            tmplist.clear();
        }

        private void diffuse() {
            for (int i = 0; i < map.length / 100; i++) {
                int coords1 = RANDOM.nextInt(map.length);
                int coords2 = getCoords(coords1, RANDOM.nextInt(11) - 5, RANDOM.nextInt(11) - 5);
                Cell cell1 = map[coords1];
                Cell cell2 = map[coords2];
                int e = (cell1.energy + cell2.energy) / 2;
                int de = (cell1.energy - cell2.energy) / 4;
                cell1.energy = e + de;
                cell2.energy = e - de;
            }
        }

        private void fountain() {
            Cell cell = map[getCoords(RANDOM.nextInt(10), RANDOM.nextInt(10))];
            cell.energy = min(MAX_ENERGY, cell.energy + RANDOM.nextInt(MAX_ENERGY / 10));
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
            if ( e > 0) {
                Cell cell = map[getCoords(p.coords, dx, dy)];
                if (cell.p != null) {
                    cell.p.energy -= RANDOM.nextInt(e);
                }
            }
        }

        public void clone(P p, int dx, int dy, byte[] mem, int ip, byte color, int e) {
            int coords = getCoords(p.coords, dx, dy);
            byte[] mem1 = copyOf(mem, mem.length);
            create(coords, mem1, ip, color, e);
        }

        private void create(int coords, byte[] mem, int ip, byte color, int e) {
            Cell cell = map[coords];
            if (cell.p == null) {
                cell.p = new P(this, coords, mem, ip, color, e);
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

        public void write(P p, byte[] bytes) {
            map[p.coords].info = bytes;
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
            return (byte) (cell.p==null ? 255 : cell.p.color);
        }

        private class Cell {
            public P p;
            public int energy;
            public byte[] info = EMPTY;
        }
    }
}
