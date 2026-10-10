# FinTrack

Tracks your spending from **bank and delivery-app SMS** on your Android phone, and lets **Claude on your PC** categorize it over MCP.

- **The phone has no AI.** It uses fixed text rules to pull out the **amount**, the **date** and whether money went out or came in. It masks every account and card number (`XXXX`) before saving anything.
- **The PC** keeps the main copy of categories and every label change. It has a **web console** where you review and edit labels, and it builds a **training dataset** from your reviewed labels so an ML model can take over later.

```
 PHONE (Android, no AI)                 PC
┌───────────────────────┐  Wi-Fi/USB  ┌──────────────────────────────────────┐
│ SMS → rules → masked  │◄───────────►│ PC database (labels + full history)  │
│ amount / date / text  │   token     │   ▲             ▲            ▲       │
│ duplicate detection   │             │ MCP server   Web console   (later)   │
│ PC link server (8765) │             │ (Claude)     :8800 (you)   ML model  │
└───────────────────────┘             └──────────────────────────────────────┘
```

## Repository layout

```
android/   Android app (Kotlin, no AndroidX; one dependency: NanoHTTPD)
  app/src/main/java/com/fintrack/core/    SMS parser, masking, duplicate matching (plain Kotlin, unit-tested)
  app/src/main/java/com/fintrack/data/    SQLite store, settings
  app/src/main/java/com/fintrack/sms/     SMS receiver + inbox import
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

1. Copy the APK to the phone and open it to install (allow "Install unknown apps" for your file manager).
2. Open FinTrack and tap **Grant**.
3. On Android 13 and later, the SMS permission may show **"Restricted setting"**. Tap **App info**, then **⋮** (top right), then **Allow restricted settings**. Go back and tap **Grant** again.
4. From now on, every bank or delivery SMS that arrives is parsed and saved, even when the app is closed.
   **Import older SMS** can add older messages (since install, 30, 90 or 365 days).
5. Xiaomi, Oppo, Vivo and similar phones: set FinTrack's battery setting to **No restrictions** so incoming SMS are always processed.

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
- **Duplicates**: confirm or reject the links between bank and delivery-app SMS.
- **Categories**: add, rename, or merge (rename to an existing name), and every transaction follows.
- **Dataset**: counts, plus CSV / JSONL export (`fintrack export --format csv --out data.csv` does the same).
- **Sync with phone** button: also runs automatically whenever Claude lists or categorizes.

### Label rules

| Who | Reviewed? | Notes |
|---|---|---|
| You (console) | yes | always wins; Claude can never overwrite a manual label |
| Claude | no, until you accept it | |
| Correction (you change Claude's label) | yes | recorded as `correct`; the most useful training rows |

Every change is appended to the `label_history` table and never overwritten. A dataset row includes the masked text, sender, amount, direction, date, weekday, hour, category, who labeled it, whether it was corrected, and what the machine label had been.

## 5. How parsing and duplicate detection work

- **Senders**: known bank/UPI headers (HDFCBK, SBIUPI, ICICIB, AXISBK, KOTAKB…) and delivery apps (Swiggy, Zomato, Blinkit, Zepto, Amazon, Flipkart, BigBasket…). An unknown sender counts as a bank only if the text looks like an account, card or UPI alert. Personal phone numbers are ignored.
- **Ignored**: OTPs, promos and offers, bill-due reminders, "will be debited" notices, payment requests, failed or declined transactions, and delivery messages without an amount.
- **Amount**: `Rs` / `INR` / `₹` / `/-` / "debited by 1250.00". Balance and limit amounts are skipped.
- **Date**: `10-10-26`, `10/10/2026`, `2026-10-10`, `10Oct26`, `10 Oct 2026`, `Oct 10, 2026`, `10 Oct`. If the SMS has no date, or the date is more than 45 days away from when the SMS arrived, the SMS time is used.
- **Masking**: `XX1234`, `****1234`, numbers after `A/c` / `Acct` / `Card ending`, and any run of 9 or more digits all become `XXXX`. The unmasked text is never stored.
- **Duplicates**: a bank SMS and a delivery-app SMS with the same amount are linked. Within **10 min** they're an `auto` duplicate (not counted). Within **2 h** they're also `auto`. Within **24 h**, and only if the bank SMS names the app, they're a `possible` duplicate (still counted until you confirm). The bank SMS is always the main record. An exact repeat of the same SMS (same sender, text and minute) is dropped.

Add a bank or app by editing `android/app/src/main/java/com/fintrack/core/SenderClassifier.kt`. Add an SMS format by adding a test case to `SmsParserTest.kt` first.

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
- [ ] Phase 3: train a local model on the reviewed dataset, compare it against held-out labels, and switch the categorizer between Claude and the model (low-confidence rows fall back to Claude or you)
