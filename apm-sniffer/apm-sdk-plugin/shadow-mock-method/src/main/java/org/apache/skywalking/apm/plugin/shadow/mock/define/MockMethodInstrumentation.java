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

package org.apache.skywalking.apm.plugin.shadow.mock.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.MultiClassNameMatch;
import org.apache.skywalking.apm.plugin.shadow.mock.MethodInfo;
import org.apache.skywalking.apm.plugin.shadow.mock.MockMethodConfig;

import java.util.Map;

public class MockMethodInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    public static final String INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.shadow.mock.MockMethodInterceptor";

    @Override
    protected ClassMatch enhanceClass() {
        Map<MethodInfo, MethodInfo> ms = MockMethodConfig.getInstance();
        if (ms.isEmpty()) {
            return null;
        }
        String[] classNames = new String[ms.size()];
        int i = 0;
        for (MethodInfo mi : ms.keySet()) {
            classNames[i] = mi.getClassName();
        }
        return MultiClassNameMatch.byMultiClassMatch(classNames);
    }

    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
    }

    @Override
    public InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
                new InstanceMethodsInterceptPoint() {
                    @Override
                    public ElementMatcher<MethodDescription> getMethodsMatcher() {
                        Map<MethodInfo, MethodInfo> mm = MockMethodConfig.getInstance();
                        String[] methodNames = new String[mm.size()];
                        int i = 0;
                        for (MethodInfo mi : mm.keySet()) {
                            methodNames[i] = mi.getMethodName();
                        }
                        return ElementMatchers.namedOneOf(methodNames);
                    }

                    @Override
                    public String getMethodsInterceptor() {
                        return INTERCEPTOR_CLASS;
                    }

                    @Override
                    public boolean isOverrideArgs() {
                        return false;
                    }
                }
        };
    }
    
}
