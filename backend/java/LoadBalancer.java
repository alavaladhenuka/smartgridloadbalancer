package backend.java;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Intelligent Load Balancer Algorithm for the Microgrid.
 * 
 * Demonstrates:
 * 1. Overload Detection using forecasted/current load demand.
 * 2. Graph-Aware Target Selection: searches connected neighbor vertices (edges) with available capacity.
 * 3. Multi-destination load splitting when a single neighbor cannot accommodate the full excess.
 * 4. Transparent logging and simulation auditing.
 */
public class LoadBalancer {

    /**
     * Inner Model Class representing an individual load transfer action.
     */
    public static class BalancingAction {
        private final String timestamp;
        private final String sourceZoneId;
        private final String destinationZoneId;
        private final double amountTransferred;
        private final String reason;
        private final String status; // "TRANSFER COMPLETE" or "PARTIALLY BALANCED" or "FAILED"

        public BalancingAction(String sourceZoneId, String destinationZoneId, double amountTransferred, String reason, String status) {
            this.timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            this.sourceZoneId = sourceZoneId;
            this.destinationZoneId = destinationZoneId;
            this.amountTransferred = Math.round(amountTransferred * 100.0) / 100.0;
            this.reason = reason;
            this.status = status;
        }

        public String getTimestamp() { return timestamp; }
        public String getSourceZoneId() { return sourceZoneId; }
        public String getDestinationZoneId() { return destinationZoneId; }
        public double getAmountTransferred() { return amountTransferred; }
        public String getReason() { return reason; }
        public String getStatus() { return status; }

        @Override
        public String toString() {
            return String.format("[%s] Transfer %.1f kW from %s -> %s (%s | Reason: %s)",
                    timestamp, amountTransferred, sourceZoneId, destinationZoneId, status, reason);
        }

        public String toJson() {
            return String.format(java.util.Locale.US,
                "{\"timestamp\":\"%s\",\"source\":\"%s\",\"destination\":\"%s\",\"amount\":%.1f,\"reason\":\"%s\",\"status\":\"%s\"}",
                timestamp, sourceZoneId, destinationZoneId, amountTransferred, escapeJson(reason), status
            );
        }
    }

    private final List<BalancingAction> actionHistory;
    private final List<String> activityLogs;
    private double totalLoadShifted;

    public LoadBalancer() {
        this.actionHistory = new ArrayList<>();
        this.activityLogs = new ArrayList<>();
        this.totalLoadShifted = 0.0;
    }

    /**
     * Identifies all zones currently exceeding or predicted to exceed maximum capacity.
     */
    public List<Zone> detectOverloadedZones(Grid grid) {
        List<Zone> overloaded = new ArrayList<>();
        for (Zone z : grid.getAllZones()) {
            if (z.isOverloaded()) {
                overloaded.add(z);
                log(String.format("Zone %s predicted to exceed capacity (Forecast: %.1f kW / Cap: %.1f kW | Overload: +%.1f kW)",
                        z.getId(), z.getForecastLoad(), z.getMaximumCapacity(), z.calculateOverload()), "warning");
            }
        }
        if (overloaded.isEmpty()) {
            log("All zones operating within safe capacity limits.", "info");
        }
        return overloaded;
    }

