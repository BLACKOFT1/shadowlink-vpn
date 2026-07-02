# 📱 Compiler l'APK dans le cloud avec GitHub Actions

Pas besoin d'Android Studio ni d'un PC puissant. GitHub compile l'app
pour toi gratuitement, tu télécharges juste le résultat.

---

## Étape 1 — Créer un compte GitHub (si tu n'en as pas)

Va sur **https://github.com** → Sign up → crée ton compte (gratuit).

## Étape 2 — Créer un nouveau dépôt (repository)

1. Clique sur le **+** en haut à droite → **New repository**
2. Nom : `shadowlink-vpn-app`
3. Coche **Private** (pour que ton code reste privé, recommandé pour une app commerciale)
4. Ne coche rien d'autre (pas de README, pas de .gitignore)
5. Clique **Create repository**

## Étape 3 — Uploader le code du ZIP sur GitHub (sans ligne de commande)

GitHub permet d'uploader des fichiers directement depuis le navigateur :

1. Sur la page de ton nouveau dépôt, clique **uploading an existing file**
2. **Important** : dézippe d'abord le ZIP `SHADOWLINK_VPN_PRO_APP_FINAL.zip` sur ton PC (clic droit → Extraire tout)
3. Ouvre le dossier extrait `shadowlink/`
4. Sélectionne **tous les fichiers et dossiers à l'intérieur** (pas le dossier `shadowlink` lui-même, son contenu)
5. Glisse-dépose le tout dans la zone d'upload de GitHub
6. En bas, écris un message comme "Premier import du projet"
7. Clique **Commit changes**

⚠️ GitHub limite les uploads web à 25 Mo par fichier et ~100 fichiers d'un coup — comme le projet fait environ 100 fichiers légers, ça devrait passer en une fois. S'il y a une erreur, upload en 2-3 fois (d'abord `app/`, puis le reste).

## Étape 4 — Vérifier que le workflow se déclenche automatiquement

Dès que les fichiers sont uploadés (et donc `.github/workflows/build-apk.yml` avec eux), GitHub lance automatiquement la compilation.

1. Va dans l'onglet **Actions** en haut du dépôt
2. Tu verras "Build ShadowLink VPN Pro APK" en cours (rond jaune qui tourne)
3. Clique dessus pour voir la progression en direct
4. Ça prend généralement **3 à 8 minutes**

## Étape 5 — Télécharger l'APK compilé

1. Une fois le workflow terminé (coche verte ✅)
2. Reste sur cette page, descends tout en bas
3. Section **Artifacts** → tu verras :
   - `ShadowLinkVPN-debug` (à utiliser en premier, pas besoin de signature)
   - `ShadowLinkVPN-release-unsigned` (pour plus tard, publication)
4. Clique sur `ShadowLinkVPN-debug` → ça télécharge un ZIP contenant ton APK

## Étape 6 — Installer l'APK sur ton téléphone Android

1. Transfère le fichier `.apk` sur ton téléphone (câble USB, ou envoie-le toi-même par email/Drive)
2. Sur le téléphone, ouvre le fichier `.apk`
3. Android va demander d'autoriser "Sources inconnues" — accepte
4. Installation terminée, l'app apparaît

---

## 🔄 Pour chaque modification future du code

1. Modifie tes fichiers localement
2. Retourne sur GitHub → dans le dépôt → navigue au fichier à changer → crayon ✏️ → modifie → **Commit changes**
   *(ou upload le fichier modifié par-dessus, GitHub écrase automatiquement)*
3. Le build se relance tout seul → nouvel APK dans Actions → Artifacts

---

## ⚠️ Avant de publier réellement l'app

L'APK "debug" fonctionne très bien pour tester, mais pour publier sur
le Play Store ou distribuer officiellement il faudra signer l'APK
"release" avec une clé de signature — ça se fait aussi via GitHub
Actions (ajout d'un `keystore` en secret du dépôt). Dis-le-moi quand
tu en seras là, je préparerai cette étape avec toi.

## 🆘 Si le build échoue (croix rouge ❌)

Clique sur le workflow en échec → clique sur l'étape qui a une croix
rouge → lis le message d'erreur en bas et copie-le moi ici, je
corrigerai directement le code.
