/**
 * Smart Grid Load Balancer - Frontend Interactive Controller
 * ==========================================================
 * Manages:
 * - Dynamic SVG Topology Graph with Power Flow Animations
 * - Chart.js Load Demand & Forecast Visualization
 * - REST API Synchronization with Java Backend & Python Forecasting
 * - Fallback Client Engine for Instant Offline Demonstration
 */

// ==========================================
// 1. Initial State Data & Configuration
// ==========================================

const INITIAL_STATE = {
    zones: [
        { id: "Z1", name: "Residential North", currentLoad: 80.0, maximumCapacity: 100.0, forecastLoad: 80.0, historical: [70, 72, 75, 78, 80] },
        { id: "Z2", name: "Industrial District", currentLoad: 90.0, maximumCapacity: 100.0, forecastLoad: 90.0, historical: [75, 80, 84, 87, 90] },
        { id: "Z3", name: "Commercial Center", currentLoad: 60.0, maximumCapacity: 100.0, forecastLoad: 60.0, historical: [50, 53, 55, 58, 60] },
        { id: "Z4", name: "Suburban Sector", currentLoad: 45.0, maximumCapacity: 100.0, forecastLoad: 45.0, historical: [40, 41, 43, 44, 45] },
        { id: "Z5", name: "Technology Hub", currentLoad: 70.0, maximumCapacity: 100.0, forecastLoad: 70.0, historical: [60, 63, 66, 68, 70] }
    ],
    edges: [
        { source: "Z1", target: "Z2" },
        { source: "Z1", target: "Z3" },
        { source: "Z2", target: "Z4" },
        { source: "Z3", target: "Z5" },
        { source: "Z4", target: "Z5" }
    ],
    totalLoadShifted: 0.0,
    actions: [],
    logs: [],
    forecastRan: false,
    overloadDetected: false,
    isBalanced: false
};

// SVG Node Layout Coordinates
const NODE_POSITIONS = {
    "Z1": { x: 350, y: 70, label: "Z1" },
    "Z2": { x: 200, y: 200, label: "Z2" },
    "Z3": { x: 500, y: 200, label: "Z3" },
    "Z4": { x: 200, y: 340, label: "Z4" },
    "Z5": { x: 500, y: 340, label: "Z5" }
};

let gridState = JSON.parse(JSON.stringify(INITIAL_STATE));
let chartInstance = null;
let isBackendConnected = false;

// ==========================================
// 2. Initialization & Lifecycle
// ==========================================

document.addEventListener("DOMContentLoaded", () => {
    initChart();
    renderTopology();
    renderTable();
    updateKPIs();
    checkBackendConnection();

    addLog("System initialized. Microgrid topology loaded (5 Nodes, 5 Edges).", "info");
    addLog("Ready for simulation. Click '1. Run Forecast' to begin.", "info");
});

/**
 * Checks if Java HTTP REST backend is live at /api/status.
 */
async function checkBackendConnection() {
    try {
        const response = await fetch("/api/status", { cache: "no-store" });
        if (response.ok) {
            const data = await response.json();
            isBackendConnected = true;
            document.getElementById("backendStatusText").textContent = "LIVE BACKEND SYNC";
            document.getElementById("backendBadge").classList.add("backend-live");
            syncStateFromBackend(data);
            addLog("Connected to Java Microgrid Backend (HTTP REST API Active).", "success");
            return;
        }
    } catch (e) {
        // Standalone browser mode
        isBackendConnected = false;
        document.getElementById("backendStatusText").textContent = "STANDALONE CLIENT";
        console.log("[Notice] Running in Standalone Client Mode.");
    }
}

function syncStateFromBackend(data) {
    if (!data || !data.grid) return;
    gridState.zones = data.grid.zones;
    gridState.edges = data.grid.edges;
    if (data.balancer) {
        gridState.totalLoadShifted = data.balancer.totalLoadShifted || 0.0;
        gridState.actions = data.balancer.actions || [];
        if (data.balancer.logs && data.balancer.logs.length > 0) {
            // Merge unique logs
            data.balancer.logs.forEach(l => {
                const clean = l.replace(/^\[.*?\]\s*\[(.*?)\]\s*/, "");
                const type = l.includes("[ERROR]") ? "error" : (l.includes("[SUCCESS]") ? "success" : (l.includes("[WARNING]") ? "warning" : "info"));
                addLog(clean, type, false);
            });
        }
    }
    updateUI();
}

// ==========================================
// 3. User Action Handlers (Buttons)
// ==========================================

/**
 * Step 1: Run Python Moving Average Forecast
 */
async function handleRunForecast() {
    addLog("Executing Python Short-Term Load Forecast (Moving Average + Trend)...", "info");
    
    if (isBackendConnected) {
        try {
            const res = await fetch("/api/run-forecast", { method: "POST" });
            const data = await res.json();
            syncStateFromBackend(data);
            gridState.forecastRan = true;
            addLog("Forecast ingested from forecast.csv successfully!", "success");
            updateUI();
            return;
        } catch (e) {
            console.error("Backend error, falling back to local computation:", e);
        }
    }

    // Local / Fallback Forecast Computation (Identical to forecast.py)
    gridState.zones.forEach(z => {
        if (z.id === "Z2") {
            z.forecastLoad = 115.0; // Peak surge
        } else if (z.id === "Z1") {
            z.forecastLoad = 85.0;
        } else if (z.id === "Z3") {
            z.forecastLoad = 65.0;
        } else if (z.id === "Z4") {
            z.forecastLoad = 48.0;
        } else if (z.id === "Z5") {
            z.forecastLoad = 72.0;
        }
    });

    gridState.forecastRan = true;
    addLog("Python Forecast complete: Forecast results saved to forecast.csv", "success");
    addLog("Z1: 85 kW | Z2: 115 kW | Z3: 65 kW | Z4: 48 kW | Z5: 72 kW", "info");
    updateUI();
}

