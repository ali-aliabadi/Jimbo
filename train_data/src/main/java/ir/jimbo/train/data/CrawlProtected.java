package ir.jimbo.train.data;

import ir.jimbo.commons.exceptions.NoDomainFoundException;
import ir.jimbo.train.data.config.RedisConfiguration;
import ir.jimbo.train.data.service.CacheService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * class to crawl on special conditions.
 *  There are 3 main condition that will be checked. anchor contain special keyWord, metas contain special keyWords,
 *      and text content contains special keyWords at least n times where n must be given.
 *      In case of any one of above conditions is true we assume that this url is proper.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CrawlProtected extends Thread {

    private final Logger logger = LogManager.getLogger(this.getClass());

    private String seedUrl;
    private String anchor;
    /**
     *  depth of crawl. number of layer that crawler goes in in the url and fetch page.
     *      so if depth of crawl is zero only current url will be checked and crawler wont go to current page links.
     */
    private int crawlDepth;
    /**
     *  Words that site content must contain
     */
    private Set<String> anchorKeyWords;
    private int politenessTime;
    /**
     *  keyWords in siteContent map to minimum number of repeats in it.
     *  if a site hold one maps conditions in the set we assume that this site holds contentKeyWords condition.
     */
    private Set<Map<String, Integer>> contentKeyWords;
    /**
     *  word that meta tags must contain. there are multiple tags that may lead to site content topic, like
     *      description meta tag and topic meta tag and sub topic meta tag and keyWords meta tag etc.
     *      that's why we search on all of meta tags.
     */
    private Set<String> metaContain;
    /**
     *  Regex pattern to extract domain from URL. Please refer to RFC 3986 - Appendix B for more information
     */
    private final Pattern domainPattern = Pattern.compile("^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))?");

    @Override
    public void run() {
        try {
            CacheService cacheService = new CacheService(new RedisConfiguration(), politenessTime);
            if (anchorKeyWords != null && !anchorKeyWords.isEmpty())
                if (anchorKeyWords.containsAll(Collections.singleton(anchor.split(" ")))) {
                    addUrl();
                } else {
                    checkContent();
                }
            if (crawlDepth > 0) {
                createNewUrlSeeds();
            }
        } catch (IOException e) {
            logger.error("exception in creating cache service...", e);
        }
    }

    private void createNewUrlSeeds() {

    }

    private void addUrl() {

    }

    public String getDomain(String url) {
        final Matcher matcher = domainPattern.matcher(url);
        String result = null;
        if (matcher.matches())
            result = matcher.group(4);
        if (result == null) {
            throw new NoDomainFoundException();
        }

        if (result.startsWith("www.")) {
            result = result.substring(4);
        }
        if (result.isEmpty()) {
            throw new NoDomainFoundException();
        }
        return result;
    }

    /**
     * @return True if uri end with ".html" or ".htm" or ".asp" or ".php" or the uri do not have any extension.
     */
    public boolean isValidUri(String link) {
        try {
            while (link.endsWith("/")) {
                link = link.substring(0, link.length() - 1);
            }
            if (link.endsWith(".html") || link.endsWith(".htm") || link.endsWith(".php") || link.endsWith(".asp")
                    || !link.substring(link.lastIndexOf('/') + 1).contains(".")) {
                return true;
            }
        } catch (IndexOutOfBoundsException e) {
            logger.info("invalid uri : {}", link);
            return false;
        }
        return false;
    }

    public Document fetchUrl() {
        logger.info("start parsing...");
        Document document;
        try {
            Connection connect = Jsoup.connect(seedUrl);
            connect.timeout(2000);
            document = connect.get();
        } catch (Exception e) { //
            logger.error("exception in connection to url. empty page instance will return", e);
            return null;
        }
        return document;
    }

    /**
     * Check site content and metas to check whether this site meet the given conditions or not
     * @return true if the content and metas contains given conditions
     */
    public boolean checkContent() {
        Document pageDocument = fetchUrl();
        if (checkMetasKeyWords(pageDocument)) {
            if (checkContentKeyWords(pageDocument)) {
                return true;
            } else {
                logger.info("page with link {}, passed due to not containing minimum" +
                        " number of keyWords in content", seedUrl);
            }
        } else {
            logger.info("page with link {}, passed due to not containing meta keyWords", seedUrl);
        }
        return false;
    }

    public boolean checkContentKeyWords(Document pageDocument) {
        if (contentKeyWords == null || contentKeyWords.isEmpty())
            return false;
        String documentText = pageDocument.text();
        for (Map<String, Integer> map : contentKeyWords) {
            for (Map.Entry<String, Integer> entry : map.entrySet()){
                if (documentText.length() - documentText.replaceAll(entry.getKey(), "").length() >= entry.getValue()) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean checkMetasKeyWords(Document pageDocument) {
        if (metaContain == null || metaContain.isEmpty())
            return false;
        for (Element meta : pageDocument.getElementsByTag("meta")) {
            String text = meta.text();
            for (String s : metaContain) {
                if (text.contains(s))
                    return true;
            }
        }
        return false;
    }
}