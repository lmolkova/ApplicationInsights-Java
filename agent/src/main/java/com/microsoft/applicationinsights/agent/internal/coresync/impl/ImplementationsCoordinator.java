/*
 * ApplicationInsights-Java
 * Copyright (c) Microsoft Corporation
 * All rights reserved.
 *
 * MIT License
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the ""Software""), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.microsoft.applicationinsights.agent.internal.coresync.impl;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

import com.microsoft.applicationinsights.agent.internal.config.DataOfConfigurationForException;
import org.objectweb.asm.Type;

import com.microsoft.applicationinsights.agent.internal.common.StringUtils;
import com.microsoft.applicationinsights.agent.internal.config.AgentConfiguration;
import com.microsoft.applicationinsights.agent.internal.logger.InternalAgentLogger;
import com.microsoft.applicationinsights.agent.internal.coresync.AgentNotificationsHandler;

/**
 * The class serves as the contact point between injected code and its real implementation.
 * Injected code will notify on various events in the class, calling this class, the class
 * will then delegate the call to the relevant 'client' by consulting the data on the Thread's TLS
 *
 * Note that the called methods should not throw any exception or error otherwise that might affect user's code
 *
 * Created by gupele on 5/6/2015.
 */
public enum ImplementationsCoordinator implements AgentNotificationsHandler {
    INSTANCE;

    public final static String internalName = Type.getInternalName(ImplementationsCoordinator.class);
    public final static String internalNameAsJavaName = "L" + internalName + ";";

    private volatile long maxSqlMaxQueryThresholdInMS = 10000L;
    private volatile long redisThresholdInNS = 10000L * 1000000;

    private static RuntimeExceptionDecider runtimeExceptionDecider;

    private static ConcurrentHashMap<String, RegistrationData> notificationHandlersData = new ConcurrentHashMap<String, RegistrationData>();

    private AgentNotificationsHandler mainHandler;
    private ConcurrentHashMap<String, String> classNameToType = new ConcurrentHashMap<String, String>();

    public void initialize(AgentConfiguration configurationData) {
        maxSqlMaxQueryThresholdInMS = configurationData.getBuiltInConfiguration().getSqlMaxQueryLimitInMS();
        setRedisThresholdInMS(configurationData.getBuiltInConfiguration().getRedisThresholdInMS());
        runtimeExceptionDecider = new RuntimeExceptionDecider();
    }

    /**
     * The data we expect to have for every thread
     */
    public static class RegistrationData {
        public final ClassLoader classLoader;
        public final AgentNotificationsHandler handler;
        public final String key;

        public RegistrationData(ClassLoader classLoader, AgentNotificationsHandler handler, String key) {
            this.classLoader = classLoader;
            this.handler = handler;
            this.key = key;
        }
    }

    public void addClassNameToType(String className, String classType) {
        classNameToType.put(className, classType);
    }

    @Override
    public void httpMethodFinished(String identifier, String method, String correlationId, String uri, String target, int result, long delta) {
        try {
            AgentNotificationsHandler implementation = getImplementation();
            if (implementation != null) {
                implementation.httpMethodFinished(identifier, method, correlationId, uri, target, result, delta);
            }
        } catch (Throwable t) {
        }
    }

    @Override
    public void exceptionCaught(String classAndMethodNames, Throwable throwable) {
        try {
            AgentNotificationsHandler implementation = getImplementation();
            if (implementation != null) {
                implementation.exceptionCaught(classAndMethodNames, throwable);
            }
        } catch (Throwable t) {
        }
    }

    @Override
    public void httpMethodStarted(String classAndMethodName, String url) {
        try {
            AgentNotificationsHandler implementation = getImplementation();
            if (implementation != null) {
                implementation.httpMethodStarted(classAndMethodName, url);
            }
        } catch (Throwable t) {
        }
    }

    @Override
    public void preparedStatementMethodStarted(String classAndMethodName, PreparedStatement statement, String sqlStatement, Object[] args) {
        try {
            AgentNotificationsHandler implementation = getImplementation();
            if (implementation != null) {
                implementation.preparedStatementMethodStarted(classAndMethodName, statement, sqlStatement, args);
            }
        } catch (Throwable t) {
        }
    }

