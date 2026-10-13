package backend.java;

import java.util.*;

/**
 * Represents the Microgrid Topology as a Graph and manages Zone relationships.
 * 
 * Demonstrates Core ADSA & OOP Concepts:
 * 1. Graph Data Structure:
 *    - Vertices = Zones (Z1, Z2, Z3, Z4, Z5)
 *    - Edges = Power transmission lines / interconnections
 *    - Representation = Adjacency List (Map<String, List<String>>)
 *    - Traversal = Breadth-First Search (BFS) for finding reachable connected zones
 * 
 * 2. Divide and Conquer:
 *    - Recursive partitioning of microgrid zones into regional control clusters (Group A / Group B)
 */
public class Grid {
    // Map of Zone ID -> Zone Object (Vertices)
    private final Map<String, Zone> zones;
    
    // Adjacency List for Graph Edges (Undirected Graph)
    private final Map<String, List<String>> adjacencyList;

    /**
     * Default Constructor
     */
    public Grid() {
        this.zones = new LinkedHashMap<>();
        this.adjacencyList = new LinkedHashMap<>();
    }

    // ==========================================
    // 1. Graph Management (ADSA Concept)
    // ==========================================

    /**
     * Adds a Zone (Vertex) to the Grid Graph.
     */
    public void addZone(Zone zone) {
        zones.put(zone.getId(), zone);
        adjacencyList.putIfAbsent(zone.getId(), new ArrayList<>());
    }

    /**
     * Connects two zones with a bidirectional transmission line (Undirected Edge).
     */
    public void connectZones(String id1, String id2) {
        if (!zones.containsKey(id1) || !zones.containsKey(id2)) {
            System.err.printf("[!] Warning: Cannot connect %s and %s (one or both do not exist)%n", id1, id2);
            return;
        }

        // Add edge in both directions (undirected transmission line)
        if (!adjacencyList.get(id1).contains(id2)) {
            adjacencyList.get(id1).add(id2);
        }
        if (!adjacencyList.get(id2).contains(id1)) {
            adjacencyList.get(id2).add(id1);
        }
    }

    /**
     * Removes a Zone (Vertex) and all its associated transmission lines from the Grid Graph.
     */
    public boolean removeZone(String zoneId) {
        if (!zones.containsKey(zoneId)) {
            return false;
        }
        // Remove from zones map
        zones.remove(zoneId);
        
        // Remove from adjacency list
        adjacencyList.remove(zoneId);
        for (List<String> neighbors : adjacencyList.values()) {
            neighbors.remove(zoneId);
        }
        return true;
    }

    /**
     * Disconnects an edge between two zones.
     */
    public void disconnectZones(String id1, String id2) {
        if (adjacencyList.containsKey(id1)) adjacencyList.get(id1).remove(id2);
        if (adjacencyList.containsKey(id2)) adjacencyList.get(id2).remove(id1);
    }

    /**
     * Returns direct adjacent neighbors of a given zone.
     */
    public List<Zone> getConnectedZones(String zoneId) {
        List<Zone> neighbors = new ArrayList<>();
        List<String> neighborIds = adjacencyList.getOrDefault(zoneId, Collections.emptyList());
        
        for (String nId : neighborIds) {
            Zone neighbor = zones.get(nId);
            if (neighbor != null) {
                neighbors.add(neighbor);
            }
        }
        return neighbors;
    }

    /**
     * Checks if two zones are directly connected by an edge.
     */
    public boolean areConnected(String id1, String id2) {
        List<String> neighbors = adjacencyList.get(id1);
        return neighbors != null && neighbors.contains(id2);
    }

