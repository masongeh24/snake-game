import java.awt.*;
import javax.swing.*;

public class SimpleWindow extends JPanel {
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(Color.GREEN);
        g.fillRect(40, 40, 120, 40);
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Simple Window");
        frame.setSize(400, 300);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(new SimpleWindow());
        frame.setVisible(true);
    }
}