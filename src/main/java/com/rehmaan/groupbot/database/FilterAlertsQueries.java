package com.rehmaan.groupbot.database;

import org.apache.http.ParseException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpRetryException;
import java.util.ArrayList;
import java.util.List;

public class FilterAlertsQueries {

    private static List<JSONObject> processResponseFromElasticSearchforFilter(JSONObject response) {
        JSONArray buckets = response.getJSONObject("aggregations").getJSONObject("uniqueId").getJSONArray("buckets");
        List<JSONObject> result = new ArrayList<>();

        for(int i=0; i < buckets.length(); i++) {
            JSONObject bucket = buckets.getJSONObject(i);
            int count = bucket.getInt("doc_count");
            JSONObject topAlert = bucket.getJSONObject("topAlert")
                    .getJSONObject("hits").getJSONArray("hits")
                    .getJSONObject(0)
                    .getJSONObject("_source");

            String messageUrl = topAlert.getString("messageUrl");
            int priority = topAlert.getInt("priority");
            String _id = bucket.getJSONObject("topAlert")
                    .getJSONObject("hits").getJSONArray("hits")
                    .getJSONObject(0).getString("_id");

            JSONObject alert = new JSONObject();
            alert.put("count", count);
            alert.put("messageUrl", messageUrl);
            alert.put("priority", priority);
            alert.put("_id", _id);

            result.add(alert);
        }

        return result;
    }

    public static JSONObject filterAlerts(String field, String value, int days, String channelId) throws IOException, ParseException, HttpRetryException {
        String endpoint = "http://localhost:9200/" + ESClient.CODE_ALERTS_INDEX_NAME + "/_search";

        String query = "{\n" +
                "  \"size\": 0,\n" +
                "  \"query\": {\n" +
                "    \"bool\": {\n" +
                "      \"must\": [\n" +
                "        {\n" +
                "          \"match\": {\n" +
                "            \"" + field + "\": \"" + value +"\"\n" +
                "          }\n" +
                "        },\n" +
                "        {\n" +
                "          \"match\": {\n" +
                "            \"" + "channelId" + "\": \"" + channelId +"\"\n" +
                "          }\n" +
                "        },\n" +
                "        {\n" +
                "          \"range\": {\n" +
                "            \"createdTime\": {\n" +
                "              \"gte\": \"now-" + days + "d/d\",\n" +
                "              \"lt\": \"now+1d/d\"\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  },\n" +
                "  \"aggs\": {\n" +
                "    \"uniqueId\": {\n" +
                "      \"terms\": {\n" +
                "        \"field\": \"uniqueId\"\n" + ",\"size\":10000"+
                "      },\n" +
                "      \"aggs\": {\n" +
                "        \"topAlert\": {\n" +
                "          \"top_hits\": {\n" +
                "            \"size\": 1,\n" +
                "            \"sort\": [\n" +
                "              {\n" +
                "                \"createdTime\": {\n" +
                "                  \"order\": \"desc\"\n" +
                "                }\n" +
                "              }\n," +
                "              {\n" +
                "                \"priority\": {\n" +
                "                  \"order\": \"desc\"\n" +
                "                }\n" +
                "              }\n" +
                "            ],\n" +
                "            \"_source\": {\n" +
                "              \"includes\": [\n" +
                "                \"messageUrl\", \"priority\", \"_id\" \n" +
                "              ]\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";
        return ESClient.sendElasticsearchHttpRequest(query, endpoint);
    }

    public static JSONObject aggregateByField(String field, int days, String channelId) throws IOException, ParseException , HttpRetryException{
        String endpoint = "http://localhost:9200/" + ESClient.CODE_ALERTS_INDEX_NAME + "/_search";
        String query = "{\n" +
                "  \"size\": 0,\n" +
                "  \"query\": {\n" +
                "    \"bool\": {\n" +
                "      \"must\": [\n" +
                "        {\n" +
                "          \"range\": {\n" +
                "            \"createdTime\": {\n" +
                "              \"gte\": \"now-" + days + "d/d\",\n" +
                "              \"lt\": \"now+1d/d\"\n" +
                "            }\n" +
                "          }\n" +
                "        }, \n" +
                "        {\n" +
                "            \"match\" : {\n" +
                "                \"channelId\" : \"" + channelId + "\"\n" +
                "            }\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  },\n" +
                "  \"aggs\": {\n" +
                "    \"termsAgg\": {\n" +
                "      \"terms\": {\n" +
                "        \"field\": \"" + field + "\",\n" +
                "        \"size\": 10000\n" +
                "      },\n" +
                "      \"aggs\": {\n" +
                "        \"unique_ids_count\": {\n" +
                "          \"cardinality\": {\n" +
                "            \"field\": \"uniqueId\"\n" +
                "          }\n" +
                "        },\n" +
                "        \"topAlert\": {\n" +
                "          \"top_hits\": {\n" +
                "            \"size\": 1,\n" +
                "            \"sort\": [\n" +
                "              {\n" +
                "                \"createdTime\": {\n" +
                "                  \"order\": \"desc\"\n" +
                "                }\n" +
                "              },\n" +
                "              {\n" +
                "                \"priority\": {\n" +
                "                  \"order\": \"desc\"\n" +
                "                }\n" +
                "              }\n" +
                "            ],\n" +
                "            \"_source\": {\n" +
                "              \"includes\": [\n" +
                "                \"messageUrl\", \"priority\", \"_id\", \n" +
                "                \"" + field + "\"\n" +
                "              ]\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n";

        return ESClient.sendElasticsearchHttpRequest(query, endpoint);
    }