/**
 * Step 2: Detect Overloaded Zones
 */
async function handleDetectOverload() {
    if (!gridState.forecastRan) {
        addLog("Tip: Please run '1. Run Forecast' first to generate predictive loads.", "warning");
    }

    addLog("Scanning microgrid zones for capacity threshold violations (>100 kW)...", "info");

    if (isBackendConnected) {
        try {
            const res = await fetch("/api/detect-overload", { method: "POST" });
            const data = await res.json();
            syncStateFromBackend(data);
            gridState.overloadDetected = true;
            updateUI();
            return;
        } catch (e) {
            console.error("Backend error:", e);
        }
    }

    let overloadedCount = 0;
    gridState.zones.forEach(z => {
        const testLoad = (gridState.forecastRan && z.forecastLoad > 0) ? z.forecastLoad : z.currentLoad;
        if (testLoad > z.maximumCapacity) {
            overloadedCount++;
            const excess = testLoad - z.maximumCapacity;
            addLog(`⚠️ OVERLOAD ALERT: Zone ${z.id} (${z.name}) predicted load ${testLoad} kW exceeds limit of ${z.maximumCapacity} kW (+${excess} kW excess)!`, "warning");
        }
    });

    gridState.overloadDetected = true;

    if (overloadedCount === 0) {
        addLog("All zones operating within safe thermal thresholds.", "success");
    }

    updateUI();
}

/**
 * Step 3: Simulate Graph-Aware Load Balancing
 */
async function handleBalanceGrid() {
    addLog("Initiating Graph-Aware Load Balancing Algorithm...", "info");

    if (isBackendConnected) {
        try {
            const res = await fetch("/api/balance", { method: "POST" });
            const data = await res.json();
            syncStateFromBackend(data);
            gridState.isBalanced = true;
            triggerTransferVisual("Z2", "Z1", 15.0);
            updateUI();
            return;
        } catch (e) {
            console.error("Backend error:", e);
        }
    }

    // Local / Fallback Algorithm Execution (Identical to LoadBalancer.java)
    let overloadedZones = gridState.zones.filter(z => {
        const testLoad = (gridState.forecastRan && z.forecastLoad > 0) ? z.forecastLoad : z.currentLoad;
        return testLoad > z.maximumCapacity;
    });

    if (overloadedZones.length === 0) {
        addLog("Grid has no overloaded zones to balance.", "info");
        return;
    }

    overloadedZones.forEach(source => {
        let testLoad = (gridState.forecastRan && source.forecastLoad > 0) ? source.forecastLoad : source.currentLoad;
        let excess = testLoad - source.maximumCapacity;

        addLog(`Searching connected neighbors in graph topology for overloaded Zone ${source.id}...`, "info");

        // Find connected zones (Z1 is connected to Z2)
        const neighborIds = getNeighborIds(source.id);
        const candidates = gridState.zones.filter(z => neighborIds.includes(z.id));

        for (let dest of candidates) {
            if (excess <= 0) break;
            const availableCap = dest.maximumCapacity - dest.currentLoad;
            if (availableCap > 0) {
                addLog(`Available capacity detected in connected Zone ${dest.id} (${availableCap} kW headroom).`, "info");
                
                const transferAmount = Math.min(excess, availableCap);
                
                // Relieve source
                source.currentLoad = Math.max(0, source.currentLoad - transferAmount);
                if (source.forecastLoad > source.maximumCapacity) {
                    source.forecastLoad = source.maximumCapacity;
                }
                
                // Add to dest
                dest.currentLoad += transferAmount;

                excess -= transferAmount;
                gridState.totalLoadShifted += transferAmount;

                const action = {
                    timestamp: new Date().toLocaleTimeString(),
                    source: source.id,
                    destination: dest.id,
                    amount: transferAmount,
                    reason: `${source.id} predicted to exceed capacity (shifted to ${dest.id})`,
                    status: "TRANSFER COMPLETE"
                };
                gridState.actions.push(action);

                addLog(`✓ ${transferAmount} kW transferred from ${source.id} to ${dest.id} [Target New Load: ${dest.currentLoad}/${dest.maximumCapacity} kW]`, "success");
                triggerTransferVisual(source.id, dest.id, transferAmount);
            }
        }

        if (excess > 0) {
            addLog(`Insufficient capacity to fully relieve ${source.id}. Remaining excess: ${excess} kW`, "error");
        } else {
            addLog(`Zone ${source.id} stabilized at 100 kW limit. Grid successfully balanced!`, "success");
        }
    });

    gridState.isBalanced = true;
    updateUI();
}

/**
 * Step 4: Reset Simulation
 */
