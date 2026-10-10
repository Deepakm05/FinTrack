# FinTrack

Tracks your spending from **bank and delivery-app SMS** and **GPay / PhonePe / Paytm notifications** on your Android phone,
plus **receipt emails** (Swiggy, Zomato, Uber, Rapido, Amazon…) and **monthly bank statements** on your PC, and lets
**Claude on your PC** categorize it over MCP. The same payment seen in several places is counted once.

- **The phone has no AI.** It uses fixed text rules to pull out the **amount**, the **date** and whether money went out or came in. It masks every account and card number (`XXXX`) before saving anything.
- **The PC** keeps the main copy of categories and every label change. It has a **web console** where you review and edit labels, and it builds a **training dataset** from your reviewed labels so an ML model can take over later.

```
 PHONE (Android, no AI)                 PC
┌───────────────────────┐  Wi-Fi/USB  ┌──────────────────────────────────────┐
│ SMS + UPI app notifs  │◄───────────►│ PC database (labels + full history)  │
│ → rules → masked      │   token     │   ▲             ▲            ▲       │
│ duplicate detection   │             │ MCP server   Web console   (later)   │
│ PC link server (8765) │             │ (Claude)     :8800 (you)   ML model  │
└───────────────────────┘             └──────────────────────────────────────┘
```

## Repository layout

```
android/   Android app (Kotlin, no AndroidX; one dependency: NanoHTTPD)
  app/src/main/java/com/fintrack/core/    SMS + notification parsers, masking, duplicate matching (plain Kotlin, unit-tested)
  app/src/main/java/com/fintrack/data/    SQLite store, settings
  app/src/main/java/com/fintrack/sms/     SMS receiver + inbox import
  app/src/main/java/com/fintrack/notify/  payment-app notification listener
  app/src/main/java/com/fintrack/server/  PC link (foreground service + HTTP API)
  app/src/main/java/com/fintrack/ui/      the app screen
pc/        PC side (Python): sync, MCP server, web console, email + statement import, duplicate matching, dataset
```

---

## 1. Get the APK

GitHub Actions builds it on every push (**Actions → Android APK → Artifacts → `fintrack-apk`**).
You can also build it yourself with `cd android && ./gradlew assembleRelease`, which needs the Android SDK and JDK 17.

**Recommended: add a stable signing key once.** Without one, each CI build is signed with a different throwaway key, so a new APK won't install over the old one and you'd have to uninstall, which deletes the data on the phone.

```bash
keytool -genkeypair -v -keystore fintrack.jks -alias fintrack -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 fintrack.jks   # copy the output
```

Add these repo secrets (**Settings → Secrets and variables → Actions**): `FINTRACK_KEYSTORE_B64` (the base64 text), `FINTRACK_KEYSTORE_PASSWORD`, `FINTRACK_KEY_ALIAS` (`fintrack`), `FINTRACK_KEY_PASSWORD`. Keep `fintrack.jks` somewhere safe and **never commit it**. This repo is public.

## 2. Install on the phone

**Install from your PC over USB.** Google Play Protect blocks apps that ask for SMS or notification access when you
install them from a browser or file manager. It shows *"App blocked to protect your device"* and only an OK button.
An install with `adb` isn't blocked.

1. On the phone, open Settings → About phone and tap **Build number** 7 times (on Xiaomi phones, tap **OS version**).
   Then go to Settings → Developer options and turn on **USB debugging**. On Xiaomi / Redmi / POCO, also turn on **Install via USB**.
2. On the PC, get [SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools), connect the phone, allow debugging, and run:
   ```
   adb devices
   adb install -r app-release.apk
   ```
   `-r` installs an update over the existing app. That only works when both APKs are signed with the same key (see §1).
3. Open FinTrack:
   - **SMS:** tap **Grant**.
   - **Payment apps:** tap **Turn on notification access** and switch on *FinTrack payment apps*. FinTrack reads
     payment notifications only from UPI apps (GPay, PhonePe, Paytm, BHIM, CRED) and from Swiggy, Zomato, Blinkit, Zepto,
     Amazon, Flipkart, BigBasket, Myntra, Uber, Rapido and Ola ("you paid ₹349"). It ignores every other app.
   - If either one shows **"Restricted setting"** or is greyed out: open App info → **⋮** (top right) → **Allow restricted settings**, then try again.
