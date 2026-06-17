# Installing TicHealthSync on your iPhone (first-time guide)

Two ways to get the app on your phone:

- **Path A — your Mac + free Apple ID (recommended, $0).** Install directly from Xcode
  over a USB cable. No paid account. Apps expire after 7 days; just re-run from Xcode to
  refresh. **Use this.**
- **Path B — Codemagic + TestFlight ($99/yr).** Cloud build + install with no Mac, but
  requires the paid Apple Developer Program. Documented below as a later option.

Either way, **Codemagic is still useful right now** as the automatic compile-check CI:
the `ios-compile` workflow builds the app (unsigned) on every push, so you catch Swift
errors without a Mac. That part needs no Apple account and is already green.

---

## Path A — install via your Mac with a free Apple ID (recommended)

Do this when your Mac is ready. Needs only your normal (free) Apple ID.

1. **Get the code on the Mac.** In Terminal:
   `git clone https://github.com/allenplay7/TicHealthSync.git`
2. **Open the project.** Launch Xcode → Open → `TicHealthSync/ios-app/TicHealthSync.xcodeproj`.
3. **Add your Apple ID to Xcode.** Xcode → Settings → Accounts → **+** → Apple ID → sign
   in. (This creates a free "Personal Team".)
4. **Set signing.** Click the **TicHealthSync** project → **TicHealthSync** target →
   **Signing & Capabilities** tab → tick **Automatically manage signing** → **Team** =
   your name (Personal Team).
   - If you see a red error that the bundle id is unavailable, change **Bundle
     Identifier** to something unique, e.g. `com.allen.tichealthsync`.
5. **Prep the iPhone (one time):**
   - Plug the iPhone into the Mac with a cable; on the phone tap **Trust This Computer**.
   - iOS 16+: enable **Settings → Privacy & Security → Developer Mode → On**, then restart
     the phone.
6. **Pick your phone as the run target** in Xcode's top toolbar (next to the scheme),
   then press **Run** (the ▶ button, or ⌘R). Xcode builds and installs it.
7. **Trust the developer cert (first run only).** The app installs but iOS blocks the
   first launch. On the phone: **Settings → General → VPN & Device Management** → tap your
   Apple ID under "Developer App" → **Trust**. Then tap the app icon to open it.
8. **Allow Bluetooth** when prompted, and you're running.

That's it — no payment, no TestFlight. Re-run from Xcode whenever the 7-day signing
lapses or you change code.

---

## Path B — Codemagic + TestFlight (only if you ever want no-Mac installs)

This needs the **paid Apple Developer Program (99 USD/yr)** — skip it unless you decide
you want cloud installs without plugging into the Mac. Enroll at
<https://developer.apple.com/programs/> first, then:

---

## Step 0 — Get the compile check green first

1. Go to <https://codemagic.io>, sign up, and connect your GitHub account.
2. Add application → pick `allenplay7/TicHealthSync`. Codemagic detects `codemagic.yaml`.
3. Start a build → choose workflow **"TicHealthSync iOS (compile check, unsigned)"**.
4. Wait for it to pass (green). If it fails, send me the log and I'll fix it.

Once that's green, the project is known-good and you can set up signing.

---

## Step 1 — Create an App Store Connect API key

This is the credential Codemagic uses to sign and upload on your behalf.

1. Go to <https://appstoreconnect.apple.com> → **Users and Access** → **Integrations**
   tab → **App Store Connect API** → **Team Keys** → **(+)**.
2. Name it (e.g. `Codemagic`), set **Access = App Manager**, click Generate.
3. **Download the `.p8` file now** — you can only download it once.
4. Note the **Key ID** (next to the key) and the **Issuer ID** (shown above the list).

## Step 2 — Add the key to Codemagic

1. In Codemagic: your avatar → **Team settings** (or User settings) → **Integrations**
   → **App Store Connect** → **Add key**.
2. Upload the `.p8`, paste the **Key ID** and **Issuer ID**.
3. **Name it exactly** `TicHealthSync ASC API key` (this must match the
   `app_store_connect:` line in `codemagic.yaml`). If you name it something else,
   tell me and I'll update the yaml.

## Step 3 — Create the app record in App Store Connect

1. <https://appstoreconnect.apple.com> → **Apps** → **(+)** → **New App**.
2. Platform: iOS. Name: `TicHealthSync`. Primary language: English.
3. **Bundle ID**: select `com.tichealthsync.ios`.
   - If it's not in the list, register it first at **Certificates, Identifiers &
     Profiles → Identifiers → (+) → App IDs → App**, description `TicHealthSync`,
     Bundle ID (Explicit) `com.tichealthsync.ios`. Then come back and create the app.
4. SKU: anything unique, e.g. `tichealthsync`. Create.

## Step 4 — Build and upload to TestFlight

1. In Codemagic, start a build → workflow **"TicHealthSync iOS (signed → TestFlight)"**.
   - (The yaml is also set to auto-run this when you push a git **tag** like `v0.1.0`.)
2. Codemagic builds, signs with a managed provisioning profile, and uploads the `.ipa`
   to TestFlight. This takes ~5–15 min.
3. After upload, Apple "processes" the build for a few more minutes.

## Step 5 — Add yourself as a tester and install

1. <https://appstoreconnect.apple.com> → your app → **TestFlight** tab.
2. Under **Internal Testing**, create a group (or use the default), and add yourself
   (your Apple ID email) as a tester. Internal testers don't need Beta App Review.
3. Once the build finishes processing, assign it to that group if prompted.
4. On your iPhone: install **TestFlight** from the App Store.
5. Open the invite email and tap **View in TestFlight**, or just open the TestFlight
   app — `TicHealthSync` appears. Tap **Install**.
6. Launch the app. On first run, **allow Bluetooth** when prompted.

You're done. New builds: bump and push a tag (e.g. `git tag v0.1.1 && git push origin
v0.1.1`), and the new build shows up in TestFlight automatically.

---

## Using the app with the watch

1. On the TicWatch: open TicHealthSync → **Start BLE Advertising**.
2. In the iPhone app: **Devices** tab → **Scan** → tap **TicHealthSync** to connect.
3. **Dashboard** shows status and pending count; **Debug** sends `HELLO` / `SYNC_NOW`
   and shows the raw JSON. CoreBluetooth needs a **real device** (it does nothing in
   the Simulator).

---

## Troubleshooting

- **"No matching profiles" / signing errors**: make sure the API key name in
  `codemagic.yaml` matches the one in Codemagic, and that the bundle id
  `com.tichealthsync.ios` exists in your account (Step 3).
- **"Duplicate build number"**: the yaml stamps the Codemagic build counter into the
  version automatically, so this shouldn't happen; if it does, just rerun.
- **Build won't appear in TestFlight**: wait for "Processing" to finish in the
  TestFlight tab; check the app is assigned to your internal testing group.
- **Compile fails**: run the `ios-compile` workflow and share the log.
