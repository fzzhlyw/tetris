import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import javax.sound.sampled.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Tetris Pro - Professional Tetris Game
 * Single-file Java Swing implementation with advanced features.
 */
public class TetrisPro extends JFrame {

    // ==================== CONSTANTS ====================
    private static final int COLS = 10;
    private static final int ROWS = 20;
    private static final int BLOCK_SIZE = 36;
    private static final int GAME_WIDTH = COLS * BLOCK_SIZE;
    private static final int GAME_HEIGHT = ROWS * BLOCK_SIZE;
    private static final int BOTTOM_BAR_HEIGHT = 50;
    private static final int SIDE_PANEL_WIDTH = 190;
    private static final int TOTAL_WIDTH = GAME_WIDTH + SIDE_PANEL_WIDTH + 30;
    private static final int TOTAL_HEIGHT = GAME_HEIGHT + 30 + BOTTOM_BAR_HEIGHT;
    private static final int PREVIEW_BLOCK = 24;
    private static final int MAX_NEXT = 3;
    private static final int MAX_HOLD = 1;
    private static final int PARTICLE_COUNT = 30;
    private static final int MAX_LEVEL = 15;
    private static final int LINES_PER_LEVEL = 10;
    // Extended bag for 7-bag randomizer: 7 pieces per bag
    private static final int BAG_SIZE = 7;
    private static final int QUEUE_SIZE = MAX_NEXT + BAG_SIZE;

    // ==================== FONT UTILITY ====================
    /** Cached resolved font family for Chinese text, determined once at class load. */
    private static final String CHINESE_FONT = resolveChineseFont();

    private static String resolveChineseFont() {
        String[] candidates = {
            "Microsoft YaHei",   // Windows
            "PingFang SC",       // macOS
            "WenQuanYi Micro Hei", // Linux (Ubuntu/麒麟)
            "WenQuanYi Zen Hei",   // Linux alternative
            "Noto Sans CJK SC",    // Linux (Google Noto)
            "Heiti SC",            // macOS legacy
            "SimHei",              // Windows legacy
            "Arial Unicode MS"     // Cross-platform
        };
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Set<String> available = new HashSet<>();
        for (Font f : ge.getAllFonts()) {
            available.add(f.getName());
        }
        for (String name : candidates) {
            if (available.contains(name)) {
                return name;
            }
        }
        // Fallback: use a system sans-serif which usually supports CJK on most platforms
        return "SansSerif";
    }

    /** Create a font using the resolved Chinese font family. */
    private static Font chineseFont(int style, float size) {
        return new Font(CHINESE_FONT, style, Math.round(size));
    }

    // ==================== THEME ====================
    enum Theme {
        CLASSIC("经典",
            new Color(0xF0EDE5), new Color(0x5D6B7A), new Color(0xE8E4DB), new Color(0x95A5A6),
            0.30f,   // Grid opacity
            false,   // No glow effect
            false,   // Standard blocks
            true,    // 3D bevel effect
            new Color(0xD5D0C5)  // darker().darker() of panelBg
        ),
        NEON("霓虹",
            new Color(0x080818), new Color(0x00FFAA), new Color(0x0E0E28), new Color(0x1A1A4A),
            0.18f,   // Lower grid opacity for dark theme
            true,    // Glow effect enabled
            true,    // Hollow neon blocks
            false,   // No 3D bevel
            new Color(0x020208)  // darker().darker() of panelBg
        ),
        DARK("暗黑",
            new Color(0x1A1A2E), new Color(0x4A4A6A), new Color(0x16213E), new Color(0x3A3A5A),
            0.25f,   // Medium grid opacity
            false,   // Subtle glow only on active piece
            false,   // Flat blocks with gradient
            true,    // Minimal 3D effect
            new Color(0x0C0C18)  // darker().darker() of panelBg
        ),
        SUNSET("日落",
            new Color(0x2D1B2E), new Color(0xE8846A), new Color(0x231525), new Color(0x5A3A4A),
            0.28f,   // Warm grid
            false,   // No glow
            false,   // Gradient blocks
            true,    // Soft 3D effect
            new Color(0x130A15)  // darker().darker() of panelBg
        );

        public final String name;
        public final Color bgColor, borderColor, panelBg, panelBorder, darkerBg;
        public final float gridOpacity;
        public final boolean hasGlow;
        public final boolean hollowBlocks;
        public final boolean bevelEffect;

        Theme(String name, Color bg, Color border, Color panelBg, Color panelBorder,
              float gridOpacity, boolean hasGlow, boolean hollowBlocks, boolean bevelEffect, Color darkerBg) {
            this.name = name;
            this.bgColor = bg;
            this.borderColor = border;
            this.panelBg = panelBg;
            this.panelBorder = panelBorder;
            this.gridOpacity = gridOpacity;
            this.hasGlow = hasGlow;
            this.hollowBlocks = hollowBlocks;
            this.bevelEffect = bevelEffect;
            this.darkerBg = darkerBg;
        }
    }

    // ==================== BLOCK COLORS ====================
    private static final Color[] BLOCK_COLORS = {
        new Color(0x00, 0xCC, 0xFF),   // I - Cyan
        new Color(0x00, 0x50, 0xFF),   // J - Blue
        new Color(0xFF, 0xA0, 0x00),   // L - Orange
        new Color(0xFF, 0xDC, 0x00),   // O - Yellow
        new Color(0x00, 0xCC, 0x44),   // S - Green
        new Color(0x90, 0x00, 0xFF),   // T - Purple
        new Color(0xFF, 0x30, 0x30),   // Z - Red
    };

    // ==================== SHADOW COLORS ====================
    private static final Color[] SHADOW_COLORS = {
        new Color(0x00, 0x66, 0x99), new Color(0x00, 0x28, 0x80), new Color(0x80, 0x50, 0x00),
        new Color(0x80, 0x70, 0x00), new Color(0x00, 0x66, 0x22), new Color(0x48, 0x00, 0xB0),
        new Color(0x80, 0x18, 0x18),
    };

    // ==================== PRE-CACHED BLOCK COLOR VARIANTS ====================
    // [type][index] for common alpha values used in rendering — avoids per-frame Color allocation
    // 0=alpha15, 1=alpha20, 2=alpha30, 3=alpha40, 4=alpha80, 5=alpha120
    private static final Color[][] BLOCK_COLOR_ALPHA = new Color[7][6];
    static {
        for (int t = 0; t < 7; t++) {
            Color c = BLOCK_COLORS[t];
            int r = c.getRed(), g = c.getGreen(), b = c.getBlue();
            BLOCK_COLOR_ALPHA[t][0] = new Color(r, g, b, 15);
            BLOCK_COLOR_ALPHA[t][1] = new Color(r, g, b, 20);
            BLOCK_COLOR_ALPHA[t][2] = new Color(r, g, b, 30);
            BLOCK_COLOR_ALPHA[t][3] = new Color(r, g, b, 40);
            BLOCK_COLOR_ALPHA[t][4] = new Color(r, g, b, 80);
            BLOCK_COLOR_ALPHA[t][5] = new Color(r, g, b, 120);
        }
    }

    // Pre-cached darker/brighter variants for SUNSET warm colors per block type
    private static final Color[] SUNSET_WARM_TOP = new Color[7];
    private static final Color[] SUNSET_WARM_BOTTOM = new Color[7];
    private static final Color[] SUNSET_WARM_TOP_BRIGHTER = new Color[7];
    static {
        for (int t = 0; t < 7; t++) {
            Color c = BLOCK_COLORS[t];
            int r = c.getRed(), g = c.getGreen(), b = c.getBlue();
            SUNSET_WARM_TOP[t] = new Color(
                    Math.min(255, r + 40), Math.min(255, g + 30), Math.min(255, b + 20));
            SUNSET_WARM_BOTTOM[t] = new Color(
                    Math.max(0, r - 30), Math.max(0, g - 20), Math.max(0, b - 10));
            // brighter version of warmTop (used for border)
            Color wt = SUNSET_WARM_TOP[t];
            SUNSET_WARM_TOP_BRIGHTER[t] = wt.brighter();
        }
    }

    // Pre-cached darker variants for DARK theme
    private static final Color[] BLOCK_DARKER = new Color[7];
    static {
        for (int t = 0; t < 7; t++) {
            BLOCK_DARKER[t] = BLOCK_COLORS[t].darker();
        }
    }

    // Pre-cached brighter.brighter variants for GradientManager
    private static final Color[] BLOCK_BRIGHTER2 = new Color[7];
    static {
        for (int t = 0; t < 7; t++) {
            BLOCK_BRIGHTER2[t] = BLOCK_COLORS[t].brighter().brighter();
        }
    }

    // Shared alpha colors for side panel and other rendering
    private static final Color WHITE_ALPHA_18 = new Color(255, 255, 255, 18);
    private static final Color BLACK_ALPHA_18 = new Color(0, 0, 0, 18);

    // Pre-cached button colors for BottomBar (6 buttons)
    private static final Color[] BTN_BASE = {
        new Color(30, 188, 108),
        new Color(255, 152, 0),
        new Color(0, 176, 220),
        new Color(140, 110, 240),
        new Color(220, 60, 60),
        new Color(60, 170, 200)
    };
    private static final Color[] BTN_HOVER = {
        new Color(90, 255, 168),
        new Color(255, 212, 60),
        new Color(60, 236, 255),
        new Color(180, 170, 255),
        new Color(255, 120, 120),
        new Color(120, 230, 255)
    };
    private static final Color[] BTN_GRAD_TOP = {
        new Color(70, 228, 148),
        new Color(255, 192, 40),
        new Color(40, 216, 255),
        new Color(180, 150, 255),
        new Color(255, 100, 100),
        new Color(100, 210, 240)
    };
    private static final Color[] BTN_BORDER = {
        new Color(255, 255, 255, 80),
        new Color(255, 255, 255, 80),
        new Color(255, 255, 255, 80),
        new Color(255, 255, 255, 80),
        new Color(255, 255, 255, 80),
        new Color(255, 255, 255, 80)
    };
    private static final Color[] BTN_BORDER_HOVER = {
        new Color(255, 255, 255, 140),
        new Color(255, 255, 255, 140),
        new Color(255, 255, 255, 140),
        new Color(255, 255, 255, 140),
        new Color(255, 255, 255, 140),
        new Color(255, 255, 255, 140)
    };
    private static final Color[] BTN_BORDER_PRESSED = {
        new Color(255, 255, 255, 200),
        new Color(255, 255, 255, 200),
        new Color(255, 255, 255, 200),
        new Color(255, 255, 255, 200),
        new Color(255, 255, 255, 200),
        new Color(255, 255, 255, 200)
    };

    // ==================== TETROMINO SHAPES ====================
    private static final int[][][] SHAPES = {
        {{0,0,0,0},{1,1,1,1},{0,0,0,0},{0,0,0,0}},  // I
        {{2,0,0},{2,2,2},{0,0,0}},                    // J
        {{0,0,3},{3,3,3},{0,0,0}},                    // L
        {{4,4},{4,4}},                                 // O
        {{0,5,5},{5,5,0},{0,0,0}},                    // S
        {{0,6,0},{6,6,6},{0,0,0}},                    // T
        {{7,7,0},{0,7,7},{0,0,0}},                    // Z
    };

    // ==================== PRE-CACHED COLORS ====================
    private static final Color[] START_FOLK_COLORS = {
        new Color(255, 220, 40),
        new Color(255, 255, 255),
        new Color(200, 30, 50),
    };
    private static final Color[] DECO_COLORS = {
        new Color(0x00, 0xCC, 0xFF, 50),   // I - Cyan
        new Color(0x00, 0x50, 0xFF, 50),   // J - Blue
        new Color(0xFF, 0xA0, 0x00, 50),   // L - Orange
        new Color(0xFF, 0xDC, 0x00, 50),   // O - Yellow
        new Color(0x00, 0xCC, 0x44, 50),   // S - Green
        new Color(0x90, 0x00, 0xFF, 50),   // T - Purple
        new Color(0xFF, 0x30, 0x30, 50),   // Z - Red
    };

    // ==================== GAME STATE ====================
    private static final java.util.Random SHUFFLE_RND = new java.util.Random();
    private volatile int[][] board;
    private int score = 0;
    private int level = 1;
    private int lines = 0;
    private long startTime = 0;
    private long lastTimeSec = -1;
    private String cachedTimeStr = "";
    private boolean paused = false;
    private boolean gameOver = false;
    private boolean gameStarted = false;
    private int currentType = -1;
    private volatile int currentX, currentY;
    private volatile int[][] currentShape;
    private int[] nextQueue = new int[QUEUE_SIZE];
    private int holdType = -1;
    private boolean holdUsed = false;
    private int dropInterval = 800;
    private long lastDropTime = 0;
    private Theme currentTheme = Theme.DARK;
    private java.util.List<Particle> particles = new ArrayList<>();
    private javax.swing.Timer clearTimer = null; // Separate timer for row clearing
    private volatile boolean flashRows = false;
    private javax.swing.Timer gameTimer = null; // Game loop timer
    private int[] flashRowIndices = new int[ROWS];
    private int flashCount = 0;
    private int flashMaxCount = 15;
    private boolean gameOverFade = false;
    private int gameOverAlpha = 0;
    private long gameOverAnimStartMs = 0;
    private boolean showGhost = true;
    private boolean isLocking = false; // Prevent double-lock during line clear animation

    // ==================== START SCREEN OPTIONS ====================
    private int selectedDifficulty = 1; // 0=easy, 1=normal, 2=hard
    private int selectedThemeIdx = 2;
    private boolean showStartScreen = true;
    private int hoveredBtn = -1; // 0=start button, 1=difficulty up, 2=difficulty down, 3=theme left, 4=theme right
    // Pill bounds for start screen nav buttons (updated in paintComponent)
    private int diffPillW = 150, diffPillY = 0, diffNavX = 0;
    private int themePillW = 110, themePillY = 0, themeNavX = 0, themeRightX = 0;

    // Start screen background sparkle animation
    private javax.swing.Timer startScreenTimer = null;
    private java.util.List<Sparkle> startScreenSparkles = new ArrayList<>();
    private int startScreenSparkleTime = 0;
    private static class Sparkle {
        float x, y;
        float speed;
        float size;
        float phase;
        Sparkle(float x, float y, float speed, float size, float phase) {
            this.x = x; this.y = y; this.speed = speed; this.size = size; this.phase = phase;
        }
    }

    // Start screen side panel: falling tetromino decoration
    private int fallTetTime = 0;
    private static class FallingTet {
        int type;
        int[][] shape;
        float x, y;
        float speed;
        float rot; // 0-3
        FallingTet(int type, float x, float y, float speed) {
            this.type = type;
            this.shape = SHAPES[type];
            this.x = x; this.y = y; this.speed = speed;
        }
    }
 
    // ==================== FOCUS BORDER ====================
    private Color focusGainedBorderColor = new Color(100, 180, 255);
    private Color focusLostBorderColor = new Color(180, 180, 200);
    private void setFocusGainedColor(Color c) { focusGainedBorderColor = c; }
    private void setFocusLostColor(Color c) { focusLostBorderColor = c; }

    // ==================== SOUND ====================
    private SoundManager soundManager = new SoundManager();
    private void playSound(String type) {
        if (!soundEnabled) return;
        soundManager.play(type);
    }
    private boolean soundEnabled = true;
    private boolean bgMusicEnabled = true;
    private String musicDirectory = "music";
    private boolean bgMusicLoop = true;



    private void goToStartScreen() {
        if (gamePanel.keyRepeatTimer != null) {
            gamePanel.keyRepeatTimer.stop();
            gamePanel.keyRepeatTimer = null;
        }
        holdUsed = false;
        holdType = -1;
        currentType = -1;
        currentShape = null;
        paused = false;
        gameOver = false;
        score = 0;
        lines = 0;
        level = 1;
        dropInterval = getDropIntervalForDifficulty(selectedDifficulty);
        particles.clear();
        if (clearTimer != null) { clearTimer.stop(); clearTimer = null; }
        if (gameTimer != null) gameTimer.stop();
        if (bgMusicEnabled) {
            soundManager.stopBgMusic();
        }
        showStartScreen = true;
        gameStarted = false;
        gameOverFade = false;
        gameOverAlpha = 0;
        gameOverAnimStartMs = 0;
        fallTetTime = 0;
        flashRows = false;
        gamePanel.setBackground(currentTheme.bgColor);
        initStartScreenSparkles();
        gamePanel.requestFocusInWindow();
        SwingUtilities.invokeLater(() -> gamePanel.repaint());
    }

        // ==================== GAME PANEL ====================
    private GamePanel gamePanel;
    private BottomBar bottomBar;

    private boolean decorated =false;
    private boolean systemExitFlag = true;

    public TetrisPro(boolean decorated, boolean systemExitFlag) {
        this.decorated = decorated;
        this.systemExitFlag = systemExitFlag;
        setTitle("Tetris Pro - 俄罗斯方块专业版");

	if ( systemExitFlag )
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	else
            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        setResizable(false);
        if (!decorated) {
            setUndecorated(true);
        }
        setLayout(new BorderLayout());

        gamePanel = new GamePanel();
        add(gamePanel, BorderLayout.CENTER);

        bottomBar = new BottomBar();
        add(bottomBar, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);

        // Remove Java icon from window and dialogs
        try {
            BufferedImage icon = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            setIconImage(icon);
        } catch (Exception ignored) {}

        initGame();
    }

    // ==================== GAME INIT ====================
    private void initGame() {
        resetGameState(true);
    }

    /** Reset all game state. When restoringFromQuit is true, reinitialize the queue/bag. */
    private void resetGameState(boolean restoringFromQuit) {
        board = new int[ROWS][COLS];
        score = 0; level = 1; lines = 0;
        paused = false; gameOver = false; gameStarted = false;
        showGhost = true; soundEnabled = true; bgMusicEnabled = true;
        holdType = -1; holdUsed = false;
        currentType = -1;
        currentShape = null;
        particles.clear();
        flashRows = false; gameOverFade = false;
        gameOverAlpha = 0; gameOverAnimStartMs = 0;
        showStartScreen = true;
        selectedDifficulty = 1;
        selectedThemeIdx = 2;
        dropInterval = getDropIntervalForDifficulty(selectedDifficulty);
        startTime = System.currentTimeMillis();
        lastDropTime = 0;
        lastTimeSec = -1;
        if (restoringFromQuit) {
            initStartScreenSparkles();
            fillBag();
            for (int i = 0; i < QUEUE_SIZE; i++) {
                nextQueue[i] = getNextPiece();
            }
        }
    }

    private int getDropIntervalForDifficulty(int diff) {
        switch (diff) {
            case 0: return 1200; // easy
            case 1: return 800;  // normal
            case 2: return 400;  // hard
            default: return 800;
        }
    }

    private void initStartScreenSparkles() {
        startScreenSparkles.clear();
        startScreenSparkleTime = 0;
        long seed = 777L;
        for (int i = 0; i < 50; i++) {
            seed = seed * 6364136223846353L + 1;
            float x = (Math.abs(seed) % (GAME_WIDTH - 40)) + 20;
            seed = seed * 6364136223846353L + 1;
            float y = (Math.abs(seed) % (GAME_HEIGHT - 40)) + 40;
            seed = seed * 6364136223846353L + 1;
            float speed = 0.15f + (Math.abs(seed) % 30) / 100f;
            seed = seed * 6364136223846353L + 1;
            float size = 1.5f + (Math.abs(seed) % 25) / 10f;
            seed = seed * 6364136223846353L + 1;
            float phase = (Math.abs(seed) % 360) * (float)Math.PI / 180f;
            startScreenSparkles.add(new Sparkle(x, y, speed, size, phase));
        }
         if (startScreenTimer == null || !startScreenTimer.isRunning()) {
            if (startScreenTimer != null) { startScreenTimer.stop(); }
            startScreenTimer = new javax.swing.Timer(30, ev -> {
                startScreenSparkleTime++;
                fallTetTime++;
                repaint();
            });
            startScreenTimer.start();
        }
    }

