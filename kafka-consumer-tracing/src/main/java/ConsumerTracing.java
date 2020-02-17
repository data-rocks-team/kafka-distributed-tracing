import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.kafka.clients.KafkaTracing;
import brave.sampler.Sampler;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.urlconnection.URLConnectionSender;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

public class ConsumerTracing {
    public static void main(String[] args) {
        final Logger logger = LoggerFactory.getLogger(ConsumerTracing.class);

        Properties properties = new Properties();
        properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:9092");
        properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "test_group_application");
        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        KafkaConsumer consumer = new KafkaConsumer(properties);

        //CONFIGURE TRACING
        final URLConnectionSender sender = URLConnectionSender.newBuilder().endpoint("http://127.0.0.1:9411/api/v2/spans").build();
        final AsyncReporter reporter = AsyncReporter.builder(sender).build();
        final Tracing tracing = Tracing.newBuilder().localServiceName("simpleConsumer_test").sampler(Sampler.ALWAYS_SAMPLE).spanReporter(reporter).build();
        final KafkaTracing kafkaTracing = KafkaTracing.newBuilder(tracing).remoteServiceName("kafka").build();
        final Tracer tracer = Tracing.currentTracer();
        //END CONFIGURATION

        Consumer<String, String> tracingConsumer = kafkaTracing.consumer(consumer);

        tracingConsumer.subscribe(Collections.singleton("test_tracing"));

        while(true){
            ConsumerRecords<String,String> records = consumer.poll(Duration.ofMillis(100));

            for (ConsumerRecord record: records){
                Span span = kafkaTracing.nextSpan(record).name("kafka-to-consumer").start();
                span.annotate("Start consuming");

                logger.info("key: " + record.key() + "value: " + record.value());

                span.annotate("Consume finished");
                span.finish();
            }
        }
    }
}
