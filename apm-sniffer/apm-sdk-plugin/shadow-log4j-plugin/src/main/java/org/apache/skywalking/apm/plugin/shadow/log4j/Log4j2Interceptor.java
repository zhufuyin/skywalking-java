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

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.AppenderControl;
import org.apache.logging.log4j.core.config.AppenderControlArraySet;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class Log4j2Interceptor implements InstanceMethodsAroundInterceptor {

    private static final ILog LOGGER = LogManager.getLogger(Log4j2Interceptor.class);

    private static final String APPENDERS_FIELD = "appenders";

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        // getAppenders get appenderControls in loggerConfig
        Field appendersField = objInst.getClass().getDeclaredField(APPENDERS_FIELD);
        appendersField.setAccessible(true);
        AppenderControlArraySet appenders = (AppenderControlArraySet) appendersField.get(objInst);
        AppenderControl[] controls = (AppenderControl[]) appenders.getClass().getMethod("get").invoke(appenders);
        // build shadow appenderControl and appender for all original appenderControl
        for (AppenderControl ac : controls) {
            try {
                AppenderControl shadowAC = ShadowAppenderFactory.getOrCreateShadowAppenderControl(ac);
                if (shadowAC == null) {
                    ac.callAppender((LogEvent) allArguments[0]);
                } else {
                    // call shadow appenderControl's method: callAppender
                    shadowAC.callAppender((LogEvent) allArguments[0]);
                }
            } catch (Exception e) {
                LOGGER.error("create shadow appender or call appender fail.", e);
            }
        }
        result.defineReturnValue(1);
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
}
