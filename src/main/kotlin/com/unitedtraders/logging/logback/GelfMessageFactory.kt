package com.unitedtraders.logging.logback

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.PatternLayout
import ch.qos.logback.classic.spi.ILoggingEvent
import org.graylog2.gelfclient.GelfMessage
import org.graylog2.gelfclient.GelfMessageBuilder
import org.graylog2.gelfclient.GelfMessageLevel

/**
 * Converts log event to GELF message.
 */
interface GelfMessageFactory {

    /**
     * Converts log event to GELF message.
     *
     * @param appender
     * GELF appender
     * @param event
     * log event
     * @return GELF message
     */
    fun createMessage(appender: GraylogNettyAppender, event: ILoggingEvent): GelfMessage
}

/**
 * Default message factory implementation.
 */
class DefaultGelfMessageFactory : GelfMessageFactory {

    private val shortPatternLayout: PatternLayout
    private val fullPatternLayout: PatternLayout

    init {
        // Short message contains event message and no stack trace.
        shortPatternLayout = PatternLayout()
        shortPatternLayout.context = LoggerContext()
        shortPatternLayout.pattern = DEFAULT_SHORT_MESSAGE_PATTERN
        shortPatternLayout.start()

        // Full message contains stack trace.
        fullPatternLayout = PatternLayout()
        fullPatternLayout.context = LoggerContext()
        fullPatternLayout.pattern = DEFAULT_FULL_MESSAGE_PATTERN
        fullPatternLayout.start()
    }

    private var levelsMap = mapOf(
        Level.ERROR to GelfMessageLevel.ERROR,
        Level.INFO to GelfMessageLevel.INFO,
        Level.WARN to GelfMessageLevel.WARNING,
        Level.DEBUG to GelfMessageLevel.DEBUG,
        Level.TRACE to GelfMessageLevel.DEBUG,
        Level.ALL to GelfMessageLevel.NOTICE
    )

    override fun createMessage(appender: GraylogNettyAppender, event: ILoggingEvent): GelfMessage {

        val message = GelfMessageBuilder(shortPatternLayout.doLayout(event), appender.originHost)
            .timestamp(event.timeStamp)
            .level(levelsMap[event.level])
            .additionalFields(appender.additionalFields)
            .build()

        // additional fields borrowed from gelf appnder by pukkaone
        val fullMessage = fullPatternLayout.doLayout(event)
        if (!fullMessage.isEmpty()) {
            message.fullMessage = fullMessage
        }
        message.addAdditionalField("_facility", appender.facility)

        when {
            appender.locationIncluded -> {
                val locationInformation = event.callerData[0]
                message.addAdditionalField("_file", locationInformation.fileName)
                message.addAdditionalField("_line", locationInformation.lineNumber)
            }
            appender.loggerIncluded -> { message.addAdditionalField("_logger", event.loggerName) }
            appender.threadIncluded -> { message.addAdditionalField("_thread", event.threadName) }
            appender.markerIncluded -> { message.addAdditionalField("_marker", event.marker ?: "") }
            appender.mdcIncluded -> { event.mdcPropertyMap?.entries?.map { message.addAdditionalField(it.key, it.value) } }
        }

        return message
    }

    private fun setMessagePattern(patternLayout: PatternLayout, messagePattern: String) {
        patternLayout.stop()
        patternLayout.pattern = messagePattern
        patternLayout.start()
    }

    fun setShortMessagePattern(shortMessagePattern: String) {
        setMessagePattern(shortPatternLayout, shortMessagePattern)
    }

    fun setFullMessagePattern(fullMessagePattern: String) {
        setMessagePattern(fullPatternLayout, fullMessagePattern)
    }

    companion object {

        private val DEFAULT_SHORT_MESSAGE_PATTERN = "%m%nopex"
        private val DEFAULT_FULL_MESSAGE_PATTERN = "%m%n%xEx"
    }
}