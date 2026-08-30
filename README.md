# 🧩 BadCode's Morphe Patches

<p align="center">

[![GitHub Release](https://img.shields.io/github/v/release/0xBadCod3/morphe-patches?style=for-the-badge&logo=github)](https://github.com/0xBadCod3/morphe-patches/releases)
[![License](https://img.shields.io/github/license/0xBadCod3/morphe-patches?style=for-the-badge)](LICENSE)
[![GitHub Stars](https://img.shields.io/github/stars/0xBadCod3/morphe-patches?style=for-the-badge)](https://github.com/0xBadCod3/morphe-patches/stargazers)
[![GitHub Issues](https://img.shields.io/github/issues/0xBadCod3/morphe-patches?style=for-the-badge)](https://github.com/0xBadCod3/morphe-patches/issues)

</p>

Custom Morphe patches collection maintained by **0xBadCod3**.

---

## 🚀 How to Add to Morphe

### Method 1: One-Click Import (Easiest)
Click the link below on your Android device with Morphe installed:

👉 **[Add to Morphe](https://morphe.software/add-source?github=0xBadCod3/morphe-patches)**

---

### Method 2: In Morphe Manager App
1. Open **Morphe Manager** on your Android device.
2. Navigate to **Settings** > **Sources** (or **Patch Sources**).
3. Tap **Add Source** and paste the repository URL:
   ```text
   https://github.com/0xBadCod3/morphe-patches
   ```
4. Tap **Save** / **Update**. Morphe will fetch the latest `.mpp` patch bundle automatically.

---

### Method 3: Manual `.mpp` File Import
1. Go to the **[Releases](https://github.com/0xBadCod3/morphe-patches/releases)** page.
2. Download the latest `patches-*.mpp` file.
3. In Morphe Manager, select **Import Local Bundle** and select the `.mpp` file from your device storage.

---

## 🩹 Included Patches

<!-- PATCHES_START -->
| # | App | Patch | Target Package | Supported Version |
|---|---|---|---|---|
| 1 | **Cloudflare One Agent** | Remove Firebase & Telemetry | [`com.cloudflare.cloudflareoneagent`](https://play.google.com/store/apps/details?id=com.cloudflare.cloudflareoneagent) | `any` (v2.5.5+) |
| 2 | **Cloudflare 1.1.1.1** | Remove Firebase & Telemetry | [`com.cloudflare.onedotonedotonedotone`](https://play.google.com/store/apps/details?id=com.cloudflare.onedotonedotonedotone) | `any` (v6.38.9+) |
| 3 | **Cloudflare 1.1.1.1** | Spoof WARP+ Unlimited UI | [`com.cloudflare.onedotonedotonedotone`](https://play.google.com/store/apps/details?id=com.cloudflare.onedotonedotonedotone) | `any` (v6.38.9+) |
| 4 | **Cloudflare 1.1.1.1** | Disable Analytics / Telemetry | [`com.cloudflare.onedotonedotonedotone`](https://play.google.com/store/apps/details?id=com.cloudflare.onedotonedotonedotone) | `any` (v6.38.9+) |
| 5 | **Cloudflare 1.1.1.1** | Disable SSL Pinning | [`com.cloudflare.onedotonedotonedotone`](https://play.google.com/store/apps/details?id=com.cloudflare.onedotonedotonedotone) | `any` (v6.38.9+) |
| 6 | **Sticker Maker** | Remove Advertisements | [`customstickermaker.whatsappstickers.personalstickersforwhatsapp`](https://play.google.com/store/apps/details?id=customstickermaker.whatsappstickers.personalstickersforwhatsapp) | `any` (v1.292.79+) |
| 7 | **Sticker Maker** | Disable Analytics / Telemetry | [`customstickermaker.whatsappstickers.personalstickersforwhatsapp`](https://play.google.com/store/apps/details?id=customstickermaker.whatsappstickers.personalstickersforwhatsapp) | `any` (v1.292.79+) |
<!-- PATCHES_END -->

---

## 🛠️ Building Locally

```bash
# Clone the repository
git clone https://github.com/0xBadCod3/morphe-patches.git
cd morphe-patches

# Build the Android DEX patch bundle (.mpp)
./gradlew :patches:buildAndroid

# The output .mpp will be located in:
# patches/build/libs/patches-<version>.mpp
```

---

## 📄 License
This project is licensed under the [GNU General Public License v3.0](LICENSE).
