import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

// ═══════════════════════════════════════════════════════════════════════
//  DEAL OR NO DEAL  –  Single-file edition
//
//  Java 11+  →  java DealNoDeal.java          (no compile step needed)
//  Any Java  →  javac DealNoDeal.java && java DealNoDeal
// ═══════════════════════════════════════════════════════════════════════

public class DealNoDeal extends JFrame {

    public DealNoDeal() {
        super("Deal or No Deal");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().add(new GamePanel(), BorderLayout.CENTER);
        setPreferredSize(new Dimension(900, 620));
        pack();
        setLocationRelativeTo(null);
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); }
        catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new DealNoDeal().setVisible(true));
    }
}

// ───────────────────────────────────────────────────────────────────────
//  BACKEND: Game Engine
// ───────────────────────────────────────────────────────────────────────
class DealNoDealGame {

    static final long[] ALL_AMOUNTS = {
        1, 100, 200, 300, 400, 500, 750, 1_000,
        5_000, 10_000, 25_000, 50_000, 75_000,
        100_000, 200_000, 300_000, 400_000,
        500_000, 750_000, 1_000_000
    };
    static final int NUM_CASES = ALL_AMOUNTS.length;
    private static final int[] CASES_PER_ROUND = {6, 5, 4, 3, 2, 1, 1, 1, 1};

    enum Phase { PICK_OWN_CASE, OPENING, BANKER_OFFER, GAME_OVER }

    interface GameListener {
        void onPhaseChanged(Phase p);
        void onCaseOpened(int i, long amount);
        void onBankerOffer(long offer, double ev);
        void onGameOver(boolean deal, long winnings, long caseAmt);
    }

    private long[]       caseAmounts;
    private boolean[]    opened;
    private int          playerCase = -1;
    private Phase        phase;
    private int          currentRound, casesOpenedThisRound;
    private long         bankerOffer;
    private boolean      dealAccepted;
    private long         finalAmount;
    private GameListener listener;

    DealNoDealGame(GameListener l) { this.listener = l; init(); }

    private void init() {
        caseAmounts = new long[NUM_CASES];
        opened      = new boolean[NUM_CASES];
        playerCase  = -1; currentRound = 0; casesOpenedThisRound = 0;
        bankerOffer = 0;  dealAccepted = false; finalAmount = 0;
        phase = Phase.PICK_OWN_CASE;
        List<Long> amt = new ArrayList<>();
        for (long a : ALL_AMOUNTS) amt.add(a);
        Collections.shuffle(amt);
        for (int i = 0; i < NUM_CASES; i++) caseAmounts[i] = amt.get(i);
    }

    void pickOwnCase(int i) {
        if (phase != Phase.PICK_OWN_CASE) return;
        playerCase = i; phase = Phase.OPENING;
        listener.onPhaseChanged(phase);
    }

    boolean openCase(int i) {
        if (phase != Phase.OPENING || i == playerCase || opened[i]) return false;
        opened[i] = true;
        listener.onCaseOpened(i, caseAmounts[i]);
        casesOpenedThisRound++;
        if (casesOpenedThisRound >= casesThisRound()) {
            casesOpenedThisRound = 0;
            if (remaining() == 0) {
                finalAmount = caseAmounts[playerCase]; phase = Phase.GAME_OVER;
                listener.onGameOver(false, finalAmount, finalAmount);
            } else {
                phase = Phase.BANKER_OFFER;
                bankerOffer = calcOffer();
                listener.onBankerOffer(bankerOffer, ev());
                listener.onPhaseChanged(phase);
            }
        }
        return true;
    }

    void acceptDeal() {
        if (phase != Phase.BANKER_OFFER) return;
        dealAccepted = true; finalAmount = bankerOffer; phase = Phase.GAME_OVER;
        listener.onGameOver(true, finalAmount, caseAmounts[playerCase]);
    }

    void rejectDeal() {
        if (phase != Phase.BANKER_OFFER) return;
        currentRound++; phase = Phase.OPENING;
        listener.onPhaseChanged(phase);
    }

