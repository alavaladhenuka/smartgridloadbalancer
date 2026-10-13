package backend.java;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Lightweight Zero-Dependency HTTP Web Server using Java Standard Library (com.sun.net.httpserver).
 * 
 * Serves:
 * 1. The Interactive Frontend Dashboard (index.html, style.css, script.js)
 * 2. REST API endpoints connecting the UI directly to the Java simulation engine and Python forecaster.
 */
public class SimpleHttpServer {
    private final int port;
    private final Simulation simulation;
    private final String frontendDir;
    private HttpServer server;

    public SimpleHttpServer(int port, Simulation simulation) {
        this.port = port;
        this.simulation = simulation;
        this.frontendDir = findFrontendDirectory();
    }

    private String findFrontendDirectory() {
        Path current = Paths.get("").toAbsolutePath();
        if (Files.exists(current.resolve("frontend"))) {
            return current.resolve("frontend").toString();
        }
        if (Files.exists(current.resolve("../frontend"))) {
            return current.resolve("../frontend").normalize().toString();
        }
        if (Files.exists(current.resolve("../../frontend"))) {
            return current.resolve("../../frontend").normalize().toString();
        }
        return Paths.get(current.toString(), "frontend").toString();
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);

        // API Endpoints
        server.createContext("/api/status", new StatusHandler());
        server.createContext("/api/run-forecast", new ForecastHandler());
        server.createContext("/api/detect-overload", new OverloadHandler());
        server.createContext("/api/balance", new BalanceHandler());
        server.createContext("/api/reset", new ResetHandler());
        server.createContext("/api/divide-conquer", new DivideConquerHandler());
        server.createContext("/api/update-zone", new UpdateZoneHandler());
        server.createContext("/api/update-zones", new UpdateZoneHandler());
        server.createContext("/api/add-zone", new AddZoneHandler());
        server.createContext("/api/remove-zone", new RemoveZoneHandler());

        // Static Frontend Files
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(null); // default multithread executor
        server.start();

