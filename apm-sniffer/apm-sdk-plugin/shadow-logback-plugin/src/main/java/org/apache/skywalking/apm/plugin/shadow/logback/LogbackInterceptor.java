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

import ch.qos.logback.core.util.COWArrayList;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.CorrelationContext;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.util.CollectionUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

public class LogbackInterceptor implements InstanceMethodsAroundInterceptor {

    private static final ILog LOGGER = LogManager.getLogger(LogbackInterceptor.class);

    private static final String APPENDER_FIELD_APPENDER_LIST = "appenderList";
    private static final String APPENDER_METHOD_DOAPPEND = "doAppend";

    private static volatile Field APPENDER_LIST_FIELD;
    private static volatile Method DO_APPEND_METHOD;

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        CorrelationContext cc = ContextManager.getCorrelationContext();
        Optional<String> corr = cc.get("LMR");
        if (corr.isPresent() && "a".equals(corr.get())) {
            Field field = objInst.getClass().getDeclaredField(APPENDER_FIELD_APPENDER_LIST);
            field.setAccessible(true);
            List appenderList = (COWArrayList<Object>) field.get(objInst);
            if (CollectionUtil.isEmpty(appenderList)) {
                return;
            }
            boolean shadowLog = doShadowLog(objInst, allArguments[0]);
            if (shadowLog) {
                result.defineReturnValue(1);
            }
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {

    }

    private boolean doShadowLog(Object appendAttachable, Object event) throws Exception {
        initAppenderListField(appendAttachable);
        if (APPENDER_LIST_FIELD == null) {
            return false;
        }
        // get appender list in appendAttachable
        COWArrayList<Object> appenders = (COWArrayList<Object>) APPENDER_LIST_FIELD.get(appendAttachable);
        if (CollectionUtil.isEmpty(appenders)) {
            return false;
        }
        ClassLoader cl = appendAttachable.getClass().getClassLoader();
        COWArrayList<Object> shadowAppenders = getShadowAppenders(appendAttachable, cl, appenders);
        if (CollectionUtil.isEmpty(shadowAppenders)) {
            return false;
        }
        appendLoopOnAppenders(shadowAppenders, event);
        return true;
    }

    private COWArrayList<Object> getShadowAppenders(Object appenderAttachable, ClassLoader cl,
                                                    COWArrayList<Object> originAppenders) {
        COWArrayList<Object> shadowAppenders = ShadowAppenderFactory.getShadowAppenders(appenderAttachable);
        if (shadowAppenders != null) {
            return shadowAppenders;
        }
        synchronized (appenderAttachable) {
            shadowAppenders = ShadowAppenderFactory.getShadowAppenders(appenderAttachable);
            if (shadowAppenders != null) {
                return shadowAppenders;
            }
            shadowAppenders = new COWArrayList<>(new Object[0]);
            for (Object appender : originAppenders) {
                try {
                    if (ShadowAppenderFactory.isConsoleAppender(appender)) {
                        shadowAppenders.add(appender);
                        continue;
                    }
                    boolean isStarted = (boolean) appender.getClass().getMethod("isStarted").invoke(appender);
                    if (isStarted) {
                        Object shadowAppender = ShadowAppenderFactory.getOrCreateShadowAppender(cl, appender,
                                ShadowLogbackConfig.Plugin.Logback.SHADOW_PATH);
                        if (shadowAppender != null) {
                            shadowAppenders.add(shadowAppender);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("logback appender invoke method fail", e);
                }
            }
            if (shadowAppenders.size() > 0) {
                ShadowAppenderFactory.putShadowAppenderAttachable(appenderAttachable, shadowAppenders);
            }
            return shadowAppenders;
        }
    }

    public void appendLoopOnAppenders(COWArrayList<Object> shadowAppenders, Object event) throws Exception {
        final Object[] appenderArray = shadowAppenders.asTypedArray();
        for (Object appender : appenderArray) {
            initDoAppendMethod(appender);
            DO_APPEND_METHOD.invoke(appender, event);
        }
    }

    private static void initAppenderListField(Object appenderAttachable) throws Exception {
        if (APPENDER_LIST_FIELD == null) {
            synchronized (LogbackInterceptor.class) {
                if (APPENDER_LIST_FIELD == null) {
                    APPENDER_LIST_FIELD = appenderAttachable.getClass().getDeclaredField(APPENDER_FIELD_APPENDER_LIST);
                    APPENDER_LIST_FIELD.setAccessible(true);
                }
            }
        }
    }

    private static void initDoAppendMethod(Object appender) throws NoSuchMethodException {
        if (DO_APPEND_METHOD == null) {
            synchronized (LogbackInterceptor.class) {
                if (DO_APPEND_METHOD == null) {
                    DO_APPEND_METHOD = appender.getClass().getMethod(APPENDER_METHOD_DOAPPEND, Object.class);
                }
            }
        }
    }
}
