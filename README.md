# SMS Cleaner

Android app that tidies your SMS: browse conversations, search, auto-sort messages into categories,
clean them up in one tap, or save rules that run every day. Built with Jetpack Compose.

## Features

- **Messages**: conversation list with search and a thread view. Long-press to select and delete.
- **Cleanup**: auto categories (OTPs, bank, offers, delivery, other) with age filters and one-tap clean.
- **Rules**: filter by category, sender (exact, prefix, contains, regex), keywords, age, read state and folder.
  Live preview, quick templates, run now, or run daily at a time you choose.
- **Protection**: never delete messages from your contacts or from senders you protect (lock icon in a thread).
- **Backup**: deleted messages are kept briefly for recovery (7, 30 or 90 days, or off), then removed automatically,
  and never more than 5,000 at once. Share as CSV from Settings.

## Get the APK

1. Push to GitHub. The **Build APK** workflow runs on every push (or run it by hand in the Actions tab).
2. Open the latest green run, then download the **SmsCleaner-apk** artifact and unzip it.
3. Install `app-debug.apk`. Updates install over the old version (the signing key is fixed in the repo).

## Set up on the phone

1. Install the APK. Allow "Install unknown apps" for the app you opened it from.
   If Play Protect blocks it (India blocks sideloaded apps that read SMS), see the notes below.
2. **Android 13+:** Settings > Apps > SMS Cleaner > three-dot menu > **Allow restricted settings**.
3. Open the app and follow the two setup steps: set as default SMS app, then allow SMS and contacts access.

## Things to know

- **Deleting only works while this app is the default SMS app.** Daily runs are skipped otherwise.
- While default, picture messages (MMS) aren't received and the app can't send SMS.
  Switch your default back whenever you need to send or receive pictures.
- RCS / "chat" messages are not touched.
- Categories are heuristics based on sender and wording. Check the count before cleaning.
- With "never delete contacts" on but contacts access denied, every message from a phone number is
  protected, so only company senders can be cleaned.
- A rule needs a category, sender, keywords or an age. Rules that would match everything are refused,
  and an invalid regex matches nothing.
- Xiaomi, Oppo, Vivo and similar phones may need battery optimisation turned off for the daily run.
- The debug signing key is committed on purpose so updates work. Fine for personal use; keep the repo private.