4. From now on, every bank or delivery SMS and every payment-app notification is parsed and saved.
   **Import older SMS** can add older messages. Notifications only count from the moment access is on, because Android doesn't keep old ones.
5. Xiaomi, Oppo, Vivo and similar phones: set FinTrack's battery setting to **No restrictions** and enable **Autostart**. Otherwise
   the system may stop the notification listener.

**Why notifications as well as SMS?** Some banks no longer send SMS for small UPI payments (HDFC, for example, sends none
for UPI debits under ₹100), and UPI Lite payments never send a bank SMS. Payment apps don't post a notification for every
payment you make either. If something is still missing, compare against your bank statement. Statement import is on the roadmap.

## 3. Set up the PC (Python 3.10+)

```bash
cd pc
pip install -e .
```

On the phone, turn **PC link** on and pick a mode:

| Mode | Phone shows | PC setup |
|---|---|---|
| **Wi-Fi** (same network) | `http://192.168.x.y:8765` | `fintrack setup --phone-url http://192.168.x.y:8765 --token <token>` |
| **USB only** (safer) | `127.0.0.1` | `adb forward tcp:8765 tcp:8765` then `fintrack setup --phone-url http://127.0.0.1:8765 --token <token>` |

Tap **Copy token** in the app to copy the token. `fintrack status` checks the connection.
On Wi-Fi, traffic on your home network is not encrypted. Every request needs the token, but use USB mode on networks you don't trust.

### Connect Claude

**Claude Desktop**: add this to `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "fintrack": { "command": "fintrack", "args": ["mcp"] }
  }
}
```

**Claude Code**: `claude mcp add fintrack -- fintrack mcp`

If `fintrack` isn't on Claude's PATH, use the full path (`which fintrack` / `where fintrack`).

Then ask Claude, for example:
- *"Categorize my uncategorized October expenses."* (Claude calls `list_categories`, then `list_transactions`, then `categorize`.)
- *"How much did I spend on food this month?"* (Claude calls `spending_summary`.)
- *"Import the last 90 days of SMS from my phone."*

| MCP tool | What it does |
|---|---|
| `sync_phone` | pull new transactions, push labels and categories back to the phone |
| `list_transactions` | masked SMS text, date, amount (filters: month, status, direction) |
| `categorize` | set categories (`[{uid, category}]`); labels start as **unreviewed** |
| `list_categories` / `add_category` | the category list |
| `get_period` | the current spending period (or the one for a date / month) and when the phone last synced |
| `spending_summary` | totals by category, month, day or sender for a period (duplicates excluded) |
| `import_older_sms` | ask the phone to scan its inbox |
| `import_email` | fetch receipt / bank alert emails from Gmail |
| `import_statement` | import a bank statement file (CSV / XLSX / XLS / PDF) |

## Spending periods and last sync

- **Periods:** totals are shown per spending period, not per calendar month. By default a period starts on the **last
  day of a month** and ends the day before the next month's last day: 30 Sep – 30 Oct, 31 Oct – 29 Nov,
  30 Nov – 30 Dec. That suits a salary credited at month end. Tap the period name on the phone's Home screen to switch
  to calendar months or to a fixed day (2–28, e.g. the 25th). The PC copies the setting on every sync, so Claude, the
  console and `spending_summary` all use the same periods. A period is named after the month it ends in, so
  "November" means 31 Oct – 29 Nov.
- **Last sync:** the phone records every time the PC pulls or pushes. It shows "synced 5 min ago" on Home and on the
  PC link tab. The console header shows when the PC last synced with the phone.

## 4. Receipt emails (Gmail)

Some payments leave no SMS or notification: UPI inside Swiggy, Zomato or Amazon, UPI Lite, wallets, cash on delivery,
and **Uber / Rapido / Ola rides you pay at the end of the trip**. For these the receipt email is the record. Some banks
also send small UPI alerts only by email.

1. Turn on 2-Step Verification for your Google account, then create an **App Password** at
   <https://myaccount.google.com/apppasswords>. It's a 16-letter code. Don't use your normal password.
