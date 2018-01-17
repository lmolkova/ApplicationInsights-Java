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

package com.microsoft.applicationinsights.agent.internal.coresync;

import java.sql.PreparedStatement;
import java.sql.Statement;

/**
 * The Agent will inject code in the user's code that will be activated on predefined methods.
 * Those methods will be directed to the Agent that will need to work with that information by
 * delegating the calls to 'clients', those clients will need to implement the following interface.
 *
 * Created by gupele on 5/6/2015.
 */
public interface AgentNotificationsHandler {
    /**
     * The handler's logical name
     * @return The handler's logical name
     */
    String getName();

    /**
     * The method that is called when an exception is caught in the instrumented code.
     * @param classAndMethodNames The name of the class and method separated by '.'
     * @param throwable The throwable that was caught
     */
    void exceptionCaught(String classAndMethodNames, Throwable throwable);

    /**
     * Called when an instrumented class and method that deals with sending URLs.
     * @param classAndMethodNames The name of the class and method separated by '.'
     * @param url The url that is being sent
     */
    void httpMethodStarted(String classAndMethodNames, String url);

    /**
     * A HTTP call that was ended
     * @param identifier - HTTP identifier, i.e. the callser
     * @param method - 'GET'/'PUT' etc.
     * @param correlationId - The correlation Id for the associated HTTP call
     * @param uri - The falled uri
     * @param target - The target resource of the HTTP call
     * @param result - The result
     * @param delta - Time it took to do the call
     */
    void httpMethodFinished(String identifier, String method, String correlationId, String uri, String target, int result, long delta);

    /**
     * Called when an java.sql.Statement concrete class is called
     * @param classAndMethodNames The name of the class and method separated by '.'
     * @param statement The class that implements the java.sql.Statement interface
     * @param sqlStatement The sql statement that is used
     */
    void sqlStatementMethodStarted(String classAndMethodNames, Statement statement, String sqlStatement);

    /**
     * Called when an java.sql.Statement concrete class is called for its 'executeQuery'
     * method, this method is called by classes that can create an EXPLAIN query from the current sql statement
     * @param classAndMethodNames The name of the class and method separated by '.'
     * @param statement The class that implements the java.sql.Statement interface
     * @param sqlStatement The sql statement that is used
     */
    void sqlStatementExecuteQueryPossibleQueryPlan(String classAndMethodNames, Statement statement, String sqlStatement);

    /**
     * Called when an java.sql.PreparedStatement concrete class is called
     * @param classAndMethodNames The name of the class and method separated by '.'
     * @param statement The class that implements the java.sql.PreparedStatement interface
     * @param sqlStatement The sql statement that is used
     * @param args The values for the statement
     */
    void preparedStatementMethodStarted(String classAndMethodNames, PreparedStatement statement, String sqlStatement, Object[] args);

    /**
     * Called when an java.sql.PreparedStatement concrete class 'executeBatch' method is called
     * @param classAndMethodNames The name of the class and method separated by '.'
     * @param statement The class that implements the java.sql.PreparedStatement interface
     * @param sqlStatement The sql statement that is used
     * @param batchCounter The number of batches sent to the server
     */
    void preparedStatementExecuteBatchMethodStarted(String classAndMethodNames, PreparedStatement statement, String sqlStatement, int batchCounter);

     /**
     * Called before methods in the Jedis client class are executed.
     * @param classAndMethodNames The name of the class and method separated by '.'
     */
    void jedisMethodStarted(String classAndMethodNames);

    /**
     * A 'regular' method enter. Non HTTP/SQL method
     * @param classAndMethodNames The name of the class and method separated by '.'
     */
    void methodStarted(String classAndMethodNames);

    /**
     * Marks a method finish with an exception
     * @param classAndMethodNames The name of the class and method separated by '.'
     * @param throwable The throwable that was caught
     */
    void methodFinished(String classAndMethodNames, Throwable throwable);

    /**
     * Marks a method finish without exception
     * @param classAndMethodNames The name of the class and method separated by '.'
     * @param thresholdInMS The threshold in MS that this method should get to in order to be sent
     */
    void methodFinished(String classAndMethodNames, long thresholdInMS);

    /**
     * An instrumented method might call this method to signal end of method
     * @param classAndMethodNames The name of the class and method separated by '.'
     * @param deltaInNS The amount of time that the method was executed in nano seconds
     * @param args The method's arguments, that is an optional value and can be null
     * @param throwable A possible exception that was thrown and caused the method to finish
     */
    void methodFinished(String classAndMethodNames, long deltaInNS, Object[] args, Throwable throwable);

    void exceptionThrown(Exception e);

    void exceptionThrown(Exception e, int i);
    
    void setCorePoolSize(int i);
}
