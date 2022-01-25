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

package org.apache.skywalking.apm.plugin.jedis.shadow.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.StaticMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.NameMatch;
import org.apache.skywalking.apm.plugin.jedis.shadow.Constant;

public class JedisShadowInstrumentation extends ClassEnhancePluginDefine {

    private static final String ENHANCE_CLASS = "redis.clients.jedis.Jedis";
    private static final String DEPRECATED_ANNOTATION = "java.lang.Deprecated";

    private static final String SINGLE_KEY_INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.jedis.shadow" +
            ".JedisShadowSingleKeyInterceptor";
    private static final String VARARG_KEY_INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.jedis.shadow" +
            ".JedisShadowVarArgKeyInterceptor";
    private static final String SRC_DST_KEY_INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.jedis.shadow" +
            ".JedisShadowSrcDstKeyInterceptor";
    private static final String KEYS_VALUES_INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.jedis.shadow" +
            ".JedisShadowKeysValuesInterceptor";

    @Override
    protected ClassMatch enhanceClass() {
        return NameMatch.byName(ENHANCE_CLASS);
    }

    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
    }

    @Override
    public InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[]{
                new InstanceMethodsInterceptPoint() {
                    @Override
                    public ElementMatcher<MethodDescription> getMethodsMatcher() {
                        return ElementMatchers.namedOneOf(Constant.SINGLE_KEY_METHODS)
                                .and(ElementMatchers.takesArgument(0,
                                        ElementMatchers.named("java.lang.String")))
                                .and(ElementMatchers.not(ElementMatchers.isAbstract()))
                                .and(ElementMatchers.not(ElementMatchers.isDefaultMethod()))
                                .and(ElementMatchers.not(
                                        ElementMatchers.isAnnotatedWith(
                                                ElementMatchers.named(DEPRECATED_ANNOTATION))));
                    }

                    @Override
                    public String getMethodsInterceptor() {
                        return SINGLE_KEY_INTERCEPTOR_CLASS;
                    }

                    @Override
                    public boolean isOverrideArgs() {
                        return true;
                    }
                },
                new InstanceMethodsInterceptPoint() {
                    @Override
                    public ElementMatcher<MethodDescription> getMethodsMatcher() {
                        return ElementMatchers.namedOneOf(Constant.VARARG_KEY_METHODS)
                                .and(ElementMatchers.isVarArgs());
                    }

                    @Override
                    public String getMethodsInterceptor() {
                        return VARARG_KEY_INTERCEPTOR_CLASS;
                    }

                    @Override
                    public boolean isOverrideArgs() {
                        return true;
                    }
                },
                new InstanceMethodsInterceptPoint() {
                    @Override
                    public ElementMatcher<MethodDescription> getMethodsMatcher() {
                        return ElementMatchers.namedOneOf(Constant.SRC_DST_KEY_METHODS)
                                .and(ElementMatchers.takesArgument(0, ElementMatchers.named("java.lang.String")))
                                .and(ElementMatchers.takesArgument(1, ElementMatchers.named("java.lang.String")));
                    }

                    @Override
                    public String getMethodsInterceptor() {
                        return SRC_DST_KEY_INTERCEPTOR_CLASS;
                    }

                    @Override
                    public boolean isOverrideArgs() {
                        return true;
                    }
                },
                new InstanceMethodsInterceptPoint() {
                    @Override
                    public ElementMatcher<MethodDescription> getMethodsMatcher() {
                        return ElementMatchers.namedOneOf(Constant.KEYS_VALUES_METHODS)
                                .and(ElementMatchers.isVarArgs());
                    }

                    @Override
                    public String getMethodsInterceptor() {
                        return KEYS_VALUES_INTERCEPTOR_CLASS;
                    }

                    @Override
                    public boolean isOverrideArgs() {
                        return true;
                    }
                }
        };
    }

    @Override
    public StaticMethodsInterceptPoint[] getStaticMethodsInterceptPoints() {
        return new StaticMethodsInterceptPoint[0];
    }
}