    private void startGameFromScreen() {
        showStartScreen = false;
        if (startScreenTimer != null) { startScreenTimer.stop(); startScreenTimer = null; }
        // Recreate gameTimer because javax.swing.Timer cannot be restarted after stop()
        if (gameTimer != null) { gameTimer.stop(); }
        gameTimer = null;
        gamePanel.restartGameTimer();
        gameStarted = true;
        // Reset game state
        board = new int[ROWS][COLS];
        score = 0; level = 1; lines = 0;
        paused = false; gameOver = false;
        showGhost = true; soundEnabled = true; bgMusicEnabled = true;
        holdType = -1; holdUsed = false;
        currentType = -1; currentShape = null;
        particles.clear();
        flashRows = false; gameOverFade = false;
        gameOverAlpha = 0; gameOverAnimStartMs = 0;
        lastTimeSec = -1;
        // Apply selected theme
        currentTheme = Theme.values()[selectedThemeIdx % Theme.values().length];
        gamePanel.setBackground(currentTheme.bgColor);
        // Apply selected difficulty
        dropInterval = getDropIntervalForDifficulty(selectedDifficulty);
        // Pre-fill queue and spawn first piece
        fillBag();
        for (int i = 0; i < QUEUE_SIZE; i++) {
            nextQueue[i] = getNextPiece();
        }
        spawnPiece();
        lastDropTime = System.currentTimeMillis();
        startTime = System.currentTimeMillis();
        // Apply music settings and async start background music
        soundManager.setMusicDirectory(musicDirectory);
        soundManager.setLoopMusic(bgMusicLoop);
        SwingUtilities.invokeLater(() -> {
            if (bgMusicEnabled) {
                soundManager.startBgMusic();
            }
        });
        // Request focus with multiple attempts to ensure keyboard input works
        gamePanel.requestFocusInWindow();
        gamePanel.setFocusable(true);
        gamePanel.requestFocus();
        gamePanel.repaint();
    }

 
    private void newGame() {
        // Pause game first if actively playing
        boolean wasPlaying = gameStarted && !gameOver && !paused;
        if (wasPlaying) {
            paused = true;
        }
        // Always show confirmation dialog before starting a new game
        int ret = showNewGameConfirmDialog();
        if (ret != JOptionPane.YES_OPTION) {
            // User cancelled - resume the game
            if (wasPlaying) {
                paused = false;
            }
            return;
        }

        playSound("newgame");
        // Stop all timers to prevent stale callbacks
        if (gamePanel.keyRepeatTimer != null) {
            gamePanel.keyRepeatTimer.stop();
            gamePanel.keyRepeatTimer = null;
        }
        if (gameTimer != null) { gameTimer.stop(); }
        if (clearTimer != null) { clearTimer.stop(); clearTimer = null; }
        // Preserve bg music state: only stop if it was running, don't change enabled flag
        boolean wasBgMusicEnabled = bgMusicEnabled;
        if (bgMusicEnabled) {
            try { soundManager.stopBgMusic(); } catch (Exception ignored) {}
        }
        // Reset game state without going back to start screen
        board = new int[ROWS][COLS];
        score = 0; level = 1; lines = 0;
        paused = false; gameOver = false; gameStarted = true;
        showGhost = true; soundEnabled = true;
        holdType = -1; holdUsed = false;
        currentType = -1;
        currentShape = null;
        particles.clear();
        flashRows = false; gameOverFade = false;
        gameOverAlpha = 0; gameOverAnimStartMs = 0;
        showStartScreen = false;
        dropInterval = getDropIntervalForDifficulty(selectedDifficulty);
        startTime = System.currentTimeMillis();
        lastDropTime = System.currentTimeMillis();
        lastTimeSec = -1;
        // Pre-fill queue
        fillBag();
        for (int i = 0; i < QUEUE_SIZE; i++) {
            nextQueue[i] = getNextPiece();
        }
        // Spawn first piece
        spawnPiece();
        // Apply music settings and restart bg music if it was previously enabled
        soundManager.setMusicDirectory(musicDirectory);
        soundManager.setLoopMusic(bgMusicLoop);
        if (wasBgMusicEnabled && bgMusicEnabled) {
            soundManager.startBgMusic();
        }
        // Recreate gameTimer because javax.swing.Timer cannot be restarted after stop()
        gamePanel.restartGameTimer();
        gamePanel.requestFocusInWindow();
        SwingUtilities.invokeLater(() -> gamePanel.repaint());
    }

