package TweetConsumerProducer;

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
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    List<String> terms = Lists.newArrayList("java");

    public TwitterProducer() {}

    public static void main(String[] args) {
        new TwitterProducer().run();
    }

    private void run() {
        BlockingQueue<String> msgQueue = new LinkedBlockingQueue<String>(100000);

        //Twitter client
        Client client = createTwitterClient(msgQueue);
        client.connect();

        KafkaProducer<String, String> producer = createKafkaProducer();

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
                ProducerRecord<String, String> record = new ProducerRecord<>("twitter_test", null, msg);

                //Publish to kafka topic
                producer.send(record, (metadata, exception) -> {
                    if (exception != null) {
                        logger.error("Something bad happened", exception);
                    }
                });
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

        return new KafkaProducer<String, String>(properties);
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
