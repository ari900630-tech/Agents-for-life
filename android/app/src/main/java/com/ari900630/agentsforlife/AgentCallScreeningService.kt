package com.ari900630.agentsforlife

import android.telecom.Call
import android.telecom.CallScreeningService

class AgentCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart ?: ""
        val blocked = getSharedPreferences("agents_settings", MODE_PRIVATE)
            .getStringSet("blocked_numbers", emptySet()).orEmpty()
        val shouldBlock = number.isNotBlank() && blocked.any { normalize(it) == normalize(number) }
        val response = CallResponse.Builder()
            .setDisallowCall(shouldBlock)
            .setRejectCall(shouldBlock)
            .setSkipCallLog(shouldBlock)
            .setSkipNotification(shouldBlock)
            .build()
        respondToCall(callDetails, response)
    }

    private fun normalize(value: String): String = value.filter { it.isDigit() || it == '+' }
}