    /** Custom themed confirmation dialog for starting a new game. */
    private int showNewGameConfirmDialog() {
        int[] result = new int[1];
        JDialog[] dialogRef = new JDialog[1];

        // === Main content panel: clean white card ===
        JPanel contentPanel = new JPanel(new BorderLayout(0, 14)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
            }
        };
        contentPanel.setOpaque(true);
        contentPanel.setBackground(Color.WHITE);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(28, 36, 16, 36));

        // === Title bar: fresh green gradient ===
        JPanel titleWrap = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, new Color(0xE8, 0xF5, 0xE9),
                                                     getWidth(), 0, new Color(0xC8, 0xE6, 0xC9));
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        titleWrap.setOpaque(false);
        titleWrap.setBorder(BorderFactory.createEmptyBorder(14, 20, 14, 20));

        JLabel titleLabel = new JLabel("新游戏", SwingConstants.CENTER);
        titleLabel.setFont(chineseFont(Font.BOLD, 18));
        titleLabel.setForeground(new Color(0x2E, 0x7D, 0x32));
        titleWrap.add(titleLabel, BorderLayout.CENTER);
        contentPanel.add(titleWrap, BorderLayout.NORTH);

        // === Message ===
        String msg;
        if (gameStarted && !gameOver) {
            msg = "当前游戏进度将丢失，是否重新开始？";
        } else {
            msg = "是否开始新游戏？";
        }
        JLabel msgLabel = new JLabel(msg, SwingConstants.CENTER);
        msgLabel.setFont(chineseFont(Font.PLAIN, 15));
        msgLabel.setForeground(new Color(0x42, 0x42, 0x42));
        contentPanel.add(msgLabel, BorderLayout.CENTER);

        // === Score highlight (if applicable) ===
        if (gameStarted && !gameOver && score > 0) {
            JPanel scoreWrap = new JPanel(new BorderLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(0xFF, 0xF8, 0xE1));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    g2.setColor(new Color(0xFF, 0xD7, 0x00));
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            scoreWrap.setOpaque(false);
            scoreWrap.setBorder(BorderFactory.createEmptyBorder(12, 24, 12, 24));

            JLabel scoreLabel = new JLabel("当前分数: " + score, SwingConstants.CENTER);
            scoreLabel.setFont(chineseFont(Font.BOLD, 16));
            scoreLabel.setForeground(new Color(0xF5, 0x7F, 0x17));
            scoreWrap.add(scoreLabel, BorderLayout.CENTER);

            contentPanel.add(scoreWrap, BorderLayout.SOUTH);
        }

        // === Button panel ===
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 14)) {
            @Override protected void paintComponent(Graphics g) { super.paintComponent(g); }
        };
        btnPanel.setOpaque(true);
        btnPanel.setBackground(Color.WHITE);
        btnPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));

        // "确定" button — fresh green
        JButton yesBtn = new JButton("确定");
        yesBtn.setFont(chineseFont(Font.BOLD, 15));
        yesBtn.setForeground(Color.WHITE);
        yesBtn.setBackground(new Color(0x4C, 0xAF, 0x50));
        yesBtn.setFocusPainted(false);
        yesBtn.setBorderPainted(false);
        yesBtn.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        yesBtn.setPreferredSize(new java.awt.Dimension(90, 38));
        yesBtn.setOpaque(true);
        yesBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x38, 0x8E, 0x3C), 1),
            BorderFactory.createEmptyBorder(8, 0, 8, 0)
        ));
        yesBtn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                yesBtn.setBackground(new Color(0x38, 0x8E, 0x3C));
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                yesBtn.setBackground(new Color(0x4C, 0xAF, 0x50));
            }
        });
        yesBtn.addActionListener(e -> {
            result[0] = JOptionPane.YES_OPTION;
            dialogRef[0].dispose();
        });

        // "取消" button — soft gray outline
        JButton noBtn = new JButton("取消");
        noBtn.setFont(chineseFont(Font.BOLD, 15));
        noBtn.setForeground(new Color(0x75, 0x75, 0x75));
        noBtn.setBackground(new Color(0xF5, 0xF5, 0xF5));
        noBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0xE0, 0xE0, 0xE0), 1),
            BorderFactory.createEmptyBorder(8, 0, 8, 0)
        ));
        noBtn.setFocusPainted(false);
        noBtn.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        noBtn.setPreferredSize(new java.awt.Dimension(90, 38));
        noBtn.setOpaque(true);
        noBtn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                noBtn.setBackground(new Color(0xEE, 0xEE, 0xEE));
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                noBtn.setBackground(new Color(0xF5, 0xF5, 0xF5));
            }
        });
        noBtn.addActionListener(e -> {
            result[0] = JOptionPane.NO_OPTION;
            dialogRef[0].dispose();
        });

        btnPanel.add(yesBtn);
        btnPanel.add(noBtn);

        // === Assemble dialog ===
        dialogRef[0] = new JDialog(this, "", true);
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(true);
        wrapper.setBackground(Color.WHITE);
        wrapper.add(contentPanel, BorderLayout.CENTER);
        wrapper.add(btnPanel, BorderLayout.SOUTH);
        dialogRef[0].setContentPane(wrapper);
        dialogRef[0].setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialogRef[0].setResizable(false);
        dialogRef[0].pack();
        dialogRef[0].setLocation(
            getX() + (getWidth() - dialogRef[0].getWidth()) / 2,
            getY() + (getHeight() - dialogRef[0].getHeight()) / 2
        );

        dialogRef[0].addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                if (result[0] != JOptionPane.YES_OPTION) {
                    result[0] = JOptionPane.NO_OPTION;
                }
            }
        });

        dialogRef[0].setVisible(true);
        return result[0];
    }

    /** Draw all 7 tetromino shapes as a decorative background pattern. */
    private void drawTetrominoBackground(Graphics2D g2, int w, int h, int alpha) {
        int bs = 14; // block size for background pieces
        int gap = 18; // gap between pieces horizontally
        int totalW = 7 * (4 * bs + gap); // max width if all in one row
        int startX = (w - Math.min(totalW, w)) / 2;
        int baseY = (h - (4 * bs + gap)) / 2;

        for (int type = 0; type < 7; type++) {
            int[][] shape = SHAPES[type];
            int cols = 0, rows = 0;
            for (int r = 0; r < shape.length; r++)
                for (int c = 0; c < shape[r].length; c++)
                    if (shape[r][c] != 0) {
                        cols = Math.max(cols, c + 1);
                        rows = Math.max(rows, r + 1);
                    }
            int pieceW = cols * bs;
            int pieceH = rows * bs;
            int ox = startX + type * (4 * bs + gap) - (4 * bs + gap - pieceW) / 2;
            int oy = baseY + (4 * bs + gap - pieceH) / 2;

            Color baseColor = BLOCK_COLORS[type];
            // Filled shape with low alpha
            g2.setColor(new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), alpha));
            for (int r = 0; r < shape.length; r++) {
                for (int c = 0; c < shape[r].length; c++) {
                    if (shape[r][c] != 0) {
                        g2.fillRoundRect(ox + c * bs + 1, oy + r * bs + 1, bs - 2, bs - 2, 3, 3);
                    }
                }
            }
            // Subtle border highlight
            g2.setColor(new Color(255, 255, 255, Math.min(80, alpha + 30)));
            g2.setStroke(new BasicStroke(0.8f));
            for (int r = 0; r < shape.length; r++) {
                for (int c = 0; c < shape[r].length; c++) {
                    if (shape[r][c] != 0) {
                        g2.drawRoundRect(ox + c * bs + 1, oy + r * bs + 1, bs - 2, bs - 2, 3, 3);
                    }
                }
            }
        }
    }

    private void togglePause() {
        if (gameOver || !gameStarted) return;
        paused = !paused;
        if (paused) {
            playSound("pause");
        } else {
            playSound("resume");
            lastDropTime = System.currentTimeMillis();
        }
        gamePanel.repaint();
    }

    private void toggleNextMusic() {
        if (!gameStarted || gameOver || !JLAYER_AVAILABLE) return;
        if (!soundManager.isBgMusicRunning()) return;
        soundManager.playNextMusic();
        playSound("move");
        gamePanel.repaint();
    }

    private void togglePrevMusic() {
        if (!gameStarted || gameOver || !JLAYER_AVAILABLE) return;
        if (!soundManager.isBgMusicRunning()) return;
        soundManager.playPrevMusic();
        playSound("move");
        gamePanel.repaint();
    }

    private void cycleTheme() {
        int idx = (currentTheme.ordinal() + 1) % Theme.values().length;
        currentTheme = Theme.values()[idx];
        playSound("theme");
        gamePanel.setBackground(currentTheme.bgColor);
        gamePanel.repaint();
    }

    private void showMusicSettingsDialog() {
        JDialog dialog = new JDialog(this, "音乐设置", true);
        dialog.setResizable(false);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 16, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 8, 8, 8);

        int row = 0;

        // --- Warning row (when JLayer is unavailable) ---
        if (!JLAYER_AVAILABLE) {
            gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3; gbc.weightx = 0;
            JLabel warnLabel = new JLabel("音乐播放插件未配置，音乐播放功能禁用");
            warnLabel.setFont(chineseFont(Font.BOLD, 14));
            warnLabel.setForeground(Color.RED);
            panel.add(warnLabel, gbc);
            row++;
        }

        // --- Music directory row ---
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        gbc.gridwidth = 1;
        JLabel dirLabel = new JLabel("音乐目录:");
        dirLabel.setFont(chineseFont(Font.PLAIN, 14));
        panel.add(dirLabel, gbc);

        gbc.gridx = 1; gbc.weightx = 1;
        JTextField dirField = new JTextField(musicDirectory, 22);
        dirField.setEditable(false);
        dirField.setFont(chineseFont(Font.PLAIN, 13));
        panel.add(dirField, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        JButton browseBtn = new JButton("浏览...");
        browseBtn.setFont(chineseFont(Font.PLAIN, 13));
        browseBtn.setEnabled(JLAYER_AVAILABLE);
        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(musicDirectory);
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("选择音乐目录");
            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                dirField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        panel.add(browseBtn, gbc);

        // --- Loop toggle row ---
        gbc.gridx = 0; gbc.gridy = row + 1; gbc.gridwidth = 3; gbc.weightx = 0;
        JCheckBox loopCheck = new JCheckBox("循环播放音乐", bgMusicLoop);
        loopCheck.setFont(chineseFont(Font.PLAIN, 14));
        loopCheck.setEnabled(JLAYER_AVAILABLE);
        panel.add(loopCheck, gbc);

        // --- Button row ---
        gbc.gridy = row + 2;
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
        JButton okBtn = new JButton("确定");
        okBtn.setFont(chineseFont(Font.BOLD, 14));
        JButton cancelBtn = new JButton("取消");
        cancelBtn.setFont(chineseFont(Font.PLAIN, 14));
        btnPanel.add(okBtn);
        btnPanel.add(cancelBtn);
        panel.add(btnPanel, gbc);

        okBtn.addActionListener(e -> {
            musicDirectory = dirField.getText();
            bgMusicLoop = loopCheck.isSelected();
            soundManager.setMusicDirectory(musicDirectory);
            soundManager.setLoopMusic(bgMusicLoop);
            if (bgMusicEnabled && JLAYER_AVAILABLE) {
                try { soundManager.stopBgMusic(); } catch (Exception ignored) {}
                soundManager.startBgMusic();
            }
            dialog.dispose();
        });
        cancelBtn.addActionListener(e -> dialog.dispose());

        dialog.add(panel);
        dialog.pack();
        dialog.setLocation(
            getX() + (getWidth() - dialog.getWidth()) / 2,
            getY() + (getHeight() - dialog.getHeight()) / 2
        );
        dialog.setVisible(true);
    }

    // ==================== BAG RANDOMIZER (7-bag) ====================
    private int[] bagBuffer = new int[BAG_SIZE]; // buffer to hold remaining pieces from current bag
    private int bagPtr = 0; // next index in bagBuffer to hand out

    private void fillBag() {
        Integer[] types = {0, 1, 2, 3, 4, 5, 6};
        shuffle(types);
        for (int i = 0; i < BAG_SIZE; i++) {
            bagBuffer[i] = types[i];
        }
        bagPtr = 0;
    }

    private void shuffle(Integer[] arr) {
        for (int i = arr.length - 1; i > 0; i--) {
            int j = SHUFFLE_RND.nextInt(i + 1);
            Integer tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp;
        }
    }

    /** Get next piece type, refilling bag if needed */
    private int getNextPiece() {
        if (bagPtr >= BAG_SIZE) {
            fillBag();
        }
        return bagBuffer[bagPtr++];
    }

    // ==================== PIECE SPAWN ====================
   private void spawnPiece() {
        // Get current piece from queue (first element)
        currentType = nextQueue[0];
        
        // Shift queue left by one position
        for (int i = 0; i < QUEUE_SIZE - 1; i++) {
            nextQueue[i] = nextQueue[i + 1];
        }
        
        // Fill the last position with a new piece from bag
        nextQueue[QUEUE_SIZE - 1] = getNextPiece();

        // Initialize current piece
        currentShape = cloneShape(SHAPES[currentType]);
        currentX = COLS / 2 - currentShape[0].length / 2;
        currentY = 0;

        // Special handling for O piece (square) - center in 10-column grid
        if (currentType == 3) { // O piece
            currentX = COLS / 2 - 1;
        }

        // Check if spawn position is valid
        if (!isValidPosition(currentShape, currentX, currentY)) {
            gameOver = true;
            gameOverFade = true;
            gameOverAlpha = 0;
            gameOverAnimStartMs = System.currentTimeMillis();
            playSound("gameover");
            gameStarted = true; // Ensure game is marked as started so game over screen shows
            currentShape = null;
            return; // Exit immediately to prevent further processing
        }
    }

    private int[][] cloneShape(int[][] src) {
        int r = src.length, c = src[0].length;
        int[][] dst = new int[r][c];
        for (int i = 0; i < r; i++) System.arraycopy(src[i], 0, dst[i], 0, c);
        return dst;
    }

    // ==================== COLLISION ====================
    private boolean isValidPosition(int[][] shape, int px, int py) {
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape[r].length; c++) {
                if (shape[r][c] != 0) {
                    int bx = px + c, by = py + r;
                    // Check boundaries - must be within play area
                    if (bx < 0 || bx >= COLS || by >= ROWS) return false;
                    // Only check board collision if within vertical bounds
                    // (parts above the board are allowed during spawn)
                    if (by >= 0 && board[by][bx] != 0) return false;
                }
            }
        }
        return true;
    }

    // ==================== ROTATION ====================
    private int[][] rotateCW(int[][] shape) {
        int r = shape.length, c = shape[0].length;
        int[][] rotated = new int[c][r];
        for (int i = 0; i < r; i++)
            for (int j = 0; j < c; j++)
                rotated[j][r - 1 - i] = shape[i][j];
        return rotated;
    }

    // ==================== GHOST PIECE ====================
    private int getGhostY() {
        if (currentType < 0 || currentShape == null) return currentY;
        int gy = currentY;
        while (isValidPosition(currentShape, currentX, gy + 1)) gy++;
        return gy;
    }

    // ==================== LOCK PIECE ====================
    private void lockPiece() {
        // Prevent double-lock during line clear animation
        if (gameOver || paused || currentShape == null || currentType < 0) return;
        // If called while already locking (e.g. auto-drop fires during flash),
        // nudge the piece down one cell so it settles after animation ends.
        if (isLocking) {
            if (isValidPosition(currentShape, currentX, currentY + 1)) {
                currentY++;
            }
            return;
        }
        isLocking = true;
        
        try {
            for (int r = 0; r < currentShape.length; r++) {
                for (int c = 0; c < currentShape[r].length; c++) {
                    if (currentShape[r][c] != 0) {
                        int bx = currentX + c, by = currentY + r;
                        if (by >= 0 && by < ROWS && bx >= 0 && bx < COLS) {
                            board[by][bx] = currentType + 1;
                        }
                    }
                }
            }
            playSound("lock");
            checkLines();
        } finally {
            isLocking = false;
        }
    }

    // ==================== LINE CLEAR ====================
    private void checkLines() {
        java.util.List<Integer> fullRows = new ArrayList<>();
        for (int r = 0; r < ROWS; r++) {
            boolean full = true;
            for (int c = 0; c < COLS; c++) {
                if (board[r][c] == 0) { full = false; break; }
            }
            if (full) fullRows.add(r);
        }

        if (!fullRows.isEmpty()) {
            flashRows = true;
            for (int i = 0; i < fullRows.size(); i++) {
                flashRowIndices[i] = fullRows.get(i);
            }
            flashCount = 0;
            flashMaxCount = fullRows.size();

            // Spawn particles on cleared rows
            for (int rowIdx : fullRows) {
                for (int c = 0; c < COLS; c++) {
                    int colorIdx = board[rowIdx][c] - 1;
                    if (colorIdx >= 0 && colorIdx < BLOCK_COLORS.length) {
                        Color baseColor = BLOCK_COLORS[colorIdx];
                        for (int p = 0; p < 5; p++) {
                            particles.add(new Particle(
                                c * BLOCK_SIZE + BLOCK_SIZE / 2,
                                rowIdx * BLOCK_SIZE + BLOCK_SIZE / 2,
                                baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue()
                            ));
                        }
                    }
                }
            }

            // Trigger repaint for flash animation; score/level updated in clearRows()
            gamePanel.repaint();

            // Cancel any existing clear timer and schedule row clearing after flash animation
            if (clearTimer != null) {
                clearTimer.stop();
                clearTimer = null;
            }
            clearTimer = new javax.swing.Timer(350, e -> {
                if (!flashRows) return;
                clearRows(fullRows);
                clearTimer = null;
            });
            clearTimer.setRepeats(false);
            clearTimer.start();
        } else {
            // No lines cleared - spawn next piece
            spawnPiece();
            lastDropTime = System.currentTimeMillis(); // Reset drop timer to prevent immediate lock
            holdUsed = false;
        }
    }

    private void clearRows(java.util.List<Integer> rows) {
        // Standard Tetris: remove all cleared rows at once, drop all above rows down
        // Build set of rows to clear for O(1) lookup
        boolean[] clearRow = new boolean[ROWS];
        for (int r : rows) clearRow[r] = true;

        int[][] newBoard = new int[ROWS][COLS];
        int writeRow = ROWS - 1;
        for (int r = ROWS - 1; r >= 0; r--) {
            if (!clearRow[r]) {
                System.arraycopy(board[r], 0, newBoard[writeRow], 0, COLS);
                writeRow--;
            }
        }
        // Fill remaining top rows with zeros
        for (int r = writeRow; r >= 0; r--) {
            java.util.Arrays.fill(newBoard[r], 0);
        }
        board = newBoard;

        // Scoring: use the level BEFORE this clear for all wave scoring (standard Tetris rule)
        int preClearLevel = level;
        playSound("clear" + rows.size());
        lines += rows.size();
        level = Math.min(MAX_LEVEL, lines / LINES_PER_LEVEL + 1);
        dropInterval = Math.max(50, 800 - (level - 1) * 50);
        int[] points = {0, 100, 300, 500, 800};
        score += points[Math.min(rows.size(), points.length - 1)] * preClearLevel;

        // Spawn new piece and always reset drop timer
        spawnPiece();
        lastDropTime = System.currentTimeMillis();

        // If spawn failed (game over), clear flash and return early
        if (gameOver) {
            flashRows = false;
            return;
        }

        // Reset hold state after successful piece spawn
        holdUsed = false;

        // Force immediate repaint to show updated board, then clear flash
        gamePanel.repaint();
        flashRows = false;
    }

    // ==================== HOLD ====================
    private void doHold() {
        if (holdUsed || gameOver || paused || currentShape == null || currentType < 0) return;
        playSound("hold");
        boolean wasSwapped = false;
        if (holdType >= 0) {
            int tmp = currentType;
            currentType = holdType;
            holdType = tmp;
            wasSwapped = true;
        } else {
            holdType = currentType;
        }
        holdUsed = true;
        if (wasSwapped) {
            currentShape = cloneShape(SHAPES[currentType]);
            currentX = COLS / 2 - currentShape[0].length / 2;
            currentY = 0;
            if (currentType == 3) {
                currentX = COLS / 2 - 1;
            }
            if (!isValidPosition(currentShape, currentX, currentY)) {
                gameOver = true; gameOverFade = true; gameStarted = true;
                playSound("gameover");
                currentShape = null;
                return;
            }
        } else {
            spawnPiece();
            if (gameOver) return;
        }
        lastDropTime = System.currentTimeMillis();
        // holdUsed remains true until the next piece is placed (reset in checkLines/clearRows)
    }

    // ==================== HARD DROP ====================
    private void hardDrop() {
        if (currentType < 0 || currentShape == null) return;
        int drops = 0;
        while (isValidPosition(currentShape, currentX, currentY + 1)) {
            currentY++;
            drops++;
        }
        score += drops * 2;
        playSound("drop");
        lockPiece();
    }

    // ==================== SOFT DROP ====================
    private void softDrop() {
        if (currentType < 0 || currentShape == null) return;
        if (isValidPosition(currentShape, currentX, currentY + 1)) {
            currentY++;
            score += 1;
            lastDropTime = System.currentTimeMillis();
        }
    }

    // ==================== MOVEMENT ====================
    private void moveLeft() {
        if (currentType < 0 || currentShape == null) return;
        if (isValidPosition(currentShape, currentX - 1, currentY)) {
            currentX--;
            playSound("move");
        }
    }

    private void moveRight() {
        if (currentType < 0 || currentShape == null) return;
        if (isValidPosition(currentShape, currentX + 1, currentY)) {
            currentX++;
            playSound("move");
        }
    }

    private void rotate() {
        if (currentType < 0 || currentShape == null) return;
        int[][] rotated = rotateCW(currentShape);
        // Wall kick: try original, then offsets
        int[] kicks = {0, -1, 1, -2, 2};
        for (int kick : kicks) {
            if (isValidPosition(rotated, currentX + kick, currentY)) {
                currentShape = rotated;
                currentX += kick;
                playSound("rotate");
                return;
            }
            if (isValidPosition(rotated, currentX + kick, currentY - 1) && currentY > 0) {
                currentShape = rotated;
                currentX += kick;
                currentY -= 1;
                playSound("rotate");
                return;
            }
        }
    }

    // ==================== GAME PANEL ====================
    private class GamePanel extends JPanel {

        // Cached start screen button layout to avoid duplication across paint/click/move
        private int startScreenBtnY = 0, startScreenExitBtnY = 0, startScreenCx = 0;
        private void computeStartScreenLayout() {
            startScreenCx = 5 + GAME_WIDTH / 2;
            int optOverlayY = 30 + 85;
            int optionStartY = optOverlayY + 30;
            int themeY = optionStartY + 60;
            int pillH = 34;
            int startBtnY = themeY + pillH + 50;
            startScreenBtnY = startBtnY;
            startScreenExitBtnY = startBtnY + 44 + 48;
        }
        int getStartScreenBtnY() { return startScreenBtnY; }
        int getStartScreenExitBtnY() { return startScreenExitBtnY; }
        int getStartScreenCx() { return startScreenCx; }

        // Keyboard auto-repeat for continuous movement
        private javax.swing.Timer keyRepeatTimer = null;

        // Reusable game loop action listener for restart capability
        private java.awt.event.ActionListener gameTimerAction = null;
        private static final int KEY_REPEAT_INITIAL_DELAY = 170;
        private static final int KEY_REPEAT_INTERVAL = 30;
        private int keyRepeatKeyCode = 0;

        // M key double-press detection
        private boolean mKeyWaitingForDouble = false;
        private javax.swing.Timer mKeyTimer = null;

        // Cached reusable objects to reduce paintComponent allocation
        private final Font titleFont = chineseFont(Font.BOLD, 13);
        private final Font pauseFont = chineseFont(Font.BOLD, 36);
        private final Font resumeHintFont = chineseFont(Font.PLAIN, 16);
        private final Font gameOverFont = chineseFont(Font.BOLD, 36);
        private final Font scoreFont = chineseFont(Font.BOLD, 28);
        private final Font levelFont = chineseFont(Font.PLAIN, 16);
        private final BasicStroke thinStroke = new BasicStroke(0.5f);
        private final BasicStroke focusStroke = new BasicStroke(2f);
        private final BasicStroke borderStroke = new BasicStroke(1.5f);
        private final BasicStroke thickBorderStroke = new BasicStroke(3f);
        private final BasicStroke ghostStroke = new BasicStroke(2f);
        private final BasicStroke normalStroke = new BasicStroke(1f);
        private final BasicStroke lightStroke = new BasicStroke(1.2f);
        private final BasicStroke mediumStroke = new BasicStroke(0.8f);
        private final BasicStroke dashStroke = new BasicStroke(2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{6, 4}, 0);
        private final BasicStroke solidStroke25 = new BasicStroke(2.5f);
        private final BasicStroke solidStroke18 = new BasicStroke(1.8f);

        // Cached colors for frequent use in paintComponent
        private final Color white60 = new Color(255, 255, 255, 60);
        private final Color white80 = new Color(255, 255, 255, 80);
        private final Color white40 = new Color(255, 255, 255, 40);
        private final Color white30 = new Color(255, 255, 255, 30);
        private final Color white50 = new Color(255, 255, 255, 50);
        private final Color white200 = new Color(255, 255, 255, 200);
        private final Color white20 = new Color(255, 255, 255, 20);
        private final Color white25 = new Color(255, 255, 255, 25);
        private final Color white15 = new Color(255, 255, 255, 15);
        private final Color white18 = new Color(255, 255, 255, 18);
        private final Color white45 = new Color(255, 255, 255, 45);
        private final Color white40bg = new Color(0, 0, 0, 40);
        private final Color white60bg = new Color(0, 0, 0, 60);
        private final Color white160bg = new Color(0, 0, 0, 160);
        private final Color white45bg = new Color(0, 0, 0, 45);
        private final Color white100bg = new Color(0, 0, 0, 100);
        private final Color white25bg = new Color(0, 0, 0, 25);
        private final Color white18bg = new Color(0, 0, 0, 18);

        // Pre-cached colors for side panel and preview rendering
        private final Color classicLabelColor = new Color(0x2C3E50);
        private final Color classicValueColor = new Color(0x1A1A2E);
        private final Color sectionBgColor = new Color(255, 255, 255, 12);
        private final Color neonPreviewBg = new Color(0x00, 0xFF, 0xAA, 8);
        private final Color neonPreviewBorder = new Color(0x00, 0xFF, 0xAA, 25);
        private final Color darkPreviewBg = new Color(255, 255, 255, 8);
        private final Color sunsetPreviewBg = new Color(0xE8, 0x84, 0x6A, 10);
        private final Color sunsetPreviewBorder = new Color(0xE8, 0x84, 0x6A, 25);
        private final Color classicPreviewBg = new Color(255, 255, 255, 6);

        // Additional cached fonts for start screen and side panel
        private final Font startHintFont = chineseFont(Font.PLAIN, 14);
        private final Font startTitleFont = chineseFont(Font.BOLD, 42);
        private final Font startSubFont = chineseFont(Font.PLAIN, 15);
        private final Font startSectionFont = chineseFont(Font.BOLD, 18);
        private final Font startOptFont = chineseFont(Font.BOLD, 14);
        private final Font startSmallFont = chineseFont(Font.BOLD, 10);
        private final Font startDiffFont = chineseFont(Font.BOLD, 20);
        private final Font startDiffLabelFont = chineseFont(Font.BOLD, 13);
        private final Font startStartFont = chineseFont(Font.BOLD, 15);
        private final Font startExitFont = chineseFont(Font.PLAIN, 12);
        private final Font sideInfoLabelFont = chineseFont(Font.PLAIN, 16);
        private final Font sideStatLabelFont = chineseFont(Font.BOLD, 14);
        private final Font sideStatValueFont = chineseFont(Font.BOLD, 13);
        private final Font sideSectionFont = chineseFont(Font.BOLD, 12);
        private final Font sideControlFont = chineseFont(Font.PLAIN, 13);
        private final Font sideControlKeyFont = chineseFont(Font.BOLD, 13);
        private final Font themeDescFont = chineseFont(Font.PLAIN, 11);
        private final Font bottomBarFont = chineseFont(Font.PLAIN, 9);

        public GamePanel() {
            setPreferredSize(new Dimension(TOTAL_WIDTH, GAME_HEIGHT + 30));
            setFocusable(true);
            setBackground(currentTheme.bgColor);

            // Double buffering
            setDoubleBuffered(true);

            // Add focus listener to ensure panel can receive keyboard events
            setFocusGainedColor(new Color(100, 180, 255));
            setFocusLostColor(new Color(180, 180, 200));
            addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    repaint();
                }

                @Override
                public void focusLost(FocusEvent e) {
                    repaint();
                }
            });

            // Window dragging for undecorated frame
            if (!decorated) {
                final Point[] dragOffset = new Point[1];
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        dragOffset[0] = e.getPoint();
                    }
                    @Override
                    public void mouseReleased(MouseEvent e) {
                        dragOffset[0] = null;
                    }
                });
                addMouseMotionListener(new MouseMotionAdapter() {
                    @Override
                    public void mouseDragged(MouseEvent e) {
                        // Don't allow window dragging on start screen (buttons need drag-free clicks)
                        if (dragOffset[0] != null && !showStartScreen) {
                            JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(GamePanel.this);
                            if (frame != null) {
                                if (isShowing()) {
                                    Point screenPos = getLocationOnScreen();
                                    frame.setLocation(
                                        screenPos.x + e.getX() - dragOffset[0].x,
                                        screenPos.y + e.getY() - dragOffset[0].y);
                                }
                            }
                        }
                    }
                });
            }

            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (gameOver) {
                        if (e.getKeyCode() == KeyEvent.VK_N) {
                            if (keyRepeatTimer != null) {
                                keyRepeatTimer.stop();
                                keyRepeatTimer = null;
                            }
                            newGame();
                        } else if (e.getKeyCode() == KeyEvent.VK_Q) {
                            soundManager.stopBgMusic();
                            goToStartScreen();
                        }
                        return;
                    }

                    // Start screen key handling
                    if (showStartScreen) {
                        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                            startGameFromScreen();
                            return;
                        }
                        // Navigate options on start screen
                        if (e.getKeyCode() == KeyEvent.VK_UP) {
                            selectedDifficulty = Math.max(0, selectedDifficulty - 1);
                            dropInterval = getDropIntervalForDifficulty(selectedDifficulty);
                            repaint();
                            return;
                        }
                        if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                            selectedDifficulty = Math.min(2, selectedDifficulty + 1);
                            dropInterval = getDropIntervalForDifficulty(selectedDifficulty);
                            repaint();
                            return;
                        }
                        if (e.getKeyCode() == KeyEvent.VK_LEFT || e.getKeyCode() == KeyEvent.VK_RIGHT) {
                            if (e.getKeyCode() == KeyEvent.VK_LEFT) {
                                selectedThemeIdx = (selectedThemeIdx - 1 + Theme.values().length) % Theme.values().length;
                            } else {
                                selectedThemeIdx = (selectedThemeIdx + 1) % Theme.values().length;
                            }
                            repaint();
                            return;
                        }
                        // Q key: cleanup and exit
                        if (e.getKeyCode() == KeyEvent.VK_Q) {
                            if (gameTimer != null) gameTimer.stop();
                            if (startScreenTimer != null) { startScreenTimer.stop(); startScreenTimer = null; }
                            if (gamePanel != null && gamePanel.keyRepeatTimer != null) {
                                gamePanel.keyRepeatTimer.stop(); gamePanel.keyRepeatTimer = null;
                            }
                            soundManager.shutdown();
                            if ( systemExitFlag ) System.exit(0);
                            else {
			        dispose();
                                return;
			    }
                        }
                        return;
                    }

                    if (!gameStarted) return;
                    if (paused) {
                        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                            togglePause();
                        } else if (e.getKeyCode() == KeyEvent.VK_N) {
                            newGame();
                        } else if (e.getKeyCode() == KeyEvent.VK_Q) {
                            soundManager.stopBgMusic();
                            goToStartScreen();
                        }
                        return;
                    }
                    // Block gameplay actions during line clear flash animation
                    if (flashRows) return;
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_LEFT: {
                            if (currentShape != null && currentType >= 0) { moveLeft(); startKeyRepeat(e.getKeyCode()); } break;
                        }
                        case KeyEvent.VK_RIGHT: {
                            if (currentShape != null && currentType >= 0) { moveRight(); startKeyRepeat(e.getKeyCode()); } break;
                        }
                        case KeyEvent.VK_DOWN: {
                            if (currentShape != null && currentType >= 0) { softDrop(); startKeyRepeat(e.getKeyCode()); } break;
                        }
                        case KeyEvent.VK_UP: {
                            if (currentShape != null && currentType >= 0) { rotate(); } break;
                        }
                        case KeyEvent.VK_SPACE: if (currentShape != null && currentType >= 0) hardDrop(); break;
                        case KeyEvent.VK_C: case KeyEvent.VK_H: if (currentShape != null && currentType >= 0) doHold(); break;
                        case KeyEvent.VK_P: if (currentShape != null && currentType >= 0) toggleNextMusic(); break;
                        case KeyEvent.VK_O: if (currentShape != null && currentType >= 0) togglePrevMusic(); break;
                        case KeyEvent.VK_T: if (currentShape != null && currentType >= 0) cycleTheme(); break;
                        case KeyEvent.VK_G: if (currentShape != null && currentType >= 0) showGhost = !showGhost; break;
                        case KeyEvent.VK_N: newGame(); break;
                        case KeyEvent.VK_Q: {
                            soundManager.stopBgMusic();
                            goToStartScreen();
                            break;
                        }
                        case KeyEvent.VK_ESCAPE: togglePause(); break;
                        case KeyEvent.VK_M: {
                            if (mKeyWaitingForDouble) {
                                if (mKeyTimer != null) {
                                    mKeyTimer.stop();
                                    mKeyTimer = null;
                                }
                                mKeyWaitingForDouble = false;
                                bgMusicEnabled = !bgMusicEnabled;
                                if (bgMusicEnabled) {
                                    soundManager.resumeBgMusic();
                                } else {
                                    soundManager.stopBgMusic();
                                }
                                playSound("theme");
                            } else {
                                mKeyWaitingForDouble = true;
                                mKeyTimer = new javax.swing.Timer(400, ev -> {
                                    mKeyWaitingForDouble = false;
                                    mKeyTimer = null;
                                    soundEnabled = !soundEnabled;
                                    playSound("theme");
                                    gamePanel.repaint();
                                });
                                mKeyTimer.setRepeats(false);
                                mKeyTimer.start();
                            }
                            gamePanel.repaint();
                            break;
                        }
                        case KeyEvent.VK_L: showMusicSettingsDialog(); break;
                    }
                    e.consume();
                    // repaint handled by gameTimer (50ms interval)
                }

                @Override
                public void keyReleased(KeyEvent e) {
                    // Stop auto-repeat for arrow keys on key release
                    if (keyRepeatTimer != null) {
                        keyRepeatTimer.stop();
                        keyRepeatTimer = null;
                    }
                }

                void startKeyRepeat(int keyCode) {
                    TetrisPro.GamePanel.this.keyRepeatKeyCode = keyCode;
                    if (keyRepeatTimer != null) {
                        keyRepeatTimer.stop();
                    }
                    keyRepeatTimer = new javax.swing.Timer(KEY_REPEAT_INTERVAL, e -> {
                        if (gameOver || showStartScreen || !gameStarted || flashRows || currentType < 0) return;
                        switch (keyRepeatKeyCode) {
                            case KeyEvent.VK_LEFT: moveLeft(); break;
                            case KeyEvent.VK_RIGHT: moveRight(); break;
                            case KeyEvent.VK_DOWN: softDrop(); break;
                            case KeyEvent.VK_UP: rotate(); break;
                        }
                        repaint();
                    });
                    keyRepeatTimer.setInitialDelay(KEY_REPEAT_INITIAL_DELAY);
                    keyRepeatTimer.start();
                }
            });

            // Mouse listener for start screen button click
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (showStartScreen) {
                        computeStartScreenLayout();
                        int cx = getStartScreenCx();
                        int btnX = cx - 85, btnY = getStartScreenBtnY();
                        int btnW = 170, btnH = 44;
                        if (e.getX() >= btnX && e.getX() <= btnX + btnW &&
                            e.getY() >= btnY && e.getY() <= btnY + btnH) {
                            startGameFromScreen();
                            return;
                        }
                        // Exit button
                        int exitBtnW = 170, exitBtnH = 44;
                        int exitBtnX = cx - exitBtnW / 2;
                        int exitBtnY = getStartScreenExitBtnY();
                        if (e.getX() >= exitBtnX && e.getX() <= exitBtnX + exitBtnW &&
                            e.getY() >= exitBtnY && e.getY() <= exitBtnY + exitBtnH) {
                            // Stop all timers to prevent stale callbacks
                            if (gameTimer != null) gameTimer.stop();
                            if (startScreenTimer != null) { startScreenTimer.stop(); startScreenTimer = null; }
                            if (clearTimer != null) { clearTimer.stop(); clearTimer = null; }
                            if (gamePanel != null && gamePanel.keyRepeatTimer != null) {
                                gamePanel.keyRepeatTimer.stop(); gamePanel.keyRepeatTimer = null;
                            }
                            soundManager.shutdown();
                            if ( systemExitFlag ) System.exit(0);
                            else {
			        dispose();
                                return;
			    }
                        }
                        // Difficulty nav buttons (navBtnW=26, centered in pill with height 15)
                        int navX = diffNavX, dY = diffPillY;
                        int navBtnW = 26, navBtnH = 15;
                        int dNavY = dY + (34 - navBtnH) / 2;
                        // Left arrow
                        if (e.getX() >= navX && e.getX() <= navX + navBtnW &&
                            e.getY() >= dNavY && e.getY() <= dNavY + navBtnH) {
                            selectedDifficulty = (selectedDifficulty - 1 + 3) % 3;
                            dropInterval = getDropIntervalForDifficulty(selectedDifficulty);
                            repaint();
                            return;
                        }
                        // Right arrow
                        int rightX = navX + navBtnW + 8;
                        if (e.getX() >= rightX && e.getX() <= rightX + navBtnW &&
                            e.getY() >= dNavY && e.getY() <= dNavY + navBtnH) {
                            selectedDifficulty = (selectedDifficulty + 1) % 3;
                            dropInterval = getDropIntervalForDifficulty(selectedDifficulty);
                            repaint();
                            return;
                        }
                        // Theme nav buttons
                        int tY = themePillY;
                        int tNavY = tY + (34 - navBtnH) / 2;
                        if (e.getX() >= themeNavX && e.getX() <= themeNavX + navBtnW &&
                            e.getY() >= tNavY && e.getY() <= tNavY + navBtnH) {
                            selectedThemeIdx = (selectedThemeIdx - 1 + 4) % 4;
                            repaint();
                        }
                        if (e.getX() >= themeRightX && e.getX() <= themeRightX + navBtnW &&
                            e.getY() >= tNavY && e.getY() <= tNavY + navBtnH) {
                            selectedThemeIdx = (selectedThemeIdx + 1) % 4;
                            repaint();
                        }
                    }
                }
            });

            // Mouse motion listener for hover detection on start screen buttons
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    if (showStartScreen) {
                        computeStartScreenLayout();
                        hoveredBtn = -1;
                        int cx = getStartScreenCx();
                        int btnX = cx - 85, btnY = getStartScreenBtnY();
                        if (e.getX() >= btnX && e.getX() <= btnX + 170 &&
                            e.getY() >= btnY && e.getY() <= btnY + 44) {
                            hoveredBtn = 0;
                        }
                        // Difficulty nav buttons (navBtnW=26, centered in pill with height 15)
                        int navX = diffNavX, dY = diffPillY;
                        int navBtnW = 26, navBtnH = 15;
                        int dNavY = dY + (34 - navBtnH) / 2;
                        // Left arrow
                        if (e.getX() >= navX && e.getX() <= navX + navBtnW &&
                            e.getY() >= dNavY && e.getY() <= dNavY + navBtnH) {
                            hoveredBtn = 1;
                        }
                        // Right arrow
                        int rightX = navX + navBtnW + 8;
                        if (e.getX() >= rightX && e.getX() <= rightX + navBtnW &&
                            e.getY() >= dNavY && e.getY() <= dNavY + navBtnH) {
                            hoveredBtn = 2;
                        }
                        // Theme nav buttons
                        int tY = themePillY;
                        int tNavY = tY + (34 - navBtnH) / 2;
                        if (e.getX() >= themeNavX && e.getX() <= themeNavX + navBtnW &&
                            e.getY() >= tNavY && e.getY() <= tNavY + navBtnH) {
                            hoveredBtn = 3;
                        }
                        if (e.getX() >= themeRightX && e.getX() <= themeRightX + navBtnW &&
                            e.getY() >= tNavY && e.getY() <= tNavY + navBtnH) {
                            hoveredBtn = 4;
                        }
                        // Exit button hover
                        int exitBtnW = 170, exitBtnH = 44;
                        int exitBtnX = cx - exitBtnW / 2;
                        int exitBtnY = getStartScreenExitBtnY();
                        if (e.getX() >= exitBtnX && e.getX() <= exitBtnX + exitBtnW &&
                            e.getY() >= exitBtnY && e.getY() <= exitBtnY + exitBtnH) {
                            hoveredBtn = 5;
                        }
                        if (hoveredBtn != -1) repaint();
                    }
                }
            });

            // Auto-drop timer
            gameTimerAction = ev -> {
                // Check if bg music finished naturally (non-looping) - disable music
                if (soundManager.bgMusicFinished) {
                    soundManager.bgMusicFinished = false;
                    bgMusicEnabled = false;
                    repaint();
                }
                if (gameStarted && !paused && !gameOver && !flashRows && currentShape != null && currentType >= 0) {
                    long now = System.currentTimeMillis();
                    if (now - lastDropTime >= dropInterval) {
                        // Try to move down
                        if (isValidPosition(currentShape, currentX, currentY + 1)) {
                            currentY++;
                            lastDropTime = now;
                        } else {
                            // Cannot move down - lock the piece
                            // But only if the piece is actually at the bottom
                            // Prevent locking during line clear recovery
                            lockPiece();
                            lastDropTime = now; // Reset timer after locking
                        }
                    }
                }
                // Always update particles and repaint (including during flash for animation)
                if (gameStarted && !paused) {
                    particles.removeIf(p -> !p.update());
                }
                repaint();
            };
            gameTimer = new javax.swing.Timer(50, gameTimerAction);
            gameTimer.start();
        }

        public void restartGameTimer() {
            if (gameTimer != null) { gameTimer.stop(); }
            gameTimer = new javax.swing.Timer(50, this.gameTimerAction);
            gameTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Theme theme = currentTheme;
            g2.setColor(theme.bgColor);
            g2.fillRect(0, 0, TOTAL_WIDTH, GAME_HEIGHT + 30);

            // Focus border (replaces setBorder to avoid overlapping custom paint)
            if (hasFocus()) {
                g2.setColor(focusGainedBorderColor);
                g2.setStroke(focusStroke);
                g2.drawRoundRect(1, 1, TOTAL_WIDTH - 2, GAME_HEIGHT + 28, 4, 4);
            } else {
                g2.setColor(focusLostBorderColor);
                g2.setStroke(focusStroke);
                g2.drawRoundRect(1, 1, TOTAL_WIDTH - 2, GAME_HEIGHT + 28, 4, 4);
            }

            if (!showStartScreen) {
                // Outer frame shadow
                g2.setColor(white40bg);
                g2.fillRoundRect(7, 37, GAME_WIDTH, GAME_HEIGHT + 5, 10, 10);

                // Title bar background with enhanced gradient
                GradientPaint titleGrad = new GradientPaint(
                        5, 5, theme.borderColor.brighter(),
                        5, 27, theme.borderColor);
                g2.setPaint(titleGrad);
                g2.fillRoundRect(5, 5, GAME_WIDTH, 24, 8, 8);

                // Title bar border with glow effect
                g2.setColor(white80);
                g2.setStroke(borderStroke);
                g2.drawRoundRect(5, 5, GAME_WIDTH, 24, 8, 8);

                // Title text with icon (dark text for bright theme gradients, white for dark themes)
                Color titleTextColor = (theme == Theme.NEON) ? new Color(0x08, 0x08, 0x18) : Color.WHITE;
                g2.setColor(titleTextColor);
                g2.setFont(titleFont);
                FontMetrics fm = g2.getFontMetrics();
                String titleText = "Tetris Pro 俄罗斯方块专业版 V1.0.0";
                Rectangle2D titleBounds = fm.getStringBounds(titleText, g2);
                g2.drawString(titleText, (GAME_WIDTH - (int) titleBounds.getWidth()) / 2 + 5,
                        22 - (24 - (int) titleBounds.getHeight()) / 2);

                // Draw game area background with enhanced gradient
                GradientPaint areaGrad = new GradientPaint(
                        5, 30, theme.panelBg,
                        5, 30 + GAME_HEIGHT, theme.panelBg.darker().darker());
                g2.setPaint(areaGrad);
                g2.fillRoundRect(5, 30, GAME_WIDTH, GAME_HEIGHT, 10, 10);

                // Game area outer border with enhanced glow
                g2.setColor(white45);
                g2.setStroke(focusStroke);
                g2.drawRoundRect(5, 30, GAME_WIDTH, GAME_HEIGHT, 10, 10);
                g2.setColor(theme.borderColor);
                g2.setStroke(thickBorderStroke);
                g2.drawRoundRect(6, 31, GAME_WIDTH - 2, GAME_HEIGHT - 2, 9, 9);

                // Draw grid (theme-aware styling)
                g2.setColor(theme.borderColor);
                g2.setStroke(thinStroke);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, theme.gridOpacity));
                for (int r = 0; r <= ROWS; r++) {
                    g2.drawLine(5, 30 + r * BLOCK_SIZE, 5 + GAME_WIDTH, 30 + r * BLOCK_SIZE);
                }
                for (int c = 0; c <= COLS; c++) {
                    g2.drawLine(5 + c * BLOCK_SIZE, 30, 5 + c * BLOCK_SIZE, 30 + GAME_HEIGHT);
                }
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

                // Draw board
                for (int r = 0; r < ROWS; r++) {
                    for (int c = 0; c < COLS; c++) {
                        if (board[r][c] != 0) {
                            drawBlock(g2, 5 + c * BLOCK_SIZE, 30 + r * BLOCK_SIZE,
                                    board[r][c] - 1, false);
                        }
                    }
                }

                // Flash animation for cleared rows
                if (flashRows) {
                    if (flashCount >= flashMaxCount + 30) {
                        flashRows = false;
                    } else {
                        flashCount++;
                        int alpha = Math.max(0, 220 - flashCount * 12);
                        g2.setColor(new Color(255, 255, 255, alpha));
                        for (int i = 0; i < flashMaxCount && i < flashRowIndices.length; i++) {
                            int row = flashRowIndices[i];
                            if (row >= 0 && row < ROWS) {
                                g2.fillRect(5, 30 + row * BLOCK_SIZE, GAME_WIDTH, BLOCK_SIZE);
                            }
                        }
                    }
                }

                // Draw ghost piece
                if (gameStarted && !gameOver && currentType >= 0 && currentShape != null && showGhost) {
                    int ghostY = getGhostY();
                    if (ghostY != currentY) {
                        g2.setStroke(ghostStroke);
                        // Pre-compute ghost colors outside cell loop
                        Color ghostFillClassic = BLOCK_COLOR_ALPHA[currentType][2]; // alpha=30
                        Color ghostFillSunset = BLOCK_COLOR_ALPHA[currentType][3];  // alpha=40
                        Color ghostStrokeSunset = SUNSET_WARM_TOP_BRIGHTER[currentType];
                        for (int r = 0; r < currentShape.length; r++) {
                            for (int c = 0; c < currentShape[r].length; c++) {
                                if (currentShape[r][c] != 0) {
                                    int bx = 5 + (currentX + c) * BLOCK_SIZE;
                                    int by = 30 + (ghostY + r) * BLOCK_SIZE;

                                    // Theme-specific ghost rendering
                                    if (theme == Theme.NEON) {
                                        // Neon: dashed outline with glow
                                        g2.setStroke(dashStroke);
                                        g2.setColor(BLOCK_COLORS[currentType]);
                                        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
                                        g2.drawRoundRect(bx + 2, by + 2, BLOCK_SIZE - 4, BLOCK_SIZE - 4, 6, 6);
                                    } else if (theme == Theme.DARK) {
                                        // Dark: minimal outline
                                        g2.setColor(BLOCK_COLORS[currentType]);
                                        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));
                                        g2.drawRoundRect(bx + 3, by + 3, BLOCK_SIZE - 6, BLOCK_SIZE - 6, 5, 5);
                                    } else if (theme == Theme.SUNSET) {
                                        // Sunset: warm transparent fill
                                        g2.setColor(ghostFillSunset);
                                        g2.fillRoundRect(bx + 2, by + 2, BLOCK_SIZE - 4, BLOCK_SIZE - 4, 6, 6);
                                        g2.setColor(ghostStrokeSunset);
                                        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
                                        g2.drawRoundRect(bx + 2, by + 2, BLOCK_SIZE - 4, BLOCK_SIZE - 4, 6, 6);
                                    } else {
                                        // Classic: standard ghost
                                        g2.setColor(BLOCK_COLORS[currentType]);
                                        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
                                        g2.drawRoundRect(bx + 2, by + 2, BLOCK_SIZE - 4, BLOCK_SIZE - 4, 6, 6);
                                        g2.setColor(ghostFillClassic);
                                        g2.fillRoundRect(bx + 2, by + 2, BLOCK_SIZE - 4, BLOCK_SIZE - 4, 6, 6);
                                    }
                                    // Restore stroke after Neon dashed outline (outside cell loop)
                                    if (theme == Theme.NEON) {
                                        g2.setStroke(normalStroke);
                                    }
                                }
                            }
                        }
                        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
                    }
                }

   // Draw current piece
                if (gameStarted && !gameOver && currentType >= 0 && currentShape != null) {
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

                // Draw particles
                for (Particle p : particles) {
                    p.draw(g2);
                }

                // Draw side panel
                drawSidePanel(g2, 5 + GAME_WIDTH + 15, 30);
            }

            // Draw pause overlay
            if (paused && gameStarted) {
                g2.setColor(white160bg);
                g2.fillRoundRect(5, 30, GAME_WIDTH, GAME_HEIGHT, 10, 10);
                // Tetromino pattern background
                Shape savedPauseClip = g2.getClip();
                AffineTransform savedPauseClipXform = g2.getTransform();
                g2.setClip(5, 30, GAME_WIDTH, GAME_HEIGHT);
                g2.translate(5, 30);
                drawTetrominoBackground(g2, GAME_WIDTH, GAME_HEIGHT, 25);
                g2.setTransform(savedPauseClipXform);
                g2.setClip(savedPauseClip);
                g2.setColor(Color.WHITE);
                g2.setFont(pauseFont);
                FontMetrics fmPause = g2.getFontMetrics();
                String pauseText = "暂停中";
                Rectangle2D boundsPause = fmPause.getStringBounds(pauseText, g2);
                g2.drawString(pauseText, (GAME_WIDTH - (int) boundsPause.getWidth()) / 2 + 5,
                        30 + (GAME_HEIGHT - (int) boundsPause.getHeight()) / 2 + 15);
                g2.setFont(resumeHintFont);
                FontMetrics fmResume = g2.getFontMetrics();
                String resumeHint = "按 ESC 继续";
                Rectangle2D hintBounds = fmResume.getStringBounds(resumeHint, g2);
                g2.drawString(resumeHint, (GAME_WIDTH - (int) hintBounds.getWidth()) / 2 + 5,
                        30 + (GAME_HEIGHT - (int) boundsPause.getHeight()) / 2 + 50);
            }

            // Draw game over overlay
            if (gameOverFade && gameOver) {
                if (gameOverAnimStartMs == 0) {
                    gameOverAnimStartMs = System.currentTimeMillis();
                }
                gameOverAlpha = Math.min(220, gameOverAlpha + 5);
                long elapsedMs = System.currentTimeMillis() - gameOverAnimStartMs;
                int phase = (int) (elapsedMs / 16); // ~16ms per frame equivalent
                int overlayAlpha = Math.min(255, gameOverAlpha);
                // Dark base background (only over game area)
                g2.setColor(new Color(0, 0, 0, overlayAlpha));
                g2.fillRoundRect(5, 30, GAME_WIDTH, GAME_HEIGHT, 10, 10);
                // Tetromino pattern background
                // Delay tetromino pattern until overlay is dark enough for text contrast
                if (overlayAlpha > 140) {
                    Shape savedGameOverClip = g2.getClip();
                    AffineTransform savedGameOverClipXform = g2.getTransform();
                    g2.setClip(5, 30, GAME_WIDTH, GAME_HEIGHT);
                    g2.translate(5, 30);
                    drawTetrominoBackground(g2, GAME_WIDTH, GAME_HEIGHT, Math.min(40, (overlayAlpha - 140) / 3));
                    g2.setTransform(savedGameOverClipXform);
                    g2.setClip(savedGameOverClip);
                }
                // Phase 1: "游戏结束" title appears with pulse (alpha > 120, ~24 frames in)
                if (gameOverAlpha > 120) {
                    int contentCenterY = 30 + GAME_HEIGHT / 2;
                    float cx = 5 + GAME_WIDTH / 2f; // center of game area

                    // Title: scale bounce (large -> normal -> slightly over -> settle)
                    float bounce = (float) Math.sin(phase * 0.08) * 0.06f;
                    float scale = 1.0f + Math.max(0, -bounce);
                    int titleSize = Math.max(28, (int) (36 / scale));

                    // Color wave: white -> golden -> white
                    float hueShift = (float) Math.sin(phase * 0.05);
                    int cr = 255;
                    int cg = Math.max(0, Math.min(255, (int) (220 + hueShift * 35)));
                    int cb = Math.max(0, Math.min(255, (int) (100 + hueShift * 155)));

                    g2.setColor(new Color(cr, cg, cb, 255));
                    g2.setFont(gameOverFont.deriveFont((float) titleSize));
                    FontMetrics fmGo = g2.getFontMetrics();
                    String goText = "游戏结束";
                    Rectangle2D boundsTitle = fmGo.getStringBounds(goText, g2);
                    float titleX = cx - (float) boundsTitle.getWidth() / 2f;
                    float titleY = contentCenterY - 60f;
                    g2.drawString(goText, titleX, titleY);

                    // English subtitle: position below title using accurate bounds
                    String goText2 = "GAME OVER";
                    g2.setColor(new Color(255, 255, 255, 140));
                    g2.setFont(gameOverFont.deriveFont((float) titleSize));
                    fmGo = g2.getFontMetrics();
                    Rectangle2D boundsEn = fmGo.getStringBounds(goText2, g2);
                    float enX = cx - (float) boundsEn.getWidth() / 2f;
                    float enY = titleY + (float) boundsTitle.getHeight() + 10f;
                    g2.drawString(goText2, enX, enY);

                    // Decorative divider line under title block
                    if (phase > 10) {
                        float lineAlpha = Math.min(1f, (phase - 10) / 10f);
                        int lineY = (int) (enY + 4);
                        g2.setStroke(new BasicStroke(1.5f));
                        g2.setColor(new Color(255, 255, 255, (int) (80 * lineAlpha)));
                        int lineW = 60;
                        g2.drawLine((int) (cx - lineW / 2), lineY, (int) (cx + lineW / 2), lineY);
                        g2.setStroke(thinStroke);
                    }

                    // Phase 2: Score fades & slides up (animates in ~15 frames after title)
                    if (phase > 20) {
                        float slideIn = Math.min(1.0f, (phase - 20) / 15.0f);
                        float slideAlpha = slideIn;
                        // Start from a safe Y below the title block, slide up to final position
                        float scoreBaseline = enY + (float) boundsEn.getHeight() + 30f;
                        int scoreY = (int) (scoreBaseline + (1 - slideIn) * 20);
                        g2.setColor(new Color(255, 255, 255, (int) (255 * slideAlpha)));
                        g2.setFont(scoreFont);
                        String scoreText = "最终分数: " + score;
                        Rectangle2D sb = g2.getFontMetrics().getStringBounds(scoreText, g2);
                        float scoreX = cx - (float) sb.getWidth() / 2f;
                        g2.drawString(scoreText, scoreX, scoreY);

                        // Phase 3: Level info fades in (~35 frames)
                        if (phase > 35) {
                            float levelIn = Math.min(1.0f, (phase - 35) / 15.0f);
                            float levelBaseline = scoreY + (float) sb.getHeight() + 18f;
                            g2.setColor(new Color(200, 200, 220, (int) (200 * levelIn)));
                            g2.setFont(levelFont);
                            String levelText = "等级: " + level + "  |  行数: " + lines;
                            Rectangle2D lb = g2.getFontMetrics().getStringBounds(levelText, g2);
                            float levelX = cx - (float) lb.getWidth() / 2f;
                            g2.drawString(levelText, levelX, (int) levelBaseline);

                            // Phase 4: Hint pulses continuously (~50 frames)
                            if (phase > 50) {
                                float pulse = 0.4f + 0.6f * ((float) Math.sin(phase * 0.08) * 0.5f + 0.5f);
                                g2.setColor(new Color(200, 200, 220, (int) (180 * pulse)));
                                g2.setFont(startHintFont);
                                String newGameHint = "点击「新游戏」重新开始";
                                Rectangle2D hb = g2.getFontMetrics().getStringBounds(newGameHint, g2);
                                float hintX = cx - (float) hb.getWidth() / 2f;
                                float hintY = levelBaseline + (float) lb.getHeight() + 22;
                                g2.drawString(newGameHint, hintX, (int) hintY);
                            }
                        }
                    }
                }
            }

            // Draw start screen
            if (!gameStarted && !gameOver) {
                // === Rich 4-stop gradient background (centered on game area) ===
                int startScreenCX = 5 + GAME_WIDTH / 2; // game area center
                int midY1 = 30 + GAME_HEIGHT / 4;
                int midY2 = 30 + GAME_HEIGHT / 2;
                int midY3 = 30 + 3 * GAME_HEIGHT / 4;
                GradientPaint bgGrad1 = new GradientPaint(
                        startScreenCX, 0, new Color(160, 20, 50),
                        startScreenCX, midY1, new Color(200, 60, 30));
                GradientPaint bgGrad2 = new GradientPaint(
                        startScreenCX, midY1, new Color(200, 60, 30),
                        startScreenCX, midY2, new Color(220, 160, 20));
                GradientPaint bgGrad3 = new GradientPaint(
                        startScreenCX, midY2, new Color(220, 160, 20),
                        startScreenCX, midY3, new Color(100, 40, 160));
                GradientPaint bgGrad4 = new GradientPaint(
                        startScreenCX, midY3, new Color(100, 40, 160),
                        startScreenCX, GAME_HEIGHT + 30, new Color(20, 40, 140));
                g2.setPaint(bgGrad1); g2.fillRect(0, 0, TOTAL_WIDTH, midY1);
                g2.setPaint(bgGrad2); g2.fillRect(0, midY1, TOTAL_WIDTH, midY2 - midY1);
                g2.setPaint(bgGrad3); g2.fillRect(0, midY2, TOTAL_WIDTH, midY3 - midY2);
                g2.setPaint(bgGrad4); g2.fillRect(0, midY3, TOTAL_WIDTH, GAME_HEIGHT + 30 - midY3);

                // === Animated floating sparkles ===
                for (Sparkle sp : startScreenSparkles) {
                    float alpha = (float)(0.3 + 0.7 * Math.abs(Math.sin(startScreenSparkleTime * 0.02 * sp.speed + sp.phase)));
                    float sy = sp.y - (startScreenSparkleTime * sp.speed * 0.3f) % (GAME_HEIGHT + 40);
                    if (sy < 20) sy += GAME_HEIGHT + 40;
                    float sz = sp.size * (0.6f + 0.4f * alpha);
                    g2.setColor(new Color(255, 240, 180, (int)(alpha * 180)));
                    g2.fillOval((int)(sp.x - sz / 2), (int)(sy - sz / 2), (int)sz + 1, (int)sz + 1);
                    // Tiny glow halo
                    g2.setColor(new Color(255, 220, 100, (int)(alpha * 40)));
                    g2.fillOval((int)(sp.x - sz), (int)(sy - sz), (int)sz * 2 + 1, (int)sz * 2 + 1);
                }

                // === Decorative diamond pattern (Russian folk art style) ===
                g2.setStroke(borderStroke);
                long seed = 99999L;
                for (int i = 0; i < 16; i++) {
                    seed = seed * 6364136223846353L + 1;
                    int dx = (int)(seed % (GAME_WIDTH - 40)) + 25;
                    seed = seed * 6364136223846353L + 1;
                    int dy = (int)(seed % (GAME_HEIGHT - 60)) + 60;
                    seed = seed * 6364136223846353L + 1;
                    int size = (int)(Math.abs(seed) % 24) + 14;
                    int hue = (int)(Math.abs(seed) % 3);
                    Color dc = START_FOLK_COLORS[hue];
                    g2.setColor(new Color(dc.getRed(), dc.getGreen(), dc.getBlue(), 50));
                    g2.setStroke(lightStroke);
                    g2.drawLine(dx - size, dy - size, dx + size, dy + size);
                    g2.drawLine(dx + size, dy - size, dx - size, dy + size);
                    // Diamond outline
                    g2.drawPolygon(new int[]{dx, dx + size, dx, dx - size},
                                   new int[]{dy - size, dy, dy + size, dy}, 4);
                }

                // Radial glow behind title
                int glowCx = 5 + GAME_WIDTH / 2;
                GradientPaint glowGrad = new GradientPaint(
                        glowCx, 80, new Color(255, 220, 80, 80),
                        glowCx, 30, new Color(255, 220, 80, 0));
                g2.setPaint(glowGrad);
                g2.fillOval(glowCx - 220, 30, 440, 110);

                // === Frosted glass options panel ===
                computeStartScreenLayout();
                int cx = getStartScreenCx();
                int optOverlayY = 30 + 85;
                int optOverlayH = 360;
                int panelX = cx - 170, panelW = 340;
                // Panel shadow
                g2.setColor(new Color(0, 0, 0, 50));
                g2.fillRoundRect(panelX + 4, optOverlayY + 4, panelW, optOverlayH, 16, 16);
                // Frosted glass background
                g2.setColor(new Color(0, 0, 0, 70));
                g2.fillRoundRect(panelX, optOverlayY, panelW, optOverlayH, 16, 16);
                // Inner highlight (top edge)
                GradientPaint innerHighlight = new GradientPaint(panelX, optOverlayY, new Color(255, 255, 255, 25),
                                                                 panelX, optOverlayY + 6, new Color(255, 255, 255, 5));
                g2.setPaint(innerHighlight);
                g2.fillRoundRect(panelX + 1, optOverlayY + 1, panelW - 2, 10, 15, 15);
                // Outer border with gradient
                GradientPaint panelBorderGrad = new GradientPaint(panelX, optOverlayY, new Color(255, 220, 100, 100),
                                                                  panelX + panelW, optOverlayY + optOverlayH, new Color(255, 180, 60, 50));
                g2.setPaint(panelBorderGrad);
                g2.setStroke(new BasicStroke(1.8f));
                g2.drawRoundRect(panelX, optOverlayY, panelW, optOverlayH, 16, 16);
                // Corner ornaments
                g2.setColor(new Color(255, 220, 100, 120));
                g2.setStroke(new BasicStroke(2f));
                g2.drawArc(panelX + 2, optOverlayY + 2, 14, 14, 180, 90);
                g2.drawArc(panelX + panelW - 16, optOverlayY + 2, 14, 14, 270, 90);
                g2.drawArc(panelX + 2, optOverlayY + optOverlayH - 16, 14, 14, 90, 90);
                g2.drawArc(panelX + panelW - 16, optOverlayY + optOverlayH - 16, 14, 14, 0, 90);

                FontMetrics fmStart = g2.getFontMetrics();

                // Title with glow effect - positioned higher for better balance
                g2.setColor(new Color(255, 220, 60, 100));
                g2.setFont(startTitleFont);
                String title = "TETRIS PRO";
                Rectangle2D titleB = g2.getFontMetrics().getStringBounds(title, g2);
                int titleX = cx - (int) titleB.getWidth() / 2;
                int titleY = 30 + 40;
                g2.drawString(title, titleX - 2, titleY + 2);  // Glow offset
                g2.setColor(Color.WHITE);
                g2.drawString(title, titleX, titleY);  // Main text

                // Subtitle - positioned below title with clear spacing
                g2.setFont(startSubFont);
                g2.setColor(new Color(255, 255, 255, 180));
                String subtitle = "俄 罗 斯 方 块 专 业 版";
                Rectangle2D subB = g2.getFontMetrics().getStringBounds(subtitle, g2);
                g2.drawString(subtitle, cx - (int) subB.getWidth() / 2, titleY + 35);

                // Divider line - positioned below subtitle
                int divW = 240;
                GradientPaint divLine = new GradientPaint(
                        cx - divW / 2, 0, new Color(255, 255, 255, 60),
                        cx + divW / 2, 0, new Color(255, 255, 255, 15));
                g2.setPaint(divLine);
                g2.setStroke(borderStroke);
                g2.drawLine(cx - divW / 2, titleY + 50, cx + divW / 2, titleY + 50);

                 // --- Difficulty & Theme selection - unified elegant design ---
                int optionStartY = optOverlayY + 30;
                int optionSpacing = 60; // Vertical spacing between options
                
                // === Difficulty Selection ===
                String[] diffLabels = { "简单 (1200ms)", "普通 (800ms)", "困难 (400ms)" };
                String diffLabel = diffLabels[selectedDifficulty];
                
                // Draw difficulty label with icon
                g2.setFont(startSectionFont);
                g2.setColor(Color.WHITE);
                g2.drawString("难度", cx - 133, optionStartY + 18);
                g2.setColor(white200);
                String diffIcon = "◉";
                g2.drawString(diffIcon, cx - 148, optionStartY + 18);

                // Difficulty selector pill with enhanced styling
                int pillW = 150, pillH = 34;
                int pillX = cx - 83, pillY = optionStartY;
                diffPillW = pillW;
                diffPillY = pillY;
                diffNavX = pillX + pillW + 10;
                
                // Pill background with gradient
                GradientPaint diffGrad = new GradientPaint(
                    pillX, pillY, new Color(0x27, 0xAE, 0x60, 60),
                    pillX, pillY + pillH, new Color(0x27, 0xAE, 0x60, 30));
                g2.setPaint(diffGrad);
                g2.fillRoundRect(pillX, pillY, pillW, pillH, 16, 16);

                // Pill border
                g2.setColor(new Color(0x27, 0xAE, 0x60, 120));
                g2.setStroke(borderStroke);
                g2.drawRoundRect(pillX, pillY, pillW, pillH, 16, 16);

                // Difficulty text centered in pill
                g2.setColor(Color.WHITE);
                g2.setFont(startOptFont);
                Rectangle2D diffB = g2.getFontMetrics().getStringBounds(diffLabel, g2);
                g2.drawString(diffLabel, pillX + (pillW - (int)diffB.getWidth()) / 2, pillY + 21);

                // Navigation buttons for difficulty (left/right arrows)
                int navBtnW = 28, navBtnH = 28;
                int navBtnY = pillY + (pillH - navBtnH) / 2;
                boolean diffLeftHover = hoveredBtn == 1;
                Color diffLeftColor = diffLeftHover ? new Color(0x27, 0xAE, 0x60, 200) : new Color(0x27, 0xAE, 0x60, 80);
                g2.setColor(diffLeftColor);
                g2.fillRoundRect(diffNavX, navBtnY, navBtnW, navBtnH, 8, 8);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(diffNavX, navBtnY, navBtnW, navBtnH, 8, 8);
                g2.setColor(Color.WHITE);
                g2.setFont(startSmallFont);
                g2.drawString("◀", diffNavX + navBtnW / 2 - 5, navBtnY + 18);

                boolean diffRightHover = hoveredBtn == 2;
                Color diffRightColor = diffRightHover ? new Color(0x27, 0xAE, 0x60, 200) : new Color(0x27, 0xAE, 0x60, 80);
                int diffRightX = diffNavX + navBtnW + 8;
                g2.setColor(diffRightColor);
                g2.fillRoundRect(diffRightX, navBtnY, navBtnW, navBtnH, 8, 8);
                g2.setColor(Color.WHITE);
                g2.drawRoundRect(diffRightX, navBtnY, navBtnW, navBtnH, 8, 8);
                g2.drawString("▶", diffRightX + navBtnW / 2 - 4, navBtnY + 18);
                
                // === Theme Selection ===
                // Section divider between difficulty and theme
                int divY = optionStartY + optionSpacing - optionSpacing / 2;
                g2.setColor(white20);
                g2.setStroke(normalStroke);
                g2.drawLine(cx - 151, divY, cx + 159, divY);

                String themeLabel = Theme.values()[selectedThemeIdx].name;
                int themeY = optionStartY + optionSpacing;
                int startBtnY = getStartScreenBtnY();
                
                // Draw theme label with icon
                g2.setFont(startSectionFont);
                g2.setColor(Color.WHITE);
                g2.drawString("主题", cx - 133, themeY + 18);
                g2.setColor(white200);
                String themeIcon = "◆";
                g2.drawString(themeIcon, cx - 148, themeY + 18);

                // Theme selector pill with color swatch
                int themePillWidth = 150;
                themePillW = themePillWidth;
                themePillY = themeY;
                themeNavX = pillX + themePillWidth + 10;
                themeRightX = themeNavX + navBtnW + 8;
                
                // Theme pill background with gradient
                Color themeAccent = Theme.values()[selectedThemeIdx].borderColor;
                GradientPaint themeGrad = new GradientPaint(
                    pillX, themeY, new Color(themeAccent.getRed(), themeAccent.getGreen(), themeAccent.getBlue(), 60),
                    pillX, themeY + pillH, new Color(themeAccent.getRed(), themeAccent.getGreen(), themeAccent.getBlue(), 30));
                g2.setPaint(themeGrad);
                g2.fillRoundRect(pillX, themeY, themePillWidth, pillH, 16, 16);
                
                // Theme pill border
                g2.setColor(new Color(themeAccent.getRed(), themeAccent.getGreen(), themeAccent.getBlue(), 120));
                g2.setStroke(borderStroke);
                g2.drawRoundRect(pillX, themeY, themePillWidth, pillH, 16, 16);
                
                // Theme color swatch inside pill
                int swatchSize = 18;
                int swatchX = pillX + 8;
                int swatchY = themeY + (pillH - swatchSize) / 2;
                g2.setColor(Theme.values()[selectedThemeIdx].bgColor);
                g2.fillRoundRect(swatchX, swatchY, swatchSize, swatchSize, 4, 4);
                g2.setColor(white80);
                g2.setStroke(lightStroke);
                g2.drawRoundRect(swatchX, swatchY, swatchSize, swatchSize, 4, 4);
                
                // Theme name text - centered in pill
                g2.setColor(Color.WHITE);
                g2.setFont(startOptFont);
                Rectangle2D themeB = g2.getFontMetrics().getStringBounds(themeLabel, g2);
                int themeTextX = pillX + (themePillWidth - (int) themeB.getWidth()) / 2;
                int themeTextStart = pillX + swatchSize + 10;
                if (themeTextX < themeTextStart) themeTextX = themeTextStart;
                g2.drawString(themeLabel, themeTextX, themeY + 21);
                
                // Navigation buttons for theme (left/right arrows)
                int themeNavBtnY = themeY + (pillH - navBtnH) / 2;
                boolean themeLeftHover = hoveredBtn == 3;
                Color themeLeftColor = themeLeftHover ? new Color(0x8E, 0x44, 0xAD, 200) : new Color(0x8E, 0x44, 0xAD, 80);
                g2.setColor(themeLeftColor);
                g2.fillRoundRect(themeNavX, themeNavBtnY, navBtnW, navBtnH, 8, 8);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(themeNavX, themeNavBtnY, navBtnW, navBtnH, 8, 8);
                g2.setColor(Color.WHITE);
                g2.setFont(startSmallFont);
                g2.drawString("◀", themeNavX + navBtnW / 2 - 5, themeNavBtnY + 18);

                boolean themeRightHover = hoveredBtn == 4;
                Color themeRightColor = themeRightHover ? new Color(0x8E, 0x44, 0xAD, 200) : new Color(0x8E, 0x44, 0xAD, 80);
                g2.setColor(themeRightColor);
                g2.fillRoundRect(themeRightX, themeNavBtnY, navBtnW, navBtnH, 8, 8);
                g2.setColor(Color.WHITE);
                g2.drawRoundRect(themeRightX, themeNavBtnY, navBtnW, navBtnH, 8, 8);
                g2.drawString("▶", themeRightX + navBtnW / 2 - 4, themeNavBtnY + 18);

                // --- Start button - perfectly centered with enhanced effects ---
                int btnW = 170, btnH = 44;
                int btnX = cx - btnW / 2;
                int btnY = startBtnY;
                boolean btnHovered = hoveredBtn == 0;
                Color btnColor = btnHovered ? new Color(0x34, 0x98, 0xDB) : new Color(0x27, 0xAE, 0x60);
                GradientPaint btnGrad = new GradientPaint(
                        btnX, btnY, btnColor.brighter().brighter(),
                        btnX, btnY + btnH, btnColor);
                g2.setPaint(btnGrad);
                g2.fillRoundRect(btnX, btnY, btnW, btnH, 26, 26);

                // Button highlight (top shine)
                g2.setColor(white40);
                g2.fillRoundRect(btnX + 3, btnY + 3, btnW - 6, btnH / 2 - 3, 24, 12);

                // Button border with glow
                g2.setColor(white80);
                g2.setStroke(focusStroke);
                g2.drawRoundRect(btnX, btnY, btnW, btnH, 26, 26);

                // Button text - perfectly centered
                g2.setColor(Color.WHITE);
                g2.setFont(startStartFont);
                FontMetrics fmBtn = g2.getFontMetrics();
                String btnText = "开始游戏";
                Rectangle2D btnB = fmBtn.getStringBounds(btnText, g2);
                g2.drawString(btnText, cx - (int) btnB.getWidth() / 2,
                        btnY + (btnH + (int) btnB.getHeight()) / 2 - 4);

                // Start hint - centered below button
                g2.setFont(startDiffLabelFont);
                g2.setColor(new Color(255, 255, 255, 220));
                String startHint = "按 Enter 或点击按钮开始";
                Rectangle2D hintB = g2.getFontMetrics().getStringBounds(startHint, g2);
                g2.drawString(startHint, cx - (int) hintB.getWidth() / 2, btnY + btnH + 22);

                // --- Exit button - below start button ---
                int exitBtnW = 170, exitBtnH = 44;
                int exitBtnX = cx - exitBtnW / 2;
                int exitBtnY = getStartScreenExitBtnY();
                boolean exitHovered = hoveredBtn == 5;
                Color exitColor = exitHovered ? new Color(0xC0, 0x39, 0x2B) : new Color(0x7F, 0x8C, 0x8D);
                GradientPaint exitGrad = new GradientPaint(
                        exitBtnX, exitBtnY, exitColor.brighter(),
                        exitBtnX, exitBtnY + exitBtnH, exitColor);
                g2.setPaint(exitGrad);
                g2.fillRoundRect(exitBtnX, exitBtnY, exitBtnW, exitBtnH, 20, 20);
                g2.setColor(white50);
                g2.fillRoundRect(exitBtnX + 3, exitBtnY + 3, exitBtnW - 6, exitBtnH / 2 - 3, 18, 10);
                g2.setColor(white60);
                g2.setStroke(borderStroke);
                g2.drawRoundRect(exitBtnX, exitBtnY, exitBtnW, exitBtnH, 20, 20);
                g2.setColor(Color.WHITE);
                g2.setFont(startExitFont.deriveFont(Font.BOLD));
                FontMetrics fmExit = g2.getFontMetrics();
                String exitText = "退出游戏";
                Rectangle2D exitB = fmExit.getStringBounds(exitText, g2);
                g2.drawString(exitText, cx - (int) exitB.getWidth() / 2,
                        exitBtnY + (exitBtnH + (int) exitB.getHeight()) / 2 - 4);

                // Exit hint
                g2.setFont(startHintFont);
                g2.setColor(new Color(255, 255, 255, 160));
                String exitHint = "按 Q 键退出";
                Rectangle2D exitHintB = g2.getFontMetrics().getStringBounds(exitHint, g2);
                g2.drawString(exitHint, cx - (int) exitHintB.getWidth() / 2, exitBtnY + exitBtnH + 16);

               // Controls hint at bottom of game area
                int ctrlY = GAME_HEIGHT + 30 - 15;

                // --- Decorative Tetris pieces in bottom area ---
                int decoStartY = 560;
                int decoEndY = ctrlY - 20;
                long decoSeed = 42L;
                // Define all 7 tetromino shapes (1-unit cells)
                int[][][] decoShapes = {
                    {{0,0,0,0},{1,1,1,1},{0,0,0,0},{0,0,0,0}},  // I
                    {{2,0,0},{2,2,2},{0,0,0}},                    // J
                    {{0,0,3},{3,3,3},{0,0,0}},                    // L
                    {{4,4},{4,4}},                                 // O
                    {{0,5,5},{5,5,0},{0,0,0}},                    // S
                    {{0,6,0},{6,6,6},{0,0,0}},                    // T
                    {{7,7,0},{0,7,7},{0,0,0}},                    // Z
                };
                Color[] decoColors = DECO_COLORS;
                int decoCellSize = 20;
                AffineTransform savedTransform = g2.getTransform();
                for (int i = 0; i < 10; i++) {
                    decoSeed = decoSeed * 6364136223846353L + 1;
                    int shapeIdx = (int)(Math.abs(decoSeed) % 7);
                    decoSeed = decoSeed * 6364136223846353L + 1;
                    int dx = (int)(Math.abs(decoSeed) % (GAME_WIDTH - 60)) + 20;
                    decoSeed = decoSeed * 6364136223846353L + 1;
                    int dy = decoStartY + (int)(Math.abs(decoSeed) % (decoEndY - decoStartY));
                    decoSeed = decoSeed * 6364136223846353L + 1;
                    float rot = (Math.abs(decoSeed) % 360) * (float)Math.PI / 180f;

                    int[][] shape = decoShapes[shapeIdx];
                    Color dc = decoColors[shapeIdx];
                    g2.setColor(dc);

                    // Find bounding box of shape
                    int minR = shape.length, maxR = 0, minC = shape[0].length, maxC = 0;
                    for (int r = 0; r < shape.length; r++) {
                        for (int c = 0; c < shape[r].length; c++) {
                            if (shape[r][c] != 0) {
                                minR = Math.min(minR, r); maxR = Math.max(maxR, r);
                                minC = Math.min(minC, c); maxC = Math.max(maxC, c);
                            }
                        }
                    }
                    int sW = (maxC - minC + 1) * decoCellSize;
                    int sH = (maxR - minR + 1) * decoCellSize;

                    g2.translate(dx + sW / 2, dy + sH / 2);
                    g2.rotate(rot);
                    for (int r = minR; r <= maxR; r++) {
                        for (int c = minC; c <= maxC; c++) {
                            if (shape[r][c] != 0) {
                                int px = (c - minC) * decoCellSize - sW / 2;
                                int py = (r - minR) * decoCellSize - sH / 2;
                                g2.fillRoundRect(px, py, decoCellSize - 2, decoCellSize - 2, 3, 3);
                            }
                        }
                    }
                    g2.setTransform(savedTransform);
                }
                g2.setTransform(savedTransform);
                g2.setFont(sideInfoLabelFont);
                String controls1 = "← →移动 ↑旋转 ↓加速 空格硬降 G幽灵";
                String controls2 = "C暂存 M音乐 N新游戏 P切歌 T主题 Q返回";
                Rectangle2D ctrlB1 = g2.getFontMetrics().getStringBounds(controls1, g2);
                Rectangle2D ctrlB2 = g2.getFontMetrics().getStringBounds(controls2, g2);
                float w1 = (int) ctrlB1.getWidth(), w2 = (int) ctrlB2.getWidth();
                int tx1 = cx - (int) w1 / 2, tx2 = cx - (int) w2 / 2;
                // Semi-transparent background bar for controls text
                int bgPad = 8, bgH = 22;
                g2.setColor(white100bg);
                g2.fillRoundRect(tx1 - bgPad, ctrlY - 22 - bgH, (int)w1 + bgPad * 2, bgH, 3, 3);
                g2.fillRoundRect(tx2 - bgPad, ctrlY - bgH, (int)w2 + bgPad * 2, bgH, 3, 3);
                g2.setColor(new Color(255, 255, 255, 230));
                g2.drawString(controls1, tx1, ctrlY - 22);
                g2.drawString(controls2, tx2, ctrlY);

                // === Start screen side panel: falling tetromino decoration ===
                int spStartX = 5 + GAME_WIDTH + 15;
                int spStartY = 30;
                int spBoxW = SIDE_PANEL_WIDTH - 10;
                g2.setColor(white25bg);
                g2.fillRoundRect(spStartX + 3, spStartY + 3, spBoxW + 3, GAME_HEIGHT + 30, 8, 8);
                // Theme-adapted panel background
                g2.setPaint(new GradientPaint(
                        spStartX, spStartY, new Color(theme.panelBg.getRed(), theme.panelBg.getGreen(), theme.panelBg.getBlue(), 100),
                        spStartX + spBoxW, spStartY + GAME_HEIGHT + 30, new Color(theme.darkerBg.getRed(), theme.darkerBg.getGreen(), theme.darkerBg.getBlue(), 80)));
                g2.fillRoundRect(spStartX, spStartY, spBoxW, GAME_HEIGHT + 30, 8, 8);
                // Theme-adapted border
                g2.setColor(new Color(theme.borderColor.getRed(), theme.borderColor.getGreen(), theme.borderColor.getBlue(), 60));
                g2.setStroke(borderStroke);
                g2.drawRoundRect(spStartX, spStartY, spBoxW, GAME_HEIGHT + 30, 8, 8);
                drawFallingTetrominoAnim(g2, spStartX, spStartY, spBoxW, GAME_HEIGHT + 30);
            }

        }

        private void drawBlock(Graphics2D g2, int x, int y, int type, boolean isCurrent) {
            Color base = BLOCK_COLORS[type];
            Theme theme = currentTheme;

            // Theme-specific block rendering
            if (theme == Theme.NEON) {
                // Neon: glowing hollow blocks with vibrant outline
                if (isCurrent) {
                    // Current piece has strong glow — pre-cached alpha
                    g2.setColor(BLOCK_COLOR_ALPHA[type][2]); // alpha=30
                    g2.fillRoundRect(x - 2, y - 2, BLOCK_SIZE + 4, BLOCK_SIZE + 4, 8, 8);
                }

                // Hollow neon block with bright outline
                g2.setStroke(solidStroke25);
                g2.setColor(base);
                g2.drawRoundRect(x + 2, y + 2, BLOCK_SIZE - 4, BLOCK_SIZE - 4, 6, 6);

                // Inner glow for depth — pre-cached alpha
                g2.setStroke(normalStroke);
                g2.setColor(BLOCK_COLOR_ALPHA[type][5]); // alpha=120
                g2.drawRoundRect(x + 5, y + 5, BLOCK_SIZE - 10, BLOCK_SIZE - 10, 4, 4);

                // Subtle fill — pre-cached alpha
                g2.setColor(BLOCK_COLOR_ALPHA[type][0]); // alpha=15
                g2.fillRoundRect(x + 3, y + 3, BLOCK_SIZE - 6, BLOCK_SIZE - 6, 5, 5);
                
            } else if (theme == Theme.DARK) {
                // Dark: flat blocks with subtle gradient
                Color dk = BLOCK_DARKER[type];
                GradientManager gm = new GradientManager(dk, dk.brighter().brighter());
                gm.draw(g2, x, y, BLOCK_SIZE, BLOCK_SIZE, isCurrent);
                
                // Minimal highlight on top edge
                g2.setColor(white20);
                g2.setStroke(normalStroke);
                g2.drawLine(x + 3, y + 2, x + BLOCK_SIZE - 3, y + 2);
                
            } else if (theme == Theme.SUNSET) {
                // Sunset: warm gradient blocks with soft edges — use pre-cached warm colors
                Color warmTop = SUNSET_WARM_TOP[type];
                Color warmBottom = SUNSET_WARM_BOTTOM[type];

                GradientPaint sunsetGrad = new GradientPaint(
                    x, y, warmTop,
                    x, y + BLOCK_SIZE, warmBottom);
                g2.setPaint(sunsetGrad);
                g2.fillRoundRect(x + 1, y + 1, BLOCK_SIZE - 2, BLOCK_SIZE - 2, 6, 6);

                // Soft border — use pre-cached brighter
                g2.setColor(SUNSET_WARM_TOP_BRIGHTER[type]);
                g2.setStroke(lightStroke);
                g2.drawRoundRect(x + 1, y + 1, BLOCK_SIZE - 2, BLOCK_SIZE - 2, 6, 6);

                // Warm inner glow (this is a fixed color, not type-dependent)
                g2.setColor(new Color(255, 200, 150, 30));
                g2.fillRoundRect(x + 4, y + 4, BLOCK_SIZE - 8, BLOCK_SIZE - 8, 4, 4);
                
            } else {
                // Classic: 3D beveled blocks
                GradientManager gm = new GradientManager(base, BLOCK_BRIGHTER2[type]);
                gm.draw(g2, x, y, BLOCK_SIZE, BLOCK_SIZE, isCurrent);

                // Center glossy highlight — use pre-cached alpha
                GradientPaint centerGlow = new GradientPaint(
                        x + BLOCK_SIZE * 0.3f, y + BLOCK_SIZE * 0.3f,
                        BLOCK_COLOR_ALPHA[type][4], // alpha=80
                        x + BLOCK_SIZE * 0.5f, y + BLOCK_SIZE * 0.5f,
                        BLOCK_COLOR_ALPHA[type][1]); // alpha=20
                g2.setPaint(centerGlow);
                g2.fillRoundRect(x + 6, y + 6, BLOCK_SIZE - 12, BLOCK_SIZE - 12, 4, 4);
            }
        }

        private void drawSidePanel(Graphics2D g2, int startX, int startY) {
            Theme theme = currentTheme;
            int y = startY;
            int boxW = SIDE_PANEL_WIDTH - 10;
            FontMetrics fm = g2.getFontMetrics();

            // Side panel outer shadow
            g2.setColor(white25bg);
            g2.fillRoundRect(startX + 3, startY + 3, boxW + 3, GAME_HEIGHT + 30, 8, 8);

            // Side panel background with enhanced gradient
            g2.setPaint(new GradientPaint(
                    startX, startY, theme.panelBg,
                    startX + boxW, startY + GAME_HEIGHT + 30, theme.darkerBg));
            g2.fillRoundRect(startX, startY, boxW, GAME_HEIGHT + 30, 8, 8);

            // Side panel border with enhanced visibility
            g2.setColor(white50);
            g2.setStroke(borderStroke);
            g2.drawRoundRect(startX, startY, boxW, GAME_HEIGHT + 30, 8, 8);

            // --- Theme indicator ---
            int dotY = y + 10;
            String themeLabel = currentTheme.name;
            g2.setFont(themeDescFont);
            // Small colored circle with glow
            g2.setColor(new Color(currentTheme.borderColor.getRed(), currentTheme.borderColor.getGreen(), currentTheme.borderColor.getBlue(), 50));
            // (still allocates — but this runs once per frame, acceptable)
            g2.fillOval(startX + 6, dotY - 7, 12, 12);
            g2.setColor(currentTheme.borderColor);
            g2.fillOval(startX + 8, dotY - 5, 8, 8);
            g2.setColor(theme.panelBorder);
            g2.setStroke(mediumStroke);
            g2.drawOval(startX + 8, dotY - 5, 8, 8);
            g2.setColor(theme.panelBorder);
            g2.drawString(themeLabel, startX + 22, dotY + 4);
            y += 30;

            // --- Next piece ---
            drawSection(g2, startX, y, "下一个", boxW, theme);
            y += 26;
            for (int i = 0; i < MAX_NEXT; i++) {
                if (nextQueue[i] >= 0) {
                    int previewY = y + i * (PREVIEW_BLOCK * 2 + 18);
                    drawPreviewPiece(g2, startX, previewY, nextQueue[i], PREVIEW_BLOCK);
                }
            }
            // Container outline around all next pieces
            if (MAX_NEXT > 0 && nextQueue.length > 0) {
                Color outlineColor = (theme == Theme.CLASSIC)
                        ? theme.panelBorder
                        : new Color(255, 255, 255, 45);
                g2.setColor(outlineColor);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(startX + 5, y, boxW - 10, MAX_NEXT * (PREVIEW_BLOCK * 2 + 18), 6, 6);
            }
            y += MAX_NEXT * (PREVIEW_BLOCK * 2 + 18) + 16;

            // --- Hold ---
            drawSection(g2, startX, y, "Hold (C)", boxW, theme);
            y += 26;

            // Container outline for Hold piece
            Color holdOutlineColor = (theme == Theme.CLASSIC)
                    ? theme.panelBorder
                    : new Color(255, 255, 255, 45);
            if (holdType >= 0) {
                drawPreviewPiece(g2, startX, y, holdType, PREVIEW_BLOCK);
                g2.setColor(holdOutlineColor);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(startX + 5, y, boxW - 10, PREVIEW_BLOCK * 4 + 20, 6, 6);
            } else if (holdUsed) {
                g2.setColor(new Color(180, 190, 210));
                g2.setFont(sideStatLabelFont);
                g2.drawString("已使用", startX + 10, y + 80);
                g2.setColor(holdOutlineColor);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(startX + 5, y, boxW - 10, 36, 6, 6);
            }
            y += 80;

             // --- Divider between Hold area and Stats ---
            Color dividerColor = new Color(255, 255, 255, 50);
            g2.setColor(dividerColor);
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(startX + 6, y, startX + boxW - 6, y);
            g2.setStroke(normalStroke);
            y += 14;

            // --- Stats section title ---
            //String statsTitleText = "统计";
            //g2.setFont(chineseFont(Font.BOLD, 18));
            //g2.setColor(Color.WHITE);
            //g2.drawString(statsTitleText, startX + 8, y);
            drawSection(g2, startX, y, "统计", boxW, theme);
            y += 46;

            // Use simple label-value layout with consistent alignment
            Font statLabelFont = sideStatLabelFont;
            Font statValueFont = sideStatValueFont;
            int labelX = startX + 8;
            int valueX = startX + boxW - 8;
            int lineSpacing = 22;
            
             // Ensure good contrast for all themes
            // CLASSIC: dark labels/values on light panel; dark themes: light labels, white values
            Color labelColor, valueColor;
            if (theme == Theme.CLASSIC) {
                labelColor = classicLabelColor;
                valueColor = classicValueColor;
            } else {
                labelColor = new Color(180, 190, 210);
                valueColor = Color.WHITE;
            }
            
            // Score
            g2.setColor(labelColor);
            g2.setFont(statLabelFont);
            g2.drawString("分数", labelX, y);
            g2.setColor(valueColor);
            g2.setFont(statValueFont);
            fm = g2.getFontMetrics();
            String scoreStr = String.valueOf(score);
            int scoreWidth = fm.stringWidth(scoreStr);
            g2.drawString(scoreStr, valueX - scoreWidth, y);
            y += lineSpacing;
            
            // Level
            g2.setColor(labelColor);
            g2.setFont(statLabelFont);
            g2.drawString("等级", labelX, y);
            g2.setColor(valueColor);
            g2.setFont(statValueFont);
            String levelStr = String.valueOf(level);
            g2.drawString(levelStr, valueX - fm.stringWidth(levelStr), y);
            y += lineSpacing;
            
            // Lines
            g2.setColor(labelColor);
            g2.setFont(statLabelFont);
            g2.drawString("行数", labelX, y);
            g2.setColor(valueColor);
            g2.setFont(statValueFont);
            String linesStr = String.valueOf(lines);
            g2.drawString(linesStr, valueX - fm.stringWidth(linesStr), y);
            y += lineSpacing;
            
            // Time (cached to avoid String.format per frame)
            long elapsed = gameStarted && !paused && !gameOver ?
                    (System.currentTimeMillis() - startTime) / 1000 : 0;
            int secs = (int) (elapsed % 60);
            int mins = (int) (elapsed / 60);
            long curSec = elapsed;
            if (curSec != lastTimeSec) {
                lastTimeSec = curSec;
                // Manual format: avoid String.format allocation
                char s0 = (char)('0' + secs / 10);
                char s1 = (char)('0' + secs % 10);
                char m0 = (char)('0' + mins / 10);
                char m1 = (char)('0' + mins % 10);
                cachedTimeStr = m0 + "" + m1 + ":" + s0 + s1;
            }
            String timeStr = cachedTimeStr;
            
            g2.setColor(labelColor);
            g2.setFont(statLabelFont);
            g2.drawString("时间", labelX, y);
            g2.setColor(valueColor);
            g2.setFont(statValueFont);
            g2.drawString(timeStr, valueX - fm.stringWidth(timeStr), y);
            y += lineSpacing;
            
            // Speed
            g2.setColor(labelColor);
            g2.setFont(statLabelFont);
            g2.drawString("速度", labelX, y);
            g2.setColor(valueColor);
            g2.setFont(statValueFont);
            String speedStr = dropInterval + "ms";
            g2.drawString(speedStr, valueX - fm.stringWidth(speedStr), y);
            y += lineSpacing;
            
            // Ghost piece status
            g2.setColor(labelColor);
            g2.setFont(statLabelFont);
            g2.drawString("幽灵", labelX, y);
            g2.setColor(valueColor);
            g2.setFont(statValueFont);
            String ghostStr = showGhost ? "开启" : "关闭";
            g2.drawString(ghostStr, valueX - fm.stringWidth(ghostStr), y);
            y += lineSpacing;
            
            // Sound status
            g2.setColor(labelColor);
            g2.setFont(statLabelFont);
            g2.drawString("音效", labelX, y);
            g2.setColor(valueColor);
            g2.setFont(statValueFont);
            String soundStr = soundEnabled ? "开启" : "关闭";
            g2.drawString(soundStr, valueX - fm.stringWidth(soundStr), y);
            y += lineSpacing;

            // Music status
            g2.setColor(labelColor);
            g2.setFont(statLabelFont);
            g2.drawString("音乐", labelX, y);
            g2.setColor(valueColor);
            g2.setFont(statValueFont);
            String musicStr = bgMusicEnabled ? "开启" : "关闭";
            g2.drawString(musicStr, valueX - fm.stringWidth(musicStr), y);

            // Loop playback status
            y += lineSpacing;
            g2.setColor(labelColor);
            g2.setFont(statLabelFont);
            g2.drawString("循环", labelX, y);
            g2.setColor(valueColor);
            g2.setFont(statValueFont);
            fm = g2.getFontMetrics();
            String loopStr = bgMusicLoop ? "开启" : "关闭";
            g2.drawString(loopStr, valueX - fm.stringWidth(loopStr), y);

            // Current music name
            if (bgMusicEnabled) {
                String musicName = soundManager.getCurrentMusicName();
                if (musicName != null && !musicName.isEmpty()) {
                    y += lineSpacing;
                    if (musicName.toLowerCase().endsWith(".mp3")) {
                        musicName = musicName.substring(0, musicName.length() - 4);
                    }
                    g2.setColor(labelColor);
                    g2.setFont(statLabelFont);
                    g2.drawString("正在播放", labelX, y);
                    y += lineSpacing;
                    g2.setColor(valueColor);
                    g2.setFont(statValueFont);
                    fm = g2.getFontMetrics();
                    g2.drawString(musicName, valueX - fm.stringWidth(musicName), y);
                }
            }

            y += 24; // extra bottom padding for stats section

            // === Start screen: falling tetromino decoration ===
            if (!gameStarted && !gameOver) {
                drawFallingTetrominoAnim(g2, startX, startY, boxW, GAME_HEIGHT + 30);
            }
        }

        private void drawFallingTetrominoAnim(Graphics2D g2, int panelX, int panelY, int panelW, int panelH) {
            // fallTetTime is incremented by startScreenTimer (30ms interval)
            Color[] tetColors = {
                new Color(0x00, 0xCC, 0xFF, 180),  // I - cyan
                new Color(0x00, 0x50, 0xFF, 180),  // J - blue
                new Color(0xFF, 0xA0, 0x00, 180),  // L - orange
                new Color(0xFF, 0xDC, 0x00, 180),  // O - yellow
                new Color(0x00, 0xCC, 0x44, 180),  // S - green
                new Color(0x90, 0x00, 0xFF, 180),  // T - purple
                new Color(0xFF, 0x30, 0x30, 180),  // Z - red
            };
            int cellSize = 12;

            // Create or recycle falling pieces
            for (int i = 0; i < 5; i++) {
                float fx = panelX + 20 + (float) i * (panelW - 40) / 4f;
                float phase = (float) i * 1.7f;
                float t = fallTetTime * 0.03f;
                float speed = 0.8f + (float) Math.abs(Math.sin(phase * 3.1f)) * 0.7f;
                float yOff = ((t * speed * 120f + phase * 80f) % (panelH + 120f)) - 60f;
                int type = (i + fallTetTime / 120) % 7;
                int[][] shape = SHAPES[type];

                // Compute bounding box of shape
                int minR = shape.length, maxR = 0, minC = shape[0].length, maxC = 0;
                for (int r = 0; r < shape.length; r++) {
                    for (int c = 0; c < shape[r].length; c++) {
                        if (shape[r][c] != 0) {
                            minR = Math.min(minR, r); maxR = Math.max(maxR, r);
                            minC = Math.min(minC, c); maxC = Math.max(maxC, c);
                        }
                    }
                }
                int sW = (maxC - minC + 1) * cellSize;
                int sH = (maxR - minR + 1) * cellSize;

                // Alpha based on position: fade in at top, fade out at bottom
                float alpha = 1.0f;
                if (yOff < 40) alpha = yOff / 40f;
                else if (yOff > panelH - 20) alpha = (panelH - yOff) / 20f;
                alpha = Math.max(0, Math.min(1, alpha));

                // Subtle rotation oscillation
                float rotAngle = (float) Math.sin(fallTetTime * 0.02 + phase) * 0.15f;

                Color col = tetColors[type];
                int cr = col.getRed(), cg = col.getGreen(), cb = col.getBlue();

                g2.setColor(new Color(cr, cg, cb, (int) (alpha * 160)));

                // Draw each block of the tetromino with slight rotation
                for (int r = minR; r <= maxR; r++) {
                    for (int c = minC; c <= maxC; c++) {
                        if (shape[r][c] != 0) {
                            float bx = fx + (c - minC) * cellSize - sW / 2f;
                            float by = panelY + yOff - sH / 2f + (r - minR) * cellSize;
                            // Apply slight rotation around center
                            float cx = fx - sW / 2f;
                            float cy = panelY + yOff - sH / 2f + (minR + maxR) / 2f * cellSize;
                            float dx = bx - cx, dy = by - cy;
                            float cosA = (float) Math.cos(rotAngle);
                            float sinA = (float) Math.sin(rotAngle);
                            float rx = cx + dx * cosA - dy * sinA;
                            float ry = cy + dx * sinA + dy * cosA;
                            int sz = cellSize - 1;
                            g2.fillRoundRect((int) rx, (int) ry, sz, sz, 2, 2);
                        }
                    }
                }
            }

            // Draw decorative label at bottom
            g2.setColor(new Color(200, 200, 220, 60));
            g2.setFont(sideStatLabelFont);
            String label = "TETRIS PRO";
            int lx = panelX + panelW - 8 - g2.getFontMetrics().stringWidth(label);
            g2.drawString(label, lx, panelY + panelH - 8);
        }

        private void drawSection(Graphics2D g2, int x, int y, String title, int w, Theme theme) {
            // Section background with enhanced rounded box
            g2.setColor(sectionBgColor);
            g2.fillRoundRect(x, y, w, 24, 6, 6);
            // Section border with better visibility
            Color sectionBorderColor = (theme == Theme.CLASSIC)
                    ? new Color(0x95A5A6)
                    : new Color(255, 255, 255, 70);
            g2.setColor(sectionBorderColor);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(x, y, w, 24, 6, 6);
            // Section title with improved font
            g2.setColor(Color.WHITE);
            g2.setFont(sideSectionFont);
            g2.drawString(title, x + 10, y + 16);
        }

        private void drawPreviewPiece(Graphics2D g2, int x, int y, int type, int blockSize) {
            int[][] shape = SHAPES[type];
            int minR = shape.length, maxR = 0, minC = shape[0].length, maxC = 0;
            for (int r = 0; r < shape.length; r++)
                for (int c = 0; c < shape[r].length; c++)
                    if (shape[r][c] != 0) {
                        minR = Math.min(minR, r); maxR = Math.max(maxR, r);
                        minC = Math.min(minC, c); maxC = Math.max(maxC, c);
                    }
            int pieceW = (maxC - minC + 1) * blockSize;
            int pieceH = (maxR - minR + 1) * blockSize;
            // Center piece horizontally within panel area
            int containerW = SIDE_PANEL_WIDTH - 40;
            int ox = x + (containerW - pieceW) / 2 - minC * blockSize;
            int oy = y + 8 - minR * blockSize;

            // Background container with theme-aware styling
            Theme theme = currentTheme;
            if (theme == Theme.NEON) {
                g2.setColor(neonPreviewBg);
                g2.fillRoundRect(x + 5, y, containerW, Math.max(pieceH + 16, 36), 6, 6);
                g2.setColor(neonPreviewBorder);
                g2.setStroke(normalStroke);
                g2.drawRoundRect(x + 5, y, containerW, Math.max(pieceH + 16, 36), 6, 6);
            } else if (theme == Theme.DARK) {
                g2.setColor(darkPreviewBg);
                g2.fillRoundRect(x + 5, y, containerW, Math.max(pieceH + 16, 36), 6, 6);
                g2.setColor(white18);
                g2.setStroke(normalStroke);
                g2.drawRoundRect(x + 5, y, containerW, Math.max(pieceH + 16, 36), 6, 6);
            } else if (theme == Theme.SUNSET) {
                g2.setColor(sunsetPreviewBg);
                g2.fillRoundRect(x + 5, y, containerW, Math.max(pieceH + 16, 36), 6, 6);
                g2.setColor(sunsetPreviewBorder);
                g2.setStroke(normalStroke);
                g2.drawRoundRect(x + 5, y, containerW, Math.max(pieceH + 16, 36), 6, 6);
            } else {
                g2.setColor(classicPreviewBg);
                g2.fillRoundRect(x + 5, y, containerW, Math.max(pieceH + 16, 36), 6, 6);
                g2.setColor(white15);
                g2.setStroke(normalStroke);
                g2.drawRoundRect(x + 5, y, containerW, Math.max(pieceH + 16, 36), 6, 6);
            }

            for (int r = 0; r < shape.length; r++) {
                for (int c = 0; c < shape[r].length; c++) {
                    if (shape[r][c] != 0) {
                        Color base = BLOCK_COLORS[type];
                        
                        // Theme-specific preview rendering
                        if (theme == Theme.NEON) {
                            // Neon: hollow outline
                            g2.setStroke(focusStroke);
                            g2.setColor(base);
                            g2.drawRoundRect(ox + c * blockSize + 2, oy + r * blockSize + 2,
                                    blockSize - 4, blockSize - 4, 5, 5);
                            g2.setColor(BLOCK_COLOR_ALPHA[type][1]); // alpha=20
                            g2.fillRoundRect(ox + c * blockSize + 3, oy + r * blockSize + 3,
                                    blockSize - 6, blockSize - 6, 4, 4);
                        } else if (theme == Theme.DARK) {
                            // Dark: flat with gradient
                            Color dk = BLOCK_DARKER[type];
                            GradientManager gm = new GradientManager(dk, dk.brighter().brighter());
                            gm.draw(g2, ox + c * blockSize, oy + r * blockSize, blockSize, blockSize, false);
                        } else if (theme == Theme.SUNSET) {
                            // Sunset: warm gradient — use pre-cached warm colors
                            Color warmTop = SUNSET_WARM_TOP[type];
                            Color warmBottom = SUNSET_WARM_BOTTOM[type];
                            GradientPaint sunsetGrad = new GradientPaint(
                                ox + c * blockSize, oy + r * blockSize, warmTop,
                                ox + c * blockSize, oy + r * blockSize + blockSize, warmBottom);
                            g2.setPaint(sunsetGrad);
                            g2.fillRoundRect(ox + c * blockSize + 1, oy + r * blockSize + 1,
                                    blockSize - 2, blockSize - 2, 5, 5);
                            g2.setColor(SUNSET_WARM_TOP_BRIGHTER[type]);
                            g2.setStroke(normalStroke);
                            g2.drawRoundRect(ox + c * blockSize + 1, oy + r * blockSize + 1,
                                    blockSize - 2, blockSize - 2, 5, 5);
                        } else {
                            // Classic: standard 3D
                            GradientManager gm = new GradientManager(base, BLOCK_BRIGHTER2[type]);
                            gm.draw(g2, ox + c * blockSize, oy + r * blockSize, blockSize, blockSize, false);
                            g2.setColor(white25);
                            g2.setStroke(mediumStroke);
                            g2.drawRoundRect(ox + c * blockSize + 2, oy + r * blockSize + 2,
                                    blockSize - 4, blockSize - 4, 4, 4);
                        }
                    }
                }
            }
        }
    }

    // ==================== GRADIENT MANAGER ====================
    private static class GradientManager {
        private final Color base;
        private final Color brighter2; // pre-computed brighter().brighter()
        private final Color alpha120; // base with alpha=120
        private final Color alpha50;  // base with alpha=50
        private static final BasicStroke SOLID_STROKE_18 = new BasicStroke(1.8f);
        private static final BasicStroke NORMAL_STROKE = new BasicStroke(1f);
        private static final Color WHITE_60BG = new Color(0, 0, 0, 60);
        private static final Color WHITE_ALPHA_80 = new Color(255, 255, 255, 80);
        private static final Color WHITE_ALPHA_50 = new Color(255, 255, 255, 50);
        GradientManager(Color base, Color brighter2) {
            this.base = base;
            this.brighter2 = brighter2;
            int r = base.getRed(), g = base.getGreen(), b = base.getBlue();
            this.alpha120 = new Color(r, g, b, 120);
            this.alpha50 = new Color(r, g, b, 50);
        }
        void draw(Graphics2D g2, int x, int y, int w, int h, boolean isCurrent) {
            int pad = isCurrent ? 1 : 2;
            int radius = isCurrent ? 8 : 6;
            int bw = w - pad * 2;
            int bh = h - pad * 2;

            // Outer shadow (offset by 2px)
            g2.setColor(WHITE_60BG);
            g2.fillRoundRect(x + pad + 2, y + pad + 3, bw, bh, radius, radius);

            // Main body with gradient (pre-computed brighter2)
            GradientPaint gp = new GradientPaint(
                    x + pad, y + pad, brighter2,
                    x + pad, y + pad + bh, base);
            g2.setPaint(gp);
            g2.fillRoundRect(x + pad, y + pad, bw, bh, radius, radius);

            // Top-left glossy highlight (curved edge)
            g2.setColor(alpha120);
            g2.setStroke(SOLID_STROKE_18);
            g2.drawRoundRect(x + pad + 2, y + pad + 2, bw - 4, bh / 2 - 1, radius, radius / 2);

            // Bottom-right inner shadow
            g2.setColor(alpha50);
            g2.setStroke(NORMAL_STROKE);
            g2.drawRoundRect(x + pad + 1, y + pad + bh - 4, bw - 2, 4, 2, 2);

            // Left edge specular highlight
            g2.setColor(isCurrent ? WHITE_ALPHA_80 : WHITE_ALPHA_50);
            g2.setStroke(NORMAL_STROKE);
            g2.drawLine(x + pad + 3, y + pad + 6, x + pad + 3, y + pad + bh - 6);
        }
    }

    // ==================== PARTICLE ====================
    private static class Particle {
        int x, y, vx, vy, life, maxLife;
        byte r, g, b;
        float size;
        // Pre-computed opaque RGB colors to avoid per-frame allocation
        private transient Color opaqueColor;
        Particle(int x, int y, int cr, int cg, int cb) {
            this.x = x; this.y = y;
            this.vx = (int) (Math.random() * 12 - 6);
            this.vy = (int) (Math.random() * -10 - 3);
            this.r = (byte) cr; this.g = (byte) cg; this.b = (byte) cb;
            this.life = 30 + (int) (Math.random() * 30);
            this.maxLife = this.life;
            this.size = 2 + (float)(Math.random() * 3);
        }
        boolean update() {
            x += vx; y += vy;
            vy += 0.25;
            vx *= 0.99;
            life--;
            return life > 0;
        }
        void draw(Graphics2D g2) {
            float alpha = (float) life / maxLife;
            float currentSize = size * alpha;
            if (opaqueColor == null) {
                opaqueColor = new Color(r & 0xFF, g & 0xFF, b & 0xFF);
            }
            g2.setColor(opaqueColor);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 0.78f));
            g2.fillOval(x - (int) currentSize / 2, y - (int) currentSize / 2, (int) currentSize, (int) currentSize);
            // Glow effect
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 0.31f));
            g2.fillOval(x - (int) currentSize, y - (int) currentSize, (int) (currentSize * 2), (int) (currentSize * 2));
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        }
    }

    // ==================== SOUND MANAGER ====================
    // Whether JLayer library is available for MP3 playback
    private static final boolean JLAYER_AVAILABLE;
    static {
        boolean available = false;
        try {
            Class.forName("javazoom.jl.player.Player");
            available = true;
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            available = false;
        }
        JLAYER_AVAILABLE = available;
    }

    private static class SoundManager {
        private static final int SAMPLE_RATE = 22050;
        private SourceDataLine sfxLine;   // Dedicated line for sound effects
        // Pre-computed SFX byte buffers to avoid per-call allocation
        private final java.util.Map<String, byte[]> sfxCache = new java.util.HashMap<>();
        // Lock for thread-safe cache population
        private final Object sfxCacheLock = new Object();

        // Convert double samples to 16-bit PCM bytes (reusable buffer)
        private static byte[] toBytes(double[] samples) {
            int size = samples.length * 2;
            byte[] buffer = new byte[size];
            for (int i = 0; i < samples.length; i++) {
                int s = (int) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, samples[i] * Short.MAX_VALUE));
                buffer[i * 2] = (byte) (s & 0xFF);
                buffer[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
            }
            return buffer;
        }

        // Use a bounded thread pool for sound effects to avoid unbounded thread creation
        private java.util.concurrent.ExecutorService soundExecutor =
            java.util.concurrent.Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "sound-effect");
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            });

        // Chinese pentatonic scale (Gong, Shang, Jue, Zhi, Yu) in C major relative
        // C D E G A (do re mi sol la)
        private static final double[] PENTATONIC = {
            261.63, 293.66, 329.63, 392.00, 440.00,  // octave 4
            523.25, 587.33, 659.25, 783.99, 880.00,  // octave 5
            1046.50, 1174.66, 1318.51,                  // octave 6
        };

   // Background music state (using JLayer for MP3 playback)
    private Thread bgMusicThread = null;
    private volatile boolean bgMusicRunning = false;
    private volatile boolean bgMusicPaused = false;
    private final Object musicLock = new Object();
    private final java.util.concurrent.atomic.AtomicReference<InputStream> bgMusicStreamRef = new java.util.concurrent.atomic.AtomicReference<>(null);
    private List<File> bgMusicFiles = new ArrayList<>();
    private int currentMusicIndex = 0;
    private volatile int bgMusicGen = 0;
    private volatile String musicDir = "music";
    private volatile boolean loopMusic = true;
    volatile String currentMusicName = "";
    volatile boolean bgMusicFinished = false;

        void setMusicDirectory(String dir) { this.musicDir = dir; }
        void setLoopMusic(boolean loop) { this.loopMusic = loop; }
        String getCurrentMusicName() { return currentMusicName; }

        // Scan music directory for MP3 files
        private List<File> scanMusicFiles() {
            List<File> files = new ArrayList<>();
            File musicDir = new File(this.musicDir);
            if (!musicDir.exists() || !musicDir.isDirectory()) return files;
            // Prevent directory traversal
            try {
                String canonicalPath = musicDir.getCanonicalPath();
                File[] dirFiles = musicDir.listFiles((dir, name) -> {
                    if (!name.toLowerCase().endsWith(".mp3")) return false;
                    File f = new File(dir, name);
                    try {
                        String fCanonical = f.getCanonicalPath();
                        return fCanonical.startsWith(canonicalPath + java.io.File.separator) || fCanonical.equals(canonicalPath);
                    } catch (IOException e) {
                        return false;
                    }
                });
                if (dirFiles != null) {
                    for (File f : dirFiles) {
                        if (f.isFile() && f.canRead()) {
                            files.add(f);
                        }
                    }
                }
            } catch (IOException ignored) {}
            return files;
        }

        // Get next music file in playlist
        private File getNextMusicFile() {
            if (bgMusicFiles.isEmpty()) return null;
            currentMusicIndex = (currentMusicIndex + 1) % bgMusicFiles.size();
            return bgMusicFiles.get(currentMusicIndex);
        }

        // Get previous music file in playlist
        private File getPrevMusicFile() {
            if (bgMusicFiles.isEmpty()) return null;
            currentMusicIndex = (currentMusicIndex - 1 + bgMusicFiles.size()) % bgMusicFiles.size();
            return bgMusicFiles.get(currentMusicIndex);
        }

        // Stop any running bg music thread (called before restart)
        private void killBgMusicThread() {
            if (bgMusicThread != null) {
                bgMusicThread.interrupt();
                bgMusicThread = null;
            }
        }

        // Restart bg music with current file
        private void restartBgMusic() {
            if (bgMusicFiles.isEmpty()) return;
            if (!JLAYER_AVAILABLE) return;
            // Ensure old thread is stopped before starting new one
            killBgMusicThread();
            bgMusicFinished = false;
            bgMusicRunning = true;
            bgMusicPaused = false;
            final int myGen = ++bgMusicGen;
            bgMusicThread = new Thread(() -> {
                try {
                    File currentFile = bgMusicFiles.get(currentMusicIndex);
                    currentMusicName = currentFile.getName();
                    do {
                        if (!currentFile.exists()) break;
                        FileInputStream fileIn = null;
                        try {
                            fileIn = new FileInputStream(currentFile);
                            bgMusicStreamRef.set(fileIn);
                            Class<?> playerClass = Class.forName("javazoom.jl.player.Player");
                            java.lang.reflect.Constructor<?> ctor = playerClass.getConstructor(InputStream.class);
                            Object player = ctor.newInstance(fileIn);
                            playerClass.getMethod("play").invoke(player);
                        } catch (IOException e) {
                            break;
                        } catch (Exception e) {
                            break;
                        } finally {
                            bgMusicStreamRef.compareAndSet(fileIn, null);
                            if (fileIn != null) try { fileIn.close(); } catch (IOException ignored) {}
                        }
                        if (!loopMusic) {
                            bgMusicFinished = true;
                            break;
                        }
                        currentFile = getNextMusicFile();
                        if (currentFile == null) break;
                        currentMusicName = currentFile.getName();
                        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                    } while (bgMusicRunning && !Thread.currentThread().isInterrupted());
                } finally {
                    if (myGen == bgMusicGen) {
                        bgMusicRunning = false;
                    }
                }
            }, "bg-music");
            bgMusicThread.setDaemon(true);
            bgMusicThread.start();
        }

        void startBgMusic() {
            synchronized (musicLock) {
                if (bgMusicRunning) return;
                bgMusicFiles = scanMusicFiles();
                if (bgMusicFiles.isEmpty()) return;
                if (bgMusicFiles.size() > 1) {
                    Collections.shuffle(bgMusicFiles);
                }
                currentMusicIndex = 0;
                restartBgMusic();
            }
        }

        void stopBgMusic() {
            synchronized (musicLock) {
                bgMusicFinished = false;
                bgMusicRunning = false;
                bgMusicPaused = false;
                // Force-close the current input stream to stop JLayer Player
                InputStream stream = bgMusicStreamRef.getAndSet(null);
                if (stream != null) {
                    try { stream.close(); } catch (IOException ignored) {}
                }
                killBgMusicThread();
            }
        }

        void resumeBgMusic() {
            synchronized (musicLock) {
                if (bgMusicRunning) return;
                if (bgMusicFiles.isEmpty()) {
                    bgMusicFiles = scanMusicFiles();
                    if (bgMusicFiles.isEmpty()) return;
                    if (bgMusicFiles.size() > 1) {
                        Collections.shuffle(bgMusicFiles);
                    }
                    currentMusicIndex = 0;
                }
                bgMusicPaused = false;
                restartBgMusic();
            }
        }

        void shutdown() {
            stopBgMusic();
            // Shutdown sound effect executor with proper wait
            if (soundExecutor != null && !soundExecutor.isShutdown()) {
                soundExecutor.shutdownNow();
                try {
                    if (!soundExecutor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
                        soundExecutor.shutdownNow();
                    }
                } catch (InterruptedException ignored) {
                    soundExecutor.shutdownNow();
                }
            }
            // Close sfx line after executor shutdown to avoid concurrent access
            SourceDataLine lineToClose;
            synchronized (this) {
                lineToClose = sfxLine;
                sfxLine = null;
            }
            if (lineToClose != null && lineToClose.isOpen()) {
                synchronized (lineToClose) {
                    try { lineToClose.close(); } catch (Exception ignored) {}
                }
            }
        }

        void toggleBgMusicPause() {
            synchronized (musicLock) {
                if (bgMusicPaused) {
                    bgMusicPaused = false;
                    restartBgMusic();
                } else {
                    bgMusicRunning = false;
                    bgMusicPaused = true;
                    if (bgMusicThread != null) {
                        bgMusicThread.interrupt();
                        bgMusicThread = null;
                    }
                }
            }
        }

        boolean isBgMusicRunning() {
            return bgMusicRunning;
        }

        void playNextMusic() {
            synchronized (musicLock) {
                if (bgMusicFiles.isEmpty()) return;
                currentMusicIndex = (currentMusicIndex + 1) % bgMusicFiles.size();
                // Close current stream to stop playback immediately
                InputStream stream = bgMusicStreamRef.getAndSet(null);
                if (stream != null) {
                    try { stream.close(); } catch (IOException ignored) {}
                }
                killBgMusicThread();
                bgMusicRunning = false;
                bgMusicPaused = false;
                restartBgMusic();
            }
        }

        void playPrevMusic() {
            synchronized (musicLock) {
                if (bgMusicFiles.isEmpty()) return;
                currentMusicIndex = (currentMusicIndex - 1 + bgMusicFiles.size()) % bgMusicFiles.size();
                // Close current stream to stop playback immediately
                InputStream stream = bgMusicStreamRef.getAndSet(null);
                if (stream != null) {
                    try { stream.close(); } catch (IOException ignored) {}
                }
                killBgMusicThread();
                bgMusicRunning = false;
                bgMusicPaused = false;
                restartBgMusic();
            }
        }

        void play(final String type) {
            soundExecutor.submit(() -> {
                try {
                    // Get pre-computed or compute-once byte buffer
                    byte[] buffer;
                    synchronized (sfxCacheLock) {
                        buffer = sfxCache.get(type);
                        if (buffer == null) {
                            double[] samples = generateSound(type);
                            if (samples.length == 0) return;
                            buffer = toBytes(samples);
                            sfxCache.put(type, buffer);
                        }
                    }

                    // Initialize dedicated SFX line if needed (thread-safe: line creation is cheap enough)
                    SourceDataLine line;
                    synchronized (this) {
                        if (sfxLine != null && !sfxLine.isOpen()) {
                            try { sfxLine.close(); } catch (Exception ignored) {}
                            sfxLine = null;
                        }
                        if (sfxLine == null || !sfxLine.isOpen()) {
                            try {
                                AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
                                sfxLine = AudioSystem.getSourceDataLine(format);
                                if (sfxLine != null) {
                                    sfxLine.open(format, 4096);
                                    sfxLine.start();
                                }
                            } catch (Exception e2) {
                                sfxLine = null;
                            }
                        }
                        if (sfxLine == null || !sfxLine.isOpen()) return;
                        line = sfxLine;
                    }

                    // Write all data at once (reusing pre-computed buffer)
                    synchronized (line) {
                        line.write(buffer, 0, buffer.length);

                        // Wait for sound to finish playing (non-blocking for EDT)
                        line.drain();
                        Thread.sleep(buffer.length / 2L * 1000L / SAMPLE_RATE + 50);
                    }

                } catch (Exception e) {
                    // Audio not available - silently ignore
                }
            });
        }

        private double[] generateSound(String type) {
            int len;
            double[] data;

            switch (type) {
                case "move":
                    // Soft click: gentle sine with quick fade
                    len = SAMPLE_RATE / 30;
                    data = new double[len];
                    for (int i = 0; i < len; i++) {
                        double t = i / (double) SAMPLE_RATE;
                        double freq = 880.0; // A5
                        double env = Math.exp(-i / (double) (SAMPLE_RATE / 50)) * 0.15;
                        // Mix of sine and triangle for a softer, bell-like tone
                        double s = Math.sin(2 * Math.PI * freq * t);
                        double tri = (s > 0) ? (2.0 * s) : (-2.0 * s - 1.0); // simplified triangle
                        tri = Math.max(-1.0, Math.min(1.0, tri));
                        data[i] = (s * 0.7 + tri * 0.3) * env;
                    }
                    return data;

                case "rotate":
                    // Pleasant ascending two-note chime
                    len = SAMPLE_RATE / 25;
                    data = new double[len];
                    for (int i = 0; i < len; i++) {
                        double t = i / (double) SAMPLE_RATE;
                        double freq = (i < len / 2) ? 659.25 : 880.0; // E5 -> A5
                        double env;
                        if (i < SAMPLE_RATE / 40) {
                            env = i / (double) (SAMPLE_RATE / 40);
                        } else {
                            env = Math.exp(-(i - SAMPLE_RATE / 40) / (double) (SAMPLE_RATE / 12)) * 0.25;
                        }
                        data[i] = Math.sin(2 * Math.PI * freq * t) * env
                                + 0.15 * Math.sin(2 * Math.PI * freq * 3 * t) * env * 0.5;
                    }
                    return data;

                case "drop":
                    // Soft thud: low sine with quick pitch drop
                    len = SAMPLE_RATE / 8;
                    data = new double[len];
                    for (int i = 0; i < len; i++) {
                        double t = i / (double) SAMPLE_RATE;
                        double freq = 220.0 - 80.0 * (i / (double) len); // A3 descending
                        double env = (i < SAMPLE_RATE / 50) ?
                            (i / (double) (SAMPLE_RATE / 50)) :
                            Math.exp(-(i - SAMPLE_RATE / 50) / (double) (SAMPLE_RATE / 10)) * 0.35;
                        data[i] = Math.sin(2 * Math.PI * freq * t) * env
                                + 0.2 * Math.sin(2 * Math.PI * freq * 2.5 * t) * env * 0.3;
                    }
                    return data;

                case "lock":
                    // Satisfying click: warm tone with harmonics
                    len = SAMPLE_RATE / 12;
                    data = new double[len];
                    for (int i = 0; i < len; i++) {
                        double t = i / (double) SAMPLE_RATE;
                        double freq = 523.25; // C5
                        double env = (i < SAMPLE_RATE / 60) ?
                            (i / (double) (SAMPLE_RATE / 60)) :
                            Math.exp(-(i - SAMPLE_RATE / 60) / (double) (SAMPLE_RATE / 15)) * 0.3;
                        data[i] = Math.sin(2 * Math.PI * freq * t) * env
                                + 0.12 * Math.sin(2 * Math.PI * freq * 2.5 * t) * env * 0.4
                                + 0.06 * Math.sin(2 * Math.PI * freq * 4 * t) * env * 0.2;
                    }
                    return data;

                case "clear1":
                case "clear2":
                case "clear3":
                case "clear4":
                    int mult = type.charAt(5) - '0';
                    len = SAMPLE_RATE / 4 * mult;
                    data = new double[len];
                    // Ascending chord arpeggio with rich harmonics
                    for (int i = 0; i < len; i++) {
                        double t = i / (double) SAMPLE_RATE;
                        // Step through chord notes
                        int noteIdx = Math.min(6 + mult - 1 + i / (len / 4), PENTATONIC.length - 1);
                        double freq = PENTATONIC[noteIdx];
                        double env;
                        if (i < SAMPLE_RATE / 50) {
                            env = i / (double) (SAMPLE_RATE / 50);
                        } else {
                            env = Math.exp(-(i - SAMPLE_RATE / 50) / (double) (SAMPLE_RATE / 5)) * 0.3;
                        }
                        // Rich tone: fundamental + soft harmonics
                        data[i] = Math.sin(2 * Math.PI * freq * t) * env * 0.25
                                + 0.12 * Math.sin(2 * Math.PI * freq * 2 * t) * env * 0.15
                                + 0.06 * Math.sin(2 * Math.PI * freq * 3 * t) * env * 0.08;
                    }
                    return data;

                case "gameover":
                    len = SAMPLE_RATE * 2;
                    data = new double[len];
                    for (int i = 0; i < len; i++) {
                        double t = i / (double) SAMPLE_RATE;
                        double env = Math.exp(-i / (double) (SAMPLE_RATE));
                        // Descending pentatonic melody with warmth
                        double freq = 523.25 - 250 * (i / (double) len);
                        data[i] = Math.sin(2 * Math.PI * freq * t) * env * 0.2
                                + 0.1 * Math.sin(2 * Math.PI * freq * 2 * t) * env * 0.08;
                    }
                    return data;

                case "newgame":
                    len = SAMPLE_RATE / 2;
                    data = new double[len];
                    for (int i = 0; i < len; i++) {
                        double t = i / (double) SAMPLE_RATE;
                        double env = Math.exp(-i / (double) (SAMPLE_RATE / 4));
                        // Bright ascending pentatonic melody
                        double freq = 392.0 + 392.0 * (i / (double) len);
                        data[i] = Math.sin(2 * Math.PI * freq * t) * env * 0.2
                                + 0.12 * Math.sin(2 * Math.PI * freq * 2 * t) * env * 0.1;
                    }
                    return data;

                case "pause":
                case "resume":
                case "hold":
                case "theme":
                    len = SAMPLE_RATE / 15;
                    data = new double[len];
                    for (int i = 0; i < len; i++) {
                        double t = i / (double) SAMPLE_RATE;
                        double freq = type.equals("pause") ? 329.63 : type.equals("resume") ? 523.25 : 392.00;
                        double env = Math.exp(-i / (double) (SAMPLE_RATE / 12)) * 0.2;
                        data[i] = Math.sin(2 * Math.PI * freq * t) * env
                                + 0.1 * Math.sin(2 * Math.PI * freq * 2.5 * t) * env * 0.3;
                    }
                    return data;

                default:
                    return new double[0];
            }
        }
    }

    // ==================== MAIN ====================
    public static void main(String[] args) {
	boolean decorated = false;
        for (String arg : args) {
            if (arg.equals("--decorated") || arg.equals("-d")) {
                decorated = true;
                break;
            }
        }
	final boolean finalDecorated = decorated;
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            TetrisPro game = new TetrisPro(finalDecorated, true);
            game.setVisible(true);
        });
    }

    // ==================== BOTTOM BAR ====================
    private class BottomBar extends JPanel {
        private final Font bottomBarFont = chineseFont(Font.PLAIN, 9);
        private final BasicStroke BOTTOMBAR_NORMAL_STROKE = new BasicStroke(1f);
        private final Color BOTTOMBAR_WHITE_80 = new Color(255, 255, 255, 80);
        private final Color BOTTOMBAR_WHITE_50 = new Color(255, 255, 255, 50);
        private final BasicStroke BOTTOMBAR_LIGHT_STROKE = new BasicStroke(1.2f);
        private int hoveredBtn = -1; // 0-5 for the 6 buttons
        private int pressedBtn = -1; // 0-5 for the currently pressed button
        private TetrisPro outer;

        private final Font btnFont = chineseFont(Font.BOLD, 12);
        private BottomBar() {
            outer = TetrisPro.this;
            setPreferredSize(new Dimension(TOTAL_WIDTH, BOTTOM_BAR_HEIGHT));
            setMaximumSize(new Dimension(TOTAL_WIDTH, BOTTOM_BAR_HEIGHT));
            setMinimumSize(new Dimension(TOTAL_WIDTH, BOTTOM_BAR_HEIGHT));
            setOpaque(false);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    // Start screen buttons are handled by GamePanel, not BottomBar
                    if (outer.showStartScreen) return;
                    if (!outer.gameStarted && !outer.gameOver) return;
                    int btnW = 80, btnH = 32, btnGap = 5;
                    int totalBtnW = btnW * 6 + btnGap * 5;
                    int barW = GAME_WIDTH + SIDE_PANEL_WIDTH + 15;
                    int startX = 5 + (barW - totalBtnW) / 2;
                    int startY = (BOTTOM_BAR_HEIGHT - btnH) / 2;
                    for (int i = 0; i < 6; i++) {
                        int bx = startX + i * (btnW + btnGap);
                        if (e.getX() >= bx && e.getX() <= bx + btnW &&
                            e.getY() >= startY && e.getY() <= startY + btnH) {
                            pressedBtn = i;
                            repaint();
                            return;
                        }
                    }
                }
                @Override
                public void mouseReleased(MouseEvent e) {
                    if (pressedBtn == -1) return;
                    // Start screen buttons (indices 10=start, 11=exit)
                    // Start screen buttons are handled by GamePanel, not BottomBar
                    if (outer.showStartScreen) {
                        pressedBtn = -1;
                        repaint();
                        return;
                    }
                    int btnW = 80, btnH = 32, btnGap = 5;
                    int totalBtnW = btnW * 6 + btnGap * 5;
                    int barW = GAME_WIDTH + SIDE_PANEL_WIDTH + 15;
                    int startX = 5 + (barW - totalBtnW) / 2;
                    int startY = (BOTTOM_BAR_HEIGHT - btnH) / 2;
                    int i = pressedBtn;
                    int bx = startX + i * (btnW + btnGap);
                    if (e.getX() >= bx && e.getX() <= bx + btnW &&
                        e.getY() >= startY && e.getY() <= startY + btnH) {
                        if (i == 0) { outer.newGame(); }
                        else if (i == 1) { outer.togglePause(); }
                        else if (i == 2) { outer.toggleNextMusic(); }
                        else if (i == 3) { outer.cycleTheme(); }
                        else if (i == 4) { outer.goToStartScreen(); }
                        else if (i == 5) { outer.showMusicSettingsDialog(); }
                    }
                    pressedBtn = -1;
                    repaint();
                }
                @Override
                public void mouseClicked(MouseEvent e) {
                    // Click handling is done in mousePressed/mouseReleased
                }
            });

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    // Start screen hover is handled by GamePanel, not BottomBar
                    if (outer.showStartScreen) return;
                    if (!outer.gameStarted && !outer.gameOver) {
                        hoveredBtn = -1;
                        repaint();
                        return;
                    }
                    int btnW = 80, btnH = 32, btnGap = 5;
                    int totalBtnW = btnW * 6 + btnGap * 5;
                    int barW = GAME_WIDTH + SIDE_PANEL_WIDTH + 15;
                    int startX = 5 + (barW - totalBtnW) / 2;
                    int startY = (BOTTOM_BAR_HEIGHT - btnH) / 2;
                    for (int i = 0; i < 6; i++) {
                        int bx = startX + i * (btnW + btnGap);
                        if (e.getX() >= bx && e.getX() <= bx + btnW &&
                            e.getY() >= startY && e.getY() <= startY + btnH) {
                            hoveredBtn = i;
                            repaint();
                            return;
                        }
                    }
                    if (hoveredBtn != -1) {
                        hoveredBtn = -1;
                        repaint();
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // Only draw buttons during gameplay/game-over
            if (outer.showStartScreen || (!outer.gameStarted && !outer.gameOver)) {
                // Start screen: draw Tetris-themed decorative background that matches current theme
                Theme st = outer.currentTheme;
                GradientPaint barGrad = new GradientPaint(
                        0, 0, st.panelBg,
                        0, h, st.darkerBg);
                g2.setPaint(barGrad);
                g2.fillRect(0, 0, w, h);

                // Top border line - theme-aware
                g2.setColor(st.panelBorder);
                g2.setStroke(BOTTOMBAR_NORMAL_STROKE);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
                g2.drawLine(0, 0, w, 0);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

                // Draw decorative falling tetromino pieces
                long seed = 12345L;
                int cellSize = 8;
                int barY = h / 2 - cellSize;
                int[][][] decoShapes = {
                    {{0,0,0,0},{1,1,1,1},{0,0,0,0},{0,0,0,0}},
                    {{2,0,0},{2,2,2},{0,0,0}},
                    {{0,0,3},{3,3,3},{0,0,0}},
                    {{4,4},{4,4}},
                    {{0,5,5},{5,5,0},{0,0,0}},
                    {{0,6,0},{6,6,6},{0,0,0}},
                    {{7,7,0},{0,7,7},{0,0,0}},
                };
                Color[] decoColors = {
                    new Color(0x00, 0xCC, 0xFF, 60),
                    new Color(0x00, 0x50, 0xFF, 60),
                    new Color(0xFF, 0xA0, 0x00, 60),
                    new Color(0xFF, 0xDC, 0x00, 60),
                    new Color(0x00, 0xCC, 0x44, 60),
                    new Color(0x90, 0x00, 0xFF, 60),
                    new Color(0xFF, 0x30, 0x30, 60),
                };
                String[] decoLabels = {"I", "J", "L", "O", "S", "T", "Z"};
                for (int i = 0; i < 7; i++) {
                    seed = seed * 6364136223846353L + 1;
                    int spacing = (w - 20) / 7;
                    int startX = (w - 6 * spacing) / 2;
                    int xPos = startX + i * spacing;
                    seed = seed * 6364136223846353L + 1;
                    int rot = (int)(Math.abs(seed) % 4);
                    int dy = (int)(Math.abs(seed) % 10) - 5;

                    int[][] shape = decoShapes[i];
                    // Find bounding box
                    int minR = shape.length, maxR = 0, minC = shape[0].length, maxC = 0;
                    for (int r = 0; r < shape.length; r++) {
                        for (int c = 0; c < shape[r].length; c++) {
                            if (shape[r][c] != 0) {
                                minR = Math.min(minR, r); maxR = Math.max(maxR, r);
                                minC = Math.min(minC, c); maxC = Math.max(maxC, c);
                            }
                        }
                    }
                    int sW = (maxC - minC + 1) * cellSize;
                    int sH = (maxR - minR + 1) * cellSize;
                    Color dc = decoColors[i];

                    startX = xPos - sW / 2;
                    int startY = barY + dy - sH / 2;
                    g2.setColor(dc);
                    for (int r = minR; r <= maxR; r++) {
                        for (int c = minC; c <= maxC; c++) {
                            if (shape[r][c] != 0) {
                                g2.fillRoundRect(
                                    startX + (c - minC) * cellSize,
                                    startY + (r - minR) * cellSize,
                                    cellSize - 1, cellSize - 1, 2, 2);
                            }
                        }
                    }
                    // Label
                    g2.setColor(BOTTOMBAR_WHITE_80);
                    g2.setFont(bottomBarFont);
                    FontMetrics fm = g2.getFontMetrics();
                    String label = decoLabels[i];
                    g2.drawString(label, xPos - fm.stringWidth(label) / 2, h - 3);
                }

                return;
            }

            // Six buttons, centered horizontally
            String[] labels = {"新游戏 [N]", "暂停 [ESC]", "切歌 [P]/[O]", "切换主题 [T]", "返回 [Q]", "音乐设置 [L]"};
            int btnW = 80, btnH = 32, btnGap = 5;
            int totalBtnW = btnW * 6 + btnGap * 5;
            int startX = (w - totalBtnW) / 2;
            int startY = (h - btnH) / 2;

            for (int i = 0; i < 6; i++) {
                int bx = startX + i * (btnW + btnGap);

                boolean isHovered = (hoveredBtn == i);
                boolean isPressed = (pressedBtn == i);
                // Use pre-cached colors instead of computing per-frame
                Color btnColor = isPressed ? BTN_BASE[i] : ((isHovered) ? BTN_HOVER[i] : BTN_BASE[i]);
                Color btnBorder = isPressed ? BTN_BORDER_PRESSED[i]
                        : (isHovered ? BTN_BORDER_HOVER[i] : BTN_BORDER[i]);
                Color btnGradTop = BTN_GRAD_TOP[i];

                // Button body with subtle vertical gradient
                g2.setPaint(new GradientPaint(bx, startY, btnGradTop, bx, startY + btnH, btnColor));
                g2.fillRoundRect(bx, startY, btnW, btnH, 6, 6);

                // Button top shine highlight
                g2.setColor(BOTTOMBAR_WHITE_50);
                g2.fillRoundRect(bx + 2, startY + 2, btnW - 4, btnH / 2 - 4, 4, 4);

                // Button border
                g2.setColor(btnBorder);
                g2.setStroke(BOTTOMBAR_LIGHT_STROKE);
                g2.drawRoundRect(bx, startY, btnW, btnH, 6, 6);

                // Button text - shift slightly when pressed for "pressed in" effect
                int textOffsetX = 0, textOffsetY = 0;
                if (isPressed) { textOffsetX = 1; textOffsetY = 1; }
                g2.setColor(Color.WHITE);
                g2.setFont(btnFont);
                FontMetrics fm = g2.getFontMetrics();
                String label = labels[i];
                int textX = bx + (btnW - fm.stringWidth(label)) / 2 + textOffsetX;
                int textY = startY + (btnH - fm.getHeight()) / 2 + fm.getAscent() + textOffsetY;
                g2.drawString(label, textX, textY);
            }
        }
    }
}
