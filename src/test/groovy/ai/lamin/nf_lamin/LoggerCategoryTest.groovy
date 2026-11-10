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
package ai.lamin.nf_lamin

import org.slf4j.Logger
import spock.lang.Specification

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors

class LoggerCategoryTest extends Specification {

    private static final String PACKAGE = 'ai.lamin.nf_lamin'

    def 'every logger is named under nextflow.lamin, so that Nextflow shows it on the console'() {
        given:
        Map<String, String> loggers = pluginLoggers()

        expect:
        loggers.size() >= 26
        loggers.findAll { String className, String loggerName ->
            loggerName != className.replace(PACKAGE, 'nextflow.lamin')
        } == [:]
    }

    /** The name of each logger field in the compiled plugin classes, by class name. */
    private static Map<String, String> pluginLoggers() {
        Path root = Paths.get(LaminPlugin.protectionDomain.codeSource.location.toURI())
        List<Path> classFiles = Files.walk(root).withCloseable { stream ->
            stream.filter { Path p -> p.toString().endsWith('.class') }.collect(Collectors.toList())
        }
        Map<String, String> loggers = [:]
        for (Path file : classFiles) {
            String className = root.relativize(file).toString().replace('.class', '').replace(File.separator, '.')
            Class<?> cls = Class.forName(className, false, LaminPlugin.classLoader)
            // not just any logger: a Nextflow trait can weave in its own static logger field
            Field field = cls.declaredFields.find { Field f -> f.name == 'log' }
            if (field != null && Logger.isAssignableFrom(field.type) && Modifier.isStatic(field.modifiers)) {
                field.accessible = true
                loggers.put(className, (field.get(null) as Logger).name)
            }
        }
        return loggers
    }
}
