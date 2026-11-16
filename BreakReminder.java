import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class BreakReminder {

    // ── state ──────────────────────────────────────────────────────────────────
    private static ScheduledExecutorService scheduler;
    private static ScheduledFuture<?>        reminderTask;
    private static ScheduledFuture<?>        countdownTask;
    private static final AtomicBoolean       muted       = new AtomicBoolean(false);
    private static final AtomicBoolean       running     = new AtomicBoolean(false);
    private static final AtomicLong          nextAlertMs = new AtomicLong(0);

    // ── ui refs ────────────────────────────────────────────────────────────────
    private static JFrame      mainFrame;
    private static JLabel      statusLabel;
    private static JLabel      countdownLabel;
    private static JButton     startBtn;
    private static JButton     muteBtn;
    private static JButton     stopBtn;
    private static JSpinner    IntervalSpinner;
    private static JSpinner    muteSpinner;
    private static TrayIcon    trayIcon;

    // ── palette ────────────────────────────────────────────────────────────────
    private static final Color BG        = new Color(0x0F1117);
    private static final Color SURFACE   = new Color(0x1A1D27);
    private static final Color CARD      = new Color(0x20242F);
    private static final Color ACCENT    = new Color(0x6C63FF);
    private static final Color ACCENT2   = new Color(0xFF6584);
    private static final Color SUCCESS   = new Color(0x43E97B);
    private static final Color WARNING   = new Color(0xF7971E);
    private static final Color TEXT      = new Color(0xEAEAF0);
    private static final Color TEXT_DIM  = new Color(0x6B7280);
    private static final Color BORDER_C  = new Color(0x2D3148);

    // ── fonts ──────────────────────────────────────────────────────────────────
    private static Font FONT_TITLE;
    private static Font FONT_BODY;
    private static Font FONT_MONO;

    // ══════════════════════════════════════════════════════════════════════════
    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        loadFonts();
        scheduler = Executors.newScheduledThreadPool(2);
        SwingUtilities.invokeLater(() -> {
            buildMainWindow();
            setupTray();
        });
    }

    // ── font loading ───────────────────────────────────────────────────────────
    private static void loadFonts() {
        // Fall back gracefully if system fonts aren't ideal
        FONT_TITLE = new Font("Inter", Font.BOLD, 13);
        FONT_BODY  = new Font("Inter", Font.PLAIN, 12);
        FONT_MONO  = new Font("Consolas", Font.BOLD, 22);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  MAIN WINDOW
    // ══════════════════════════════════════════════════════════════════════════
    private static void buildMainWindow() {
        mainFrame = new JFrame("Break Reminder By ClaudeAI");
        mainFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        mainFrame.setSize(400, 460);
        mainFrame.setResizable(false);
        mainFrame.setUndecorated(true);
        mainFrame.setBackground(BG);

        JPanel root = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 20, 20));
                // subtle grid dots
                g2.setColor(new Color(0x2D3148));
                for (int x = 20; x < getWidth(); x += 30)
                    for (int y = 20; y < getHeight(); y += 30)
                        g2.fillOval(x, y, 2, 2);
                g2.dispose();
            }
        };
        root.setOpaque(false);
        root.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        // ── drag support ───────────────────────────────────────────────────────
        Point[] dragOrigin = {null};
        root.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { dragOrigin[0] = e.getPoint(); }
        });
        root.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                if (dragOrigin[0] != null) {
                    Point loc = mainFrame.getLocation();
                    mainFrame.setLocation(loc.x + e.getX() - dragOrigin[0].x,
                                          loc.y + e.getY() - dragOrigin[0].y);
                }
            }
        });

        root.add(buildTitleBar(),  BorderLayout.NORTH);
        root.add(buildContent(),   BorderLayout.CENTER);
        root.add(buildFooter(),    BorderLayout.SOUTH);

        // rounded window shape
        mainFrame.setShape(new RoundRectangle2D.Double(0, 0, 400, 460, 20, 20));
        mainFrame.setContentPane(root);
        mainFrame.setLocationRelativeTo(null);
        mainFrame.setVisible(true);
    }

    // ── title bar ──────────────────────────────────────────────────────────────
    private static JPanel buildTitleBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(16, 20, 8, 16));

        // icon + title
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);

        JLabel icon = new JLabel(createAppIcon(28));
        // Title pane
        JPanel titlebox = new JPanel();
		titlebox.setLayout(new BoxLayout(titlebox, BoxLayout.Y_AXIS));
		titlebox.setOpaque(false);
		
        JLabel title = new JLabel("   Break Reminder");
        title.setFont(new Font("Inter", Font.BOLD, 18));
        title.setForeground(TEXT);

        JLabel sub = new JLabel("    Stay refreshed, stay focused");
        sub.setFont(new Font("Inter", Font.PLAIN, 12));
        sub.setForeground(TEXT_DIM);
        
        titlebox.add(title);
        titlebox.add(sub);

        left.add(icon);
        left.add(titlebox);
        //left.add(sub);

        // close button
        //JButton close = circleButton("✕", ACCENT2, 26);
        JButton close = circleButton("X", ACCENT2, 26);
        close.addActionListener(e -> mainFrame.setVisible(false));

        bar.add(left,  BorderLayout.WEST);
        bar.add(close, BorderLayout.EAST);
        return bar;
    }

    // ── main content ──────────────────────────────────────────────────────────
    private static JPanel buildContent() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(4, 20, 4, 20));

        panel.add(buildStatusCard());
        panel.add(Box.createVerticalStrut(14));
        panel.add(buildSettingsCard());
        panel.add(Box.createVerticalStrut(14));
        panel.add(buildControlsCard());
        return panel;
    }

    // ── STATUS CARD ────────────────────────────────────────────────────────────
    private static JPanel buildStatusCard() {
        JPanel card = glassCard();
        card.setLayout(new BorderLayout(0, 8));

        JLabel heading = sectionLabel("❤ STATUS");

        // big countdown
        countdownLabel = new JLabel("--:--", SwingConstants.CENTER);
        countdownLabel.setFont(FONT_MONO);
        countdownLabel.setForeground(ACCENT);

        statusLabel = new JLabel("Press Start to begin", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Inter", Font.PLAIN, 12));
        statusLabel.setForeground(TEXT_DIM);

        JPanel inner = new JPanel(new GridLayout(2, 1, 0, 4));
        inner.setOpaque(false);
        inner.add(countdownLabel);
        inner.add(statusLabel);

        card.add(heading, BorderLayout.NORTH);
        card.add(inner,   BorderLayout.CENTER);
        return card;
    }

    // ── SETTINGS CARD ─────────────────────────────────────────────────────────
    private static JPanel buildSettingsCard() {
        JPanel card = glassCard();
        card.setLayout(new BorderLayout(0, 10));

        JLabel heading = sectionLabel("⌘  SETTINGS");

        JPanel grid = new JPanel(new GridLayout(2, 2, 12, 10));
        grid.setOpaque(false);
        grid.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Interval
        grid.add(fieldLabel("Reminder Interval (min)"));
        IntervalSpinner = styledSpinner(1, 240, 30, 5);
        grid.add(IntervalSpinner);

        // mute duration
        grid.add(fieldLabel("Mute duration (min)"));
        muteSpinner = styledSpinner(1, 120, 15, 5);
        grid.add(muteSpinner);

        card.add(heading, BorderLayout.NORTH);
        card.add(grid,    BorderLayout.CENTER);
        return card;
    }

    // ── CONTROLS CARD ─────────────────────────────────────────────────────────
    private static JPanel buildControlsCard() {
        JPanel card = glassCard();
        card.setLayout(new BorderLayout(0, 12));

        JLabel heading = sectionLabel("❖  CONTROLS");

        JPanel btnRow = new JPanel(new GridLayout(1, 3, 10, 0));
        btnRow.setOpaque(false);
        btnRow.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        startBtn = accentButton("▶  Start", SUCCESS);
        muteBtn  = accentButton("●  Mute",  WARNING);
        stopBtn  = accentButton("■  Stop",  ACCENT2);

        muteBtn.setEnabled(false);
        stopBtn.setEnabled(false);

        startBtn.addActionListener(e -> onStart());
        muteBtn .addActionListener(e -> onMute());
        stopBtn .addActionListener(e -> onStop());

        btnRow.add(startBtn);
        btnRow.add(muteBtn);
        btnRow.add(stopBtn);

        card.add(heading, BorderLayout.NORTH);
        card.add(btnRow,  BorderLayout.CENTER);
        return card;
    }

    // ── footer ────────────────────────────────────────────────────────────────
    private static JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER));
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(4, 0, 14, 0));
        JLabel lbl = new JLabel("Minimises to System Tray  ❤  v1.0");
        lbl.setFont(new Font("Inter", Font.PLAIN, 12));
        lbl.setForeground(TEXT_DIM);
        
        footer.add(lbl);
        return footer;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  CONTROL LOGIC
    // ══════════════════════════════════════════════════════════════════════════
    private static void onStart() {
        if (running.get()) return;
        int minutes = (int) IntervalSpinner.getValue();
        scheduleReminder(minutes);
        running.set(true);
        muted.set(false);
        startBtn.setEnabled(false);
        muteBtn.setEnabled(true);
        stopBtn.setEnabled(true);
        statusLabel.setForeground(SUCCESS);
        updateTrayTooltip("Running – every " + minutes + " min");
    }

    private static void scheduleReminder(int minutes) {
        if (reminderTask != null) reminderTask.cancel(false);
        if (countdownTask != null) countdownTask.cancel(false);

        long IntervalMs = minutes * 60_000L;
        nextAlertMs.set(System.currentTimeMillis() + IntervalMs);

        reminderTask = scheduler.scheduleAtFixedRate(() -> {
            if (!muted.get()) {
                nextAlertMs.set(System.currentTimeMillis() + IntervalMs);
                SwingUtilities.invokeLater(BreakReminder::showAlert);
            }
        }, IntervalMs, IntervalMs, TimeUnit.MILLISECONDS);

        countdownTask = scheduler.scheduleAtFixedRate(() ->
            SwingUtilities.invokeLater(BreakReminder::tickCountdown),
            0, 1, TimeUnit.SECONDS);
    }

    private static void tickCountdown() {
        long remaining = nextAlertMs.get() - System.currentTimeMillis();
        if (remaining < 0) remaining = 0;
        long m = remaining / 60_000;
        long s = (remaining % 60_000) / 1000;
        countdownLabel.setText(String.format("%02d:%02d", m, s));

        if (muted.get()) {
            statusLabel.setText("Muted – next alert after unmute");
            statusLabel.setForeground(WARNING);
            countdownLabel.setForeground(WARNING);
        } else {
            statusLabel.setText("Next reminder in " + m + "m " + s + "s");
            statusLabel.setForeground(SUCCESS);
            countdownLabel.setForeground(ACCENT);
        }
    }

    private static void onMute() {
        if (!running.get()) return;
        if (muted.get()) {
            // unmute
            muted.set(false);
            muteBtn.setText("●  Mute");
            // re-schedule from now
            int minutes = (int) IntervalSpinner.getValue();
            scheduleReminder(minutes);
            statusLabel.setForeground(SUCCESS);
        } else {
            // mute
            muted.set(true);
            int muteMins = (int) muteSpinner.getValue();
            muteBtn.setText("○  Unmute");
            statusLabel.setText("Muted for " + muteMins + " min");
            statusLabel.setForeground(WARNING);
            updateTrayTooltip("Muted for " + muteMins + " min");
            // auto-unmute
            scheduler.schedule(() -> {
                if (muted.get()) {
                    muted.set(false);
                    int minutes = (int) IntervalSpinner.getValue();
                    SwingUtilities.invokeLater(() -> {
                        muteBtn.setText("●  Mute");
                        scheduleReminder(minutes);
                    });
                }
            }, muteMins, TimeUnit.MINUTES);
        }
    }

    private static void onStop() {
        if (reminderTask  != null) reminderTask.cancel(false);
        if (countdownTask != null) countdownTask.cancel(false);
        running.set(false);
        muted.set(false);
        startBtn.setEnabled(true);
        muteBtn.setEnabled(false);
        stopBtn.setEnabled(false);
        muteBtn.setText("●  Mute");
        countdownLabel.setText("--:--");
        countdownLabel.setForeground(ACCENT);
        statusLabel.setText("Stopped – have a great day!");
        statusLabel.setForeground(TEXT_DIM);
        updateTrayTooltip("Break Reminder – Stopped");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ALERT DIALOG
    // ══════════════════════════════════════════════════════════════════════════
    private static void showAlert() {
        JDialog dlg = new JDialog((Frame) null, true);
        dlg.setUndecorated(true);
        dlg.setBackground(new Color(0, 0, 0, 0));
        dlg.setSize(420, 320);
        dlg.setLocationRelativeTo(null);
        dlg.setAlwaysOnTop(true);

        JPanel root = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // glow bg
                RadialGradientPaint rg = new RadialGradientPaint(
                    getWidth() / 2f, getHeight() / 2f, getWidth() * 0.7f,
                    new float[]{0f, 1f},
                    new Color[]{new Color(0x2D1F5E), new Color(0x0F1117)}
                );
                g2.setPaint(rg);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 24, 24));

                // border glow
                g2.setStroke(new BasicStroke(2f));
                g2.setColor(new Color(108, 99, 255, 180));
                g2.draw(new RoundRectangle2D.Float(1, 1, getWidth()-2, getHeight()-2, 23, 23));
                g2.dispose();
            }
        };
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(30, 30, 24, 30));

        // ── top icon strip ─────────────────────────────────────────────────────
        JLabel iconLbl = new JLabel(createPulseIcon(52), SwingConstants.CENTER);

        // ── headline ───────────────────────────────────────────────────────────
        JLabel headline = new JLabel("Time to Refresh!", SwingConstants.CENTER);
        headline.setFont(new Font("Inter", Font.BOLD, 22));
        headline.setForeground(TEXT);

        JLabel sub = new JLabel("<html><div style='text-align:center;'>Take a short break — stand up, stretch,<br>look away from the screen for a moment.</div></html>", SwingConstants.CENTER);
        sub.setFont(new Font("Inter", Font.PLAIN, 12));
        sub.setForeground(TEXT_DIM);

        // ── progress bar (5-second auto-close) ────────────────────────────────
        int[] secondsLeft = {10};
        JPanel progressOuter = new JPanel(null) {
            @Override public Dimension getPreferredSize() { return new Dimension(0, 6); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BORDER_C);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                int filled = (int) (getWidth() * (secondsLeft[0] / 10.0));
                GradientPaint gp = new GradientPaint(0, 0, ACCENT, filled, 0, ACCENT2);
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, filled, getHeight(), 6, 6);
                g2.dispose();
            }
        };
        progressOuter.setOpaque(false);

        JLabel timerLbl = new JLabel("Closes in 10s", SwingConstants.CENTER);
        timerLbl.setFont(new Font("Inter", Font.PLAIN, 10));
        timerLbl.setForeground(TEXT_DIM);

        // ── buttons ────────────────────────────────────────────────────────────
        JButton dismissBtn = alertButton("Got It ✓", ACCENT);
        JButton snoozeBtn  = alertButton("Snooze 5m", new Color(0x3D4468));

        dismissBtn.addActionListener(e -> dlg.dispose());
        snoozeBtn.addActionListener(e -> {
            dlg.dispose();
            boolean wasMuted = muted.get();
            muted.set(true);
            scheduler.schedule(() -> {
                muted.set(wasMuted);
                if (!wasMuted) {
                    int minutes = (int) IntervalSpinner.getValue();
                    SwingUtilities.invokeLater(() -> scheduleReminder(minutes));
                }
            }, 5, TimeUnit.MINUTES);
        });

        JPanel btnRow = new JPanel(new GridLayout(1, 2, 12, 0));
        btnRow.setOpaque(false);
        btnRow.add(snoozeBtn);
        btnRow.add(dismissBtn);

        // ── layout ─────────────────────────────────────────────────────────────
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);
        iconLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        headline.setAlignmentX(Component.CENTER_ALIGNMENT);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);
        timerLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        center.add(iconLbl);
        center.add(Box.createVerticalStrut(14));
        center.add(headline);
        center.add(Box.createVerticalStrut(8));
        center.add(sub);
        center.add(Box.createVerticalStrut(20));
        center.add(progressOuter);
        center.add(Box.createVerticalStrut(4));
        center.add(timerLbl);
        center.add(Box.createVerticalStrut(16));
        center.add(btnRow);

        root.add(center, BorderLayout.CENTER);
        dlg.setContentPane(root);
        dlg.setShape(new RoundRectangle2D.Double(0, 0, 420, 320, 24, 24));

        // auto-close timer
        Timer autoClose = new Timer(1000, null);
        autoClose.addActionListener(e -> {
            secondsLeft[0]--;
            timerLbl.setText("Closes in " + secondsLeft[0] + "s");
            progressOuter.repaint();
            if (secondsLeft[0] <= 0) {
                autoClose.stop();
                dlg.dispose();
            }
        });
        autoClose.start();

        // flash tray
        if (trayIcon != null) trayIcon.displayMessage(
            "Break Time!", "Time to refresh — stand up and stretch!",
            TrayIcon.MessageType.INFO);

        dlg.setVisible(true);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SYSTEM TRAY
    // ══════════════════════════════════════════════════════════════════════════
    private static void setupTray() {
        if (!SystemTray.isSupported()) return;
        SystemTray tray = SystemTray.getSystemTray();
        Image img = createTrayImage();
        trayIcon = new TrayIcon(img, "Break Reminder");
        trayIcon.setImageAutoSize(true);

        PopupMenu menu = new PopupMenu();
        MenuItem show  = new MenuItem("Open");
        MenuItem alert = new MenuItem("Alert Now");
        MenuItem quit  = new MenuItem("Quit");

        show .addActionListener(e -> SwingUtilities.invokeLater(() -> { mainFrame.setVisible(true); mainFrame.toFront(); }));
        alert.addActionListener(e -> SwingUtilities.invokeLater(BreakReminder::showAlert));
        quit .addActionListener(e -> { scheduler.shutdownNow(); System.exit(0); });

        menu.add(show);
        menu.add(alert);
        menu.addSeparator();
        menu.add(quit);
        trayIcon.setPopupMenu(menu);
        trayIcon.addActionListener(e -> SwingUtilities.invokeLater(() -> { mainFrame.setVisible(true); mainFrame.toFront(); }));

        try { tray.add(trayIcon); } catch (AWTException ignored) {}
    }

    private static void updateTrayTooltip(String tip) {
        if (trayIcon != null) trayIcon.setToolTip(tip);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  WIDGET HELPERS
    // ══════════════════════════════════════════════════════════════════════════
    private static JPanel glassCard() {
        return new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.setColor(BORDER_C);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 14, 14);
                g2.dispose();
            }
            @Override public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
    }

    static {
        // common card padding applied via override
    }

    private static JLabel sectionLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Inter", Font.BOLD, 11));
        //lbl.setForeground(TEXT_DIM);
        lbl.setForeground(new Color(0x9B9DA2));
        lbl.setBorder(new EmptyBorder(10, 12, 6, 12));
        return lbl;
    }

    private static JLabel fieldLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Inter", Font.PLAIN, 11));
        lbl.setForeground(TEXT_DIM);
        return lbl;
    }

    private static JSpinner styledSpinner(int min, int max, int val, int step) {
        SpinnerNumberModel model = new SpinnerNumberModel(val, min, max, step);
        JSpinner sp = new JSpinner(model) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(SURFACE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(BORDER_C);
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        sp.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 2));
        JComponent editor = sp.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            JTextField tf = ((JSpinner.DefaultEditor) editor).getTextField();
            tf.setBackground(SURFACE);
            tf.setForeground(TEXT);
            tf.setCaretColor(ACCENT);
            tf.setFont(new Font("Inter", Font.BOLD, 13));
            tf.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        }
        return sp;
    }

    private static JButton accentButton(String text, Color color) {
        JButton btn = new JButton(text) {
            private boolean hover = false;
            { addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hover = true;  repaint(); }
                public void mouseExited (MouseEvent e) { hover = false; repaint(); }
            }); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = isEnabled()
                    ? (hover ? color.brighter() : color.darker())
                    : new Color(0x2D3148);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setFont(getFont());
                g2.setColor(isEnabled() 
					? (hover ? color.BLACK : Color.WHITE) 
					: TEXT_DIM);
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth()  - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), x, y);
                g2.dispose();
            }
        };
        btn.setFont(new Font("Inter", Font.BOLD, 12));
        btn.setPreferredSize(new Dimension(0, 36));
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private static JButton alertButton(String text, Color color) {
        JButton btn = accentButton(text, color);
        btn.setPreferredSize(new Dimension(140, 40));
        btn.setFont(new Font("Inter", Font.BOLD, 13));
        return btn;
    }

    private static JButton circleButton(String text, Color color, int size) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color.darker());
                g2.fillOval(0, 0, size, size);
                g2.setFont(getFont());
                g2.setColor(Color.WHITE);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                    (size - fm.stringWidth(getText())) / 2,
                    (size + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        btn.setPreferredSize(new Dimension(size, size));
        btn.setMaximumSize(new Dimension(size, size));
        btn.setFont(new Font("Inter", Font.BOLD, 11));
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ICON GENERATION
    // ══════════════════════════════════════════════════════════════════════════
    private static ImageIcon createAppIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // gradient circle
        RadialGradientPaint rg = new RadialGradientPaint(
            size/2f, size/2f, size/2f,
            new float[]{0f, 1f},
            new Color[]{ACCENT, new Color(0x3B35AA)}
        );
        g2.setPaint(rg);
        g2.fillOval(0, 0, size, size);
        // clock hands
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int cx = size/2, cy = size/2, r = size/2 - 4;
        g2.drawOval(cx-r, cy-r, r*2, r*2);
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(cx, cy, cx, cy - r + 4);         // 12 o'clock
        g2.drawLine(cx, cy, cx + r - 4, cy + 2);     // 3 o'clock
        g2.dispose();
        return new ImageIcon(img);
    }

    private static ImageIcon createPulseIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // outer glow ring
        g2.setColor(new Color(108, 99, 255, 60));
        g2.fillOval(0, 0, size, size);
        g2.setColor(new Color(108, 99, 255, 100));
        g2.fillOval(4, 4, size-8, size-8);
        // inner circle
        RadialGradientPaint rg = new RadialGradientPaint(
            size/2f, size/2f, size/2f-6,
            new float[]{0f, 1f},
            new Color[]{new Color(0x8B83FF), ACCENT}
        );
        g2.setPaint(rg);
        g2.fillOval(8, 8, size-16, size-16);
        // coffee cup icon (simple)
        g2.setColor(Color.WHITE);
        int ox = size/2 - 9, oy = size/2 - 8;
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawRoundRect(ox, oy+4, 14, 10, 3, 3);
        g2.drawArc(ox+14, oy+6, 6, 6, 270, 180);
        g2.drawLine(ox+2, oy+14, ox+12, oy+14);  // saucer hint
        // steam
        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawArc(ox+3, oy-4, 4, 5, 0, 180);
        g2.drawArc(ox+8, oy-4, 4, 5, 0, 180);
        g2.dispose();
        return new ImageIcon(img);
    }

    private static Image createTrayImage() {
        int size = 16;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(ACCENT);
        g2.fillOval(0, 0, size, size);
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int cx = size/2, cy = size/2, r = size/2 - 2;
        g2.drawOval(cx-r, cy-r, r*2, r*2);
        g2.drawLine(cx, cy, cx, cy-r+2);
        g2.drawLine(cx, cy, cx+r-2, cy+1);
        g2.dispose();
        return img;
    }
}
