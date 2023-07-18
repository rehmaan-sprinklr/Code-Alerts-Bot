package com.rehmaan.groupbot;

import com.microsoft.bot.builder.MessageFactory;

import java.io.IOException;
import com.microsoft.bot.builder.TurnContext;
import com.microsoft.bot.builder.teams.TeamsActivityHandler;
import com.microsoft.bot.integration.Configuration;
import com.microsoft.bot.schema.*;
import com.rehmaan.groupbot.adaptiveCard.GetInput;
import com.rehmaan.groupbot.authController.AuthController;
import com.rehmaan.groupbot.database.ESClient;
import com.rehmaan.groupbot.incomingActivityHandler.*;
import com.rehmaan.groupbot.messageParsing.AlertParser;
import com.rehmaan.groupbot.readAlerts.GetMessages;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;


public class Bot extends TeamsActivityHandler {

    @Autowired
    NotificationScheduler notificationScheduler;

    @Autowired
    FilterAlertsActivityHandler filterAlertsActivityHandler;

    @Autowired
    OwnersAssignActivityHandler ownersAssignActivityHandler;

    @Autowired
    MarkAsResolvedHandler markAsResolvedHandler;

    @Autowired
    UnresolvedAlertsSinceNdays unresolvedAlertsSinceNdays;

    @Autowired
    SummaryByFieldsActivityHandler summaryByFieldsActivityHandler;

    @Autowired
    UpdatePriorityActivityHandler updatePriorityHandler;

    private String appId;
    private String appPassword;
    public Bot(Configuration configuration) {
        appId= "967c973b-c486-4d38-968a-3d964800821b";
        appPassword = "V4B8Q~R2zD6E~qs7oSCMVvWpK5lX~dkNx4nrWchl";
    }

    private static CardAction createButton(String title, String text) {
        CardAction action = new CardAction();
        action.setTitle(title);
        action.setText(text);
        action.setType(ActionTypes.MESSAGE_BACK);
        return action;
    }


    private Activity cardForFilterAlerts() {

        CardAction filterAlertsAction = createButton("Filter Alerts", "filter_alerts");
        CardAction summaryByFieldsAction = createButton("Summary by a given fields different values", "summary_by_fields");
        CardAction showUnresolvedAlertsAction = createButton("Show unresolved alerts since N days", "unresolved");

        List<CardAction> buttons = new ArrayList<>();
        buttons.add(filterAlertsAction);
        buttons.add(summaryByFieldsAction);
        buttons.add(showUnresolvedAlertsAction);

        HeroCard card = new HeroCard();
        card.setButtons(buttons);
        card.setTitle("Filter Alerts Choose one option : )");
        Activity activity = MessageFactory.attachment(card.toAttachment());
        return activity;
    }

    private Activity cardForOwnersHandling() {
        CardAction assign = createButton("Assign Owner", "assign_button");
        CardAction remove= createButton("Remove Owner", "remove_button");

        List<CardAction> buttons = new ArrayList<>();
        buttons.add(assign);
        buttons.add(remove);

        HeroCard card = new HeroCard();
        card.setButtons(buttons);
        card.setTitle("Owner handling Choose one option :)");
        Activity activity = MessageFactory.attachment(card.toAttachment());
        return activity;
    }

    private Activity createWelcomeCardActivity() {
        CardAction refreshMessages = createButton("Refresh Alerts", "refresh_alerts");
        CardAction filterAlertsExpandAction = createButton("Filter Alerts", "filter_alerts_options");
        CardAction ownersHandlingExpandAction = createButton("Assign or remove owners for an alert", "owners_handling_options");
        CardAction updatePriority = createButton("Update priority (add or update a keyword's priority)", "update_priority");
        CardAction notify = createButton("Schedule Notification", "notify_button");
        CardAction markAsResolvedButton  = createButton("Mark Alert as Resolved", "mark_as_resolved");


        HeroCard card = new HeroCard();

        List<CardAction> buttons = new ArrayList<>();
        buttons.add(refreshMessages);
        buttons.add(filterAlertsExpandAction);
        buttons.add(ownersHandlingExpandAction);
        buttons.add(updatePriority);
        buttons.add(notify);
        buttons.add(markAsResolvedButton);

        card.setButtons(buttons);
        card.setTitle("Welcome to the bot :)");
        Activity activity = MessageFactory.attachment(card.toAttachment());
        return activity;
    }

    private CompletableFuture<Void> sendErrorMessage(TurnContext turnContext) {
        String replyText = "not able to connect to the server right now";
        Activity replyActivity= MessageFactory.text(replyText);
        return turnContext.sendActivity(replyActivity).thenApply(resourceResponse -> null);
    }

