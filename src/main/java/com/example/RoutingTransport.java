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

//Custom transport that routes Sentry events to different projects based on event content analysis.
public class RoutingTransport implements ITransport {
    
    private final ILogger logger;
    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final Map<String, ITransport> transportCache;
    private final SentryOptions baseOptions;
    
    // DSNs are now defined in SimpleRoutingConfig.PROJECT_BOXES
    private final SimpleRoutingConfig.ProjectBox[] projectBoxes;
    
    public RoutingTransport(SentryOptions options) {
        this.logger = options.getLogger();
        this.rateLimiter = new RateLimiter(options);
        this.objectMapper = new ObjectMapper();
        this.transportCache = new ConcurrentHashMap<>();
        this.baseOptions = options;
        this.projectBoxes = SimpleRoutingConfig.PROJECT_BOXES;
        
        logger.log(SentryLevel.INFO, "Simple RoutingTransport initialized with " + projectBoxes.length + " project boxes");
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
                if (item.getHeader().getType().equals(SentryItemType.Event)) {
                    String eventJson = new String(item.getData());
                    JsonNode eventNode = objectMapper.readTree(eventJson);
                    
                    // Extract simple data for matching
                    Map<String, String> tags = extractTags(eventNode);
                    String exceptionType = extractExceptionType(eventNode);
                    String message = extractMessage(eventNode);
                    
                    for (SimpleRoutingConfig.ProjectBox box : projectBoxes) {
                        if (box.matches(tags, exceptionType, message)) {
                            return box.dsn;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.log(SentryLevel.ERROR, "Error analyzing event content for routing", e);
        }
        
        logger.log(SentryLevel.INFO, "No project boxes matched, using last box as default");
        return projectBoxes.length > 0 ? projectBoxes[projectBoxes.length - 1].dsn : null;
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
        logger.log(SentryLevel.INFO, "Creating HTTP transport for: " + maskDsn(dsn));
        return new RealHttpTransport(dsn, logger);
    }
    
    private static class RealHttpTransport implements ITransport {
        private final String dsn;
        private final ILogger logger;
        private final RateLimiter rateLimiter;
        private final String apiUrl;
        private final String authKey;
        
        public RealHttpTransport(String dsn, ILogger logger) {
            this.dsn = dsn;
            this.logger = logger;
            this.rateLimiter = new RateLimiter(new SentryOptions());
            
            // Parse DSN to extract components
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
                connection.setRequestProperty("User-Agent", "sentry.java.routing.demo/1.0.0");
                connection.setRequestProperty("X-Sentry-Auth", 
                    "Sentry sentry_version=7, sentry_client=sentry.java.routing.demo/1.0.0, sentry_key=" + authKey);
                
                try (java.io.OutputStream os = connection.getOutputStream()) {
                    StringBuilder envelopeBuilder = new StringBuilder();
                    
                    // Envelope header
                    envelopeBuilder.append("{\"event_id\":\"")
                        .append(envelope.getHeader().getEventId() != null ? envelope.getHeader().getEventId() : "unknown")
                        .append("\",\"sent_at\":\"")
                        .append(java.time.Instant.now().toString())
                        .append("\"}")
                        .append("\n");
                    
                    // Event item header and data
                    for (SentryEnvelopeItem item : envelope.getItems()) {
                        if (item.getHeader().getType().equals(SentryItemType.Event)) {
                            envelopeBuilder.append("{\"type\":\"event\",\"length\":")
                                .append(item.getData().length)
                                .append("}")
                                .append("\n");
                            envelopeBuilder.append(new String(item.getData()));
                            break;
                        }
                    }
                    
                    byte[] envelopeBytes = envelopeBuilder.toString().getBytes("UTF-8");
                    os.write(envelopeBytes);
                }
                
                int responseCode = connection.getResponseCode();
                logger.log(SentryLevel.INFO, "HTTP Response: " + responseCode + " from Sentry");
                
                if (responseCode >= 200 && responseCode < 300) {
                    logger.log(SentryLevel.INFO, "Event sent to Sentry project: " + maskDsn(dsn));
                } else {
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(connection.getErrorStream()))) {
                        String errorResponse = reader.lines().reduce("", (a, b) -> a + b);
                    }
                }
                
            } catch (Exception e) {
                throw new IOException("Failed to send to Sentry", e);
            }
        }
        
        @Override
        public void flush(long timeoutMillis) {
            logger.log(SentryLevel.DEBUG, "Flushing HTTP transport for: " + maskDsn(dsn));
        }
        
        @Override
        public void close() throws IOException {
            logger.log(SentryLevel.DEBUG, "Closing HTTP transport for: " + maskDsn(dsn));
        }
        
        @Override
        public void close(boolean isRestarting) throws IOException {
            logger.log(SentryLevel.DEBUG, "Closing HTTP transport (restarting: " + isRestarting + ") for: " + maskDsn(dsn));
        }
        
        @Override
        public RateLimiter getRateLimiter() {
            return rateLimiter;
        }
        
        private String maskDsn(String dsn) {
            if (dsn == null) return "null";
            return dsn.replaceAll("://[^@]+@", "://***@");
        }
    }
    
    private String maskDsn(String dsn) {
        if (dsn == null) return "null";
        return dsn.replaceAll("://[^@]+@", "://***@");
    }
    
    private String getProjectName(String dsn) {
        for (SimpleRoutingConfig.ProjectBox box : projectBoxes) {
            if (box.dsn.equals(dsn)) {
                return box.name;
            }
        }
        return "Unknown Project";
    }
    
    @Override
    public void flush(long timeoutMillis) {
        logger.log(SentryLevel.DEBUG, "Flushing all cached transports with timeout: " + timeoutMillis);
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
        logger.log(SentryLevel.INFO, "Closing RoutingTransport (restarting: " + isRestarting + ") and all cached transports");
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