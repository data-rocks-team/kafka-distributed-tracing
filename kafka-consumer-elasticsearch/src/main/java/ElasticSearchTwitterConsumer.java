import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.kafka.clients.KafkaTracing;
import brave.sampler.Sampler;
import com.google.gson.JsonParser;
import org.apache.http.HttpHost;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.urlconnection.URLConnectionSender;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

public class ElasticSearchTwitterConsumer {
    final static Logger logger = LoggerFactory.getLogger(ElasticSearchTwitterConsumer.class);

    public static RestHighLevelClient createClient(){
        RestClientBuilder builder = RestClient
                .builder(new HttpHost("localhost", 9200, "http"));

        return new RestHighLevelClient(builder);
    }

    public static KafkaConsumer<String, String> createConsumer(String topic){

        String bootstrapServer = "127.0.0.1:9092";
        String groupId = "kafka_to_elasticSearch";

        Properties properties = new Properties();
        properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServer);
        properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "100");

        KafkaConsumer<String, String> consumer = new KafkaConsumer(properties);

//        consumer.subscribe(Collections.singletonList(topic));

        return consumer;
    }

    private static String extractIdFromTweet(String tweetJson) {
        return JsonParser.parseString(tweetJson).getAsJsonObject().get("id_str").getAsString();
    }

    public static void main(String[] args) throws IOException {
        final String topic = "twitter_test";

        //CONFIGURE TRACING
        final URLConnectionSender sender = URLConnectionSender.newBuilder().endpoint("http://127.0.0.1:9411/api/v2/spans").build();
        final AsyncReporter reporter = AsyncReporter.builder(sender).build();
        final Tracing tracing = Tracing.newBuilder().localServiceName("twitter-consumer").sampler(Sampler.ALWAYS_SAMPLE).spanReporter(reporter).build();
        final KafkaTracing kafkaTracing = KafkaTracing.newBuilder(tracing).remoteServiceName("kafka").build();
        final Tracer tracer = Tracing.currentTracer();
        //END CONFIGURATION

        RestHighLevelClient client = createClient();

        //Run the index request using a client

        KafkaConsumer<String, String> consumer = createConsumer(topic);
        Consumer<String, String> tracingConsumer = kafkaTracing.consumer(consumer);

        tracingConsumer.subscribe(Collections.singletonList(topic));

        while(true){
            ConsumerRecords<String,String> records = tracingConsumer.poll(Duration.ofMillis(100));

            int recordCounts = records.count();
            logger.info("Received " + recordCounts + " records");

            BulkRequest bulkRequest = new BulkRequest();

            for (ConsumerRecord record: records) {
                try {

                    Span span = kafkaTracing.nextSpan(record).name("consume-tweet").start();
                    span.annotate("start consuming");

                    //*****
                    String id = extractIdFromTweet(record.value().toString());

                    //Index Request
                    IndexRequest indexRequest =  new IndexRequest("twitter", "tweets", id)
                            .source(record.value().toString(), XContentType.JSON);

                    bulkRequest.add(indexRequest);
                    //******

                    span.annotate("consume finished");
                    span.finish();

                } catch (NullPointerException e){
                    logger.warn("skipping bad data: " + record.value());
                }

                //Run the index request using a client
//                IndexResponse indexResponse = client.index(indexRequest, RequestOptions.DEFAULT);
//                logger.info(indexResponse.getId());
            }

            if (recordCounts > 0) {
                BulkResponse bulkItemResponses = client.bulk(bulkRequest, RequestOptions.DEFAULT);
                logger.info("Committing offsets...");
                consumer.commitSync();
                logger.info("Offsets have been committed");
            }
        }
//        client.close();

    }


}
