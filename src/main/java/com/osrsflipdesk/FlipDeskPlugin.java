package com.osrsflipdesk;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Notification;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@PluginDescriptor(
    name = "OSRS Flip Desk",
    description = "Find affordable GE flips using competitive prices and learn from your actual fills",
    tags = {"grand exchange", "ge", "flipping", "prices", "profit", "trading"}
)
public class FlipDeskPlugin extends Plugin
{
    private static final Logger log = LoggerFactory.getLogger(FlipDeskPlugin.class);
    private static final long REFRESH_TIMEOUT_SECONDS = 90L;
    private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance(Locale.US);

    @Inject
    private Notifier notifier;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ConfigManager configManager;

    @Inject
    private FlipDeskConfig config;

    @Inject
    private Gson gson;

    @Inject
    private ScheduledExecutorService executor;

    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    private final Map<Integer, OfferSnapshot> offersBySlot = new HashMap<>();
    private final Map<Integer, FlipOpportunity> recommendationsByItem = new HashMap<>();
    private final Map<Integer, RememberedRecommendation> recentRecommendations = new ConcurrentHashMap<>();

    private FlipDeskPanel panel;
    private NavigationButton navigationButton;
    private MarketClient marketClient;
    private LearningStore learningStore;
    private PositionStore positionStore;
    private BuyLimitStore buyLimitStore;
    private ScheduledFuture<?> scheduledRefresh;
    private ExecutorService scanExecutor;

    private volatile int cachedGpOnHand = 50_000_000;
    private volatile FlipStrictness cachedStrictness = FlipStrictness.BALANCED;
    private volatile FlipTicketSize cachedTicketSize = FlipTicketSize.MEDIUM;
    private volatile FlipSortMode cachedSortMode = FlipSortMode.TOTAL_PROFIT;
    private volatile double cachedBuyPriceTolerance = 0.20;
    private volatile long cachedRecommendationMemoryMs = 60L * 60L * 1000L;
    private volatile boolean cachedTrackNearMarketBuys = true;