    @Override
    public void methodFinished(String classAndMethodName, long deltaInNS, Object[] args, Throwable throwable) {
        try {
            AgentNotificationsHandler implementation = getImplementation();
            if (implementation != null) {
                implementation.methodFinished(classAndMethodName, deltaInNS, args, throwable);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Override
    public void preparedStatementExecuteBatchMethodStarted(String name, PreparedStatement statement, String sqlStatement, int batchCounter) {
        try {
            AgentNotificationsHandler implementation = getImplementation();
            if (implementation != null) {
                implementation.preparedStatementExecuteBatchMethodStarted(name, statement, sqlStatement, batchCounter);
            }
        } catch (Throwable t) {
        }
    }

    @Override
    public void sqlStatementExecuteQueryPossibleQueryPlan(String name, Statement statement, String sqlStatement) {
        try {
            AgentNotificationsHandler implementation = getImplementation();
            if (implementation != null) {
                implementation.sqlStatementExecuteQueryPossibleQueryPlan(name, statement, sqlStatement);
            }
        } catch (Throwable t) {
        }
    }

    @Override
    public void sqlStatementMethodStarted(String name, Statement statement, String sqlStatement) {
        try {
            AgentNotificationsHandler implementation = getImplementation();
            if (implementation != null) {
                implementation.sqlStatementMethodStarted(name, statement, sqlStatement);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Override
    public void jedisMethodStarted(String name) {
        try {
            AgentNotificationsHandler implementation = getImplementation();
            if (implementation != null) {
                implementation.jedisMethodStarted(name);
            }
        } catch (Throwable t) {
        }
    }

    @Override
    public void methodStarted(String name) {
        try {
            AgentNotificationsHandler implementation = getImplementation();
            String classType;
            if (implementation != null) {
                if (!StringUtils.isNullOrEmpty(name)) {
                    int index = name.lastIndexOf(".");
                    if (index != -1) {
                        String className = name.substring(0, index);
                        classType = classNameToType.get(className);
                        name = name + '#' + classType;
                    }
                }
                implementation.methodStarted(name);
            }
        } catch (Throwable t) {
        }
    }

    @Override
    public void methodFinished(String name, Throwable throwable) {
        try {
            AgentNotificationsHandler implementation = getImplementation();
            if (implementation != null) {
                implementation.methodFinished(name, throwable);
            }
        } catch (Throwable t) {
        }
    }

     @Override
    public void methodFinished(String name, long thresholdInMS) {
        try {
            AgentNotificationsHandler implementation = getImplementation();
            if (implementation != null) {
                implementation.methodFinished(name, thresholdInMS);
            }
        } catch (Throwable t) {
        }
    }

    @Override
    public void exceptionThrown(Exception e) {
        try {
            AgentNotificationsHandler implementation = getImplementation();
            if (implementation == null) {
                return;
            }

            RuntimeExceptionDecider.ValidationResult decision = runtimeExceptionDecider.isValid(e);
            if (!decision.valid) {
                return;
            }

            implementation.exceptionThrown(e, decision.stackSize);

        } catch (Throwable t) {
        }
    }

    @Override
    public void exceptionThrown(Exception e, int i) {
        throw new UnsupportedOperationException();
    }

    /**
     * Will return null since this is only the coordinator and not a real SDK handler.
     * @return null.
     */
    @Override
    public String getName() {
        return null;
    }

    @Override
    public void setCorePoolSize(int i)
    {
        try {
            AgentNotificationsHandler implementation = getImplementation();
            if (implementation != null) {
                implementation.setCorePoolSize(i);
            }
        } catch (Throwable t) {
        }
        
        InternalAgentLogger.INSTANCE.logAlways(InternalAgentLogger.LoggingLevel.INFO, " getCorePoolSize ");
    }
    
    public String register(ClassLoader classLoader, AgentNotificationsHandler handler) {
        try {
            if (handler == null) {
                throw new IllegalArgumentException("AgentNotificationsHandler must be a non-null value");
            }

            String implementationName = handler.getName();
            if (StringUtils.isNullOrEmpty(implementationName)) {
                throw new IllegalArgumentException("AgentNotificationsHandler name must have be a non-null non empty value");
            }

            notificationHandlersData.put(implementationName, new RegistrationData(classLoader, handler, implementationName));

            return implementationName;
        } catch (Throwable throwable) {
            InternalAgentLogger.INSTANCE.error("Exception: '%s'", throwable.getMessage());
            return null;
        }
    }

    public void registerSelf(AgentNotificationsHandler handler) {
        try {
            if (handler == null) {
                throw new IllegalArgumentException("registerSelf: AgentNotificationsHandler must be a non-null value");
            }

            mainHandler = handler;
            InternalAgentLogger.INSTANCE.trace("Setting main handler");
        } catch (Throwable throwable) {
            InternalAgentLogger.INSTANCE.error("Exception: '%s'", throwable.getMessage());
        }
    }

    public void setRedisThresholdInMS(long thresholdInMS) {
        redisThresholdInNS = thresholdInMS * 1000000;
        if (redisThresholdInNS < 0) {
            redisThresholdInNS = 0;
        }
    }

    public long getRedisThresholdInNS() {
        return redisThresholdInNS;
    }

    public long getQueryPlanThresholdInMS() {
        return maxSqlMaxQueryThresholdInMS;
    }

    public void setQueryPlanThresholdInMS(long maxSqlMaxQueryThresholdInMS) {
        if (maxSqlMaxQueryThresholdInMS >= 0) {
            this.maxSqlMaxQueryThresholdInMS = maxSqlMaxQueryThresholdInMS;
        }
    }

    public void setExceptionData    (DataOfConfigurationForException exceptionData) {
        this.runtimeExceptionDecider.setExceptionData(exceptionData);
    }

    private AgentNotificationsHandler getImplementation() {
        String key = AgentTLS.getTLSKey();
        if (key != null && key.length() > 0) {
            RegistrationData implementation = notificationHandlersData.get(key);
            if (implementation != null) {
                return implementation.handler;
            }
        }

        return mainHandler;
    }
}
