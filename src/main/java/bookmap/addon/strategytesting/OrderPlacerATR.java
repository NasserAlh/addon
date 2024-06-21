package bookmap.addon.strategytesting;

import velox.api.layer1.data.OrderDuration;
import velox.api.layer1.data.SimpleOrderSendParameters;
import velox.api.layer1.data.SimpleOrderSendParametersBuilder;
import velox.api.layer1.simplified.Api;

public class OrderPlacerATR {
    private final String alias;
    private final Api api;

    public OrderPlacerATR(String alias, Api api) {
        if (alias == null || api == null) {
            throw new IllegalArgumentException("Arguments cannot be null");
        }
        this.alias = alias;
        this.api = api;
    }

    public boolean placeOrder(boolean isBuy, double price, int quantity, int trailingStopStep, double atr) {
        SimpleOrderSendParametersBuilder builder = new SimpleOrderSendParametersBuilder(alias, isBuy, quantity);
        builder.setDuration(OrderDuration.IOC);

        // Use ATR for setting stop loss and take profit
        int takeProfitOffset = (int) (price + (isBuy ? atr : -atr));
        int stopLossOffset = (int) (price - (isBuy ? atr : -atr));

        builder.setTakeProfitOffset(takeProfitOffset);
        builder.setStopLossOffset(stopLossOffset);

        if (trailingStopStep > 0) {
            builder.setStopLossTrailingStep(trailingStopStep);
        }

        SimpleOrderSendParameters order = builder.build();
        api.sendOrder(order);
        return true;
    }
}
