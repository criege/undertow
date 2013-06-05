/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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

package io.undertow.server.session;

import io.undertow.UndertowMessages;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.HttpHandlers;
import io.undertow.server.handlers.ResponseCodeHandler;

/**
 * Handler that attaches the session to the request.
 * <p/>
 * This handler is also the place where session cookie configuration properties are configured.
 * <p/>
 * note: this approach is not used by Servlet, which has its own session handlers
 *
 * @author Stuart Douglas
 */
public class SessionAttachmentHandler implements HttpHandler {

    private volatile HttpHandler next = ResponseCodeHandler.HANDLE_404;

    private volatile SessionManager sessionManager;

    /**
     * The config that is used for this session. It is possible for multiple session to be attached to the same
     * HTTP request. Handlers that wish to share a session must also share the session configuration.
     */
    private final SessionConfig sessionConfig;

    public SessionAttachmentHandler(final SessionManager sessionManager, final SessionConfig sessionConfig) {
        this.sessionConfig = sessionConfig;
        if (sessionManager == null) {
            throw UndertowMessages.MESSAGES.sessionManagerMustNotBeNull();
        }
        this.sessionManager = sessionManager;
    }

    public SessionAttachmentHandler(final HttpHandler next, final SessionManager sessionManager, final SessionConfig sessionConfig) {
        this.sessionConfig = sessionConfig;
        if (sessionManager == null) {
            throw UndertowMessages.MESSAGES.sessionManagerMustNotBeNull();
        }
        this.next = next;
        this.sessionManager = sessionManager;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        if (sessionManager == null) {
            throw UndertowMessages.MESSAGES.sessionManagerMustNotBeNull();
        }
        exchange.putAttachment(SessionManager.ATTACHMENT_KEY, sessionManager);
        sessionManager.getSession(exchange, sessionConfig);
        final UpdateLastAccessTimeListener handler = new UpdateLastAccessTimeListener(sessionConfig, sessionManager);
        exchange.addExchangeCompleteListener(handler);
        next.handleRequest(exchange);

    }


    public HttpHandler getNext() {
        return next;
    }

    public SessionAttachmentHandler setNext(final HttpHandler next) {
        HttpHandlers.handlerNotNull(next);
        this.next = next;
        return this;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public SessionAttachmentHandler setSessionManager(final SessionManager sessionManager) {
        if (sessionManager == null) {
            throw UndertowMessages.MESSAGES.sessionManagerMustNotBeNull();
        }
        this.sessionManager = sessionManager;
        return this;
    }

    private static class UpdateLastAccessTimeListener implements ExchangeCompletionListener {

        private final SessionConfig sessionConfig;
        private final SessionManager sessionManager;

        private UpdateLastAccessTimeListener(final SessionConfig sessionConfig, final SessionManager sessionManager) {
            this.sessionConfig = sessionConfig;
            this.sessionManager = sessionManager;
        }

        @Override
        public void exchangeEvent(final HttpServerExchange exchange, final NextListener next) {
            try {
                final Session session = sessionManager.getSession(exchange, sessionConfig);
                if (session != null) {
                    session.requestDone(exchange);
                }
            } finally {
                next.proceed();
            }
        }
    }

}
