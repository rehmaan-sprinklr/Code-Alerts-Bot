package com.rehmaan.groupbot.database;

import org.apache.http.ParseException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.TopHits;
import org.elasticsearch.search.sort.SortOrder;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpRetryException;
import java.util.ArrayList;
import java.util.List;


/**
 * A class that helps to interact with the Elasticsearch database to filter alerts.
 *
 * @author mohammad rehmaan
 */
public class FilterAlertsQueries {

    private static String codeAlertsIndexName = IndexNames.getCodeAlertsIndexName();


    /**
     * makes a query to elastic search to get alerts on the basis of value of a field
     *
     * @param field     field on which filtering needs to occur example environment
     * @param value     value of field example prod
     * @param days      number of last days till which we want the results
     * @param channelId channelId of teams
     * @return response sent by elastic search after it ran the query. response is returned in form of JSONObject
     * @throws IOException        thrown if call to elastic search database was not successful
     * @throws ParseException
     * @throws HttpRetryException
     */

    public static List<JSONObject> filterAlerts(String field, String value, int days, String channelId) throws IOException, ParseException, HttpRetryException {
        RestHighLevelClient client = ElasticSearchRestClient.createClient();

        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        boolQueryBuilder.must(QueryBuilders.rangeQuery("createdTime").gte("now-" + days + "d/d")
                .lt("now+1d/d"));
        boolQueryBuilder.must(QueryBuilders.matchQuery("channelId", channelId));
        boolQueryBuilder.must(QueryBuilders.matchQuery(field, value));

        TermsAggregationBuilder aggregation = AggregationBuilders.terms("uniqueId")
                .field("uniqueId")
                .subAggregation(
                        AggregationBuilders.topHits("topAlert")
                                .size(1)
                                .sort("createdTime", SortOrder.DESC)
                                .fetchSource(new String[]{"messageUrl", "priority", "_id", field}, null)
                );

        SearchRequest searchRequest = new SearchRequest(codeAlertsIndexName);
        searchRequest.source().query(boolQueryBuilder).aggregation(aggregation).size(0);

        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

        if (searchResponse.status().getStatus() != 200) {
            client.close();
            throw new HttpRetryException("Error cannot filter Alerts", searchResponse.status().getStatus());
        }

        Terms uniqueIdTerms = searchResponse.getAggregations().get("uniqueId");

        List<JSONObject> finalResult = new ArrayList<>();

        for (Terms.Bucket bucket : uniqueIdTerms.getBuckets()) {
            TopHits topHits = bucket.getAggregations().get("topAlert");
            long count = bucket.getDocCount(); // this is doc count
            SearchHit[] searchHits = topHits.getHits().getHits();
            for (SearchHit hit : searchHits) {
                String id = hit.getId();
                String messageUrl = hit.getSourceAsMap().get("messageUrl").toString();
                int priority = Integer.parseInt(hit.getSourceAsMap().get("priority").toString());

                JSONObject alert = new JSONObject();
                alert.put("count", count);
                alert.put("messageUrl", messageUrl);
                alert.put("_id", id);
                alert.put("priority", priority);
                alert.put(field, value);
                finalResult.add(alert);
            }
        }

        client.close();
        return finalResult;
    }

