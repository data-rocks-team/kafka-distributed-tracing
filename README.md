# Kafka Tracing via zipkin
Run `docker-compose up` to start zipkin - elasticsearch - kafka - zookeeper

Auto topic creation is set to true: run in order Producer, Consumer and Streaming to create topics and first trace.

Go to localhost:9411 to check the traced record in zipkin UI

Using the Brave(Java) instrumentation library for zipkin: https://github.com/openzipkin/brave

Zipkin tracing system saves data into either ElasticSearch or Cassandra.

### Zipkin Configuration

#### Producer
Configure tracing 

```java
//CONFIGURE TRACING
final URLConnectionSender sender = URLConnectionSender.newBuilder().endpoint("http://127.0.0.1:9411/api/v2/spans").build();
final AsyncReporter reporter = AsyncReporter.builder(sender).build();
final Tracing tracing = Tracing.newBuilder().localServiceName("simpleProducer_test").sampler(Sampler.ALWAYS_SAMPLE).spanReporter(reporter).build();
final KafkaTracing kafkaTracing = KafkaTracing.newBuilder(tracing).remoteServiceName("kafka").build();
final Tracer tracer = Tracing.currentTracer();
//END CONFIGURATION
```

Wrap kafka producer in kafka tracing:
`final Producer tracedKafkaProducer = kafkaTracing.producer(producer);`

Create spans:
- measurements are taken between the annotations
- in producer use the reporter flush to force messages to be sent to zipkin. If producer is too fast, its span will not have time to be sent to zipkin

```java
//Create record
ProducerRecord<String, String> record = new ProducerRecord<>("test_tracing", null, "Test");

//Create span
ScopedSpan span = tracer.startScopedSpan("produce-to-kafka");
span.tag("name", "sending-kafka-record");
span.annotate("starting operation");
span.annotate("sending message to kafka");

tracedKafkaProducer.send(record);

span.annotate("complete operation");
span.finish();
reporter.flush(); // flush method which sends messages to zipkin

logger.info("End of application");
```

#### Consumer

Same configuration
- only change it to localServiceName
```java
//CONFIGURE TRACING
final URLConnectionSender sender = URLConnectionSender.newBuilder().endpoint("http://127.0.0.1:9411/api/v2/spans").build();
final AsyncReporter reporter = AsyncReporter.builder(sender).build();
final Tracing tracing = Tracing.newBuilder().localServiceName("simpleConsumer_test").sampler(Sampler.ALWAYS_SAMPLE).spanReporter(reporter).build();
final KafkaTracing kafkaTracing = KafkaTracing.newBuilder(tracing).remoteServiceName("kafka").build();
final Tracer tracer = Tracing.currentTracer();
//END CONFIGURATION
```

Wrap consumer into kafkaTracing

`Consumer<String, String> tracingConsumer = kafkaTracing.consumer(consumer);`

Subscribe tracing consumer to topic"
`tracingConsumer.subscribe(Collections.singleton("test_tracing"));`


Read data and send spans to zipkin: nextSpan starts sending and span.finish ends it

```java
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
```
#### Streaming

Add the configuration:

```java
//CONFIGURE TRACING
final URLConnectionSender sender = URLConnectionSender.newBuilder().endpoint("http://127.0.0.1:9411/api/v2/spans").build();
final AsyncReporter reporter = AsyncReporter.builder(sender).build();
final Tracing tracing = Tracing.newBuilder().localServiceName("Kafka_Streaming").sampler(Sampler.ALWAYS_SAMPLE).spanReporter(reporter).build();
final KafkaStreamsTracing kafkaStreamsTracing = KafkaStreamsTracing.create(tracing);
//END CONFIGURATION
```
Wrap kafkaStream into kafkaStreamTracing

`KafkaStreams streams = kafkaStreamsTracing.kafkaStreams(builder.build(), config);`
