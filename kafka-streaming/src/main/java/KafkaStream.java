import brave.Tracing;
import brave.kafka.streams.KafkaStreamsTracing;
import brave.sampler.Sampler;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.urlconnection.URLConnectionSender;

import java.util.Properties;

public class KafkaStream {
    public static void main(String[] args) {

        //CONFIGURE TRACING
        final URLConnectionSender sender = URLConnectionSender.newBuilder().endpoint("http://127.0.0.1:9411/api/v2/spans").build();
        final AsyncReporter reporter = AsyncReporter.builder(sender).build();
        final Tracing tracing = Tracing.newBuilder().localServiceName("Kafka_Streaming").sampler(Sampler.ALWAYS_SAMPLE).spanReporter(reporter).build();
        final KafkaStreamsTracing kafkaStreamsTracing = KafkaStreamsTracing.create(tracing);
        //END CONFIGURATION

        Properties config = new Properties();
        config.put(StreamsConfig.APPLICATION_ID_CONFIG, "stream-application");
        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:9092");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        StreamsBuilder builder = new StreamsBuilder();

        KStream<String, String> data = builder.stream("test_tracing");
        KStream<String, String> streamData = data.mapValues(v -> v.toUpperCase());
        streamData.to("test_tracing_stream", Produced.with(Serdes.String(), Serdes.String()));

        KafkaStreams streams = kafkaStreamsTracing.kafkaStreams(builder.build(), config);
                //new KafkaStreams(builder.build(), config);

        streams.cleanUp();
        streams.start();

        // shutdown hook to correctly close the streams application
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
    }
}
