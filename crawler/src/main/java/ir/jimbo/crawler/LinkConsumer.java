package ir.jimbo.crawler;

import ir.jimbo.crawler.config.KafkaConfiguration;
import ir.jimbo.crawler.exceptions.NoDomainFoundException;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LinkConsumer implements Runnable{

    private Logger logger = LogManager.getLogger(this.getClass());
    private long pollDuration;
    private KafkaConfiguration kafkaConfiguration;
    private CacheService cacheService;
    private Pattern domainPattern = Pattern.compile("^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))?");

    public LinkConsumer(KafkaConfiguration kafkaConfiguration, CacheService cacheService) {
        pollDuration = Long.parseLong(kafkaConfiguration.getProperty("poll.duration"));
        this.kafkaConfiguration = kafkaConfiguration;
        this.cacheService = cacheService;
    }

    @Override
    public void run() {
        boolean repeat = true;
        Consumer<Long, String> consumer = kafkaConfiguration.getConsumer();
        String uri;
        Producer<Long, String> producer = kafkaConfiguration.getLinkProducer();
        while (repeat) {
            ConsumerRecords<Long, String> consumerRecords = consumer.poll(Duration.ofMillis(pollDuration));
            logger.info("get link from kafka numbers taken : " + consumerRecords.count());
            for (ConsumerRecord<Long, String> record : consumerRecords) {
                uri = record.value();
                logger.debug("the link readed from kafka : " + uri);
                // for logging we can use methods provide by ConsumerRecord class
                try {
                    if (politenessChecker(getDomain(uri))) {
                        App.urlToParseQueue.put(uri);
                    } else {
                        ProducerRecord<Long, String> producerRecord = new ProducerRecord<>(
                                kafkaConfiguration.getProperty("links.topic.name"), uri);
                        producer.send(producerRecord);
                    }
                } catch (NoDomainFoundException e) {
                    logger.error("bad uri. cant take domain", e);
                } catch (InterruptedException e) {
                    logger.error("error in putting uri to queue", e);
                }
            }
        }
    }

    private boolean politenessChecker(String uri) {
        return ! cacheService.isDomainExist(uri);
    }

    private String getDomain(String url) throws NoDomainFoundException {
        final Matcher matcher = domainPattern.matcher(url);
        if (matcher.matches())
            return matcher.group(4);
        throw new NoDomainFoundException();
    }
}
