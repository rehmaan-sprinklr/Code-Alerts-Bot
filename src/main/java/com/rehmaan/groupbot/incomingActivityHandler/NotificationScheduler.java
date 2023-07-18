package com.rehmaan.groupbot.incomingActivityHandler;

import com.microsoft.bot.builder.BotCallbackHandler;
import com.microsoft.bot.builder.MessageFactory;
import com.microsoft.bot.builder.TurnContext;
import com.microsoft.bot.integration.BotFrameworkHttpAdapter;
import com.microsoft.bot.schema.*;
import com.rehmaan.groupbot.adaptiveCard.AdaptiveCard;
import com.rehmaan.groupbot.database.CommonQueries;
import com.rehmaan.groupbot.database.ESClient;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;

@Component
public class NotificationScheduler {
    @Autowired
    ESClient esClient;

    @Autowired
    BotFrameworkHttpAdapter adapter;
    private int lengthOfEsId = 20;

    public String getId(String text) throws Exception {
        if(text.contains("thread.tacv2/")) {
            int left = text.indexOf("thread.tacv2/") + "thread.tacv2/".length();
            int right = text.indexOf("?");
            String id = text.substring(left, right);
            return id;
        }

        else if(text.contains("esId:")) {
            int left = text.indexOf("esId:") + "esId:".length();
            int right = left + lengthOfEsId;
            String id = text.substring(left, right);
            return id;
        }
        throw new Exception("invalid input given by user");
    }
    public CompletableFuture<Void> sendNotification(TurnContext turnContext, String idUserInput, String channelId) {
        String id= null;
        try {
            id = getId(idUserInput);
        }
        catch(Exception ex) {
            return turnContext.sendActivity(MessageFactory.text("invalid user input")).thenApply(resourceResponse -> null);
        }


        StringBuilder stringBuilder = new StringBuilder();
        List<Mention> mentions = new ArrayList<>();

        try{
            JSONObject alert = null;
            if(idUserInput.contains("esId:")) {
                alert = CommonQueries.getByESId(id, channelId);
            }
            else {
                alert = CommonQueries.getByMessageId(id, channelId);
            }

            if(alert == null) {
                return CompletableFuture.completedFuture((Void) null);
            }
            JSONArray ownersJsonArray = alert.getJSONArray("owners");
            String messageUrl = alert.getString("messageUrl");
            List<String> owners = new ArrayList<>();
            for(Object owner :  ownersJsonArray) {
                owners.add(owner.toString());
            }
           if(owners.size() == 0) {
               return CompletableFuture.completedFuture((Void) null);
           }
           for(String owner : owners) {
               String [] arr = owner.split("#");
               String userId = arr[0];
               String userName = arr[1];
               Mention mention= new Mention();
               ChannelAccount channelAccount = new ChannelAccount(userId, userName);
               mention.setMentioned(channelAccount);
               mention.setText(
                       "<at>" + URLEncoder.encode(userName) + "</at>"
               );
               mentions.add(mention);
           }
           stringBuilder.append("Hello everyone! tagged people please look into the following alert \n");
           stringBuilder.append("\n" + "<a href=\""+ messageUrl + "\">Alert link</a>\n");
           for(Mention mention : mentions) {
               stringBuilder.append(mention.getText() + " ");
           }

           Activity replyActivity = MessageFactory.text(stringBuilder.toString());
           replyActivity.setMentions(mentions);
           replyActivity.setTextFormat(TextFormatTypes.XML);
           return turnContext.sendActivity(replyActivity).thenApply(resourceResponse -> null);
        }
        catch(Exception ex) {
            ex.printStackTrace();
        }
        return CompletableFuture.completedFuture((Void) null);
    }

    public CompletableFuture<Void> scheduleNotification(TurnContext turnContext, String messageUrl, String channelId, int durationInMinutes, int count, String appId) {
        int inMilliSeconds = durationInMinutes*60*1000;
        for(int i=1; i <= count; i++) {
            Timer timer= new Timer();
            TimerTask timerTask = new TimerTask() {
                @Override
                public void run() {
                    ConversationReference conversationReference = turnContext.getActivity().getConversationReference();
                    BotCallbackHandler botCallbackHandler = new BotCallbackHandler() {
                        @Override
                        public CompletableFuture<Void> invoke(TurnContext turnContext) {
                            return sendNotification(turnContext, messageUrl, channelId);
                        }
                    };
                    adapter.continueConversation(appId, conversationReference, botCallbackHandler);
                }
            };
            timer.schedule(timerTask, inMilliSeconds*i);
        }

        return turnContext.sendActivity(MessageFactory.text("Owners of this alert will be Notified after the scheduled time"))
                .thenApply(resourceResponse -> null);
    }
    public CompletableFuture<Void> sendNotificationInputForm(TurnContext turnContext) {
        String adaptiveCardJson= "{\n" +
                " \"type\": \"AdaptiveCard\",\n" +
                " \"body\": [\n" +
                " {\n" +
                " \"type\": \"TextBlock\",\n" +
                " \"text\": \"Notification scheduler form\",\n" +
                " \"weight\": \"Bolder\",\n" +
                " \"size\": \"Medium\"\n" +
                " },\n" +
                " {\n" +
                " \"type\": \"Input.Text\",\n" +
                " \"id\": \"messageUrl\",\n" +
                " \"placeholder\": \"MESSAGE_URL\"\n" +
                " },\n" +
                " {\n" +
                " \"type\": \"Input.Text\",\n" +
                " \"id\": \"count\",\n" +
                " \"placeholder\": \"COUNT_OF_NOTIFICATION\"\n" +
                " },\n" +
                " {\n" +
                " \"type\": \"Input.Text\",\n" +
                " \"id\": \"time\",\n" +
                " \"placeholder\": \"TIME_IN_MINUTES\"\n" +
                " }],"+
                " \"actions\": [\n" +
                " {\n" +
                " \"type\": \"Action.Submit\",\n" +
                " \"title\": \"Send\"\n" +
                " }\n" +
                " ],\n" +
                " \"$schema\": \"http://adaptivecards.io/schemas/adaptive-card.json\",\n" +
                " \"version\": \"1.5\"\n" +
                "}";
        Activity reply = AdaptiveCard.createForm(adaptiveCardJson);
        return turnContext.sendActivity(reply).thenApply(resourceResponse -> null);
    }

    public Activity sendAfterSubmitCard() {
        String cardText = "CodeAlertBot 🤖 : Notification scheduled Success ✅✅";
        String adaptiveCardJson = "{\n" +
                "  \"type\": \"AdaptiveCard\",\n" +
                "  \"body\": [\n" +
                "    {\n" +
                "      \"type\": \"TextBlock\",\n" +
                "      \"size\": \"medium\",\n" +
                "      \"weight\": \"bolder\",\n" +
                "      \"text\":\"" + cardText +  "\",\n" +
                "      \"style\": \"heading\",\n" +
                "      \"wrap\": true\n" +
                "    }" +
                "   ]," +
                "  \"$schema\": \"http://adaptivecards.io/schemas/adaptive-card.json\",\n" +
                "  \"version\": \"1.5\"\n" +
                "}";
        return AdaptiveCard.createForm(adaptiveCardJson);
    }
}
