# Sentry Transport Demo

This project demonstrates custom transport implementation in the Sentry Java SDK that routes events to different Sentry projects based on event content.

## Overview

The `RoutingTransport` class implements the `ITransport` interface to:
- Analyze event content (tags, exceptions, messages) for routing decisions
- Create HTTP transports for each target DSN with caching
- Send events to appropriate Sentry projects via real HTTP requests

## Routing Configuration

### JSON Configuration (Production-Ready)
Routing rules are defined in `src/main/resources/sentry-routing-config.json`:

```json
{
  "projects": [
    {
      "name": "Gateway Project",
      "dsn": "https://YOUR_KEY@o0.ingest.sentry.io/YOUR_PROJECT_ID",
      "rules": {
        "tags": ["gateway"],
        "statusCodes": ["502"],
        "exceptionTypes": ["BadGatewayException"],
        "messageKeywords": ["502 Bad Gateway", "Upstream service"]
      }
    }
  ]
}
```

### Fallback Configuration
If the JSON file is not found or fails to load, the system uses hardcoded default routes defined in `RoutingConfiguration.getDefaultRoutes()`.

### Configuration Features
- **JSON-Based**: Easy to modify without recompilation
- **Environment-Specific**: Different JSON files for dev/staging/production
- **Hardcoded Fallback**: Ensures system always has routing configuration
- **Hot-Reload Ready**: Can be extended to reload configuration at runtime

## Project Structure

- `RoutingTransport.java` - Custom transport implementation with multiplexing logic
- `RoutingConfiguration.java` - Loads routes from JSON with hardcoded fallback
- `ProjectRoute.java` - Routing destination with matching criteria
- `ErrorController.java` - REST endpoints that generate different telemetry types
- `SentryTransportDemoApplication.java` - Spring Boot application with custom transport factory
- `ConsoleLogger.java` - Debug logger for transport routing
- `sentry-routing-config.json` - JSON configuration for routing rules

## API Endpoints

- `GET /api/health` - Health check
- `GET /api/gateway-error` - Generates error events with "gateway" tags
- `GET /api/internal-error` - Generates error events with "internal" tags
- `GET /api/generic-error` - Generates generic error events
- `GET /api/transaction-test` - Generates transaction telemetry for routing test
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

## Transport Implementation

This implementation demonstrates use of Sentry's official transport extension points:
- Implements `ITransport` interface as designed by Sentry
- Uses `setTransportFactory()` for SDK integration
- Handles real event envelopes with content analysis
- Makes actual HTTP requests to Sentry API endpoints
- Manages transport lifecycle and rate limiting correctly

