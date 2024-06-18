package bookmap.addon.strategytesting;

import java.util.ArrayList;
import java.util.List;

public class VWAPCalculator {
    static class Trade {
        double price;
        int volume;

        public Trade(double price, int volume) {
            this.price = price;
            this.volume = volume;
        }
    }

    private final List<Trade> trades = new ArrayList<>();
    private double totalValue = 0;
    private int totalVolume = 0;
    private double latestVWAP = 0;
    private boolean isVwapUpdated = false;

    public void addTrade(double price, int volume) {
        trades.add(new Trade(price, volume));
        // Update totals incrementally
        totalValue += price * volume;
        totalVolume += volume;
        isVwapUpdated = false;
    }

    public double calculateVWAP() {
        if (!isVwapUpdated && totalVolume > 0) {
            latestVWAP = totalValue / totalVolume;
            isVwapUpdated = true;
        }
        return latestVWAP;
    }

    public double getLatestVWAP() {
        if (!isVwapUpdated) {
            calculateVWAP(); // Ensure VWAP is calculated if it's out of date
        }
        return latestVWAP;
    }

    public void resetVWAP() {
        trades.clear();
        totalValue = 0;
        totalVolume = 0;
        latestVWAP = 0;
        isVwapUpdated = false;
    }
}
