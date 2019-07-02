package com.unitedtraders.logging.logback

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.UnsynchronizedAppenderBase
import org.graylog2.gelfclient.GelfConfiguration
import org.graylog2.gelfclient.GelfMessage
import org.graylog2.gelfclient.GelfTransports
import org.graylog2.gelfclient.transport.GelfTransport
import org.graylog2.gelfclient.transport.GelfUdpTransport
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicLong

class GraylogNettyAppender : UnsynchronizedAppenderBase<ILoggingEvent>() {

    var graylogHost = "localhost"
    var graylogPort = 12201 // default TCP-GELF port
    var transport = "TCP" // TCP or UDP
    var queueSize = 512
    var queueFullStrategy = "DROP" // DROP or UDP
    var queueProcessRate = 1; // in seconds (1 means once per second, 10 means once per 10 seconds)
    var tlsEnabled = false
    var tlsCertVerificationEnabled = true
    var reconnectDelay = 500
    var connectTimeout = 1000
    var tcpNoDelay = false
    var tcpKeepAlive = false
    var sendBufferSize = -1
    var maxInflightSends = 512
    var eventLoopThreads = 0

    var additionalFields = mutableMapOf<String, Any>()

    var locationIncluded = false
    var loggerIncluded = false
    var markerIncluded = false
    var mdcIncluded = false
    var threadIncluded = false
    val facility = "netty-gelf-client"

    var originHost: String = ""
        get() {
           if (field.isEmpty()) {
                return getLocalHostName() ?: ""
           } else {
               return field
           }
        }

    private lateinit var gelfClient: GelfTransport
    private lateinit var udpGelfClient: GelfUdpTransport
    private lateinit var messageFactory: GelfMessageFactory

    override fun start() {

        if (!isInputParamsValid()) return

        try {
            val config = createClientConfiguration()
            gelfClient = GelfTransports.create(config)
            if (queueFullStrategy == "UDP") {
                udpGelfClient = GelfTransports.create(GelfTransports.UDP, config) as GelfUdpTransport
            }
            messageFactory = DefaultGelfMessageFactory()
            super.start()
        } catch (ex: Throwable) {
            addError("Couldn't start appender", ex)
        }
    }

    override fun stop() {
        try {
            gelfClient.stop()
            super.stop()
        } catch (ex: Throwable) {
            addError("Couldn't stop appender", ex)
        }
    }

    override fun append(event: ILoggingEvent) {

        if (event.level == Level.OFF) {
            // don't send log with off level
            return
        }

        sendMessage(messageFactory.createMessage(this, event), event.level)
    }

    private fun sendMessage(msg: GelfMessage?, eventLogLevel: Level) {

        if (gelfClient.trySend(msg)) {
            return
        }

        // if queue is full then apply some strategy
        if (eventLogLevel.levelInt >= Level.WARN.levelInt) {
            gelfClient.send(msg)
        } else {
            if (queueFullStrategy == "DROP") {
                // drop all logs which level less than WARN
                return;
            } else {
                // or send them via UDP
                if (!udpGelfClient.trySend(msg)) {
                    udpGelfClient.send(msg)
                }
            }
        }
    }

    private fun createClientConfiguration(): GelfConfiguration {

        val config = GelfConfiguration(graylogHost, graylogPort)
        config.queueSize(queueSize)
        config.queueProcessRateInSec(queueProcessRate)
        config.connectTimeout(connectTimeout)
        config.reconnectDelay(reconnectDelay)
        config.tcpNoDelay(tcpNoDelay)
        config.tcpKeepAlive(tcpKeepAlive)
        config.sendBufferSize(sendBufferSize)
        config.maxInflightSends(maxInflightSends)
        config.threads(eventLoopThreads)
        config.transport(GelfTransports.valueOf(transport))

        if (tlsEnabled) config.enableTls() else config.disableTls()
        if (tlsCertVerificationEnabled) config.enableTlsCertVerification() else config.disableTlsCertVerification()

        return config
    }

    private fun isInputParamsValid(): Boolean {
        if (graylogHost.isEmpty()) {
            addError("param 'graylogHost' is not set")
            return false
        }

        if (!isTransportCorrect()) {
            addError("param 'transport' is not correctly set. It should be one of from (${GelfTransports.values().map { it.name }})")
            return false
        }

        val strategies = arrayOf("DROP", "UDP")
        if (!(queueFullStrategy in strategies)) {
            addError("param 'queueFullStrategy' is not correctly set. It should be one of from ($strategies)")
            return false
        }

        return true
    }

    private fun isTransportCorrect() = GelfTransports.values().any { it.name == transport }

    fun addAdditionalField(keyValue: String) {
        val parts = keyValue.split("=".toRegex(), 2).toTypedArray()
        if (parts.size != 2) {
            addError(
                String.format(
                    "additionalField must be in the format key=value, but found [%s]",
                    keyValue
                )
            )
            return
        }
        additionalFields[parts[0]] = parts[1]
    }

    private fun getLocalHostName(): String? {
        var hostName: String? = null
        try {
            hostName = InetAddress.getLocalHost().hostName
        } catch (e: UnknownHostException) {
            addError("Unknown local hostname", e)
        }

        return hostName
    }
}