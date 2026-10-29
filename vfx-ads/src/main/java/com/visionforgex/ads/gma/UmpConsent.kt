package com.visionforgex.ads.gma

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.visionforgex.ads.engine.ConsentDebug
import com.visionforgex.ads.engine.ConsentEngine

/**
 * Google's User Messaging Platform: the consent form where the law needs it (GDPR in the EEA, the UK
 * and Switzerland; US state laws), set up once in Ad Manager → Privacy & messaging. Ads are requested
 * only when it says they may be (a user who already answered can be asked for ads at once).
 */
internal class UmpConsent : ConsentEngine {
    private fun info(c: Context): ConsentInformation = UserMessagingPlatform.getConsentInformation(c)

    override fun gather(activity: Activity, underAge: Boolean, debug: ConsentDebug?, done: (Boolean, String?) -> Unit) {
        val b = ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(underAge)
        if (debug != null) {
            val d = ConsentDebugSettings.Builder(activity).setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
            debug.testDeviceHashedIds.forEach { d.addTestDeviceHashedId(it) }
            b.setConsentDebugSettings(d.build())
        }
        val ci = info(activity)
        var answered = false
        val once = { can: Boolean, err: String? -> if (!answered) { answered = true; done(can, err) } }
        try {
            ci.requestConsentInfoUpdate(activity, b.build(), {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError -> once(ci.canRequestAds(), formError?.message) }
            }, { requestError -> once(ci.canRequestAds(), requestError.message) })
        } catch (t: Throwable) { once(ci.canRequestAds(), t.message) }
    }

    override fun canRequestAds(context: Context): Boolean = try { info(context).canRequestAds() } catch (t: Throwable) { false }

    override fun privacyOptionsRequired(context: Context): Boolean = try {
        info(context).privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    } catch (t: Throwable) { false }

    override fun showPrivacyOptions(activity: Activity, done: (String?) -> Unit) {
        try { UserMessagingPlatform.showPrivacyOptionsForm(activity) { e -> done(e?.message) } } catch (t: Throwable) { done(t.message) }
    }

    override fun reset(context: Context) { try { info(context).reset() } catch (_: Throwable) {} }
}
