package com.example;

import java.util.*;

/**
 * Represents a routing destination for Sentry events.
 * Contains the target DSN and matching criteria.
 */
public class ProjectRoute {
    public final String name;
    public final String dsn;
    public final Set<String> tags;
    public final Set<String> statusValues;
    public final Set<String> exceptionTypes;
    public final Set<String> messageKeywords;
    public final Set<String> environments;
    public final Set<String> levels;
    
    public ProjectRoute(String name, String dsn, String[] tags, String[] statusValues, 
                       String[] exceptionTypes, String[] messageKeywords) {
        this(name, dsn, tags, statusValues, exceptionTypes, messageKeywords, new String[]{}, new String[]{});
    }
    
    public ProjectRoute(String name, String dsn, String[] tags, String[] statusValues, 
                       String[] exceptionTypes, String[] messageKeywords, 
                       String[] environments, String[] levels) {
        this.name = name;
        this.dsn = dsn;
        this.tags = Set.of(tags);
        this.statusValues = Set.of(statusValues);
        this.exceptionTypes = Set.of(exceptionTypes);
        this.messageKeywords = Set.of(messageKeywords);
        this.environments = environments.length > 0 ? Set.of(environments) : Collections.emptySet();
        this.levels = levels.length > 0 ? Set.of(levels) : Collections.emptySet();
    }
    
    public boolean matches(Map<String, String> eventTags, String exceptionType, String message, 
                          String environment, String level) {
        if (eventTags != null) {
            for (String tag : tags) {
                if (eventTags.containsKey(tag)) return true;
            }
            
            String status = eventTags.get("status");
            if (status != null && statusValues.contains(status)) return true;
        }
        
        if (exceptionType != null) {
            String lowerException = exceptionType.toLowerCase();
            for (String pattern : exceptionTypes) {
                if (lowerException.contains(pattern.toLowerCase())) return true;
            }
        }
        
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            for (String keyword : messageKeywords) {
                if (lowerMessage.contains(keyword.toLowerCase())) return true;
            }
        }
        
        if (!environments.isEmpty() && environment != null) {
            if (environments.contains(environment.toLowerCase())) return true;
        }
        
        if (!levels.isEmpty() && level != null) {
            if (levels.contains(level.toLowerCase())) return true;
        }
        
        return false;
    }
    
    public boolean matches(Map<String, String> eventTags, String exceptionType, String message) {
        return matches(eventTags, exceptionType, message, null, null);
    }
}

