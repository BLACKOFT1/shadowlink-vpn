package com.shadowlink.vpn.utils

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Gestionnaire de publicités.
 *
 * Règles métier :
 *  - Bannière FIXE en bas de l'écran Home, toujours visible, jamais retirée.
 *  - Pour obtenir 1h de VPN gratuit, l'utilisateur doit regarder
 *    2 publicités de 30 secondes (rewarded) à la suite.
 *  - Disponible maximum 3 fois par jour (vérifié côté app ET côté panel
 *    via /api/reward/grant qui compte les requêtes du jour).
 */
object AdManager {

    // IDs de TEST Google — remplace par tes vrais IDs AdMob avant publication
    private const val BANNER_AD_UNIT_ID      = "ca-app-pub-3940256099942544/6300978111"
    private const val REWARDED_AD_UNIT_ID    = "ca-app-pub-3940256099942544/5224354917"

    private const val ADS_REQUIRED_FOR_REWARD = 2   // 2 pubs de 30s
    private const val MAX_REWARDS_PER_DAY     = 3

    private var rewardedAd: RewardedAd? = null
    private var isLoadingRewarded = false
    private var adsWatchedInSequence = 0

    // ── Bannière fixe ────────────────────────────────────────────

    fun createBannerAd(context: Context): AdView {
        return AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = BANNER_AD_UNIT_ID
            loadAd(AdRequest.Builder().build())
        }
    }

    // ── Rewarded : séquence de 2 pubs de 30s ────────────────────

    fun loadRewardedAd(context: Context) {
        if (isLoadingRewarded || rewardedAd != null) return
        isLoadingRewarded = true
        RewardedAd.load(context, REWARDED_AD_UNIT_ID, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { rewardedAd = ad; isLoadingRewarded = false }
                override fun onAdFailedToLoad(error: LoadAdError) { rewardedAd = null; isLoadingRewarded = false }
            })
    }

    fun isRewardedAdReady() = rewardedAd != null

    /** Combien de pubs déjà regardées aujourd'hui (info locale, le panel fait foi) */
    fun rewardsUsedToday(): Int = PrefsManager.rewardsUsedToday()

    fun canWatchAdsToday(): Boolean = rewardsUsedToday() < MAX_REWARDS_PER_DAY

    /**
     * Démarre la séquence de 2 publicités de 30s.
     * Appelle onSequenceComplete() une fois les 2 pubs visionnées.
     */
    fun startRewardSequence(
        activity: Activity,
        onAdShown: (current: Int, total: Int) -> Unit,
        onSequenceComplete: () -> Unit,
        onFailed: (String) -> Unit
    ) {
        if (!canWatchAdsToday()) {
            onFailed("Limite quotidienne atteinte ($MAX_REWARDS_PER_DAY pubs/jour). Revenez demain.")
            return
        }
        adsWatchedInSequence = 0
        showNextAdInSequence(activity, onAdShown, onSequenceComplete, onFailed)
    }

    private fun showNextAdInSequence(
        activity: Activity,
        onAdShown: (current: Int, total: Int) -> Unit,
        onSequenceComplete: () -> Unit,
        onFailed: (String) -> Unit
    ) {
        val ad = rewardedAd
        if (ad == null) {
            onFailed("Publicité en cours de chargement, réessayez dans quelques secondes.")
            loadRewardedAd(activity)
            return
        }

        onAdShown(adsWatchedInSequence + 1, ADS_REQUIRED_FOR_REWARD)

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                loadRewardedAd(activity)
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewardedAd = null
                onFailed("Erreur d'affichage de la publicité.")
            }
        }

        ad.show(activity) { _ ->
            adsWatchedInSequence++
            if (adsWatchedInSequence >= ADS_REQUIRED_FOR_REWARD) {
                // Les 2 pubs ont été regardées en entier
                PrefsManager.incrementRewardsUsedToday()
                onSequenceComplete()
            } else {
                // Charger et enchaîner avec la 2e pub
                loadRewardedAd(activity)
                // Petite latence pour laisser le SDK respirer avant la 2e pub
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    showNextAdInSequence(activity, onAdShown, onSequenceComplete, onFailed)
                }, 800)
            }
        }
    }
}
