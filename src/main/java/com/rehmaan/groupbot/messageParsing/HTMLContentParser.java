package com.rehmaan.groupbot.messageParsing;

import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.HashMap;

public class HTMLContentParser {

    public static String getStackTrace(String str) {
        if(!str.contains("Stacktrace")) {
            return  "";
        }
        int l = str.indexOf("<code>");
        int r = str.indexOf("</code>");
        l = l +"<code>".length();
        return str.substring(l, r);
    }

    public static HashMap<String, String> convertHTMLTableToJSON(String str) {
        int startIndex = str.indexOf("<table");
        int endIndex = str.indexOf("</table>");

        // this won't be used once we read only messages sent by web hooks
        if(startIndex ==-1 || endIndex == -1) return null;
        endIndex += "</table>".length();
        HashMap<String, String> result = new HashMap<>();
        Document doc = Jsoup.parse(str);
        Element table = doc.selectFirst("table");
        for(Element row : table.select("tr")) {
            Elements tds = row.select("td");
            String key = tds.get(0).text();
            String value = tds.get(1).text();
            result.put(key, value);
        }
        return result;
    }
}
