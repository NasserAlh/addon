package bookmap.addon.strategytesting;

import velox.api.layer1.annotations.*;
import velox.api.layer1.simplified.*;
import velox.gui.StrategyPanel;
import velox.api.layer1.data.*;
import velox.api.layer1.layers.utils.OrderBook;
import velox.api.layer1.messages.indicators.Layer1ApiUserMessageModifyIndicator.GraphType;
import java.awt.*;
import velox.api.layer1.common.Log;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedList;
import java.util.Queue;

import javax.swing.*;

import java.util.Map;

@Layer1TradingStrategy
@Layer1SimpleAttachable
@Layer1StrategyName("VWAP Breakout ATR Strategy")
@Layer1ApiVersion(Layer1ApiVersionValue.VERSION2)
public class TheStrategyATR
        implements CustomModule, TradeDataListener, TimeListener, OrdersListener, CustomSettingsPanelProvider,
        BarDataListener {

    private VWAPCalculator vwapCalculator = new VWAPCalculator();
    private OrderPlacer orderPlacer;
    private Indicator priceIndicator, vwapIndicator;
    private boolean priceAboveVwap = false;
    private long lastCrossTime = 0;
    private long currentTime = 0;

    private Integer confirmationTime = 5;
    private Integer trailingStopStep = 10;
    private Integer quantity = 1;
    private Integer takeProfitOffset = 10;
    private Integer stopLossOffset = 8;
    private Boolean enableTrading = true;
    private Boolean enableBreakoutDetection = true;

    private boolean isOrderOpen = false;
    private Map<String, Integer> activeOrdersUnfilled = new ConcurrentHashMap<>();

    private static final int ATR_PERIOD = 14; // ATR period
    private Queue<Double> trueRanges = new LinkedList<>();
    private double previousClose = Double.NaN;

    @Override
    public void initialize(String alias, InstrumentInfo info, Api api, InitialState initialState) {
        if (priceIndicator == null) {
            priceIndicator = api.registerIndicator("Price", GraphType.PRIMARY);
            priceIndicator.setColor(Color.GREEN);
        } else {
            Log.info("Price Indicator already initialized.");
        }
        if (vwapIndicator == null) {
            vwapIndicator = api.registerIndicator("VWAP", GraphType.PRIMARY);
            vwapIndicator.setColor(Color.WHITE);
        } else {
            Log.info("VWAP Indicator already initialized.");
        }
        orderPlacer = new OrderPlacer(alias, api);
    }

    @Override
    public StrategyPanel[] getCustomSettingsPanels() {
        BookmapSettingsPanel panel = new BookmapSettingsPanel("Settings");
        JTextField confirmationTimeField = confirmationTime(panel);
        JTextField trailingStopStepField = trailingStopStep(panel);
        JTextField quantityField = quantity(panel);
        JTextField takeProfitOffsetField = takeProfitOffset(panel);
        JTextField stopLossOffsetField = stopLossOffset(panel);
        JCheckBox enableTradingCheckBox = enableTrading(panel);
        JCheckBox enableBreakoutDetectionCheckBox = enableBreakoutDetection(panel);

        JButton applyButton = new JButton("Apply");
        JLabel notificationLabel = new JLabel();
        notificationLabel.setForeground(Color.BLUE);
        panel.addSettingsItem(notificationLabel);

        applyButton.addActionListener(e -> {
            try {
                confirmationTime = Integer.parseInt(confirmationTimeField.getText());
                trailingStopStep = Integer.parseInt(trailingStopStepField.getText());
                quantity = Integer.parseInt(quantityField.getText());
                takeProfitOffset = Integer.parseInt(takeProfitOffsetField.getText());
                stopLossOffset = Integer.parseInt(stopLossOffsetField.getText());
                enableTrading = enableTradingCheckBox.isSelected();
                enableBreakoutDetection = enableBreakoutDetectionCheckBox.isSelected();

                notificationLabel.setText("Changes applied successfully!");
                notificationLabel.setForeground(Color.GREEN);
            } catch (NumberFormatException ex) {
                notificationLabel.setText("Invalid input. Please enter valid numbers.");
                notificationLabel.setForeground(Color.RED);
            }

            Timer timer = new Timer(3000, event -> notificationLabel.setText(""));
            timer.setRepeats(false);
            timer.start();
        });
        panel.addSettingsItem(applyButton);

        return new StrategyPanel[] { panel };
    }

    private JTextField confirmationTime(BookmapSettingsPanel panel) {
        JTextField confirmationTimeField = new JTextField(confirmationTime.toString());
        panel.addSettingsItem("Confirmation Time (seconds)", confirmationTimeField);
        return confirmationTimeField;
    }

    private JTextField trailingStopStep(BookmapSettingsPanel panel) {
        JTextField trailingStopStepField = new JTextField(trailingStopStep.toString());
        panel.addSettingsItem("Trailing Stop Step", trailingStopStepField);
        return trailingStopStepField;
    }

    private JTextField quantity(BookmapSettingsPanel panel) {
        JTextField quantityField = new JTextField(quantity.toString());
        panel.addSettingsItem("Quantity", quantityField);
        return quantityField;
    }

    private JTextField takeProfitOffset(BookmapSettingsPanel panel) {
        JTextField takeProfitOffsetField = new JTextField(takeProfitOffset.toString());
        panel.addSettingsItem("Take Profit Offset", takeProfitOffsetField);
        return takeProfitOffsetField;
    }

    private JTextField stopLossOffset(BookmapSettingsPanel panel) {
        JTextField stopLossOffsetField = new JTextField(stopLossOffset.toString());
        panel.addSettingsItem("Stop Loss Offset", stopLossOffsetField);
        return stopLossOffsetField;
    }

    private JCheckBox enableTrading(BookmapSettingsPanel panel) {
        JCheckBox enableTradingCheckBox = new JCheckBox("Enable Trading", enableTrading);
        panel.addSettingsItem(enableTradingCheckBox);
        return enableTradingCheckBox;
    }

    private JCheckBox enableBreakoutDetection(BookmapSettingsPanel panel) {
        JCheckBox enableBreakoutDetectionCheckBox = new JCheckBox("Enable Breakout Detection",
                enableBreakoutDetection);
        panel.addSettingsItem(enableBreakoutDetectionCheckBox);
        return enableBreakoutDetectionCheckBox;
    }

    @Override
    public void onTrade(double price, int size, TradeInfo tradeInfo) {
        priceIndicator.addPoint(price);
        vwapCalculator.addTrade(price, size);
        double vwapValue = vwapCalculator.calculateVWAP();
        vwapIndicator.addPoint(vwapValue);

        if (enableTrading && enableBreakoutDetection && !isOrderOpen) {
            long dynamicConfirmationTime = calculateDynamicConfirmationTime();
            checkForBreakoutOrBreakdown(price, vwapValue, dynamicConfirmationTime);
        }
    }

    @Override
    public void onTimestamp(long t) {
        currentTime = t;
    }

    @Override
    public void onOrderUpdated(OrderInfoUpdate orderInfoUpdate) {
        if (orderInfoUpdate.status.isActive()) {
            activeOrdersUnfilled.put(orderInfoUpdate.orderId, orderInfoUpdate.unfilled);
        } else {
            activeOrdersUnfilled.remove(orderInfoUpdate.orderId);
        }
        isOrderOpen = !activeOrdersUnfilled.isEmpty();
    }

    @Override
    public void onOrderExecuted(ExecutionInfo executionInfo) {
        Integer unfilled = activeOrdersUnfilled.get(executionInfo.orderId);
        if (unfilled != null) {
            int newUnfilled = unfilled - executionInfo.size;
            if (newUnfilled <= 0) {
                activeOrdersUnfilled.remove(executionInfo.orderId);
            } else {
                activeOrdersUnfilled.put(executionInfo.orderId, newUnfilled);
            }
        }
        isOrderOpen = !activeOrdersUnfilled.isEmpty();
    }

    @Override
    public void stop() {
    }

    @Override
    public long getInterval() {
        return Intervals.INTERVAL_5_MINUTES; // time is always in nanoseconds
    }

    @Override
    public void onBar(OrderBook orderBookMbp, Bar bar) {
        double high = bar.getHigh();
        double low = bar.getLow();
        double close = bar.getClose();

        // Update true range for ATR calculation
        updateTrueRange(high, low, close);

        double price = close; // Use the close price of the bar for trading decisions
        double vwapValue = vwapCalculator.calculateVWAP(); // Assuming VWAP is updated elsewhere

        if (enableTrading && enableBreakoutDetection && !isOrderOpen) {
            long dynamicConfirmationTime = calculateDynamicConfirmationTime();
            checkForBreakoutOrBreakdown(price, vwapValue, dynamicConfirmationTime);
        }
    }

    private long calculateDynamicConfirmationTime() {
        double atr = calculateATR();
        if (Double.isNaN(atr)) {
            return confirmationTime * 1_000_000_000L; // Default to fixed confirmation time if ATR is not available
        }
        double volatilityFactor = atr / previousClose; // Example of using ATR relative to the previous close
        return (long) (confirmationTime * (1 + volatilityFactor) * 1_000_000_000L);
    }

    private double calculateATR() {
        if (trueRanges.size() < ATR_PERIOD) {
            return Double.NaN; // Not enough data to calculate ATR
        }

        double sum = 0;
        for (double tr : trueRanges) {
            sum += tr;
        }
        return sum / ATR_PERIOD;
    }

    private void updateTrueRange(double high, double low, double close) {
        if (!Double.isNaN(previousClose)) {
            double tr1 = high - low;
            double tr2 = Math.abs(high - previousClose);
            double tr3 = Math.abs(low - previousClose);
            double trueRange = Math.max(tr1, Math.max(tr2, tr3));
            trueRanges.add(trueRange);
            if (trueRanges.size() > ATR_PERIOD) {
                trueRanges.poll();
            }
        }
        previousClose = close;
    }

    private void checkForBreakoutOrBreakdown(double price, double vwapValue, long dynamicConfirmationTime) {
        boolean isBullishBreakout = price > vwapValue && !priceAboveVwap;
        boolean isBearishBreakdown = price < vwapValue && priceAboveVwap;

        if (isBullishBreakout || isBearishBreakdown) {
            if (lastCrossTime == 0) {
                lastCrossTime = currentTime;
            } else if (currentTime - lastCrossTime > dynamicConfirmationTime) {
                Log.info(isBullishBreakout ? "Confirmed bullish breakout"
                        : "Confirmed bearish breakdown" + " at price: " + price);
                priceAboveVwap = isBullishBreakout;
                lastCrossTime = 0;

                orderPlacer.placeOrder(isBullishBreakout, price, quantity, trailingStopStep, takeProfitOffset,
                        stopLossOffset);
            }
        } else {
            lastCrossTime = 0;
        }
    }
}