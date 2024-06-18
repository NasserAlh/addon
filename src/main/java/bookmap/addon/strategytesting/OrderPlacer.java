package bookmap.addon.strategytesting;

import velox.api.layer1.data.OrderDuration;
import velox.api.layer1.data.SimpleOrderSendParameters;
import velox.api.layer1.data.SimpleOrderSendParametersBuilder;
import velox.api.layer1.simplified.Api;

public class OrderPlacer {
    private final String alias;
    private final Api api;

    public OrderPlacer(String alias, Api api) {
        if (alias == null || api == null) {
            throw new IllegalArgumentException("Arguments cannot be null");
        }
        this.alias = alias;
        this.api = api;
    }

    public boolean placeOrder(boolean isBuy, double price, int quantity, int trailingStopStep, int takeProfitOffset,
            int stopLossOffset) {
        SimpleOrderSendParametersBuilder builder = new SimpleOrderSendParametersBuilder(alias, isBuy, quantity);
        builder.setDuration(OrderDuration.IOC);
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
