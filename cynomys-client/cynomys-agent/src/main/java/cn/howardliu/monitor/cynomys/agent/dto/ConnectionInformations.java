/*
 * Copyright 2008-2016 by Emeric Vernat
 *
 *     This file is part of Java Melody.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.howardliu.monitor.cynomys.agent.dto;

import java.io.Serializable;
import java.util.*;

/**
 * Informations sur l'ouverture d'une connexion jdbc (heure et stack trace). Cet
 * état est celui d'une connexion à un instant t. Les instances sont
 * sérialisables pour pouvoir être transmises au serveur de collecte.
 *
 * @author Emeric Vernat
 */
public class ConnectionInformations implements Serializable {
    private static final long serialVersionUID = -6063966419161604125L;
    private static final String OWN_PACKAGE = ConnectionInformations.class.getName()
            .substring(0, ConnectionInformations.class.getName().lastIndexOf('.'));
    private static final boolean CONNECTIONS_STACK_TRACES_DISABLED = Boolean.FALSE;
    private final long openingTime = System.currentTimeMillis();
    private final StackTraceElement[] openingStackTrace;
    private final long threadId;

    public ConnectionInformations() {
        super();
        final Thread currentThread = Thread.currentThread();
        if (CONNECTIONS_STACK_TRACES_DISABLED) {
            this.openingStackTrace = null;
        } else {
            this.openingStackTrace = currentThread.getStackTrace();
        }
        this.threadId = currentThread.getId();
    }

    public Date getOpeningDate() {
        return new Date(openingTime);
    }

    public List<StackTraceElement> getOpeningStackTrace() {
        if (openingStackTrace == null) {
            return Collections.emptyList();
        }
        final List<StackTraceElement> stackTrace = new ArrayList<>(Arrays.asList(openingStackTrace));

        stackTrace.remove(0);
        while (stackTrace.get(0).getClassName().startsWith(OWN_PACKAGE)) {
            stackTrace.remove(0);
        }
        return stackTrace;
    }

    public long getThreadId() {
        return threadId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[openingDate=" + getOpeningDate() + ", threadId=" + getThreadId() + ']';
    }
}