    /**
     * Aggregates alerts by the specified field and returns a JSON object with the results.
     *
     * @param field     The field to aggregate by.
     * @param days      The number of days to aggregate alerts for.
     * @param channelId The channel ID to filter alerts for.
     * @return A JSON object with the aggregation results.
     * @throws IOException        If an I/O error occurs.
     * @throws ParseException     If a parsing error occurs.
     * @throws HttpRetryException If an HTTP error occurs.
     */
    public static List<JSONObject> aggregateByField(String field, int days, String channelId) throws IOException, ParseException, HttpRetryException {
        RestHighLevelClient client = ElasticSearchRestClient.createClient();

        RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("createdTime")
                .gte("now-" + days + "d/d")
                .lt("now+1d/d");

        MatchQueryBuilder matchQuery = QueryBuilders.matchQuery("channelId", channelId);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .must(rangeQuery)
                .must(matchQuery);

        TermsAggregationBuilder aggregation = AggregationBuilders.terms("termsAgg")
                .field(field)
                .size(10000)
                .subAggregation(
                        AggregationBuilders.cardinality("unique_ids_count")
                                .field("uniqueId")
                )
                .subAggregation(
                        AggregationBuilders.topHits("topAlert")
                                .size(1)
                                .sort("createdTime", SortOrder.DESC)
                                .sort("priority", SortOrder.DESC)
                                .fetchSource(new String[]{"messageUrl", "priority", "_id", field}, null)
                );

        SearchRequest searchRequest = new SearchRequest(codeAlertsIndexName);
        searchRequest.source().query(boolQuery).aggregation(aggregation).size(0);

        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        if (searchResponse.status().getStatus() != 200) {
            client.close();
            throw new HttpRetryException("Error cannot filter Alerts", searchResponse.status().getStatus());
        }

        JSONObject responseJson = new JSONObject(searchResponse.toString());
        JSONArray buckets = responseJson.getJSONObject("aggregations").getJSONObject("sterms#termsAgg").getJSONArray("buckets");
        List<JSONObject> finalResult = new ArrayList<>();

        for (int i = 0; i < buckets.length(); i++) {
            JSONObject bucket = buckets.getJSONObject(i);
            JSONObject source = bucket.getJSONObject("top_hits#topAlert")
                    .getJSONObject("hits")
                    .getJSONArray("hits")
                    .getJSONObject(0).getJSONObject("_source");

            String id = bucket.getJSONObject("top_hits#topAlert")
                    .getJSONObject("hits")
                    .getJSONArray("hits")
                    .getJSONObject(0).getString("_id");
            String messageUrl = source.getString("messageUrl");
            int priority = source.getInt("priority");
            int count = bucket.getJSONObject("cardinality#unique_ids_count").getInt("value");
            String value = source.getString(field);

            JSONObject alert = new JSONObject();
            alert.put("messageUrl", messageUrl);
            alert.put("priority", priority);
            alert.put("_id", id);
            alert.put("count", count);
            alert.put(field, value);

            finalResult.add(alert);
        }

        client.close();
        return finalResult;
    }


    /**
     * Gets a list of alerts for the specified environment, number of days, and channel ID.
     *
     * @param value     The environment value to filter alerts for.
     * @param days      The number of days to get alerts for.
     * @param channelId The channel ID to filter alerts for.
     * @return A list of JSON objects with the alerts.
     * @throws IOException        If an I/O error occurs.
     * @throws ParseException     If a parsing error occurs.
     * @throws HttpRetryException If an HTTP error occurs.
     */

    public static List<JSONObject> getByEnvironment(String value, int days, String channelId) throws IOException, ParseException, HttpRetryException {
        List<JSONObject> result = filterAlerts("environment", value, days, channelId);
        return result;
    }


    /**
     * Gets a list of alerts for the specified partner ID, number of days, and channel ID.
     *
     * @param value     The partner ID value to filter alerts for.
     * @param days      The number of days to get alerts for.
     * @param channelId The channel ID to filter alerts for.
     * @return A list of JSON objects with the alerts.
     * @throws IOException        If an I/O error occurs.
     * @throws ParseException     If a parsing error occurs.
     * @throws HttpRetryException If an HTTP error occurs.
     */
    public static List<JSONObject> getByPartnerId(String value, int days, String channelId) throws IOException, ParseException, HttpRetryException {
        return filterAlerts("partnerId", value, days, channelId);
    }


    /**
     * Gets a list of alerts for the specified stack trace keyword, number of days, and channel ID.
     *
     * @param value     The stack trace keyword value to filter alerts for.
     * @param days      The number of days to get alerts for.
     * @param channelId The channel ID to filter alerts for.
     * @return A list of JSON objects with the alerts.
     * @throws IOException        If an I/O error occurs.
     * @throws ParseException     If a parsing error occurs.
     * @throws HttpRetryException If an HTTP error occurs.
     */
    public static List<JSONObject> getByStackTraceKeywords(String value, int days, String channelId) throws IOException, ParseException, HttpRetryException {
        List<JSONObject> result = filterAlerts("stackTrace", value, days, channelId);
        return result;
    }


    /**
     * Gets a list of alerts for the specified service, number of days, and channel ID.
     *
     * @param value     The service value to filter alerts for.
     * @param days      The number of days to get alerts for.
     * @param channelId The channel ID to filter alerts for.
     * @return A list of JSON objects with the alerts.
     * @throws IOException        If an I/O error occurs.
     * @throws ParseException     If a parsing error occurs.
     * @throws HttpRetryException If an HTTP error occurs.
     */
    public static List<JSONObject> getByService(String value, int days, String channelId) throws IOException, ParseException, HttpRetryException {
        StringBuilder stringBuilder = new StringBuilder();
        value = value.toLowerCase();
        for (char c : value.toCharArray()) {
            if (c != ' ') {
                stringBuilder.append(c);
            }
        }
        value = stringBuilder.toString();
        return filterAlerts("stackTraceForService", value, days, channelId);
    }


