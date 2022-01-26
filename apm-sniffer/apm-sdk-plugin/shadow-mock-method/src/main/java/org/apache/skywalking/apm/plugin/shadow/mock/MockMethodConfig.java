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

package org.apache.skywalking.apm.plugin.shadow.mock;

import org.apache.skywalking.apm.util.StringUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MockMethodConfig {

    private static Map<MethodInfo, MethodInfo> INSTANCE = null;

    private MockMethodConfig() {
        
    }

    public static Map<MethodInfo, MethodInfo> getInstance() {
        if (INSTANCE == null) {
            synchronized (MockMethodConfig.class) {
                if (INSTANCE == null) {
                    INSTANCE = getReplacedMethods();
                }
            }
        }
        return Collections.unmodifiableMap(INSTANCE);
    }

    private static Map<MethodInfo, MethodInfo> getReplacedMethods() {
        Map<MethodInfo, MethodInfo> rms = new HashMap<>();
        String replaceMethods = ShadowMockMethodConfig.Plugin.Mock.METHOD_REPLACEMENT;
        if (StringUtil.isEmpty(replaceMethods)) {
            return rms;
        }
        String[] methods = replaceMethods.split(",");
        for (String method : methods) {
            String[] clazzMethods = method.split(":");
            if (clazzMethods == null || clazzMethods.length < 2) {
                continue;
            }

            String[] srcClassMethod = clazzMethods[0].split("#");
            if (srcClassMethod == null || StringUtil.isBlank(srcClassMethod[0])
                    || StringUtil.isBlank(srcClassMethod[1])) {
                continue;
            }
            String[] dstClassMethod = clazzMethods[1].split("#");
            if (dstClassMethod == null || StringUtil.isBlank(dstClassMethod[0])
                    || StringUtil.isBlank(dstClassMethod[1])) {
                continue;
            }
            MethodInfo srcMi = new MethodInfo(srcClassMethod[0].trim(), srcClassMethod[1].trim());
            MethodInfo dstMi = new MethodInfo(dstClassMethod[0].trim(), dstClassMethod[1].trim());
            rms.put(srcMi, dstMi);
        }
        return rms;
    }
}
