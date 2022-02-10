/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.plugin.shadow.log4j;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.appender.FileManager;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.RollingFileManager;
import org.apache.logging.log4j.core.config.AppenderControl;

import java.io.File;
import java.lang.reflect.Field;

public class ShadowAppenderFactory {

    public static final String APPENDER_SHADOW_PREFIX = "shadow_";

    private static Cache<AppenderControl, AppenderControl> SHADOW_APPENDER_CONTROL_CACHE =
            CacheBuilder.newBuilder().weakKeys().build();

    public static AppenderControl getOrCreateShadowAppenderControl(AppenderControl originAC) throws Exception {
        Appender originAppender = originAC.getAppender();
        // only support FileAppender and RollingFileAppender currently
        if (!(originAppender instanceof FileAppender) && !(originAppender instanceof RollingFileAppender)) {
            return null;
        }
        AppenderControl ac = SHADOW_APPENDER_CONTROL_CACHE.getIfPresent(originAC);
        if (ac != null) {
            return ac;
        }
        synchronized (originAC) {
            ac = SHADOW_APPENDER_CONTROL_CACHE.getIfPresent(originAC);
            if (ac != null) {
                return ac;
            }
            Object filter = originAC.getClass().getMethod("getFilter").invoke(originAC);
            Field levelField = originAC.getClass().getDeclaredField("level");
            levelField.setAccessible(true);
            Object level = levelField.get(originAC);
            Appender shadowAppender = createShadowAppender(originAppender);
            if (shadowAppender != null) {
                AppenderControl shadowAC = new AppenderControl(shadowAppender, (Level) level, (Filter) filter);
                SHADOW_APPENDER_CONTROL_CACHE.put(originAC, shadowAC);
                return shadowAC;
            }
            return null;
        }
    }

    private static Appender createShadowAppender(Appender originAppender) throws Exception {
        String shadowAppenderName = APPENDER_SHADOW_PREFIX + originAppender.getName();

        Class<?> originAppenderClazz = originAppender.getClass();
        if (originAppenderClazz.equals(FileAppender.class)) {
            FileAppender fileAppender = (FileAppender) originAppender;
            FileManager manager = fileAppender.getManager();
            String simpleFileName = getSimpleFileName(fileAppender.getFileName());
            String shadowFile = getShadowPath() + APPENDER_SHADOW_PREFIX + simpleFileName;
            FileAppender.Builder<?> builder = FileAppender.newBuilder();
            builder.withAppend(true).setName(shadowAppenderName)
                    .withFileName(shadowFile)
                    .withCreateOnDemand(manager.isCreateOnDemand())
                    .withLocking(manager.isLocking())
                    .withImmediateFlush(fileAppender.getImmediateFlush())
                    .setLayout(fileAppender.getLayout())
                    .setConfiguration(manager.getLoggerContext().getConfiguration())
                    .setIgnoreExceptions(fileAppender.ignoreExceptions())
                    .setFilter(fileAppender.getFilter());
//                    .withFilePermissions(PosixFilePermissions.toString(manager.getFilePermissions()))
//                    .withFileOwner(manager.getFileOwner())
//                    .withFileGroup(manager.getFileGroup());
            return builder.build();
        } else if (originAppenderClazz.equals(RollingFileAppender.class)) {
            RollingFileAppender rollingFileAppender = (RollingFileAppender) originAppender;
            RollingFileManager manager = rollingFileAppender.getManager();
            String simpleFileName = getSimpleFileName(rollingFileAppender.getFileName());
            String shadowFile = getShadowPath() + APPENDER_SHADOW_PREFIX + simpleFileName;
            RollingFileAppender.Builder<?> builder = RollingFileAppender.newBuilder();
            builder.withAppend(true).setName(shadowAppenderName)
                    .withFileName(shadowFile)
                    .withCreateOnDemand(manager.isCreateOnDemand())
                    .withLocking(manager.isLocking())
                    .withImmediateFlush(rollingFileAppender.getImmediateFlush())
                    .withFilePattern(rollingFileAppender.getFilePattern())
                    .withPolicy(rollingFileAppender.getTriggeringPolicy())
                    .withStrategy(manager.getRolloverStrategy())
                    .setLayout(rollingFileAppender.getLayout())
                    .setConfiguration(manager.getLoggerContext().getConfiguration())
                    .setIgnoreExceptions(rollingFileAppender.ignoreExceptions())
                    .setFilter(rollingFileAppender.getFilter());
//                    .withFilePermissions(PosixFilePermissions.toString(manager.getFilePermissions()))
//                    .withFileOwner(manager.getFileOwner())
//                    .withFileGroup(manager.getFileGroup());
            return builder.build();
        }
        return null;
    }

    private static String getShadowPath() {
        if (ShadowLog4jConfig.Plugin.Log4j.SHADOW_PATH.endsWith(File.separator)) {
            return ShadowLog4jConfig.Plugin.Log4j.SHADOW_PATH;
        } else {
            return ShadowLog4jConfig.Plugin.Log4j.SHADOW_PATH + File.separator;
        }
    }

    private static String getSimpleFileName(String fileName) {
        if (!fileName.startsWith("/")) {
            return fileName;
        }
        return fileName.substring(fileName.lastIndexOf("/") + 1);
    }
}
