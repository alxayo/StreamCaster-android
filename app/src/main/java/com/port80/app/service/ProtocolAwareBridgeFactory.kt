package com.port80.app.service

import com.pedro.common.ConnectChecker
import com.port80.app.data.model.StreamProtocol
import javax.inject.Inject

/**
 * Creates the correct [EncoderBridge] implementation based on the streaming protocol.
 *
 * - RTMP / RTMPS → [RtmpCamera2Bridge]
 * - SRT → [SrtCamera2Bridge]
 *
 * Injected via Hilt and bound in [com.port80.app.di.ServiceModule].
 */
class ProtocolAwareBridgeFactory @Inject constructor() : EncoderBridge.Factory {
    override fun create(connectChecker: ConnectChecker, protocol: StreamProtocol): EncoderBridge =
        when (protocol) {
            StreamProtocol.RTMP, StreamProtocol.RTMPS -> RtmpCamera2Bridge(connectChecker)
            StreamProtocol.SRT -> SrtCamera2Bridge(connectChecker)
        }
}