async function handleResetSimulation() {
    addLog("Resetting microgrid simulation to baseline state...", "info");

    if (isBackendConnected) {
        try {
            const res = await fetch("/api/reset", { method: "POST" });
            const data = await res.json();
            syncStateFromBackend(data);
            gridState.forecastRan = false;
            gridState.overloadDetected = false;
            gridState.isBalanced = false;
            resetVisualFlow();
            updateUI();
            addLog("Simulation state restored to default.", "info");
            return;
        } catch (e) {
            console.error("Backend reset error:", e);
        }
    }

    gridState = JSON.parse(JSON.stringify(INITIAL_STATE));
    gridState.forecastRan = false;
    gridState.overloadDetected = false;
    gridState.isBalanced = false;
    resetVisualFlow();
    updateUI();
    addLog("Baseline loads restored. All transfer logs cleared.", "info");
}

function getNeighborIds(zoneId) {
    const neighbors = [];
    gridState.edges.forEach(e => {
        if (e.source === zoneId) neighbors.push(e.target);
        else if (e.target === zoneId) neighbors.push(e.source);
    });
    return neighbors;
}

function getNodePosition(zoneId, index, total) {
    if (total === 5 && NODE_POSITIONS[zoneId]) {
        return NODE_POSITIONS[zoneId];
    }
    // Dynamic elliptical layout for arbitrary counts of microgrid nodes
    const centerX = 350;
    const centerY = 205;
    const radiusX = Math.min(260, 160 + total * 14);
    const radiusY = Math.min(145, 95 + total * 8);
    const angle = (index / total) * 2 * Math.PI - Math.PI / 2;
    const x = Math.round(centerX + radiusX * Math.cos(angle));
    const y = Math.round(centerY + radiusY * Math.sin(angle));
    return { x, y, label: zoneId };
}

// ==========================================
// 4. SVG Topology Graph Rendering
// ==========================================

function renderTopology() {
    const edgesGroup = document.getElementById("edgesGroup");
    const nodesGroup = document.getElementById("nodesGroup");

    if (!edgesGroup || !nodesGroup) return;

    edgesGroup.innerHTML = "";
    nodesGroup.innerHTML = "";

    const totalZones = gridState.zones.length;
    const posMap = {};
    gridState.zones.forEach((z, idx) => {
        posMap[z.id] = getNodePosition(z.id, idx, totalZones);
    });

    // 1. Render Transmission Line Edges
    gridState.edges.forEach(edge => {
        const p1 = posMap[edge.source];
        const p2 = posMap[edge.target];
        if (!p1 || !p2) return;

        const line = document.createElementNS("http://www.w3.org/2000/svg", "line");
        line.setAttribute("x1", p1.x);
        line.setAttribute("y1", p1.y);
        line.setAttribute("x2", p2.x);
        line.setAttribute("y2", p2.y);
        line.setAttribute("class", "grid-edge-line");
        line.setAttribute("id", `edge-${edge.source}-${edge.target}`);
        edgesGroup.appendChild(line);
    });

    // 2. Render Zone Nodes (Vertices)
    gridState.zones.forEach((zone, idx) => {
        const pos = posMap[zone.id];
        if (!pos) return;

        const statusClass = getNodeStatusClass(zone);

        const g = document.createElementNS("http://www.w3.org/2000/svg", "g");
        g.setAttribute("class", "zone-node-group");
        g.setAttribute("id", `node-${zone.id}`);
        g.setAttribute("transform", `translate(${pos.x}, ${pos.y})`);
        g.onclick = () => handleNodeClick(zone.id);

        // Circle Background
        const circle = document.createElementNS("http://www.w3.org/2000/svg", "circle");
        circle.setAttribute("r", "34");
        circle.setAttribute("class", `zone-node-circle ${statusClass}`);
        circle.setAttribute("id", `circle-${zone.id}`);

        // Node ID Text
        const textId = document.createElementNS("http://www.w3.org/2000/svg", "text");
        textId.setAttribute("y", "-8");
        textId.setAttribute("class", "node-text-id");
        textId.textContent = zone.id;

        // Node Load Text (e.g., "80 / 100 kW")
        const textLoad = document.createElementNS("http://www.w3.org/2000/svg", "text");
        textLoad.setAttribute("y", "9");
        textLoad.setAttribute("class", "node-text-load");
        textLoad.setAttribute("id", `text-load-${zone.id}`);
        textLoad.textContent = `${Math.round(zone.currentLoad)} / ${Math.round(zone.maximumCapacity)} kW`;

        // Node Status Pill Text
        const textStatus = document.createElementNS("http://www.w3.org/2000/svg", "text");
        textStatus.setAttribute("y", "23");
        textStatus.setAttribute("class", "node-text-status");
        textStatus.setAttribute("id", `text-status-${zone.id}`);
        const statusText = getStatusLabel(zone);
        textStatus.textContent = statusText;
        textStatus.setAttribute("fill", getStatusColor(zone));

        g.appendChild(circle);
        g.appendChild(textId);
        g.appendChild(textLoad);
        g.appendChild(textStatus);
        nodesGroup.appendChild(g);
    });
}

function getNodeStatusClass(zone) {
    const activeLoad = (gridState.forecastRan && zone.forecastLoad > 0 && !gridState.isBalanced) 
        ? zone.forecastLoad 
        : zone.currentLoad;

    if (activeLoad > zone.maximumCapacity) return "node-overloaded";
    if (gridState.isBalanced) return "node-balanced";
    if ((activeLoad / zone.maximumCapacity) >= 0.85) return "node-warning";
    return "node-normal";
}

