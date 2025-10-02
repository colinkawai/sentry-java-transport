package com.example;

import io.sentry.*;
import io.sentry.transport.ITransport;
import io.sentry.transport.RateLimiter;
import io.sentry.transport.AsyncHttpTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

//Custom transport that routes Sentry events to different projects based on event content
public class RoutingTransport implements ITransport {
    
    private final ILogger logger;
    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final Map<String, ITransport> transportCache;
    private final SentryOptions baseOptions;
    
    private final ProjectRoute[] projectRoutes;
    
    public RoutingTransport(SentryOptions options) {
        this.logger = options.getLogger();
        this.rateLimiter = new RateLimiter(options);
        this.objectMapper = new ObjectMapper();
        this.transportCache = new ConcurrentHashMap<>();
        this.baseOptions = options;
        this.projectRoutes = RoutingConfiguration.loadRoutes();
        
        logger.log(SentryLevel.DEBUG, "RoutingTransport initialized with " + projectRoutes.length + " project routes");
    }
    
    @Override
    public void send(SentryEnvelope envelope, Hint hint) throws IOException {
        String targetDsn = analyzeEventContentAndRoute(envelope);

        ITransport targetTransport = getOrCreateTransport(targetDsn);
        if (targetTransport != null) {
            targetTransport.send(envelope, hint);
            logger.log(SentryLevel.INFO, "Event successfully sent to " + getProjectName(targetDsn));
        } else {
            logger.log(SentryLevel.ERROR, "Failed to create transport for DSN: " + maskDsn(targetDsn));
        }
    }
    
    private String analyzeEventContentAndRoute(SentryEnvelope envelope) {
        try {
            for (SentryEnvelopeItem item : envelope.getItems()) {
                SentryItemType itemType = item.getHeader().getType();
                
                if (itemType.equals(SentryItemType.Event)) {
                    return routeEvent(item);
                } else if (itemType.equals(SentryItemType.Transaction)) {
                    return routeTransaction(item);
                } else {
                    logger.log(SentryLevel.DEBUG, "Routing non-event telemetry type: " + itemType);
                    return getDefaultDsn();
                }
            }
        } catch (Exception e) {
            logger.log(SentryLevel.ERROR, "Error analyzing content for routing", e);
        }
        
        return getDefaultDsn();
    }
    
    private String routeEvent(SentryEnvelopeItem item) {
        try {
            String eventJson = new String(item.getData());
            JsonNode eventNode = objectMapper.readTree(eventJson);
            
            Map<String, String> tags = extractTags(eventNode);
            String exceptionType = extractExceptionType(eventNode);
            String message = extractMessage(eventNode);
            
        for (ProjectRoute route : projectRoutes) {
            if (route.matches(tags, exceptionType, message)) {
                logger.log(SentryLevel.DEBUG, "Event matched project: " + route.name);
                return route.dsn;
                }
            }
        } catch (Exception e) {
            logger.log(SentryLevel.ERROR, "Error routing event", e);
        }
        
        return getDefaultDsn();
    }
    
