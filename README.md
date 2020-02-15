Kafka Cheat Sheet
-----------

### Create topic
`kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 3 --topic test`

### Add partition to topic
`$ kafka-topics.sh --alter --zookeeper localhost:2181 --topic test --partitions 3`

### List existing topics
`$ kafka-topics.sh --zookeeper localhost:2181 --list`
 
### Describe topic
`$ kafka-topics.sh --describe --zookeeper localhost:2181 --topic test`

### Purge a topic
`$ kafka-topics.sh --zookeeper localhost:2181 --alter --topic mytopic --config retention.ms=1000`

`$ kafka-topics.sh --zookeeper localhost:2181 --alter --topic test --delete-config retention.ms`
 
### Delete a topic
`$ kafka-topics.sh --zookeeper localhost:2181 --delete --topic test`

### Read from 2 offsets before end of topic
`$ kafka-run-class.sh kafka.tools.GetOffsetShell --broker-list localhost:9092 --topic test --time -2`

### Read from earliest offset
`$ kafka-run-class.sh kafka.tools.GetOffsetShell --broker-list localhost:9092 --topic test --time -1`

### Consume messages with the console consumer
`$ kafka-console-consumer.sh --new-consumer --bootstrap-server localhost:9092 --topic mytopic --from-beginning`

### Get the consumer offsets for a topic
`$ kafka-consumer-offset-checker.sh --zookeeper=localhost:2181 --topic=mytopic --group=my_consumer_group`

## Kafka Consumer Groups

### List the consumer groups known to Kafka

`$ kafka-consumer-groups.sh --new-consumer --bootstrap-server localhost:9092 --list` (new api)

### View the details of a consumer group 
`$ kafka-consumer-groups.sh --zookeeper localhost:2181 --describe --group <group name>`

## kafkacat

### Get list of topics

`$ kafkacat -L -b kafka`

### Getting the last five message of a topic
`$ kafkacat -C -b localhost:9092 -t mytopic -p 0 -o -5 -e`

### Starting consumer
`$ kafkacat -C -b kafka -t test`

### Starting producer
`$ kafkacat -P -b kafka -t test`

### Format message in consumer
`$ kafkacat -C -b kafka -t test -f 'Topic %t [%p] at offset %o: key %k: %s\n'`

### Transferring messages between topics and clusters

`$ kafkacat -C -b kafka -t test -e | kafkacat -P -b kafka -t test2`

### Transferring messages with file in the middle

`$ kafkacat -C -b kafka -t test -e > awesome-messages.txt`
`$ cat awesome-messages.txt | kafkacat -P -b kafka -t test2`

### Pipe kafkacat results to JSON format
`$ kafkacat -C -b kafka -t test-topic -f | jq`

## Zookeeper

### Starting the Zookeeper Shell
`$ zookeeper-server-start.sh config/zookeeper.properties`

### Starting broker
`$ kafka-server-start.sh config/server.properties`

## Producer config

### Replica acknowledgment

`acks=0` (Broker does not reply to producer)
`acks=1` (Leader response is requested = replication not guaranteed = producer retries if no ack received)
`acks=all` (Replicas + Leader to acknowledge) => Adds latency
 
`min.insync.replicas=2` (most common) must be used with acks=all => at least 2 brokers that are ISR including leader must respond

`retries` => default is max int for kafka >2.1
`retry.backoff.ms` = setting is by default 100 ms
`delivery.timeout.ms` = 120000 == 2 minutes (to prevent producer retrying till max int)

### Idempotent producer
`producerProperties.put("enable.idempotence",true)`

### Save producer: impact on throughput
enable.idempotence=true
min.insync.replicas=2 (min_)
acks=all
max.in.flight.requests.per.connection=5
retries=MAX_INT