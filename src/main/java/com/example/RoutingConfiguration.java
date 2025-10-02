package com.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Production-ready routing configuration with JSON support and hardcoded fallback.
 */
public class RoutingConfiguration {
    
    private static final String CONFIG_FILE = "sentry-routing-config.json";
    private static final ObjectMapper mapper = new ObjectMapper();
    
    public static ProjectRoute[] loadRoutes() {
        try {
            // Load from classpath (works in both dev and production)
            java.io.InputStream is = RoutingConfiguration.class.getClassLoader()
                .getResourceAsStream(CONFIG_FILE);
            
            if (is != null) {
                System.out.println("========================================");
                System.out.println("Loading routing configuration from classpath: " + CONFIG_FILE);
                RoutingConfigJson config = mapper.readValue(is, RoutingConfigJson.class);
                ProjectRoute[] routes = config.toProjectRoutes();
                System.out.println("Loaded " + routes.length + " routes from JSON:");
                for (ProjectRoute route : routes) {
                    System.out.println("  - " + route.name + " -> " + route.dsn.substring(0, 50) + "...");
                }
                System.out.println("========================================");
                return routes;
            } else {
                System.out.println("Config file not found in classpath, using defaults");
            }
        } catch (IOException e) {
            System.err.println("========================================");
            System.err.println("ERROR: Failed to load routing config from JSON!");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.err.println("Using fallback defaults with placeholder DSNs");
            System.err.println("========================================");
        }
        
        return getDefaultRoutes();
    }
    
    private static ProjectRoute[] getDefaultRoutes() {
        return new ProjectRoute[] {
            new ProjectRoute(
                "Gateway Project",
                "https://YOUR_GATEWAY_PROJECT_KEY@o0.ingest.sentry.io/YOUR_GATEWAY_PROJECT_ID",
                new String[]{"gateway"},
                new String[]{"502"},
                new String[]{"BadGatewayException"},
                new String[]{"502 Bad Gateway", "Upstream service"}
            ),
            
            new ProjectRoute(
                "Internal Errors Project",
                "https://YOUR_INTERNAL_PROJECT_KEY@o0.ingest.sentry.io/YOUR_INTERNAL_PROJECT_ID",
                new String[]{"internal"},
                new String[]{"500"},
                new String[]{"InternalServerException"},
                new String[]{"500 Internal Server Error", "Database connection"}
            ),
            
            new ProjectRoute(
                "Default Project", 
                "https://YOUR_DEFAULT_PROJECT_KEY@o0.ingest.sentry.io/YOUR_DEFAULT_PROJECT_ID",
                new String[]{"generic", "default"},
                new String[]{"400", "404"},
                new String[]{"RuntimeException", "Exception"},
                new String[]{"Generic application error"}
            )
        };
    }
    
    public static class RoutingConfigJson {
        public List<ProjectRouteJson> projects;
        
        public ProjectRoute[] toProjectRoutes() {
            return projects.stream()
                .map(p -> new ProjectRoute(
                    p.name,
                    p.dsn,
                    p.rules.tags.toArray(new String[0]),
                    p.rules.statusCodes.toArray(new String[0]),
                    p.rules.exceptionTypes.toArray(new String[0]),
                    p.rules.messageKeywords.toArray(new String[0])
                ))
                .toArray(ProjectRoute[]::new);
        }
    }
    
    public static class ProjectRouteJson {
        public String name;
        public String dsn;
        public RulesJson rules;
    }
    
    public static class RulesJson {
        public List<String> tags = new ArrayList<>();
        public List<String> statusCodes = new ArrayList<>();
        public List<String> exceptionTypes = new ArrayList<>();
        public List<String> messageKeywords = new ArrayList<>();
    }
}

