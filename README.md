# SMS Cleaner

Android app that deletes SMS by rules (sender, keywords, age, read state) and can run the rules daily.

**Status: written but never compiled or run.** Expect to fix a typo or two on the first build.

## Get the APK (no Android Studio needed)

1. Create a new GitHub repository and upload this whole folder (including the hidden `.github` folder).
2. Open the **Actions** tab, then **Build APK**, then **Run workflow** (it also runs on every push).
3. When it finishes, open the run and download the **SmsCleaner-apk** artifact. Unzip it to get `app-debug.apk`.
4. If the build fails, open the failed step and send me the red error lines.

Or with Android Studio (JDK 17): open this folder, then Build > Build APK(s).
The debug APK is signed with a debug key and installs fine for personal use.

## Install and set up (you and your relatives)

1. Send the APK and tap it on the phone. Allow "Install unknown apps" for the app you opened it from.
2. **Android 13+:** Settings > Apps > SMS Cleaner > three-dot menu > **Allow restricted settings**.
3. Open SMS Cleaner, tap **Set as default SMS app**, and approve.
4. Add a rule, tap **Preview matches** to see what it would delete, then save.
5. Tap **Run now**, or switch on **Run rules daily at** and pick a time.

## Things to know

- **Deleting only works while this app is the default SMS app.** Daily runs are skipped (with a notification) otherwise.
- **While default, picture messages (MMS) are not received** and the app cannot send SMS. Use the Inbox button to read messages. Switch the default back whenever you need to send.
- RCS / "chat" messages are not touched.
- A rule needs a sender, keywords or an age. Rules that would match everything are refused, and a bad regex matches nothing.
- Before every deletion, the messages are appended to a CSV (use **Share backup**). Deletion from the phone itself is permanent.
- Sender regex does a partial match, so anchor it (`^AX-.*BK$`) if you need a full match.
- Xiaomi, Oppo, Vivo and similar phones may need battery optimisation turned off for the app, or the daily job may not fire.
- Rules are stored as JSON in SharedPreferences (no Room), to keep the build simple.
