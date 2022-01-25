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

package org.apache.skywalking.apm.plugin.jedis.shadow;

public class Constant {
    public final static String PREFIX = "shadow_";

    // the first arg of these methods is string key
    public static final String[] SINGLE_KEY_METHODS = new String[]{"get", "set", "getDel", "getEx", "exists", "del",
            "unlink", "type", "expire", "expireAt", "ttl", "move", "getSet", "setnx", "decrBy", "decr", "incrBy",
            "incrByFloat", "incr", "append", "substr", "hset", "hset", "hget", "hsetnx", "hmset", "hmget", "hincrBy",
            "hincrByFloat", "hexists", "hdel", "hlen", "hkeys", "hvals", "hgetAll", "hrandfield", "hrandfield",
            "hrandfieldWithValues", "rpush", "lpush", "llen", "lrange", "ltrim", "lindex", "lset", "lrem", "lpop",
            "lpos", "rpop", "sadd", "smembers", "srem", "spop", "scard", "sismember", "srandmember", "srandmember",
            "zadd", "zaddIncr", "zrange", "zrem", "zincrby", "zrank", "zrevrank", "zrevrange", "zrangeWithScores",
            "zrevrangeWithScores", "zrandmember", "zrandmemberWithScores", "zcard", "zscore", "zmscore", "zpopmax",
            "zpopmin", "pexpire", "pexpireAt", "pttl", "psetex", "strlen", "hstrlen", "geohash", "geodist", "geoadd",
            "pfmerge", "georadius", "getrange", "setrange", "bitpos", "getbit", "setbit", "linsert", "echo", "rpushx",
            "persist", "lpushx", "zremrangeByLex", "zrevrangeByLex", "zrangeByLex", "zlexcount"};

    // arg of these methods is String...
    public static final String[] VARARG_KEY_METHODS = new String[]{"del", "exists", "unlink", "mget",
            "sinter", "sunion", "sdiff", "zdiff", "zdiffWithScores", "watch"};

    // the first and second arg of these methods are srcKey, dstKey
    public static final String[] SRC_DST_KEY_METHODS = new String[]{"rename", "renamenx", "copy", "rpoplpush",
            "brpoplpush", "smove", "lmove", "blmove"};

    // args of these methods is in format of (key,value,key,value, ...)
    public static final String[] KEYS_VALUES_METHODS = new String[]{"mset", "msetnx"};
}