    private String routeTransaction(SentryEnvelopeItem item) {
        try {
            String transactionJson = new String(item.getData());
            JsonNode transactionNode = objectMapper.readTree(transactionJson);
            
            Map<String, String> tags = extractTags(transactionNode);
            
        if (tags != null) {
            for (ProjectRoute route : projectRoutes) {
                for (String tag : route.tags) {
                    if (tags.containsKey(tag)) {
                        logger.log(SentryLevel.DEBUG, "Transaction matched project: " + route.name);
                        return route.dsn;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.log(SentryLevel.ERROR, "Error routing transaction", e);
        }
        
        return getDefaultDsn();
    }
    
    private String getDefaultDsn() {
        return projectRoutes.length > 0 ? projectRoutes[projectRoutes.length - 1].dsn : null;
    }
    
    private Map<String, String> extractTags(JsonNode eventNode) {
        JsonNode tagsNode = eventNode.get("tags");
        if (tagsNode == null || !tagsNode.isObject()) return Collections.emptyMap();
        
        Map<String, String> tags = new HashMap<>();
        tagsNode.fields().forEachRemaining(entry -> 
            tags.put(entry.getKey(), entry.getValue().asText()));
        return tags;
    }
    
    private String extractExceptionType(JsonNode eventNode) {
        JsonNode exceptionsNode = eventNode.get("exception");
        if (exceptionsNode != null && exceptionsNode.has("values")) {
            JsonNode valuesNode = exceptionsNode.get("values");
            if (valuesNode.isArray() && valuesNode.size() > 0) {
                JsonNode firstException = valuesNode.get(0);
                JsonNode typeNode = firstException.get("type");
                if (typeNode != null) {
                    return typeNode.asText();
                }
            }
        }
        return null;
    }
    
    private String extractMessage(JsonNode eventNode) {
        JsonNode messageNode = eventNode.get("message");
        if (messageNode != null) {
            JsonNode formattedNode = messageNode.get("formatted");
            if (formattedNode != null) {
                return formattedNode.asText();
            }
            return messageNode.asText();
        }
        return null;
    }
    
    private ITransport getOrCreateTransport(String dsn) {
        return transportCache.computeIfAbsent(dsn, this::createTransportForDsn);
    }
    
    private ITransport createTransportForDsn(String dsn) {
        return new DirectHttpTransport(dsn, logger);
    }
    
    private static class DirectHttpTransport implements ITransport {
        private final String dsn;
        private final ILogger logger;
        private final RateLimiter rateLimiter;
        private final String apiUrl;
        private final String authKey;
        
        public DirectHttpTransport(String dsn, ILogger logger) {
            this.dsn = dsn;
            this.logger = logger;
            this.rateLimiter = new RateLimiter(new SentryOptions());
            
            String protocol = dsn.substring(0, dsn.indexOf("://"));
            String remaining = dsn.substring(dsn.indexOf("://") + 3);
            this.authKey = remaining.substring(0, remaining.indexOf("@"));
            String hostAndProject = remaining.substring(remaining.indexOf("@") + 1);
            String host = hostAndProject.substring(0, hostAndProject.indexOf("/"));
            String projectId = hostAndProject.substring(hostAndProject.indexOf("/") + 1);
            
            this.apiUrl = protocol + "://" + host + "/api/" + projectId + "/envelope/";
        }
        
        @Override
        public void send(SentryEnvelope envelope, Hint hint) throws IOException {
            try {
                
                java.net.URL url = new java.net.URL(apiUrl);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/x-sentry-envelope");
                connection.setRequestProperty("User-Agent", "sentry.java/8.22.0");
                connection.setRequestProperty("X-Sentry-Auth", 
                    "Sentry sentry_version=7, sentry_client=sentry.java/8.22.0, sentry_key=" + authKey);
                
                try (java.io.OutputStream os = connection.getOutputStream()) {
                    StringBuilder envelopeBuilder = new StringBuilder();
                    
                    envelopeBuilder.append("{\"event_id\":\"")
                        .append(envelope.getHeader().getEventId() != null ? envelope.getHeader().getEventId() : "unknown")
                        .append("\",\"sent_at\":\"")
                        .append(java.time.Instant.now().toString())
                        .append("\"}\n");
                    
                    for (SentryEnvelopeItem item : envelope.getItems()) {
                        envelopeBuilder.append("{\"type\":\"")
                            .append(item.getHeader().getType().getItemType())
                            .append("\",\"length\":")
                            .append(item.getData().length)
                            .append("}\n");
                        envelopeBuilder.append(new String(item.getData()));
                    }
                    
                    byte[] envelopeBytes = envelopeBuilder.toString().getBytes("UTF-8");
                    os.write(envelopeBytes);
                }
                
                int responseCode = connection.getResponseCode();
                
                if (responseCode < 200 || responseCode >= 300) {
                    logger.log(SentryLevel.WARNING, "Unexpected response code: " + responseCode);
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(connection.getErrorStream()))) {
                        String errorResponse = reader.lines().reduce("", (a, b) -> a + b);
                        logger.log(SentryLevel.ERROR, "Sentry error response: " + errorResponse);
                    } catch (Exception e) {
                        logger.log(SentryLevel.ERROR, "Could not read error response");
                    }
                }
                
            } catch (Exception e) {
                logger.log(SentryLevel.ERROR, "Failed to send to Sentry", e);
                throw new IOException("Failed to send to Sentry", e);
            }
        }
        
        @Override
        public void flush(long timeoutMillis) { }
        
        @Override
        public void close() throws IOException { }
        
        @Override
        public void close(boolean isRestarting) throws IOException { }
        
        @Override
        public RateLimiter getRateLimiter() {
            return rateLimiter;
        }
    }
    
    private String maskDsn(String dsn) {
        if (dsn == null) return "null";
        return dsn.replaceAll("://[^@]+@", "://***@");
    }
    
    private String getProjectName(String dsn) {
        for (ProjectRoute route : projectRoutes) {
            if (route.dsn.equals(dsn)) {
                return route.name;
            }
        }
        return "Unknown Project";
    }
    
    @Override
    public void flush(long timeoutMillis) {
        logger.log(SentryLevel.DEBUG, "Flushing all cached transports");
        for (ITransport transport : transportCache.values()) {
            if (transport != null) {
                transport.flush(timeoutMillis);
            }
        }
    }
    
    @Override
    public void close() throws IOException {
        logger.log(SentryLevel.INFO, "Closing RoutingTransport and all cached transports");
        for (ITransport transport : transportCache.values()) {
            if (transport != null) {
                transport.close();
            }
        }
        transportCache.clear();
    }
    
    @Override
    public void close(boolean isRestarting) throws IOException {
        logger.log(SentryLevel.INFO, "Closing RoutingTransport");
        for (ITransport transport : transportCache.values()) {
            if (transport != null) {
                transport.close(isRestarting);
            }
        }
        transportCache.clear();
    }
    
    @Override
    public RateLimiter getRateLimiter() {
        return rateLimiter;
    }
}
