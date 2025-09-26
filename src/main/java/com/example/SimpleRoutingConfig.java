package com.example;

import java.util.*;

public class SimpleRoutingConfig {
    // TODO: Replace these DSNs with your actual Sentry project DSNs
    public static final ProjectBox[] PROJECT_BOXES = {
        // Box 1: Gateway/502 Project
        new ProjectBox(
            "Gateway Project",
            "https://YOUR_GATEWAY_PROJECT_KEY@o0.ingest.sentry.io/YOUR_GATEWAY_PROJECT_ID",
            new String[]{"gateway"},                                  // only "gateway" tag, not generic ones
            new String[]{"502"},                                      // only 502 status
            new String[]{"BadGatewayException"},                     // only our specific exception
            new String[]{"502 Bad Gateway", "Upstream service"}     // specific message content
        ),
        
        // Box 2: Internal/500 Project
        new ProjectBox(
            "Internal Errors Project",
            "https://YOUR_INTERNAL_PROJECT_KEY@o0.ingest.sentry.io/YOUR_INTERNAL_PROJECT_ID",
            new String[]{"internal"},                                 // only "internal" tag
            new String[]{"500"},                                      // only 500 status
            new String[]{"InternalServerException"},                 // only our specific exception
            new String[]{"500 Internal Server Error", "Database connection"} // specific message content
        ),
        
        // Box 3: Default Project - Catches everything else
        new ProjectBox(
            "Default Project", 
            "https://YOUR_DEFAULT_PROJECT_KEY@o0.ingest.sentry.io/YOUR_DEFAULT_PROJECT_ID",
            new String[]{"generic", "default", "catch_all_never_matches"}, // specific tags only
            new String[]{"400", "404", "999"},                       // other status codes
            new String[]{"RuntimeException", "Exception"},           // generic exceptions (this will catch most)
            new String[]{"Generic application error"}               // specific to our generic endpoint
        )
    };
    
    public static class ProjectBox {
        public final String name;
        public final String dsn;
        public final Set<String> tags;
        public final Set<String> statusValues;
        public final Set<String> exceptionTypes;
        public final Set<String> messageKeywords;
        
        public ProjectBox(String name, String dsn, String[] tags, String[] statusValues, 
                         String[] exceptionTypes, String[] messageKeywords) {
            this.name = name;
            this.dsn = dsn;
            this.tags = Set.of(tags);
            this.statusValues = Set.of(statusValues);
            this.exceptionTypes = Set.of(exceptionTypes);
            this.messageKeywords = Set.of(messageKeywords);
        }
        
        public boolean matches(Map<String, String> eventTags, String exceptionType, String message) {
            // Check tags
            if (eventTags != null) {
                for (String tag : tags) {
                    if (eventTags.containsKey(tag)) return true;
                }
                
                String status = eventTags.get("status");
                if (status != null && statusValues.contains(status)) return true;
            }
            
            // Check exception type
            if (exceptionType != null) {
                String lowerException = exceptionType.toLowerCase();
                for (String pattern : exceptionTypes) {
                    if (lowerException.contains(pattern.toLowerCase())) return true;
                }
            }
            
            // Check message keywords
            if (message != null) {
                String lowerMessage = message.toLowerCase();
                for (String keyword : messageKeywords) {
                    if (lowerMessage.contains(keyword.toLowerCase())) return true;
                }
            }
            
            return false;
        }
    }
}
