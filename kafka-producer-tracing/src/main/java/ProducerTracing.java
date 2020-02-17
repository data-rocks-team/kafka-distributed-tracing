import brave.ScopedSpan;
import brave.Tracer;
import brave.Tracing;
import brave.kafka.clients.KafkaTracing;
import brave.sampler.Sampler;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.urlconnection.URLConnectionSender;

import java.util.Properties;

public class ProducerTracing {
    private final Logger logger = LoggerFactory.getLogger(ProducerTracing.class.getName());

    public ProducerTracing() {}

    public static void main(String[] args) throws InterruptedException {
        new ProducerTracing().run("test_tracing");
    }

    private void run(String topic) throws InterruptedException {

        KafkaProducer<String, String> producer = createKafkaProducer();

        //CONFIGURE TRACING
        final URLConnectionSender sender = URLConnectionSender.newBuilder().endpoint("http://127.0.0.1:9411/api/v2/spans").build();
        final AsyncReporter reporter = AsyncReporter.builder(sender).build();
        final Tracing tracing = Tracing.newBuilder().localServiceName("Kafka_Producer").sampler(Sampler.ALWAYS_SAMPLE).spanReporter(reporter).build();
        final KafkaTracing kafkaTracing = KafkaTracing.newBuilder(tracing).remoteServiceName("kafka").build();
        final Tracer tracer = Tracing.currentTracer();
        //END CONFIGURATION

        final Producer tracedKafkaProducer = kafkaTracing.producer(producer);

        //Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(()-> {
            logger.info("stopping application...");
            logger.info("closing producer...");
            tracedKafkaProducer.close();
            logger.info("Done");
        }));

        //Create record
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, null, "some small test");

        //CREATE SPAN
        ScopedSpan span = tracer.startScopedSpan("produce-to-kafka");
        span.tag("name", "sending-kafka-record");

        span.annotate("starting operation");

        span.annotate("sending message to kafka");

        tracedKafkaProducer.send(record, (metadata, exception) -> {
            if (exception == null) {
                logger.info("Received new metadata: \n" +
                        "Topic: " + metadata.topic() + "\n" +
                        "Partition: " + metadata.partition() + "\n" +
                        "Offset: " + metadata.offset()
                );
            } else {
                logger.error("Error while producing: " + exception);
            }

        });

        span.annotate("complete operation");
        span.finish();
        reporter.flush(); // flush method which sends messages to zipkin

        logger.info("End of application");

    }

    private KafkaProducer<String, String> createKafkaProducer() {

        //Producer config
        Properties properties = new Properties();
        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:9092");
        properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        return new KafkaProducer<>(properties);
    }

}
