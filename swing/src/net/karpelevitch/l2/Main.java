package net.karpelevitch.l2;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;

public class Main {

    public static final int SIZE = 300;
    public static final Color[] BASE_COLORS = new Color[]{Color.GREEN, Color.BLUE, Color.CYAN, Color.MAGENTA, Color.ORANGE, Color.PINK, Color.RED, Color.YELLOW};
    private static final int[] COLORS;

    static {
        int COLOR_COUNT = BASE_COLORS.length * 3;
        COLORS = new int[COLOR_COUNT];
        for (int i = 0; i < BASE_COLORS.length; i++) {
            Color color = BASE_COLORS[i];
            COLORS[i * 3] = color.getRGB();
            COLORS[i * 3 + 1] = color.darker().getRGB();
            COLORS[i * 3 + 2] = color.darker().darker().getRGB();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        final JFrame frame = new JFrame("l2");
        frame.setBounds(0, 0, SIZE, SIZE);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        final BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        frame.setVisible(true);

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

        World world = new World(SIZE, COLORS.length);


        long startTime = System.currentTimeMillis();
        int gen = 0;
        int frames = 0;
        int print = 0;
        //noinspection InfiniteLoopStatement
        while (true) {

            int maxage = world.update();


            gen++;
            frames++;
            print++;
            long now;
            if ((now = System.currentTimeMillis()) - startTime > 100) {
                int totalEnergy = world.draw(mode[0] == 0, new World.RGBDraw() {
                    @Override
                    public void drawMono(int i, int j, int b) {
                        int rgb = 0xff000000 | (b << 16) | (b << 8) | b;
                        image.setRGB(i, j, rgb);
                    }

                    @Override
                    public void drawColor(int i, int j, int color) {
                        image.setRGB(i, j, COLORS[color]);
                    }

                    @Override
                    public void done() {
                    }
                });
                frame.repaint();
                long totalMemory = Runtime.getRuntime().totalMemory();
                long maxMemory = Runtime.getRuntime().maxMemory();
                long freeMem = Runtime.getRuntime().freeMemory();
                double freePercent = 100.0 * freeMem / totalMemory;
                Runtime.getRuntime().gc();
                if (print >= 1000) {
                    System.out.printf("%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%f\n", gen, frames * 1000 / (now - startTime), world.list.size(), totalEnergy, maxage, world.maxgen, freeMem, totalMemory, maxMemory, freePercent);
                    print = 0;
                }
                startTime = now;
                frames = 0;
            }
        }
    }

}
