package com.example;

import io.sentry.Sentry;
import io.sentry.SentryOptions;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import jakarta.annotation.PostConstruct;

@SpringBootApplication
public class SentryTransportDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SentryTransportDemoApplication.class, args);
    }

    @PostConstruct
    public void initSentry() {
        Sentry.init(options -> {
            //transport will override the destination anyway
            options.setDsn("https://YOUR_BASE_DSN_KEY@o0.ingest.sentry.io/YOUR_BASE_PROJECT_ID");
            options.setDebug(true);
            options.setLogger(new ConsoleLogger());
            options.setTracesSampleRate(1.0);
            
            // Set our custom transport factory  
            options.setTransportFactory((sentryOptions, requestDetails) -> new RoutingTransport(sentryOptions));
            
            options.setAttachStacktrace(true);
            options.setBeforeSend((event, hint) -> {
                return event;
            });
        });
    }
}

