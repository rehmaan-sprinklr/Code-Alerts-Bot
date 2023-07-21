package com.rehmaan.groupbot.database;

public class IndexNames {
    private static String codeAlertsIndexName = "code_alerts_index";
    private static String priorityIndexName = "priority";
    private static String channelReadingStatusIndexName = "channel_reading_status_index";

    public static String getCodeAlertsIndexName() {
        return codeAlertsIndexName;
    }

    public static String getPriorityIndexNameIndexName() {
        return priorityIndexName;
    }

    public static String getChannelReadingStatusIndexName() {
        return channelReadingStatusIndexName;
    }

}
