# 📱 SHADOWLINK VPN PRO — GUIDE D'INSTALLATION COMPLET

## ─── STRUCTURE DU PROJET ──────────────────────────────────────

```
shadowlink/
├── app/
│   ├── build.gradle
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/shadowlink/vpn/
│       │   ├── ShadowLinkApp.kt           ← Application class
│       │   ├── activities/
│       │   │   ├── SplashActivity.kt
│       │   │   ├── LoginActivity.kt
│       │   │   ├── MainActivity.kt
│       │   │   ├── VpnFileActivity.kt     ← Créer/importer profil
│       │   │   └── UpdateActivity.kt
│       │   ├── fragments/
│       │   │   ├── HomeFragment.kt        ← Connexion VPN
│       │   │   ├── ProfilesFragment.kt    ← Gestion profils
│       │   │   ├── LogsFragment.kt        ← Journaux
│       │   │   └── SettingsFragment.kt    ← Paramètres
│       │   ├── adapters/
│       │   │   └── ProfilesAdapter.kt
│       │   ├── models/
│       │   │   └── Models.kt              ← Tous les data classes
│       │   ├── network/
│       │   │   └── ApiClient.kt           ← Communication panel
│       │   ├── services/
│       │   │   ├── ShadowVpnService.kt    ← Interface TUN Android
│       │   │   ├── SshTunnelService.kt    ← Tunnel SSH
│       │   │   └── KeepAliveService.kt
│       │   ├── utils/
│       │   │   ├── PrefsManager.kt        ← Stockage local
│       │   │   ├── SecurityUtils.kt       ← HWID + AES-256
│       │   │   ├── AdManager.kt           ← AdMob + Rewarded Ads
│       │   │   ├── UpdateManager.kt       ← Mises à jour in-app
│       │   │   ├── VpnFileManager.kt      ← Import/export configs
│       │   │   ├── FormatUtils.kt
│       │   │   └── BootReceiver.kt
│       │   └── vpn/
│       │       ├── VpnManager.kt          ← Gestionnaire central VPN
│       │       └── V2RayConfigBuilder.kt  ← Génération config Xray
│       └── res/
│           ├── layout/                    ← Tous les écrans XML
│           ├── drawable/                  ← Icones et backgrounds
│           ├── values/                    ← Colors, strings, styles
│           ├── menu/                      ← Navigation bottom
│           ├── raw/                       ← Animation Lottie
│           └── xml/                       ← FileProvider paths
├── build.gradle
├── settings.gradle
└── gradle.properties
```

---

## ─── ÉTAPE 1 : PRÉPARER ANDROID STUDIO ──────────────────────

1. Télécharger **Android Studio Hedgehog** (2023.1.1+)
   → https://developer.android.com/studio

2. Ouvrir Android Studio → **Open an existing project**
   → Sélectionner le dossier `shadowlink/`

3. Attendre la synchronisation Gradle (peut prendre 5–10 min)

---

## ─── ÉTAPE 2 : AJOUTER LA LIBRAIRIE V2RAY ──────────────────

La lib V2Ray n'est pas incluse dans le projet (trop volumineuse).
Tu as 2 options :

### Option A — Depuis le ZIP BlueSpace VPN (recommandé)
```
Copier ce fichier :
BlueSpace VPN/app/libs/libv2ray.aar

Vers :
shadowlink/app/libs/libv2ray.aar
```

### Option B — Depuis hiddify-libv2ray (GitHub)
```
https://github.com/hiddify/hiddify-libv2ray/releases
Télécharger libv2ray.aar → le mettre dans app/libs/
```

---

## ─── ÉTAPE 3 : CONFIGURER ADMOB ─────────────────────────────

1. Créer un compte sur https://admob.google.com
2. Créer une application "ShadowLink VPN Pro"
3. Créer 3 blocs d'annonces :
   - **Bannière** (Banner)
   - **Interstitiel** (Interstitial)
   - **Avec récompense** (Rewarded) ← Pour le "1h gratuite"

4. Copier les IDs dans `AdManager.kt` :
```kotlin
private const val BANNER_AD_UNIT_ID = "ca-app-pub-XXXX/XXXX"
private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-XXXX/XXXX"
private const val REWARDED_AD_UNIT_ID = "ca-app-pub-XXXX/XXXX"
```

5. Copier ton App ID dans `res/values/strings.xml` :
```xml
<string name="admob_app_id">ca-app-pub-XXXXXXXXXXXXXXXX~XXXXXXXXXX</string>
```

> ⚠️ Tant que tu n'as pas les vrais IDs, les IDs de test Google
> sont déjà configurés dans AdManager.kt → l'app fonctionnera.

---

## ─── ÉTAPE 4 : CONFIGURER L'URL DU PANEL ────────────────────

Dans `utils/PrefsManager.kt`, ligne panelUrl :
```kotlin
var panelUrl: String
    get() = prefs.getString(KEY_PANEL_URL, "https://TON-PANEL.com") ?: ...
```

Remplace `https://TON-PANEL.com` par l'URL réelle de ton panel.

L'utilisateur peut aussi le changer dans Paramètres → URL du panel.

---

## ─── ÉTAPE 5 : AJOUTER L'ANIMATION LOTTIE ──────────────────

