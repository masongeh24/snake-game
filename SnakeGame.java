import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
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
        private int highScore = 0;
        private boolean gameOver = false;
        private boolean isStarted = false;
        private static final String HIGH_SCORE_FILE = "highscore.txt";
        private List<int[]> obstacles;

        private enum Direction {
            UP, DOWN, LEFT, RIGHT
        }

        public GamePanel() {
            setBackground(Color.BLACK);
            setFocusable(true);
            requestFocusInWindow();
            addKeyListener(this);
            loadHighScore();
            initializeSnake();
            obstacles = new ArrayList<>();
            spawnFood();
            timer = new Timer(150, e -> move());

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

        private void spawnObstacle() {
            // 35% chance to spawn an obstacle
            if (Math.random() > 0.35) {
                return;
            }
            boolean placed = false;
            int attempts = 0;
            while (!placed && attempts < 50) {
                int x = (int) (Math.random() * GRID_WIDTH);
                int y = (int) (Math.random() * GRID_HEIGHT);
                boolean occupied = false;

                // Check if position is on snake or food
                for (int[] segment : snake) {
                    if (segment[0] == x && segment[1] == y) {
                        occupied = true;
                        break;
                    }
                }
                if (x == food[0] && y == food[1]) {
                    occupied = true;
                }

                // Check if position is too close to snake head (within 1 cell)
                int[] head = snake.get(0);
                if (Math.abs(x - head[0]) <= 1 && Math.abs(y - head[1]) <= 1) {
                    occupied = true;
                }

                if (!occupied) {
                    obstacles.add(new int[]{x, y});
                    placed = true;
                }
                attempts++;
            }
        }

        private void playTone(float frequency, int durationMs) {
            playTone(frequency, durationMs, 7);
        }

        private void playTone(float frequency, int durationMs, int amplitude) {
            try {
                AudioFormat af = new AudioFormat(44100, 8, 1, true, false);
                SourceDataLine line = AudioSystem.getSourceDataLine(af);
                line.open(af);
                line.start();

                byte[] buf = new byte[44100 / 10]; // 100ms buffer
                for (int i = 0; i < buf.length; i++) {
                    double angle = i / (44100.0 / frequency) * 2.0 * Math.PI;
                    buf[i] = (byte) (Math.signum(Math.sin(angle)) * amplitude);
                }

                int samples = (int) ((durationMs / 1000.0) * 44100);
                int remaining = samples;
                while (remaining > 0) {
                    int chunk = Math.min(buf.length, remaining);
                    line.write(buf, 0, chunk);
                    remaining -= chunk;
                }

                line.drain();
                line.close();
            } catch (LineUnavailableException e) {
                // Sound not available, ignore
            }
        }

        private void playCrunch() {
            // Play a short crunch sound: multiple quick tones
            new Thread(() -> {
                playTone(200, 50, 20);
                try { Thread.sleep(20); } catch (InterruptedException e) {}
                playTone(150, 50, 20);
                try { Thread.sleep(20); } catch (InterruptedException e) {}
                playTone(100, 50, 20);
            }).start();
        }

        private void playGameOver() {
            // Two-tone game over sound
            new Thread(() -> {
                playTone(150, 300);
                try { Thread.sleep(100); } catch (InterruptedException e) {}
                playTone(100, 500);
            }).start();
        }

        private int getDelay() {
            return Math.max(50, 150 - score * 5);
        }

        private void loadHighScore() {
            try {
                File file = new File(HIGH_SCORE_FILE);
                if (file.exists()) {
                    BufferedReader reader = new BufferedReader(new FileReader(file));
                    String line = reader.readLine();
                    if (line != null && !line.isEmpty()) {
                        highScore = Integer.parseInt(line.trim());
                    }
                    reader.close();
                }
            } catch (Exception e) {
                highScore = 0;
            }
        }

        private void saveHighScore() {
            try {
                FileWriter writer = new FileWriter(HIGH_SCORE_FILE);
                writer.write(String.valueOf(highScore));
                writer.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void resetGame() {
            if (score > highScore) {
                highScore = score;
                saveHighScore();
            }
            snake.clear();
            obstacles.clear();
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
                playGameOver();
                repaint();
                return;
            }

            // Check self collision
            for (int[] segment : snake) {
                if (segment[0] == newX && segment[1] == newY) {
                    gameOver = true;
                    timer.stop();
                    playGameOver();
                    repaint();
                    return;
                }
            }

            // Check obstacle collision
            for (int[] obstacle : obstacles) {
                if (obstacle[0] == newX && obstacle[1] == newY) {
                    gameOver = true;
                    timer.stop();
                    playGameOver();
                    repaint();
                    return;
                }
            }

            snake.add(0, new int[]{newX, newY});

            // Check if ate food
            if (newX == food[0] && newY == food[1]) {
                score++;
                spawnFood();
                spawnObstacle();
                timer.setDelay(getDelay());
                playCrunch();
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

            if (!isStarted) {
                // Draw start screen
                Font originalFont = g2d.getFont();
                g2d.setFont(new Font("Arial", Font.BOLD, 48));
                FontMetrics fm = g2d.getFontMetrics();
                int centerX = getWidth() / 2;

                String title = "Snake";
                int titleWidth = fm.stringWidth(title);
                g2d.setColor(Color.GREEN);
                g2d.drawString(title, centerX - titleWidth / 2, 200);

                g2d.setFont(new Font("Arial", Font.PLAIN, 20));
                fm = g2d.getFontMetrics();
                String highScoreMsg = "High Score: " + highScore;
                int highScoreWidth = fm.stringWidth(highScoreMsg);
                g2d.setColor(Color.YELLOW);
                g2d.drawString(highScoreMsg, centerX - highScoreWidth / 2, 260);

                g2d.setFont(new Font("Arial", Font.PLAIN, 24));
                fm = g2d.getFontMetrics();
                String startMsg = "Press any key to start";
                int msgWidth = fm.stringWidth(startMsg);
                g2d.setColor(Color.WHITE);
                g2d.drawString(startMsg, centerX - msgWidth / 2, 320);

                g2d.setFont(originalFont);
                return;
            }

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

            // Draw obstacles
            g2d.setColor(Color.BLUE);
            for (int[] obstacle : obstacles) {
                g2d.fillRect(obstacle[0] * CELL_SIZE, obstacle[1] * CELL_SIZE, CELL_SIZE, CELL_SIZE);
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
                String scoreText = "Final Score: " + score;
                String highScoreText = "High Score: " + highScore;
                String restartText = "Press R to Restart";

                int gameOverWidth = fm.stringWidth(gameOverText);
                int scoreWidth = fm.stringWidth(scoreText);
                int highScoreWidth = fm.stringWidth(highScoreText);
                int restartWidth = fm.stringWidth(restartText);
                int maxWidth = Math.max(gameOverWidth, Math.max(Math.max(scoreWidth, highScoreWidth), restartWidth));

                int textHeight = fm.getHeight();
                int totalHeight = textHeight * 4 + 10; // 4 lines + padding

                int boxX = centerX - maxWidth / 2 - 10;
                int boxY = 270 - textHeight / 2 - 5;
                int boxWidth = maxWidth + 20;
                int boxHeight = totalHeight + 10;

                // Draw black box
                g2d.setColor(Color.BLACK);
                g2d.fillRect(boxX, boxY, boxWidth, boxHeight);

                // Draw text
                g2d.setColor(Color.RED);
                g2d.drawString(gameOverText, centerX - gameOverWidth / 2, 280);
                g2d.drawString(scoreText, centerX - scoreWidth / 2, 310);
                g2d.setColor(Color.YELLOW);
                g2d.drawString(highScoreText, centerX - highScoreWidth / 2, 335);
                g2d.setColor(Color.RED);
                g2d.drawString(restartText, centerX - restartWidth / 2, 360);

                g2d.setFont(originalFont);
            }
        }

        @Override
        public void keyPressed(KeyEvent e) {
            if (!isStarted) {
                isStarted = true;
                timer.start();
                return;
            }
            int key = e.getKeyCode();
            if (gameOver) {
                if (key == KeyEvent.VK_R) {
                    resetGame();
                }
                return;
            }
            Direction oldDirection = direction;
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
            // Play direction sound if changed
            if (direction != oldDirection) {
                final float freq;
                switch (direction) {
                    case UP: freq = 440; break; // A
                    case DOWN: freq = 261; break; // C
                    case LEFT: freq = 293; break; // D
                    case RIGHT: freq = 392; break; // G
                    default: freq = 0; break;
                }
                if (freq > 0) {
                    new Thread(() -> playTone(freq, 500)).start();
                }
            }
        }

        @Override
        public void keyReleased(KeyEvent e) {}

        @Override
        public void keyTyped(KeyEvent e) {}
    }
}
