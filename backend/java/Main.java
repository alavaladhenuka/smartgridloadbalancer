package backend.java;

/**
 * Entry Point for the Smart Grid Load Balancer Project.
 * 
 * Usage:
 * 1. Web Mode (Default): java backend.java.Main [port]
 *    Starts the local HTTP Web Server and hosts the interactive dashboard on http://localhost:8080
 * 
 * 2. CLI Mode: java backend.java.Main --cli
 *    Runs the complete academic ADSA/OOPJ simulation demonstration in the terminal.
 */
public class Main {
    public static void main(String[] args) {
        Simulation simulation = new Simulation();

        boolean cliMode = false;
        int port = 8080;

        for (String arg : args) {
            if (arg.equalsIgnoreCase("--cli") || arg.equalsIgnoreCase("cli")) {
                cliMode = true;
            } else if (arg.matches("\\d+")) {
                port = Integer.parseInt(arg);
            }
        }

        if (cliMode) {
            // Run Full Terminal Demonstration
            simulation.runFullDemonstration();
        } else {
            // Run Web Server for Interactive Dashboard
            try {
                // Initialize baseline simulation and export state
                simulation.initializeGrid();
                simulation.exportHistoricalData();

                SimpleHttpServer server = new SimpleHttpServer(port, simulation);
                server.start();

                System.out.println("System ready. Open your web browser to view the live dashboard.");
                System.out.println("Press Ctrl+C to terminate the server.\n");

                // Keep main thread alive
                Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

            } catch (Exception e) {
                System.err.println("[!] Failed to start HTTP server: " + e.getMessage());
                System.out.println("[*] Falling back to CLI demonstration mode...\n");
                simulation.runFullDemonstration();
            }
        }
    }
}
