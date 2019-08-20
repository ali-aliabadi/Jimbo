package ir.jimbo.web.graph.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import ir.jimbo.commons.model.ElasticPage;
import ir.jimbo.web.graph.config.ElasticSearchConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ElasticSearchService {
    private static final Logger LOGGER = LogManager.getLogger(ElasticSearchService.class);
    private static final int SCROLL_SIZE = 10;
    private static final long SCROLL_TIMEOUT = 2;
    private ElasticSearchConfiguration configuration;
    private TransportClient client;
    private String esScrollID = null;

    public ElasticSearchService(ElasticSearchConfiguration configuration) {
        this.configuration = configuration;
        client = configuration.getClient();
    }

    public List<ElasticPage> getSourcePages() {
        SearchResponse scrollResp;
        if (esScrollID == null)
            scrollResp = client.prepareSearch(configuration.getSourceName())
                    .setScroll(TimeValue.timeValueMinutes(SCROLL_TIMEOUT))
                    .setQuery(QueryBuilders.matchAllQuery())
                    .setSize(SCROLL_SIZE)
                    .get();
        else
            scrollResp = client.prepareSearchScroll(esScrollID)
                    .setScroll(TimeValue.timeValueMinutes(SCROLL_TIMEOUT))
                    .execute()
                    .actionGet();
        esScrollID = scrollResp.getScrollId();
        List<ElasticPage> pages = new ArrayList<>();
        SearchHit[] searchHits = scrollResp.getHits().getHits();
        ObjectMapper reader = new ObjectMapper();
        for (SearchHit hit : searchHits) {
            try {
                pages.add(reader.readValue(hit.getSourceAsString(), ElasticPage.class));
            } catch (IOException e) {
                LOGGER.error("Source page parse exception", e);
            }
        }
        return pages;
    }

    public TransportClient getClient() {
        return client;
    }
}