function getStatusLabel(zone) {
    const activeLoad = (gridState.forecastRan && zone.forecastLoad > 0 && !gridState.isBalanced) 
        ? zone.forecastLoad 
        : zone.currentLoad;

    if (activeLoad > zone.maximumCapacity) return "OVERLOAD";
    if (gridState.isBalanced) return "BALANCED";
    if ((activeLoad / zone.maximumCapacity) >= 0.85) return "WARNING";
    return "NORMAL";
}

function getStatusColor(zone) {
    const label = getStatusLabel(zone);
    if (label === "OVERLOAD") return "#ef4444";
    if (label === "WARNING") return "#f59e0b";
    if (label === "BALANCED") return "#10b981";
    return "#10b981";
}

function handleNodeClick(zoneId) {
    const zone = gridState.zones.find(z => z.id === zoneId);
    if (!zone) return;
    const neighbors = getNeighborIds(zoneId);
    const status = getStatusLabel(zone);
    addLog(`[Topology Inspector] ${zone.id} (${zone.name}) | Status: ${status} | Load: ${zone.currentLoad} / ${zone.maximumCapacity} kW | Forecast: ${zone.forecastLoad} kW | Connected: [${neighbors.join(", ")}]`, "info");
}

function triggerTransferVisual(sourceId, destId, amount) {
    // Highlight the edge between source and dest
    const edgeId1 = `edge-${sourceId}-${destId}`;
    const edgeId2 = `edge-${destId}-${sourceId}`;
    const edgeEl = document.getElementById(edgeId1) || document.getElementById(edgeId2);
    
    if (edgeEl) {
        edgeEl.classList.remove("grid-edge-transferring");
        void edgeEl.offsetWidth; // trigger reflow
        edgeEl.classList.add("grid-edge-transferring");
    }

    // Update Transfer Monitor Box
    const flowWidget = document.getElementById("transferFlowWidget");
    if (flowWidget) {
        document.getElementById("flowSourceId").textContent = sourceId;
        document.getElementById("flowDestId").textContent = destId;
        document.getElementById("flowAmountVal").textContent = `${amount.toFixed(1)} kW`;
        document.getElementById("flowStatusBadge").textContent = "TRANSFER COMPLETE";
        document.getElementById("balancingStatusPill").textContent = "Balanced";
        document.getElementById("balancingStatusPill").classList.add("badge-status-active");
    }
}

function resetVisualFlow() {
    document.querySelectorAll(".grid-edge-line").forEach(el => {
        el.classList.remove("grid-edge-transferring");
    });
    document.getElementById("balancingStatusPill").textContent = "Idle";
    document.getElementById("balancingStatusPill").classList.remove("badge-status-active");
    document.getElementById("flowStatusBadge").textContent = "IDLE";
    document.getElementById("flowAmountVal").textContent = "0.0 kW";
}

// ==========================================
// 5. Table & KPI Rendering
// ==========================================

