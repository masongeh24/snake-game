import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
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
        private int[] food;
        private int score = 0;
        private boolean gameOver = false;

        private enum Direction {
            UP, DOWN, LEFT, RIGHT
        }

        public GamePanel() {
            setBackground(Color.BLACK);
            setFocusable(true);
            requestFocusInWindow();
            addKeyListener(this);
            initializeSnake();
            spawnFood();
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

        private void spawnFood() {
            boolean placed = false;
            while (!placed) {
                int x = (int) (Math.random() * GRID_WIDTH);
                int y = (int) (Math.random() * GRID_HEIGHT);
                boolean occupied = false;
                for (int[] segment : snake) {
                    if (segment[0] == x && segment[1] == y) {
                        occupied = true;
                        break;
                    }
                }
                if (!occupied) {
                    food = new int[]{x, y};
                    placed = true;
                }
            }
        }

        private int getDelay() {
            return Math.max(50, 150 - score * 5);
        }

        private void resetGame() {
            snake.clear();
            initializeSnake();
            direction = Direction.RIGHT;
            score = 0;
            gameOver = false;
            spawnFood();
            timer.setDelay(150);
            timer.start();
            repaint();
        }

        private void move() {
            if (gameOver) return;

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

            // Check wall collision
            if (newX < 0 || newX >= GRID_WIDTH || newY < 0 || newY >= GRID_HEIGHT) {
                gameOver = true;
                timer.stop();
                repaint();
                return;
            }

            // Check self collision
            for (int[] segment : snake) {
                if (segment[0] == newX && segment[1] == newY) {
                    gameOver = true;
                    timer.stop();
                    repaint();
                    return;
                }
            }

            snake.add(0, new int[]{newX, newY});

            // Check if ate food
            if (newX == food[0] && newY == food[1]) {
                score++;
                spawnFood();
                timer.setDelay(getDelay());
                // Don't remove tail, snake grows
            } else {
                snake.remove(snake.size() - 1);
            }

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

            // Draw food
            g2d.setColor(Color.RED);
            g2d.fillOval(food[0] * CELL_SIZE + 5, food[1] * CELL_SIZE + 5, CELL_SIZE - 10, CELL_SIZE - 10);

            // Draw score
            g2d.setColor(Color.WHITE);
            g2d.drawString("Score: " + score, 10, 20);

            // Draw game over
            if (gameOver) {
                Font originalFont = g2d.getFont();
                g2d.setFont(new Font("Arial", Font.BOLD, 24));
                FontMetrics fm = g2d.getFontMetrics();
                int centerX = getWidth() / 2;

                String gameOverText = "Game Over";
                int gameOverWidth = fm.stringWidth(gameOverText);
                g2d.setColor(Color.RED);
                g2d.drawString(gameOverText, centerX - gameOverWidth / 2, 280);

                String scoreText = "Final Score: " + score;
                int scoreWidth = fm.stringWidth(scoreText);
                g2d.drawString(scoreText, centerX - scoreWidth / 2, 310);

                String restartText = "Press R to Restart";
                int restartWidth = fm.stringWidth(restartText);
                g2d.drawString(restartText, centerX - restartWidth / 2, 340);

                g2d.setFont(originalFont);
            }
        }

        @Override
        public void keyPressed(KeyEvent e) {
            int key = e.getKeyCode();
            if (gameOver) {
                if (key == KeyEvent.VK_R) {
                    resetGame();
                }
                return;
            }
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
