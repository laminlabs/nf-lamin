/*
 * Copyright 2025, Lamin Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.lamin.nf_lamin.helpers

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory

/**
 * Records what the plugin logs, to check what ends up on the console: Nextflow shows INFO and
 * above for loggers under {@code nextflow.lamin}.
 */
class LogCapture {

    private final Logger logger = LoggerFactory.getLogger('nextflow.lamin') as Logger
    private final ListAppender<ILoggingEvent> appender = new ListAppender<ILoggingEvent>()
    private final Level previousLevel

    LogCapture() {
        previousLevel = logger.level
        logger.level = Level.DEBUG
        appender.start()
        logger.addAppender(appender)
    }

    void stop() {
        logger.detachAppender(appender)
        appender.stop()
        logger.level = previousLevel
    }

    List<String> getInfos() { return messagesAt(Level.INFO) }

    List<String> getWarnings() { return messagesAt(Level.WARN) }

    List<String> getErrors() { return messagesAt(Level.ERROR) }

    List<String> getDebugs() { return messagesAt(Level.DEBUG) }

    private List<String> messagesAt(Level level) {
        // a snapshot: artifact worker threads may still be logging
        return new ArrayList<ILoggingEvent>(appender.list)
            .findAll { ILoggingEvent event -> event.level == level }
            .collect { ILoggingEvent event -> event.formattedMessage }
    }
}
