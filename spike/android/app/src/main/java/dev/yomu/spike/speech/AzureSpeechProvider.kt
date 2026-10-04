package dev.yomu.spike.speech

/**
 * Azure account-level provider: use REST for the voice catalog and the SDK
 * for synthesis plus timing. Shipping this composite remains gated on the
 * SDK's redistribution/license review.
 */
class AzureSpeechProvider(credentials: SpeechCredentials) : CloudSpeechProvider {
    private val catalog = AzureRestSpeechProvider(credentials)
    private val synthesis = AzureSdkSpeechProvider(credentials)

    override val id = "azure-speech"
    override val capabilities = catalog.capabilities + synthesis.capabilities
    override fun listVoices(): List<SpeechVoice> = catalog.listVoices()
    override fun synthesize(request: SpeechSynthesisRequest): SpeechSynthesisResult = synthesis.synthesize(request)
}
