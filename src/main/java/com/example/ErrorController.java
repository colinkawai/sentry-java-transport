package com.example;

import io.sentry.Sentry;
import io.sentry.ITransaction;
import io.sentry.SpanStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ErrorController {

    @GetMapping("/gateway-error")
    public ResponseEntity<Map<String, String>> gatewayError() {
        try {
            Sentry.configureScope(scope -> {
                scope.setTag("status", "502");
                scope.setTag("gateway", "true");
                scope.setTag("error_type", "gateway");
            });

            throw new BadGatewayException("Upstream service returned 502 Bad Gateway");
            
        } catch (Exception e) {
            Sentry.captureException(e);
            
            Map<String, String> response = new HashMap<>();
            response.put("error", "Bad Gateway");
            response.put("message", "Gateway error routed to appropriate project");
            
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
        }
    }

    @GetMapping("/internal-error")
    public ResponseEntity<Map<String, String>> internalError() {
        try {
            Sentry.configureScope(scope -> {
                scope.setTag("status", "500");
                scope.setTag("internal", "true");
                scope.setTag("error_type", "internal");
            });

            throw new InternalServerException("Database connection failed with 500 Internal Server Error");
            
        } catch (Exception e) {
            Sentry.captureException(e);
            
            Map<String, String> response = new HashMap<>();
            response.put("error", "Internal Server Error");
            response.put("message", "Internal error routed to appropriate project");
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/generic-error")
    public ResponseEntity<Map<String, String>> genericError() {
        try {
            Sentry.configureScope(scope -> {
                scope.setTag("error_type", "generic");
                scope.setTag("status", "400");
            });

            throw new RuntimeException("Generic application error occurred");
            
        } catch (Exception e) {
            Sentry.captureException(e);
            
            Map<String, String> response = new HashMap<>();
            response.put("error", "Generic Error");
            response.put("message", "Generic error routed to default project");
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @PostMapping("/custom-error")
    public ResponseEntity<Map<String, String>> customError(@RequestBody Map<String, String> request) {
        try {
            String errorType = request.getOrDefault("type", "generic");
            String message = request.getOrDefault("message", "Custom error occurred");
            
            Sentry.configureScope(scope -> {
                request.forEach(scope::setTag);
            });

            Exception exception;
            switch (errorType.toLowerCase()) {
                case "gateway":
                    exception = new BadGatewayException("Custom gateway error: " + message);
                    break;
                case "internal":
                    exception = new InternalServerException("Custom internal error: " + message);
                    break;
                default:
                    exception = new RuntimeException("Custom error: " + message);
            }
            
            throw exception;
            
        } catch (Exception e) {
            Sentry.captureException(e);
            
            Map<String, String> response = new HashMap<>();
            response.put("error", "Custom Error");
            response.put("message", "Custom error with tags: " + request);
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @GetMapping("/transaction-test")
    public ResponseEntity<Map<String, String>> transactionTest() {
        ITransaction transaction = Sentry.startTransaction("test-transaction", "http.server");
        
        try {
            Sentry.configureScope(scope -> {
                scope.setTag("gateway", "true");
                scope.setTag("transaction_type", "api_call");
            });
            
            Thread.sleep(100);
            transaction.setStatus(SpanStatus.OK);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Transaction sent for routing test");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            transaction.setStatus(SpanStatus.INTERNAL_ERROR);
            transaction.setThrowable(e);
            throw new RuntimeException(e);
        } finally {
            transaction.finish();
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "healthy");
        response.put("message", "Sentry Transport Demo is running");
        response.put("endpoints", "/gateway-error, /internal-error, /generic-error, /custom-error, /transaction-test");
        
        return ResponseEntity.ok(response);
    }

    public static class BadGatewayException extends RuntimeException {
        public BadGatewayException(String message) {
            super(message);
        }
    }

    public static class InternalServerException extends RuntimeException {
        public InternalServerException(String message) {
            super(message);
        }
    }
}

