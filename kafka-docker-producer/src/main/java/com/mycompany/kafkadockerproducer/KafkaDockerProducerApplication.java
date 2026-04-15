package com.mycompany.kafkadockerproducer;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Slf4j
@SpringBootApplication
public class KafkaDockerProducerApplication implements CommandLineRunner {

    @Autowired(required = false)
    private OpenTelemetry openTelemetry;

    private KafkaProducer<String, String> kafkaProducer;
    private long interval;
    private boolean stopped = false;

    @EventListener(ApplicationStartedEvent.class)
    public void installOtelAppender() {
        if (openTelemetry != null) {
            OpenTelemetryAppender.install(openTelemetry);
            log.info("OpenTelemetryAppender installed");
        } else {
            log.warn("OpenTelemetry bean not found - logs will not be exported");
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(KafkaDockerProducerApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        String bootstrapServer = "";
        long initialDelay = 0;
        List<CryptoType> listTopics = new ArrayList<>();

        if (args.length < 4) {
            log.error("java -jar target/kafka-docker-producer <bootstrap-server> <initial_delay> <interval> <topic-1> -- <topic-n>");
            log.error("Example: java -jar target/kafka-docker-producer localhost:29092 1000 3000 BTC,LTC,ETH");
            System.exit(1);
        }

        try {
            bootstrapServer = args[0];
            initialDelay = Long.parseLong(args[1]);
            interval = Long.parseLong(args[2]);
            String[] topics = args[3].split(",");
            for (String t : topics) {
                listTopics.add(CryptoType.getCryptoType(t));
            }
        } catch (Exception e) {
            log.error("Exception {}", e.getMessage(), e);
            System.exit(1);
        }

        Thread.sleep(initialDelay);

        Properties properties = new Properties();
        properties.put("bootstrap.servers", bootstrapServer);
        properties.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        properties.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        properties.put("acks", "1");

        kafkaProducer = new KafkaProducer<>(properties);

        try {
            while (!stopped) {
                for (CryptoType crypto : listTopics) {
                    if (crypto != CryptoType.UNKNOWN) {
                        sendData(crypto.getInitials(), crypto.getUrl());
                    }
                }
            }
        } finally {
            kafkaProducer.close();
        }
    }

    private void sendData(String topic, String url) throws InterruptedException, IOException {
        String data = getRequest(url);
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, topic, data);
        kafkaProducer.send(record, (recordMetadata, e) -> {
            if (e != null) {
                log.error("Exception {}", e.getMessage(), e);
            } else {
                log.info("[{}] JSON data sent to topic {}.", LocalTime.now().toString(), topic);
            }
        });
        Thread.sleep(interval);
    }

    private String getRequest(String url) throws IOException {
        StringBuilder response = new StringBuilder();

        URL obj = URI.create(url).toURL();
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();

        con.setRequestMethod("GET");
        con.setRequestProperty("User-Agent", "Mozilla/5.0");

        try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
        }

        return response.toString();
    }

    public void stop() {
        this.stopped = true;
    }
}