    @Override
    protected void startUp()
    {
        marketClient = new MarketClient(gson);
        learningStore = new LearningStore(configManager);
        positionStore = new PositionStore(configManager, gson);
        buyLimitStore = new BuyLimitStore(configManager, gson);
        panel = injector.getInstance(FlipDeskPanel.class);
        scanExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "osrs-flip-desk-scan");
            thread.setDaemon(true);
            return thread;
        });

        reloadCachedConfig();

        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");

        navigationButton = NavigationButton.builder()
            .tooltip("OSRS Flip Desk")
            .icon(icon)
            .priority(5)
            .panel(panel)
            .build();

        clientToolbar.addNavigation(navigationButton);

        scheduledRefresh = executor.scheduleWithFixedDelay(
            this::refreshIfDue,
            1,
            5,
            TimeUnit.SECONDS);

        if (panel != null)
        {
            panel.setGpOnHandDisplay(cachedGpOnHand);
        }
        refreshNow();
    }

    @Override
    protected void shutDown()
    {
        if (scheduledRefresh != null)
        {
            scheduledRefresh.cancel(true);
            scheduledRefresh = null;
        }

        if (scanExecutor != null)
        {
            scanExecutor.shutdownNow();
            scanExecutor = null;
        }

        if (navigationButton != null)
        {
            clientToolbar.removeNavigation(navigationButton);
            navigationButton = null;
        }

        offersBySlot.clear();
        refreshing.set(false);
    }

    void setGpOnHand(int gp)
    {
        configManager.setConfiguration(FlipDeskConfig.GROUP, "gpOnHand", gp);
        cachedGpOnHand = gp;
    }

    void refreshNow()
    {
        if (scanExecutor != null)
        {
            scanExecutor.execute(this::refreshMarket);
        }
    }

    void removeActiveFlip(long positionId)
    {
        if (positionStore.removeActive(positionId))
        {
            refreshPositionsPanel();
        }
    }

    void clearActiveFlips()
    {
        positionStore.clearActive();
        refreshPositionsPanel();
    }

    int getBuyLimit(int itemId)
    {
        return marketClient == null ? 0 : marketClient.getBuyLimit(itemId);
    }

    int getRemainingBuyLimit(int itemId)
    {
        int limit = getBuyLimit(itemId);
        if (limit <= 0 || buyLimitStore == null)
        {
            return 0;
        }
        return buyLimitStore.remaining(itemId, limit);
    }

    private void refreshPositionsPanel()
    {
        SwingUtilities.invokeLater(() -> {
            if (panel != null)
            {
                panel.setPositions(
                    positionStore.getActive(),
                    positionStore.getHistory(),
                    positionStore.getTotalProfit());
            }
        });
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!FlipDeskConfig.GROUP.equals(event.getGroup()))
        {
            return;
        }

        if ("testFillNotifications".equals(event.getKey()))
        {
            if ("true".equalsIgnoreCase(event.getNewValue()))
            {
                sendTestFillNotifications();
                configManager.setConfiguration(FlipDeskConfig.GROUP, "testFillNotifications", false);
            }
            return;
        }

        reloadCachedConfig();
    }

    private void sendTestFillNotifications()
    {
        Notification buyNotification = config.buyFillNotification();
        Notification sellNotification = config.sellFillNotification();

        boolean buyEnabled = buyNotification != null && buyNotification.isEnabled();
        boolean sellEnabled = sellNotification != null && sellNotification.isEnabled();

        if (!buyEnabled && !sellEnabled)
        {
            if (notifier != null)
            {
                notifier.notify("Flip Desk: both buy and sell fill notifications are disabled.");
            }
            return;
        }

        if (buyEnabled)
        {
            notifier.notify(
                buyNotification,
                "Flip Desk: Bought 1,000 Test Item @ 150 ea (test)");
        }

        if (sellEnabled)
        {
            notifier.notify(
                sellNotification,
                "Flip Desk: Sold 1,000 Test Item @ 175 ea (+25,000 gp) (test)");
        }
    }

    private void reloadCachedConfig()
    {
        cachedGpOnHand = config.gpOnHand();

        FlipStrictness strictness = config.strictness();
        cachedStrictness = strictness == null ? FlipStrictness.BALANCED : strictness;

        FlipTicketSize ticketSize = config.ticketSize();
        cachedTicketSize = ticketSize == null ? FlipTicketSize.MEDIUM : ticketSize;

        FlipSortMode sortMode = config.sortMode();
        cachedSortMode = sortMode == null ? FlipSortMode.TOTAL_PROFIT : sortMode;

        cachedBuyPriceTolerance = config.buyPriceToleranceTenths() / 1000.0;
        cachedRecommendationMemoryMs = Math.max(5, config.recommendationMemoryMinutes()) * 60L * 1000L;
        cachedTrackNearMarketBuys = config.trackNearMarketBuys();

        if (panel != null)
        {
            final int gp = cachedGpOnHand;
            SwingUtilities.invokeLater(() -> panel.setGpOnHandDisplay(gp));
        }
    }

    private volatile long lastRefreshMillis = 0L;

    private void refreshIfDue()
    {
        long interval = Math.max(5, config.refreshSeconds()) * 1000L;
        if (System.currentTimeMillis() - lastRefreshMillis >= interval)
        {
            refreshNow();
        }
    }

    private void refreshMarket()
    {
        if (!refreshing.compareAndSet(false, true))
        {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            if (panel != null)
            {
                panel.setLoading();
            }
        });

        AtomicBoolean finished = new AtomicBoolean(false);
        ScheduledFuture<?> watchdog = executor.schedule(() -> {
            if (!finished.compareAndSet(false, true))
            {
                return;
            }

            refreshing.set(false);
            log.warn("Flip Desk market refresh timed out after {} seconds", REFRESH_TIMEOUT_SECONDS);
            SwingUtilities.invokeLater(() -> {
                if (panel != null)
                {
                    panel.setError(
                        "Market refresh timed out after "
                            + REFRESH_TIMEOUT_SECONDS
                            + " seconds. Check your internet connection, then click Refresh.");
                }
            });
        }, REFRESH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        List<FlipOpportunity> opportunities = null;
        String errorMessage = null;
        List<PositionStore.Position> active = Collections.emptyList();
        List<PositionStore.Position> history = Collections.emptyList();
        long totalProfit = 0L;
        String sortLabel = sortLabel(cachedSortMode);
        long startedAt = System.currentTimeMillis();

        try
        {
            opportunities = marketClient.scan(
                cachedGpOnHand,
                cachedSortMode,
                cachedStrictness,
                cachedTicketSize,
                buyLimitStore);

            synchronized (recommendationsByItem)
            {
                recommendationsByItem.clear();
                long now = System.currentTimeMillis();
                for (FlipOpportunity opportunity : opportunities)
                {
                    recommendationsByItem.put(opportunity.itemId, opportunity);
                    recentRecommendations.put(opportunity.itemId, new RememberedRecommendation(
                        opportunity.itemId,
                        opportunity.name,
                        opportunity.buyPrice,
                        opportunity.sellPrice,
                        opportunity.patientBuy,
                        now));
                }
                expireOldRecommendations(now);
            }

            active = positionStore.getActive();
            history = positionStore.getHistory();
            totalProfit = positionStore.getTotalProfit();
            lastRefreshMillis = System.currentTimeMillis();

            log.info(
                "Flip Desk refreshed {} opportunities in {} ms",
                opportunities.size(),
                lastRefreshMillis - startedAt);
        }
        catch (Exception ex)
        {
            log.warn("Flip Desk market refresh failed", ex);
            errorMessage = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        }
        finally
        {
            watchdog.cancel(false);

            if (finished.compareAndSet(false, true))
            {
                refreshing.set(false);

                final List<FlipOpportunity> finalOpportunities = opportunities;
                final String finalError = errorMessage;
                final List<PositionStore.Position> finalActive = active;
                final List<PositionStore.Position> finalHistory = history;
                final long finalTotalProfit = totalProfit;
                final String finalSortLabel = sortLabel;

                SwingUtilities.invokeLater(() -> {
                    if (panel == null)
                    {
                        return;
                    }

                    if (finalError != null)
                    {
                        panel.setError(finalError);
                        return;
                    }

                    panel.setOpportunities(
                        finalOpportunities != null ? finalOpportunities : Collections.emptyList(),
                        finalSortLabel);
                    panel.setPositions(finalActive, finalHistory, finalTotalProfit);
                });
            }
        }
    }

    @Subscribe
    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
    {
        int slot = event.getSlot();
        GrandExchangeOffer offer = event.getOffer();

        if (offer.getState() == GrandExchangeOfferState.EMPTY || offer.getItemId() <= 0)
        {
            offersBySlot.remove(slot);
            return;
        }

        OfferSnapshot previous = offersBySlot.get(slot);

        // Fresh slot / counter reset: start from 0 so instant fills on a new offer are counted.
        boolean countersReset = previous == null
            || previous.itemId != offer.getItemId()
            || offer.getQuantitySold() < previous.quantitySold
            || offer.getSpent() < previous.spent;

        // Same slot, new price/size, counters did not reset in this event: adopt to avoid double-count.
        boolean relistedSameCounters = !countersReset
            && previous != null
            && (previous.offerPrice != offer.getPrice() || previous.totalQuantity != offer.getTotalQuantity());

        int baselineQuantity;
        int baselineSpent;
        if (countersReset)
        {
            baselineQuantity = 0;
            baselineSpent = 0;
        }
        else if (relistedSameCounters)
        {
            baselineQuantity = offer.getQuantitySold();
            baselineSpent = offer.getSpent();
        }
        else
        {
            baselineQuantity = previous.quantitySold;
            baselineSpent = previous.spent;
        }

        if (offer.getQuantitySold() > baselineQuantity && offer.getSpent() > baselineSpent)
        {
            int deltaQuantity = offer.getQuantitySold() - baselineQuantity;
            int deltaSpent = offer.getSpent() - baselineSpent;

            int remainingInOffer = Math.max(0, offer.getTotalQuantity() - baselineQuantity);
            if (deltaQuantity > remainingInOffer)
            {
                deltaQuantity = remainingInOffer;
                if (offer.getQuantitySold() > 0)
                {
                    int avgPrice = offer.getSpent() / offer.getQuantitySold();
                    deltaSpent = avgPrice * deltaQuantity;
                }
            }

            if (deltaQuantity > 0 && deltaSpent > 0)
            {
                int actualExecutionPrice = deltaSpent / deltaQuantity;
                processFill(offer, deltaQuantity, actualExecutionPrice);
            }
        }

        OfferSnapshot next = new OfferSnapshot();
        next.itemId = offer.getItemId();
        next.quantitySold = offer.getQuantitySold();
        next.spent = offer.getSpent();
        next.totalQuantity = offer.getTotalQuantity();
        next.offerPrice = offer.getPrice();
        next.state = offer.getState();
        offersBySlot.put(slot, next);
    }

    private void processFill(GrandExchangeOffer offer, int deltaQuantity, int actualExecutionPrice)
    {
        boolean buy = isBuyState(offer.getState());
        if (!buy && !isSellState(offer.getState()))
        {
            return;
        }

        MarketClient.MarketSnapshot market = marketClient.getSnapshot(offer.getItemId());

        learningStore.recordFill(
            offer.getItemId(),
            buy,
            actualExecutionPrice,
            market);

        if (buy)
        {
            // Count every observed buy toward the 4-hour GE limit, even if we don't open a flip.
            buyLimitStore.recordBuy(offer.getItemId(), deltaQuantity);

            boolean tracked = tryRecordBuyFill(offer.getItemId(), deltaQuantity, actualExecutionPrice);
            if (tracked)
            {
                notifyBuyFill(offer.getItemId(), deltaQuantity, actualExecutionPrice);
            }
            else
            {
                log.debug(
                    "Ignored buy fill itemId={} qty={} price={} (not active, not recent recommendation, not near market)",
                    offer.getItemId(),
                    deltaQuantity,
                    actualExecutionPrice);
            }
        }
        else
        {
            PositionStore.SellResult sellResult = positionStore.recordSellFill(
                offer.getItemId(),
                deltaQuantity,
                actualExecutionPrice);
            int matched = Math.max(0, deltaQuantity - sellResult.unmatchedQuantity);
            if (matched > 0)
            {
                notifySellFill(offer.getItemId(), matched, actualExecutionPrice, sellResult.realizedProfit);
            }
        }

        SwingUtilities.invokeLater(() -> {
            if (panel != null)
            {
                panel.setPositions(
                    positionStore.getActive(),
                    positionStore.getHistory(),
                    positionStore.getTotalProfit());
            }
        });

        refreshNow();
    }

    private boolean tryRecordBuyFill(int itemId, int deltaQuantity, int actualExecutionPrice)
    {
        PositionStore.Position activePosition = positionStore.getActivePosition(itemId);
        if (activePosition != null)
        {
            int sellTarget = marketClient.getRecommendedSellPrice(itemId, learningStore);
            if (sellTarget <= 0)
            {
                sellTarget = activePosition.recommendedSellPrice;
            }

            positionStore.recordBuyFill(
                itemId,
                activePosition.itemName,
                deltaQuantity,
                actualExecutionPrice,
                sellTarget);
            return true;
        }

        RememberedRecommendation remembered = getRememberedRecommendation(itemId);
        if (remembered != null && matchesRecommendedBuy(actualExecutionPrice, remembered))
        {
            int sellTarget = marketClient.getRecommendedSellPrice(itemId, learningStore);
            if (sellTarget <= 0)
            {
                sellTarget = remembered.sellPrice;
            }

            positionStore.recordBuyFill(
                itemId,
                remembered.name,
                deltaQuantity,
                actualExecutionPrice,
                sellTarget);
            return true;
        }

        if (cachedTrackNearMarketBuys && marketLooksLikeFlipBuy(itemId, actualExecutionPrice))
        {
            int sellTarget = marketClient.getRecommendedSellPrice(itemId, learningStore);
            if (sellTarget <= 0)
            {
                MarketClient.MarketSnapshot snap = marketClient.getSnapshot(itemId);
                if (snap != null)
                {
                    sellTarget = Math.min(snap.latestHigh, snap.avgHigh);
                }
            }

            if (sellTarget > actualExecutionPrice)
            {
                positionStore.recordBuyFill(
                    itemId,
                    marketClient.getItemName(itemId),
                    deltaQuantity,
                    actualExecutionPrice,
                    sellTarget);
                return true;
            }
        }

        return false;
    }

    private RememberedRecommendation getRememberedRecommendation(int itemId)
    {
        long now = System.currentTimeMillis();
        RememberedRecommendation remembered = recentRecommendations.get(itemId);
        if (remembered == null)
        {
            FlipOpportunity current;
            synchronized (recommendationsByItem)
            {
                current = recommendationsByItem.get(itemId);
            }
            if (current == null)
            {
                return null;
            }
            return new RememberedRecommendation(
                current.itemId,
                current.name,
                current.buyPrice,
                current.sellPrice,
                current.patientBuy,
                now);
        }

        if (now - remembered.seenAt > cachedRecommendationMemoryMs)
        {
            recentRecommendations.remove(itemId, remembered);
            return null;
        }

        return remembered;
    }

    private boolean matchesRecommendedBuy(int executionPrice, RememberedRecommendation remembered)
    {
        return isWithinBuyTolerance(executionPrice, remembered.buyPrice)
            || isWithinBuyTolerance(executionPrice, remembered.patientBuy);
    }

    private boolean marketLooksLikeFlipBuy(int itemId, int executionPrice)
    {
        MarketClient.MarketSnapshot snap = marketClient.getSnapshot(itemId);
        if (snap == null || executionPrice <= 0)
        {
            return false;
        }

        int marketBuy = Math.max(snap.latestLow, snap.avgLow);
        if (marketBuy <= 0)
        {
            return false;
        }

        // Allow buys from a bit under the tape up through the configured tolerance above it.
        double lowBound = marketBuy * (1.0 - cachedBuyPriceTolerance);
        double highBound = marketBuy * (1.0 + cachedBuyPriceTolerance);
        return executionPrice >= lowBound && executionPrice <= highBound;
    }

    private void expireOldRecommendations(long now)
    {
        Iterator<Map.Entry<Integer, RememberedRecommendation>> it =
            recentRecommendations.entrySet().iterator();
        while (it.hasNext())
        {
            Map.Entry<Integer, RememberedRecommendation> entry = it.next();
            if (now - entry.getValue().seenAt > cachedRecommendationMemoryMs)
            {
                it.remove();
            }
        }
    }

    private boolean isWithinBuyTolerance(int executionPrice, int recommendedBuyPrice)
    {
        if (recommendedBuyPrice <= 0)
        {
            return false;
        }

        double deviation = Math.abs(executionPrice - recommendedBuyPrice)
            / (double) recommendedBuyPrice;
        return deviation <= cachedBuyPriceTolerance;
    }

    private static boolean isBuyState(GrandExchangeOfferState state)
    {
        return state == GrandExchangeOfferState.BUYING
            || state == GrandExchangeOfferState.BOUGHT
            || state == GrandExchangeOfferState.CANCELLED_BUY;
    }

    private static boolean isSellState(GrandExchangeOfferState state)
    {
        return state == GrandExchangeOfferState.SELLING
            || state == GrandExchangeOfferState.SOLD
            || state == GrandExchangeOfferState.CANCELLED_SELL;
    }

    private void notifyBuyFill(int itemId, int quantity, int price)
    {
        Notification notification = config.buyFillNotification();
        if (notification == null || !notification.isEnabled() || notifier == null)
        {
            return;
        }

        String name = marketClient.getItemName(itemId);
        notifier.notify(
            notification,
            "Flip Desk: Bought "
                + NUMBER.format(quantity)
                + " "
                + name
                + " @ "
                + NUMBER.format(price)
                + " ea");
    }

    private void notifySellFill(int itemId, int quantity, int price, long realizedProfit)
    {
        Notification notification = config.sellFillNotification();
        if (notification == null || !notification.isEnabled() || notifier == null)
        {
            return;
        }

        String name = marketClient.getItemName(itemId);
        String profitText = realizedProfit >= 0
            ? "+" + NUMBER.format(realizedProfit) + " gp"
            : NUMBER.format(realizedProfit) + " gp";
        notifier.notify(
            notification,
            "Flip Desk: Sold "
                + NUMBER.format(quantity)
                + " "
                + name
                + " @ "
                + NUMBER.format(price)
                + " ea ("
                + profitText
                + ")");
    }

    private static String sortLabel(FlipSortMode sortMode)
    {
        return sortMode == FlipSortMode.FILL_CONFIDENCE ? "fill confidence" : "total profit";
    }

    @Provides
    FlipDeskConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(FlipDeskConfig.class);
    }

    private static final class OfferSnapshot
    {
        int itemId;
        int quantitySold;
        int spent;
        int totalQuantity;
        int offerPrice;
        GrandExchangeOfferState state;
    }

    private static final class RememberedRecommendation
    {
        final int itemId;
        final String name;
        final int buyPrice;
        final int sellPrice;
        final int patientBuy;
        final long seenAt;

        RememberedRecommendation(
            int itemId,
            String name,
            int buyPrice,
            int sellPrice,
            int patientBuy,
            long seenAt)
        {
            this.itemId = itemId;
            this.name = name;
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
            this.patientBuy = patientBuy;
            this.seenAt = seenAt;
        }
    }
}
