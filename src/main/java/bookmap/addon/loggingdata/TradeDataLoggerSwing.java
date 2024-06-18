package bookmap.addon.loggingdata;

import velox.api.layer1.annotations.*;
import velox.api.layer1.common.Log;
import velox.api.layer1.data.*;
import velox.api.layer1.simplified.*;

import javax.swing.*;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Layer1SimpleAttachable
@Layer1StrategyName("TradeDataLogger with Swing")
@Layer1ApiVersion(Layer1ApiVersionValue.VERSION2)
public class TradeDataLoggerSwing implements CustomModule, TradeDataListener, HistoricalDataListener, TimeListener {

    private long lastTimestamp = 0; // To store the last timestamp
    private DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private BufferedWriter writer;

    @Override
    public void initialize(String alias, InstrumentInfo info, Api api, InitialState initialState) {
        SwingUtilities.invokeLater(() -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int option = fileChooser.showSaveDialog(null);

            if (option == JFileChooser.APPROVE_OPTION) {
                Path selectedPath = fileChooser.getSelectedFile().toPath();
                Path filePath = selectedPath.resolve("tradeData.csv");
                try {
                    Files.createDirectories(filePath.getParent()); // Ensure the directory exists
                    writer = Files.newBufferedWriter(filePath, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    // Updated CSV headers to include detailed TradeInfo
                    writer.write(
                            "Timestamp,Price,Size,isOtc,isBidAggressor,isExecutionStart,isExecutionEnd,aggressorOrderId,passiveOrderId\n");
                } catch (IOException e) {
                    Log.info("Error initializing BufferedWriter: " + e.getMessage());
                }
            } else {
                Log.info("User canceled directory selection.");
            }
        });
    }

    @Override
    public void onTrade(double price, int size, velox.api.layer1.data.TradeInfo tradeInfo) {
        // Convert nanoseconds to seconds (as Instant requires seconds)
        Instant instant = Instant.ofEpochSecond(lastTimestamp / 1_000_000_000);
        String formattedTime = dateTimeFormatter.format(instant);

        try {
            if (writer != null) {
                // Decompose TradeInfo and write each piece of information under its column
                writer.write(String.format("%s,%f,%d,%b,%b,%b,%b,%s,%s\n",
                        formattedTime, price, size,
                        tradeInfo.isOtc, tradeInfo.isBidAggressor,
                        tradeInfo.isExecutionStart, tradeInfo.isExecutionEnd,
                        tradeInfo.aggressorOrderId, tradeInfo.passiveOrderId));
            }
        } catch (IOException e) {
            Log.info("Error writing to file: " + e.getMessage());
        }
    }

    @Override
    public void onTimestamp(long timestamp) {
        // Update the last timestamp whenever a new one is received
        lastTimestamp = timestamp;
    }

    @Override
    public void stop() {
        // Cleanup resources if needed
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException e) {
            Log.info("Error closing BufferedWriter: " + e.getMessage());
        }
    }
}

