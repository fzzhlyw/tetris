import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.List;

public class TetrisNetServer {
    private static final int DEFAULT_PORT = 8888;
    private static final int MAX_PLAYERS = 100;
    private static final int SOCKET_TIMEOUT_MS = 30_000;
    private static final int HEARTBEAT_INTERVAL_MS = 10_000;
    private static final int MAX_MSG_PER_SEC = 60;
    private final int port;
    private ServerSocket serverSocket;
    private final java.util.List<PlayerConnection> players = new CopyOnWriteArrayList<>();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final AtomicInteger totalConnections = new AtomicInteger(0);
    private final long startTime;
    private static final Object stateLock = new Object();
    private ExecutorService playerThreadPool;
    private ScheduledExecutorService heartbeatScheduler;

    private JFrame frame;
    private PlayerTableModel tableModel;
    private JLabel statusLabel;
    private JLabel infoLabel;

    public TetrisNetServer(int port) {
        this.port = port;
        startTime = System.currentTimeMillis();
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
                if (port < 1 || port > 65535) {
                    System.err.println("端口号范围 1-65535，使用默认端口 " + DEFAULT_PORT);
                    port = DEFAULT_PORT;
                }
            } catch (NumberFormatException e) {
                System.err.println("无效端口号，使用默认端口 " + DEFAULT_PORT);
            }
        }
        new TetrisNetServer(port).start();
    }

    public void start() {
        SwingUtilities.invokeLater(this::buildGUI);

        playerThreadPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "heartbeat");
            t.setDaemon(true);
            return t;
        });

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                SwingUtilities.invokeLater(this::updateStatus);

                while (true) {
                    Socket socket = serverSocket.accept();
                    socket.setSoTimeout(SOCKET_TIMEOUT_MS);
                    int activeCount = 0;
                    for (PlayerConnection p : players) {
                        if (p.running) activeCount++;
                    }
                    if (activeCount >= MAX_PLAYERS) {
                        try (PrintWriter reject = new PrintWriter(socket.getOutputStream(), true)) {
                            reject.println("SERVER_FULL");
                        }
                        socket.close();
                        continue;
                    }
                    int id = nextId.getAndIncrement();
                    totalConnections.incrementAndGet();

                    PlayerConnection pc = new PlayerConnection(socket, id);
                    players.add(pc);
                    pc.future = playerThreadPool.submit(pc);
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    if (statusLabel != null) {
                        statusLabel.setText("服务端已停止: " + e.getMessage());
                    } else {
                        System.err.println("服务端停止: " + e.getMessage());
                    }
                });
            }
        }, "server-accept").start();

        heartbeatScheduler.scheduleAtFixedRate(() -> {
            for (PlayerConnection p : players) {
                try {
                    if (p.running) {
                        p.send("PING");
                        if (p.missedPongs.incrementAndGet() > 3) {
                            if (p.socket != null && !p.socket.isClosed()) p.socket.close();
                        }
                    }
                } catch (Exception ignored) {
                    // ignore per-player heartbeat errors
                }
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
            } catch (IOException ignored) {}
            for (PlayerConnection p : players) {
                p.running = false;
                try {
                    if (p.socket != null && !p.socket.isClosed()) p.socket.close();
                } catch (IOException ignored) {}
            }
            players.clear();
            if (playerThreadPool != null) playerThreadPool.shutdownNow();
            if (heartbeatScheduler != null) heartbeatScheduler.shutdownNow();
        }, "server-shutdown"));
    }

    private void buildGUI() {
        frame = new JFrame("TetrisNet 服务端监控");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(780, 520);
        frame.setMinimumSize(new Dimension(640, 400));
        frame.setLocationRelativeTo(null);

        // Header panel
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(0x2B, 0x2B, 0x2B));
        header.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

        JLabel title = new JLabel("TetrisNet 服务端管理");
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        title.setForeground(new Color(0x00, 0xDD, 0xAA));
        header.add(title, BorderLayout.WEST);

        infoLabel = new JLabel();
        infoLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
        infoLabel.setForeground(new Color(0xCC, 0xCC, 0xCC));
        header.add(infoLabel, BorderLayout.EAST);

        // Table
        tableModel = new PlayerTableModel();
        JTable table = new JTable(tableModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                if (!isRowSelected(row)) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(0xF0, 0xF5, 0xFF));
                }
                return c;
            }
        };
        table.setFillsViewportHeight(true);
        table.setRowHeight(30);
        table.setFont(new Font("SansSerif", Font.PLAIN, 14));
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 13));
        table.getTableHeader().setBackground(new Color(0xE8, 0xE8, 0xE8));
        table.getTableHeader().setReorderingAllowed(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }

        TableColumnModel cm = table.getColumnModel();
        cm.getColumn(0).setPreferredWidth(50);
        cm.getColumn(0).setMaxWidth(70);
        cm.getColumn(1).setPreferredWidth(120);
        cm.getColumn(2).setPreferredWidth(180);
        cm.getColumn(3).setPreferredWidth(90);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "已连接玩家"));
        scroll.getViewport().setBackground(Color.WHITE);

        // Status bar
        statusLabel = new JLabel("服务端启动中...");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(6, 10, 6, 10),
            BorderFactory.createEtchedBorder()));

        frame.add(header, BorderLayout.NORTH);
        frame.add(scroll, BorderLayout.CENTER);
        frame.add(statusLabel, BorderLayout.SOUTH);
        frame.setVisible(true);

        new javax.swing.Timer(1000, e -> updateInfoLabel()).start();
    }

    private void updateInfoLabel() {
        long elapsed = System.currentTimeMillis() - startTime;
        long h = elapsed / 3600000;
        long m = (elapsed % 3600000) / 60000;
        long s = (elapsed % 60000) / 1000;
        infoLabel.setText(String.format("运行时间: %02d:%02d:%02d  |  历史连接: %d",
            h, m, s, totalConnections.get()));
    }

    private void updateStatus() {
        long count = players.stream().filter(p -> p.running).count();
        statusLabel.setText("运行中  |  端口: " + port + "  |  当前在线: " + count);
    }

    private void refreshTable() {
        SwingUtilities.invokeLater(() -> {
            tableModel.fireTableDataChanged();
            updateStatus();
        });
    }

    private void broadcastPlayerList() {
        String data = buildPlayerListData();
        String msg = "PLAYER_LIST:" + data;
        for (PlayerConnection p : players) {
            if (p.running) {
                try { p.send(msg); } catch (Exception ignored) {}
            }
        }
        refreshTable();
    }

    private String buildPlayerListData() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (PlayerConnection p : players) {
            if (p.running) {
                if (!first) sb.append(",");
                sb.append(p.id).append(":").append(p.status);
                first = false;
            }
        }
        return sb.toString();
    }

    private PlayerConnection findPlayer(int id) {
        for (PlayerConnection p : players) {
            if (p.id == id && p.running) return p;
        }
        return null;
    }

    private class PlayerTableModel extends AbstractTableModel {
        private final String[] columns = {"ID", "状态", "IP 地址", "客户端端口"};

        @Override
        public int getRowCount() {
            int count = 0;
            for (PlayerConnection p : players) {
                if (p.running) count++;
            }
            return count;
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int col) {
            return columns[col];
        }

        @Override
        public Object getValueAt(int row, int col) {
            List<PlayerConnection> active = new ArrayList<>();
            for (PlayerConnection p : players) {
                if (p.running) active.add(p);
            }
            if (row < 0 || row >= active.size()) return "";
            PlayerConnection p = active.get(row);
            switch (col) {
                case 0: return p.id;
                case 1: return p.status;
                case 2: return p.socket != null ? p.socket.getInetAddress().getHostAddress() : "";
                case 3: return p.socket != null ? p.socket.getPort() : -1;
                default: return "";
            }
        }
    }

    private class PlayerConnection implements Runnable {
        Socket socket;
        PrintWriter out;
        BufferedReader in;
        final int id;
        volatile boolean running = true;
        volatile String status = "idle";
        volatile Integer opponentId = null;
        volatile int challengedBy = -1;
        volatile int challengeTargetId = -1;
        volatile boolean rematchAsked = false;
        final AtomicInteger missedPongs = new AtomicInteger(0);
        final AtomicInteger msgCount = new AtomicInteger(0);
        private volatile long msgWindowStart = System.nanoTime();
        Future<?> future;

        PlayerConnection(Socket socket, int id) {
            this.socket = socket;
            this.id = id;
        }

        void send(String msg) {
            if (out != null && running) {
                synchronized (out) {
                    out.println(msg);
                    out.flush();
                }
            }
        }

        private void cleanup() {
            Integer myOpponentId = null;
            int myChallengedBy = -1;
            int myChallengeTargetId = -1;
            boolean wasPlaying = false;

            synchronized (TetrisNetServer.stateLock) {
                if (!running) return;
                running = false;
                if (opponentId != null) {
                    myOpponentId = opponentId;
                    wasPlaying = "result".equals(status);
                }
                myChallengedBy = challengedBy;
                myChallengeTargetId = challengeTargetId;
                players.remove(this);
            }

            if (future != null) {
                future.cancel(false);
            }

            // Notify opponent
            if (myOpponentId != null) {
                PlayerConnection opp = findPlayer(myOpponentId);
                if (opp != null) {
                    if (wasPlaying) {
                        opp.send("OPPONENT_LEFT");
                    } else {
                        opp.send("GAMEOVER");
                    }
                    synchronized (TetrisNetServer.stateLock) {
                        opp.opponentId = null;
                        opp.status = "idle";
                        opp.rematchAsked = false;
                    }
                }
            }
            // Notify challenger (if I was the target being challenged)
            if (myChallengedBy != -1) {
                PlayerConnection challenger = findPlayer(myChallengedBy);
                if (challenger != null) {
                    synchronized (TetrisNetServer.stateLock) {
                        challenger.status = "idle";
                        challenger.challengeTargetId = -1;
                    }
                    challenger.send("CHALLENGE_REJECTED");
                }
            }
            // Notify target (if I was the challenger)
            if (myChallengeTargetId != -1) {
                PlayerConnection target = findPlayer(myChallengeTargetId);
                if (target != null) {
                    synchronized (TetrisNetServer.stateLock) {
                        target.challengedBy = -1;
                    }
                    target.send("CHALLENGE_CANCELLED");
                }
            }

            broadcastPlayerList();
            try {
                Socket s = socket;
                if (s != null && !s.isClosed()) s.close();
            } catch (IOException ignored) {}
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                send("PLAYER_ID:" + id);
                broadcastPlayerList();

                String line;
                while (running && (line = in.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    // rate limiting
                    long now = System.nanoTime();
                    long windowStart = msgWindowStart;
                    if (now - windowStart > 1_000_000_000L) {
                        msgCount.set(0);
                        msgWindowStart = now;
                    }
                    if (msgCount.incrementAndGet() > MAX_MSG_PER_SEC) {
                        send("RATE_LIMITED");
                        running = false;
                        break;
                    }
                    if (line.equals("PONG")) {
                        missedPongs.set(0);
                        continue;
                    }
                    handleMessage(line);
                }
            } catch (SocketTimeoutException e) {
                send("TIMEOUT");
            } catch (IOException e) {
                // disconnected
            } finally {
                cleanup();
            }
        }

        private void handleMessage(String line) {

            if (line.equals("SINGLEPLAYER")) {
                synchronized (TetrisNetServer.stateLock) {
                    status = "single";
                }
                broadcastPlayerList();
                return;
            }

            if (line.equals("LOBBY")) {
                int myChallengedBy, myChallengeTarget;
                synchronized (TetrisNetServer.stateLock) {
                    Integer myOppId = opponentId;
                    if ("result".equals(status) && myOppId != null) {
                        PlayerConnection opp = findPlayer(myOppId);
                        if (opp != null && "result".equals(opp.status)) {
                            opp.send("OPPONENT_LEFT");
                            opp.status = "idle";
                            opp.opponentId = null;
                            opp.rematchAsked = false;
                        }
                    }
                    myChallengedBy = challengedBy;
                    myChallengeTarget = challengeTargetId;
                    status = "idle";
                    opponentId = null;
                    challengedBy = -1;
                    challengeTargetId = -1;
                    rematchAsked = false;
                }
                if (myChallengedBy != -1) {
                    PlayerConnection challenger = findPlayer(myChallengedBy);
                    if (challenger != null) {
                        synchronized (TetrisNetServer.stateLock) {
                            challenger.status = "idle";
                            challenger.challengeTargetId = -1;
                        }
                        challenger.send("CHALLENGE_REJECTED");
                    }
                }
                if (myChallengeTarget != -1) {
                    PlayerConnection target = findPlayer(myChallengeTarget);
                    if (target != null) {
                        synchronized (TetrisNetServer.stateLock) {
                            target.challengedBy = -1;
                        }
                        target.send("CHALLENGE_CANCELLED");
                    }
                }
                broadcastPlayerList();
                return;
            }

            if (line.startsWith("CHALLENGE:")) {
                if (line.length() <= 10) {
                    send("CHALLENGE_REJECTED");
                    return;
                }
                int targetId;
                try {
                    targetId = Integer.parseInt(line.substring(10));
                } catch (NumberFormatException e) {
                    send("CHALLENGE_REJECTED");
                    return;
                }
                if (targetId < 1) {
                    send("CHALLENGE_REJECTED");
                    return;
                }
                if (targetId == id) {
                    send("CHALLENGE_REJECTED");
                    return;
                }
                PlayerConnection target = findPlayer(targetId);
                if (target == null || !target.running) {
                    send("CHALLENGE_REJECTED");
                    return;
                }
                synchronized (TetrisNetServer.stateLock) {
                    String myStatus = status;
                    if (!myStatus.equals("idle") && !myStatus.equals("single")) {
                        send("CHALLENGE_REJECTED");
                        return;
                    }
                    String ts = target.status;
                    if (!ts.equals("idle") && !ts.equals("single")) {
                        send("CHALLENGE_REJECTED");
                        return;
                    }
                    challengeTargetId = targetId;
                    target.challengedBy = id;
                    status = "challenging";
                }
                target.send("CHALLENGE:" + id);
                broadcastPlayerList();
                return;
            }

            if (line.equals("CHALLENGE_CANCEL")) {
                int myTargetId;
                synchronized (TetrisNetServer.stateLock) {
                    myTargetId = challengeTargetId;
                    challengeTargetId = -1;
                    status = "idle";
                }
                if (myTargetId != -1) {
                    PlayerConnection target = findPlayer(myTargetId);
                    if (target != null) {
                        synchronized (TetrisNetServer.stateLock) {
                            target.challengedBy = -1;
                        }
                        target.send("CHALLENGE_CANCELLED");
                    }
                }
                broadcastPlayerList();
                return;
            }

            if (line.equals("CHALLENGE_ACCEPT")) {
                PlayerConnection challenger;
                int challengerId;
                synchronized (TetrisNetServer.stateLock) {
                    int myChallengerId = challengedBy;
                    if (myChallengerId == -1) return;
                    challenger = findPlayer(myChallengerId);
                    if (challenger == null || !challenger.running) {
                        challengedBy = -1;
                        return;
                    }
                    challengedBy = -1;
                    challengerId = challenger.id;
                    challenger.status = "playing";
                    challenger.opponentId = id;
                    challenger.challengeTargetId = -1;
                    status = "playing";
                    opponentId = challengerId;
                }
                send("GAME_START:" + challengerId);
                challenger.send("GAME_START:" + id);
                broadcastPlayerList();
                return;
            }

            if (line.equals("CHALLENGE_REJECT")) {
                int myChallengerId;
                synchronized (TetrisNetServer.stateLock) {
                    myChallengerId = challengedBy;
                    challengedBy = -1;
                }
                if (myChallengerId != -1) {
                    PlayerConnection challenger = findPlayer(myChallengerId);
                    if (challenger != null) {
                        synchronized (TetrisNetServer.stateLock) {
                            if (challenger.challengeTargetId == id) {
                                challenger.status = "idle";
                                challenger.challengeTargetId = -1;
                                challenger.send("CHALLENGE_REJECTED");
                            }
                        }
                    }
                }
                broadcastPlayerList();
                return;
            }

            Integer currentOpponentId = opponentId;
            if (currentOpponentId != null) {
                PlayerConnection opp = findPlayer(currentOpponentId);
                if (opp == null) return;

                if (line.equals("GAMEOVER")) {
                    synchronized (TetrisNetServer.stateLock) {
                        opp.status = "result";
                        status = "result";
                    }
                    opp.send("GAMEOVER");
                    broadcastPlayerList();
                } else if (line.equals("RESTART")) {
                    synchronized (TetrisNetServer.stateLock) {
                        opp.status = "playing";
                        status = "playing";
                    }
                    opp.send("RESTART");
                    broadcastPlayerList();
                } else if (line.equals("REMATCH")) {
                    synchronized (TetrisNetServer.stateLock) {
                        if (status.equals("playing")) return;
                        if (!opp.running || !opp.status.equals("result")) {
                            send("OPPONENT_LEFT");
                            status = "idle";
                            opponentId = null;
                            broadcastPlayerList();
                            return;
                        }
                        rematchAsked = true;
                    }
                    opp.send("REMATCH_OFFER");
                } else if (line.equals("REMATCH_ACCEPT")) {
                    if (!opp.running) return;
                    synchronized (TetrisNetServer.stateLock) {
                        if (!status.equals("result") && !status.equals("idle")) return;
                        if (!opp.status.equals("result") && !opp.status.equals("idle")) {
                            send("OPPONENT_LEFT");
                            status = "idle";
                            opponentId = null;
                            broadcastPlayerList();
                            return;
                        }
                        opp.status = "playing";
                        opp.rematchAsked = false;
                        status = "playing";
                        rematchAsked = false;
                    }
                    send("GAME_START:" + opp.id);
                    opp.send("GAME_START:" + id);
                    broadcastPlayerList();
                } else if (line.equals("REMATCH_REJECT")) {
                    boolean wasAsked;
                    synchronized (TetrisNetServer.stateLock) {
                        wasAsked = opp.rematchAsked;
                        if (wasAsked) {
                            opp.status = "idle";
                            opp.opponentId = null;
                            opp.rematchAsked = false;
                        }
                        status = "idle";
                        opponentId = null;
                        rematchAsked = false;
                    }
                    if (wasAsked) opp.send("REMATCH_REJECTED");
                    broadcastPlayerList();
                } else if (line.startsWith("LINES:") || line.startsWith("BOARD:")) {
                    opp.send(line);
                }
                return;
            }
        }
    }
}
