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
        private int volumeLevel = 3; // 0-10
        private boolean paused = false;
        private boolean inOptionsMenu = false;
        private boolean adjustingVolume = false;
        private int pauseSelection = 0;
        private boolean gameOver = false;
        private boolean isStarted = false;
        private int startMenuSelection = 0;
        private static final String HIGH_SCORE_FILE = "highscore.txt";
        private static final String SAVE_FILE = "snake_save.txt";
        private List<int[]> obstacles;
        private int optionsMenuSelection = 0;
        private String optionsMessage = "";

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
                for (int[] obstacle : obstacles) {
                    if (obstacle[0] == x && obstacle[1] == y) {
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
            playTone(frequency, durationMs, getAmplitude());
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

        private void exitToMainMenu() {
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
            paused = false;
            inOptionsMenu = false;
            adjustingVolume = false;
            pauseSelection = 0;
            isStarted = false;
            timer.stop();
            repaint();
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

        private int getAmplitude() {
            return Math.max(1, volumeLevel * 3);
        }

        private void togglePause() {
            if (!isStarted || gameOver) {
                return;
            }
            paused = !paused;
            if (paused) {
                timer.stop();
            } else {
                timer.start();
            }
            repaint();
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

        private void saveGameState() {
            try (FileWriter writer = new FileWriter(SAVE_FILE)) {
                writer.write("volume=" + volumeLevel + "\n");
                writer.write("score=" + score + "\n");
                writer.write("highScore=" + highScore + "\n");
                writer.write("direction=" + direction.name() + "\n");
                writer.write("isStarted=" + isStarted + "\n");
                writer.write("paused=" + paused + "\n");
                writer.write("gameOver=" + gameOver + "\n");
                writer.write("food=" + food[0] + "," + food[1] + "\n");
                writer.write("snake=");
                for (int i = 0; i < snake.size(); i++) {
                    int[] segment = snake.get(i);
                    writer.write(segment[0] + "," + segment[1]);
                    if (i < snake.size() - 1) {
                        writer.write(";");
                    }
                }
                writer.write("\n");
                writer.write("obstacles=");
                for (int i = 0; i < obstacles.size(); i++) {
                    int[] obstacle = obstacles.get(i);
                    writer.write(obstacle[0] + "," + obstacle[1]);
                    if (i < obstacles.size() - 1) {
                        writer.write(";");
                    }
                }
                writer.write("\n");
                optionsMessage = "Game state saved to " + SAVE_FILE;
            } catch (Exception e) {
                optionsMessage = "Failed to save game state.";
            }
        }

        private void loadGameState() {
            try {
                File file = new File(SAVE_FILE);
                if (!file.exists()) {
                    optionsMessage = "Save file not found.";
                    return;
                }
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("volume=")) {
                        volumeLevel = Integer.parseInt(line.substring(line.indexOf('=') + 1));
                    } else if (line.startsWith("score=")) {
                        score = Integer.parseInt(line.substring(line.indexOf('=') + 1));
                    } else if (line.startsWith("highScore=")) {
                        highScore = Integer.parseInt(line.substring(line.indexOf('=') + 1));
                    } else if (line.startsWith("direction=")) {
                        direction = Direction.valueOf(line.substring(line.indexOf('=') + 1));
                    } else if (line.startsWith("isStarted=")) {
                        isStarted = Boolean.parseBoolean(line.substring(line.indexOf('=') + 1));
                    } else if (line.startsWith("paused=")) {
                        paused = Boolean.parseBoolean(line.substring(line.indexOf('=') + 1));
                    } else if (line.startsWith("gameOver=")) {
                        gameOver = Boolean.parseBoolean(line.substring(line.indexOf('=') + 1));
                    } else if (line.startsWith("food=")) {
                        String[] parts = line.substring(line.indexOf('=') + 1).split(",");
                        food = new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
                    } else if (line.startsWith("snake=")) {
                        snake.clear();
                        String[] segments = line.substring(line.indexOf('=') + 1).split(";");
                        for (String segment : segments) {
                            if (segment.isEmpty()) continue;
                            String[] coords = segment.split(",");
                            snake.add(new int[]{Integer.parseInt(coords[0]), Integer.parseInt(coords[1])});
                        }
                    } else if (line.startsWith("obstacles=")) {
                        obstacles.clear();
                        String[] items = line.substring(line.indexOf('=') + 1).split(";");
                        for (String item : items) {
                            if (item.isEmpty()) continue;
                            String[] coords = item.split(",");
                            obstacles.add(new int[]{Integer.parseInt(coords[0]), Integer.parseInt(coords[1])});
                        }
                    }
                }
                reader.close();
                if (!isStarted) {
                    isStarted = true;
                }
                if (paused || gameOver) {
                    timer.stop();
                } else {
                    timer.setDelay(getDelay());
                    timer.start();
                }
                optionsMessage = "Game state loaded from " + SAVE_FILE;
            } catch (Exception e) {
                optionsMessage = "Failed to load game state.";
            }
            inOptionsMenu = true;
            adjustingVolume = false;
            optionsMenuSelection = 0;
            repaint();
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
            paused = false;
            inOptionsMenu = false;
            adjustingVolume = false;
            pauseSelection = 0;
            spawnFood();
            timer.setDelay(150);
            timer.start();
            repaint();
        }

        private void move() {
            if (gameOver || paused) return;

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

            if (inOptionsMenu) {
                g2d.setColor(Color.BLACK);
                g2d.fillRect(0, 0, getWidth(), getHeight());

                Font originalFont = g2d.getFont();
                g2d.setFont(new Font("Arial", Font.BOLD, 36));
                FontMetrics fm = g2d.getFontMetrics();
                int centerX = getWidth() / 2;

                String title = "Options Menu";
                int titleWidth = fm.stringWidth(title);
                g2d.setColor(Color.WHITE);
                g2d.drawString(title, centerX - titleWidth / 2, 140);

                g2d.setFont(new Font("Arial", Font.PLAIN, 20));
                fm = g2d.getFontMetrics();
                String statusText = "   Score: " + score;
                int statusWidth = fm.stringWidth(statusText);
                g2d.drawString(statusText, centerX - statusWidth / 2, 190);

                String[] menuOptions = {"Save Game", "Load Game", "Back"};
                int menuStartY = 240;
                for (int i = 0; i < menuOptions.length; i++) {
                    g2d.setColor(optionsMenuSelection == i ? Color.YELLOW : Color.WHITE);
                    g2d.drawString(menuOptions[i], centerX - fm.stringWidth(menuOptions[i]) / 2, menuStartY + i * 40);
                }

                String helpText = "Use UP/DOWN, ENTER to choose, ESC to return";
                int helpWidth = fm.stringWidth(helpText);
                g2d.setColor(Color.LIGHT_GRAY);
                g2d.drawString(helpText, centerX - helpWidth / 2, menuStartY + menuOptions.length * 40 + 30);

                if (!optionsMessage.isEmpty()) {
                    g2d.setColor(Color.GREEN);
                    int messageWidth = fm.stringWidth(optionsMessage);
                    g2d.drawString(optionsMessage, centerX - messageWidth / 2, menuStartY + menuOptions.length * 40 + 60);
                }

                g2d.setFont(originalFont);
                return;
            }

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
                String[] options = {"Start Game", "Options"};
                for (int i = 0; i < options.length; i++) {
                    g2d.setColor(i == startMenuSelection ? Color.GREEN : Color.WHITE);
                    int optionWidth = fm.stringWidth(options[i]);
                    g2d.drawString(options[i], centerX - optionWidth / 2, 320 + i * 40);
                }

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

            // Draw pause overlay if paused
            if (paused) {
                Font originalFont = g2d.getFont();
                g2d.setFont(new Font("Arial", Font.BOLD, 28));
                FontMetrics fm = g2d.getFontMetrics();
                int centerX = getWidth() / 2;

                String title = "Paused";
                String resumeText = "Resume";
                String volumeText = "Adjust Volume: " + volumeLevel;
                String optionsText = "Options Menu";
                String exitText = "Exit to Main Menu";

                int maxWidth = Math.max(fm.stringWidth(title), Math.max(Math.max(fm.stringWidth(resumeText), fm.stringWidth(volumeText)), Math.max(fm.stringWidth(optionsText), fm.stringWidth(exitText))));
                int textHeight = fm.getHeight();
                int totalHeight = textHeight * 6 + 80; // Include an extra line for volume instructions
                int boxX = centerX - maxWidth / 2 - 20;
                int boxY = 180;
                int boxWidth = maxWidth + 40;
                int boxHeight = totalHeight;

                g2d.setColor(Color.BLACK);
                g2d.fillRect(boxX, boxY, boxWidth, boxHeight);
                g2d.setColor(Color.WHITE);
                g2d.drawString(title, centerX - fm.stringWidth(title) / 2, boxY + textHeight + 10);

                g2d.setFont(new Font("Arial", Font.PLAIN, 20));
                fm = g2d.getFontMetrics();
                int itemY = boxY + textHeight * 2 + 10;
                String[] items = {resumeText, volumeText, optionsText, exitText};
                for (int i = 0; i < items.length; i++) {
                    if (pauseSelection == i && !adjustingVolume) {
                        g2d.setColor(Color.YELLOW);
                    } else {
                        g2d.setColor(Color.WHITE);
                    }
                    g2d.drawString(items[i], centerX - fm.stringWidth(items[i]) / 2, itemY + i * (textHeight + 10));
                }

                if (adjustingVolume || pauseSelection == 1) {
                    String adjustText = "Use LEFT/RIGHT to change, ENTER to confirm";
                    int adjustWidth = fm.stringWidth(adjustText);
                    g2d.setColor(Color.WHITE);
                    g2d.drawString(adjustText, centerX - adjustWidth / 2, boxY + boxHeight - 20);
                }

                g2d.setFont(originalFont);
            }


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
            int key = e.getKeyCode();
            if (!isStarted) {
                if (key == KeyEvent.VK_UP) {
                    startMenuSelection = (startMenuSelection + 1) % 2;
                    repaint();
                } else if (key == KeyEvent.VK_DOWN) {
                    startMenuSelection = (startMenuSelection + 1) % 2;
                    repaint();
                } else if (key == KeyEvent.VK_ENTER) {
                    if (startMenuSelection == 0) {
                        isStarted = true;
                        timer.start();
                    } else {
                        inOptionsMenu = true;
                        optionsMenuSelection = 0;
                        optionsMessage = "";
                    }
                    repaint();
                }
                return;
            }

            if (inOptionsMenu) {
                if (key == KeyEvent.VK_ESCAPE) {
                    inOptionsMenu = false;
                    repaint();
                    return;
                }
                if (key == KeyEvent.VK_UP) {
                    optionsMenuSelection = (optionsMenuSelection + 2) % 3;
                    repaint();
                    return;
                }
                if (key == KeyEvent.VK_DOWN) {
                    optionsMenuSelection = (optionsMenuSelection + 1) % 3;
                    repaint();
                    return;
                }
                if (key == KeyEvent.VK_ENTER) {
                    switch (optionsMenuSelection) {
                        case 0:
                            saveGameState();
                            break;
                        case 1:
                            loadGameState();
                            break;
                        case 2:
                            inOptionsMenu = false;
                            break;
                    }
                    repaint();
                    return;
                }
                return;
            }

            if (gameOver) {
                if (key == KeyEvent.VK_R) {
                    resetGame();
                }
                return;
            }

            if (paused) {
                if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ESCAPE) {
                    togglePause();
                    return;
                }
                if (adjustingVolume) {
                    if (key == KeyEvent.VK_LEFT && volumeLevel > 0) {
                        volumeLevel--;
                    } else if (key == KeyEvent.VK_RIGHT && volumeLevel < 10) {
                        volumeLevel++;
                    } else if (key == KeyEvent.VK_ENTER) {
                        adjustingVolume = false;
                    }
                    repaint();
                    return;
                }

                if (key == KeyEvent.VK_UP) {
                    pauseSelection = (pauseSelection + 3) % 4;
                    repaint();
                    return;
                }
                if (key == KeyEvent.VK_DOWN) {
                    pauseSelection = (pauseSelection + 1) % 4;
                    repaint();
                    return;
                }
                if (key == KeyEvent.VK_ENTER) {
                    switch (pauseSelection) {
                        case 0:
                            togglePause();
                            break;
                        case 1:
                            adjustingVolume = true;
                            break;
                        case 2:
                            inOptionsMenu = true;
                            optionsMenuSelection = 0;
                            optionsMessage = "";
                            break;
                        case 3:
                            exitToMainMenu();
                            break;
                    }
                    return;
                }
                return;
            }

            if (key == KeyEvent.VK_SPACE) {
                togglePause();
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
                    new Thread(() -> playTone(freq, 250)).start();
                }
            }
        }

        @Override
        public void keyReleased(KeyEvent e) {}

        @Override
        public void keyTyped(KeyEvent e) {}
    }
}
