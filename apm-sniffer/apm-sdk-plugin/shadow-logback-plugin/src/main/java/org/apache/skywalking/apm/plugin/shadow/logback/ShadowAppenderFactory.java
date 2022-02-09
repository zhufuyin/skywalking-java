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

package org.apache.skywalking.apm.plugin.shadow.logback;

import ch.qos.logback.core.Appender;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.rolling.RollingPolicy;
import ch.qos.logback.core.rolling.TriggeringPolicy;
import ch.qos.logback.core.util.COWArrayList;
import ch.qos.logback.core.util.FileSize;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.util.CollectionUtil;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.apache.skywalking.apm.plugin.shadow.logback.Constants.APPENDER_SHADOW_PREFIX;
import static org.apache.skywalking.apm.plugin.shadow.logback.Constants.FIELD_AAI;
import static org.apache.skywalking.apm.plugin.shadow.logback.Constants.FIELD_APPENDER_LIST;

public class ShadowAppenderFactory {

    private static final ILog LOGGER = LogManager.getLogger(ShadowAppenderFactory.class);

    private static Cache<Object, COWArrayList<Object>> SHADOW_ATTACHABLE_CACHE = CacheBuilder.newBuilder()
            .weakKeys().build();

    private static Cache SHADOW_APPENDER_CACHE = CacheBuilder.newBuilder().weakKeys().build();

    private static final String CONSOLE_APPENDER_NAME = "ch.qos.logback.core.ConsoleAppender";
    private static final String ASYNC_APPENDER_NAME = "ch.qos.logback.classic.AsyncAppender";
    private static final String FILE_APPENDER_NAME = "ch.qos.logback.core.FileAppender";
    private static final String ROLLING_FILE_APPENDER_NAME = "ch.qos.logback.core.rolling.RollingFileAppender";

    // create shadow-appender for raw appender
    public static Object getOrCreateShadowAppender(ClassLoader cl, Object originAppender,
                                                   String shadowPath) throws Exception {
        String appenderName = getAppenderName(originAppender);
        if (appenderName.startsWith(APPENDER_SHADOW_PREFIX)) {
            return originAppender;
        }
        Object shadowAppender = SHADOW_APPENDER_CACHE.getIfPresent(originAppender);
        if (shadowAppender != null) {
            return shadowAppender;
        }
        shadowAppender = createShadowAppender(cl, originAppender, appenderName, shadowPath);
        if (shadowAppender != null) {
            SHADOW_APPENDER_CACHE.put(originAppender, shadowAppender);
        }
        return shadowAppender;
    }

    public static Object createShadowAppender(ClassLoader cl, Object originAppender, String appenderName,
                                              String shadowPath) throws Exception {

        if (ASYNC_APPENDER_NAME.equals(originAppender.getClass().getName())) { // async appender
            return createAsyncShadowAppender(cl, originAppender, appenderName, shadowPath);
        } else {
            if (!isFileAppender(cl, originAppender)) {
                return null;
            }
            return createFileShadowAppender(cl, originAppender, appenderName, shadowPath);
        }
    }

    public static boolean isConsoleAppender(Object appender) {
        return CONSOLE_APPENDER_NAME.equals(appender.getClass().getName());
    }

    public static boolean isFileAppender(ClassLoader cl, Object appender) throws ClassNotFoundException {
        return cl.loadClass(FILE_APPENDER_NAME).isAssignableFrom(appender.getClass());
    }

    public static Object createAsyncShadowAppender(ClassLoader cl, Object originAppender, String appenderName,
                                                   String shadowPath) throws Exception {
        Object aai = originAppender.getClass().getField(FIELD_AAI).get(originAppender);
        List appenders = (List) aai.getClass().getField(FIELD_APPENDER_LIST).get(aai);
        if (CollectionUtil.isEmpty(appenders)) {
            return null;
        }
        Class<?> asyncAppenderClazz = originAppender.getClass();
        Method addAppenderMethod = asyncAppenderClazz.getMethod("addAppender", Appender.class);

        Object shadowAsyncAppender = asyncAppenderClazz.getDeclaredConstructor().newInstance();
        for (Object appender : appenders) {
            Object shadowAppender = getOrCreateShadowAppender(cl, appender, shadowPath);
            if (shadowAppender != null) {
                addAppenderMethod.invoke(shadowAsyncAppender, shadowAppender);
            }
        }
        Method msSetName = asyncAppenderClazz.getMethod("setName", String.class);
        msSetName.invoke(shadowAsyncAppender, APPENDER_SHADOW_PREFIX + appenderName);
        asyncAppenderClazz.getMethod("start").invoke(shadowAsyncAppender);
        return shadowAsyncAppender;
    }