        System.out.println("================================================================================");
        System.out.printf("⚡ SMART GRID LOAD BALANCER WEB DASHBOARD IS ONLINE!%n");
        System.out.printf("🌐 URL: http://localhost:%d%n", port);
        System.out.println("================================================================================");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            System.out.println("[*] HTTP Server stopped.");
        }
    }

    // ==========================================
    // HTTP Handlers
    // ==========================================

    private void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, String jsonResponse) throws IOException {
        addCorsHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        byte[] bytes = jsonResponse.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String getUnifiedStateJson() {
        return String.format("{\"grid\":%s,\"balancer\":%s}",
                simulation.getGrid().toJson(), simulation.getLoadBalancer().toJson());
    }

    class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            sendJsonResponse(exchange, 200, getUnifiedStateJson());
        }
    }

    class ForecastHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            simulation.runPythonForecast();
            sendJsonResponse(exchange, 200, getUnifiedStateJson());
        }
    }

    class OverloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            simulation.detectOverloads();
            sendJsonResponse(exchange, 200, getUnifiedStateJson());
        }
    }

    class BalanceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            simulation.executeLoadBalancing();
            sendJsonResponse(exchange, 200, getUnifiedStateJson());
        }
    }

    class ResetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            simulation.reset();
            sendJsonResponse(exchange, 200, getUnifiedStateJson());
        }
    }

    class DivideConquerHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            List<List<Zone>> partitions = simulation.getGrid().runDivideAndConquerPartition();
            StringBuilder sb = new StringBuilder("{\"partitions\":[");
            for (int i = 0; i < partitions.size(); i++) {
                sb.append("[");
                List<Zone> group = partitions.get(i);
                for (int j = 0; j < group.size(); j++) {
                    sb.append("\"").append(group.get(j).getId()).append("\"");
                    if (j < group.size() - 1) sb.append(",");
                }
                sb.append("]");
                if (i < partitions.size() - 1) sb.append(",");
            }
            sb.append("]}");
            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    class UpdateZoneHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            try {
                String query = exchange.getRequestURI().getQuery();
                if (query != null && !query.isEmpty()) {
                    String zoneId = null;
                    double load = -1;
                    double capacity = -1;
                    for (String param : query.split("&")) {
                        String[] pair = param.split("=");
                        if (pair.length == 2) {
                            String k = java.net.URLDecoder.decode(pair[0].trim(), java.nio.charset.StandardCharsets.UTF_8);
                            String v = java.net.URLDecoder.decode(pair[1].trim(), java.nio.charset.StandardCharsets.UTF_8);
                            if (k.equalsIgnoreCase("id") || k.equalsIgnoreCase("zoneId")) {
                                zoneId = v;
                            } else if (k.equalsIgnoreCase("load") || k.equalsIgnoreCase("currentLoad")) {
                                load = Double.parseDouble(v);
                            } else if (k.equalsIgnoreCase("capacity") || k.equalsIgnoreCase("cap") || k.equalsIgnoreCase("maxCapacity")) {
                                capacity = Double.parseDouble(v);
                            }
                        }
                    }
                    if (zoneId != null) {
                        simulation.updateZone(zoneId, load, capacity);
                    }
                } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    String body = new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    // Support JSON: {"Z1_load": 80, "Z1_cap": 100, ...} or {"id": "Z1", "load": 80, "capacity": 100}
                    String clean = body.replaceAll("[{}\"\\s]", "");
                    for (String item : clean.split(",")) {
                        String[] pair = item.split(":");
                        if (pair.length == 2) {
                            String k = pair[0];
                            String v = pair[1];
                            if (k.contains("_")) {
                                String[] parts = k.split("_");
                                String zId = parts[0];
                                String type = parts[1];
                                Zone z = simulation.getGrid().getZone(zId);
                                if (z != null) {
                                    double num = Double.parseDouble(v);
                                    if (type.equalsIgnoreCase("load")) z.updateLoad(num);
                                    if (type.equalsIgnoreCase("cap") || type.equalsIgnoreCase("capacity") || type.equalsIgnoreCase("allotted")) z.setMaximumCapacity(num);
                                }
                            }
                        }
                    }
                    simulation.exportHistoricalData();
                    simulation.exportStateJson();
                }
            } catch (Exception e) {
                System.err.println("[!] Error updating zone: " + e.getMessage());
            }
            sendJsonResponse(exchange, 200, getUnifiedStateJson());
        }
    }

    class AddZoneHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            try {
                String id = null;
                String name = null;
                double load = 50.0;
                double capacity = 100.0;
                List<String> connectedTo = new ArrayList<>();

                String query = exchange.getRequestURI().getQuery();
                if (query != null && !query.isEmpty()) {
                    for (String param : query.split("&")) {
                        String[] pair = param.split("=");
                        if (pair.length == 2) {
                            String k = java.net.URLDecoder.decode(pair[0].trim(), java.nio.charset.StandardCharsets.UTF_8);
                            String v = java.net.URLDecoder.decode(pair[1].trim(), java.nio.charset.StandardCharsets.UTF_8);
                            if (k.equalsIgnoreCase("id")) id = v;
                            else if (k.equalsIgnoreCase("name")) name = v;
                            else if (k.equalsIgnoreCase("load")) load = Double.parseDouble(v);
                            else if (k.equalsIgnoreCase("capacity") || k.equalsIgnoreCase("cap")) capacity = Double.parseDouble(v);
                            else if (k.equalsIgnoreCase("connect") || k.equalsIgnoreCase("neighbors")) {
                                for (String c : v.split(",")) {
                                    if (!c.trim().isEmpty()) connectedTo.add(c.trim());
                                }
                            }
                        }
                    }
                } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    String body = new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    String clean = body.replaceAll("[{}\"]", "");
                    for (String item : clean.split(",")) {
                        String[] pair = item.split(":");
                        if (pair.length == 2) {
                            String k = pair[0].trim();
                            String v = pair[1].trim();
                            if (k.equalsIgnoreCase("id")) id = v;
                            else if (k.equalsIgnoreCase("name")) name = v;
                            else if (k.equalsIgnoreCase("load")) load = Double.parseDouble(v);
                            else if (k.equalsIgnoreCase("capacity") || k.equalsIgnoreCase("cap")) capacity = Double.parseDouble(v);
                            else if (k.equalsIgnoreCase("connect") || k.equalsIgnoreCase("neighbors")) {
                                for (String c : v.replace("[", "").replace("]", "").split(";")) {
                                    if (!c.trim().isEmpty()) connectedTo.add(c.trim());
                                }
                            }
                        }
                    }
                }
                if (id != null && !id.trim().isEmpty()) {
                    simulation.addCustomZone(id, name, load, capacity, connectedTo);
                }
            } catch (Exception e) {
                System.err.println("[!] Error in AddZoneHandler: " + e.getMessage());
            }
            sendJsonResponse(exchange, 200, getUnifiedStateJson());
        }
    }

    class RemoveZoneHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            try {
                String id = null;
                String query = exchange.getRequestURI().getQuery();
                if (query != null && !query.isEmpty()) {
                    for (String param : query.split("&")) {
                        String[] pair = param.split("=");
                        if (pair.length == 2 && (pair[0].equalsIgnoreCase("id") || pair[0].equalsIgnoreCase("zoneId"))) {
                            id = java.net.URLDecoder.decode(pair[1].trim(), java.nio.charset.StandardCharsets.UTF_8);
                        }
                    }
                } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    String body = new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    String clean = body.replaceAll("[{}\"\\s]", "");
                    for (String item : clean.split(",")) {
                        String[] pair = item.split(":");
                        if (pair.length == 2 && (pair[0].equalsIgnoreCase("id") || pair[0].equalsIgnoreCase("zoneId"))) {
                            id = pair[1].trim();
                        }
                    }
                }
                if (id != null && !id.trim().isEmpty()) {
                    simulation.removeCustomZone(id);
                }
            } catch (Exception e) {
                System.err.println("[!] Error in RemoveZoneHandler: " + e.getMessage());
            }
            sendJsonResponse(exchange, 200, getUnifiedStateJson());
        }
    }

    class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            // Strip leading slashes so File resolution on Windows treats path as relative to frontendDir
            String relativePath = path.startsWith("/") ? path.substring(1) : path;
            if (relativePath.isEmpty()) {
                relativePath = "index.html";
            }

            File file = new File(frontendDir, relativePath);
            if (!file.exists() || file.isDirectory()) {
                file = new File(frontendDir, "index.html");
            }

            if (!file.exists()) {
                String notFound = "404 Not Found - Frontend directory missing";
                exchange.sendResponseHeaders(404, notFound.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(notFound.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                return;
            }

            String contentType = "text/html; charset=utf-8";
            String lowerName = file.getName().toLowerCase();
            if (lowerName.endsWith(".css")) {
                contentType = "text/css; charset=utf-8";
            } else if (lowerName.endsWith(".js")) {
                contentType = "application/javascript; charset=utf-8";
            } else if (lowerName.endsWith(".json")) {
                contentType = "application/json; charset=utf-8";
            } else if (lowerName.endsWith(".svg")) {
                contentType = "image/svg+xml";
            } else if (lowerName.endsWith(".png")) {
                contentType = "image/png";
            } else if (lowerName.endsWith(".ico")) {
                contentType = "image/x-icon";
            }

            addCorsHeaders(exchange);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            exchange.sendResponseHeaders(200, fileBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(fileBytes);
            }
        }
    }
}
