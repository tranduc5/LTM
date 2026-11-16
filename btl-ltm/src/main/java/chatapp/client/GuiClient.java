package chatapp.client;


import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;

/** Client giao diện Swing. Chạy: java GuiClient */
public class GuiClient extends JFrame {
    private final JTextArea chatArea = new JTextArea();
    private final DefaultListModel<String> roomModel = new DefaultListModel<>();
    private final DefaultListModel<String> userModel = new DefaultListModel<>();
    private final JList<String> roomList = new JList<>(roomModel);
    private final JList<String> userList = new JList<>(userModel);
    private final JTextField input = new JTextField();
    private final JLabel status = new JLabel(" Chưa kết nối");

    private Socket socket;
    private PrintWriter out;
    private String nick;

    public GuiClient() {
        super("Multi-room Chat");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(820, 540);
        setLocationRelativeTo(null);

        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);

        JPanel left = new JPanel(new GridLayout(2, 1, 0, 6));
        left.add(titled(new JScrollPane(roomList), "Phòng (nhấp đúp để vào)"));
        left.add(titled(new JScrollPane(userList), "Người dùng (nhấp đúp để nhắn riêng)"));
        left.setPreferredSize(new Dimension(220, 0));

        JButton sendBtn = new JButton("Gửi");
        JButton newRoomBtn = new JButton("Tạo/Vào phòng...");
        JPanel bottom = new JPanel(new BorderLayout(6, 0));
        bottom.add(newRoomBtn, BorderLayout.WEST);
        bottom.add(input, BorderLayout.CENTER);
        bottom.add(sendBtn, BorderLayout.EAST);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        root.add(left, BorderLayout.WEST);
        root.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        root.add(status, BorderLayout.NORTH);
        setContentPane(root);

        input.addActionListener(e -> sendInput());
        sendBtn.addActionListener(e -> sendInput());
        newRoomBtn.addActionListener(e -> {
            String r = JOptionPane.showInputDialog(this, "Tên phòng:");
            if (r != null && !r.isBlank()) send("JOIN " + r.trim());
        });
        roomList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && roomList.getSelectedValue() != null) {
                    String v = roomList.getSelectedValue();
                    send("JOIN " + v.substring(0, v.lastIndexOf(" (")));
                }
            }
        });
        userList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && userList.getSelectedValue() != null) {
                    input.setText("/pm " + userList.getSelectedValue() + " ");
                    input.requestFocus();
                }
            }
        });
    }

    private static JComponent titled(JComponent c, String title) {
        c.setBorder(BorderFactory.createTitledBorder(title));
        return c;
    }

    private void connect() {
        JTextField hostF = new JTextField("localhost");
        JTextField portF = new JTextField(String.valueOf(Protocol.DEFAULT_PORT));
        JTextField nickF = new JTextField();
        JPasswordField passF = new JPasswordField();
        JCheckBox createF = new JCheckBox("Đăng ký tài khoản mới");
        Object[] form = {"Server:", hostF, "Cổng:", portF, "Tài khoản:", nickF, "Mật khẩu:", passF, createF};
        while (true) {
            if (JOptionPane.showConfirmDialog(null, form, "Kết nối",
                    JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) System.exit(0);
            try {
                socket = new Socket(hostF.getText().trim(), Integer.parseInt(portF.getText().trim()));
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                break;
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null, "Không kết nối được: " + ex.getMessage());
            }
        }
        nick = nickF.getText().trim();
        new Thread(this::readLoop, "reader").start();
        String password=new String(passF.getPassword());
        if(createF.isSelected())send("REGISTER "+nick+" "+password);
        send("LOGIN "+nick+" "+password);
        send("WATCH");
    }

    private void readLoop() {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                final String l = line;
                SwingUtilities.invokeLater(() -> handle(l));
            }
        } catch (IOException ignored) { }
        SwingUtilities.invokeLater(() -> {
            append("! Mất kết nối tới server.");
            status.setText(" Đã ngắt kết nối");
            input.setEnabled(false);
        });
    }

    private void handle(String line) {
        String[] f = line.split("\t");
        String t = LocalTime.now().withNano(0).toString();
        switch (f[0]) {
            case "CHAT":   append("[" + t + "] " + f[2] + ": " + f[3]); break;
            case "HISTORY": append("[" + f[2] + "] " + f[3] + ": " + f[4]); break;
            case "PM":     append("[" + t + "] (riêng) " + f[1] + " -> " + f[2] + ": " + f[3]); break;
            case "SYSTEM": append("* " + f[2]); break;
            case "INFO":   append("* " + f[1]); break;
            case "ERROR":  append("! " + f[1]); break;
            case "LOGINOK": nick = f[1]; break;
            case "REGISTEROK": append("* "+f[1]);break;
            case "FILEMSG":append("* File từ "+f[3]+": "+f[5]+" (dùng Web để tải)");break;
            case "PMHISTORY":append("[riêng]["+f[4]+"] "+f[1]+" -> "+f[2]+": "+f[3]);break;
            case "JOINED":
                chatArea.setText("");
                status.setText(" " + nick + "  |  Phòng: " + f[1]);
                append("* Bạn đã vào phòng '" + f[1] + "'");
                break;
            case "ROOMS":
                roomModel.clear();
                if (f.length > 1 && !f[1].isEmpty()) {
                    for (String r : f[1].split(",")) {
                        int i = r.lastIndexOf(':');
                        roomModel.addElement(r.substring(0, i) + " (" + r.substring(i + 1) + ")");
                    }
                }
                break;
            case "USERS":
                userModel.clear();
                if (f.length > 2 && !f[2].isEmpty()) for (String u : f[2].split(",")) userModel.addElement(u);
                break;
            default: break;
        }
    }

    private void sendInput() {
        String text = input.getText().trim();
        input.setText("");
        if (text.isEmpty()) return;
        if (text.equalsIgnoreCase("/help")) { append(Protocol.HELP); return; }
        String wire = Protocol.toWire(text);
        if (wire == null) { append(Protocol.HELP); return; }
        send(wire);
        if (wire.equals("QUIT")) System.exit(0);
    }

    private void send(String line) {
        if (out != null) out.println(line);
    }

    private void append(String s) {
        chatArea.append(s + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GuiClient c = new GuiClient();
            c.connect();
            c.setVisible(true);
        });
    }
}
