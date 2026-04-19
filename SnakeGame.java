import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public class SnakeGame {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Snake");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);

            GamePanel gamePanel = new GamePanel();
            gamePanel.setPreferredSize(new Dimension(600, 600));
            frame.add(gamePanel);

            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    private static class GamePanel extends JPanel implements KeyListener {
        private static final int CELL_SIZE = 30;
        private static final int GRID_WIDTH = 20;
        private static final int GRID_HEIGHT = 20;
        private List<int[]> snake;
        private Direction direction = Direction.RIGHT;
        private Timer timer;

        private enum Direction {
            UP, DOWN, LEFT, RIGHT
        }

        public GamePanel() {
            setBackground(Color.DARK_GRAY);
            setFocusable(true);
            requestFocusInWindow();
            addKeyListener(this);
            initializeSnake();
            timer = new Timer(150, e -> move());
            timer.start();
        }

        private void initializeSnake() {
            snake = new ArrayList<>();
            // Starting snake: three segments near center, facing right
            snake.add(new int[]{10, 10}); // head
            snake.add(new int[]{9, 10});  // body
            snake.add(new int[]{8, 10});  // tail
        }

        private void move() {
            int[] head = snake.get(0);
            int newX = head[0];
            int newY = head[1];

            switch (direction) {
                case UP:
                    newY--;
                    break;
                case DOWN:
                    newY++;
                    break;
                case LEFT:
                    newX--;
                    break;
                case RIGHT:
                    newX++;
                    break;
            }

            // Wrap around edges
            if (newX < 0) newX = GRID_WIDTH - 1;
            else if (newX >= GRID_WIDTH) newX = 0;
            if (newY < 0) newY = GRID_HEIGHT - 1;
            else if (newY >= GRID_HEIGHT) newY = 0;

            snake.add(0, new int[]{newX, newY});
            snake.remove(snake.size() - 1);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;

            // Draw grid lines
            g2d.setColor(Color.GRAY);
            for (int i = 0; i <= GRID_WIDTH; i++) {
                g2d.drawLine(i * CELL_SIZE, 0, i * CELL_SIZE, GRID_HEIGHT * CELL_SIZE);
            }
            for (int i = 0; i <= GRID_HEIGHT; i++) {
                g2d.drawLine(0, i * CELL_SIZE, GRID_WIDTH * CELL_SIZE, i * CELL_SIZE);
            }

            // Draw snake
            g2d.setColor(Color.GREEN);
            for (int[] segment : snake) {
                g2d.fillRect(segment[0] * CELL_SIZE, segment[1] * CELL_SIZE, CELL_SIZE, CELL_SIZE);
            }
        }

        @Override
        public void keyPressed(KeyEvent e) {
            int key = e.getKeyCode();
            switch (key) {
                case KeyEvent.VK_UP:
                    if (direction != Direction.DOWN) direction = Direction.UP;
                    break;
                case KeyEvent.VK_DOWN:
                    if (direction != Direction.UP) direction = Direction.DOWN;
                    break;
                case KeyEvent.VK_LEFT:
                    if (direction != Direction.RIGHT) direction = Direction.LEFT;
                    break;
                case KeyEvent.VK_RIGHT:
                    if (direction != Direction.LEFT) direction = Direction.RIGHT;
                    break;
            }
        }

        @Override
        public void keyReleased(KeyEvent e) {}

        @Override
        public void keyTyped(KeyEvent e) {}
    }
}
