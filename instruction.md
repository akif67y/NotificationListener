I’d explain the current app to a new user like this:

> **Notification Collector watches Messenger and WhatsApp notification previews for course-related information.** It saves messages on your phone, removes repeated copies, and labels likely academic content. If you connect your own backend, it can turn relevant chat messages into a list of tests, deadlines, classes, resources, and questions, then alert you to important confirmed updates.

### How someone would use it

1. Open the Android app and grant **notification access**. The app can only read messages that appear in notifications; it cannot search old chats or see messages with no notification preview.
2. In **Settings**, choose which apps to collect from. Messenger is on by default; WhatsApp is off.
3. Add subjects in **Courses**. As notifications arrive, their chats appear in **Chats**, where the user can assign a course and enable a chat for analysis.
4. Check **Inbox** for saved messages and their local relevance labels. This works without a server.
5. To get **Events**, configure an HTTPS backend and device token, enable cloud analysis, and enable the desired chats. **Events** shows the current academic items; **Activity** shows when those items were created or updated. Optional alerts and a roughly 8 AM digest require Android notification permission. [App screens](D:/NotificationListener/app/src/main/java/dev/notificationlistener/ui/NotificationCollectorApp.kt:64) · [analysis flow](D:/NotificationListener/app/src/main/java/dev/notificationlistener/analysis/AnalysisWorker.kt:27)

The key expectation to set is that the app **does not currently silence or remove junk notifications from Messenger or WhatsApp**. It observes their notifications and builds a separate academic view. It also keeps locally captured messages in the Inbox even when they are labeled irrelevant; enabling a chat controls cloud analysis, not collection. [Capture logic](D:/NotificationListener/app/src/main/java/dev/notificationlistener/data/CaptureRepository.kt:11) · [chat analysis gate](D:/NotificationListener/app/src/main/java/dev/notificationlistener/data/AppDao.kt:156)

One practical limitation: the backend defaults to a simple keyword heuristic. That mode creates basic events but does not extract dates, and its confidence stays below the immediate-alert threshold. The fuller “academic assistant” experience depends on configuring an AI provider on the backend. [Backend provider](D:/NotificationListener/backend/app/providers.py:111) · [alert threshold](D:/NotificationListener/app/src/main/java/dev/notificationlistener/analysis/AnalysisWorker.kt:120)

So, as a product description, I’d currently call it an **academic notification collector and event organizer**, rather than a notification blocker. I based this on the current source code; I haven’t run it on a phone.