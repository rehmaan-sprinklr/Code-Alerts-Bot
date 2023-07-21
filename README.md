# Code-Alerts-Bot

## Description
Alerts triggered from the code need to be analysed so that certain actions can be taken proactively even before actual users observe issues.
The Code Alerts Bot analyses the code alerts and offers various features that assist in summarizing, filtering, assigning priorities, reminding, and resolving alerts. This helps the developers to quickly see in which environment, or partnerId the maximum failures are happening.

The bot is event driven and user will have to just click on buttons and fill forms to use the bot making it user friendly 

Users can mark the alerts as resolved once they are resolved 

## Features
Below is the full list of features it implements 

### Login functionality included
User can sign in via his microsoft account to grant access to read messages of a channel. OAuth2 is implemented so that user can sign in.


![image](https://github.com/rehmaan-sprinklr/Code-Alerts-Bot/assets/139646418/9e5ac4be-971e-4182-b2bf-a5c56016f511)


### 1. Day Wise Summary of Unique Alerts
The bot provides a day-wise summary of unique alerts along with the number of occurrences for each alert.


![image](https://github.com/rehmaan-sprinklr/Code-Alerts-Bot/assets/139646418/15d5ae9a-be47-46f7-972a-60b1520abac0)


### 2. Filtering Alerts
The bot allows users to filter alerts based on the following criteria:

Environment: Filter alerts specific to a particular environment (e.g., production, staging).
Partner Id: Filter alerts associated with a specific partner or client.
Stack Trace Keywords: Filter alerts containing certain keywords in the stack trace.
Service: Filter alerts related to a specific service or component.



![image](https://github.com/rehmaan-sprinklr/Code-Alerts-Bot/assets/139646418/276b9c0f-b65f-4f1f-b808-28f1a1591b5d)


![image](https://github.com/rehmaan-sprinklr/Code-Alerts-Bot/assets/139646418/4bf45016-1683-4694-bf38-239dafbe8d9b)



### 3. Unresolved Alerts
The bot can display alerts that remain unresolved for "n" days, where "n" is a configurable parameter provided by the user.


![image](https://github.com/rehmaan-sprinklr/Code-Alerts-Bot/assets/139646418/395ef2bd-0970-4c1e-a2b9-1af540bf654a)



### 4. Priority Assignment
Users can assign priority to alerts based on specific keywords or critical services. This feature allows users to highlight critical issues effectively.


![image](https://github.com/rehmaan-sprinklr/Code-Alerts-Bot/assets/139646418/2806a866-b75d-494a-91d9-154955d413c6)




![image](https://github.com/rehmaan-sprinklr/Code-Alerts-Bot/assets/139646418/7f9c5be1-4dd1-4c0a-9ceb-5b438a929c13)




### 5. Reminder Notifications
The bot offers a reminder notification system, which allows users to tag specific individuals and set the frequency of reminders. The bot will send reminder notifications to the tagged person at the specified intervals until the alert is resolved.



![image](https://github.com/rehmaan-sprinklr/Code-Alerts-Bot/assets/139646418/30ba1abf-5e67-480c-abbf-a4600d9ea467)




### 6. Automated Tagging and Re-assigning Owners
The bot automatically tags the appropriate owner to each alert based on owners assigned. Additionally, users can reassign owners if necessary.



![image](https://github.com/rehmaan-sprinklr/Code-Alerts-Bot/assets/139646418/5dda494a-e7b0-4062-9532-386e41e15d02)



![image](https://github.com/rehmaan-sprinklr/Code-Alerts-Bot/assets/139646418/fc11aab9-62ad-4607-8f68-89f2548170fc)




### 7. Resolving Alerts
Users can mark alerts as resolved when the underlying issues are fixed.



### 8. Auto Refresh Alerts
The bot periodically refreshes the list of alerts, ensuring that users have up-to-date information. It also provides a Refresh Button for manual refreshing with just a single click.


## How to Setup the Bot 

##### 1. In the `src/main/manifest` folder download the `archive.zip` file 
##### 2. Add this zip file as a customized app in the channel 
##### 3. Send the message to start the sign in process -> `@CodeAlertBot` sign in
##### 4. Follow the instructions of sign in using microsoft account and allow access to read messages of channels 
##### 5. Once the Bot is setup you can start using it via `@CodeAlertBot` show me options command 
