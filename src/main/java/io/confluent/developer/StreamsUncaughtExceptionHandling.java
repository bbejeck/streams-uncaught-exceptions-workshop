package io.confluent.developer;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class StreamsUncaughtExceptionHandling {

    int counter = 0;

    public Properties buildStreamsProperties(Properties envProps) {
        Properties props = new Properties();

        props.put(StreamsConfig.APPLICATION_ID_CONFIG, envProps.getProperty("application.id"));
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, envProps.getProperty("bootstrap.servers"));
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.REPLICATION_FACTOR_CONFIG, 3);
        props.put(StreamsConfig.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
        props.put("sasl.mechanism", "PLAIN");
        props.put("sasl.jaas.config", envProps.getProperty("sasl.jaas.config"));
        props.put("basic.auth.credentials.source", "USER_INFO");
//        props.put("schema.registry.basic.auth.user.info", envProps.getProperty("schema.registry.basic.auth.user.info"));

        return props;
    }

    public Topology buildTopology(Properties envProps) {
        final StreamsBuilder builder = new StreamsBuilder();
        final String inputTopic = envProps.getProperty("input.topic.name");
        final String outputTopic = envProps.getProperty("output.topic.name");

        builder.stream(inputTopic, Consumed.with(Serdes.String(), Serdes.String()))
                .mapValues(value -> {
                    counter++;
                    if (counter == 2 || counter == 8 || counter == 15) {
                        throw new IllegalStateException("It works on my box!!!");
                    }
                    return value.toUpperCase();
                })
                .to(outputTopic, Produced.with(Serdes.String(), Serdes.String()));
        
        return builder.build();
    }


    public Properties loadEnvProperties(String fileName) throws IOException {
        Properties envProps = new Properties();
        FileInputStream input = new FileInputStream(fileName);
        envProps.load(input);
        input.close();

        return envProps;
    }

    public static void main(String[] args) throws Exception {

        if (args.length < 1) {
            throw new IllegalArgumentException("This program takes one argument: the path to an environment configuration file.");
        }

        StreamsUncaughtExceptionHandling tw = new StreamsUncaughtExceptionHandling();
        Properties envProps = tw.loadEnvProperties(args[0]);
        Properties streamProps = tw.buildStreamsProperties(envProps);

        // Change this to StreamsConfig.EXACTLY_ONCE to eliminate duplicates
        streamProps.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.AT_LEAST_ONCE);
        Topology topology = tw.buildTopology(envProps);



        final int maxFailures = Integer.parseInt(envProps.getProperty("max.failures"));
        final long maxTimeInterval = Long.parseLong(envProps.getProperty("max.time.millis"));
        final KafkaStreams streams = new KafkaStreams(topology, streamProps);
        final MaxFailuresUncaughtExceptionHandler exceptionHandler = new MaxFailuresUncaughtExceptionHandler(maxFailures, maxTimeInterval);
        streams.setUncaughtExceptionHandler(exceptionHandler);

        // Attach shutdown handler to catch Control-C.
        Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
            @Override
            public void run() {
                streams.close();
            }
        });

        try {
            streams.cleanUp();
            streams.start();
        } catch (Throwable e) {
            System.exit(1);
        }
    }


}