    void restart() { init(); listener.onPhaseChanged(phase); }

    private int casesThisRound() {
        return currentRound < CASES_PER_ROUND.length ? CASES_PER_ROUND[currentRound] : 1;
    }
    private int remaining() {
        int c = 0;
        for (int i = 0; i < NUM_CASES; i++) if (i != playerCase && !opened[i]) c++;
        return c;
    }
    double ev() {
        long s = 0; int c = 0;
        for (int i = 0; i < NUM_CASES; i++) if (!opened[i]) { s += caseAmounts[i]; c++; }
        return c == 0 ? 0 : (double) s / c;
    }
    private long calcOffer() {
        double factor = Math.min(1.0, 0.30 + currentRound * 0.10);
        double var    = 0.95 + Math.random() * 0.10;
        return Math.round(ev() * factor * var);
    }

    Phase   getPhase()                  { return phase; }
    int     getPlayerCase()             { return playerCase; }
    boolean isCaseOpened(int i)         { return opened[i]; }
    long    getCaseAmount(int i)        { return caseAmounts[i]; }
    long    getBankerOffer()            { return bankerOffer; }
    int     getCurrentRound()           { return currentRound; }
    int     getRemainingInRound()       { return casesThisRound() - casesOpenedThisRound; }

    static String fmt(long v) { return String.format("$%,d", v); }
    static String fmtShort(long v) {
        if (v >= 1_000_000) return "1M";
        if (v >= 1_000)     return (v / 1_000) + "K";
        return String.valueOf(v);
    }
}

// ───────────────────────────────────────────────────────────────────────
//  FRONTEND (shared): Game Panel — works in both JFrame and JApplet
// ───────────────────────────────────────────────────────────────────────
class GamePanel extends JPanel implements DealNoDealGame.GameListener {

    // Palette
    private static final Color BG_DARK      = new Color(12,  10,  30);
    private static final Color BG_MID       = new Color(22,  18,  55);
    private static final Color GOLD         = new Color(255, 195,   0);
    private static final Color GOLD_DARK    = new Color(180, 130,   0);
    private static final Color SILVER       = new Color(192, 192, 200);
    private static final Color RED_HOT      = new Color(220,  30,  50);
    private static final Color GREEN_BRIGHT = new Color(  0, 210, 100);
    private static final Color BLUE_BRIGHT  = new Color( 50, 150, 255);
    private static final Color TEXT_WHITE   = new Color(240, 235, 255);
    private static final Color TEXT_DIM     = new Color(130, 120, 160);
    private static final Color BANKER_DARK  = new Color( 30,  25,  70);

    private DealNoDealGame game;
    private JButton[]  caseButtons;
    private JLabel     statusLabel, roundLabel, toOpenLabel, offerLabel;
    private JPanel     bankerPanel, amountsPanel;
    private JLabel[]   amountLabels;
    private Set<Long>  eliminated = new HashSet<>();

    GamePanel() {
        setLayout(new BorderLayout(8, 8));
        setBackground(BG_DARK);
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        game = new DealNoDealGame(this);
        buildUI();
        refreshAmounts();
        updateStatus();
    }

