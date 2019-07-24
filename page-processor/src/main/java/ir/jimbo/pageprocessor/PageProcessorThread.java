package ir.jimbo.pageprocessor;

import ir.jimbo.commons.model.Page;
import ir.jimbo.pageprocessor.config.KafkaConfiguration;
import ir.jimbo.pageprocessor.manager.ElasticSearchService;
import ir.jimbo.pageprocessor.manager.HTableManager;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.Producer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class PageProcessorThread extends Thread {
    private static final Logger LOGGER = LogManager.getLogger(PageProcessorThread.class);
    private final HTableManager hTableManager;
    private final String hQualifier;
    private Consumer<Long, Page> pageConsumer;
    private Producer<Long, String> linkProducer;
    private KafkaConfiguration kafkaConfiguration = KafkaConfiguration.getInstance();
    private ElasticSearchService esService;
    private Long pollDuration;

    public PageProcessorThread(String hTableName, String hColumnFamily, String hQualifier) throws IOException {
        hTableManager = new HTableManager(hTableName, hColumnFamily);
        pageConsumer = kafkaConfiguration.getPageConsumer();
        linkProducer = kafkaConfiguration.getLinkProducer();
        this.hQualifier = hQualifier;
        pollDuration = Long.parseLong(kafkaConfiguration.getPropertyValue("consumer.poll.duration"));
    }

    @Override
    public void run() {
        while (!interrupted()) {
            ConsumerRecords<Long, Page> records = pageConsumer.poll(Duration.ofMillis(pollDuration));
            List<Page> pages = new ArrayList<>();
            for (ConsumerRecord<Long, Page> record : records) {
                pages.add(record.value());
                System.err.println(record.value());
            }
            boolean isAdded = esService.insertPages(pages);
            pageConsumer.commitSync();
        }
    }
}
