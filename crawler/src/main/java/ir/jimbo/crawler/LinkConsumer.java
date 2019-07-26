package ir.jimbo.crawler;

import ir.jimbo.crawler.config.KafkaConfiguration;
import ir.jimbo.crawler.service.CacheService;
import ir.jimbo.crawler.exceptions.NoDomainFoundException;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LinkConsumer extends Thread {

    private Logger logger = LogManager.getLogger(this.getClass());
    private long pollDuration;
    private KafkaConfiguration kafkaConfiguration;
    private CacheService cacheService;
    AtomicBoolean repeat;
    private CountDownLatch countDownLatch;
    private Pattern domainPattern = Pattern.compile("^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))?");
    // Regex pattern to extract domain from URL
    // Please refer to RFC 3986 - Appendix B for more information

    LinkConsumer(KafkaConfiguration kafkaConfiguration, CacheService cacheService, CountDownLatch consumerLatch) {
        pollDuration = kafkaConfiguration.getPollDuration();
        this.kafkaConfiguration = kafkaConfiguration;
        this.cacheService = cacheService;
        repeat = new AtomicBoolean(true);
        countDownLatch = consumerLatch;
    }

    @Override
    public void run() {
        Consumer<Long, String> consumer = kafkaConfiguration.getConsumer();
        String uri;
        Producer<Long, String> producer = kafkaConfiguration.getLinkProducer();
        logger.info("consumer thread started");
        while (repeat.get()) {
            ConsumerRecords<Long, String> consumerRecords = consumer.poll(Duration.ofMillis(pollDuration));
            for (ConsumerRecord<Long, String> record : consumerRecords) {
                uri = record.value();
//                logger.info("uri readed from kafka : " + uri);
                try {
                    if (politenessChecker(getDomain(uri))) {
                        App.linkQueue.put(uri);
                        cacheService.addDomain(getDomain(uri));
                        logger.info("uri \"" + uri + "\" added to queue");
                    } else {
                        logger.info("it was not polite crawling this uri : " + uri);
                        ProducerRecord<Long, String> producerRecord = new ProducerRecord<>(
                                kafkaConfiguration.getLinkTopicName(), uri);
                        producer.send(producerRecord);
                    }
                } catch (NoDomainFoundException e) {
                    logger.error("bad uri. cant take domain", e);
                } catch (Exception e) {
                    logger.error("error in putting uri to queue (interrupted exception) uri : " + uri);
                    ProducerRecord<Long, String> producerRecord = new ProducerRecord<>(
                            kafkaConfiguration.getLinkTopicName(), uri);
                    producer.send(producerRecord);
                }
            }
            try {
                consumer.commitSync();
            } catch (Exception e) {
                logger.info("unable to commit.##################################################################");
            }
        }
        countDownLatch.countDown();
        try {
            producer.close();
        } catch (Exception e) {
            logger.info("error in closing producer");
        }
        try {
            consumer.close();
        } catch (Exception e) {
            logger.info("error in closing consumer");
        }
    }

    private boolean politenessChecker(String uri) {
        return !cacheService.isDomainExist(uri);
    }

    private String getDomain(String url) {
        final Matcher matcher = domainPattern.matcher(url);
        if (matcher.matches())
            return matcher.group(4);
        throw new NoDomainFoundException();
    }

    void close() {
        repeat.set(false);
    }
}
