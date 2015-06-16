package net.karpelevitch.l2;

import static java.lang.Math.abs;
import static java.lang.Math.min;
import static java.lang.System.arraycopy;
import static net.karpelevitch.l2.EnergyField.MAX_ENERGY;

class P {
    public static final int[] dx = {0, 1, 1, 1, 0, -1, -1, -1, 0, 2, 2, 2, 0, -2, -2, -2};
    public static final int[] dy = {-1, -1, 0, 1, 1, 1, 0, -1, -2, -2, 0, 2, 2, 2, 0, -2};
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
    private final World world;
    public int coords;
    int generation;
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
                mem[getIndex(nextByte())] += (cmdExtra - 8);
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
                int length = nextByte() << (cmdExtra & 7);
                from = getIndex(from);
                to = getIndex(to);
                boolean insert = cmdExtra > 7;
                copy(from, to, length, insert);
                break;
            }
            case DELETE: {
                int length = nextByte() << cmdExtra;
                int index = getIndex(nextByte());
                delete(index, length);
                break;
            }
            case JUMP: {
                int length = nextByte() + 1;
                int multiplier = cmdExtra > 7 ? -(1 << (cmdExtra - 8)) : 1 << cmdExtra;
                ip = getIndex(multiplier * length);
                break;
            }
            case SETCOLOR: {
                color = (byte) (nextByte() % world.getColorCount());
                energy -= 3;
                break;
            }

            case READCOLOR: {
                byte thatcolor = getColor(dx[cmdExtra], dy[cmdExtra]);
                int jump = nextByte();
                int nextByte = nextByte();
                byte cmpcolor = (byte) (nextByte == 255 ? 255 : (nextByte % (world.getColorCount())));
                if (cmpcolor == thatcolor) {
                    ip = getIndex(jump);
                }
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
                        length = Math.min(World.MAX_MEM - memlength, min(length, msglen));
                        if (length > 0) {
                            byte[] newmem;
                            if (memlength + length > mem.length) {
                                if (Runtime.getRuntime().freeMemory() < 10 * World.MAX_MEM) break;
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
                if (cmdExtra == 15) {
                    // check my energy
                    int curenergy = 4096 * energy / (MAX_ENERGY + 1);
                    int cmpenergy = cmdExtra * 256 + nextByte();
                    int offset = nextByte();
                    if (curenergy < cmpenergy) {
                        ip = getIndex(offset);
                    }
                } else if (cmdExtra == 14) {
                    // read energy
                    int nb = nextByte();
                    int nb2 = nextByte();
                    int offset = nextByte();
                    boolean checkThis = (nb & 128) == 0;
                    int dir = nb & 15;
                    boolean rel = (nb & 64) == 0;
                    int mul = (nb >>> 4) & 3;
                    int energyread;
                    if (checkThis)
                        energyread = world.readEnergy(this);
                    else if (rel)
                        energyread = (MAX_ENERGY + world.readEnergy(this, dx[dir], dy[dir]) - world.readEnergy(this)) / 2;
                    else
                        energyread = world.readEnergy(this, dx[dir], dy[dir]);
                    if (energyread * 1024 / (MAX_ENERGY + 1) < mul * 256 + nb2) {
                        ip = getIndex(offset);
                    }
                } else if (cmdExtra > 7) {
                    // get energy
                    energy -= (1 << (cmdExtra - 7)) / 2;
                    energy = Math.min(energy + getEnergy(1 << (cmdExtra - 7)), MAX_ENERGY);
                } else {
                    // put energy
                    int de = min(energy, nextByte() + 1);
                    putEnergy(de, dx[cmdExtra], dy[cmdExtra]);
                    energy -= de;
                }
                break;
            }
            case CLONE: {
                int i = min(nextByte(), memlength - 1);
                byte c = (byte) (nextByte() % world.getColorCount());
                int e = energy * ((cmdExtra & 7) + 1) / 9;
                clone(dx[cmdExtra], dy[cmdExtra], mem, memlength, i, c, e, cmdExtra > 7);
                energy -= e;
                energy -= 20;
                break;
            }
            case RND: {
                int index = getIndex(nextByte());
                mem[index] = (byte) World.RANDOM.nextInt(0x100);
                break;
            }
            case SHOOT: {
                int e = min(energy, nextByte());
                shoot(dx[cmdExtra], dy[cmdExtra], e);
                energy -= e;
            }
        }
    }

    void delete(int index, int length) {
        length = min(length, memlength - index);
        if (length > 0) {
            int remlen = memlength - index - length;
            if (remlen > 0) {
                arraycopy(mem, index + length, mem, index, remlen);
            }
            memlength -= length;
            if (ip >= memlength) ip = 0;
        }
    }

    void copy(int from, int to, int length, boolean insert) {
        if (from != to) {
            if (insert) {
                int maxlen = Math.min(min(memlength - from, memlength - to), World.MAX_MEM - memlength);
                if (to > from) {
                    maxlen = min(maxlen, to - from);
                }
                length = min(length, maxlen);
                if (length > 0) {
                    byte[] newmem;
                    if (memlength + length > mem.length) {
                        if (Runtime.getRuntime().freeMemory() < 10 * World.MAX_MEM) return;
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
            } else {
                int maxlen = min(min(memlength - from, memlength - to), abs(from - to));
                length = min(length, maxlen);
                arraycopy(mem, from, mem, to, length);
            }
        }
    }

    private void shoot(int dx, int dy, int e) {
        world.shoot(this, dx, dy, e);
    }

    private void clone(int dx, int dy, byte[] mem, int memlength, int ip, byte color, int e, boolean okToAppend) {
        world.clone(this, dx, dy, mem, memlength, ip, color, e, okToAppend);
    }

    private void putEnergy(int e, int dx, int dy) {
        world.putEnergy(this, dx, dy, e);
    }

    private int getEnergy(int e) {
        return world.getEnergy(this, e);
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