    // ── Build UI ─────────────────────────────────────────────────────────────
    private void buildUI() {
        // Title + status bar
        JLabel title = label("DEAL OR NO DEAL", new Font("Georgia", Font.BOLD, 28), GOLD, SwingConstants.CENTER);
        statusLabel  = label("Pick YOUR briefcase!", new Font("SansSerif", Font.BOLD, 14), TEXT_WHITE, SwingConstants.CENTER);
        roundLabel   = label("",                     new Font("SansSerif", Font.PLAIN, 12), TEXT_DIM,   SwingConstants.CENTER);
        toOpenLabel  = label("",                     new Font("SansSerif", Font.PLAIN, 12), BLUE_BRIGHT,SwingConstants.CENTER);

        JPanel top = new JPanel(new GridLayout(4, 1, 0, 2));
        top.setOpaque(false);
        top.add(title); top.add(statusLabel); top.add(roundLabel); top.add(toOpenLabel);
        add(top, BorderLayout.NORTH);

        // Centre
        JPanel centre = new JPanel(new BorderLayout(8, 0));
        centre.setOpaque(false);
        centre.add(buildAmountsPanel(), BorderLayout.WEST);
        centre.add(buildCasesGrid(),    BorderLayout.CENTER);
        bankerPanel = buildBankerPanel();
        bankerPanel.setVisible(false);
        centre.add(bankerPanel, BorderLayout.EAST);
        add(centre, BorderLayout.CENTER);

        // Bottom
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 4));
        bottom.setOpaque(false);
        JButton restart = styledBtn("↺  New Game", SILVER, BG_MID);
        restart.addActionListener(e -> restartGame());
        bottom.add(restart);
        add(bottom, BorderLayout.SOUTH);
    }

    private JPanel buildAmountsPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG_MID);
        p.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(GOLD_DARK, 1, true),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        p.setPreferredSize(new Dimension(130, 0));

        JLabel hdr = label("PRIZE BOARD", new Font("Georgia", Font.BOLD, 11), GOLD, SwingConstants.CENTER);
        hdr.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(hdr);
        p.add(Box.createVerticalStrut(6));

        long[] amounts = DealNoDealGame.ALL_AMOUNTS;
        amountLabels = new JLabel[amounts.length];
        for (int i = amounts.length - 1; i >= 0; i--) {
            JLabel lbl = label(DealNoDealGame.fmt(amounts[i]),
                               new Font("Monospaced", Font.BOLD, 11),
                               amtColor(amounts[i]), SwingConstants.CENTER);
            lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
            lbl.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
            amountLabels[i] = lbl;
            p.add(lbl);
            p.add(Box.createVerticalStrut(2));
        }
        return p;
    }

    private JPanel buildCasesGrid() {
        JPanel p = new JPanel(new GridLayout(4, 5, 8, 8));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        caseButtons = new JButton[DealNoDealGame.NUM_CASES];
        for (int i = 0; i < DealNoDealGame.NUM_CASES; i++) {
            final int idx = i;
            caseButtons[i] = buildCaseButton(i + 1);
            caseButtons[i].addActionListener(e -> onCaseClick(idx));
            p.add(caseButtons[i]);
        }
        return p;
    }

    private JPanel buildBankerPanel() {
        JPanel p = new JPanel(new GridLayout(5, 1, 0, 6));
        p.setBackground(BANKER_DARK);
        p.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(GOLD_DARK, 2, true),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        p.setPreferredSize(new Dimension(160, 0));

        offerLabel = label("$0", new Font("Georgia", Font.BOLD, 24), GREEN_BRIGHT, SwingConstants.CENTER);

        JButton deal   = styledBtn("DEAL!",   GREEN_BRIGHT, BG_DARK);
        JButton noDeal = styledBtn("NO DEAL", RED_HOT,      BG_DARK);
        deal.addActionListener(e -> game.acceptDeal());
        noDeal.addActionListener(e -> game.rejectDeal());

        p.add(label("☎  THE BANKER", new Font("Georgia", Font.BOLD, 13), GOLD, SwingConstants.CENTER));
        p.add(label("OFFERS YOU:",   new Font("SansSerif", Font.PLAIN, 11), TEXT_DIM, SwingConstants.CENTER));
        p.add(offerLabel);
        p.add(deal);
        p.add(noDeal);
        return p;
    }

    // ── Case Button ──────────────────────────────────────────────────────────
    private JButton buildCaseButton(int num) {
        JButton btn = new JButton(String.valueOf(num)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg  = (Color) getClientProperty("bg");
                Color bdr = (Color) getClientProperty("bdr");
                if (bg  == null) bg  = new Color(40, 60, 130);
                if (bdr == null) bdr = GOLD_DARK;
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(bdr);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, getWidth()-2, getHeight()-2, 12, 12);
                g2.setColor(new Color(0,0,0,50));
                g2.fillRoundRect(6, 4, getWidth()-12, getHeight()/2, 4, 4);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Georgia", Font.BOLD, 15));
        btn.setForeground(TEXT_WHITE);
        btn.setContentAreaFilled(false); btn.setBorderPainted(false); btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.putClientProperty("bg",  new Color(40, 60, 130));
        btn.putClientProperty("bdr", GOLD_DARK);
        btn.setPreferredSize(new Dimension(70, 55));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                if (btn.isEnabled()) { btn.putClientProperty("bg", new Color(60, 90, 180)); btn.repaint(); }
            }
            public void mouseExited(MouseEvent e) {
                Color c = (Color) btn.getClientProperty("bg");
                if (c != null && !c.equals(RED_HOT) && !c.equals(GOLD) && !c.equals(new Color(20,20,40)))
                    { btn.putClientProperty("bg", new Color(40, 60, 130)); btn.repaint(); }
            }
        });
        return btn;
    }

    // ── Listener Callbacks ────────────────────────────────────────────────────
    @Override public void onPhaseChanged(DealNoDealGame.Phase ph) {
        SwingUtilities.invokeLater(() -> {
            updateStatus();
            bankerPanel.setVisible(ph == DealNoDealGame.Phase.BANKER_OFFER);
            if (ph == DealNoDealGame.Phase.OPENING)
                for (int i = 0; i < DealNoDealGame.NUM_CASES; i++)
                    if (!game.isCaseOpened(i) && i != game.getPlayerCase())
                        caseButtons[i].setEnabled(true);
        });
    }

    @Override public void onCaseOpened(int i, long amount) {
        SwingUtilities.invokeLater(() -> {
            markOpened(i, amount);
            eliminated.add(amount);
            refreshAmounts();
            updateStatus();
        });
    }

    @Override public void onBankerOffer(long offer, double ev) {
        SwingUtilities.invokeLater(() -> {
            offerLabel.setText(DealNoDealGame.fmt(offer));
            double r = ev > 0 ? (double) offer / ev : 0;
            offerLabel.setForeground(r >= 0.85 ? GREEN_BRIGHT : r >= 0.60 ? GOLD : RED_HOT);
            for (JButton b : caseButtons) b.setEnabled(false);
            updateStatus();
        });
    }

    @Override public void onGameOver(boolean deal, long win, long caseAmt) {
        SwingUtilities.invokeLater(() -> {
            for (JButton b : caseButtons) b.setEnabled(false);
            bankerPanel.setVisible(false);
            for (int i = 0; i < DealNoDealGame.NUM_CASES; i++)
                if (!game.isCaseOpened(i)) markOpened(i, game.getCaseAmount(i));

            String msg = deal
                ? "<html><center><b>You took the DEAL!</b><br/>You win: <font color='#00D264'>"
                  + DealNoDealGame.fmt(win) + "</font><br/><font size='2'>(Your case had "
                  + DealNoDealGame.fmt(caseAmt) + ")</font></center></html>"
                : "<html><center><b>No Deal — You kept your case!</b><br/>You win: <font color='#FFC300'>"
                  + DealNoDealGame.fmt(win) + "</font></center></html>";
            JOptionPane.showMessageDialog(this, msg, "GAME OVER", JOptionPane.INFORMATION_MESSAGE);
            statusLabel.setText("Game Over! Press New Game to play again.");
            toOpenLabel.setText("");
        });
    }

    // ── Interaction ──────────────────────────────────────────────────────────
    private void onCaseClick(int idx) {
        if (game.getPhase() == DealNoDealGame.Phase.PICK_OWN_CASE) {
            game.pickOwnCase(idx);
            JButton b = caseButtons[idx];
            b.setEnabled(false);
            b.setText("<html><center>★<br/><font size='1'>YOURS</font></center></html>");
            b.setForeground(BG_DARK);
            b.putClientProperty("bg",  GOLD);
            b.putClientProperty("bdr", GOLD);
            b.repaint();
        } else if (game.getPhase() == DealNoDealGame.Phase.OPENING) {
            game.openCase(idx);
        }
    }

    private void restartGame() {
        eliminated.clear();
        game.restart();
        for (int i = 0; i < DealNoDealGame.NUM_CASES; i++) {
            JButton b = caseButtons[i];
            b.setText(String.valueOf(i + 1));
            b.setForeground(TEXT_WHITE);
            b.putClientProperty("bg",  new Color(40, 60, 130));
            b.putClientProperty("bdr", GOLD_DARK);
            b.setEnabled(true);
            b.repaint();
        }
        bankerPanel.setVisible(false);
        refreshAmounts();
        updateStatus();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private void markOpened(int i, long amount) {
        JButton b = caseButtons[i];
        b.setEnabled(false);
        b.setText("<html><center><font size='1'>$</font><br/>"
            + DealNoDealGame.fmtShort(amount) + "</center></html>");
        b.setForeground(amtColor(amount));
        b.putClientProperty("bg",  new Color(20, 20, 40));
        b.putClientProperty("bdr", new Color(60, 60, 80));
        b.repaint();
    }

    private void refreshAmounts() {
        long[] amounts = DealNoDealGame.ALL_AMOUNTS;
        for (int i = 0; i < amounts.length; i++) {
            if (eliminated.contains(amounts[i])) {
                amountLabels[i].setForeground(new Color(60, 55, 80));
                amountLabels[i].setText("<html><strike>" + DealNoDealGame.fmt(amounts[i]) + "</strike></html>");
            } else {
                amountLabels[i].setForeground(amtColor(amounts[i]));
                amountLabels[i].setText(DealNoDealGame.fmt(amounts[i]));
            }
        }
    }

    private void updateStatus() {
        switch (game.getPhase()) {
            case PICK_OWN_CASE:
                statusLabel.setText("Pick YOUR briefcase to keep!");
                roundLabel.setText("Choose wisely — it stays closed until the end.");
                toOpenLabel.setText(""); break;
            case OPENING:
                int rem = game.getRemainingInRound();
                statusLabel.setText("Open a briefcase!");
                roundLabel.setText("Round " + (game.getCurrentRound() + 1));
                toOpenLabel.setText("Open " + rem + " more briefcase" + (rem != 1 ? "s" : "") + " this round."); break;
            case BANKER_OFFER:
                statusLabel.setText("☎  The Banker is calling…");
                roundLabel.setText("Round " + (game.getCurrentRound() + 1) + " complete");
                toOpenLabel.setText(""); break;
            default: break;
        }
    }

    private Color amtColor(long v) {
        if (v >= 500_000) return new Color(255,  80,  80);
        if (v >= 100_000) return new Color(255, 150,  50);
        if (v >=  10_000) return GOLD;
        if (v >=   1_000) return new Color(200, 200, 120);
        return new Color(160, 200, 255);
    }

    private JLabel label(String text, Font f, Color fg, int align) {
        JLabel l = new JLabel(text, align);
        l.setFont(f); l.setForeground(fg); return l;
    }

    private JButton styledBtn(String text, Color fg, Color bg) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? fg.darker() : bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(fg); g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1, 1, getWidth()-2, getHeight()-2, 10, 10);
                g2.dispose(); super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Georgia", Font.BOLD, 13));
        btn.setForeground(fg);
        btn.setContentAreaFilled(false); btn.setBorderPainted(false); btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }
}

// ───────────────────────────────────────────────────────────────────────
//  APPLET FRONTEND  (JApplet — deprecated but functional)
//  Run with:  appletviewer DealNoDealApplet.html
// ───────────────────────────────────────────────────────────────────────
@SuppressWarnings("deprecation")
class DealNoDealApplet extends JApplet {
    @Override public void init() {
        try {
            SwingUtilities.invokeAndWait(() -> {
                getContentPane().setLayout(new BorderLayout());
                getContentPane().add(new GamePanel(), BorderLayout.CENTER);
            });
        } catch (Exception e) { e.printStackTrace(); }
    }
}
