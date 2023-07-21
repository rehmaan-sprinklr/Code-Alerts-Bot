package com.rehmaan.groupbot.database;

import org.apache.http.ParseException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpRetryException;
import java.util.List;


/**
 * A class that helps to handle owners of alerts queries
 *
 * @author mohammad rehmaan
 */
public class OwnersHandlingQueries {

    private static String codeAlertsIndexName = IndexNames.getCodeAlertsIndexName();

    /**
     * Assigns owners to an alert.
     *
     * @param ownerIds  The list of owner IDs to assign to the alert.
     * @param channelId The channel ID of the alert.
     * @param id        The ID of the alert.
     * @param isTeamsId Whether the ID is a Teams message ID.
     * @throws IOException        If an I/O error occurs.
     * @throws ParseException     If a parsing error occurs.
     * @throws HttpRetryException If an HTTP error occurs.
     */
    public static void assignOwners(List<String> ownerIds, String channelId, String id, boolean isTeamsId) throws IOException, ParseException, HttpRetryException {
        JSONObject alert = isTeamsId ? CommonQueries.getByMessageId(id, channelId) : CommonQueries.getByESId(id, channelId);
        if (alert == null) {
            return;
        }
        String documentId = alert.getString("uniqueId");
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("[");

        for (String owner : ownerIds) {
            stringBuilder.append("\"" + owner + "\",");
        }

        if (ownerIds.size() > 0) {
            stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        }
        stringBuilder.append("]");

        String stringOfOwnersArray = stringBuilder.toString();

        String query = "{\n" +
                "\"query\": {\n" +
                "    \"match\": {\n" +
                "      \"uniqueId\": \"" + documentId + "\"\n" +
                "    }\n" +
                "  }," +
                "  \"script\": {\n" +
                "    \"inline\": \"ctx._source.owners.addAll(params.value); ctx._source.owners = ctx._source.owners.stream().distinct().collect(Collectors.toList())\",\n" +
                "    \"lang\": \"painless\",\n" +
                "    \"params\": {\n" +
                "      \"value\":" + stringOfOwnersArray + "\n" +
                "    }\n" +
                "  }\n" +
                "}";
        String endpoint = ElasticSearchRestClient.endPoint + codeAlertsIndexName + "/_update_by_query/";
        ESClient.sendElasticsearchHttpRequest(query, endpoint);
    }


    /**
     * Removes owners from an alert.
     *
     * @param ownerIds  The list of owner IDs to remove from the alert.
     * @param id        The ID of the alert.
     * @param channelId The channel ID of the alert.
     * @param isTeamsId Whether the ID is a Teams message ID.
     * @throws IOException        If an I/O error occurs.
     * @throws ParseException     If a parsing error occurs.
     * @throws HttpRetryException If an HTTP error occurs.
     */
    public static void removeOwners(List<String> ownerIds, String id, String channelId, boolean isTeamsId) throws IOException, ParseException, HttpRetryException {
        JSONObject alert = isTeamsId ? CommonQueries.getByMessageId(id, channelId) : CommonQueries.getByESId(id, channelId);
        if (alert == null) {
            return;
        }

        String documentId = alert.getString("uniqueId");
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("[");

        for (String owner : ownerIds) {
            stringBuilder.append("\"" + owner + "\",");
        }

        if (ownerIds.size() > 0) {
            stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        }
        stringBuilder.append("]");

        String stringOfOwnersArray = stringBuilder.toString();

        String query = "{\n" +
                "\"query\": {\n" +
                "    \"match\": {\n" +
                "      \"uniqueId\": \"" + documentId + "\"\n" +
                "    }\n" +
                "  }," +
                "  \"script\": {\n" +
                "    \"inline\": \"ctx._source.owners.removeAll(params.value); ctx._source.owners = ctx._source.owners.stream().distinct().collect(Collectors.toList())\",\n" +
                "    \"lang\": \"painless\",\n" +
                "    \"params\": {\n" +
                "      \"value\":" + stringOfOwnersArray + "\n" +
                "    }\n" +
                "  }\n" +
                "}";
        String endpoint = ElasticSearchRestClient.endPoint + codeAlertsIndexName + "/_update_by_query/";
        ESClient.sendElasticsearchHttpRequest(query, endpoint);
    }

}