    private CompletableFuture<Void> signInUserHandler(TurnContext turnContext, String channelId) {
        try {
            if(AuthController.isSignedIn(channelId)) {
                return turnContext.sendActivity(
                        MessageFactory.text("You are already signed in. tag the bot and type - show me options to use Alerts Analyzer Bot")
                ).thenApply(resourceResponse -> null);
            }
        }
        catch(IOException ex) {
            return sendErrorMessage(turnContext);
        }
        return turnContext.sendActivity(MessageFactory
                        .text("Follow this link https://login.microsoftonline.com/common/oauth2/v2.0/authorize?client_id=967c973b-c486-4d38-968a-3d964800821b&response_type=code&scope=offline_access%20channelmessage.read.all"))
                .thenApply(resourceResponse -> null);
    }

    private CompletableFuture<Void> setUpUserHandler(TurnContext turnContext, String channelId) {
        Activity message = turnContext.getActivity();
        String messageText = message.getText();

        int messageLength = messageText.length();
        int startingIndexOfUUID =messageText.indexOf("login ")+"login ".length();

        StringBuilder stringBuilder = new StringBuilder();
        int startindIndexGroupId = messageText.indexOf("groupId=") + "groupId=".length();
        for(int i=startindIndexGroupId; i < messageLength; i++) {
            if(messageText.charAt(i) == '&') {
                break;
            }
            else {
                stringBuilder.append(messageText.charAt(i));
            }
        }
        String groupId = stringBuilder.toString();

        if(messageLength - startingIndexOfUUID < 36) {
            return turnContext.sendActivity(
                    MessageFactory.text("Wrong Message sent by user try signing in again and follow the instructions shown")
            ).thenApply(resourceResponse -> null);
        }

        String uuid = message.getText().substring(startingIndexOfUUID, startingIndexOfUUID+36);
        try{
            AuthController.generateRefreshToken(uuid, channelId, groupId);
        }
        catch(IOException ex) {
            return sendErrorMessage(turnContext);
        }
        return turnContext.sendActivity(MessageFactory.text("successfully logged In. Alerts Analyzer Bot is ready!!"))
                .thenApply(resourceResponse -> null);
    }

    private CompletableFuture<Void> refreshData(TurnContext turnContext, String channelId) {

        try {
            List<JSONObject> alerts = GetMessages.readAlertFromChannel(channelId);
            for(JSONObject alert : alerts) {
               try {
                   JSONObject alertObject = AlertParser.getAlertObject(alert);
                   ESClient.insert(alertObject);
               }
               catch(Exception ex) {
                   ex.printStackTrace();
               }
            }
        }
        catch(IOException ex) {
            return sendErrorMessage(turnContext);
        }
        String reply = "Successfully refreshed the data";
        return turnContext.sendActivity(MessageFactory.text(reply)).thenApply(resourceResponse -> null);
    }


