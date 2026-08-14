package com.osrsflipdesk;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
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

    /**
     * Text wrap width for sidebar content.
     * PANEL_WIDTH includes the scrollbar gutter; cards also have horizontal insets.
     */
    private static final int WRAP_WIDTH = PluginPanel.PANEL_WIDTH
        - PluginPanel.SCROLLBAR_WIDTH
        - PluginPanel.BORDER_OFFSET * 2
        - 24;
    private static final int WRAP_WIDTH_NARROW = Math.max(100, WRAP_WIDTH - 78);

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
    private final JLabel historySummary = new JLabel("0 trades");
    private final JComboBox<HistorySortMode> historySort = new JComboBox<>(HistorySortMode.values());
    private List<PositionStore.Position> lastHistory = Collections.emptyList();

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

        header.add(label("Set Flip strictness in settings if the list looks empty.", META_FONT, MUTED_TEXT));
        header.add(Box.createRigidArea(new Dimension(0, 6)));

        JPanel money = new JPanel(new BorderLayout(6, 0));
        money.setBackground(ColorScheme.DARK_GRAY_COLOR);
        money.setAlignmentX(LEFT_ALIGNMENT);
        money.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));

        money.add(label("GP on hand", BODY_BOLD, Color.WHITE), BorderLayout.NORTH);

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
        setLabelText(totalProfit, "Total profit: 0 gp");
        header.add(totalProfit);

        activeSummary.setFont(BODY_FONT);
        activeSummary.setForeground(SECONDARY_TEXT);
        activeSummary.setAlignmentX(LEFT_ALIGNMENT);
        setLabelText(activeSummary, "No active flips");
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
        setLabelText(status, "Loading market data…");
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

        historySummary.setFont(BODY_FONT);
        historySummary.setForeground(SECONDARY_TEXT);
        historySummary.setAlignmentX(LEFT_ALIGNMENT);
        setLabelText(historySummary, "0 trades");
        historyTab.add(historySummary);
        historyTab.add(Box.createRigidArea(new Dimension(0, 4)));

        JPanel historyControls = new JPanel(new BorderLayout(8, 0));
        historyControls.setOpaque(false);
        historyControls.setAlignmentX(LEFT_ALIGNMENT);
        historyControls.add(plainLabel("Sort", META_FONT, MUTED_TEXT), BorderLayout.WEST);
        historySort.setFont(BUTTON_FONT);
        historySort.setSelectedItem(HistorySortMode.MOST_RECENT);
        historySort.addActionListener(e -> renderHistory());
        // Keep the combo readable in the narrow sidebar (don't force a 28px clip height).
        Dimension sortSize = historySort.getPreferredSize();
        historySort.setPreferredSize(new Dimension(Math.max(120, sortSize.width), sortSize.height));
        historyControls.add(historySort, BorderLayout.CENTER);
        historyControls.setMaximumSize(new Dimension(
            Integer.MAX_VALUE,
            Math.max(sortSize.height, historyControls.getPreferredSize().height) + 2));
        historyTab.add(historyControls);
        historyTab.add(Box.createRigidArea(new Dimension(0, 4)));

        JButton exportHistoryButton = new JButton("Export CSV");
        exportHistoryButton.setFont(BUTTON_FONT);
        exportHistoryButton.setAlignmentX(LEFT_ALIGNMENT);
        exportHistoryButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, exportHistoryButton.getPreferredSize().height));
        exportHistoryButton.setToolTipText("Save completed flip history as a CSV you can open in Excel");
        exportHistoryButton.addActionListener(e -> exportHistory());
        historyTab.add(exportHistoryButton);
        historyTab.add(Box.createRigidArea(new Dimension(0, 4)));
        historyTab.add(historyResults);

        tabs.addTab("Flips", wrapScroll(recommendedTab));
        tabs.addTab("Active", wrapScroll(activeTab));
        tabs.addTab("History", wrapScroll(historyTab));

        add(tabs, BorderLayout.CENTER);
    }

    void setLoading()
    {
        setLabelText(status, "Updating market… (first load can take up to 30s)");
        refreshButton.setEnabled(false);
    }

    void setError(String message)
    {
        status.setForeground(PROFIT_RED);
        setLabelText(status, "Update failed. " + (message == null ? "" : message));
        refreshButton.setEnabled(true);
    }

    void setOpportunities(List<FlipOpportunity> opportunities, String sortLabel)
    {
        refreshButton.setEnabled(true);
        results.removeAll();

        List<FlipOpportunity> rows = opportunities == null ? Collections.emptyList() : opportunities;
        String labelText = sortLabel == null || sortLabel.isEmpty() ? "total profit" : sortLabel;
        status.setForeground(SECONDARY_TEXT);
        setLabelText(status, rows.isEmpty()
            ? "No flips matched. Lower Minimum flip profit, raise GP on hand, or try More flips."
            : "Top " + rows.size() + " flips by " + labelText + " (scroll for more)");

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
        setLabelText(totalProfit, "Total profit: " + gp(cumulativeProfit));
        totalProfit.setForeground(profitColor(cumulativeProfit));
        setLabelText(activeSummary, active == null || active.isEmpty()
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

        activeResults.revalidate();
        activeResults.repaint();

        lastHistory = history == null
            ? Collections.emptyList()
            : new ArrayList<>(history);
        renderHistory();
    }

    private void renderHistory()
    {
        historyResults.removeAll();

        int tradeCount = lastHistory.size();
        HistorySortMode sortMode = historySort.getSelectedItem() instanceof HistorySortMode
            ? (HistorySortMode) historySort.getSelectedItem()
            : HistorySortMode.MOST_RECENT;

        setLabelText(historySummary, tradeCount == 0
            ? "0 trades"
            : NUMBER.format(tradeCount) + " trade" + (tradeCount == 1 ? "" : "s")
                + "  •  sorted by " + sortMode.toString().toLowerCase(Locale.US));

        if (tradeCount == 0)
        {
            historyResults.add(label("Completed flips will appear here.", BODY_FONT, SECONDARY_TEXT));
        }
        else
        {
            for (PositionStore.Position p : sortedHistoryCopy())
            {
                historyResults.add(buildHistoryCard(p));
                historyResults.add(Box.createRigidArea(new Dimension(0, 4)));
            }
        }

        historyResults.revalidate();
        historyResults.repaint();
    }

    private void exportHistory()
    {
        if (lastHistory.isEmpty())
        {
            JOptionPane.showMessageDialog(
                dialogParent(),
                "No completed trades to export yet.",
                "Export history",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US));
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export flip history");
        chooser.setSelectedFile(new java.io.File("osrs-flip-desk-history-" + stamp + ".csv"));
        chooser.setFileFilter(new FileNameExtensionFilter("CSV (Excel)", "csv"));

        if (chooser.showSaveDialog(dialogParent()) != JFileChooser.APPROVE_OPTION)
        {
            return;
        }

        Path path = chooser.getSelectedFile().toPath();
        if (!path.getFileName().toString().toLowerCase(Locale.US).endsWith(".csv"))
        {
            path = path.resolveSibling(path.getFileName().toString() + ".csv");
        }

        try
        {
            List<PositionStore.Position> rows = sortedHistoryCopy();
            HistoryExport.writeCsv(path, rows);
            JOptionPane.showMessageDialog(
                dialogParent(),
                "Exported " + NUMBER.format(rows.size()) + " trade"
                    + (rows.size() == 1 ? "" : "s") + " to:\n" + path,
                "Export history",
                JOptionPane.INFORMATION_MESSAGE);
        }
        catch (Exception ex)
        {
            JOptionPane.showMessageDialog(
                dialogParent(),
                "Export failed:\n" + ex.getMessage(),
                "Export history",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private List<PositionStore.Position> sortedHistoryCopy()
    {
        List<PositionStore.Position> rows = new ArrayList<>(lastHistory);
        HistorySortMode sortMode = historySort.getSelectedItem() instanceof HistorySortMode
            ? (HistorySortMode) historySort.getSelectedItem()
            : HistorySortMode.MOST_RECENT;

        if (sortMode == HistorySortMode.MOST_PROFITABLE)
        {
            rows.sort(
                Comparator.comparingLong((PositionStore.Position p) -> p.realizedProfit).reversed()
                    .thenComparing(Comparator.comparingLong((PositionStore.Position p) -> p.closedAt).reversed()));
        }
        else
        {
            rows.sort(Comparator.comparingLong((PositionStore.Position p) -> p.closedAt).reversed());
        }
        return rows;
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
                + String.format(Locale.US, "%.2f%% ROI", f.roi),
            BODY_BOLD,
            profitColor(f.totalProfit)));
        card.add(label(
            gp(capital) + " capital in",
            META_FONT,
            MUTED_TEXT));

        String limitText = f.remainingBuyLimit < f.buyLimit
            ? "Limit " + NUMBER.format(f.remainingBuyLimit) + "/" + NUMBER.format(f.buyLimit)
            : "Limit " + NUMBER.format(f.buyLimit);
        card.add(label(
            limitText + "  •  " + f.confidence + "% conf",
            META_FONT,
            f.remainingBuyLimit < f.buyLimit ? new Color(255, 196, 120) : SECONDARY_TEXT));
        card.add(label(
            NUMBER.format(f.volume5m) + " vol  •  " + f.quoteAgeSeconds + "s"
                + (f.learnedFills > 0 ? "  •  " + f.learnedFills + " fills" : ""),
            META_FONT,
            MUTED_TEXT));

        return card;
    }

    private JPanel buildActiveCard(PositionStore.Position p)
    {
        JPanel card = card();

        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setOpaque(false);
        header.setAlignmentX(LEFT_ALIGNMENT);
        // Don't lock height — item names + Remove share a narrow row and must be allowed to grow.
        JLabel nameLabel = wrapLabel(nullToEmpty(p.itemName), HEADING_FONT, Color.WHITE, WRAP_WIDTH_NARROW);
        nameLabel.setToolTipText(nullToEmpty(p.itemName));
        header.add(nameLabel, BorderLayout.CENTER);

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
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(28, header.getPreferredSize().height)));
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
                ? "Limit hit (0/" + NUMBER.format(buyLimit) + ")"
                : "Limit " + NUMBER.format(remainingLimit) + "/" + NUMBER.format(buyLimit);
            card.add(label(limitLabel, META_FONT, limitColor));
        }

        int breakEvenSell = MarketClient.breakEvenSellPrice(p.averageBuyPrice);
        JLabel pricesLabel = label(
            "Buy " + gp(p.averageBuyPrice) + " → Sell " + gp(p.recommendedSellPrice),
            BODY_BOLD,
            Color.WHITE);
        if (breakEvenSell > 0)
        {
            pricesLabel.setToolTipText("Break-even sell after tax: " + gp(breakEvenSell));
        }
        card.add(pricesLabel);

        if (breakEvenSell > 0 && p.recommendedSellPrice > 0 && p.recommendedSellPrice < breakEvenSell)
        {
            card.add(label(
                "Underwater — sell ≥ " + gp(breakEvenSell),
                META_FONT,
                PROFIT_RED));
        }

        long unrealizedEstimate = 0L;
        if (p.recommendedSellPrice > 0 && p.quantityRemaining > 0)
        {
            long remainingCost = PositionStore.remainingBuyCost(p);
            long gross = (long) p.recommendedSellPrice * p.quantityRemaining;
            long tax = (long) MarketClient.geTax(p.recommendedSellPrice) * p.quantityRemaining;
            unrealizedEstimate = gross - tax - remainingCost;
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
            setLabelText(status, "Enter GP as a whole number, e.g. 50,000,000.");
            return false;
        }
    }

    private static void setLabelText(JLabel label, String text)
    {
        setLabelText(label, text, WRAP_WIDTH);
    }

    private static void setLabelText(JLabel label, String text, int widthPx)
    {
        label.setText(htmlWrap(text, widthPx));
    }

    /** Non-wrapping label for tight control rows (e.g. Sort + combo). */
    private static JLabel plainLabel(String text, Font font, Color color)
    {
        JLabel label = new JLabel(text == null ? "" : text);
        label.setFont(font);
        label.setForeground(color);
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private static JLabel label(String text, Font font, Color color)
    {
        return wrapLabel(text, font, color, WRAP_WIDTH);
    }

    private static JLabel wrapLabel(String text, Font font, Color color, int widthPx)
    {
        JLabel label = new JLabel();
        label.setFont(font);
        label.setForeground(color);
        label.setAlignmentX(LEFT_ALIGNMENT);
        setLabelText(label, text, widthPx);
        return label;
    }

    private static String htmlWrap(String text, int widthPx)
    {
        String raw = text == null ? "" : text;
        String escaped = raw
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
        // Keep below the visible sidebar column or trailing glyphs clip.
        return "<html><body style='width:" + Math.max(80, widthPx) + "px'>" + escaped + "</body></html>";
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