    /**
     * Executes the simulated load redistribution across graph-connected zones.
     */
    public List<BalancingAction> balanceGrid(Grid grid) {
        List<BalancingAction> newActions = new ArrayList<>();
        List<Zone> overloadedZones = detectOverloadedZones(grid);

        if (overloadedZones.isEmpty()) {
            log("Grid is already balanced. No load redistribution required.", "info");
            return newActions;
        }

        log(String.format("Initiating load balancing across %d overloaded zone(s)...", overloadedZones.size()), "info");

        for (Zone source : overloadedZones) {
            double remainingOverload = source.calculateOverload();
            if (remainingOverload <= 0) continue;

            log(String.format("Balancing Source Zone %s (Excess Overload = %.1f kW)...", source.getId(), remainingOverload), "info");

            // Step 1: Find connected zones via Graph Topology (direct neighbors first, then BFS connected)
            List<Zone> candidateDestinations = grid.getConnectedZones(source.getId());

            // If direct neighbors don't have enough capacity, broaden search via BFS reachable nodes
            List<Zone> reachable = grid.getReachableZonesBFS(source.getId());
            for (Zone rz : reachable) {
                if (!candidateDestinations.contains(rz)) {
                    candidateDestinations.add(rz);
                }
            }

            double totalTransferredForSource = 0.0;

            for (Zone dest : candidateDestinations) {
                if (remainingOverload <= 0) break;

                double availableCap = dest.getAvailableCapacity();
                if (availableCap > 0) {
                    log(String.format("Available capacity detected in connected Zone %s (Headroom: %.1f kW)", dest.getId(), availableCap), "info");

                    // Amount to transfer is minimum of required overload relief and destination capacity
                    double transferAmount = Math.min(remainingOverload, availableCap);
                    transferAmount = Math.round(transferAmount * 100.0) / 100.0;

                    // Perform simulated transfer
                    source.reduceLoad(transferAmount);
                    dest.addLoad(transferAmount);

                    remainingOverload = Math.round((remainingOverload - transferAmount) * 100.0) / 100.0;
                    totalTransferredForSource += transferAmount;
                    this.totalLoadShifted += transferAmount;

                    String reason = String.format("%s predicted to exceed its capacity (shifted to %s)", source.getId(), dest.getId());
                    BalancingAction action = new BalancingAction(source.getId(), dest.getId(), transferAmount, reason, "TRANSFER COMPLETE");
                    
                    newActions.add(action);
                    actionHistory.add(action);

                    log(String.format("%.1f kW transferred from %s to %s. [Target New Load: %.1f/%.1f kW]",
                            transferAmount, source.getId(), dest.getId(), dest.getCurrentLoad(), dest.getMaximumCapacity()), "success");
                }
            }

            // Check if any overload remains un-relieved
            if (remainingOverload > 0) {
                String warningMsg = String.format("Insufficient available capacity in connected grid to fully balance %s (Remaining excess: %.1f kW)",
                        source.getId(), remainingOverload);
                log(warningMsg, "error");
                
                BalancingAction partialAction = new BalancingAction(source.getId(), "GRID_EXHAUSTED", 0.0, warningMsg, "INSUFFICIENT CAPACITY");
                newActions.add(partialAction);
                actionHistory.add(partialAction);
            } else {
                log(String.format("Zone %s successfully stabilized! Total shifted: %.1f kW", source.getId(), totalTransferredForSource), "success");
            }
        }

        log("Grid balancing simulation completed.", "success");
        return newActions;
    }

    public void log(String message, String type) {
        String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String formatted = String.format("[%s] [%s] %s", timestamp, type.toUpperCase(), message);
        activityLogs.add(formatted);
        System.out.println(formatted);
    }

    public List<BalancingAction> getActionHistory() {
        return actionHistory;
    }

    public List<String> getActivityLogs() {
        return activityLogs;
    }

    public double getTotalLoadShifted() {
        return Math.round(totalLoadShifted * 100.0) / 100.0;
    }

    public void reset() {
        actionHistory.clear();
        activityLogs.clear();
        totalLoadShifted = 0.0;
        log("Load Balancer state reset to default.", "info");
    }

    public static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public String toJson() {
        StringBuilder actionsJson = new StringBuilder("[");
        for (int i = 0; i < actionHistory.size(); i++) {
            actionsJson.append(actionHistory.get(i).toJson());
            if (i < actionHistory.size() - 1) actionsJson.append(",");
        }
        actionsJson.append("]");

        StringBuilder logsJson = new StringBuilder("[");
        for (int i = 0; i < activityLogs.size(); i++) {
            logsJson.append("\"").append(escapeJson(activityLogs.get(i))).append("\"");
            if (i < activityLogs.size() - 1) logsJson.append(",");
        }
        logsJson.append("]");

        return String.format(java.util.Locale.US, "{\"totalLoadShifted\":%.1f,\"actions\":%s,\"logs\":%s}",
                totalLoadShifted, actionsJson.toString(), logsJson.toString());
    }
}