    public static List<JSONObject> getByEnvironment(String value, int days, String channelId) throws IOException, ParseException, HttpRetryException {
        JSONObject response = filterAlerts("environment", value, days, channelId);
        return processResponseFromElasticSearchforFilter(response);
    }

    public static List<JSONObject> getByPartnerId(String value, int days, String channelId) throws IOException, ParseException, HttpRetryException {
        JSONObject response = filterAlerts("partnerId", value, days, channelId);
        return processResponseFromElasticSearchforFilter(response);
    }

    public static List<JSONObject> getByStackTraceKeywords(String value, int days, String channelId) throws IOException, ParseException, HttpRetryException {
        JSONObject response = filterAlerts("stackTrace", value, days, channelId);
        return processResponseFromElasticSearchforFilter(response);
    }

    public static List<JSONObject> getByService(String value, int days, String channelId) throws IOException, ParseException, HttpRetryException {
        StringBuilder stringBuilder = new StringBuilder();
        value =value.toLowerCase();
        for(char c : value.toCharArray()) {
            if(c!= ' ') {
                stringBuilder.append(c);
            }
        }
        value = stringBuilder.toString();
        JSONObject response = filterAlerts("stackTraceForService", value, days, channelId);
        return processResponseFromElasticSearchforFilter(response);
    }

    public static List<JSONObject> unresolvedAlerts(int days, String channelId) throws IOException,ParseException, HttpRetryException {
        String query = "{\n" +
                "  \"size\": 0,\n" +
                "  \"query\": {\n" +
                "    \"bool\": {\n" +
                "      \"must\": [\n" +
                "        {\n" +
                "          \"range\": {\n" +
                "            \"createdTime\": {\"gte\": \"now-" + days + "d/d\", \"lt\": \"now+1d/d\"}\n" +
                "          }\n" +
                "        }, \n" +
                "\n" +
                "        {\n" +
                "            \"match\" : {\n" +
                "                \"channelId\" : \"" + channelId + "\"\n" +
                "            }\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  },\n" +
                "  \"aggs\": {\n" +
                "    \"uniqueId\": {\n" +
                "      \"terms\": {\n" +
                "        \"field\": \"uniqueId\"\n" +
                "      },\n" +
                "      \"aggs\": {\n" +
                "        \"topAlert\": {\n" +
                "          \"top_hits\": {\n" +
                "            \"size\": 1,\n" +
                "            \"sort\": [{ \"createdTime\": {\"order\": \"desc\"}}],\n" +
                "            \"_source\": {\n" +
                "              \"includes\": [\"messageUrl\", \"priority\", \"_id\"]\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";
        String endpoint= "http://localhost:9200/" + ESClient.CODE_ALERTS_INDEX_NAME + "/_search";

        JSONObject response= ESClient.sendElasticsearchHttpRequest(query, endpoint);
        return processResponseFromElasticSearchforFilter(response);
    }
    // ok done

    // OK done
    public static List<JSONObject> getUnresolvedAlertsSummaryByEnvironment(String channelId, int days) throws IOException, ParseException, HttpRetryException {
        JSONObject response = aggregateByField("environment", days, channelId);

        JSONArray buckets = response.getJSONObject("aggregations").getJSONObject("termsAgg").getJSONArray("buckets");

        List<JSONObject> result = new ArrayList<>();

        for(int i=0; i < buckets.length(); i++) {
            JSONObject bucket = buckets.getJSONObject(i);
            JSONObject alert = getAlertFromBucket(bucket, "environment");
            result.add(alert);
        }
        return result;
    }

    // OK done
    public static List<JSONObject> getUnresolvedAlertsSummaryByPartnerId(String channelId, int days) throws IOException, ParseException, HttpRetryException {
        JSONObject response = aggregateByField("partnerId", days, channelId);
        JSONArray buckets = response.getJSONObject("aggregations").getJSONObject("termsAgg").getJSONArray("buckets");

        List<JSONObject> result = new ArrayList<>();

        for(int i=0; i < buckets.length(); i++) {
            JSONObject bucket = buckets.getJSONObject(i);
            JSONObject alert = getAlertFromBucket(bucket, "partnerId");
            result.add(alert);
        }
        return result;
    }

    // OK DONE
    public static List<JSONObject> getUnresolvedAlertsSummaryByHostInfo(String channelId, int days) throws IOException, ParseException, HttpRetryException {
        JSONObject response = aggregateByField("hostInfo", days, channelId);
        JSONArray buckets = response.getJSONObject("aggregations").getJSONObject("termsAgg").getJSONArray("buckets");

        List<JSONObject> result = new ArrayList<>();

        for(int i=0; i < buckets.length(); i++) {
            JSONObject bucket = buckets.getJSONObject(i);
            JSONObject alert = getAlertFromBucket(bucket, "hostInfo");
            result.add(alert);
        }
        return result;
    }

    private static JSONObject getAlertFromBucket(JSONObject bucket, String field) {
        int count = bucket.getJSONObject("unique_ids_count").getInt("value");
        JSONObject topAlert = bucket.getJSONObject("topAlert")
                .getJSONObject("hits").getJSONArray("hits")
                .getJSONObject(0)
                .getJSONObject("_source");

        String messageUrl = topAlert.getString("messageUrl");
        int priority = topAlert.getInt("priority");
        String _id = bucket.getJSONObject("topAlert")
                .getJSONObject("hits").getJSONArray("hits")
                .getJSONObject(0).getString("_id");

        String valueOfField = topAlert.getString(field);

        JSONObject alert = new JSONObject();
        alert.put("count", count);
        alert.put("messageUrl", messageUrl);
        alert.put("priority", priority);
        alert.put("_id", _id);
        alert.put(field, valueOfField);

        return alert;
    }
}
