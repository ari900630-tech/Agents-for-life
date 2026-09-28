package com.ari900630.agentsforlife

import android.telecom.Call
import android.telecom.CallScreeningService

class AgentCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        respondToCall(callDetails, CallResponse.Builder().setDisallowCall(false).build())
    }
}