    private static Object createFileShadowAppender(ClassLoader cl, Object originAppender, String appenderName,
                                                          String shadowPath) throws Exception {
        Class<?> appenderClass = originAppender.getClass();
        String fileName = (String) appenderClass.getMethod("getFile").invoke(originAppender);
        Object encoder = appenderClass.getMethod("getEncoder").invoke(originAppender);
        Object ctx = appenderClass.getMethod("getContext").invoke(originAppender);

        Object shadowAppender = appenderClass.getDeclaredConstructor().newInstance();
        String shadowFilePath = shadowPath + File.separator + APPENDER_SHADOW_PREFIX + getSimpleFileName(fileName);
        appenderClass.getMethod("setFile", String.class).invoke(shadowAppender, shadowFilePath);
        appenderClass.getMethod("setName", String.class)
                .invoke(shadowAppender, APPENDER_SHADOW_PREFIX + appenderName);

        // rolling file appender
        if (ROLLING_FILE_APPENDER_NAME.equals(originAppender.getClass().getName())) {
            Object rollingPolicy = appenderClass.getMethod("getRollingPolicy").invoke(originAppender);
            Class<?> timeBasedRollingPolicy = cl.loadClass("ch.qos.logback.core.rolling.TimeBasedRollingPolicy");
            if (timeBasedRollingPolicy.isAssignableFrom(rollingPolicy.getClass())) {
                // sub class of TimeBasedRollingPolicy
                Object shadowRollingPolicy = cloneTimePolicy(shadowAppender, rollingPolicy, shadowPath);
                appenderClass.getMethod("setRollingPolicy", RollingPolicy.class)
                        .invoke(shadowAppender, shadowRollingPolicy);
            } else { // todo FixedWindowRollingPolicy
                rollingPolicy.getClass().getMethod("start").invoke(rollingPolicy);
                appenderClass.getMethod("setRollingPolicy", RollingPolicy.class)
                        .invoke(shadowAppender, rollingPolicy);
            }

            Object triggerPolicy = appenderClass.getMethod("getTriggeringPolicy").invoke(originAppender);
            if (timeBasedRollingPolicy.isAssignableFrom(triggerPolicy.getClass())) {
                Object shadowTriggerPolicy = cloneTimePolicy(shadowAppender, triggerPolicy, shadowPath);
                appenderClass.getMethod("setTriggeringPolicy", TriggeringPolicy.class)
                        .invoke(shadowAppender, shadowTriggerPolicy);
            } else { // todo FixedWindowRollingPolicy
                triggerPolicy.getClass().getMethod("start").invoke(triggerPolicy);
                appenderClass.getMethod("setTriggeringPolicy", TriggeringPolicy.class)
                        .invoke(shadowAppender, triggerPolicy);
            }
        }
        appenderClass.getMethod("setEncoder", Encoder.class).invoke(shadowAppender, encoder);
        appenderClass.getMethod("setContext", Context.class).invoke(shadowAppender, ctx);
        appenderClass.getMethod("setAppend", boolean.class).invoke(shadowAppender, true);
        appenderClass.getMethod("start").invoke(shadowAppender);
        return shadowAppender;
    }

    private static Object cloneTimePolicy(Object appender, Object policy, String shadowPath) throws Exception {
        Class<?> pClass = policy.getClass();
        Object shadowPolicy = pClass.getDeclaredConstructor().newInstance();
        String fileNamePattern = (String) pClass.getMethod("getFileNamePattern")
                .invoke(policy);
        String shadowFilePath = shadowPath + File.separator + getSimpleFileName(fileNamePattern);

        pClass.getMethod("setFileNamePattern", String.class).invoke(shadowPolicy, shadowFilePath);
        Context ctx = (Context) pClass.getMethod("getContext").invoke(policy);
        pClass.getMethod("setContext", Context.class).invoke(shadowPolicy, ctx);
        int maxHistory = (int) pClass.getMethod("getMaxHistory").invoke(policy);
        pClass.getMethod("setMaxHistory", int.class).invoke(shadowPolicy, maxHistory);
        pClass.getMethod("setParent", FileAppender.class).invoke(shadowPolicy, appender);
        Field totalSizeCapField = getFieldWithSuper(pClass, "totalSizeCap");
        totalSizeCapField.setAccessible(true);
        Object totalSizeCap = totalSizeCapField.get(policy);
        pClass.getMethod("setTotalSizeCap", FileSize.class).invoke(shadowPolicy, totalSizeCap);
        try {
            Field maxFileSizeField = pClass.getDeclaredField("maxFileSize");
            maxFileSizeField.setAccessible(true);
            Object maxFileSize = maxFileSizeField.get(policy);
            pClass.getMethod("setMaxFileSize", FileSize.class).invoke(shadowPolicy, maxFileSize);
        } catch (Exception e) { // not SizeAndTimeBasedRollingPolicy, ignore
        }
        Field cleanHistoryOnStartField = getFieldWithSuper(pClass, "cleanHistoryOnStart");
        cleanHistoryOnStartField.setAccessible(true);
        boolean cleanHistoryOnStart = (boolean) cleanHistoryOnStartField.get(policy);
        pClass.getMethod("setCleanHistoryOnStart", boolean.class).invoke(shadowPolicy, cleanHistoryOnStart);
        pClass.getMethod("start").invoke(shadowPolicy);
        return shadowPolicy;
    }

    private static Field getFieldWithSuper(Class<?> clazz, String field) throws Exception {
        NoSuchFieldException fieldException = null;
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(field);
            } catch (NoSuchFieldException e) {
                if (fieldException != null) {
                    fieldException = e;
                }
            }
            clazz = clazz.getSuperclass();
        }
        throw fieldException;
    }

    private static String getSimpleFileName(String fileName) {
        if (!fileName.startsWith("/")) {
            return fileName;
        }
        return fileName.substring(fileName.lastIndexOf("/") + 1);
    }

    public static COWArrayList<Object> getShadowAppenders(Object appenderAttachable) {
        return SHADOW_ATTACHABLE_CACHE.getIfPresent(appenderAttachable);
    }

    public static void putShadowAppenderAttachable(Object appenderAttachable, COWArrayList<Object> shadowAppenders) {
        SHADOW_ATTACHABLE_CACHE.put(appenderAttachable, shadowAppenders);
    }

    public static String getAppenderName(Object appender) throws Exception {
        return (String) appender.getClass().getMethod("getName").invoke(appender);
    }
}
