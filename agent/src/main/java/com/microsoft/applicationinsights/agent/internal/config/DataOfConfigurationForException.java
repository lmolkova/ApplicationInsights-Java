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

package com.microsoft.applicationinsights.agent.internal.config;

import java.util.HashSet;

/**
 * Created by gupele on 8/18/2016.
 */
public final class DataOfConfigurationForException {
    private boolean enabled = false;
    private int stackSize = Integer.MAX_VALUE;
    private HashSet<String> suppressedExceptions = new HashSet<String>();
    private HashSet<String> validPathForExceptions = new HashSet<String>();

    public int getStackSize() {
        return stackSize;
    }

    public void setStackSize(int stackSize) {
        this.stackSize = stackSize;
    }

    public HashSet<String> getSuppressedExceptions() {
        return suppressedExceptions;
    }

    public void setSuppressedExceptions(HashSet<String> suppressedExceptions) {
        this.suppressedExceptions = suppressedExceptions;
    }

    public HashSet<String> getValidPathForExceptions() {
        return validPathForExceptions;
    }

    public void setValidPathForExceptions(HashSet<String> validPathForExceptions) {
        this.validPathForExceptions = validPathForExceptions;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
