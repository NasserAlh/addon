package bookmap.addon.strategytesting;

import velox.api.layer1.annotations.*;
import velox.api.layer1.simplified.*;
import velox.gui.StrategyPanel;
import velox.api.layer1.data.*;
import velox.api.layer1.messages.indicators.Layer1ApiUserMessageModifyIndicator.GraphType;
import java.awt.*;
import velox.api.layer1.common.Log;
import java.util.concurrent.ConcurrentHashMap;


import javax.swing.*;

import java.util.Map;

@Layer1TradingStrategy
@Layer1SimpleAttachable
@Layer1StrategyName("VWAP Breakout Strategy")
@Layer1ApiVersion(Layer1ApiVersionValue.VERSION2)
public class TheStrategy implements CustomModule, TradeDataListener, TimeListener,
        OrdersListener, CustomSettingsPanelProvider {

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
            checkForBreakoutOrBreakdown(price, vwapValue);
        }
    }

    private void checkForBreakoutOrBreakdown(double price, double vwapValue) {
        boolean isBullishBreakout = price > vwapValue && !priceAboveVwap;
        boolean isBearishBreakdown = price < vwapValue && priceAboveVwap;

        if (isBullishBreakout || isBearishBreakdown) {
            if (lastCrossTime == 0) {
                lastCrossTime = currentTime;
            } else if (currentTime - lastCrossTime > confirmationTime * 1_000_000_000L) {
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
}