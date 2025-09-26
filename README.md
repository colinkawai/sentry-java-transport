# Sentry Transport Demo - Event Multiplexing

This project demonstrates custom transport implementation in the Sentry Java SDK that multiplexes events to different Sentry projects based on event content analysis.

## Overview

The `RoutingTransport` class implements the `ITransport` interface to:
- Analyze event content (tags, exceptions, messages) for routing decisions
- Create HTTP transports for each target DSN with caching
- Send events to appropriate Sentry projects via real HTTP requests
- Manage transport lifecycle and rate limiting

## Routing Configuration

### Current Implementation
Routing rules are defined in `SimpleRoutingConfig.java` using a simple array-based approach:

```java
public static final ProjectBox[] PROJECT_BOXES = {
    new ProjectBox(
        "Gateway Project",
        "https://gateway-key@o88872.ingest.us.sentry.io/gateway-project-id",
        new String[]{"gateway"},                    // tags
        new String[]{"502"},                        // status codes
        new String[]{"BadGatewayException"},        // exception types
        new String[]{"502 Bad Gateway"}             // message keywords
    ),
    // Additional project configurations...
};
```

### Production Recommendations
For production environments, consider:

1. **External Configuration**: Load routing rules from JSON/YAML files for runtime updates
2. **Environment-Specific Rules**: Different routing for dev/staging/production
3. **Rule Validation**: Schema validation for routing configuration
4. **Monitoring**: Metrics on routing decisions and transport performance
5. **Fallback Handling**: Robust error handling for transport failures

## Project Structure

- `RoutingTransport.java` - Custom transport implementation with multiplexing logic
- `SimpleRoutingConfig.java` - Project routing configuration using array-based approach
- `ErrorController.java` - REST endpoints that generate different types of errors
- `SentryTransportDemoApplication.java` - Spring Boot application with custom transport factory
- `ConsoleLogger.java` - Debug logger for transport routing

## API Endpoints

- `GET /api/health` - Health check
- `GET /api/gateway-error` - Generates gateway errors with "gateway" tags
- `GET /api/internal-error` - Generates internal errors with "internal" tags
- `GET /api/generic-error` - Generates generic errors for default routing
- `POST /api/custom-error` - Accepts custom error payloads with configurable tags

## Running the Demo

1. **Start the application:**
   ```bash
   mvn spring-boot:run
   ```

2. **Test different routing scenarios:**
   ```bash
   curl http://localhost:8081/api/gateway-error
   curl http://localhost:8081/api/internal-error
   curl http://localhost:8081/api/generic-error
   ```

3. **Monitor console output for routing decisions and HTTP responses**

## Configuration Management

### Current Approach
DSNs and routing rules are defined in `SimpleRoutingConfig.PROJECT_BOXES` array for simplicity and demonstration purposes.

### Production Approach
For production systems, implement:

1. **External Configuration Files**
   ```json
   {
     "projects": [
       {
         "name": "Gateway Project",
         "dsn": "https://key@sentry.io/project",
         "rules": {
           "tags": ["gateway"],
           "statusCodes": ["502"],
           "exceptionTypes": ["BadGatewayException"]
         }
       }
     ]
   }
   ```

2. **Environment-Specific Configuration**
   - Different routing rules for development, staging, production
   - Environment variable-driven DSN configuration
   - Runtime rule reloading capabilities

3. **Rule Management**
   - Schema validation for configuration files
   - Rule precedence and conflict resolution
   - Audit logging for configuration changes

## Transport Implementation

This implementation demonstrates proper use of Sentry's official transport extension points:
- Implements `ITransport` interface as designed by Sentry
- Uses `setTransportFactory()` for proper SDK integration
- Handles real event envelopes with content analysis
- Makes actual HTTP requests to Sentry API endpoints
- Manages transport lifecycle and rate limiting correctly