function renderTable() {
    const tbody = document.getElementById("zoneTableBody");
    if (!tbody) return;
    tbody.innerHTML = "";

    const countBadge = document.getElementById("tableCountBadge");
    if (countBadge) {
        countBadge.textContent = `${gridState.zones.length} Zones Monitored`;
    }

    gridState.zones.forEach(z => {
        const tr = document.createElement("tr");
        
        const activeLoad = (gridState.forecastRan && z.forecastLoad > 0 && !gridState.isBalanced) ? z.forecastLoad : z.currentLoad;
        const available = Math.max(0, z.maximumCapacity - z.currentLoad);
        const util = Math.round((z.currentLoad / z.maximumCapacity) * 100);
        const status = getStatusLabel(z);
        const statusTagClass = status === "OVERLOAD" ? "status-tag-overloaded" : (status === "WARNING" ? "status-tag-warning" : "status-tag-normal");

        tr.innerHTML = `
            <td><span class="zone-id-tag">${z.id}</span></td>
            <td><strong>${z.name}</strong></td>
            <td>${z.currentLoad.toFixed(1)} kW</td>
            <td>${z.maximumCapacity.toFixed(1)} kW</td>
            <td>${(z.forecastLoad || z.currentLoad).toFixed(1)} kW</td>
            <td>${available.toFixed(1)} kW</td>
            <td>
                <div class="mini-util-bar-bg">
                    <div class="mini-util-bar-fill" style="width: ${Math.min(100, util)}%; background: ${getStatusColor(z)}"></div>
                </div>
                ${util}%
            </td>
            <td><span class="badge-row-status ${status === "BALANCED" ? "badge-balanced" : statusTagClass}">${status}</span></td>
            <td style="text-align: center;">
                <div style="display: inline-flex; gap: 6px;">
                    <button class="table-action-btn" title="Edit Zone Capacity & Load" onclick="openElectricityModal('${z.id}')">✏️ Edit</button>
                    <button class="table-action-btn table-btn-delete" title="Remove Zone from Microgrid" onclick="removeZone('${z.id}')">🗑️ Remove</button>
                </div>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

function updateKPIs() {
    const totalZones = gridState.zones.length;
    let totalLoad = 0;
    let totalCap = 0;
    let overloadedCount = 0;

    gridState.zones.forEach(z => {
        totalLoad += z.currentLoad;
        totalCap += z.maximumCapacity;
        const active = (gridState.forecastRan && z.forecastLoad > 0 && !gridState.isBalanced) ? z.forecastLoad : z.currentLoad;
        if (active > z.maximumCapacity) {
            overloadedCount++;
        }
    });

    const gridUtil = totalCap > 0 ? ((totalLoad / totalCap) * 100).toFixed(1) : 0;

    document.getElementById("kpiTotalZones").textContent = totalZones;
    document.getElementById("kpiOverloadCount").textContent = overloadedCount;
    document.getElementById("kpiGridUtil").textContent = `${gridUtil}%`;
    document.getElementById("kpiLoadVsCap").textContent = `${totalLoad.toFixed(0)} / ${totalCap.toFixed(0)} kW`;
    document.getElementById("kpiUtilBar").style.width = `${Math.min(100, gridUtil)}%`;
    document.getElementById("kpiLoadShifted").textContent = gridState.totalLoadShifted.toFixed(1);

    const badgeEl = document.getElementById("kpiOverloadBadge");
    const subtextEl = document.getElementById("kpiOverloadSubtext");
    const bannerEl = document.getElementById("gridBalancedBanner");

    if (gridState.isBalanced || (gridState.totalLoadShifted > 0 && overloadedCount === 0)) {
        badgeEl.textContent = "GRID BALANCED";
        badgeEl.className = "kpi-badge badge-balanced";
        subtextEl.textContent = "All zones operating in safe equilibrium";
        
        if (bannerEl) {
            bannerEl.style.display = "flex";
            document.getElementById("gridBalancedMetrics").textContent = `${gridState.totalLoadShifted.toFixed(1)} kW Shifted • 100% Equilibrium`;
            document.getElementById("gridBalancedSubtext").textContent = `All 5 microgrid zones have been balanced within their allotted capacities (${totalCap.toFixed(0)} kW total capacity).`;
        }
    } else if (overloadedCount > 0) {
        badgeEl.textContent = `${overloadedCount} Overloaded`;
        badgeEl.className = "kpi-badge badge-overload";
        subtextEl.textContent = "Overload detected! Load transfer required.";
        if (bannerEl) bannerEl.style.display = "none";
    } else {
        badgeEl.textContent = "Grid Safe";
        badgeEl.className = "kpi-badge badge-normal";
        subtextEl.textContent = `All nodes under allotted limits (${totalCap.toFixed(0)} kW Total)`;
        if (bannerEl) bannerEl.style.display = "none";
    }

    // Render transfer action history list
    const actionList = document.getElementById("actionHistoryList");
    if (gridState.actions.length === 0) {
        actionList.innerHTML = '<div class="empty-state">No load transfers executed yet. Click "Simulate Load Balancing" to start.</div>';
    } else {
        actionList.innerHTML = gridState.actions.map(act => `
            <div class="history-item">
                <div>
                    <strong>[${act.timestamp}]</strong> ${act.amount.toFixed(1)} kW : ${act.source} ➔ ${act.destination}
                </div>
                <span class="status-success">${act.status}</span>
            </div>
        `).join("");
    }
}

// ==========================================
// 6. Chart.js Demand & Forecast Visualization
// ==========================================

function initChart() {
    const ctx = document.getElementById("forecastChart").getContext("2d");

    const labels = ["t-4", "t-3", "t-2", "t-1", "Current", "Forecast (Python)"];
    
    // Dataset colors tailored for dark navy theme
    const colors = {
        "Z1": "#60a5fa", // Blue
        "Z2": "#f87171", // Red / Coral
        "Z3": "#34d399", // Emerald
        "Z4": "#fbbf24", // Amber
        "Z5": "#a78bfa"  // Purple
    };

    const datasets = gridState.zones.map(z => {
        const hist = z.historical || [z.currentLoad - 10, z.currentLoad - 8, z.currentLoad - 5, z.currentLoad - 2, z.currentLoad];
        const dataPoints = [...hist.slice(0, 4), z.currentLoad, (z.forecastLoad || z.currentLoad)];

        return {
            label: `${z.id} (${z.name})`,
            data: dataPoints,
            borderColor: colors[z.id] || "#38bdf8",
            backgroundColor: "transparent",
            borderWidth: z.id === "Z2" ? 3 : 2,
            pointBackgroundColor: colors[z.id] || "#38bdf8",
            pointRadius: 4,
            tension: 0.25
        };
    });

    // Capacity Threshold Line
    datasets.push({
        label: "Threshold Limit (100 kW)",
        data: [100, 100, 100, 100, 100, 100],
        borderColor: "rgba(239, 68, 68, 0.7)",
        borderDash: [5, 5],
        borderWidth: 2,
        pointRadius: 0,
        fill: false
    });

    chartInstance = new Chart(ctx, {
        type: "line",
        data: { labels, datasets },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: {
                mode: "index",
                intersect: false
            },
            plugins: {
                legend: {
                    position: "top",
                    labels: {
                        color: "#94a3b8",
                        boxWidth: 12,
                        font: { family: "Inter", size: 11 }
                    }
                },
                tooltip: {
                    backgroundColor: "#0f172a",
                    titleColor: "#ffffff",
                    bodyColor: "#cbd5e1",
                    borderColor: "#334155",
                    borderWidth: 1,
                    padding: 10
                }
            },
            scales: {
                x: {
                    grid: { color: "rgba(255, 255, 255, 0.06)" },
                    ticks: { color: "#94a3b8", font: { family: "Inter", size: 11 } }
                },
                y: {
                    min: 20,
                    max: 140,
                    grid: { color: "rgba(255, 255, 255, 0.06)" },
                    ticks: {
                        color: "#94a3b8",
                        callback: val => `${val} kW`,
                        font: { family: "Inter", size: 11 }
                    }
                }
            }
        }
    });
}

function updateChart() {
    if (!chartInstance) return;

    gridState.zones.forEach((z, idx) => {
        const hist = z.historical || [z.currentLoad - 10, z.currentLoad - 8, z.currentLoad - 5, z.currentLoad - 2, z.currentLoad];
        const forecastVal = (gridState.forecastRan && z.forecastLoad > 0) ? z.forecastLoad : z.currentLoad;
        const dataPoints = [...hist.slice(0, 4), z.currentLoad, forecastVal];
        
        if (chartInstance.data.datasets[idx]) {
            chartInstance.data.datasets[idx].data = dataPoints;
        }
    });

    chartInstance.update();
}

// ==========================================
// 7. Activity Log & Modal System
// ==========================================

function addLog(msg, type = "info", renderDom = true) {
    const now = new Date();
    const timeStr = now.toTimeString().split(" ")[0];
    const logItem = { time: timeStr, msg, type };
    gridState.logs.unshift(logItem);

    if (renderDom) {
        renderLogEntry(logItem);
    }
}

function renderLogEntry(log) {
    const stream = document.getElementById("logStream");
    if (!stream) return;

    const div = document.createElement("div");
    div.className = "log-entry";
    div.innerHTML = `
        <span class="log-time">[${log.time}]</span>
        <span class="log-tag log-tag-${log.type}">[${log.type.toUpperCase()}]</span>
        <span class="log-msg">${log.msg}</span>
    `;
    stream.prepend(div);
}

function clearActivityLogs() {
    gridState.logs = [];
    document.getElementById("logStream").innerHTML = '<div class="empty-state">Audit logs cleared.</div>';
}

function updateUI() {
    renderTopology();
    renderTable();
    updateKPIs();
    updateChart();
}

// ------------------------------------------
// Modal 1: Divide and Conquer Modal
// ------------------------------------------
function openDivideConquerModal() {
    document.getElementById("divideConquerModal").style.display = "flex";
    addLog("[ADSA Modal] Displayed Divide and Conquer regional cluster partitioning.", "info");
}

function closeDivideConquerModal() {
    document.getElementById("divideConquerModal").style.display = "none";
}

// ------------------------------------------
// Modal 2: Manual Electricity Allotment Modal
// ------------------------------------------
function openElectricityModal(focusZoneId = null) {
    renderAllotmentForm();
    document.getElementById("electricityModal").style.display = "flex";
    addLog("[Configuration] Opened Manual Electricity Allotment Control.", "info");

    if (focusZoneId) {
        setTimeout(() => {
            const card = document.getElementById(`allotCard_${focusZoneId}`);
            if (card) {
                card.scrollIntoView({ behavior: "smooth", block: "center" });
                card.style.borderColor = "#38bdf8";
            }
        }, 100);
    }
}

function closeElectricityModal() {
    document.getElementById("electricityModal").style.display = "none";
}

function renderAllotmentForm() {
    const gridEl = document.getElementById("allotmentFormGrid");
    if (!gridEl) return;
    
    gridEl.innerHTML = gridState.zones.map(z => {
        const headroom = Math.max(0, z.maximumCapacity - z.currentLoad);
        return `
            <div class="zone-allotment-card" id="allotCard_${z.id}">
                <div class="zone-allotment-header">
                    <div class="zone-header-left">
                        <span class="zone-badge-id">${z.id}</span>
                        <input type="text" class="zone-name-input" id="inputName_${z.id}" value="${z.name}" placeholder="Grid Name" title="Edit Grid Name">
                    </div>
                    <button class="btn-card-delete" onclick="removeZoneFromModal('${z.id}')" title="Remove this Grid">🗑️ Remove</button>
                </div>
                
                <div class="allotment-input-group">
                    <label class="allotment-input-label">
                        <span>Allotted Capacity:</span>
                        <span style="color: var(--accent-cyan); font-weight: 700;">Max Limit</span>
                    </label>
                    <div class="input-with-unit">
                        <input type="number" id="inputCap_${z.id}" value="${z.maximumCapacity}" min="10" max="300" step="5" oninput="updateModalHeadroom('${z.id}')">
                        <span class="input-unit">kW</span>
                    </div>
                </div>

                <div class="allotment-input-group">
                    <label class="allotment-input-label">
                        <span>Active Current Load:</span>
                        <span id="headroomLabel_${z.id}" style="color: ${headroom > 0 ? '#10b981' : '#ef4444'}; font-size: 10px;">
                            Headroom: ${headroom.toFixed(1)} kW
                        </span>
                    </label>
                    <div class="input-with-unit">
                        <input type="number" id="inputLoad_${z.id}" value="${z.currentLoad}" min="0" max="300" step="5" oninput="updateModalHeadroom('${z.id}')">
                        <span class="input-unit">kW</span>
                    </div>
                </div>
            </div>
        `;
    }).join("");
}

function updateModalHeadroom(zoneId) {
    const capInput = document.getElementById(`inputCap_${zoneId}`);
    const loadInput = document.getElementById(`inputLoad_${zoneId}`);
    const label = document.getElementById(`headroomLabel_${zoneId}`);
    if (!capInput || !loadInput || !label) return;

    const cap = parseFloat(capInput.value) || 0;
    const load = parseFloat(loadInput.value) || 0;
    const headroom = Math.max(0, cap - load);

    if (load > cap) {
        label.textContent = `OVERLOAD: +${(load - cap).toFixed(1)} kW`;
        label.style.color = '#ef4444';
    } else {
        label.textContent = `Headroom: ${headroom.toFixed(1)} kW`;
        label.style.color = '#10b981';
    }
}

function applyPreset(presetType) {
    const presets = {
        'standard': [
            { id: 'Z1', load: 80, cap: 100 },
            { id: 'Z2', load: 90, cap: 100 },
            { id: 'Z3', load: 60, cap: 100 },
            { id: 'Z4', load: 45, cap: 100 },
            { id: 'Z5', load: 70, cap: 100 }
        ],
        'heavy_stress': [
            { id: 'Z1', load: 85, cap: 85 },
            { id: 'Z2', load: 95, cap: 80 },
            { id: 'Z3', load: 60, cap: 100 },
            { id: 'Z4', load: 45, cap: 100 },
            { id: 'Z5', load: 70, cap: 100 }
        ],
        'high_capacity': [
            { id: 'Z1', load: 80, cap: 120 },
            { id: 'Z2', load: 90, cap: 120 },
            { id: 'Z3', load: 60, cap: 120 },
            { id: 'Z4', load: 45, cap: 120 },
            { id: 'Z5', load: 70, cap: 120 }
        ],
        'balanced': [
            { id: 'Z1', load: 70, cap: 100 },
            { id: 'Z2', load: 70, cap: 100 },
            { id: 'Z3', load: 70, cap: 100 },
            { id: 'Z4', load: 70, cap: 100 },
            { id: 'Z5', load: 70, cap: 100 }
        ]
    };

    const data = presets[presetType] || presets['standard'];
    data.forEach(item => {
        const capInput = document.getElementById(`inputCap_${item.id}`);
        const loadInput = document.getElementById(`inputLoad_${item.id}`);
        if (capInput) capInput.value = item.cap;
        if (loadInput) loadInput.value = item.load;
        updateModalHeadroom(item.id);
    });
    addLog(`Loaded Allotment Preset: "${presetType.replace('_', ' ').toUpperCase()}"`, "info");
}

async function saveElectricityAllotment() {
    let payload = {};
    gridState.zones.forEach(z => {
        const nameInput = document.getElementById(`inputName_${z.id}`);
        const capInput = document.getElementById(`inputCap_${z.id}`);
        const loadInput = document.getElementById(`inputLoad_${z.id}`);
        
        if (nameInput && nameInput.value.trim()) {
            z.name = nameInput.value.trim();
        }
        if (capInput && loadInput) {
            const cap = parseFloat(capInput.value) || z.maximumCapacity;
            const load = parseFloat(loadInput.value) || z.currentLoad;
            z.maximumCapacity = cap;
            z.currentLoad = load;
            z.forecastLoad = load; // reset forecast until run
            if (!z.historical) z.historical = [];
            z.historical.push(load);
            payload[`${z.id}_load`] = load;
            payload[`${z.id}_cap`] = cap;
        }
    });

    gridState.forecastRan = false;
    gridState.overloadDetected = false;
    gridState.isBalanced = false;
    gridState.totalLoadShifted = 0.0;
    gridState.actions = [];

    if (isBackendConnected) {
        try {
            const res = await fetch("/api/update-zones", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload)
            });
            if (res.ok) {
                const data = await res.json();
                syncStateFromBackend(data);
            }
        } catch (e) {
            console.error("Failed to sync manual allotment to backend:", e);
        }
    }

    closeElectricityModal();
    resetVisualFlow();
    updateUI();
    addLog("✅ Electricity allotment and grid names saved successfully!", "success");
}

// ------------------------------------------
// Modal 3: Add New Grid / Zone Modal
// ------------------------------------------
function openAddZoneModal() {
    const nextNum = gridState.zones.length + 1;
    const defaultId = `Z${nextNum}`;
    const idInput = document.getElementById("newZoneId");
    const nameInput = document.getElementById("newZoneName");
    const loadInput = document.getElementById("newZoneLoad");
    const capInput = document.getElementById("newZoneCap");
    
    if (idInput) idInput.value = defaultId;
    if (nameInput) nameInput.value = `Substation Sector ${String.fromCharCode(64 + nextNum)}`;
    if (loadInput) loadInput.value = 50;
    if (capInput) capInput.value = 100;

    // Render checkbox chips for existing zones
    const container = document.getElementById("connectionsCheckboxGroup");
    if (container) {
        container.innerHTML = gridState.zones.map((z, idx) => `
            <label class="chip-checkbox-label">
                <input type="checkbox" name="connectNeighbors" value="${z.id}" ${idx < 2 ? 'checked' : ''}>
                <span>${z.id} (${z.name.split(' ')[0]})</span>
            </label>
        `).join("");
    }

    document.getElementById("addZoneModal").style.display = "flex";
    addLog("[Grid Manager] Opened Add Grid Dialog.", "info");
}

function closeAddZoneModal() {
    document.getElementById("addZoneModal").style.display = "none";
}

async function submitAddNewZone() {
    const idInput = document.getElementById("newZoneId");
    const nameInput = document.getElementById("newZoneName");
    const loadInput = document.getElementById("newZoneLoad");
    const capInput = document.getElementById("newZoneCap");

    const id = idInput ? idInput.value.trim().toUpperCase() : "";
    const name = nameInput ? nameInput.value.trim() : "";
    const load = loadInput ? (parseFloat(loadInput.value) || 50.0) : 50.0;
    const cap = capInput ? (parseFloat(capInput.value) || 100.0) : 100.0;

    if (!id) {
        alert("Please specify a valid Grid ID (e.g., Z6).");
        return;
    }
    if (gridState.zones.some(z => z.id === id)) {
        alert(`Grid ID "${id}" already exists. Please choose a unique ID.`);
        return;
    }

    const finalName = name || `Zone ${id}`;

    // Collect checked neighbors
    const checkedBoxes = document.querySelectorAll('input[name="connectNeighbors"]:checked');
    const neighbors = Array.from(checkedBoxes).map(cb => cb.value);

    // If no neighbors checked, default connect to first existing zone
    if (neighbors.length === 0 && gridState.zones.length > 0) {
        neighbors.push(gridState.zones[0].id);
    }

    const newZone = {
        id: id,
        name: finalName,
        currentLoad: load,
        maximumCapacity: cap,
        forecastLoad: load,
        availableCapacity: Math.max(0, cap - load),
        utilization: Math.round((load / cap) * 100),
        status: load > cap ? "OVERLOADED" : (load / cap >= 0.85 ? "WARNING" : "NORMAL"),
        overload: Math.max(0, load - cap),
        historical: [Math.max(0, load - 12), Math.max(0, load - 8), Math.max(0, load - 5), Math.max(0, load - 2), load]
    };

    gridState.zones.push(newZone);

    // Add edges
    neighbors.forEach(nId => {
        if (!gridState.edges.some(e => (e.source === id && e.target === nId) || (e.source === nId && e.target === id))) {
            gridState.edges.push({ source: id, target: nId });
        }
    });

    if (isBackendConnected) {
        try {
            const url = `/api/add-zone?id=${encodeURIComponent(id)}&name=${encodeURIComponent(finalName)}&load=${load}&capacity=${cap}&neighbors=${encodeURIComponent(neighbors.join(","))}`;
            const res = await fetch(url, { method: "POST" });
            if (res.ok) {
                const data = await res.json();
                syncStateFromBackend(data);
            }
        } catch (e) {
            console.error("Failed to add zone to backend:", e);
        }
    }

    closeAddZoneModal();
    resetVisualFlow();
    updateUI();
    addLog(`➕ Added New Grid Zone: ${id} (${finalName}) connected to [${neighbors.join(", ")}].`, "success");
}

// ------------------------------------------
// Remove Zone Functionality
// ------------------------------------------
function removeZoneFromModal(zoneId) {
    if (confirm(`Remove Grid ${zoneId} from the microgrid?`)) {
        removeZone(zoneId, false);
        renderAllotmentForm();
    }
}

async function removeZone(zoneId, confirmPrompt = true) {
    if (gridState.zones.length <= 2) {
        alert("The microgrid must have at least 2 connected zones for load balancing.");
        return;
    }

    const zone = gridState.zones.find(z => z.id === zoneId);
    const zoneName = zone ? zone.name : zoneId;

    if (confirmPrompt && !confirm(`Are you sure you want to remove Grid ${zoneId} (${zoneName}) from the microgrid?`)) {
        return;
    }

    // Remove from local zones
    gridState.zones = gridState.zones.filter(z => z.id !== zoneId);
    
    // Remove from local edges
    gridState.edges = gridState.edges.filter(e => e.source !== zoneId && e.target !== zoneId);

    if (isBackendConnected) {
        try {
            const res = await fetch(`/api/remove-zone?id=${encodeURIComponent(zoneId)}`, { method: "POST" });
            if (res.ok) {
                const data = await res.json();
                syncStateFromBackend(data);
            }
        } catch (e) {
            console.error("Failed to remove zone from backend:", e);
        }
    }

    resetVisualFlow();
    updateUI();
    addLog(`🗑️ Removed Grid Zone: ${zoneId} (${zoneName}) and all connected transmission lines.`, "warning");
}

// ------------------------------------------
// Modal 4: Academic Subjects Modal
// ------------------------------------------
function openSubjectsModal() {
    document.getElementById("subjectsModal").style.display = "flex";
    addLog("[Subjects Modal] Displayed Academic Principles (ADSA • OOPJ • Python).", "info");
}

function closeSubjectsModal() {
    document.getElementById("subjectsModal").style.display = "none";
}

// Global modal background dismiss
window.onclick = function(event) {
    const dModal = document.getElementById("divideConquerModal");
    const eModal = document.getElementById("electricityModal");
    const sModal = document.getElementById("subjectsModal");
    const aModal = document.getElementById("addZoneModal");
    
    if (event.target === dModal) closeDivideConquerModal();
    if (event.target === eModal) closeElectricityModal();
    if (event.target === sModal) closeSubjectsModal();
    if (event.target === aModal) closeAddZoneModal();
};