    /**
     * Gets a list of unresolved alerts for the specified number of days and channel ID.
     *
     * @param days      The number of days to get alerts for.
     * @param channelId The channel ID to filter alerts for.
     * @return A list of JSON objects with the alerts.
     * @throws IOException        If an I/O error occurs.
     * @throws ParseException     If a parsing error occurs.
     * @throws HttpRetryException If an HTTP error occurs.
     */
    public static List<JSONObject> unresolvedAlerts(int days, String channelId) throws IOException, ParseException, HttpRetryException {
        RestHighLevelClient client = ElasticSearchRestClient.createClient();

        RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("createdTime")
                .gte("now-" + days + "d/d")
                .lt("now+1d/d");

        MatchQueryBuilder matchQuery = QueryBuilders.matchQuery("channelId", channelId);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .must(rangeQuery)
                .must(matchQuery);

        TermsAggregationBuilder aggregation = AggregationBuilders.terms("uniqueId")
                .field("uniqueId")
                .subAggregation(
                        AggregationBuilders.topHits("topAlert")
                                .size(1)
                                .sort("createdTime", SortOrder.DESC)
                                .fetchSource(new String[]{"messageUrl", "priority", "_id"}, null)
                );

        SearchRequest searchRequest = new SearchRequest(codeAlertsIndexName);
        searchRequest.source().query(boolQuery).aggregation(aggregation).size(0);

        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

        if (searchResponse.status().getStatus() != 200) {
            client.close();
            throw new HttpRetryException("Error cannot filter Alerts", searchResponse.status().getStatus());
        }

        Terms uniqueIdTerms = searchResponse.getAggregations().get("uniqueId");

        List<JSONObject> finalResult = new ArrayList<>();

        for (Terms.Bucket bucket : uniqueIdTerms.getBuckets()) {
            TopHits topHits = bucket.getAggregations().get("topAlert");
            long count = bucket.getDocCount(); // this is doc count
            SearchHit[] searchHits = topHits.getHits().getHits();
            for (SearchHit hit : searchHits) {
                String id = hit.getId();
                String messageUrl = hit.getSourceAsMap().get("messageUrl").toString();
                int priority = Integer.parseInt(hit.getSourceAsMap().get("priority").toString());

                JSONObject alert = new JSONObject();
                alert.put("count", count);
                alert.put("messageUrl", messageUrl);
                alert.put("_id", id);
                alert.put("priority", priority);
                finalResult.add(alert);
            }
        }
        client.close();
        return finalResult;
    }

    /**
     * Gets a list of unresolved alerts summaries by environment for the specified channel ID and number of days.
     *
     * @param channelId The channel ID to filter alerts for.
     * @param days      The number of days to get alerts for.
     * @return A list of JSON objects with the alerts summaries.
     * @throws IOException        If an I/O error occurs.
     * @throws ParseException     If a parsing error occurs.
     * @throws HttpRetryException If an HTTP error occurs.
     */
    public static List<JSONObject> getUnresolvedAlertsSummaryByEnvironment(String channelId, int days) throws IOException, ParseException, HttpRetryException {
        return aggregateByField("environment", days, channelId);
    }


    /**
     * Gets a list of unresolved alerts summaries by partner ID for the specified channel ID and number of days.
     *
     * @param channelId The channel ID to filter alerts for.
     * @param days      The number of days to get alerts for.
     * @return A list of JSON objects with the alerts summaries.
     * @throws IOException        If an I/O error occurs.
     * @throws ParseException     If a parsing error occurs.
     * @throws HttpRetryException If an HTTP error occurs.
     */
    public static List<JSONObject> getUnresolvedAlertsSummaryByPartnerId(String channelId, int days) throws IOException, ParseException, HttpRetryException {
        return aggregateByField("partnerId", days, channelId);
    }


    /**
     * Gets a list of unresolved alerts summaries by host info for the specified channel ID and number of days.
     *
     * @param channelId The channel ID to filter alerts for.
     * @param days      The number of days to get alerts for.
     * @return A list of JSON objects with the alerts summaries.
     * @throws IOException        If an I/O error occurs.
     * @throws ParseException     If a parsing error occurs.
     * @throws HttpRetryException If an HTTP error occurs.
     */
    public static List<JSONObject> getUnresolvedAlertsSummaryByHostInfo(String channelId, int days) throws IOException, ParseException, HttpRetryException {
        return aggregateByField("hostInfo", days, channelId);
    }

}
