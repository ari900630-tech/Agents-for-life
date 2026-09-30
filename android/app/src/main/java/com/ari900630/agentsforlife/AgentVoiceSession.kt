package com.ari900630.agentsforlife

import android.content.Intent
import android.service.voice.VoiceInteractionSession

class AgentVoiceSession(context: android.content.Context) : VoiceInteractionSession(context) {
    override fun onShow(args: android.os.Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startAssistantActivity(intent)
    }
}
