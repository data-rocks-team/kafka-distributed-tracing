import brave.ScopedSpan;
import brave.Tracer;
import brave.Tracing;
import brave.kafka.clients.KafkaTracing;
import brave.sampler.Sampler;
import com.google.common.collect.Lists;
import com.twitter.hbc.ClientBuilder;
import com.twitter.hbc.core.Client;
import com.twitter.hbc.core.Constants;
import com.twitter.hbc.core.Hosts;
import com.twitter.hbc.core.HttpHosts;
import com.twitter.hbc.core.endpoint.StatusesFilterEndpoint;
import com.twitter.hbc.core.processor.StringDelimitedProcessor;
import com.twitter.hbc.httpclient.auth.Authentication;
import com.twitter.hbc.httpclient.auth.OAuth1;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.urlconnection.URLConnectionSender;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class TwitterProducer {
    private final String consumerKey = System.getenv("consumerKey");
    private final String consumerSecret = System.getenv("consumerSecret");
    private final String token = System.getenv("token");
    private final String secret = System.getenv("secret");
    private final Logger logger = LoggerFactory.getLogger(TwitterProducer.class.getName());

    List<String> terms = Lists.newArrayList( "trump", "boris");

    public TwitterProducer() {}

    public static void main(String[] args) {
        new TwitterProducer().run("twitter_test");
    }

    private void run(String topic) {
        BlockingQueue<String> msgQueue = new LinkedBlockingQueue<String>(100000);

        //Twitter client
        Client client = createTwitterClient(msgQueue);
        client.connect();
        //END

        KafkaProducer<String, String> producer = createKafkaProducer();

        //CONFIGURE TRACING
        final URLConnectionSender sender = URLConnectionSender.newBuilder().endpoint("http://127.0.0.1:9411/api/v2/spans").build();
        final AsyncReporter reporter = AsyncReporter.builder(sender).build();
        final Tracing tracing = Tracing.newBuilder().localServiceName("twitter-producer").sampler(Sampler.ALWAYS_SAMPLE).spanReporter(reporter).build();
        final KafkaTracing kafkaTracing = KafkaTracing.newBuilder(tracing).remoteServiceName("kafka").build();
        final Tracer tracer = Tracing.currentTracer();
        //END CONFIGURATION

        final Producer<String, String> tracedKafkaProducer = kafkaTracing.producer(producer);

        //Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(()-> {
            logger.info("stopping application...");
            logger.info("shutting down twitter client...");
            client.stop();
            logger.info("closing producer...");
            producer.close();
            logger.info("Done");
        }));

        while(!client.isDone()) {

            String msg = null;
            try {
                msg = msgQueue.poll(4, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                logger.info("Error in pooling tweet: " + e);
                client.stop();
            }

            if (msg!=null) {
                logger.info(msg);

                //Create record
                ProducerRecord<String, String> record = new ProducerRecord<>(topic, null, msg);

                //CREATE SPAN
                ScopedSpan span = tracer.startScopedSpan("twitter-to-kafka");
                span.tag("name", "sending-kafka-record");
                span.annotate("starting operation");

                span.annotate("sending message to kafka");
                tracedKafkaProducer.send(record);
                span.annotate("complete operation");
                span.finish();
            }
        }

        logger.info("End of application");

    }
    private KafkaProducer<String, String> createKafkaProducer() {

        //Producer config
        Properties properties = new Properties();
        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:9092");
        properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.setProperty(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        properties.setProperty(ProducerConfig.ACKS_CONFIG, "all");
        properties.setProperty(ProducerConfig.RETRIES_CONFIG, Integer.toString(Integer.MAX_VALUE));
        properties.setProperty(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5");

        //high throughput settings

        properties.setProperty(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        properties.setProperty(ProducerConfig.LINGER_MS_CONFIG, "20");
        properties.setProperty(ProducerConfig.BATCH_SIZE_CONFIG, Integer.toString(32*1024));

        return new KafkaProducer<>(properties);
    }

    public Client createTwitterClient(BlockingQueue<String> msgQueueFromRun){

        BlockingQueue<String> msgQueue = msgQueueFromRun;
        Hosts hosebirdHosts = new HttpHosts(Constants.STREAM_HOST);
        StatusesFilterEndpoint hosebirdEndpoint = new StatusesFilterEndpoint().trackTerms(terms);
        Authentication hosebirdAuth = new OAuth1(consumerKey, consumerSecret, token, secret);

        ClientBuilder builder = new ClientBuilder()
                .name("Test-Client-01")                              // optional: mainly for the logs
                .hosts(hosebirdHosts)
                .authentication(hosebirdAuth)
                .endpoint(hosebirdEndpoint)
                .processor(new StringDelimitedProcessor(msgQueue));

        Client hosebirdClient = builder.build();
        return hosebirdClient;
    }
}
