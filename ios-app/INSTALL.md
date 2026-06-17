# Installing TicHealthSync on your iPhone (first-time guide)

You're building on Windows with no Mac, so the path is:

**GitHub → Codemagic (builds + signs in the cloud) → TestFlight → your iPhone.**

There are two milestones in `codemagic.yaml`:

- **`ios-compile`** — verifies the app compiles. Needs **no Apple account**. Do this first.
- **`ios-testflight`** — signs the app and sends it to TestFlight so you can install it.
  Needs a **paid Apple Developer Program** membership.

---

## The one unavoidable cost

To install your own app on a physical iPhone without a Mac, you need the **Apple
Developer Program: 99 USD/year**. A free Apple ID will not work here (free signing only
works from Xcode on a Mac, and those apps expire after 7 days). Enroll at
<https://developer.apple.com/programs/> (enrollment can take a few hours to a day).

Everything below assumes you've enrolled.

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