    /**
     * Graph Traversal: Breadth-First Search (BFS) to find all connected candidate zones
     * ordered by topological proximity (distance from the source zone).
     */
    public List<Zone> getReachableZonesBFS(String startZoneId) {
        List<Zone> reachable = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();

        visited.add(startZoneId);
        queue.add(startZoneId);

        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            
            // Do not include the start zone itself in candidate destinations
            if (!currentId.equals(startZoneId)) {
                Zone z = zones.get(currentId);
                if (z != null) {
                    reachable.add(z);
                }
            }

            for (String neighborId : adjacencyList.getOrDefault(currentId, Collections.emptyList())) {
                if (!visited.contains(neighborId)) {
                    visited.add(neighborId);
                    queue.add(neighborId);
                }
            }
        }
        return reachable;
    }

    // ==========================================
    // 2. Divide and Conquer Partitioning (ADSA)
    // ==========================================

    /**
     * Academic Demonstration of Divide and Conquer:
     * Recursively partitions a list of grid zones into balanced regional control sub-groups.
     * 
     * Pattern:
     * Base Case: If list has <= 2 zones, return it as a leaf cluster.
     * Divide: Split the list into Left and Right sub-lists.
     * Conquer: Recursively partition Left and Right sub-lists.
     * Combine: Merge partitioned sub-groups into a structured hierarchical tree.
     */
    public List<List<Zone>> partitionZonesRecursive(List<Zone> zoneList, int depth) {
        String indent = "  ".repeat(depth);
        System.out.printf("%s[D&C Step - Depth %d] Partitioning group of %d zones: %s%n",
                indent, depth, zoneList.size(), getZoneIdsString(zoneList));

        // Base Case: Small enough group (1 or 2 zones)
        if (zoneList.size() <= 2) {
            System.out.printf("%s -> Base Case reached: Sub-Group formed %s%n", indent, getZoneIdsString(zoneList));
            List<List<Zone>> baseResult = new ArrayList<>();
            baseResult.add(new ArrayList<>(zoneList));
            return baseResult;
        }

        // Divide Step: Find midpoint
        int mid = zoneList.size() / 2;
        List<Zone> leftHalf = new ArrayList<>(zoneList.subList(0, mid));
        List<Zone> rightHalf = new ArrayList<>(zoneList.subList(mid, zoneList.size()));

        System.out.printf("%s -> Divided into Left (Group A): %s | Right (Group B): %s%n",
                indent, getZoneIdsString(leftHalf), getZoneIdsString(rightHalf));

        // Conquer Step: Recursively partition left and right halves
        List<List<Zone>> leftPartition = partitionZonesRecursive(leftHalf, depth + 1);
        List<List<Zone>> rightPartition = partitionZonesRecursive(rightHalf, depth + 1);

        // Combine Step: Merge partitions
        List<List<Zone>> combinedResult = new ArrayList<>();
        combinedResult.addAll(leftPartition);
        combinedResult.addAll(rightPartition);

        System.out.printf("%s -> Combined partitions at Depth %d%n", indent, depth);
        return combinedResult;
    }

    /**
     * Helper to run divide-and-conquer on all zones.
     */
    public List<List<Zone>> runDivideAndConquerPartition() {
        System.out.println("\n------------------------------------------------------------");
        System.out.println("ADSA: Divide and Conquer Zone Partitioning Demonstration");
        System.out.println("------------------------------------------------------------");
        List<Zone> allZonesList = new ArrayList<>(zones.values());
        return partitionZonesRecursive(allZonesList, 0);
    }

    private String getZoneIdsString(List<Zone> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append(list.get(i).getId());
            if (i < list.size() - 1) sb.append(", ");
        }
        sb.append("]");
        return sb.toString();
    }

    // ==========================================
    // 3. Grid Metrics and Accessors
    // ==========================================

    public Zone getZone(String id) {
        return zones.get(id);
    }

    public Collection<Zone> getAllZones() {
        return zones.values();
    }

    public Map<String, List<String>> getAdjacencyList() {
        return adjacencyList;
    }

    public double getTotalCurrentLoad() {
        return zones.values().stream().mapToDouble(Zone::getCurrentLoad).sum();
    }

    public double getTotalCapacity() {
        return zones.values().stream().mapToDouble(Zone::getMaximumCapacity).sum();
    }

    public double getGridUtilization() {
        double cap = getTotalCapacity();
        if (cap <= 0) return 0.0;
        return Math.round((getTotalCurrentLoad() / cap) * 1000.0) / 10.0;
    }

    public int getOverloadedZoneCount() {
        return (int) zones.values().stream().filter(Zone::isOverloaded).count();
    }

    /**
     * Exports current Grid state as a JSON string for frontend synchronization.
     */
    public String toJson() {
        StringBuilder zonesJson = new StringBuilder("[");
        int count = 0;
        for (Zone z : zones.values()) {
            zonesJson.append(z.toJson());
            if (++count < zones.size()) zonesJson.append(",");
        }
        zonesJson.append("]");

        // Edges JSON: array of {"source": "Z1", "target": "Z2"}
        StringBuilder edgesJson = new StringBuilder("[");
        Set<String> processedEdges = new HashSet<>();
        boolean first = true;
        for (Map.Entry<String, List<String>> entry : adjacencyList.entrySet()) {
            String u = entry.getKey();
            for (String v : entry.getValue()) {
                String edgeKey = (u.compareTo(v) < 0) ? (u + "-" + v) : (v + "-" + u);
                if (!processedEdges.contains(edgeKey)) {
                    processedEdges.add(edgeKey);
                    if (!first) edgesJson.append(",");
                    edgesJson.append(String.format("{\"source\":\"%s\",\"target\":\"%s\"}", u, v));
                    first = false;
                }
            }
        }
        edgesJson.append("]");

        return String.format(Locale.US,
            "{\"totalZones\":%d,\"overloadedCount\":%d,\"totalLoad\":%.1f,\"totalCapacity\":%.1f,\"utilization\":%.1f,\"zones\":%s,\"edges\":%s}",
            zones.size(), getOverloadedZoneCount(), getTotalCurrentLoad(), getTotalCapacity(), getGridUtilization(), zonesJson.toString(), edgesJson.toString()
        );
    }
}
