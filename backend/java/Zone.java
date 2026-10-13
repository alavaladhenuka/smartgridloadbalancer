package backend.java;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an individual power zone/microgrid vertex in the Smart Grid.
 * 
 * Demonstrates Core OOP Concepts:
 * - Encapsulation (private fields, public getters/setters)
 * - Domain Methods (business logic for capacity, overload, and load updates)
 */
public class Zone {
    private String id;                       // Unique Identifier (e.g., "Z1", "Z2")
    private String name;                     // Descriptive name (e.g., "Residential Zone A")
    private double currentLoad;              // Current active load in Kilowatts (kW)
    private double maximumCapacity;          // Maximum allowable load capacity in kW
    private double forecastLoad;             // Forecasted future load in kW (from Python)
    private List<Double> historicalLoads;    // Past historical load readings for forecasting

    /**
     * Parameterized Constructor
     */
    public Zone(String id, String name, double currentLoad, double maximumCapacity, List<Double> historicalLoads) {
        this.id = id;
        this.name = name;
        this.currentLoad = currentLoad;
        this.maximumCapacity = maximumCapacity;
        this.forecastLoad = currentLoad; // Default to current load until forecast is loaded
        this.historicalLoads = (historicalLoads != null) ? new ArrayList<>(historicalLoads) : new ArrayList<>();
    }

    /**
     * Simplified Constructor with default historical data
     */
    public Zone(String id, String name, double currentLoad, double maximumCapacity) {
        this(id, name, currentLoad, maximumCapacity, new ArrayList<>());
    }

    // ==========================================
    // Core Domain Logic & Academic Methods
    // ==========================================

    /**
     * Calculates remaining headroom before reaching maximum capacity.
     * Formula: Available Capacity = Max Capacity - Current Load
     */
    public double getAvailableCapacity() {
        double available = maximumCapacity - currentLoad;
        return Math.max(0.0, Math.round(available * 100.0) / 100.0);
    }

    /**
     * Checks if the zone is currently overloaded or predicted to become overloaded.
     */
    public boolean isOverloaded() {
        double activeTestLoad = (forecastLoad > 0) ? forecastLoad : currentLoad;
        return activeTestLoad > maximumCapacity;
    }

    /**
     * Calculates the overload amount exceeding maximum capacity.
     * Formula: Overload = Forecast Load - Maximum Capacity
     */
    public double calculateOverload() {
        double activeTestLoad = (forecastLoad > 0) ? forecastLoad : currentLoad;
        if (activeTestLoad > maximumCapacity) {
            return Math.round((activeTestLoad - maximumCapacity) * 100.0) / 100.0;
        }
        return 0.0;
    }

    /**
     * Calculates the current utilization rate as a percentage (%).
     */
    public double getUtilizationPercentage() {
        if (maximumCapacity <= 0) return 0.0;
        return Math.round((currentLoad / maximumCapacity) * 1000.0) / 10.0;
    }

    /**
     * Categorizes status into NORMAL, WARNING, or OVERLOADED.
     */
    public String getStatus() {
        if (isOverloaded()) {
            return "OVERLOADED";
        } else if (getUtilizationPercentage() >= 85.0) {
            return "WARNING";
        } else {
            return "NORMAL";
        }
    }

    /**
     * Safely adds transferred load to this zone.
     */
    public void addLoad(double amount) {
        if (amount > 0) {
            this.currentLoad = Math.round((this.currentLoad + amount) * 100.0) / 100.0;
        }
    }

    /**
     * Safely relieves/reduces load from this overloaded zone.
     */
    public void reduceLoad(double amount) {
        if (amount > 0) {
            this.currentLoad = Math.max(0.0, Math.round((this.currentLoad - amount) * 100.0) / 100.0);
            if (this.forecastLoad > this.maximumCapacity) {
                this.forecastLoad = Math.max(this.maximumCapacity, Math.round((this.forecastLoad - amount) * 100.0) / 100.0);
            }
        }
    }

    /**
     * Updates load to a specified value.
     */
    public void updateLoad(double newLoad) {
        this.currentLoad = Math.round(newLoad * 100.0) / 100.0;
    }

    // ==========================================
    // Getters and Setters (Encapsulation)
    // ==========================================

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getCurrentLoad() {
        return currentLoad;
    }

    public void setCurrentLoad(double currentLoad) {
        this.currentLoad = currentLoad;
    }

    public double getMaximumCapacity() {
        return maximumCapacity;
    }

    public void setMaximumCapacity(double maximumCapacity) {
        this.maximumCapacity = maximumCapacity;
    }

    public double getForecastLoad() {
        return forecastLoad;
    }

    public void setForecastLoad(double forecastLoad) {
        this.forecastLoad = Math.round(forecastLoad * 100.0) / 100.0;
    }

    public List<Double> getHistoricalLoads() {
        return historicalLoads;
    }

    public void setHistoricalLoads(List<Double> historicalLoads) {
        this.historicalLoads = historicalLoads;
    }

    public void addHistoricalReading(double reading) {
        this.historicalLoads.add(reading);
    }

    @Override
    public String toString() {
        return String.format("Zone[%s | Load: %.1f/%.1f kW | Forecast: %.1f kW | Avail: %.1f kW | Status: %s]",
                id, currentLoad, maximumCapacity, forecastLoad, getAvailableCapacity(), getStatus());
    }

    /**
     * Returns a JSON representation string for web integration.
     */
    public String toJson() {
        StringBuilder histJson = new StringBuilder("[");
        for (int i = 0; i < historicalLoads.size(); i++) {
            histJson.append(historicalLoads.get(i));
            if (i < historicalLoads.size() - 1) histJson.append(",");
        }
        histJson.append("]");

        return String.format(java.util.Locale.US,
            "{\"id\":\"%s\",\"name\":\"%s\",\"currentLoad\":%.1f,\"maximumCapacity\":%.1f,\"forecastLoad\":%.1f,\"availableCapacity\":%.1f,\"utilization\":%.1f,\"status\":\"%s\",\"overload\":%.1f,\"historical\":%s}",
            LoadBalancer.escapeJson(id), LoadBalancer.escapeJson(name), currentLoad, maximumCapacity, forecastLoad, getAvailableCapacity(), getUtilizationPercentage(), LoadBalancer.escapeJson(getStatus()), calculateOverload(), histJson.toString()
        );
    }
}
