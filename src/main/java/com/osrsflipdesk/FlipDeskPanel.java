package com.osrsflipdesk;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

final class FlipDeskPanel extends PluginPanel
{
    private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance(Locale.US);
    private static final SimpleDateFormat DATE = new SimpleDateFormat("MMM d, h:mm a", Locale.US);

    private static final Color SECONDARY_TEXT = new Color(205, 210, 218);
    private static final Color MUTED_TEXT = new Color(175, 182, 192);
    private static final Color PROFIT_GREEN = new Color(105, 220, 135);
    private static final Color PROFIT_RED = new Color(240, 105, 105);
    private static final Color BUY_GREEN = new Color(138, 223, 138);
    private static final Color SELL_BLUE = new Color(117, 191, 255);

    private static final Font TITLE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 18);
    private static final Font HEADING_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 15);
    private static final Font BODY_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
    private static final Font BODY_BOLD = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    private static final Font PRICE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 17);
    private static final Font META_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    private static final Font BUTTON_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 13);

    private final FlipDeskPlugin plugin;
    private final FlipDeskConfig config;

    private final JTextField gpField = new JTextField();
    private final JButton refreshButton = new JButton("Refresh");
    private final JLabel status = new JLabel("Loading market data…");
    private final JPanel results = verticalPanel();
    private final JPanel activeResults = verticalPanel();
    private final JPanel historyResults = verticalPanel();
    private final JLabel totalProfit = new JLabel("Total profit: 0 gp");
    private final JLabel activeSummary = new JLabel("No active flips");

    @Inject
    FlipDeskPanel(FlipDeskPlugin plugin, FlipDeskConfig config)
    {
        // false = no PluginPanel outer JScrollPane. Nested scroll panes eat mouse-wheel
        // events (scrollbar still works; wheel does nothing). Each tab has its own scroller.
        super(false);

        this.plugin = plugin;
        this.config = config;

        setLayout(new BorderLayout(0, 8));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(ColorScheme.DARK_GRAY_COLOR);
        header.setOpaque(false);

        JLabel title = new JLabel("OSRS Flip Desk");
        title.setFont(TITLE_FONT);
        title.setForeground(ColorScheme.BRAND_ORANGE);
        title.setAlignmentX(LEFT_ALIGNMENT);
        header.add(title);

        JLabel subtitle = label("Set Flip strictness in settings if the list looks empty.", META_FONT, MUTED_TEXT);
        header.add(subtitle);
        header.add(Box.createRigidArea(new Dimension(0, 6)));

        JPanel money = new JPanel(new BorderLayout(6, 0));
        money.setBackground(ColorScheme.DARK_GRAY_COLOR);
        money.setAlignmentX(LEFT_ALIGNMENT);
        money.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));

        JLabel gpLabel = label("GP on hand", BODY_BOLD, Color.WHITE);
        money.add(gpLabel, BorderLayout.NORTH);

        gpField.setText(NUMBER.format(config.gpOnHand()));
        gpField.setFont(PRICE_FONT);
        money.add(gpField, BorderLayout.CENTER);
        refreshButton.setFont(BUTTON_FONT);
        money.add(refreshButton, BorderLayout.EAST);
        header.add(money);

        header.add(label("Press Enter after changing GP.", META_FONT, MUTED_TEXT));
        header.add(Box.createRigidArea(new Dimension(0, 6)));

        totalProfit.setFont(HEADING_FONT);
        totalProfit.setForeground(PROFIT_GREEN);
        totalProfit.setAlignmentX(LEFT_ALIGNMENT);
        header.add(totalProfit);

        activeSummary.setFont(BODY_FONT);
        activeSummary.setForeground(SECONDARY_TEXT);
        activeSummary.setAlignmentX(LEFT_ALIGNMENT);
        header.add(activeSummary);

        gpField.addActionListener(e -> saveGpAndRefresh());
        refreshButton.addActionListener(e -> {
            saveGp();
            plugin.refreshNow();
        });

        add(header, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(BODY_FONT);

        JPanel recommendedTab = new JPanel();
        recommendedTab.setLayout(new BoxLayout(recommendedTab, BoxLayout.Y_AXIS));
        recommendedTab.setBackground(ColorScheme.DARK_GRAY_COLOR);
        status.setFont(BODY_FONT);
        status.setForeground(SECONDARY_TEXT);
        status.setAlignmentX(LEFT_ALIGNMENT);
        recommendedTab.add(status);
        recommendedTab.add(Box.createRigidArea(new Dimension(0, 4)));
        recommendedTab.add(results);

        JPanel activeTab = new JPanel();
        activeTab.setLayout(new BoxLayout(activeTab, BoxLayout.Y_AXIS));
        activeTab.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JButton clearActiveButton = new JButton("Clear all active");
        clearActiveButton.setFont(BUTTON_FONT);
        clearActiveButton.setAlignmentX(LEFT_ALIGNMENT);
        clearActiveButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, clearActiveButton.getPreferredSize().height));
        clearActiveButton.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(
                dialogParent(),
                "Remove all active flips? This does not change total realized profit.",
                "Clear active flips",
                JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION)
            {
                plugin.clearActiveFlips();
            }
        });
        activeTab.add(clearActiveButton);
        activeTab.add(Box.createRigidArea(new Dimension(0, 4)));
        activeTab.add(activeResults);

        JPanel historyTab = new JPanel();
        historyTab.setLayout(new BoxLayout(historyTab, BoxLayout.Y_AXIS));
        historyTab.setBackground(ColorScheme.DARK_GRAY_COLOR);
        historyTab.add(historyResults);

        tabs.addTab("Flips", wrapScroll(recommendedTab));
        tabs.addTab("Active", wrapScroll(activeTab));
        tabs.addTab("History", wrapScroll(historyTab));

        add(tabs, BorderLayout.CENTER);
    }

    void setLoading()
    {
        status.setText("Updating market… (first load can take up to 30 seconds)");
        refreshButton.setEnabled(false);
    }

    void setError(String message)
    {
        status.setForeground(PROFIT_RED);
        status.setText("Update failed. " + (message == null ? "" : message));
        refreshButton.setEnabled(true);
    }

    void setOpportunities(List<FlipOpportunity> opportunities, String sortLabel)
    {
        refreshButton.setEnabled(true);
        results.removeAll();

        List<FlipOpportunity> rows = opportunities == null ? Collections.emptyList() : opportunities;
        String labelText = sortLabel == null || sortLabel.isEmpty() ? "total profit" : sortLabel;
        status.setForeground(SECONDARY_TEXT);
        status.setText(rows.isEmpty()
            ? "No flips matched. Lower Minimum flip profit, raise GP on hand, or try More flips."
            : "Top " + rows.size() + " flips sorted by " + labelText + " (scroll for more)");

        int rank = 1;
        for (FlipOpportunity flip : rows)
        {
            results.add(buildOpportunityCard(rank++, flip));
            results.add(Box.createRigidArea(new Dimension(0, 4)));
        }

        results.revalidate();
        results.repaint();
    }

    void setPositions(
        List<PositionStore.Position> active,
        List<PositionStore.Position> history,
        long cumulativeProfit)
    {
        totalProfit.setText("Total profit: " + gp(cumulativeProfit));
        totalProfit.setForeground(profitColor(cumulativeProfit));
        activeSummary.setText(active == null || active.isEmpty()
            ? "No active flips"
            : active.size() + " active flip" + (active.size() == 1 ? "" : "s") + " (scroll for more)");

        activeResults.removeAll();
        if (active == null || active.isEmpty())
        {
            activeResults.add(label("No in-progress flips yet.", BODY_FONT, SECONDARY_TEXT));
        }
        else
        {
            for (PositionStore.Position p : active)
            {
                activeResults.add(buildActiveCard(p));
                activeResults.add(Box.createRigidArea(new Dimension(0, 4)));
            }
        }

        historyResults.removeAll();
        if (history == null || history.isEmpty())
        {
            historyResults.add(label("Completed flips will appear here.", BODY_FONT, SECONDARY_TEXT));
        }
        else
        {
            for (PositionStore.Position p : history)
            {
                historyResults.add(buildHistoryCard(p));
                historyResults.add(Box.createRigidArea(new Dimension(0, 4)));
            }
        }

        activeResults.revalidate();
        activeResults.repaint();
        historyResults.revalidate();
        historyResults.repaint();
    }

    private JPanel buildOpportunityCard(int rank, FlipOpportunity f)
    {
        JPanel card = card();

        card.add(label("#" + rank + " " + nullToEmpty(f.name), HEADING_FONT, Color.WHITE));

        JPanel prices = new JPanel(new GridLayout(1, 2, 6, 0));
        prices.setOpaque(false);
        prices.setAlignmentX(LEFT_ALIGNMENT);
        prices.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        prices.add(priceBlock("BUY ≤", gp(f.buyPrice), BUY_GREEN));
        prices.add(priceBlock("SELL ≥", gp(f.sellPrice), SELL_BLUE));
        card.add(prices);

        card.add(label(
            "Patient: " + gp(f.patientBuy) + " / " + gp(f.patientSell),
            META_FONT,
            MUTED_TEXT));
        long capital = (long) f.buyPrice * f.quantity;
        card.add(label(
            "~" + gp(f.totalProfit) + "  •  "
                + NUMBER.format(f.quantity) + " qty  •  "
                + gp(capital) + " in  •  "
                + String.format(Locale.US, "%.2f%% ROI", f.roi),
            BODY_BOLD,
            profitColor(f.totalProfit)));

        String limitText = f.remainingBuyLimit < f.buyLimit
            ? "Limit " + NUMBER.format(f.remainingBuyLimit) + "/" + NUMBER.format(f.buyLimit) + " left"
            : "Limit " + NUMBER.format(f.buyLimit);
        card.add(label(
            limitText + "  •  "
                + f.confidence + "% conf  •  "
                + NUMBER.format(f.volume5m) + " vol  •  "
                + f.quoteAgeSeconds + "s"
                + (f.learnedFills > 0 ? "  •  " + f.learnedFills + " fills" : ""),
            META_FONT,
            f.remainingBuyLimit < f.buyLimit ? new Color(255, 196, 120) : SECONDARY_TEXT));

        return card;
    }

    private JPanel buildActiveCard(PositionStore.Position p)
    {
        JPanel card = card();

        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setOpaque(false);
        header.setAlignmentX(LEFT_ALIGNMENT);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        header.add(label(nullToEmpty(p.itemName), HEADING_FONT, Color.WHITE), BorderLayout.CENTER);

        JButton removeButton = new JButton("Remove");
        removeButton.setFont(BUTTON_FONT);
        removeButton.setToolTipText("Remove this flip from Active tracking");
        final long positionId = p.id;
        final String itemName = p.itemName;
        removeButton.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(
                dialogParent(),
                "Remove \"" + itemName + "\" from active flips?",
                "Remove active flip",
                JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION)
            {
                plugin.removeActiveFlip(positionId);
            }
        });
        header.add(removeButton, BorderLayout.EAST);
        card.add(header);

        card.add(label(
            NUMBER.format(p.quantityRemaining) + " left  •  "
                + NUMBER.format(p.quantitySold) + "/" + NUMBER.format(p.quantityBought) + " sold",
            BODY_FONT,
            SECONDARY_TEXT));

        int buyLimit = plugin.getBuyLimit(p.itemId);
        int remainingLimit = plugin.getRemainingBuyLimit(p.itemId);
        if (buyLimit > 0)
        {
            Color limitColor = remainingLimit <= 0
                ? PROFIT_RED
                : (remainingLimit < buyLimit ? new Color(255, 196, 120) : SECONDARY_TEXT);
            String limitLabel = remainingLimit <= 0
                ? "Buy limit reached (0/" + NUMBER.format(buyLimit) + ")"
                : "Buy limit " + NUMBER.format(remainingLimit) + "/" + NUMBER.format(buyLimit) + " left";
            card.add(label(limitLabel, META_FONT, limitColor));
        }

        card.add(label(
            "Buy " + gp(p.averageBuyPrice) + "  →  Sell " + gp(p.recommendedSellPrice),
            BODY_BOLD,
            Color.WHITE));

        long unrealizedEstimate = 0L;
        if (p.recommendedSellPrice > 0 && p.quantityRemaining > 0)
        {
            int netPer = p.recommendedSellPrice - p.averageBuyPrice - MarketClient.geTax(p.recommendedSellPrice);
            unrealizedEstimate = (long) netPer * p.quantityRemaining;
        }

        card.add(label(
            "Est " + gp(unrealizedEstimate) + "  •  Realized " + gp(p.realizedProfit),
            BODY_BOLD,
            profitColor(unrealizedEstimate)));
        card.add(label("Opened " + DATE.format(new Date(p.openedAt)), META_FONT, MUTED_TEXT));

        return card;
    }

    private JPanel buildHistoryCard(PositionStore.Position p)
    {
        JPanel card = card();

        card.add(label(nullToEmpty(p.itemName), HEADING_FONT, Color.WHITE));
        card.add(label(
            NUMBER.format(p.quantityBought) + " qty  •  "
                + gp(p.averageBuyPrice) + " → " + gp(p.lastSellPrice),
            BODY_FONT,
            SECONDARY_TEXT));
        card.add(label("Profit: " + gp(p.realizedProfit), BODY_BOLD, profitColor(p.realizedProfit)));
        card.add(label(
            DATE.format(new Date(p.openedAt)) + " → " + DATE.format(new Date(p.closedAt)),
            META_FONT,
            MUTED_TEXT));

        return card;
    }

    /**
     * Parent confirm dialogs to the RuneLite window, not the narrow sidebar panel.
     * Sidebar parenting places the dialog near the panel edge and often clips Yes/No off-screen.
     */
    private Component dialogParent()
    {
        Window window = SwingUtilities.getWindowAncestor(this);
        return window != null ? window : this;
    }

    private static JScrollPane wrapScroll(JPanel content)
    {
        // Keep content top-aligned so BoxLayout children don't stretch to fill the viewport.
        JPanel northWrap = new JPanel(new BorderLayout());
        northWrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
        northWrap.add(content, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(northWrap);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setWheelScrollingEnabled(true);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.getVerticalScrollBar().setBlockIncrement(96);
        return scroll;
    }

    private static JPanel priceBlock(String caption, String value, Color valueColor)
    {
        JPanel block = new JPanel();
        block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
        block.setOpaque(false);

        JLabel captionLabel = label(caption, META_FONT, valueColor);
        captionLabel.setAlignmentX(CENTER_ALIGNMENT);
        block.add(captionLabel);

        JLabel valueLabel = label(value, PRICE_FONT, Color.WHITE);
        valueLabel.setAlignmentX(CENTER_ALIGNMENT);
        block.add(valueLabel);

        return block;
    }

    private static JPanel verticalPanel()
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        return panel;
    }

    private static JPanel card()
    {
        JPanel card = new JPanel()
        {
            @Override
            public Dimension getMaximumSize()
            {
                Dimension preferred = getPreferredSize();
                return new Dimension(Integer.MAX_VALUE, preferred.height);
            }
        };
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(70, 73, 78)),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        card.setAlignmentX(LEFT_ALIGNMENT);
        return card;
    }

    void setGpOnHandDisplay(int gp)
    {
        gpField.setText(NUMBER.format(Math.max(0, gp)));
    }

    private void saveGpAndRefresh()
    {
        if (saveGp())
        {
            plugin.refreshNow();
        }
    }

    private boolean saveGp()
    {
        String raw = gpField.getText().replace(",", "").trim();
        try
        {
            long parsed = Long.parseLong(raw);
            int gp = (int) Math.max(0, Math.min(Integer.MAX_VALUE, parsed));
            gpField.setText(NUMBER.format(gp));
            plugin.setGpOnHand(gp);
            return true;
        }
        catch (NumberFormatException ex)
        {
            status.setForeground(PROFIT_RED);
            status.setText("Enter GP as a whole number, e.g. 50,000,000.");
            return false;
        }
    }

    private static JLabel label(String text, Font font, Color color)
    {
        JLabel label = new JLabel(text == null ? "" : text);
        label.setFont(font);
        label.setForeground(color);
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private static Color profitColor(long value)
    {
        return value >= 0 ? PROFIT_GREEN : PROFIT_RED;
    }

    private static String gp(long value)
    {
        return NUMBER.format(value) + " gp";
    }

    private static String nullToEmpty(String s)
    {
        return s == null ? "" : s;
    }
}