2. `fintrack email-setup --address you@gmail.com` asks for the app password and saves it in
   `~/.fintrack/config.json`, readable only by you.
3. Fetch with `fintrack email --days 30`, the **Fetch email** button in the console, or ask Claude *"import my emails"*.

FinTrack only looks at mail **from** known senders (Swiggy, Zomato, Blinkit, Zepto, Amazon, Flipkart, BigBasket, Myntra,
Uber, Rapido, Ola, BookMyShow, Domino's and the main banks). It opens Gmail **read-only** (nothing is marked as read) and
stores only a short masked snippet of each receipt, never the whole email. The amount comes from "Amount paid", then
"Order total / Grand total", then the largest "Total". Promotions and cancelled orders are skipped, and refunds count as
money in. Emails it has already checked are not downloaded again.

## 5. Monthly bank statement

Download the statement from net banking as **CSV / Excel (.xlsx, .xls) / PDF** and import it:

```bash
pip install -e ".[statements]"          # once: Excel + PDF support
fintrack statement HDFC_Oct.pdf --account HDFC --password <pdf password>
```

You can also use the **Import statement** button in the console, or ask Claude *"import the statement at C:\…\oct.csv for HDFC"*.

Every line that matches a payment already tracked (SMS, notification, email) is **linked to it as a duplicate**. Every
line that matches nothing is **added as a missed payment**. Importing the same or an overlapping statement again adds
nothing. Columns are found by their names (Date / Txn Date, Narration / Description / Particulars, Withdrawal / Debit,
Deposit / Credit, or Amount + Dr/Cr), which covers HDFC, SBI, ICICI, Axis, Kotak and most others. PDF reading is
best-effort. Use CSV or Excel if your bank offers it.

## 6. The web console

The console starts automatically with the MCP server, or you can run `fintrack console` and open <http://localhost:8800>. It only accepts connections from your own PC.

- **Transactions**: filter by month, category, status or source, change a category from the dropdown, or bulk-edit selected rows.
  Each row also shows the other records of the same payment (e.g. "＋ email · RAPIDO: Ride Invoice…").
- **Review Claude's labels**: accept or correct them. Only reviewed labels go into the training dataset.
- **Duplicates**: confirm ("Same purchase") or reject ("Different") every link, whether the phone or the PC made it.
- **Categories**: add, rename, or merge (rename to an existing name), and every transaction follows.
- **Dataset**: counts, plus CSV / JSONL export (`fintrack export --format csv --out data.csv` does the same).
- **Fetch email**, **Import statement** and **Sync with phone** buttons. Sync also runs automatically whenever Claude lists or categorizes.

Email and statement records live only on the PC. The phone app shows SMS and notification records.

### Label rules

| Who | Reviewed? | Notes |
|---|---|---|
| You (console) | yes | always wins; Claude can never overwrite a manual label |
| Claude | no, until you accept it | |
| Correction (you change Claude's label) | yes | recorded as `correct`; the most useful training rows |

Every change is appended to the `label_history` table and never overwritten. A dataset row includes the masked text, sender, amount, direction, date, weekday, hour, category, who labeled it, whether it was corrected, and what the machine label had been.

## 7. How parsing and duplicate detection work

- **Senders**: known bank/UPI headers (HDFCBK, SBIUPI, ICICIB, AXISBK, KOTAKB…) and delivery apps (Swiggy, Zomato, Blinkit, Zepto, Amazon, Flipkart, BigBasket…). An unknown sender counts as a bank only if the text looks like an account, card or UPI alert. Personal phone numbers are ignored.
- **Ignored**: OTPs, promos and offers, bill-due reminders, "will be debited" notices, payment requests, failed or declined transactions, and delivery messages without an amount.
- **Payment-app notifications**: only GPay, PhonePe, Paytm, BHIM and CRED, read through Android notification access.
  "₹40 paid to X" / "Payment of ₹349 received by Swiggy" count as money out. "X paid you ₹500" / "Received ₹300 from Y"
  count as money in. Requests, failed or pending payments, UPI Lite / wallet top-ups, cashback, scratch cards and offers are ignored.
  A payment notification that also mentions a reward is still counted.
- **Merchant app notifications** (Swiggy, Zomato, Blinkit, Zepto, Amazon, Flipkart, BigBasket, Myntra, Uber, Rapido, Ola):
  counted only when they say money moved ("₹349 paid", "₹245 charged", "You paid ₹103 to your captain", "fare"). Order
  updates, offers, fare estimates and cancelled trips are ignored. Refunds count as money in.
- **Amount**: `Rs` / `INR` / `₹` / `/-` / "debited by 1250.00". Balance and limit amounts are skipped.
- **Date**: `10-10-26`, `10/10/2026`, `2026-10-10`, `10Oct26`, `10 Oct 2026`, `Oct 10, 2026`, `10 Oct`. If the SMS has no date, or the date is more than 45 days away from when the SMS arrived, the SMS time is used.
- **Masking**: `XX1234`, `****1234`, numbers after `A/c` / `Acct` / `Card ending`, and any run of 9 or more digits all become `XXXX`. The unmasked text is never stored. Emails and statements are masked the same way on the PC.
- **Duplicates on the phone**: records from **different sources** with the same amount are linked: bank SMS, UPI app
  notification, merchant app notification, merchant SMS. The main record is the bank SMS if there is one, then the UPI
  notification, then the merchant's notification, then the merchant's SMS, so one Swiggy order paid with GPay gives one
  counted row instead of four. Within **10 min** or **2 h** a match is an `auto`
  duplicate (not counted). Within **24 h**, and only if the main record names the app, it's a `possible` duplicate (still counted
  until you confirm it). Two notifications from the same app are never linked. An exact repeat (same sender, text and minute) is dropped.
- **Duplicates on the PC** (email + statement): the PC links these to the phone's records, or to each other, and
  never changes a link the phone made.
  - **Email ↔ SMS / notification:** same amount, same time windows as above. A Rapido or Uber receipt links to the UPI debit
    you made to the driver's personal UPI ID, so Claude sees it was a ride.
  - **Statement line ↔ anything:** same amount and same date (`auto`) or ±1 day (`possible`). Each payment takes at
    most one line per statement account, so two ₹40 teas on the same day stay two payments.
  - **Email ↔ email from the same merchant:** within 30 min it's `auto` ("order placed" + "order confirmed"). Within 3 days
    it's `possible`, because it could be a second identical order. You decide.
  - **Main record:** the phone's record if there is one, then the email, then the statement line. If you categorized
    the email first, the label moves to the main record. Rejected links are never recreated.

Add a bank or delivery app in `core/SenderClassifier.kt`, or a payment app in `core/NotificationParser.kt` (the package
name is shown in Play Store links). Add a new message format by writing a test case in `SmsParserTest.kt` /
`NotificationParserTest.kt` first. Email senders and receipt labels live in `pc/fintrack_pc/email_import.py`;
statement column names are in `pc/fintrack_pc/statement_import.py`.

## 8. Development

```bash
cd android && ./gradlew testDebugUnitTest   # parser / masking / duplicate tests
cd pc && pip install -e ".[test]" && pytest  # sync, labels, email, statements, duplicates, console, MCP
```

### Phone HTTP API (port 8765, `Authorization: Bearer <token>`)

| Endpoint | Purpose |
|---|---|
| `GET /api/status` | device id, counts, latest version |
| `GET /api/changes?since=V&limit=N` | rows changed after version V (incremental sync) |
| `POST /api/push` | `{categories, labels:[{uid,category}], dup_states:[{uid,state}]}` from the PC |
| `POST /api/import` | `{days}`: scan older inbox SMS |

## Roadmap

- [x] Phase 1: phone parser, masking, duplicates, PC link, sync, MCP tools
- [x] Phase 2: web console (edit, review, duplicates, categories, dataset export)
- [x] Phase 1b: UPI app notifications (GPay / PhonePe / Paytm / BHIM / CRED) and merchant app notifications
      (Swiggy, Zomato, Uber, Rapido, ...), duplicate linking across all phone sources
- [x] Receipt emails (Gmail) and bank statement import, with duplicate matching across all sources on the PC
- [ ] Phase 3: train a local model on the reviewed dataset, compare it against held-out labels, and switch the categorizer between Claude and the model (low-confidence rows fall back to Claude or you)