    protected CompletableFuture<Void> onMessageActivity(TurnContext turnContext) {
        Activity message = turnContext.getActivity();
        String val = message.getConversation().getId();
        String channelId = val.substring(0, val.indexOf(";"));

        if (turnContext.getActivity().isType(ActivityTypes.MESSAGE)&&turnContext.getActivity().getText() == null) {
            HashMap<String, String> userInput = GetInput.getReceive(turnContext);
            System.out.println(userInput);

            if(userInput.keySet().size() == 3 && userInput.containsKey("field")) {
                // filter     field, value, days
                String field = userInput.get("field");
                String value = userInput.get("value");
                int days = Integer.parseInt(userInput.get("days"));

                Activity updatedCard = filterAlertsActivityHandler.sendAfterSubmitCard(field, value, days);
                updatedCard.setId(turnContext.getActivity().getReplyToId());

                filterAlertsActivityHandler.sendFilteredAlerts(turnContext, field, value, days, channelId);
                return turnContext.updateActivity(updatedCard).thenApply(resourceResponse -> null);
            }

            if(userInput.keySet().size() == 3 && userInput.containsKey("time")) {
                // notification schedule
                int time= Integer.parseInt(userInput.get("time"));
                int count = Integer.parseInt(userInput.get("count"));
                String messageUrl = userInput.get("messageUrl");

                Activity updatedCard = notificationScheduler.sendAfterSubmitCard();
                updatedCard.setId(turnContext.getActivity().getReplyToId());
                turnContext.updateActivity(updatedCard).thenApply(resourceResponse -> null);

                return notificationScheduler.scheduleNotification(turnContext, messageUrl, channelId, time, count, appId);
            }

            else if(userInput.keySet().size() == 1 && userInput.containsKey("days")) {
                // unresolved n days
                int days = Integer.parseInt(userInput.get("days"));

                Activity updatedCard = unresolvedAlertsSinceNdays.sendAfterSubmitCard(days);
                updatedCard.setId(turnContext.getActivity().getReplyToId());

                turnContext.updateActivity(updatedCard).thenApply(resourceResponse -> null);
                return unresolvedAlertsSinceNdays.getUnresolvedAlertsSinceNdays(channelId, days, turnContext);
            }

            else if(userInput.keySet().size() ==1 && userInput.containsKey("messageUrl")) {
                // mark as resolved 
                String messageUrl = userInput.get("messageUrl");

                Activity updatedCard = markAsResolvedHandler.sendAfterSubmitCard();
                updatedCard.setId(turnContext.getActivity().getReplyToId());
                turnContext.updateActivity(updatedCard).thenApply(resourceResponse -> null);

                return markAsResolvedHandler.markAsResolved(turnContext, messageUrl, channelId);
            }

            else if(userInput.containsKey("keyword")) {
                // priority update
                String keyword = userInput.get("keyword");
                int value = Integer.parseInt(userInput.get("value"));

                Activity updatedCard = updatePriorityHandler.sendAfterSubmitCard(keyword, value);
                updatedCard.setId(turnContext.getActivity().getReplyToId());

                turnContext.updateActivity(updatedCard).thenApply(resourceResponse -> null);
                return updatePriorityHandler.updatePriority(keyword, value, channelId, turnContext);
            }

            // generate summary by field values
            String field = userInput.get("field");
            int days = Integer.parseInt(userInput.get("days"));

            Activity updatedCard = summaryByFieldsActivityHandler.sendAfterSubmitCard(field, days);
            updatedCard.setId(turnContext.getActivity().getReplyToId());

            turnContext.updateActivity(updatedCard).thenApply(resourceResponse -> null);
            return summaryByFieldsActivityHandler.sendSummary(field, channelId, days, turnContext);
        }

        if(message.getText().contains("refresh_alerts")) {
            return refreshData(turnContext, channelId);
        }

        if(message.getText().contains("sign in")) {
            return signInUserHandler(turnContext, channelId);
        }

        if(message.getText().contains("login") && message.getText().contains("groupId")) {
            return setUpUserHandler(turnContext, channelId);
        }

        if(message.getText().contains("filter_alerts_options")) {
            return turnContext.sendActivity(cardForFilterAlerts()).thenApply(resourceResponse -> null);
        }

        if(message.getText().contains("owners_handling_options")) {
            return turnContext.sendActivity(cardForOwnersHandling()).thenApply(resourceResponse -> null);
        }

        if(message.getText().contains("show me options")) {
            return turnContext.sendActivity(createWelcomeCardActivity()).thenApply(resourceResponse -> null);
        }

        if(message.getText().contains("filter_alerts")) {
            return filterAlertsActivityHandler.sendFilterForm(turnContext);
        }

        if(message.getText().contains("unresolved")) {
            return unresolvedAlertsSinceNdays.sendFormForDaysInput(turnContext);
        }

        if(message.getText().contains("update_priority")) {
            return updatePriorityHandler.sendFormForPriorityInput(turnContext);
        }

        if(message.getText().contains("summary_by_fields")) {
            return summaryByFieldsActivityHandler.sendFormForSummary(turnContext);
        }

        if(message.getText().contains("assign_button")) {
            return turnContext.sendActivity(MessageFactory.text("{url of alert} assign_owners and tag all people you want to assign as owner")).
                    thenApply(resourceResponse -> null);
        }

        if(message.getText().contains("remove_button")) {
            return turnContext.sendActivity(MessageFactory.text("{url of alert} remove_owners and tag all people you want to remove as owner")).
                    thenApply(resourceResponse -> null);
        }

        if(message.getText().contains("assign_owners")) {
            return ownersAssignActivityHandler.assignOwners(turnContext, channelId);
        }

        if(message.getText().contains("remove_owners")) {
            return ownersAssignActivityHandler.removeOwners(turnContext, channelId);
        }

        if(message.getText().contains("notify_button")) {
            return notificationScheduler.sendNotificationInputForm(turnContext);
        }

        if(message.getText().contains("mark_as_resolved")) {
            return markAsResolvedHandler.sendMarkAsResolvedForm(turnContext);
        }

        Mention mention = new Mention();
        mention.setMentioned(turnContext.getActivity().getFrom());
        mention.setText(
                "<at>" + URLEncoder.encode(turnContext.getActivity().getFrom().getName()) + "</at>"
        );

        Activity replyActivity = MessageFactory.text("Hello " + mention.getText() + ".");
        replyActivity.setMentions(Collections.singletonList(mention));

        return turnContext.sendActivity(replyActivity).thenApply(resourceResponse -> null);
    }

    @Override
    protected CompletableFuture<Void> onInstallationUpdateAdd(TurnContext turnContext) {
        return turnContext.sendActivity(MessageFactory.text("hello thanks for installing the bot")).thenApply(resourceResponse -> null);
    }

}
