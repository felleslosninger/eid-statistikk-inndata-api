package no.difi.statistics;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortOrder;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;

public class Statistics {

    private Client elasticSearchClient;
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public Statistics(Client elasticSearchClient) {
        this.elasticSearchClient = elasticSearchClient;
    }

    public List<TimeSeriesPoint> minutes(String seriesName, ZonedDateTime from, ZonedDateTime to) {
        return timeSeries(seriesName, "minutes", from, to);
    }

    public List<TimeSeriesPoint> hours(String seriesName, ZonedDateTime from, ZonedDateTime to) {
        return timeSeries(seriesName, "hours", from, to);
    }

    private List<TimeSeriesPoint> timeSeries(String seriesName, String type, ZonedDateTime from, ZonedDateTime to) {
        SearchResponse response = elasticSearchClient.prepareSearch(seriesName)
                .setTypes(type)
                .addField("time").addField("value")
                .setQuery(rangeQuery("time").from(dateTimeFormatter.format(from)).to(dateTimeFormatter.format(to)))
                .addSort("time", SortOrder.ASC)
                .setSize(10_000) // 10 000 is maximum
                .execute().actionGet();
        List<TimeSeriesPoint> series = new ArrayList<>();
        for (SearchHit hit : response.getHits()) {
            series.add(point(hit));
        }
        return series;
    }

    private TimeSeriesPoint point(SearchHit hit) {
        return new TimeSeriesPoint(time(hit), value(hit));
    }

    private ZonedDateTime time(SearchHit hit) {
        return ZonedDateTime.parse(hit.field("time").value(), dateTimeFormatter);
    }

    private int value(SearchHit hit) {
        return hit.field("value").value();
    }

}
