# FinTrack

Tracks your spending from **bank and delivery-app SMS** and **GPay / PhonePe / Paytm notifications** on your Android phone, and lets **Claude on your PC** categorize it over MCP.

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
pc/        PC side (Python): sync, MCP server, web console, dataset export
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
     notifications from GPay, PhonePe, Paytm, BHIM and CRED only. It ignores every other app.
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
| `spending_summary` | totals by category, month, day or sender (duplicates excluded) |
| `import_older_sms` | ask the phone to scan its inbox |

## 4. The web console

The console starts automatically with the MCP server, or you can run `fintrack console` and open <http://localhost:8800>. It only accepts connections from your own PC.

- **Transactions**: filter by month, category or status, change a category from the dropdown, or bulk-edit selected rows.
- **Review Claude's labels**: accept or correct them. Only reviewed labels go into the training dataset.
- **Duplicates**: records from **different sources** with the same amount are linked: bank SMS, UPI app notification,
  delivery-app SMS. The main record is the bank SMS if there is one, then the UPI notification, then the delivery SMS,
  so one Swiggy order paid with GPay gives one counted row instead of three. Within **10 min** or **2 h** a match is an `auto`
  duplicate (not counted). Within **24 h**, and only if the main record names the app, it's a `possible` duplicate (still counted
  until you confirm it). Two notifications from the same app are never linked. An exact repeat (same sender, text and minute) is dropped.

Add a bank or delivery app in `core/SenderClassifier.kt`, or a payment app in `core/NotificationParser.kt` (the package
name is shown in Play Store links). Add a new message format by writing a test case in `SmsParserTest.kt` /
`NotificationParserTest.kt` first.

## 6. Development

```bash
cd android && ./gradlew testDebugUnitTest   # parser / masking / duplicate tests
cd pc && pip install -e ".[test]" && pytest  # sync, labels, console, MCP tools
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
- [x] Phase 1b: GPay / PhonePe / Paytm / BHIM / CRED notifications, three-way duplicate linking
- [ ] Bank statement import (PDF/CSV) on the PC to fill whatever SMS and notifications miss
- [ ] Phase 3: train a local model on the reviewed dataset, compare it against held-out labels, and switch the categorizer between Claude and the model (low-confidence rows fall back to Claude or you)
