package com.huiyi.assistant.ops;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LogFile;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * staging/prod must write Java logs to logging.file.name. Dev keeps the console appender only.
 */
class LogbackFileAppenderTest {

    private static final String PROBE = "logback-file-appender-probe";

    @TempDir
    Path tempDir;

    @AfterEach
    void resetLogging() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.stop();
        new LogbackLoggingSystem(getClass().getClassLoader()).cleanUp();
        context.reset();
        clearLogFileProperties();
    }

    @Test
    void stagingProfileWritesLogFileAndKeepsConsole() throws Exception {
        Path logFile = tempDir.resolve("app.log");
        initialize("docker", "staging", logFile);
        assertTrue(hasAppender(ConsoleAppender.class), "console appender must stay attached");
        assertTrue(hasAppender(FileAppender.class), "file appender must be attached when LOG_FILE is set");
        root().info(PROBE);
        String written = Files.readString(logFile, StandardCharsets.UTF_8);
        assertTrue(written.contains(PROBE), "log line must reach the file named by logging.file.name");
    }

    @Test
    void prodProfileWritesLogFileAndKeepsConsole() throws Exception {
        Path logFile = tempDir.resolve("app.log");
        initialize("docker", "prod", logFile);
        assertTrue(hasAppender(ConsoleAppender.class), "console appender must stay attached");
        assertTrue(hasAppender(FileAppender.class), "file appender must be attached when LOG_FILE is set");
        root().info(PROBE);
        String written = Files.readString(logFile, StandardCharsets.UTF_8);
        assertTrue(written.contains(PROBE), "log line must reach the file named by logging.file.name");
    }

    @Test
    void devProfileWithoutLogFileUsesConsoleOnly() {
        initialize("docker", null, null);
        assertTrue(hasAppender(ConsoleAppender.class), "dev must keep the console appender when LOG_FILE is unset");
        assertFalse(hasAppender(FileAppender.class), "dev must not attach a file appender when LOG_FILE is unset");
    }

    private void initialize(String firstProfile, String secondProfile, Path logFile) {
        clearLogFileProperties();
        MockEnvironment environment = new MockEnvironment();
        if (secondProfile == null) {
            environment.setActiveProfiles(firstProfile);
        } else {
            environment.setActiveProfiles(firstProfile, secondProfile);
            environment.setProperty("logging.file.name", logFile.toString().replace('\\', '/'));
        }
        LogbackLoggingSystem system = new LogbackLoggingSystem(getClass().getClassLoader());
        system.cleanUp();
        system.beforeInitialize();
        system.initialize(new LoggingInitializationContext(environment), null, LogFile.get(environment));
    }

    private static void clearLogFileProperties() {
        System.clearProperty("LOG_FILE");
        System.clearProperty("LOG_PATH");
        System.clearProperty("logging.file.name");
        System.clearProperty("logging.file.path");
    }

    private static Logger root() {
        return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }

    private static boolean hasAppender(Class<?> type) {
        Iterator<Appender<ILoggingEvent>> appenders = root().iteratorForAppenders();
        while (appenders.hasNext()) {
            if (type.isInstance(appenders.next())) {
                return true;
            }
        }
        return false;
    }
}