1. Aller sur https://lottiefiles.com
2. Chercher "vpn" ou "shield" ou "loading"
3. Télécharger un fichier JSON gratuit
4. Renommer en `vpn_connecting.json`
5. Placer dans `app/src/main/res/raw/`

---

## ─── ÉTAPE 6 : COMPILER L'APK ───────────────────────────────

### APK de Debug (pour tester)
```
Build → Build Bundle(s) / APK(s) → Build APK(s)
```
Le fichier sera dans : `app/build/outputs/apk/debug/app-debug.apk`

### APK de Release (pour distribuer)
1. `Build → Generate Signed Bundle / APK`
2. Créer un **keystore** (garde-le précieusement !)
3. Choisir **APK** → Release
4. Le fichier sera dans : `app/build/outputs/apk/release/app-release.apk`

---

## ─── FONCTIONNALITÉS IMPLÉMENTÉES ──────────────────────────

| Fonctionnalité | Status |
|---|---|
| SSH Direct | ✅ |
| SSH Proxy | ✅ |
| SSH SSL | ✅ |
| SSH SSL + Payload | ✅ |
| SlowDNS | ✅ |
| UDP Hysteria | ✅ |
| V2Ray VMess | ✅ |
| V2Ray VLESS | ✅ |
| Trojan | ✅ |
| Shadowsocks | ✅ |
| Login compte panel | ✅ |
| Sync profils depuis panel | ✅ |
| Créer profil manuellement | ✅ |
| Import fichier (.slvpn, vmess://, ss://) | ✅ |
| Export profils | ✅ |
| Partage de profil (lien) | ✅ |
| AdMob Bannière | ✅ |
| AdMob Interstitiel | ✅ |
| Rewarded Ads → 1h gratuite | ✅ |
| Mise à jour in-app | ✅ |
| HWID anti-partage de compte | ✅ |
| Chiffrement AES-256-GCM | ✅ |
| Stats temps réel (upload/download/ping) | ✅ |
| Journaux (Logs) | ✅ |
| Bilingue FR/EN | ✅ |
| Thème Bleu foncé + Noir | ✅ |
| ProGuard obfuscation | ✅ |

---

## ─── API DU PANEL (ENDPOINTS ATTENDUS) ──────────────────────

Ton panel PHP devra exposer ces routes :

```
POST /api/auth/login
     Body: { username, password, hwid }
     Retourne: { success, token, user: { id, username, plan,
                 expiresAt, dataLimitMb, dataUsedMb, active } }

GET  /api/user/info
     Header: Authorization: Bearer TOKEN
     Retourne: { success, data: UserInfo }

GET  /api/vpn/profiles
     Header: Authorization: Bearer TOKEN
     Retourne: { success, data: [VpnProfile, ...] }

POST /api/auth/logout
     Header: Authorization: Bearer TOKEN

GET  /api/app/update?version=CODE
     Retourne: { success, data: { latestVersion, versionCode,
                 apkUrl, changelog, forceUpdate } }

POST /api/reward/grant
     Header: Authorization: Bearer TOKEN
     Body: { minutes, hwid }
```

---

## ─── PROCHAINE ÉTAPE : PANEL ADMIN PHP ──────────────────────

Le panel PHP/MySQL sera créé séparément.
Il permettra de :
- Gérer les utilisateurs (créer, activer, expirer)
- Ajouter des configs VPN depuis ton serveur 3X-UI
- Voir les statistiques de connexion
- Gérer les mises à jour de l'app
- Publier l'APK mis à jour

---

## ─── SUPPORT ─────────────────────────────────────────────────

Package ID : `com.shadowlink.vpn`
Version    : 1.0.0
minSdk     : 21 (Android 5.0+)
targetSdk  : 34 (Android 14)

---

## ─── SÉCURITÉ IMPLÉMENTÉE ──────────────────────────────

| Composant | Implémentation |
|---|---|
| Stockage local | EncryptedSharedPreferences (AES-256-GCM + AES-256-SIV) |
| Token API | Chiffré sur disque, révocable depuis panel |
| Profils VPN | Credentials chiffrés AES-256-GCM en BDD |
| Transport | HTTPS + Certificate Pinning (activer avant publication) |
| Anti-partage | HWID SHA-256 lié à l'appareil |
| Kill Switch | Coupe tout le trafic si VPN se déconnecte |
| Split Tunnel | Exclure des apps du VPN par package name |
| Logs | IPs et UUID masqués automatiquement |
| Reconnexion | WorkManager — max 5 tentatives toutes les 30s |
| Logs centralisés | AppLogger — masquage auto des données sensibles |

## ─── FICHIERS CLÉS SÉCURITÉ ─────────────────────────────

- `utils/SecurePrefsManager.kt`  → EncryptedSharedPreferences
- `network/CertificatePinner.kt` → Certificate Pinning OkHttp
- `vpn/KillSwitchManager.kt`     → Kill Switch réseau
- `vpn/SplitTunnelManager.kt`    → Split Tunnel par app
- `vpn/AutoReconnectManager.kt`  → Reconnexion WorkManager
- `utils/AppLogger.kt`           → Logs sécurisés centralisés
