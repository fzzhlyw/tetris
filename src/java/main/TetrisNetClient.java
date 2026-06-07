import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class TetrisNetClient extends JFrame {

    private static final int COLS = 10;
    private static final int ROWS = 20;
    private static final int BLOCK_SIZE = 28;
    private static final int GAME_WIDTH = COLS * BLOCK_SIZE;
    private static final int GAME_HEIGHT = ROWS * BLOCK_SIZE;
    private static final int PREVIEW_BLOCK = 14;
    private static final int OPP_BLOCK = 14;
    private static final int OPP_COLS = 10;
    private static final int OPP_ROWS = 20;
    private static final int SIDE_PANEL_WIDTH = 220;
    private static final int TOTAL_WIDTH = GAME_WIDTH + SIDE_PANEL_WIDTH + 30;
    private static final int TOTAL_HEIGHT = GAME_HEIGHT + 30 + 200;
    private static final int BAG_SIZE = 7;
    private static final int QUEUE_SIZE = 3 + BAG_SIZE;
    private static final int SERVER_PORT = 8888;

    private static final Color[] COLORS = {
        new Color(0x00, 0xCC, 0xFF), new Color(0x00, 0x50, 0xFF),
        new Color(0xFF, 0xA0, 0x00), new Color(0xFF, 0xDC, 0x00),
        new Color(0x00, 0xCC, 0x44), new Color(0x90, 0x00, 0xFF),
        new Color(0xFF, 0x30, 0x30),
    };
    private static final Color GARBAGE_COLOR = new Color(0x55, 0x55, 0x55);
    private static final Color[] DARKER = new Color[7];
    static { for (int i = 0; i < 7; i++) DARKER[i] = COLORS[i].darker(); }

    private static final int[][][] SHAPES = {
        {{0,0,0,0},{1,1,1,1},{0,0,0,0},{0,0,0,0}},
        {{2,0,0},{2,2,2},{0,0,0}},
        {{0,0,3},{3,3,3},{0,0,0}},
        {{4,4},{4,4}},
        {{0,5,5},{5,5,0},{0,0,0}},
        {{0,6,0},{6,6,6},{0,0,0}},
        {{7,7,0},{0,7,7},{0,0,0}},
    };

    private static final Color WHITE10 = new Color(255,255,255,10);
    private static final Color WHITE20 = new Color(255,255,255,20);
    private static final Color WHITE30 = new Color(255,255,255,30);
    private static final Color WHITE40 = new Color(255,255,255,40);
    private static final Color WHITE50 = new Color(255,255,255,50);
    private static final Color WHITE60 = new Color(255,255,255,60);
    private static final Color WHITE80 = new Color(255,255,255,80);
    private static final Color WHITE120 = new Color(255,255,255,120);
    private static final Color WHITE200 = new Color(255,255,255,200);
    private static final Color BG_DARK = new Color(0x0E,0x0E,0x28);
    private static final Color BG_PANEL = new Color(0x16,0x21,0x3E);
    private static final Color BG_DARKER = new Color(0x0C,0x0C,0x18);
    private static final Color GRID_LINE = new Color(0x4A,0x4A,0x6A);
    private static final Color LABEL_COLOR = new Color(180,190,210);
    private static final Color FOCUS_COLOR = new Color(100,180,255);
    private static final Color BORDER_NEON = new Color(0x00, 0xFF, 0xAA);
    private static final Color HOLD_USED = new Color(180, 190, 210);
    private static final Color BTN_EXIT = new Color(220, 70, 70);
    private static final Color BTN_ACCEPT = new Color(80, 255, 150);
    private static final Color BTN_PRACTICE = new Color(60, 220, 180);
    private static final Color BTN_PRO = new Color(255, 180, 50);
    private static final Color BTN_BACK = new Color(140, 200, 80);
    private static final Color BTN_CREATE = new Color(0, 195, 255);
    private static final Color BTN_JOIN = new Color(80, 230, 120);
    private static final Color BTN_SERVER = new Color(180, 110, 255);
    private static final Color BTN_LOBBY_PRACTICE = new Color(74, 107, 245);
    private static final Color BTN_LOBBY_REFRESH = new Color(255, 140, 66);
    private static final Color BTN_LOBBY_BACK = new Color(232, 86, 107);
    private static final Color BTN_LOBBY_CHALLENGE = new Color(155, 89, 182);
    private static final Color BTN_LOBBY_ACCEPT = new Color(26, 188, 156);
    private static final Color BTN_LOBBY_REJECT = new Color(231, 76, 60);
    private static final Color BTN_LOBBY_CANCEL = new Color(243, 156, 18);
    private static final int OPP_WIDTH = OPP_COLS * OPP_BLOCK;
    private static final int OPP_HEIGHT = OPP_ROWS * OPP_BLOCK;

    private static final Random RND = new Random();
    private int[][] board = new int[ROWS][COLS];
    private int[][] oppBoard = new int[OPP_ROWS][OPP_COLS];
    private int score = 0, level = 1, lines = 0;
    private int oppScore = 0, oppLevel = 1, oppLines = 0;
    private boolean gameOver = false, paused = false, gameStarted = false;
    private boolean oppGameOver = false;
    private boolean won = false;
    private int currentType = -1, currentX, currentY;
    private int[][] currentShape;
    private int[] nextQueue = new int[QUEUE_SIZE];
    private int holdType = -1;
    private boolean holdUsed = false;
    private int dropInterval = 600;
    private long lastDropTime = 0;
    private boolean flashRows = false;
    private int[] flashRowsList = new int[ROWS];
    private int flashCount = 0;
    private int flashMax = 0;
    private java.util.Timer clearTimer = null;
    private boolean showGhost = true;
    private boolean isLocking = false;
    private int garbagePending = 0;

    private int[] bagBuffer = new int[BAG_SIZE];
    private int bagPtr = 0;

    private Socket socket;
    private volatile PrintWriter netOut;
    private volatile BufferedReader netIn;
    private volatile boolean connected = false;
    private volatile boolean connectingCancelled = false;
    private int myPlayerId = 0;
    private boolean isHost = false;
    private ServerSocket hostServerSocket;
    private Thread networkThread;

    private enum Screen { MENU, HOST_PORT_INPUT, HOST_WAIT, JOIN_INPUT, LOBBY, PLAYING, RESULT }
    private Screen currentScreen = Screen.MENU;
    private String resultMessage = "";
    private String joinIp = getDefaultJoinIp();
    private int joinPort = SERVER_PORT;
    private String joinPortStr = String.valueOf(SERVER_PORT);
    private String hostPortStr = String.valueOf(SERVER_PORT);
    private int hostPort = SERVER_PORT;
    private String statusMessage = "";
    private long statusMessageEndTime = 0;
    private int menuHover = -1;
    private int joinMode = 0; // 0=join host, 1=connect to dedicated server

    private java.util.List<Integer> lobbyIds = new java.util.ArrayList<>();
    private java.util.List<String> lobbyStatuses = new java.util.ArrayList<>();
    private int selectedLobbyIdx = -1;
    private int challengeFromId = -1;
    private boolean challengePending = false;
    private int challengeTargetId = -1;
    private boolean showChallengeDialog = false;
    private boolean inSinglePlayer = false;
    private boolean rematchOffer = false;
    private boolean rematchPending = false;
    private boolean showRematchDialog = false;
    private javax.swing.Timer rematchTimeoutTimer = null;
    private String toastMessage = "";
    private long toastEndTime = 0;
    private int lobbyBtnHover = -1;

    private GamePanel gamePanel;
    private javax.swing.Timer gameTimer;

    public TetrisNetClient() {
        setTitle("Tetris Net");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        getContentPane().setBackground(BG_DARK);
        setLayout(new BorderLayout());
        gamePanel = new GamePanel();
        gamePanel.setOpaque(true);
        gamePanel.setBackground(BG_DARK);
        add(gamePanel, BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(null);
        try {
            BufferedImage icon = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            setIconImage(icon);
        } catch (Exception ignored) {}
        initGame();
    }

    private void initGame() {
        board = new int[ROWS][COLS];
        oppBoard = new int[OPP_ROWS][OPP_COLS];
        score = 0; level = 1; lines = 0;
        oppScore = 0; oppLevel = 1; oppLines = 0;
        holdType = -1; holdUsed = false;
        currentType = -1; currentShape = null;
        gameOver = false; paused = false; gameStarted = false;
        oppGameOver = false; won = false;
        flashRows = false; garbagePending = 0; isLocking = false;
        connected = false;
        connectingCancelled = false;
        currentScreen = Screen.MENU;
        rematchOffer = false;
        rematchPending = false;
        showRematchDialog = false;
        resultMessage = "";
        hostPortStr = String.valueOf(SERVER_PORT);
        joinPortStr = String.valueOf(SERVER_PORT);
        statusMessage = "";
        statusMessageEndTime = 0;
        fillBag();
        for (int i = 0; i < QUEUE_SIZE; i++) nextQueue[i] = getNextPiece();
    }

    private void fillBag() {
        Integer[] types = {0,1,2,3,4,5,6};
        for (int i = types.length-1; i > 0; i--) {
            int j = RND.nextInt(i+1);
            Integer tmp = types[i]; types[i] = types[j]; types[j] = tmp;
        }
        for (int i = 0; i < BAG_SIZE; i++) bagBuffer[i] = types[i];
        bagPtr = 0;
    }

    private int getNextPiece() {
        if (bagPtr >= BAG_SIZE) fillBag();
        return bagBuffer[bagPtr++];
    }

    private void spawnPiece() {
        currentType = nextQueue[0];
        for (int i = 0; i < QUEUE_SIZE-1; i++) nextQueue[i] = nextQueue[i+1];
        nextQueue[QUEUE_SIZE-1] = getNextPiece();
        currentShape = cloneShape(SHAPES[currentType]);
        currentX = COLS/2 - currentShape[0].length/2;
        currentY = 0;
        if (currentType == 3) currentX = COLS/2 - 1;
        if (!validPos(currentShape, currentX, currentY)) {
            gameOver = true;
            currentShape = null;
            sendGameOver();
            SwingUtilities.invokeLater(() -> {
                if (!won) resultMessage = "你输了！";
                currentScreen = Screen.RESULT;
                gamePanel.repaint();
            });
        }
    }

    private int[][] cloneShape(int[][] src) {
        int r = src.length, c = src[0].length;
        int[][] dst = new int[r][c];
        for (int i = 0; i < r; i++) System.arraycopy(src[i], 0, dst[i], 0, c);
        return dst;
    }

    private boolean validPos(int[][] shape, int px, int py) {
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape[r].length; c++) {
                if (shape[r][c] != 0) {
                    int bx = px + c, by = py + r;
                    if (bx < 0 || bx >= COLS || by >= ROWS) return false;
                    if (by >= 0 && board[by][bx] != 0) return false;
                }
            }
        }
        return true;
    }

    private int[][] rotateCW(int[][] shape) {
        int r = shape.length, c = shape[0].length;
        int[][] rotated = new int[c][r];
        for (int i = 0; i < r; i++)
            for (int j = 0; j < c; j++)
                rotated[j][r-1-i] = shape[i][j];
        return rotated;
    }

    private int getGhostY() {
        if (currentShape == null) return currentY;
        int gy = currentY;
        while (validPos(currentShape, currentX, gy+1)) gy++;
        return gy;
    }

    private void lockPiece() {
        if (gameOver || paused || currentShape == null || isLocking) return;
        isLocking = true;
        try {
            for (int r = 0; r < currentShape.length; r++) {
                for (int c = 0; c < currentShape[r].length; c++) {
                    if (currentShape[r][c] != 0) {
                        int bx = currentX + c, by = currentY + r;
                        if (by >= 0 && by < ROWS && bx >= 0 && bx < COLS)
                            board[by][bx] = currentType + 1;
                    }
                }
            }
            checkLines();
            sendBoard();
        } finally { isLocking = false; }
    }

    private void checkLines() {
        java.util.List<Integer> full = new ArrayList<>();
        for (int r = 0; r < ROWS; r++) {
            boolean fullRow = true;
            for (int c = 0; c < COLS; c++) {
                if (board[r][c] == 0) { fullRow = false; break; }
            }
            if (fullRow) full.add(r);
        }
        if (!full.isEmpty()) {
            flashRows = true;
            flashCount = 0;
            flashMax = full.size();
            for (int i = 0; i < full.size() && i < flashRowsList.length; i++)
                flashRowsList[i] = full.get(i);
            gamePanel.repaint();
            if (clearTimer != null) { clearTimer.cancel(); clearTimer.purge(); }
            clearTimer = new java.util.Timer();
            clearTimer.schedule(new TimerTask() {
                public void run() {
                    SwingUtilities.invokeLater(() -> {
                        if (!flashRows) return;
                        clearRows(full);
                        clearTimer = null;
                    });
                }
            }, 300);
        } else {
            spawnPiece();
            lastDropTime = System.currentTimeMillis();
            holdUsed = false;
        }
    }

    private void clearRows(java.util.List<Integer> rows) {
        boolean[] clearRow = new boolean[ROWS];
        for (int r : rows) clearRow[r] = true;
        int[][] newBoard = new int[ROWS][COLS];
        int writeRow = ROWS - 1;
        for (int r = ROWS-1; r >= 0; r--) {
            if (!clearRow[r]) {
                System.arraycopy(board[r], 0, newBoard[writeRow], 0, COLS);
                writeRow--;
            }
        }
        board = newBoard;
        int n = rows.size();
        lines += n;
        level = Math.min(15, lines / 10 + 1);
        dropInterval = Math.max(50, 600 - (level-1) * 40);
        int[] points = {0, 100, 300, 500, 800};
        score += points[Math.min(n, points.length-1)] * level;
        sendLines(n);
        applyGarbage();
        if (gameOver) { flashRows = false; gamePanel.repaint(); return; }
        spawnPiece();
        lastDropTime = System.currentTimeMillis();
        holdUsed = false;
        flashRows = false;
        gamePanel.repaint();
    }

    private void applyGarbage() {
        if (garbagePending <= 0) return;
        int g = Math.min(garbagePending, ROWS);
        garbagePending -= g;
        for (int r = 0; r < ROWS - g; r++)
            System.arraycopy(board[r + g], 0, board[r], 0, COLS);
        for (int r = ROWS - g; r < ROWS; r++) {
            int gap = RND.nextInt(COLS);
            for (int c = 0; c < COLS; c++)
                board[r][c] = (c == gap) ? 0 : 9;
        }
        sendBoard();
        if (currentShape != null && !validPos(currentShape, currentX, currentY)) {
            gameOver = true;
            sendGameOver();
            SwingUtilities.invokeLater(() -> {
                if (!won) resultMessage = "你输了！";
                currentScreen = Screen.RESULT;
                gamePanel.repaint();
            });
        }
    }

    private void moveLeft() {
        if (currentShape != null && validPos(currentShape, currentX-1, currentY))
            currentX--;
    }

    private void moveRight() {
        if (currentShape != null && validPos(currentShape, currentX+1, currentY))
            currentX++;
    }

    private void softDrop() {
        if (currentShape != null && validPos(currentShape, currentX, currentY+1)) {
            currentY++;
            score++;
            lastDropTime = System.currentTimeMillis();
        }
    }

    private void hardDrop() {
        if (currentShape == null) return;
        int drops = 0;
        while (validPos(currentShape, currentX, currentY+1)) { currentY++; drops++; }
        score += drops * 2;
        lockPiece();
    }

    private void rotate() {
        if (currentShape == null) return;
        int[][] rotated = rotateCW(currentShape);
        int[] kicks = {0, -1, 1, -2, 2};
        for (int kick : kicks) {
            if (validPos(rotated, currentX + kick, currentY)) {
                currentShape = rotated;
                currentX += kick;
                return;
            }
            if (validPos(rotated, currentX + kick, currentY - 1) && currentY > 0) {
                currentShape = rotated;
                currentX += kick;
                currentY -= 1;
                return;
            }
        }
    }

    private void doHold() {
        if (holdUsed || gameOver || paused || currentShape == null) return;
        if (holdType >= 0) {
            int tmp = currentType; currentType = holdType; holdType = tmp;
            currentShape = cloneShape(SHAPES[currentType]);
            currentX = COLS/2 - currentShape[0].length/2;
            currentY = 0;
            if (currentType == 3) currentX = COLS/2 - 1;
            if (!validPos(currentShape, currentX, currentY)) {
                gameOver = true; currentShape = null;
                sendGameOver();
                SwingUtilities.invokeLater(() -> {
                    if (!won) resultMessage = "你输了！";
                    currentScreen = Screen.RESULT;
                    gamePanel.repaint();
                });
                return;
            }
        } else {
            holdType = currentType;
            spawnPiece();
            if (gameOver) return;
        }
        holdUsed = true;
        lastDropTime = System.currentTimeMillis();
    }

    private void sendLines(int n) { sendNetMsg("LINES:" + n); }
    private void sendGameOver() { sendNetMsg("GAMEOVER"); }

    private void sendNetMsg(String msg) {
        if (netOut != null && connected) {
            synchronized (netOut) { netOut.println(msg); netOut.flush(); }
        }
    }

    private void sendBoard() { sendNetMsg("BOARD:" + serializeBoard()); }

    private String serializeBoard() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < ROWS; r++) {
            if (r > 0) sb.append('|');
            for (int c = 0; c < COLS; c++)
                sb.append(board[r][c]);
        }
        return sb.toString();
    }

    private void deserializeBoard(String data) {
        String[] rows = data.split("\\|");
        for (int r = 0; r < rows.length && r < OPP_ROWS; r++) {
            String row = rows[r];
            for (int c = 0; c < row.length() && c < OPP_COLS; c++) {
                int val = row.charAt(c) - '0';
                if (val < 0 || val > 9) val = 0;
                oppBoard[r][c] = val;
            }
        }
    }

    private void startReaderThread() {
        networkThread = new Thread(() -> {
            try {
                String line;
                while ((line = netIn.readLine()) != null) {
                    final String msg = line.trim();
                    if (msg.isEmpty()) continue;
                    SwingUtilities.invokeLater(() -> handleNetMsg(msg));
                }
                // Graceful disconnection (stream ended)
                SwingUtilities.invokeLater(() -> handleDisconnect());
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> handleDisconnect());
            }
        }, "net-reader");
        networkThread.setDaemon(true);
        networkThread.start();
    }

    private void startHost() {
        isHost = true;
        currentScreen = Screen.HOST_WAIT;
        connectingCancelled = false;
        toastMessage = "游戏创建成功！端口: " + hostPort;
        toastEndTime = System.currentTimeMillis() + 3000;
        gamePanel.repaint();
        new Thread(() -> {
            try {
                hostServerSocket = new ServerSocket(hostPort);
                socket = hostServerSocket.accept();

                if (connectingCancelled) {
                    socket.close();
                    hostServerSocket.close();
                    return;
                }

                netOut = new PrintWriter(socket.getOutputStream(), true);
                netIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                myPlayerId = 0;
                netOut.println("PLAYER_ID:1");
                netOut.println("START");
                netOut.flush();

                startReaderThread();
                SwingUtilities.invokeLater(() -> {
                    connected = true;
                    currentScreen = Screen.PLAYING;
                    startGame();
                });
            } catch (IOException e) {
                if (!connectingCancelled) {
                    SwingUtilities.invokeLater(() -> {
                        resultMessage = "创建游戏失败: " + e.getMessage();
                        currentScreen = Screen.MENU;
                        gamePanel.repaint();
                    });
                }
            }
        }, "host-thread").start();
    }

    private void startJoin() {
        joinMode = 0;
        currentScreen = Screen.JOIN_INPUT;
        gamePanel.repaint();
    }

    private void startServerMode() {
        joinMode = 1;
        currentScreen = Screen.JOIN_INPUT;
        gamePanel.repaint();
    }

    private void doConnect() {
        if (joinPortStr.isEmpty()) {
            resultMessage = "请输入端口号";
            currentScreen = Screen.MENU;
            gamePanel.repaint();
            return;
        }
        try {
            joinPort = Integer.parseInt(joinPortStr);
            if (joinPort < 1024 || joinPort > 65535) {
                resultMessage = "端口号范围: 1024-65535";
                currentScreen = Screen.MENU;
                gamePanel.repaint();
                return;
            }
        } catch (NumberFormatException ex) {
            resultMessage = "无效的端口号";
            currentScreen = Screen.MENU;
            gamePanel.repaint();
            return;
        }
        currentScreen = Screen.HOST_WAIT;
        connectingCancelled = false;
        gamePanel.repaint();
        new Thread(() -> {
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(joinIp, joinPort), 5000);
                netOut = new PrintWriter(socket.getOutputStream(), true);
                netIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                if (connectingCancelled) return;

                String idLine = netIn.readLine();
                if (idLine != null && idLine.startsWith("PLAYER_ID:"))
                    myPlayerId = Integer.parseInt(idLine.substring(10));

                if (connectingCancelled) return;

                if (joinMode == 1) {
                    // Server mode: enter lobby after connect
                    startReaderThread();
                    SwingUtilities.invokeLater(() -> {
                        connected = true;
                        currentScreen = Screen.LOBBY;
                        gamePanel.repaint();
                    });
                } else {
                    // P2P join mode: expect START
                    String startLine = netIn.readLine();
                    if (startLine == null || !startLine.equals("START")) {
                        throw new IOException("Invalid server response");
                    }
                    startReaderThread();
                    SwingUtilities.invokeLater(() -> {
                        connected = true;
                        currentScreen = Screen.PLAYING;
                        startGame();
                    });
                }
            } catch (IOException e) {
                if (!connectingCancelled) {
                    SwingUtilities.invokeLater(() -> {
                        resultMessage = "连接失败: " + e.getMessage();
                        currentScreen = Screen.MENU;
                        gamePanel.repaint();
                    });
                }
            }
        }, "join-thread").start();
    }

    private void handleNetMsg(String msg) {
        // Handle player list updates regardless of screen
        if (msg.startsWith("PLAYER_LIST:")) {
            String data = msg.substring(12);
            lobbyIds.clear();
            lobbyStatuses.clear();
            if (!data.isEmpty()) {
                String[] entries = data.split(",");
                for (String entry : entries) {
                    String[] parts = entry.split(":");
                    if (parts.length < 2) continue;
                    try {
                        lobbyIds.add(Integer.parseInt(parts[0]));
                        lobbyStatuses.add(parts[1]);
                    } catch (NumberFormatException ignored) {}
                }
            }
            if (currentScreen == Screen.LOBBY) gamePanel.repaint();
            return;
        }

        // Handle challenge messages in lobby or while playing single player
        if (msg.startsWith("CHALLENGE:")) {
            if (currentScreen == Screen.LOBBY || (currentScreen == Screen.PLAYING && inSinglePlayer)) {
                try { challengeFromId = Integer.parseInt(msg.substring(10)); } catch (NumberFormatException e) { return; }
                showChallengeDialog = true;
                if (currentScreen == Screen.PLAYING) {
                    paused = true;
                }
                gamePanel.repaint();
            }
            return;
        }
        if (msg.equals("CHALLENGE_REJECTED")) {
            challengePending = false;
            challengeTargetId = -1;
            toastMessage = "对方拒绝了挑战";
            toastEndTime = System.currentTimeMillis() + 2000;
            gamePanel.repaint();
            return;
        }
        if (msg.equals("CHALLENGE_CANCELLED")) {
            showChallengeDialog = false;
            challengeFromId = -1;
            toastMessage = "对方取消了挑战";
            toastEndTime = System.currentTimeMillis() + 2000;
            if (currentScreen == Screen.PLAYING && inSinglePlayer) {
                paused = false;
            }
            gamePanel.repaint();
            return;
        }
        if (msg.startsWith("GAME_START:")) {
            showChallengeDialog = false;
            challengeFromId = -1;
            rematchOffer = false;
            rematchPending = false;
            showRematchDialog = false;
            cancelRematchTimer();
            int opponentId;
            try { opponentId = Integer.parseInt(msg.substring(11)); } catch (NumberFormatException e) { return; }
            if (currentScreen == Screen.PLAYING && inSinglePlayer) {
                // Accepting challenge while in single player
                inSinglePlayer = false;
            }
            startCompetitiveGame(opponentId);
            return;
        }

        if (msg.equals("REMATCH_OFFER")) {
            rematchOffer = true;
            rematchPending = false;
            showRematchDialog = true;
            cancelRematchTimer();
            gamePanel.repaint();
            return;
        }
        if (msg.equals("REMATCH_REJECTED")) {
            rematchPending = false;
            rematchOffer = false;
            showRematchDialog = false;
            cancelRematchTimer();
            if (currentScreen == Screen.RESULT) {
                toastMessage = "对方拒绝了请求";
                toastEndTime = System.currentTimeMillis() + 2000;
                currentScreen = Screen.LOBBY;
                gamePanel.repaint();
            }
            return;
        }
        if (msg.equals("OPPONENT_LEFT")) {
            rematchPending = false;
            rematchOffer = false;
            showRematchDialog = false;
            cancelRematchTimer();
            if (currentScreen == Screen.RESULT && joinMode == 1) {
                toastMessage = "对方已离开";
                toastEndTime = System.currentTimeMillis() + 2000;
                currentScreen = Screen.LOBBY;
                gamePanel.repaint();
            }
            return;
        }
        if (msg.equals("RESTART")) {
            showRematchDialog = false;
            cancelRematchTimer();
            if (joinMode == 1 && !inSinglePlayer) {
                // Server mode competitive: go to lobby
                currentScreen = Screen.LOBBY;
                gamePanel.repaint();
            } else {
                restartGame();
            }
            return;
        }
        if (msg.equals("GAMEOVER")) {
            oppGameOver = true;
            if (joinMode == 1 && !inSinglePlayer) {
                // Server mode: opponent lost, we win
                won = true;
                resultMessage = "你赢了！";
                gameOver = true;
                currentScreen = Screen.RESULT;
                gamePanel.repaint();
                return;
            }
            if (!gameOver) {
                won = true;
                resultMessage = "你赢了！";
            } else {
                resultMessage = "你输了！";
            }
            gameOver = true;
            currentScreen = Screen.RESULT;
            gamePanel.repaint();
        } else if (msg.startsWith("GARBAGE:")) {
            int n;
            try { n = Integer.parseInt(msg.substring(8)); } catch (NumberFormatException e) { return; }
            if (n > 0) addGarbage(n);
        } else if (msg.startsWith("LINES:")) {
            int n;
            try { n = Integer.parseInt(msg.substring(6)); } catch (NumberFormatException e) { return; }
            int garbage = 0;
            if (n == 2) garbage = 1;
            else if (n == 3) garbage = 2;
            else if (n >= 4) garbage = 4;
            if (garbage > 0) addGarbage(garbage);
        } else if (msg.startsWith("BOARD:")) {
            deserializeBoard(msg.substring(6));
        }
    }

    private void handleDisconnect() {
        if (!connected) return;
        connected = false;
        if (currentScreen == Screen.LOBBY || joinMode == 1) {
            resultMessage = "连接断开！";
            currentScreen = Screen.MENU;
            gamePanel.repaint();
            return;
        }
        resultMessage = "连接断开！";
        currentScreen = Screen.RESULT;
        gameOver = true;
        gamePanel.repaint();
    }

    private void addGarbage(int n) {
        if (n <= 0 || gameOver) return;
        garbagePending += n;
        applyGarbage();
    }

    private void startGame() {
        board = new int[ROWS][COLS];
        oppBoard = new int[OPP_ROWS][OPP_COLS];
        score = 0; level = 1; lines = 0;
        holdType = -1; holdUsed = false;
        gameOver = false; paused = false;
        gameStarted = true;
        oppGameOver = false;
        flashRows = false; garbagePending = 0;
        fillBag();
        for (int i = 0; i < QUEUE_SIZE; i++) nextQueue[i] = getNextPiece();
        spawnPiece();
        lastDropTime = System.currentTimeMillis();
        sendBoard();
        gamePanel.requestFocusInWindow();
        gamePanel.repaint();
    }

    private void shutdown() {
        connectingCancelled = true;
        if (socket != null) { try { socket.close(); } catch (IOException ignored) {} }
        if (hostServerSocket != null) { try { hostServerSocket.close(); } catch (IOException ignored) {} }
        if (clearTimer != null) { clearTimer.cancel(); clearTimer = null; }
        if (gameTimer != null) gameTimer.stop();
        if (gamePanel != null && gamePanel.keyRepeatTimer != null) {
            gamePanel.keyRepeatTimer.stop();
            gamePanel.keyRepeatTimer = null;
        }
        cancelRematchTimer();
        dispose();
        System.exit(0);
    }

    private void resetToMenu() {
        connectingCancelled = true;
        if (socket != null) { try { socket.close(); } catch (IOException ignored) {} }
        if (hostServerSocket != null) { try { hostServerSocket.close(); } catch (IOException ignored) {} }
        if (clearTimer != null) { clearTimer.cancel(); clearTimer = null; }
        if (gameTimer != null) gameTimer.stop();
        lobbyIds.clear();
        lobbyStatuses.clear();
        selectedLobbyIdx = -1;
        challengeFromId = -1;
        challengePending = false;
        challengeTargetId = -1;
        showChallengeDialog = false;
        inSinglePlayer = false;
        rematchOffer = false;
        rematchPending = false;
        showRematchDialog = false;
        cancelRematchTimer();
        toastEndTime = 0;
        statusMessage = "";
        statusMessageEndTime = 0;
        hostPortStr = String.valueOf(SERVER_PORT);
        joinPortStr = String.valueOf(SERVER_PORT);
        initGame();
        gamePanel.repaint();
    }

    private void restartGame() {
        board = new int[ROWS][COLS];
        oppBoard = new int[OPP_ROWS][OPP_COLS];
        score = 0; level = 1; lines = 0;
        oppScore = 0; oppLevel = 1; oppLines = 0;
        holdType = -1; holdUsed = false;
        currentType = -1; currentShape = null;
        gameOver = false; gameStarted = true;
        oppGameOver = false; won = false;
        flashRows = false; garbagePending = 0; isLocking = false;
        fillBag();
        for (int i = 0; i < QUEUE_SIZE; i++) nextQueue[i] = getNextPiece();
        spawnPiece();
        lastDropTime = System.currentTimeMillis();
        sendBoard();
        currentScreen = Screen.PLAYING;
        gamePanel.requestFocusInWindow();
        gamePanel.repaint();
    }

    private void startRematchTimer(int seconds) {
        if (rematchTimeoutTimer != null) {
            rematchTimeoutTimer.stop();
            rematchTimeoutTimer = null;
        }
        rematchTimeoutTimer = new javax.swing.Timer(seconds * 1000, e -> {
            rematchPending = false;
            rematchOffer = false;
            showRematchDialog = false;
            if (currentScreen == Screen.RESULT) {
                toastMessage = "等待超时，返回大厅";
                toastEndTime = System.currentTimeMillis() + 2000;
                if (joinMode == 1) sendNetMsg("LOBBY");
                currentScreen = Screen.LOBBY;
                gamePanel.repaint();
            }
        });
        rematchTimeoutTimer.setRepeats(false);
        rematchTimeoutTimer.start();
    }

    private void cancelRematchTimer() {
        if (rematchTimeoutTimer != null) {
            rematchTimeoutTimer.stop();
            rematchTimeoutTimer = null;
        }
    }

    private void startCompetitiveGame(int opponentId) {
        inSinglePlayer = false;
        board = new int[ROWS][COLS];
        oppBoard = new int[OPP_ROWS][OPP_COLS];
        score = 0; level = 1; lines = 0;
        oppScore = 0; oppLevel = 1; oppLines = 0;
        holdType = -1; holdUsed = false;
        currentType = -1; currentShape = null;
        gameOver = false; paused = false;
        gameStarted = true;
        oppGameOver = false;
        flashRows = false; garbagePending = 0; isLocking = false;
        fillBag();
        for (int i = 0; i < QUEUE_SIZE; i++) nextQueue[i] = getNextPiece();
        spawnPiece();
        lastDropTime = System.currentTimeMillis();
        sendBoard();
        currentScreen = Screen.PLAYING;
        gamePanel.requestFocusInWindow();
        gamePanel.repaint();
    }

    private void startSinglePlayer() {
        inSinglePlayer = true;
        sendNetMsg("SINGLEPLAYER");
        board = new int[ROWS][COLS];
        oppBoard = new int[OPP_ROWS][OPP_COLS];
        score = 0; level = 1; lines = 0;
        holdType = -1; holdUsed = false;
        currentType = -1; currentShape = null;
        gameOver = false; paused = false;
        gameStarted = true;
        oppGameOver = false;
        flashRows = false; garbagePending = 0; isLocking = false;
        fillBag();
        for (int i = 0; i < QUEUE_SIZE; i++) nextQueue[i] = getNextPiece();
        spawnPiece();
        lastDropTime = System.currentTimeMillis();
        currentScreen = Screen.PLAYING;
        gamePanel.requestFocusInWindow();
        gamePanel.repaint();
    }

    private void startStandaloneSinglePlayer() {
        inSinglePlayer = true;
        joinMode = 0;
        board = new int[ROWS][COLS];
        oppBoard = new int[OPP_ROWS][OPP_COLS];
        score = 0; level = 1; lines = 0;
        holdType = -1; holdUsed = false;
        currentType = -1; currentShape = null;
        gameOver = false; paused = false;
        gameStarted = true;
        oppGameOver = false;
        flashRows = false; garbagePending = 0; isLocking = false;
        fillBag();
        for (int i = 0; i < QUEUE_SIZE; i++) nextQueue[i] = getNextPiece();
        spawnPiece();
        lastDropTime = System.currentTimeMillis();
        currentScreen = Screen.PLAYING;
        gamePanel.requestFocusInWindow();
        gamePanel.repaint();
    }

    private void startProSinglePlayer() {
        SwingUtilities.invokeLater(() -> {
            try {
                TetrisPro proGame = new TetrisPro(true, false);
                proGame.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            TetrisNetClient game = new TetrisNetClient();
            game.setVisible(true);
        });
    }

    private static String getDefaultJoinIp() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> nifs = java.net.NetworkInterface.getNetworkInterfaces();
            while (nifs.hasMoreElements()) {
                java.net.NetworkInterface nif = nifs.nextElement();
                if (nif.isLoopback() || !nif.isUp()) continue;
                java.util.Enumeration<java.net.InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress addr = addrs.nextElement();
                    if (addr instanceof java.net.Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    // ==================== GAME PANEL ====================
    private class GamePanel extends JPanel {
        private final Font titleFont = new Font("SansSerif", Font.BOLD, 14);
        private final Font scoreFont = new Font("SansSerif", Font.BOLD, 22);
        private final Font statFont = new Font("SansSerif", Font.BOLD, 12);
        private final Font statValFont = new Font("SansSerif", Font.BOLD, 11);
        private final Font statNumFont = new Font("SansSerif", Font.BOLD, 15);
        private final Font menuTitleFont = new Font("SansSerif", Font.BOLD, 36);
        private final Font menuBtnFont = new Font("SansSerif", Font.BOLD, 18);
        private final Font menuSmallFont = new Font("SansSerif", Font.PLAIN, 13);
        private final Font sectionFont = new Font("SansSerif", Font.BOLD, 11);
        private final Font pauseFont = new Font("SansSerif", Font.BOLD, 30);
        private final Font waitFont = new Font("SansSerif", Font.PLAIN, 15);

        private final BasicStroke thinStroke = new BasicStroke(0.5f);
        private final BasicStroke normStroke = new BasicStroke(1f);
        private final BasicStroke thickStroke = new BasicStroke(2f);
        private final BasicStroke dashStroke = new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{5,3}, 0);

        private javax.swing.Timer keyRepeatTimer;
        private int keyRepeatCode;
        private boolean typingIp = false;
        private int ipCursorPos = 0;
        private javax.swing.Timer cursorTimer;
        private boolean cursorVisible = true;
        private boolean ipFocused = true;
        private boolean exitHover = false;
        private boolean lobbyReturnHover = false;
        private int exitBtnY = 0;

        GamePanel() {
            setPreferredSize(new Dimension(TOTAL_WIDTH, TOTAL_HEIGHT));
            setFocusable(true);
            setBackground(BG_DARK);
            setDoubleBuffered(true);

            addFocusListener(new FocusAdapter() {
                public void focusGained(FocusEvent e) { repaint(); }
                public void focusLost(FocusEvent e) { repaint(); }
            });

            addMouseListener(new MouseAdapter() {
                public void mouseReleased(MouseEvent e) {
                    if (currentScreen == Screen.MENU) {
                        int cx = getWidth() / 2;
                        int btnW = 220, btnH = 46;
                        int hostY = 265, joinY = 327, serverY = 389, singleY = 451, proY = 513, exitY = 575;
                        int bx = cx - btnW/2;
                        if (e.getX() >= bx && e.getX() <= bx+btnW &&
                            e.getY() >= hostY && e.getY() <= hostY+btnH) {
                            hostPortStr = String.valueOf(SERVER_PORT);
                            statusMessage = "";
                            currentScreen = Screen.HOST_PORT_INPUT;
                            gamePanel.repaint(); return;
                        }
                        if (e.getX() >= bx && e.getX() <= bx+btnW &&
                            e.getY() >= joinY && e.getY() <= joinY+btnH) {
                            startJoin(); return;
                        }
                        if (e.getX() >= bx && e.getX() <= bx+btnW &&
                            e.getY() >= serverY && e.getY() <= serverY+btnH) {
                            startServerMode(); return;
                        }
                        if (e.getX() >= bx && e.getX() <= bx+btnW &&
                            e.getY() >= singleY && e.getY() <= singleY+btnH) {
                            startStandaloneSinglePlayer(); return;
                        }
                        if (e.getX() >= bx && e.getX() <= bx+btnW &&
                            e.getY() >= proY && e.getY() <= proY+btnH) {
                            startProSinglePlayer(); return;
                        }
                        if (e.getX() >= bx && e.getX() <= bx+btnW &&
                            e.getY() >= exitY && e.getY() <= exitY+btnH) {
                            shutdown(); return;
                        }
                    } else if (currentScreen == Screen.JOIN_INPUT) {
                        int cx = getWidth() / 2;
                        int boxX = cx - 100;
                        int boxW = 200;
                        int boxH = 36;
                        // IP input box click
                        int ipBoxY = 222;
                        if (e.getX() >= boxX && e.getX() <= boxX + boxW &&
                            e.getY() >= ipBoxY && e.getY() <= ipBoxY + boxH) {
                            ipFocused = true;
                            repaint(); return;
                        }
                        // Port input box click
                        int portBoxY = 285;
                        if (e.getX() >= boxX && e.getX() <= boxX + boxW &&
                            e.getY() >= portBoxY && e.getY() <= portBoxY + boxH) {
                            ipFocused = false;
                            repaint(); return;
                        }
                        // Connect button
                        int btnW = 180, btnH = 44;
                        int btnY = 358;
                        int bx = cx - btnW/2;
                        if (e.getX() >= bx && e.getX() <= bx+btnW &&
                            e.getY() >= btnY && e.getY() <= btnY+btnH) {
                            doConnect(); return;
                        }
                        // Exit button
                        int extBtnW = 130, extBtnH = 34;
                        int extBtnY = 418;
                        int extBx = cx - extBtnW/2;
                        if (e.getX() >= extBx && e.getX() <= extBx + extBtnW &&
                            e.getY() >= extBtnY && e.getY() <= extBtnY + extBtnH) {
                            resetToMenu(); return;
                        }
                        } else if (currentScreen == Screen.HOST_PORT_INPUT) {
                            int cx = getWidth() / 2;
                            // Create button
                            int btnW = 180, btnH = 44;
                            int btnY = 335;
                            int bx = cx - btnW/2;
                            if (e.getX() >= bx && e.getX() <= bx+btnW &&
                                e.getY() >= btnY && e.getY() <= btnY+btnH) {
                                doCreateGame(); return;
                            }
                            // Back button
                            int extBtnW = 130, extBtnH = 34;
                            int extBtnY = 420;
                            int extBx = cx - extBtnW/2;
                            if (e.getX() >= extBx && e.getX() <= extBx + extBtnW &&
                                e.getY() >= extBtnY && e.getY() <= extBtnY + extBtnH) {
                                currentScreen = Screen.MENU;
                                statusMessage = "";
                                gamePanel.repaint(); return;
                            }
                        } else if (currentScreen == Screen.HOST_WAIT) {
                            int cx = getWidth() / 2;
                            int cy = getHeight() / 2;
                            java.util.List<String> ips = getLocalIPs();
                            int ipCount = isHost ? ips.size() : 0;
                            int panelH = Math.max(200, 120 + ipCount * 40 + (isHost ? 120 : 70));
                            int btnW = 130, btnH = 34;
                            int btnY = cy + panelH / 2 - 48;
                            int bx = cx - btnW / 2;
                            if (e.getX() >= bx && e.getX() <= bx + btnW &&
                                e.getY() >= btnY && e.getY() <= btnY + btnH) {
                                resetToMenu(); return;
                            }
                        } else if (currentScreen == Screen.LOBBY) {
                            int w = getWidth();
                            int h = getHeight();
                            int cx = w / 2;
                            int panelX = 20;
                            int panelY = 92;
                            int panelW = w - 40;
                        int entryH = 36;
                        int gap = 4;

                        // Challenge dialog buttons
                        if (showChallengeDialog) {
                            int dw = 320, dh = 120;
                            int dx = cx - dw / 2;
                            int abtnW = 90, abtnH = 34;
                            int abtnX = cx - abtnW - 12;
                            int rbtnX = cx + 12;
                            int abtnY = (h / 2 - dh / 2) + 52;
                            // Accept
                            if (e.getX() >= abtnX && e.getX() <= abtnX + abtnW &&
                                e.getY() >= abtnY && e.getY() <= abtnY + abtnH) {
                                sendNetMsg("CHALLENGE_ACCEPT");
                                showChallengeDialog = false;
                                challengeFromId = -1;
                                return;
                            }
                            // Reject
                            if (e.getX() >= rbtnX && e.getX() <= rbtnX + abtnW &&
                                e.getY() >= abtnY && e.getY() <= abtnY + abtnH) {
                                sendNetMsg("CHALLENGE_REJECT");
                                showChallengeDialog = false;
                                challengeFromId = -1;
                                return;
                            }
                            return;
                        }

                        // Bottom buttons
                        int btnYb = h - 72;
                        int bBtnH = 38;
                        // Single player: center=cx-105, w=90
                        int spLeft = (cx - 105) - 90 / 2;
                        if (e.getX() >= spLeft && e.getX() <= spLeft + 90 &&
                            e.getY() >= btnYb && e.getY() <= btnYb + bBtnH) {
                            startSinglePlayer();
                            return;
                        }
                        // Refresh: center=cx, w=90
                        int refLeft = cx - 90 / 2;
                        if (e.getX() >= refLeft && e.getX() <= refLeft + 90 &&
                            e.getY() >= btnYb && e.getY() <= btnYb + bBtnH) {
                            repaint();
                            return;
                        }
                        // Back: center=cx+105, w=90
                        int backLeft = (cx + 105) - 90 / 2;
                        if (e.getX() >= backLeft && e.getX() <= backLeft + 90 &&
                            e.getY() >= btnYb && e.getY() <= btnYb + bBtnH) {
                            resetToMenu();
                            return;
                        }

                        // Player rows
                        int entryY = panelY + 42;
                        for (int i = 0; i < lobbyIds.size(); i++) {
                            if (lobbyIds.get(i) == myPlayerId) {
                                continue;
                            }
                            boolean inRow = e.getX() >= panelX + 8 && e.getX() <= panelX + panelW - 8 &&
                                            e.getY() >= entryY && e.getY() <= entryY + entryH;
                            if (inRow || (e.getX() >= panelX && e.getX() <= panelX + panelW &&
                                          e.getY() >= entryY && e.getY() <= entryY + entryH)) {
                                // Check challenge button
                                int btnX = panelX + panelW - 72;
                                int btnW2 = 56, btnH2 = 26;
                                int btnY2 = entryY + (entryH - btnH2) / 2;
                                String st = lobbyStatuses.get(i);
                                boolean chal = !st.equals("playing") && !st.equals("challenging");
                                if (chal && !challengePending &&
                                    e.getX() >= btnX && e.getX() <= btnX + btnW2 &&
                                    e.getY() >= btnY2 && e.getY() <= btnY2 + btnH2) {
                                    challengePending = true;
                                    challengeTargetId = lobbyIds.get(i);
                                    selectedLobbyIdx = i;
                                    sendNetMsg("CHALLENGE:" + challengeTargetId);
                                    repaint();
                                    return;
                                }
                                selectedLobbyIdx = i;
                                repaint();
                                return;
                            }
                            entryY += entryH + gap;
                        }

                        // Cancel challenge button at fixed position
                        if (challengePending) {
                            int canBtnX = cx + 70;
                            int canBtnY = h - 108;
                            int canBtnW = 70, canBtnH = 26;
                            if (e.getX() >= canBtnX && e.getX() <= canBtnX + canBtnW &&
                                e.getY() >= canBtnY && e.getY() <= canBtnY + canBtnH) {
                                sendNetMsg("CHALLENGE_CANCEL");
                                challengePending = false;
                                challengeTargetId = -1;
                                repaint();
                                return;
                            }
                        }
                    } else if (currentScreen == Screen.RESULT) {
                        int cx = getWidth() / 2;
                        int cy = getHeight() / 2;
                        if (showRematchDialog) {
                            int btnW = 100, btnH = 38;
                            int gap = 20;
                            int btnY = cy + 60;
                            int bx = cx - gap / 2 - btnW / 2;
                            if (e.getX() >= bx && e.getX() <= bx+btnW &&
                                e.getY() >= btnY && e.getY() <= btnY+btnH) {
                                sendNetMsg("REMATCH_ACCEPT");
                                showRematchDialog = false;
                                rematchOffer = false;
                                gamePanel.repaint();
                                return;
                            }
                            bx = cx + btnW / 2 + gap / 2;
                            if (e.getX() >= bx && e.getX() <= bx+btnW &&
                                e.getY() >= btnY && e.getY() <= btnY+btnH) {
                                sendNetMsg("REMATCH_REJECT");
                                showRematchDialog = false;
                                rematchOffer = false;
                                gamePanel.repaint();
                                return;
                            }
                            btnY = cy + 105;
                            btnW = 130; btnH = 34;
                            bx = cx - btnW/2;
                            if (e.getX() >= bx && e.getX() <= bx+btnW &&
                                e.getY() >= btnY && e.getY() <= btnY+btnH) {
                                shutdown();
                                return;
                            }
                            return;
                        }
                        // Play again / rematch
                        int btnW = 200, btnH = 42;
                        int btnY = cy + 60;
                        int bx = cx - btnW/2;
                        if (e.getX() >= bx && e.getX() <= bx+btnW &&
                            e.getY() >= btnY && e.getY() <= btnY+btnH) {
                            if (rematchPending) return;
                            if (joinMode == 1) {
                                sendNetMsg("REMATCH");
                                rematchPending = true;
                                startRematchTimer(30);
                                toastMessage = "已发送请求，等待对方...";
                                toastEndTime = System.currentTimeMillis() + 3000;
                                gamePanel.repaint();
                            } else {
                                sendNetMsg("RESTART");
                                restartGame();
                            }
                            return;
                        }
                        // Back to menu button
                        btnY = cy + 108;
                        btnW = 150; btnH = 38;
                        bx = cx - btnW/2;
                        if (e.getX() >= bx && e.getX() <= bx+btnW &&
                            e.getY() >= btnY && e.getY() <= btnY+btnH) {
                            if (joinMode == 1) sendNetMsg("LOBBY");
                            resetToMenu();
                            return;
                        }
                        // Exit button
                        btnY = cy + 150;
                            btnW = 130; btnH = 36;
                            bx = cx - btnW/2;
                            if (e.getX() >= bx && e.getX() <= bx+btnW &&
                                e.getY() >= btnY && e.getY() <= btnY+btnH) {
                                shutdown();
                                return;
                            }
                        } else if (currentScreen == Screen.PLAYING) {
                        // Challenge dialog during single player
                        if (showChallengeDialog) {
                            int cx2 = getWidth() / 2;
                            int h2 = getHeight();
                            int dw = 320, dh = 120;
                            int abtnW = 90, abtnH = 34;
                            int abtnX = cx2 - abtnW - 12;
                            int rbtnX = cx2 + 12;
                            int abtnY = (h2 / 2 - dh / 2) + 52;
                            if (e.getX() >= abtnX && e.getX() <= abtnX + abtnW &&
                                e.getY() >= abtnY && e.getY() <= abtnY + abtnH) {
                                sendNetMsg("CHALLENGE_ACCEPT");
                                showChallengeDialog = false;
                                challengeFromId = -1;
                                paused = false;
                                return;
                            }
                            if (e.getX() >= rbtnX && e.getX() <= rbtnX + abtnW &&
                                e.getY() >= abtnY && e.getY() <= abtnY + abtnH) {
                                sendNetMsg("CHALLENGE_REJECT");
                                showChallengeDialog = false;
                                challengeFromId = -1;
                                paused = false;
                                return;
                            }
                            return;
                        }
                        int sx = 5 + GAME_WIDTH + 15;
                        int boxW = SIDE_PANEL_WIDTH - 10;
                        int btnW = 85, btnH = 28;
                        int gap = 8;
                        int totalW = btnW * 2 + gap;
                        int leftMargin = (boxW - totalW) / 2;
                        int returnBtnX = sx + leftMargin;
                        int exitBtnX2 = sx + leftMargin + btnW + gap;
                        if (e.getX() >= returnBtnX && e.getX() <= returnBtnX + btnW &&
                            e.getY() >= exitBtnY && e.getY() <= exitBtnY + btnH) {
                            resetToMenu();
                            return;
                        }
                        if (e.getX() >= exitBtnX2 && e.getX() <= exitBtnX2 + btnW &&
                            e.getY() >= exitBtnY && e.getY() <= exitBtnY + btnH) {
                            shutdown();
                        }
                    }
                }
            });

            addMouseMotionListener(new MouseMotionAdapter() {
                public void mouseMoved(MouseEvent e) {
                    if (currentScreen == Screen.MENU) {
                        int cx = getWidth() / 2;
                        int btnW = 220, btnH = 46;
                        int hostY = 265, joinY = 327, serverY = 389, singleY = 451, proY = 513, exitY = 575;
                        int bx = cx - btnW/2;
                        int old = menuHover;
                        menuHover = -1;
                        if (e.getX() >= bx && e.getX() <= bx+btnW &&
                            e.getY() >= hostY && e.getY() <= hostY+btnH)
                            menuHover = 0;
                        if (e.getX() >= bx && e.getX() <= bx+btnW &&
                            e.getY() >= joinY && e.getY() <= joinY+btnH)
                            menuHover = 1;
                        if (e.getX() >= bx && e.getX() <= bx+btnW &&
                            e.getY() >= serverY && e.getY() <= serverY+btnH)
                            menuHover = 2;
                        if (e.getX() >= bx && e.getX() <= bx+btnW &&
                            e.getY() >= singleY && e.getY() <= singleY+btnH)
                            menuHover = 3;
                        if (e.getX() >= bx && e.getX() <= bx+btnW &&
                            e.getY() >= proY && e.getY() <= proY+btnH)
                            menuHover = 4;
                        if (e.getX() >= bx && e.getX() <= bx+btnW &&
                            e.getY() >= exitY && e.getY() <= exitY+btnH)
                            menuHover = 5;
                        if (old != menuHover) repaint();
                    } else if (currentScreen == Screen.HOST_WAIT) {
                        int cx = getWidth() / 2;
                        int cy = getHeight() / 2;
                        java.util.List<String> ips = getLocalIPs();
                        int ipCount = isHost ? ips.size() : 0;
                        int panelH = Math.max(200, 120 + ipCount * 40 + (isHost ? 120 : 70));
                        int btnW = 130, btnH = 34;
                        int btnY = cy + panelH / 2 - 48;
                        int bx = cx - btnW / 2;
                        int old = menuHover;
                        menuHover = -1;
                        if (e.getX() >= bx && e.getX() <= bx + btnW &&
                            e.getY() >= btnY && e.getY() <= btnY + btnH)
                            menuHover = 0;
                        if (old != menuHover) repaint();
                    } else if (currentScreen == Screen.LOBBY) {
                        int w = getWidth();
                        int h = getHeight();
                        int cx = w / 2;
                        int old = lobbyBtnHover;
                        lobbyBtnHover = -1;
                        int panelX = 20;
                        int panelY = 92;
                        int panelW = w - 40;
                        int entryH = 36, gap = 4;

                        // Challenge dialog buttons
                        if (showChallengeDialog) {
                            int dw = 320, dh = 120;
                            int abtnW = 90, abtnH = 34;
                            int abtnX = cx - abtnW - 12;
                            int rbtnX = cx + 12;
                            int abtnY = (h / 2 - dh / 2) + 52;
                            if (e.getX() >= abtnX && e.getX() <= abtnX + abtnW &&
                                e.getY() >= abtnY && e.getY() <= abtnY + abtnH)
                                lobbyBtnHover = 10;
                            else if (e.getX() >= rbtnX && e.getX() <= rbtnX + abtnW &&
                                e.getY() >= abtnY && e.getY() <= abtnY + abtnH)
                                lobbyBtnHover = 11;
                            if (old != lobbyBtnHover) repaint();
                            return;
                        }

                        // Bottom buttons
                        int btnYb = h - 72;
                        int bBtnH = 38;
                        int spLeft = (cx - 105) - 90 / 2;
                        if (e.getX() >= spLeft && e.getX() <= spLeft + 90 &&
                            e.getY() >= btnYb && e.getY() <= btnYb + bBtnH)
                            lobbyBtnHover = 1;
                        int refLeft = cx - 90 / 2;
                        if (e.getX() >= refLeft && e.getX() <= refLeft + 90 &&
                            e.getY() >= btnYb && e.getY() <= btnYb + bBtnH)
                            lobbyBtnHover = 2;
                        int backLeft = (cx + 105) - 90 / 2;
                        if (e.getX() >= backLeft && e.getX() <= backLeft + 90 &&
                            e.getY() >= btnYb && e.getY() <= btnYb + bBtnH)
                            lobbyBtnHover = 3;

                        // Cancel button
                        if (challengePending) {
                            int canBtnX = cx + 70;
                            int canBtnY = h - 108;
                            int canBtnW = 70, canBtnH = 26;
                            if (e.getX() >= canBtnX && e.getX() <= canBtnX + canBtnW &&
                                e.getY() >= canBtnY && e.getY() <= canBtnY + canBtnH)
                                lobbyBtnHover = 5;
                        }

                        // Player rows - check challenge button
                        if (!challengePending) {
                            int entryY = panelY + 42;
                            for (int i = 0; i < lobbyIds.size(); i++) {
                                if (lobbyIds.get(i) == myPlayerId) {
                                    continue;
                                }
                                int btnX = panelX + panelW - 72;
                                int btnW2 = 56, btnH2 = 26;
                                int btnY2 = entryY + (entryH - btnH2) / 2;
                                String st = lobbyStatuses.get(i);
                                boolean chal = !st.equals("playing") && !st.equals("challenging");
                                if (chal && !challengePending &&
                                    e.getX() >= btnX && e.getX() <= btnX + btnW2 &&
                                    e.getY() >= btnY2 && e.getY() <= btnY2 + btnH2) {
                                    lobbyBtnHover = 0;
                                    selectedLobbyIdx = i;
                                    break;
                                }
                                entryY += entryH + gap;
                            }
                        }

                        if (old != lobbyBtnHover) repaint();
                    } else if (currentScreen == Screen.JOIN_INPUT) {
                        int cx = getWidth() / 2;
                        // Connect button
                        int btnW = 180, btnH = 44;
                        int btnY = 358;
                        int bx = cx - btnW/2;
                        // Exit button
                        int extBtnW = 130, extBtnH = 34;
                        int extBtnY = 418;
                        int extBx = cx - extBtnW/2;
                        int old = menuHover;
                        menuHover = -1;
                        if (e.getX() >= bx && e.getX() <= bx+btnW &&
                            e.getY() >= btnY && e.getY() <= btnY+btnH)
                            menuHover = 0;
                        if (e.getX() >= extBx && e.getX() <= extBx+extBtnW &&
                            e.getY() >= extBtnY && e.getY() <= extBtnY+extBtnH)
                            menuHover = 1;
                        if (old != menuHover) repaint();
                    } else if (currentScreen == Screen.HOST_PORT_INPUT) {
                        int cx = getWidth() / 2;
                        int btnW = 180, btnH = 44;
                        int btnY = 335;
                        int extBtnW = 130, extBtnH = 34;
                        int extBtnY = 420;
                        int old = menuHover;
                        menuHover = -1;
                        if (e.getX() >= cx - btnW/2 && e.getX() <= cx - btnW/2 + btnW &&
                            e.getY() >= btnY && e.getY() <= btnY + btnH)
                            menuHover = 0;
                        if (e.getX() >= cx - extBtnW/2 && e.getX() <= cx - extBtnW/2 + extBtnW &&
                            e.getY() >= extBtnY && e.getY() <= extBtnY + extBtnH)
                            menuHover = 1;
                        if (old != menuHover) repaint();
                    }
                    if (currentScreen == Screen.RESULT) {
                        int cx = getWidth() / 2;
                        int cy = getHeight() / 2;
                        int old = menuHover;
                        menuHover = -1;
                        if (showRematchDialog) {
                            int btnW = 100, btnH = 38;
                            int gap = 20;
                            int btnY = cy + 60;
                            int bx = cx - gap / 2 - btnW / 2;
                            if (e.getX() >= bx && e.getX() <= bx+btnW &&
                                e.getY() >= btnY && e.getY() <= btnY+btnH)
                                menuHover = 0;
                            bx = cx + btnW / 2 + gap / 2;
                            if (e.getX() >= bx && e.getX() <= bx+btnW &&
                                e.getY() >= btnY && e.getY() <= btnY+btnH)
                                menuHover = 1;
                            btnY = cy + 105;
                            btnW = 130; btnH = 34;
                            bx = cx - btnW/2;
                            if (e.getX() >= bx && e.getX() <= bx+btnW &&
                                e.getY() >= btnY && e.getY() <= btnY+btnH)
                                menuHover = 2;
                        } else {
                            // Play again button
                            int btnW = 200, btnH = 42;
                            int btnY = cy + 60;
                            int bx = cx - btnW/2;
                            if (e.getX() >= bx && e.getX() <= bx+btnW &&
                                e.getY() >= btnY && e.getY() <= btnY+btnH)
                                menuHover = 0;
                            // Back to menu button
                            btnY = cy + 108;
                            btnW = 150; btnH = 38;
                            bx = cx - btnW/2;
                            if (e.getX() >= bx && e.getX() <= bx+btnW &&
                                e.getY() >= btnY && e.getY() <= btnY+btnH)
                                menuHover = 1;
                            // Exit button
                            btnY = cy + 150;
                            btnW = 130; btnH = 36;
                            bx = cx - btnW/2;
                            if (e.getX() >= bx && e.getX() <= bx+btnW &&
                                e.getY() >= btnY && e.getY() <= btnY+btnH)
                                menuHover = 2;
                        }
                        if (old != menuHover) repaint();
                    }
                    if (currentScreen == Screen.PLAYING) {
                        if (showChallengeDialog) {
                            int oldBh = lobbyBtnHover;
                            lobbyBtnHover = -1;
                            int cx2 = getWidth() / 2;
                            int h2 = getHeight();
                            int dw = 320, dh = 120;
                            int abtnW = 90, abtnH = 34;
                            int abtnX = cx2 - abtnW - 12;
                            int rbtnX = cx2 + 12;
                            int abtnY = (h2 / 2 - dh / 2) + 52;
                            if (e.getX() >= abtnX && e.getX() <= abtnX + abtnW &&
                                e.getY() >= abtnY && e.getY() <= abtnY + abtnH)
                                lobbyBtnHover = 10;
                            else if (e.getX() >= rbtnX && e.getX() <= rbtnX + abtnW &&
                                e.getY() >= abtnY && e.getY() <= abtnY + abtnH)
                                lobbyBtnHover = 11;
                            if (oldBh != lobbyBtnHover) repaint();
                            return;
                        }
                        int sx = 5 + GAME_WIDTH + 15;
                        int boxW = SIDE_PANEL_WIDTH - 10;
                        int btnW = 85, btnH = 28;
                        boolean oldExit = exitHover;
                        boolean oldReturn = lobbyReturnHover;
                        int gap = 8;
                        int totalW = btnW * 2 + gap;
                        int leftMargin = (boxW - totalW) / 2;
                        int returnBtnX = sx + leftMargin;
                        int exitBtnX2 = sx + leftMargin + btnW + gap;
                        lobbyReturnHover = (e.getX() >= returnBtnX && e.getX() <= returnBtnX + btnW &&
                                             e.getY() >= exitBtnY && e.getY() <= exitBtnY + btnH);
                        exitHover = (e.getX() >= exitBtnX2 && e.getX() <= exitBtnX2 + btnW &&
                                      e.getY() >= exitBtnY && e.getY() <= exitBtnY + btnH);
                        if (oldExit != exitHover || oldReturn != lobbyReturnHover) repaint();
                    }
                }
            });

            addKeyListener(new KeyAdapter() {
                public void keyPressed(KeyEvent e) {
                    if (currentScreen == Screen.JOIN_INPUT) {
                        handleJoinInput(e);
                        return;
                    }
                    if (currentScreen == Screen.HOST_PORT_INPUT) {
                        handleHostPortInput(e);
                        return;
                    }
                    if (currentScreen == Screen.MENU) {
                        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                            hostPortStr = String.valueOf(SERVER_PORT);
                            statusMessage = "";
                            currentScreen = Screen.HOST_PORT_INPUT;
                            repaint(); return;
                        } else if (e.getKeyCode() == KeyEvent.VK_J) {
                            startJoin();
                        } else if (e.getKeyCode() == KeyEvent.VK_S) {
                            startServerMode();
                        } else if (e.getKeyCode() == KeyEvent.VK_A) {
                            startStandaloneSinglePlayer();
                        } else if (e.getKeyCode() == KeyEvent.VK_P) {
                            startProSinglePlayer();
                        } else if (e.getKeyCode() == KeyEvent.VK_Q) {
                            shutdown();
                        }
                        return;
                    }
                    if (currentScreen == Screen.LOBBY) {
                        if (e.getKeyCode() == KeyEvent.VK_ESCAPE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                            resetToMenu();
                        } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                            // Challenge selected player or start single player
                            if (showChallengeDialog) {
                                sendNetMsg("CHALLENGE_ACCEPT");
                                showChallengeDialog = false;
                                challengeFromId = -1;
                            } else if (challengePending) {
                                sendNetMsg("CHALLENGE_CANCEL");
                                challengePending = false;
                                challengeTargetId = -1;
                            } else if (selectedLobbyIdx >= 0 && selectedLobbyIdx < lobbyIds.size() &&
                                       lobbyIds.get(selectedLobbyIdx) != myPlayerId) {
                                String st = lobbyStatuses.get(selectedLobbyIdx);
                                if (st.equals("idle") || st.equals("single")) {
                                    challengePending = true;
                                    challengeTargetId = lobbyIds.get(selectedLobbyIdx);
                                    sendNetMsg("CHALLENGE:" + challengeTargetId);
                                }
                            }
                        } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                            // Select previous player
                            int idx = selectedLobbyIdx;
                            do {
                                idx--;
                                if (idx < 0) idx = lobbyIds.size() - 1;
                            } while (idx >= 0 && lobbyIds.get(idx) == myPlayerId);
                            if (idx >= 0) selectedLobbyIdx = idx;
                        } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                            int idx = selectedLobbyIdx;
                            do {
                                idx++;
                                if (idx >= lobbyIds.size()) idx = 0;
                            } while (idx < lobbyIds.size() && lobbyIds.get(idx) == myPlayerId);
                            if (idx < lobbyIds.size()) selectedLobbyIdx = idx;
                        } else if (e.getKeyCode() == KeyEvent.VK_S) {
                            startSinglePlayer();
                        }
                        repaint();
                        return;
                    }
                    if (currentScreen == Screen.RESULT) {
                        if (showRematchDialog) {
                            if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                                sendNetMsg("REMATCH_ACCEPT");
                                showRematchDialog = false;
                                rematchOffer = false;
                                gamePanel.repaint();
                            } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                                sendNetMsg("REMATCH_REJECT");
                                showRematchDialog = false;
                                rematchOffer = false;
                                gamePanel.repaint();
                            }
                        } else {
                            if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                                if (!rematchPending && joinMode == 1) {
                                    sendNetMsg("REMATCH");
                                    rematchPending = true;
                                    startRematchTimer(30);
                                    toastMessage = "已发送请求，等待对方...";
                                    toastEndTime = System.currentTimeMillis() + 3000;
                                    gamePanel.repaint();
                                } else if (joinMode != 1) {
                                    sendNetMsg("RESTART");
                                    restartGame();
                                }
                            } else if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                                if (joinMode == 1) sendNetMsg("LOBBY");
                                resetToMenu();
                            }
                        }
                        return;
                    }
                    if (currentScreen == Screen.HOST_WAIT) {
                        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                            resetToMenu();
                        }
                        return;
                    }
                    if (showChallengeDialog) {
                        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                            sendNetMsg("CHALLENGE_ACCEPT");
                            showChallengeDialog = false;
                            challengeFromId = -1;
                            paused = false;
                            repaint();
                        } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                            sendNetMsg("CHALLENGE_REJECT");
                            showChallengeDialog = false;
                            challengeFromId = -1;
                            paused = false;
                            repaint();
                        }
                        return;
                    }
                    if (!gameStarted) return;
                    if (gameOver) return;
                    if (paused) {
                        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) togglePause();
                        return;
                    }
                    if (flashRows) return;
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_LEFT: moveLeft(); startKeyRepeat(e.getKeyCode()); break;
                        case KeyEvent.VK_RIGHT: moveRight(); startKeyRepeat(e.getKeyCode()); break;
                        case KeyEvent.VK_DOWN: softDrop(); startKeyRepeat(e.getKeyCode()); break;
                        case KeyEvent.VK_UP: rotate(); break;
                        case KeyEvent.VK_SPACE: hardDrop(); break;
                        case KeyEvent.VK_C: case KeyEvent.VK_H: doHold(); break;
                        case KeyEvent.VK_G: showGhost = !showGhost; break;
                        case KeyEvent.VK_ESCAPE: togglePause(); break;
                    }
                }
                public void keyReleased(KeyEvent e) {
                    if (keyRepeatTimer != null) { keyRepeatTimer.stop(); keyRepeatTimer = null; }
                }
            });

            cursorTimer = new javax.swing.Timer(500, e -> { cursorVisible = !cursorVisible; repaint(); });
            cursorTimer.start();

            gameTimer = new javax.swing.Timer(50, ev -> {
                if (gameStarted && !paused && !gameOver && !flashRows && currentShape != null) {
                    long now = System.currentTimeMillis();
                    if (now - lastDropTime >= dropInterval) {
                        if (validPos(currentShape, currentX, currentY + 1)) {
                            currentY++;
                            lastDropTime = now;
                        } else {
                            lockPiece();
                            lastDropTime = now;
                        }
                    }
                }
                repaint();
            });
            gameTimer.start();
        }

        private void startKeyRepeat(int keyCode) {
            this.keyRepeatCode = keyCode;
            if (keyRepeatTimer != null) keyRepeatTimer.stop();
            keyRepeatTimer = new javax.swing.Timer(30, e -> {
                if (gameOver || !gameStarted || flashRows || currentType < 0) return;
                switch (keyRepeatCode) {
                    case KeyEvent.VK_LEFT: moveLeft(); break;
                    case KeyEvent.VK_RIGHT: moveRight(); break;
                    case KeyEvent.VK_DOWN: softDrop(); break;
                }
                repaint();
            });
            keyRepeatTimer.setInitialDelay(170);
            keyRepeatTimer.start();
        }

        private void handleJoinInput(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                doConnect();
                return;
            }
            if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                currentScreen = Screen.MENU;
                repaint();
                return;
            }
            if (e.getKeyCode() == KeyEvent.VK_TAB) {
                ipFocused = !ipFocused;
                repaint();
                return;
            }
            if (ipFocused) {
                if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE && joinIp.length() > 0) {
                    joinIp = joinIp.substring(0, joinIp.length() - 1);
                    repaint();
                    return;
                }
                char c = e.getKeyChar();
                if (Character.isDigit(c) || c == '.') {
                    if (joinIp.length() < 15) {
                        joinIp += c;
                        repaint();
                    }
                }
            } else {
                if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE && joinPortStr.length() > 0) {
                    joinPortStr = joinPortStr.substring(0, joinPortStr.length() - 1);
                    repaint();
                    return;
                }
                char c = e.getKeyChar();
                if (Character.isDigit(c)) {
                    if (joinPortStr.length() < 5) {
                        joinPortStr += c;
                        repaint();
                    }
                }
            }
        }

        private void handleHostPortInput(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                doCreateGame();
                return;
            }
            if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                currentScreen = Screen.MENU;
                statusMessage = "";
                repaint();
                return;
            }
            if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE && hostPortStr.length() > 0) {
                hostPortStr = hostPortStr.substring(0, hostPortStr.length() - 1);
                statusMessage = "";
                repaint();
                return;
            }
            char c = e.getKeyChar();
            if (Character.isDigit(c)) {
                if (hostPortStr.length() < 5) {
                    hostPortStr += c;
                    statusMessage = "";
                    repaint();
                }
            }
        }

        private void doCreateGame() {
            if (hostPortStr.isEmpty()) {
                statusMessage = "请输入端口号";
                statusMessageEndTime = System.currentTimeMillis() + 3000;
                repaint();
                return;
            }
            try {
                hostPort = Integer.parseInt(hostPortStr);
                if (hostPort < 1024 || hostPort > 65535) {
                    statusMessage = "端口号范围: 1024-65535";
                    statusMessageEndTime = System.currentTimeMillis() + 3000;
                    repaint();
                    return;
                }
            } catch (NumberFormatException ex) {
                statusMessage = "无效的端口号";
                statusMessageEndTime = System.currentTimeMillis() + 3000;
                repaint();
                return;
            }

            statusMessage = "正在检测端口...";
            repaint();

            new Thread(() -> {
                try {
                    ServerSocket testSocket = new ServerSocket(hostPort);
                    testSocket.close();
                    SwingUtilities.invokeLater(() -> {
                        statusMessage = "游戏创建成功！端口: " + hostPort;
                        statusMessageEndTime = System.currentTimeMillis() + 3000;
                        startHost();
                    });
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() -> {
                        statusMessage = "端口 " + hostPort + " 已被占用，请选择其他端口";
                        statusMessageEndTime = System.currentTimeMillis() + 5000;
                        repaint();
                    });
                }
            }, "port-check-thread").start();
        }

        private void togglePause() {
            if (gameOver || !gameStarted) return;
            paused = !paused;
            if (!paused) lastDropTime = System.currentTimeMillis();
            repaint();
        }

        private void drawBlock(Graphics2D g2, int x, int y, int type, boolean current) {
            if (type == 8 || type == 9) {
                // Garbage
                g2.setColor(GARBAGE_COLOR);
                g2.fillRoundRect(x+1, y+1, BLOCK_SIZE-2, BLOCK_SIZE-2, 4, 4);
                g2.setColor(new Color(0x66, 0x66, 0x66));
                g2.setStroke(normStroke);
                g2.drawRoundRect(x+1, y+1, BLOCK_SIZE-2, BLOCK_SIZE-2, 4, 4);
                // X pattern
                g2.setColor(new Color(0x44, 0x44, 0x44));
                g2.setStroke(thinStroke);
                g2.drawLine(x+4, y+4, x+BLOCK_SIZE-4, y+BLOCK_SIZE-4);
                g2.drawLine(x+BLOCK_SIZE-4, y+4, x+4, y+BLOCK_SIZE-4);
                return;
            }
            if (type < 0 || type >= COLORS.length) return;
            Color base = COLORS[type];
            Color dk = DARKER[type];
            // Gradient from brighter to darker
            g2.setColor(dk.brighter());
            g2.fillRoundRect(x+1, y+1, BLOCK_SIZE-2, BLOCK_SIZE-2, 5, 5);
            GradientPaint gp = new GradientPaint(x, y, dk.brighter(), x, y+BLOCK_SIZE, base);
            g2.setPaint(gp);
            g2.fillRoundRect(x+1, y+1, BLOCK_SIZE-2, BLOCK_SIZE-2, 5, 5);
            // Highlight
            g2.setColor(new Color(255, 255, 255, 25));
            g2.fillRoundRect(x+3, y+3, BLOCK_SIZE-6, BLOCK_SIZE/2-3, 4, 4);
            // Border
            g2.setColor(new Color(255, 255, 255, 40));
            g2.setStroke(normStroke);
            g2.drawRoundRect(x+1, y+1, BLOCK_SIZE-2, BLOCK_SIZE-2, 5, 5);
        }

        private void drawOppBlock(Graphics2D g2, int x, int y, int type) {
            int bs = OPP_BLOCK;
            if (type == 0) return;
            Color c = (type == 8 || type == 9) ? GARBAGE_COLOR : COLORS[type-1];
            g2.setColor(c);
            g2.fillRoundRect(x+1, y+1, bs-2, bs-2, 2, 2);
            g2.setColor(new Color(255, 255, 255, 20));
            g2.setStroke(thinStroke);
            g2.drawRoundRect(x+1, y+1, bs-2, bs-2, 2, 2);
        }

        private void drawPreview(Graphics2D g2, int x, int y, int type, int bs) {
            if (type < 0 || type >= SHAPES.length) return;
            int[][] shape = SHAPES[type];
            int minR = shape.length, maxR = 0, minC = shape[0].length, maxC = 0;
            for (int r = 0; r < shape.length; r++)
                for (int c = 0; c < shape[r].length; c++)
                    if (shape[r][c] != 0) {
                        minR = Math.min(minR, r); maxR = Math.max(maxR, r);
                        minC = Math.min(minC, c); maxC = Math.max(maxC, c);
                    }
            int pw = (maxC-minC+1) * bs;
            int ph = (maxR-minR+1) * bs;
            int ox = x + (SIDE_PANEL_WIDTH - 40 - pw) / 2 - minC * bs;
            int oy = y + 8 - minR * bs;
            for (int r = minR; r <= maxR; r++) {
                for (int c = minC; c <= maxC; c++) {
                    if (shape[r][c] != 0) {
                        int bx = ox + c * bs, by = oy + r * bs;
                        Color base = COLORS[type];
                        g2.setColor(base);
                        g2.fillRoundRect(bx+1, by+1, bs-2, bs-2, 3, 3);
                        g2.setColor(new Color(255, 255, 255, 30));
                        g2.setStroke(thinStroke);
                        g2.drawRoundRect(bx+1, by+1, bs-2, bs-2, 3, 3);
                    }
                }
            }
        }

        private void drawSection(Graphics2D g2, int x, int y, String title, int w) {
            g2.setColor(new Color(0x00, 0xFF, 0xAA, 15));
            g2.fillRoundRect(x, y, w, 22, 6, 6);
            g2.setColor(new Color(0x00, 0xFF, 0xAA, 60));
            g2.setStroke(normStroke);
            g2.drawRoundRect(x, y, w, 22, 6, 6);
            g2.setColor(new Color(0x00, 0xFF, 0xAA));
            g2.setFont(sectionFont);
            g2.drawString(title, x + 8, y + 15);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // Background
            g2.setColor(BG_DARK);
            g2.fillRect(0, 0, w, h);

            // Focus border
            if (hasFocus()) {
                g2.setColor(FOCUS_COLOR);
                g2.setStroke(thickStroke);
                g2.drawRoundRect(1, 1, w-2, h-2, 4, 4);
            } else {
                g2.setColor(new Color(180, 180, 200));
                g2.setStroke(thickStroke);
                g2.drawRoundRect(1, 1, w-2, h-2, 4, 4);
            }

            if (currentScreen == Screen.MENU) {
                drawMenuScreen(g2);
            } else if (currentScreen == Screen.LOBBY) {
                drawLobbyScreen(g2);
            } else if (currentScreen == Screen.HOST_PORT_INPUT) {
                drawHostPortScreen(g2);
            } else if (currentScreen == Screen.HOST_WAIT) {
                drawWaitScreen(g2);
            } else if (currentScreen == Screen.JOIN_INPUT) {
                drawJoinScreen(g2);
            } else if (currentScreen == Screen.RESULT) {
                drawResultScreen(g2);
            } else {
                // ===== PLAYING SCREEN =====
            // Title bar
            GradientPaint titleGrad = new GradientPaint(5, 5, BORDER_NEON.brighter(), 5, 27, BORDER_NEON);
            g2.setPaint(titleGrad);
            g2.fillRoundRect(5, 5, GAME_WIDTH, 24, 8, 8);
            g2.setColor(WHITE80);
            g2.setStroke(normStroke);
            g2.drawRoundRect(5, 5, GAME_WIDTH, 24, 8, 8);
            g2.setColor(new Color(0x08, 0x08, 0x18));
            g2.setFont(titleFont);
            String title;
            if (joinMode == 1) {
                title = "Tetris Net - 玩家 " + myPlayerId;
            } else {
                title = "Tetris Net - 玩家 " + (myPlayerId + 1);
            }
            FontMetrics ftm = g2.getFontMetrics();
            g2.drawString(title, (GAME_WIDTH - ftm.stringWidth(title)) / 2 + 5, 21);

            // Game area background
            GradientPaint areaGrad = new GradientPaint(5, 30, BG_PANEL, 5, 30+GAME_HEIGHT, BG_DARKER);
            g2.setPaint(areaGrad);
            g2.fillRoundRect(5, 30, GAME_WIDTH, GAME_HEIGHT, 10, 10);
            g2.setColor(WHITE40);
            g2.setStroke(thickStroke);
            g2.drawRoundRect(5, 30, GAME_WIDTH, GAME_HEIGHT, 10, 10);
            g2.setColor(BORDER_NEON);
            g2.setStroke(new BasicStroke(3f));
            g2.drawRoundRect(6, 31, GAME_WIDTH-2, GAME_HEIGHT-2, 9, 9);

            // Grid
            g2.setColor(GRID_LINE);
            g2.setStroke(thinStroke);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));
            for (int r = 0; r <= ROWS; r++)
                g2.drawLine(5, 30 + r*BLOCK_SIZE, 5+GAME_WIDTH, 30 + r*BLOCK_SIZE);
            for (int c = 0; c <= COLS; c++)
                g2.drawLine(5 + c*BLOCK_SIZE, 30, 5 + c*BLOCK_SIZE, 30+GAME_HEIGHT);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

            // Board blocks
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    if (board[r][c] != 0)
                        drawBlock(g2, 5 + c*BLOCK_SIZE, 30 + r*BLOCK_SIZE, board[r][c]-1, false);
                }
            }

            // Flash animation
            if (flashRows) {
                flashCount++;
                if (flashCount > flashMax + 20) flashRows = false;
                else {
                    int alpha = Math.max(0, 220 - flashCount * 12);
                    g2.setColor(new Color(255, 255, 255, alpha));
                    for (int i = 0; i < flashMax && i < flashRowsList.length; i++) {
                        int row = flashRowsList[i];
                        if (row >= 0 && row < ROWS)
                            g2.fillRect(5, 30 + row*BLOCK_SIZE, GAME_WIDTH, BLOCK_SIZE);
                    }
                }
            }

            // Ghost
            if (gameStarted && !gameOver && currentShape != null && showGhost) {
                int ghostY = getGhostY();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));
                g2.setStroke(dashStroke);
                for (int r = 0; r < currentShape.length; r++) {
                    for (int c = 0; c < currentShape[r].length; c++) {
                        if (currentShape[r][c] != 0) {
                            int bx = 5 + (currentX + c) * BLOCK_SIZE;
                            int by = 30 + (ghostY + r) * BLOCK_SIZE;
                            g2.setColor(COLORS[currentType]);
                            g2.drawRoundRect(bx+3, by+3, BLOCK_SIZE-6, BLOCK_SIZE-6, 5, 5);
                        }
                    }
                }
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            }

            // Current piece
            if (gameStarted && !gameOver && currentShape != null) {
                for (int r = 0; r < currentShape.length; r++) {
                    for (int c = 0; c < currentShape[r].length; c++) {
                        if (currentShape[r][c] != 0) {
                            int bx = 5 + (currentX + c) * BLOCK_SIZE;
                            int by = 30 + (currentY + r) * BLOCK_SIZE;
                            drawBlock(g2, bx, by, currentType, true);
                        }
                    }
                }
            }

            // Pause overlay
            if (paused && gameStarted && !showChallengeDialog) {
                g2.setColor(new Color(0, 0, 0, 160));
                g2.fillRoundRect(5, 30, GAME_WIDTH, GAME_HEIGHT, 10, 10);
                g2.setColor(Color.WHITE);
                g2.setFont(pauseFont);
                String pt = "PAUSED";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(pt, (GAME_WIDTH - fm.stringWidth(pt))/2 + 5,
                    30 + (GAME_HEIGHT - fm.getHeight())/2 + fm.getAscent());
            }

            // Challenge dialog overlay (for single player mode)
            if (showChallengeDialog && currentScreen == Screen.PLAYING) {
                int w2 = getWidth();
                int h2 = getHeight();
                int cx2 = w2 / 2;
                g2.setColor(new Color(0, 0, 0, 160));
                g2.fillRect(0, 0, w2, h2);

                int dw = 320, dh = 120;
                int dx = cx2 - dw / 2, dy = h2 / 2 - dh / 2;

                // Dialog shadow
                g2.setColor(new Color(0, 0, 0, 160));
                g2.fillRoundRect(dx + 4, dy + 4, dw, dh, 16, 16);

                // Dialog background with gradient
                GradientPaint dlgGrad = new GradientPaint(dx, dy, new Color(25, 20, 50),
                    dx, dy + dh, new Color(15, 12, 40));
                g2.setPaint(dlgGrad);
                g2.fillRoundRect(dx, dy, dw, dh, 16, 16);

                // Neon border
                g2.setColor(new Color(0, 255, 170, 60));
                g2.setStroke(thickStroke);
                g2.drawRoundRect(dx, dy, dw, dh, 16, 16);

                // Decorative accent line
                g2.setColor(new Color(0, 255, 170, 80));
                g2.setStroke(normStroke);
                g2.drawLine(dx + 30, dy + 36, dx + dw - 30, dy + 36);

                // Challenge text
                g2.setColor(WHITE200);
                g2.setFont(new Font("SansSerif", Font.BOLD, 15));
                String chal = "玩家 " + challengeFromId + " 发起挑战！";
                FontMetrics fm2 = g2.getFontMetrics();
                g2.drawString(chal, cx2 - fm2.stringWidth(chal) / 2, dy + 28);

                // Accept button
                int abtnW = 90, abtnH = 34;
                int abtnX = cx2 - abtnW - 12;
                int abtnY = dy + 52;
                if (lobbyBtnHover == 10) {
                    g2.setColor(new Color(0, 255, 170, 50));
                    g2.setStroke(thickStroke);
                    g2.drawRoundRect(abtnX - 3, abtnY - 3, abtnW + 6, abtnH + 6, 10, 10);
                }
                g2.setColor((lobbyBtnHover == 10) ? new Color(0x00, 0xEE, 0xAA) : new Color(0x00, 0xBB, 0x77));
                g2.fillRoundRect(abtnX, abtnY, abtnW, abtnH, 8, 8);
                g2.setColor(WHITE40);
                g2.setStroke(normStroke);
                g2.drawRoundRect(abtnX, abtnY, abtnW, abtnH, 8, 8);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 14));
                String accT = "接受";
                fm2 = g2.getFontMetrics();
                g2.drawString(accT, abtnX + (abtnW - fm2.stringWidth(accT)) / 2,
                    abtnY + (abtnH + fm2.getAscent()) / 2 - 2);

                // Reject button
                int rbtnX = cx2 + 12;
                if (lobbyBtnHover == 11) {
                    g2.setColor(new Color(255, 80, 80, 50));
                    g2.setStroke(thickStroke);
                    g2.drawRoundRect(rbtnX - 3, abtnY - 3, abtnW + 6, abtnH + 6, 10, 10);
                }
                g2.setColor((lobbyBtnHover == 11) ? new Color(0xDD, 0x55, 0x55) : new Color(0xAA, 0x33, 0x33));
                g2.fillRoundRect(rbtnX, abtnY, abtnW, abtnH, 8, 8);
                g2.setColor(WHITE40);
                g2.setStroke(normStroke);
                g2.drawRoundRect(rbtnX, abtnY, abtnW, abtnH, 8, 8);
                g2.setColor(Color.WHITE);
                String rejT = "拒绝";
                fm2 = g2.getFontMetrics();
                g2.drawString(rejT, rbtnX + (abtnW - fm2.stringWidth(rejT)) / 2,
                    abtnY + (abtnH + fm2.getAscent()) / 2 - 2);
            }

            // ===== STATS PANEL (below game area) =====
            int statsY = 30 + GAME_HEIGHT + 12;
            int statsH = 132;
            GradientPaint statsGrad = new GradientPaint(5, statsY, new Color(20, 25, 50), 5, statsY + statsH, new Color(12, 14, 30));
            g2.setPaint(statsGrad);
            g2.fillRoundRect(5, statsY, GAME_WIDTH, statsH, 10, 10);
            g2.setColor(WHITE50);
            g2.setStroke(normStroke);
            g2.drawRoundRect(5, statsY, GAME_WIDTH, statsH, 10, 10);

            g2.setColor(new Color(0x00, 0xFF, 0xAA, 15));
            g2.fillRoundRect(5, statsY, GAME_WIDTH, 24, 8, 8);
            g2.setColor(new Color(0x00, 0xFF, 0xAA, 60));
            g2.setStroke(normStroke);
            g2.drawRoundRect(5, statsY, GAME_WIDTH, 24, 8, 8);
            g2.setColor(new Color(0x00, 0xFF, 0xAA));
            g2.setFont(menuSmallFont);
            String statsTitle = "游戏统计";
            FontMetrics stm = g2.getFontMetrics();
            g2.drawString(statsTitle, 5 + (GAME_WIDTH - stm.stringWidth(statsTitle)) / 2, statsY + 17);

            int sx = 5 + 10;
            int sy = statsY + 41;
            int gapY = 28;
            int colW = (GAME_WIDTH - 20) / 2;

            // Row 1: Score | Level
            g2.setFont(statFont);
            g2.setColor(LABEL_COLOR);
            g2.drawString("分数", sx, sy);
            g2.setFont(statNumFont);
            g2.setColor(new Color(0x00, 0xCC, 0xFF));
            String sv = String.valueOf(score);
            g2.drawString(sv, sx + colW - 4 - g2.getFontMetrics().stringWidth(sv), sy);

            g2.setFont(statFont);
            g2.setColor(LABEL_COLOR);
            g2.drawString("等级", sx + colW, sy);
            g2.setFont(statNumFont);
            g2.setColor(new Color(0xFF, 0xA0, 0x00));
            sv = String.valueOf(level);
            g2.drawString(sv, sx + colW + colW - 4 - g2.getFontMetrics().stringWidth(sv), sy);
            sy += gapY;

            // Row 2: Lines | Speed
            g2.setFont(statFont);
            g2.setColor(LABEL_COLOR);
            g2.drawString("行数", sx, sy);
            g2.setFont(statNumFont);
            g2.setColor(new Color(0x00, 0xCC, 0x44));
            sv = String.valueOf(lines);
            g2.drawString(sv, sx + colW - 4 - g2.getFontMetrics().stringWidth(sv), sy);

            g2.setFont(statFont);
            g2.setColor(LABEL_COLOR);
            g2.drawString("速度", sx + colW, sy);
            g2.setFont(statNumFont);
            g2.setColor(new Color(0xFF, 0xDC, 0x00));
            sv = dropInterval + "ms";
            g2.drawString(sv, sx + colW + colW - 4 - g2.getFontMetrics().stringWidth(sv), sy);
            sy += gapY;

            // Row 3: Garbage pending | Ghost toggle
            g2.setFont(statFont);
            g2.setColor(LABEL_COLOR);
            g2.drawString("垃圾", sx, sy);
            g2.setFont(statNumFont);
            g2.setColor(new Color(0xFF, 0x30, 0x30));
            sv = String.valueOf(garbagePending);
            g2.drawString(sv, sx + colW - 4 - g2.getFontMetrics().stringWidth(sv), sy);

            g2.setFont(statFont);
            g2.setColor(LABEL_COLOR);
            g2.drawString("幽灵", sx + colW, sy);
            g2.setFont(statNumFont);
            g2.setColor(new Color(0x90, 0x00, 0xFF));
            sv = showGhost ? "开" : "关";
            g2.drawString(sv, sx + colW + colW - 4 - g2.getFontMetrics().stringWidth(sv), sy);

            // ===== SIDE PANEL =====
                drawSidePanel(g2, 5 + GAME_WIDTH + 15, 30);
            }


        }

        private void drawSidePanel(Graphics2D g2, int sx, int sy) {
            int boxW = SIDE_PANEL_WIDTH - 10;
            int y = sy;

            // Panel shadow & bg
            int panelH = GAME_HEIGHT + 160;
            g2.setColor(new Color(0, 0, 0, 40));
            g2.fillRoundRect(sx+3, sy+3, boxW+3, panelH, 8, 8);
            g2.setPaint(new GradientPaint(sx, sy, BG_PANEL, sx+boxW, sy+GAME_HEIGHT+30, BG_DARKER));
            g2.fillRoundRect(sx, sy, boxW, panelH, 8, 8);
            g2.setColor(WHITE50);
            g2.setStroke(normStroke);
            g2.drawRoundRect(sx, sy, boxW, panelH, 8, 8);

            // Opponent status
            g2.setColor(new Color(0x00, 0xFF, 0xAA, 15));
            g2.fillRoundRect(sx+2, y, boxW-4, 22, 6, 6);
            String oppLabel;
            if (inSinglePlayer) {
                oppLabel = "练习模式";
            } else if (joinMode == 1) {
                oppLabel = "对手";
            } else {
                oppLabel = "对手(P" + (1-myPlayerId) + ")";
            }
            g2.setFont(sectionFont);
            g2.setColor(new Color(0x00, 0xFF, 0xAA));
            g2.drawString(oppLabel, sx + (boxW - g2.getFontMetrics().stringWidth(oppLabel)) / 2, y + 16);
            y += 24;

            // Opponent board (smaller)
            int oppX = sx + (boxW - OPP_WIDTH) / 2;
            int oppY = y;

            // Opponent board background
            g2.setColor(new Color(0, 0, 0, 60));
            g2.fillRoundRect(oppX-2, oppY-2, OPP_WIDTH+4, OPP_HEIGHT+4, 6, 6);
            g2.setColor(WHITE30);
            g2.setStroke(thinStroke);
            g2.drawRoundRect(oppX-2, oppY-2, OPP_WIDTH+4, OPP_HEIGHT+4, 6, 6);

            // Opponent blocks
            for (int r = 0; r < OPP_ROWS; r++) {
                for (int c = 0; c < OPP_COLS; c++) {
                    if (oppBoard[r][c] != 0)
                        drawOppBlock(g2, oppX + c*OPP_BLOCK, oppY + r*OPP_BLOCK, oppBoard[r][c]);
                }
            }

            // Opponent "GAME OVER" overlay
            if (oppGameOver) {
                g2.setColor(new Color(0, 0, 0, 140));
                g2.fillRoundRect(oppX-2, oppY-2, OPP_WIDTH+4, OPP_HEIGHT+4, 6, 6);
                g2.setColor(new Color(255, 80, 80));
                g2.setFont(new Font("SansSerif", Font.BOLD, 14));
                String go = "游戏结束";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(go, oppX + (OPP_WIDTH - fm.stringWidth(go))/2,
                    oppY + OPP_HEIGHT/2 + fm.getAscent()/2);
            }

            y += OPP_HEIGHT + 16;

            // Divider
            g2.setColor(WHITE60);
            g2.setStroke(normStroke);
            g2.drawLine(sx+8, y, sx+boxW-8, y);
            y += 10;

            // --- Next pieces ---
            drawSection(g2, sx, y, "下一个", boxW);
            y += 24;
            for (int i = 0; i < 3; i++) {
                if (i < nextQueue.length && nextQueue[i] >= 0) {
                    drawPreview(g2, sx, y + i*(PREVIEW_BLOCK*2+10), nextQueue[i], PREVIEW_BLOCK);
                }
            }
            y += 3 * (PREVIEW_BLOCK*2+10) + 8;

            // Hold
            drawSection(g2, sx, y, "暂存 [C]", boxW);
            y += 24;
            if (holdType >= 0 && !holdUsed) {
                drawPreview(g2, sx, y, holdType, PREVIEW_BLOCK);
            } else if (holdUsed) {
                g2.setColor(HOLD_USED);
                g2.setFont(statFont);
                g2.drawString("已用", sx+10, y+30);
            }
            y += 60;

            // Divider
            g2.setColor(WHITE40);
            g2.setStroke(normStroke);
            g2.drawLine(sx+5, y, sx+boxW-5, y);
            y += 10;

            // Key hints
            drawSection(g2, sx, y, "操作提示", boxW);
            y += 38;
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g2.setColor(new Color(180, 190, 210));
            String hints = "方向:移动  上:旋转  空格:掉落";
            String hints2 = "C:暂存  G:幽灵  ESC:暂停";
            g2.drawString(hints, sx + (boxW - g2.getFontMetrics().stringWidth(hints)) / 2, y);
            y += 12;
            g2.drawString(hints2, sx + (boxW - g2.getFontMetrics().stringWidth(hints2)) / 2, y);
            y += 12;

            // Divider before exit
            g2.setColor(new Color(255, 80, 80, 60));
            g2.setStroke(normStroke);
            g2.drawLine(sx+5, y, sx+boxW-5, y);
            y += 10;

            // Bottom buttons
            int btnW = 85, btnH = 28;
            int remainingSpace = sy + panelH - y;
            exitBtnY = y + (remainingSpace - btnH) / 2;

            if (joinMode == 1) {
                // Server mode: Return to Lobby + Exit Game side by side
                int gap = 8;
                int totalW = btnW * 2 + gap;
                int leftMargin = (boxW - totalW) / 2;
                int returnBtnX = sx + leftMargin;
                int exitBtnX2 = sx + leftMargin + btnW + gap;

                // Return to menu button
                g2.setColor(lobbyReturnHover ? new Color(0x44, 0xAA, 0xFF, 230) : new Color(0x33, 0x88, 0xCC, 180));
                g2.fillRoundRect(returnBtnX, exitBtnY, btnW, btnH, 10, 10);
                if (lobbyReturnHover) {
                    g2.setColor(new Color(0x66, 0xCC, 0xFF, 60));
                    g2.setStroke(new BasicStroke(4f));
                    g2.drawRoundRect(returnBtnX-2, exitBtnY-2, btnW+4, btnH+4, 12, 12);
                }
                g2.setColor(lobbyReturnHover ? WHITE200 : WHITE120);
                g2.setStroke(thickStroke);
                g2.drawRoundRect(returnBtnX, exitBtnY, btnW, btnH, 10, 10);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 13));
                String returnText = "返回";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(returnText, returnBtnX + (btnW - fm.stringWidth(returnText))/2,
                    exitBtnY + (btnH + fm.getAscent())/2 - 2);

                // Exit button
                g2.setColor(exitHover ? new Color(0xDD, 0x55, 0x55, 230) : new Color(0xAA, 0x33, 0x33, 180));
                g2.fillRoundRect(exitBtnX2, exitBtnY, btnW, btnH, 10, 10);
                if (exitHover) {
                    g2.setColor(new Color(0xEE, 0x77, 0x77, 60));
                    g2.setStroke(new BasicStroke(4f));
                    g2.drawRoundRect(exitBtnX2-2, exitBtnY-2, btnW+4, btnH+4, 12, 12);
                }
                g2.setColor(exitHover ? WHITE200 : WHITE120);
                g2.setStroke(thickStroke);
                g2.drawRoundRect(exitBtnX2, exitBtnY, btnW, btnH, 10, 10);
                g2.setColor(Color.WHITE);
                String exitText = "退出游戏";
                FontMetrics fm2 = g2.getFontMetrics();
                g2.drawString(exitText, exitBtnX2 + (btnW - fm2.stringWidth(exitText))/2,
                    exitBtnY + (btnH + fm2.getAscent())/2 - 2);
            } else {
                // P2P/Single mode: Return to Menu + Exit Game side by side
                int gap = 8;
                int totalW = btnW * 2 + gap;
                int leftMargin = (boxW - totalW) / 2;
                int returnBtnX = sx + leftMargin;
                int exitBtnX2 = sx + leftMargin + btnW + gap;

                // Return to menu button
                g2.setColor(lobbyReturnHover ? new Color(0x44, 0xAA, 0xFF, 230) : new Color(0x33, 0x88, 0xCC, 180));
                g2.fillRoundRect(returnBtnX, exitBtnY, btnW, btnH, 10, 10);
                if (lobbyReturnHover) {
                    g2.setColor(new Color(0x66, 0xCC, 0xFF, 60));
                    g2.setStroke(new BasicStroke(4f));
                    g2.drawRoundRect(returnBtnX-2, exitBtnY-2, btnW+4, btnH+4, 12, 12);
                }
                g2.setColor(lobbyReturnHover ? WHITE200 : WHITE120);
                g2.setStroke(thickStroke);
                g2.drawRoundRect(returnBtnX, exitBtnY, btnW, btnH, 10, 10);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 13));
                String returnText = "返回";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(returnText, returnBtnX + (btnW - fm.stringWidth(returnText))/2,
                    exitBtnY + (btnH + fm.getAscent())/2 - 2);

                // Exit button
                g2.setColor(exitHover ? new Color(0xDD, 0x55, 0x55, 230) : new Color(0xAA, 0x33, 0x33, 180));
                g2.fillRoundRect(exitBtnX2, exitBtnY, btnW, btnH, 10, 10);
                if (exitHover) {
                    g2.setColor(new Color(0xEE, 0x77, 0x77, 60));
                    g2.setStroke(new BasicStroke(4f));
                    g2.drawRoundRect(exitBtnX2-2, exitBtnY-2, btnW+4, btnH+4, 12, 12);
                }
                g2.setColor(exitHover ? WHITE200 : WHITE120);
                g2.setStroke(thickStroke);
                g2.drawRoundRect(exitBtnX2, exitBtnY, btnW, btnH, 10, 10);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 13));
                String exitText = "退出游戏";
                FontMetrics fm2 = g2.getFontMetrics();
                g2.drawString(exitText, exitBtnX2 + (btnW - fm2.stringWidth(exitText))/2,
                    exitBtnY + (btnH + fm2.getAscent())/2 - 2);
            }
            y += btnH + 14;
        }

        private void drawLobbyScreen(Graphics2D g2) {
            int w = getWidth();
            int h = getHeight();
            int cx = w / 2;

            // Background with richer gradient
            GradientPaint bgGrad1 = new GradientPaint(0, 0, new Color(8, 6, 34),
                0, h * 0.3f, new Color(20, 14, 58));
            g2.setPaint(bgGrad1);
            g2.fillRect(0, 0, w, (int)(h * 0.3f));
            GradientPaint bgGrad2 = new GradientPaint(0, h * 0.3f, new Color(20, 14, 58),
                0, h, new Color(4, 4, 20));
            g2.setPaint(bgGrad2);
            g2.fillRect(0, (int)(h * 0.3f), w, (int)(h * 0.7f));

            // Subtle grid (finer spacing, lower opacity)
            g2.setColor(new Color(255, 255, 255, 5));
            g2.setStroke(thinStroke);
            for (int x = 0; x < w; x += 35) g2.drawLine(x, 0, x, h);
            for (int y = 0; y < h; y += 35) g2.drawLine(0, y, w, y);

            // Decorative blocks
            drawDecoBlock(g2, 20, 140, 1, 0.07f);
            drawDecoBlock(g2, w - 85, h - 170, 4, 0.08f);
            drawDecoBlock(g2, 45, h - 110, 3, 0.05f);
            drawDecoBlock(g2, w - 105, 105, 5, 0.06f);

            // Title glow
            g2.setColor(new Color(0, 200, 255, 15));
            g2.fillOval(cx - 150, 14, 300, 26);

            // Title
            g2.setColor(BORDER_NEON);
            g2.setFont(menuTitleFont);
            String title = "服务器大厅";
            FontMetrics ftm = g2.getFontMetrics();
            g2.drawString(title, cx - ftm.stringWidth(title) / 2, 50);

            // Title gradient underline
            int titleW = ftm.stringWidth(title);
            int tlX = cx - titleW / 2;
            g2.setPaint(new LinearGradientPaint(tlX, 0, tlX + titleW, 0,
                new float[]{0f, 0.5f, 1f},
                new Color[]{new Color(0, 255, 170, 0), new Color(0, 255, 170, 120), new Color(0, 255, 170, 0)}));
            g2.setStroke(thickStroke);
            g2.drawLine(tlX, 58, tlX + titleW, 58);

            // Subtitle with player count
            g2.setFont(menuSmallFont);
            g2.setColor(WHITE200);
            String sub = "在线玩家 (" + lobbyIds.size() + ")";
            ftm = g2.getFontMetrics();
            g2.drawString(sub, cx - ftm.stringWidth(sub) / 2, 78);

            // Player list panel
            int panelX = 20;
            int panelY = 92;
            int panelW = w - 40;
            int panelH = h - panelY - 108;
            int arc = 16;

            // Panel shadow
            g2.setColor(new Color(0, 0, 0, 120));
            g2.fillRoundRect(panelX + 4, panelY + 4, panelW, panelH, arc, arc);

            // Panel background
            g2.setColor(new Color(0, 0, 0, 85));
            g2.fillRoundRect(panelX, panelY, panelW, panelH, arc, arc);

            // Panel border
            g2.setColor(new Color(255, 255, 255, 15));
            g2.setStroke(normStroke);
            g2.drawRoundRect(panelX, panelY, panelW, panelH, arc, arc);

            // Column headers
            g2.setColor(WHITE200);
            g2.setFont(new Font("SansSerif", Font.BOLD, 12));
            FontMetrics fmCol = g2.getFontMetrics();
            g2.drawString("玩家", panelX + 22, panelY + 24);
            g2.drawString("状态", panelX + panelW - 125, panelY + 24);
            String opText = "操作";
            g2.drawString(opText, panelX + panelW - 44 - fmCol.stringWidth(opText) / 2, panelY + 24);

            // Header divider
            g2.setColor(WHITE40);
            g2.drawLine(panelX + 14, panelY + 30, panelX + panelW - 14, panelY + 30);

            // Player entries
            int entryY = panelY + 42;
            int entryH = 36;

            if (lobbyIds.isEmpty()) {
                g2.setColor(WHITE120);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 15));
                String empty = "暂无其他玩家在线";
                ftm = g2.getFontMetrics();
                g2.drawString(empty, cx - ftm.stringWidth(empty) / 2, entryY + 50);
            }

            for (int i = 0; i < lobbyIds.size(); i++) {
                int pid = lobbyIds.get(i);
                String status = lobbyStatuses.get(i);

                if (pid == myPlayerId) continue;

                boolean isSelected = (i == selectedLobbyIdx);

                // Player card background
                if (isSelected) {
                    g2.setColor(new Color(0, 255, 170, 25));
                    g2.fillRoundRect(panelX + 8, entryY, panelW - 16, entryH, 10, 10);
                    g2.setColor(new Color(0, 255, 170, 70));
                    g2.setStroke(thickStroke);
                    g2.drawRoundRect(panelX + 8, entryY, panelW - 16, entryH, 10, 10);
                } else if ((entryY / 80) % 2 == 0) {
                    g2.setColor(new Color(255, 255, 255, 3));
                    g2.fillRoundRect(panelX + 8, entryY, panelW - 16, entryH, 10, 10);
                }

                // Player avatar circle
                int avatarX = panelX + 20;
                int avatarY = entryY + entryH / 2;
                g2.setColor(new Color(60, 140, 220));
                g2.fillOval(avatarX - 12, avatarY - 12, 24, 24);
                g2.setColor(WHITE40);
                g2.setStroke(thinStroke);
                g2.drawOval(avatarX - 12, avatarY - 12, 24, 24);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 11));
                String initial = String.valueOf(pid);
                ftm = g2.getFontMetrics();
                g2.drawString(initial, avatarX - ftm.stringWidth(initial) / 2,
                    avatarY + ftm.getAscent() / 2 - 1);

                // Player name
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 14));
                String name = "玩家 " + pid;
                g2.drawString(name, avatarX + 22, entryY + 23);

                // Status
                String statusText;
                Color statusColor;
                boolean challengeable = false;
                switch (status) {
                    case "playing":
                        statusText = "对战中";
                        statusColor = new Color(255, 80, 80);
                        break;
                    case "challenging":
                        statusText = "请求中";
                        statusColor = new Color(255, 190, 60);
                        break;
                    case "single":
                        statusText = "练习中";
                        statusColor = new Color(60, 180, 255);
                        challengeable = true;
                        break;
                    default:
                        statusText = "空闲";
                        statusColor = new Color(60, 255, 120);
                        challengeable = true;
                        break;
                }

                // Status dot indicator
                int dotX = panelX + panelW - 138;
                int dotY = entryY + entryH / 2;
                g2.setColor(statusColor);
                g2.fillOval(dotX - 4, dotY - 4, 8, 8);
                g2.setColor(new Color(255, 255, 255, 40));
                g2.setStroke(thinStroke);
                g2.drawOval(dotX - 4, dotY - 4, 8, 8);

                // Status text
                g2.setColor(statusColor);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
                g2.drawString(statusText, panelX + panelW - 125, entryY + 22);

                // Challenge button (only on selected row, if challengeable)
                if (isSelected && challengeable && !challengePending) {
                    int btnX = panelX + panelW - 72;
                    int btnW = 56;
                    int btnH2 = 26;
                    int btnY2 = entryY + (entryH - btnH2) / 2;
                    boolean hover = (lobbyBtnHover == 0);

                    if (hover) {
                        g2.setColor(new Color(155, 89, 182, 30));
                        g2.fillRoundRect(btnX - 2, btnY2 - 2, btnW + 4, btnH2 + 4, 9, 9);
                    }

                    g2.setColor(hover ? new Color(0xBE, 0x7E, 0xDD) : BTN_LOBBY_CHALLENGE);
                    g2.fillRoundRect(btnX, btnY2, btnW, btnH2, 8, 8);
                    g2.setColor(hover ? WHITE80 : WHITE40);
                    g2.setStroke(normStroke);
                    g2.drawRoundRect(btnX, btnY2, btnW, btnH2, 8, 8);
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("SansSerif", Font.BOLD, 12));
                    String btnT = "对战";
                    ftm = g2.getFontMetrics();
                    g2.drawString(btnT, btnX + (btnW - ftm.stringWidth(btnT)) / 2,
                        btnY2 + (btnH2 + ftm.getAscent()) / 2 - 2);
                }

                entryY += entryH + 4;
            }

            // Challenge pending indicator with pulse
            if (challengePending) {
                long pulse = System.currentTimeMillis() % 1200;
                float pulseAlpha = 0.5f + 0.3f * (float)Math.sin(pulse * Math.PI * 2 / 1200);

                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pulseAlpha));
                g2.setColor(new Color(255, 200, 80));
                g2.fillOval(cx - 80, panelY + panelH - 68, 8, 8);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));

                g2.setColor(new Color(255, 200, 80));
                g2.setFont(new Font("SansSerif", Font.BOLD, 14));
                String wait = "等待对方回应...";
                ftm = g2.getFontMetrics();
                g2.drawString(wait, cx - 64, panelY + panelH - 60);

                // Cancel button
                int canBtnX = cx + 70;
                int canBtnY = h - 108;
                int canBtnW = 70, canBtnH = 26;
                boolean canHover = (lobbyBtnHover == 5);
                g2.setColor(canHover ? new Color(0xF7, 0xB8, 0x4A) : BTN_LOBBY_CANCEL);
                g2.fillRoundRect(canBtnX, canBtnY, canBtnW, canBtnH, 8, 8);
                g2.setColor(canHover ? WHITE80 : WHITE40);
                g2.setStroke(normStroke);
                g2.drawRoundRect(canBtnX, canBtnY, canBtnW, canBtnH, 8, 8);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 12));
                String cc = "取消";
                ftm = g2.getFontMetrics();
                g2.drawString(cc, canBtnX + (canBtnW - ftm.stringWidth(cc)) / 2,
                    canBtnY + (canBtnH + ftm.getAscent()) / 2 - 2);
            }

            // Bottom buttons
            int btnYb = h - 82;
            int bBtnH = 38, bBtnR = 12;
            drawMenuBtnSmall(g2, cx - 105, btnYb, 90, bBtnH, bBtnR, "单人练习", lobbyBtnHover == 1, BTN_LOBBY_PRACTICE);
            drawMenuBtnSmall(g2, cx, btnYb, 90, bBtnH, bBtnR, "刷新", lobbyBtnHover == 2, BTN_LOBBY_REFRESH);
            drawMenuBtnSmall(g2, cx + 105, btnYb, 90, bBtnH, bBtnR, "返回", lobbyBtnHover == 3, BTN_LOBBY_BACK);

            // Toast
            if (System.currentTimeMillis() < toastEndTime) {
                g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
                g2.setColor(WHITE200);
                ftm = g2.getFontMetrics();
                g2.drawString(toastMessage, cx - ftm.stringWidth(toastMessage) / 2, h - 8);
            }

            // Challenge dialog overlay
            if (showChallengeDialog) {
                g2.setColor(new Color(0, 0, 0, 160));
                g2.fillRect(0, 0, w, h);

                int dw = 320, dh = 120;
                int dx = cx - dw / 2, dy = h / 2 - dh / 2;

                // Dialog shadow
                g2.setColor(new Color(0, 0, 0, 160));
                g2.fillRoundRect(dx + 4, dy + 4, dw, dh, 16, 16);

                // Dialog background with gradient
                GradientPaint dlgGrad = new GradientPaint(dx, dy, new Color(25, 20, 50),
                    dx, dy + dh, new Color(15, 12, 40));
                g2.setPaint(dlgGrad);
                g2.fillRoundRect(dx, dy, dw, dh, 16, 16);

                // Neon border
                g2.setColor(new Color(0, 255, 170, 60));
                g2.setStroke(thickStroke);
                g2.drawRoundRect(dx, dy, dw, dh, 16, 16);

                // Decorative accent line
                g2.setColor(new Color(0, 255, 170, 80));
                g2.setStroke(normStroke);
                g2.drawLine(dx + 30, dy + 36, dx + dw - 30, dy + 36);

                // Challenge text
                g2.setColor(WHITE200);
                g2.setFont(new Font("SansSerif", Font.BOLD, 15));
                String chal = "玩家 " + challengeFromId + " 发起挑战！";
                ftm = g2.getFontMetrics();
                g2.drawString(chal, cx - ftm.stringWidth(chal) / 2, dy + 28);

                // Accept button
                int abtnW = 90, abtnH = 34;
                int abtnX = cx - abtnW - 12;
                int abtnY = dy + 52;
                if (lobbyBtnHover == 10) {
                    g2.setColor(new Color(26, 188, 156, 50));
                    g2.setStroke(thickStroke);
                    g2.drawRoundRect(abtnX - 3, abtnY - 3, abtnW + 6, abtnH + 6, 10, 10);
                }
                g2.setColor((lobbyBtnHover == 10) ? new Color(0x48, 0xD1, 0xB8) : BTN_LOBBY_ACCEPT);
                g2.fillRoundRect(abtnX, abtnY, abtnW, abtnH, 8, 8);
                g2.setColor(WHITE40);
                g2.setStroke(normStroke);
                g2.drawRoundRect(abtnX, abtnY, abtnW, abtnH, 8, 8);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 14));
                String accT = "接受";
                ftm = g2.getFontMetrics();
                g2.drawString(accT, abtnX + (abtnW - ftm.stringWidth(accT)) / 2,
                    abtnY + (abtnH + ftm.getAscent()) / 2 - 2);

                // Reject button
                int rbtnX = cx + 12;
                if (lobbyBtnHover == 11) {
                    g2.setColor(new Color(231, 76, 60, 50));
                    g2.setStroke(thickStroke);
                    g2.drawRoundRect(rbtnX - 3, abtnY - 3, abtnW + 6, abtnH + 6, 10, 10);
                }
                g2.setColor((lobbyBtnHover == 11) ? new Color(0xF0, 0x6B, 0x5E) : BTN_LOBBY_REJECT);
                g2.fillRoundRect(rbtnX, abtnY, abtnW, abtnH, 8, 8);
                g2.setColor(WHITE40);
                g2.setStroke(normStroke);
                g2.drawRoundRect(rbtnX, abtnY, abtnW, abtnH, 8, 8);
                g2.setColor(Color.WHITE);
                String rejT = "拒绝";
                ftm = g2.getFontMetrics();
                g2.drawString(rejT, rbtnX + (abtnW - ftm.stringWidth(rejT)) / 2,
                    abtnY + (abtnH + ftm.getAscent()) / 2 - 2);
            }

            // Hint
            g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
            g2.setColor(WHITE200);
            String hint = "点击玩家选中，点击 [对战] 按钮发起对战";
            if (challengePending) {
                hint = "等待对方回应，点击 [取消] 取消请求";
            }
            ftm = g2.getFontMetrics();
            g2.drawString(hint, cx - ftm.stringWidth(hint) / 2, h - 22);
        }

        private void drawMenuScreen(Graphics2D g2) {
            int w = getWidth();
            int h = getHeight();
            int cx = w / 2;

            // Background: deep gradient
            GradientPaint bgGrad1 = new GradientPaint(0, 0, new Color(10, 8, 36),
                0, h * 0.4f, new Color(20, 14, 56));
            g2.setPaint(bgGrad1);
            g2.fillRect(0, 0, w, (int)(h * 0.4f));
            GradientPaint bgGrad2 = new GradientPaint(0, h * 0.4f, new Color(20, 14, 56),
                0, h, new Color(6, 6, 24));
            g2.setPaint(bgGrad2);
            g2.fillRect(0, (int)(h * 0.4f), w, (int)(h * 0.6f));

            // Subtle grid
            g2.setColor(new Color(255, 255, 255, 6));
            g2.setStroke(thinStroke);
            for (int x = 0; x < w; x += 30) g2.drawLine(x, 0, x, h);
            for (int y = 0; y < h; y += 30) g2.drawLine(0, y, w, y);

            // Decorative blocks
            drawDecoBlock(g2, 25, 140, 0, 0.12f);
            drawDecoBlock(g2, w - 90, 180, 1, 0.10f);
            drawDecoBlock(g2, 40, h - 140, 2, 0.08f);
            drawDecoBlock(g2, w - 75, h - 180, 3, 0.10f);
            drawDecoBlock(g2, w / 2 - 30, h - 80, 4, 0.06f);
            drawDecoBlock(g2, w - 100, 300, 5, 0.10f);
            drawDecoBlock(g2, 50, 320, 6, 0.08f);

            // Title glow
            g2.setColor(new Color(0, 200, 255, 20));
            g2.fillOval(cx - 200, 28, 400, 30);

            // Title shadow
            g2.setColor(new Color(0, 0, 0, 80));
            g2.setFont(menuTitleFont);
            String title = "TETRIS NET";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(title, cx - fm.stringWidth(title) / 2 + 2, 97);

            // Title
            g2.setColor(new Color(0, 255, 170));
            g2.drawString(title, cx - fm.stringWidth(title) / 2, 95);

            // Subtitle
            g2.setFont(menuSmallFont);
            g2.setColor(WHITE200);
            String sub = "网络对战";
            fm = g2.getFontMetrics();
            g2.drawString(sub, cx - fm.stringWidth(sub) / 2, 120);

            // Glass panel
            int panelY = 145;
            int panelH = 550;
            g2.setColor(new Color(0, 0, 0, 100));
            g2.fillRoundRect(cx - 170, panelY, 340, panelH, 18, 18);
            g2.setColor(new Color(255, 255, 255, 12));
            g2.setStroke(normStroke);
            g2.drawRoundRect(cx - 170, panelY, 340, panelH, 18, 18);

            // Info text
            g2.setFont(new Font("SansSerif", Font.BOLD, 14));
            g2.setColor(WHITE200);
            String info = "通过网络与其他玩家对战";
            fm = g2.getFontMetrics();
            g2.drawString(info, cx - fm.stringWidth(info) / 2, panelY + 35);
            String info2 = "先碰顶者输！";
            fm = g2.getFontMetrics();
            g2.drawString(info2, cx - fm.stringWidth(info2) / 2, panelY + 54);

            // Garbage rules
            g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
            g2.setColor(WHITE200);
            String r1 = "消除2行→1垃圾行    消除3行→2垃圾行";
            String r2 = "消除4行(Tetris)→4垃圾行！";
            fm = g2.getFontMetrics();
            g2.drawString(r1, cx - fm.stringWidth(r1) / 2, panelY + 80);
            fm = g2.getFontMetrics();
            g2.drawString(r2, cx - fm.stringWidth(r2) / 2, panelY + 96);

            // Host button
            int btnY = panelY + 120;
            drawMenuButton(g2, cx, btnY, 220, 46, "创建游戏 [ENTER]", menuHover == 0, BTN_CREATE);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g2.setColor(WHITE80);
            String hint1 = "创建本地游戏(P2P)";
            fm = g2.getFontMetrics();
            g2.drawString(hint1, cx - fm.stringWidth(hint1) / 2, btnY + 60);

            // Join button
            btnY = panelY + 182;
            drawMenuButton(g2, cx, btnY, 220, 46, "加入游戏 [J]", menuHover == 1, BTN_JOIN);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g2.setColor(WHITE80);
            String hint2 = "加入他人的游戏";
            fm = g2.getFontMetrics();
            g2.drawString(hint2, cx - fm.stringWidth(hint2) / 2, btnY + 60);

            // Server button
            btnY = panelY + 244;
            drawMenuButton(g2, cx, btnY, 220, 46, "服务器模式 [S]", menuHover == 2, BTN_SERVER);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g2.setColor(WHITE80);
            String hint3 = "连接到专用服务器";
            fm = g2.getFontMetrics();
            g2.drawString(hint3, cx - fm.stringWidth(hint3) / 2, btnY + 60);

            // Single player button
            btnY = panelY + 306;
            drawMenuButton(g2, cx, btnY, 220, 46, "经典单机 [A]", menuHover == 3, BTN_PRACTICE);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g2.setColor(WHITE80);
            String hint4 = "离线单人练习";
            fm = g2.getFontMetrics();
            g2.drawString(hint4, cx - fm.stringWidth(hint4) / 2, btnY + 60);

            // Pro single player button
            btnY = panelY + 368;
            drawMenuButton(g2, cx, btnY, 220, 46, "专业版单机 [P]", menuHover == 4, BTN_PRO);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g2.setColor(WHITE80);
            String hintPro = "全新体验·专业级玩法";
            fm = g2.getFontMetrics();
            g2.drawString(hintPro, cx - fm.stringWidth(hintPro) / 2, btnY + 60);

            // Exit button
            btnY = panelY + 430;
            drawMenuButton(g2, cx, btnY, 220, 46, "退出游戏 [Q]", menuHover == 5, BTN_EXIT);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g2.setColor(WHITE80);
            String hint5 = "按Q或点击退出";
            fm = g2.getFontMetrics();
            g2.drawString(hint5, cx - fm.stringWidth(hint5) / 2, btnY + 60);
        }

        private void drawDecoBlock(Graphics2D g2, int x, int y, int type, float alpha) {
            int[][] shape = SHAPES[type];
            int bs = 18;
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            Color c = COLORS[type];
            for (int r = 0; r < shape.length; r++) {
                for (int c2 = 0; c2 < shape[r].length; c2++) {
                    if (shape[r][c2] != 0) {
                        int bx = x + c2 * bs;
                        int by = y + r * bs;
                        g2.setColor(c);
                        g2.fillRoundRect(bx, by, bs - 1, bs - 1, 3, 3);
                        g2.setColor(new Color(255, 255, 255, 30));
                        g2.setStroke(thinStroke);
                        g2.drawRoundRect(bx, by, bs - 1, bs - 1, 3, 3);
                    }
                }
            }
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        }

        private void drawMenuButton(Graphics2D g2, int cx, int y, int w, int h, String text, boolean hover) {
            drawMenuButton(g2, cx, y, w, h, text, hover, Color.WHITE);
        }

        private void drawMenuButton(Graphics2D g2, int cx, int y, int w, int h, String text, boolean hover, Color fgColor) {
            int x = cx - w/2;
            Color base;
            if (fgColor == BTN_EXIT)
                base = hover ? new Color(0xDD, 0x55, 0x55) : new Color(0xAA, 0x33, 0x33);
            else if (fgColor == BTN_ACCEPT)
                base = hover ? new Color(0x00, 0xEE, 0xAA) : new Color(0x00, 0xBB, 0x77);
            else if (fgColor == BTN_PRACTICE)
                base = hover ? new Color(0x55, 0xCC, 0xBB) : new Color(0x33, 0xAA, 0x99);
            else if (fgColor == BTN_BACK)
                base = hover ? new Color(0xAA, 0xCC, 0x66) : new Color(0x88, 0xAA, 0x44);
            else if (fgColor == BTN_LOBBY_PRACTICE)
                base = hover ? new Color(0x6A, 0x8B, 0xFF) : new Color(0x4A, 0x6B, 0xF5);
            else if (fgColor == BTN_LOBBY_REFRESH)
                base = hover ? new Color(0xFF, 0xAA, 0x66) : new Color(0xFF, 0x8C, 0x42);
            else if (fgColor == BTN_LOBBY_BACK)
                base = hover ? new Color(0xF0, 0x78, 0x8A) : new Color(0xE8, 0x56, 0x6B);
            else if (fgColor == BTN_CREATE)
                base = hover ? new Color(0x00, 0xF0, 0xF0) : new Color(0x00, 0xC0, 0xC0);
            else if (fgColor == BTN_JOIN)
                base = hover ? new Color(0xF0, 0xF0, 0x00) : new Color(0xC0, 0xC0, 0x00);
            else if (fgColor == BTN_SERVER)
                base = hover ? new Color(0xA0, 0x40, 0xF0) : new Color(0x80, 0x20, 0xC0);
            else if (fgColor == BTN_PRO)
                base = hover ? new Color(0xFF, 0xCC, 0x44) : new Color(0xE0, 0x90, 0x20);
            else
                base = hover ? new Color(0x00, 0xEE, 0xAA) : new Color(0x00, 0xCC, 0x88);
            GradientPaint gp = new GradientPaint(x, y, base.brighter(), x, y+h, base);
            g2.setPaint(gp);
            g2.fillRoundRect(x, y, w, h, 24, 24);
            g2.setColor(hover ? WHITE80 : WHITE40);
            g2.setStroke(thickStroke);
            g2.drawRoundRect(x, y, w, h, 24, 24);
            g2.setColor(Color.WHITE);
            g2.setFont(menuBtnFont);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, cx - fm.stringWidth(text)/2, y + (h + fm.getAscent())/2 - 2);
        }

        private void drawMenuBtnSmall(Graphics2D g2, int cx, int y, int w, int h, int r, String text, boolean hover) {
            drawMenuBtnSmall(g2, cx, y, w, h, r, text, hover, Color.WHITE);
        }

        private void drawMenuBtnSmall(Graphics2D g2, int cx, int y, int w, int h, int r, String text, boolean hover, Color fgColor) {
            int x = cx - w/2;
            Color base;
            if (fgColor == BTN_EXIT)
                base = hover ? new Color(0xDD, 0x55, 0x55) : new Color(0xAA, 0x33, 0x33);
            else if (fgColor == BTN_ACCEPT)
                base = hover ? new Color(0x00, 0xEE, 0xAA) : new Color(0x00, 0xBB, 0x77);
            else if (fgColor == BTN_PRACTICE)
                base = hover ? new Color(0x55, 0xCC, 0xBB) : new Color(0x33, 0xAA, 0x99);
            else if (fgColor == BTN_BACK)
                base = hover ? new Color(0xAA, 0xCC, 0x66) : new Color(0x88, 0xAA, 0x44);
            else if (fgColor == BTN_LOBBY_PRACTICE)
                base = hover ? new Color(0x6A, 0x8B, 0xFF) : new Color(0x4A, 0x6B, 0xF5);
            else if (fgColor == BTN_LOBBY_REFRESH)
                base = hover ? new Color(0xFF, 0xAA, 0x66) : new Color(0xFF, 0x8C, 0x42);
            else if (fgColor == BTN_LOBBY_BACK)
                base = hover ? new Color(0xF0, 0x78, 0x8A) : new Color(0xE8, 0x56, 0x6B);
            else if (fgColor == BTN_CREATE)
                base = hover ? new Color(0x00, 0xF0, 0xF0) : new Color(0x00, 0xC0, 0xC0);
            else if (fgColor == BTN_JOIN)
                base = hover ? new Color(0xF0, 0xF0, 0x00) : new Color(0xC0, 0xC0, 0x00);
            else if (fgColor == BTN_SERVER)
                base = hover ? new Color(0xA0, 0x40, 0xF0) : new Color(0x80, 0x20, 0xC0);
            else if (fgColor == BTN_PRO)
                base = hover ? new Color(0xFF, 0xCC, 0x44) : new Color(0xE0, 0x90, 0x20);
            else
                base = hover ? new Color(0x00, 0xEE, 0xAA) : new Color(0x00, 0xCC, 0x88);
            GradientPaint gp = new GradientPaint(x, y, base.brighter(), x, y+h, base);
            g2.setPaint(gp);
            g2.fillRoundRect(x, y, w, h, r, r);
            g2.setColor(hover ? WHITE80 : WHITE40);
            g2.setStroke(normStroke);
            g2.drawRoundRect(x, y, w, h, r, r);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, 13));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, cx - fm.stringWidth(text)/2, y + (h + fm.getAscent())/2 - 2);
        }

        private void drawWaitScreen(Graphics2D g2) {
            int w = getWidth();
            int h = getHeight();
            int cx = w / 2;
            int cy = h / 2;

            // Background
            GradientPaint bgGrad1 = new GradientPaint(0, 0, new Color(10, 8, 36),
                0, h, new Color(22, 16, 60));
            g2.setPaint(bgGrad1);
            g2.fillRect(0, 0, w, h);

            // Subtle grid
            g2.setColor(new Color(255, 255, 255, 6));
            g2.setStroke(thinStroke);
            for (int x = 0; x < w; x += 30) g2.drawLine(x, 0, x, h);
            for (int y = 0; y < h; y += 30) g2.drawLine(0, y, w, y);

            // IPs and panel sizing
            java.util.List<String> ips = getLocalIPs();
            boolean showIPs = isHost;
            int ipCount = showIPs ? ips.size() : 0;
            int panelW = 380;
            int panelH = Math.max(200, 120 + ipCount * 40 + (showIPs ? 120 : 70));
            int panelX = cx - panelW / 2;
            int panelY = cy - panelH / 2;

            // Breathing border animation
            long tCycle = System.currentTimeMillis() / 1200;
            boolean glow = (tCycle % 2 == 0);

            // Panel background
            g2.setColor(new Color(0, 0, 0, 170));
            g2.fillRoundRect(panelX, panelY, panelW, panelH, 20, 20);

            // Panel border with glow effect
            if (glow) {
                g2.setColor(new Color(0, 255, 170, 60));
                g2.setStroke(new BasicStroke(2.5f));
            } else {
                g2.setColor(WHITE40);
                g2.setStroke(normStroke);
            }
            g2.drawRoundRect(panelX, panelY, panelW, panelH, 20, 20);

            // Title
            String title = isHost ? "等待对手连接" : "连接中";
            g2.setColor(WHITE200);
            g2.setFont(menuBtnFont);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(title, cx - fm.stringWidth(title) / 2, panelY + 36);

            // Animated dots
            long tDot = System.currentTimeMillis() / 500;
            int dots = (int)(tDot % 4);
            StringBuilder sbDot = new StringBuilder();
            for (int i = 0; i < dots; i++) sbDot.append(".");
            String ds = sbDot.toString();
            g2.setFont(menuBtnFont);
            g2.setColor(WHITE80);
            fm = g2.getFontMetrics();
            g2.drawString(ds, cx + fm.stringWidth(title) / 2 + 6, panelY + 36);

            if (showIPs) {
                // IP section header
                g2.setColor(WHITE200);
                g2.setFont(new Font("SansSerif", Font.BOLD, 15));
                String ipLabel = "--- 本机IP地址 ---";
                fm = g2.getFontMetrics();
                g2.drawString(ipLabel, cx - fm.stringWidth(ipLabel) / 2, panelY + 64);

                // IP addresses with highlighted background
                g2.setFont(new Font("Monospaced", Font.BOLD, 20));
                int ipY = panelY + 104;
                for (String ip : ips) {
                    fm = g2.getFontMetrics();
                    int ipW = fm.stringWidth(ip);
                    int ipX = cx - ipW / 2;
                    g2.setColor(new Color(0, 255, 170, 25));
                    g2.fillRoundRect(ipX - 14, ipY - 20, ipW + 28, 32, 8, 8);
                    g2.setColor(new Color(0, 255, 170, 60));
                    g2.setStroke(thinStroke);
                    g2.drawRoundRect(ipX - 14, ipY - 20, ipW + 28, 32, 8, 8);
                    g2.setColor(Color.WHITE);
                    g2.drawString(ip, ipX, ipY);
                    ipY += 40;
                }

                // Port info with prominent badge
                int portBadgeY = ipY - 14;
                g2.setColor(new Color(0, 200, 255, 35));
                g2.fillRoundRect(cx - 120, portBadgeY, 240, 34, 8, 8);
                g2.setColor(new Color(0, 200, 255, 70));
                g2.setStroke(normStroke);
                g2.drawRoundRect(cx - 120, portBadgeY, 240, 34, 8, 8);
                g2.setFont(new Font("SansSerif", Font.BOLD, 18));
                g2.setColor(new Color(0, 220, 255));
                String portInfo = "端口: " + hostPort;
                fm = g2.getFontMetrics();
                g2.drawString(portInfo, cx - fm.stringWidth(portInfo) / 2, portBadgeY + 23);

                // Hint text
                g2.setFont(menuSmallFont);
                g2.setColor(new Color(255, 255, 255, 80));
                String hint = "请在其他客户端输入上方IP地址加入游戏";
                fm = g2.getFontMetrics();
                g2.drawString(hint, cx - fm.stringWidth(hint) / 2, portBadgeY + 56);
            }

            // Exit button
            int btnW = 130;
            int btnH = 34;
            int btnY = panelY + panelH - 48;
            boolean btnHover = (menuHover == 0 && currentScreen == Screen.HOST_WAIT);
            drawMenuBtnSmall(g2, cx, btnY, btnW, btnH, 17, "取消", btnHover, BTN_EXIT);

            // Toast message
            if (System.currentTimeMillis() < toastEndTime && !toastMessage.isEmpty()) {
                g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
                g2.setColor(new Color(0x00, 0xFF, 0xAA));
                FontMetrics tfm = g2.getFontMetrics();
                g2.drawString(toastMessage, cx - tfm.stringWidth(toastMessage) / 2, panelY + panelH + 20);
            }
        }

        private void drawHostPortScreen(Graphics2D g2) {
            int w = getWidth();
            int h = getHeight();
            int cx = w / 2;

            // Background
            GradientPaint bgGrad1 = new GradientPaint(0, 0, new Color(10, 8, 36),
                0, h, new Color(22, 16, 60));
            g2.setPaint(bgGrad1);
            g2.fillRect(0, 0, w, h);

            // Subtle grid
            g2.setColor(new Color(255, 255, 255, 6));
            g2.setStroke(thinStroke);
            for (int x = 0; x < w; x += 30) g2.drawLine(x, 0, x, h);
            for (int y = 0; y < h; y += 30) g2.drawLine(0, y, w, y);

            // Panel
            g2.setColor(new Color(0, 0, 0, 160));
            g2.fillRoundRect(cx - 180, 110, 360, 400, 16, 16);
            g2.setColor(WHITE40);
            g2.setStroke(normStroke);
            g2.drawRoundRect(cx - 180, 110, 360, 400, 16, 16);

            // Title
            g2.setColor(Color.WHITE);
            g2.setFont(menuBtnFont);
            String title = "创建游戏";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(title, cx - fm.stringWidth(title)/2, 165);

            // Description
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g2.setColor(WHITE80);
            String desc = "设置本地游戏端口号";
            fm = g2.getFontMetrics();
            g2.drawString(desc, cx - fm.stringWidth(desc)/2, 190);

            // Port input label
            g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
            g2.setColor(LABEL_COLOR);
            String portLabel = "端口号:";
            fm = g2.getFontMetrics();
            g2.drawString(portLabel, cx - fm.stringWidth(portLabel)/2, 220);

            // Port input box
            int boxX = cx - 100;
            int boxY = 225;
            int boxW = 200;
            int boxH = 36;
            g2.setColor(new Color(0, 0, 0, 100));
            g2.fillRoundRect(boxX, boxY, boxW, boxH, 8, 8);
            g2.setColor(WHITE60);
            g2.setStroke(normStroke);
            g2.drawRoundRect(boxX, boxY, boxW, boxH, 8, 8);

            // Port text
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Monospaced", Font.BOLD, 16));
            String displayPort = hostPortStr + (cursorVisible && currentScreen == Screen.HOST_PORT_INPUT ? "|" : " ");
            fm = g2.getFontMetrics();
            g2.drawString(displayPort, boxX + 8, boxY + boxH/2 + fm.getAscent()/2 - 2);

            // Port range hint
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g2.setColor(WHITE80);
            String rangeHint = "范围: 1024-65535";
            fm = g2.getFontMetrics();
            g2.drawString(rangeHint, cx - fm.stringWidth(rangeHint)/2, 280);

            // Status message
            if (!statusMessage.isEmpty()) {
                g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
                if (statusMessage.contains("成功")) {
                    g2.setColor(new Color(0x00, 0xFF, 0xAA));
                } else if (statusMessage.contains("占用") || statusMessage.contains("错误") || statusMessage.contains("无效") || statusMessage.contains("请输入")) {
                    g2.setColor(new Color(0xFF, 0x60, 0x60));
                } else {
                    g2.setColor(WHITE200);
                }
                fm = g2.getFontMetrics();
                g2.drawString(statusMessage, cx - fm.stringWidth(statusMessage)/2, 315);
            }

            // Create button
            int btnY = 335;
            drawMenuButton(g2, cx, btnY, 180, 44, "创建", menuHover == 0);

            // Back button
            int extBtnY = 420;
            boolean extHover = (menuHover == 1 && currentScreen == Screen.HOST_PORT_INPUT);
            drawMenuBtnSmall(g2, cx, extBtnY, 130, 34, 17, "返回", extHover, BTN_EXIT);

            // Hint
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g2.setColor(WHITE80);
            String hint = "按ENTER创建，按ESC返回";
            fm = g2.getFontMetrics();
            g2.drawString(hint, cx - fm.stringWidth(hint)/2, 475);
        }

        private void drawJoinScreen(Graphics2D g2) {
            int w = getWidth();
            int h = getHeight();
            int cx = w / 2;

            // Background
            GradientPaint bgGrad1 = new GradientPaint(0, 0, new Color(10, 8, 36),
                0, h, new Color(22, 16, 60));
            g2.setPaint(bgGrad1);
            g2.fillRect(0, 0, w, h);

            // Subtle grid
            g2.setColor(new Color(255, 255, 255, 6));
            g2.setStroke(thinStroke);
            for (int x = 0; x < w; x += 30) g2.drawLine(x, 0, x, h);
            for (int y = 0; y < h; y += 30) g2.drawLine(0, y, w, y);

            // Panel
            int panelH = 420;
            g2.setColor(new Color(0, 0, 0, 160));
            g2.fillRoundRect(cx - 180, 110, 360, panelH, 16, 16);
            g2.setColor(WHITE40);
            g2.setStroke(normStroke);
            g2.drawRoundRect(cx - 180, 110, 360, panelH, 16, 16);

            // Title
            g2.setColor(Color.WHITE);
            g2.setFont(menuBtnFont);
            String title = (joinMode == 1) ? "连接到服务器" : "加入游戏";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(title, cx - fm.stringWidth(title)/2, 165);

            // Mode description
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g2.setColor(WHITE80);
            String desc = (joinMode == 1)
                ? "输入服务器IP地址和端口"
                : "输入房主IP地址和端口";
            fm = g2.getFontMetrics();
            g2.drawString(desc, cx - fm.stringWidth(desc)/2, 190);

            int boxX = cx - 100;
            int boxW = 200;
            int boxH = 36;

            // IP input label
            g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
            g2.setColor(LABEL_COLOR);
            String ipLabel = "IP地址:";
            fm = g2.getFontMetrics();
            g2.drawString(ipLabel, cx - fm.stringWidth(ipLabel)/2, 218);

            // IP input box
            int ipBoxY = 222;
            g2.setColor(new Color(0, 0, 0, 100));
            g2.fillRoundRect(boxX, ipBoxY, boxW, boxH, 8, 8);
            if (ipFocused) {
                g2.setColor(FOCUS_COLOR);
            } else {
                g2.setColor(WHITE60);
            }
            g2.setStroke(normStroke);
            g2.drawRoundRect(boxX, ipBoxY, boxW, boxH, 8, 8);

            // IP text
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Monospaced", Font.BOLD, 16));
            String displayIp = joinIp + (cursorVisible && ipFocused ? "|" : " ");
            fm = g2.getFontMetrics();
            g2.drawString(displayIp, boxX + 8, ipBoxY + boxH/2 + fm.getAscent()/2 - 2);

            // Port input label
            g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
            g2.setColor(LABEL_COLOR);
            String portLabel = "端口号:";
            fm = g2.getFontMetrics();
            g2.drawString(portLabel, cx - fm.stringWidth(portLabel)/2, 280);

            // Port input box
            int portBoxY = 285;
            g2.setColor(new Color(0, 0, 0, 100));
            g2.fillRoundRect(boxX, portBoxY, boxW, boxH, 8, 8);
            if (!ipFocused) {
                g2.setColor(FOCUS_COLOR);
            } else {
                g2.setColor(WHITE60);
            }
            g2.setStroke(normStroke);
            g2.drawRoundRect(boxX, portBoxY, boxW, boxH, 8, 8);

            // Port text
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Monospaced", Font.BOLD, 16));
            String displayPort = joinPortStr + (cursorVisible && !ipFocused ? "|" : " ");
            fm = g2.getFontMetrics();
            g2.drawString(displayPort, boxX + 8, portBoxY + boxH/2 + fm.getAscent()/2 - 2);

            // Port range hint
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g2.setColor(WHITE80);
            String rangeHint = "范围: 1024-65535";
            fm = g2.getFontMetrics();
            g2.drawString(rangeHint, cx - fm.stringWidth(rangeHint)/2, 340);

            // Connect button
            int btnY = 358;
            drawMenuButton(g2, cx, btnY, 180, 44, "连接", menuHover == 0);

            // Exit button
            int extBtnY = 418;
            boolean extHover = (menuHover == 1 && currentScreen == Screen.JOIN_INPUT);
            drawMenuBtnSmall(g2, cx, extBtnY, 130, 34, 17, "退出", extHover, BTN_EXIT);

            // Hint
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g2.setColor(WHITE80);
            String hint = "TAB切换输入框，按ENTER连接，按ESC返回";
            fm = g2.getFontMetrics();
            g2.drawString(hint, cx - fm.stringWidth(hint)/2, 480);
        }

        private void drawResultScreen(Graphics2D g2) {
            int w = getWidth();
            int h = getHeight();
            int cx = w / 2;
            int cy = h / 2;

            // Dark overlay covering entire panel
            g2.setColor(new Color(0, 0, 0, 180));
            g2.fillRect(0, 0, w, h);

            // Panel (taller to fit 3 buttons)
            g2.setColor(new Color(0, 0, 0, 200));
            g2.fillRoundRect(cx-175, cy-100, 350, 310, 16, 16);
            g2.setColor(WHITE50);
            g2.setStroke(normStroke);
            g2.drawRoundRect(cx-175, cy-100, 350, 310, 16, 16);

            // Result
            String result;
            Color resultColor;
            if (won) {
                result = "你赢了！";
                resultColor = new Color(0x00, 0xFF, 0xAA);
            } else {
                result = "游戏结束";
                resultColor = new Color(0xFF, 0x60, 0x60);
            }
            g2.setColor(resultColor);
            g2.setFont(new Font("SansSerif", Font.BOLD, 34));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(result, cx - fm.stringWidth(result)/2, cy - 30);

            // Score
            g2.setFont(scoreFont);
            g2.setColor(Color.WHITE);
            String sc = "分数：" + score;
            fm = g2.getFontMetrics();
            g2.drawString(sc, cx - fm.stringWidth(sc)/2, cy + 15);

            g2.setFont(statFont);
            g2.setColor(LABEL_COLOR);
            String det = "等级：" + level + "  行数：" + lines;
            fm = g2.getFontMetrics();
            g2.drawString(det, cx - fm.stringWidth(det)/2, cy + 40);

            if (showRematchDialog) {
                int btnW = 100, btnH = 38;
                int gap = 20;
                int btnY = cy + 60;
                drawMenuButton(g2, cx - gap/2, btnY, btnW, btnH, "接受", menuHover == 0, BTN_ACCEPT);
                drawMenuButton(g2, cx + btnW + gap/2, btnY, btnW, btnH, "拒绝", menuHover == 1, BTN_EXIT);
                btnY = cy + 105;
                drawMenuButton(g2, cx, btnY, 130, 34, "退出游戏", menuHover == 2, BTN_EXIT);
            } else {
                // Play again / rematch button
                int btnY = cy + 60;
                String firstBtn = "再来一局";
                if (rematchPending) firstBtn = "等待对方...";
                drawMenuButton(g2, cx, btnY, 200, 42, firstBtn, menuHover == 0, BTN_ACCEPT);
                // Back to menu button
                btnY = cy + 108;
                drawMenuButton(g2, cx, btnY, 150, 38, "返回菜单", menuHover == 1, BTN_BACK);
                // Exit button
                btnY = cy + 150;
                drawMenuButton(g2, cx, btnY, 130, 36, "退出游戏", menuHover == 2, BTN_EXIT);
            }
        }

        private java.util.List<String> getLocalIPs() {
            java.util.List<String> ips = new java.util.ArrayList<>();
            try {
                java.util.Enumeration<java.net.NetworkInterface> nifs = java.net.NetworkInterface.getNetworkInterfaces();
                while (nifs.hasMoreElements()) {
                    java.net.NetworkInterface nif = nifs.nextElement();
                    if (nif.isLoopback() || !nif.isUp()) continue;
                    java.util.Enumeration<java.net.InetAddress> addrs = nif.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        java.net.InetAddress addr = addrs.nextElement();
                        if (addr instanceof Inet4Address) {
                            ips.add(addr.getHostAddress());
                        }
                    }
                }
            } catch (Exception ignored) {}
            if (ips.isEmpty()) ips.add("127.0.0.1");
            return ips;
        }
    }
}
