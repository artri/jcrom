/**
 * This file is part of the JCROM project.
 * Copyright (C) 2008-2019 - All rights reserved.
 * Authors: Olafur Gauti Gudmundsson, Nicolas Dos Santos
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jcrom.internal;

import java.io.IOException;
import java.util.UUID;

import org.jcrom.FlushMode;
import org.jcrom.JcrRuntimeException;
import org.jcrom.Session;
import org.jcrom.SessionFactory;
import org.jcrom.Transaction;
import org.jcrom.engine.spi.SessionImplementor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of a Session.
 * <p/>
 * Exposes two interfaces:<ul>
 * <li>{@link org.jcrom.Session} to the application</li>
 * </ul>
 * <p/>
 * This class is not thread-safe.
 */
public class SessionImpl implements SessionImplementor {
	private static final long serialVersionUID = -8254588340258340383L;
	private static final Logger LOGGER = LoggerFactory.getLogger(SessionImpl.class);
	
	private transient SessionFactoryImpl sessionFactory;
	private String uuid;
	private FlushMode flushMode;
	
	private Transaction currentJcrTransaction;
	private boolean waitingForAutoClose;
	private boolean closed;
	
	public SessionImpl(SessionFactoryImpl sessionFactory, SessionCreationOptions options) {
		this.sessionFactory = sessionFactory;
		this.flushMode = FlushMode.AUTO;
	}

	@Override
	public SessionFactory getSessionFactory() {
		return this.sessionFactory;
	}
	
	/**
	 * @return the uuid
	 */
	public String getUUID() {
		if (null == this.uuid) {
			// lazy initialization
			this.uuid = UUID.randomUUID().toString();
		}
		return uuid;
	}

	@Override
	public boolean isOpened() {
		return !isClosed();
	}

	@Override
	public boolean isClosed() {
		return closed || sessionFactory.isClosed();
	}

	@Override
	public boolean isOpenOrWaitingForAutoClose() {
		return !isClosed() || waitingForAutoClose;
	}
	
	@Override
	public void checkOpen(boolean markForRollbackIfClosed) {
		if (isClosed()) {
			if (markForRollbackIfClosed /*&& transactionCoordinator.isTransactionActive()*/ ) {
				markForRollbackOnly();
			}
			throw new IllegalStateException( "Session is closed" );
		}
	}
	
	@Override
	public void close() throws IOException {
		if (closed && !waitingForAutoClose) {
			LOGGER.trace("Already closed");
			return;
		}
		
		LOGGER.trace("Closing session {}", getUUID());
		
		if (null != currentJcrTransaction) {
			currentJcrTransaction.invalidate();
		}
		
		setClosed();
	}
	
	protected void setClosed() {
		closed = true;
		waitingForAutoClose = false;
		cleanupOnClose();
	}
	
	protected void cleanupOnClose() {
		// nothing to do in base impl, here for SessionImpl hook
	}
	
	@Override
	public void clear() {
		checkOpen();

		// Do not call checkTransactionSynchStatus() here -- if a delayed
		// afterCompletion exists, it can cause an infinite loop.
		pulseTransactionCoordinator();

		try {
			internalClear();
		}
		catch (RuntimeException e) {
			throw new JcrRuntimeException(e);
		}
	}

	private void internalClear() {
//		persistenceContext.clear();
//		actionQueue.clear();
//
//		final ClearEvent event = new ClearEvent(this);
//		for (ClearEventListener listener : listeners(EventType.CLEAR) ) {
//			listener.onClear( event );
//		}
	}
	
	@Override
	public void flush() throws JcrRuntimeException {
		checkOpen();
		doFlush();
	}

	@Override
	public void setFlushMode(FlushMode flushMode) {
		checkOpen();
		this.flushMode = flushMode;
	}

	@Override
	public FlushMode getFlushMode() {
		return flushMode;
	}
	
	@Override
	public Transaction beginTransaction() {
		checkOpen();
		
		Transaction transaction = getTransaction();
		transaction.begin();
		return transaction;
	}

	@Override
	public void markForRollbackOnly() {
		try {
			accessTransaction().markRollbackOnly();
		} catch (Exception ignore) { }
	}
	
	@Override
	public boolean isTransactionInProgress() {
		if ( waitingForAutoClose ) {
			return getSessionFactory().isOpened() /*&& transactionCoordinator.isTransactionActive()*/;
		}
		return !isClosed() /*&& transactionCoordinator.isTransactionActive()*/;
	}
	
	@Override
	public Transaction getTransaction() {
		return accessTransaction();
	}

	@Override
	public Transaction accessTransaction() {	
		if (null == this.currentJcrTransaction) {
			this.currentJcrTransaction = new TransactionImpl(this);
		}
		
		if (isOpened() || (waitingForAutoClose && getSessionFactory().isOpened())) {
			pulseTransactionCoordinator();
		}
		return this.currentJcrTransaction;
	}

	protected Transaction getCurrentTransaction() {
		return this.currentJcrTransaction;
	}
	
	protected void pulseTransactionCoordinator() {
		if (!isClosed()) {
			//transactionCoordinator.pulse();
		}
	}
	
	private void doFlush() {
		checkTransactionNeeded();
		pulseTransactionCoordinator();

		try {
			if (persistenceContext.getCascadeLevel() > 0 ) {
				throw new JcrRuntimeException("Flush during cascade is dangerous");
			}

			FlushEvent flushEvent = new FlushEvent(this);
			for (FlushEventListener listener : listeners(EventType.FLUSH) ) {
				listener.onFlush( flushEvent );
			}
		} catch (RuntimeException e) {
			throw new JcrRuntimeException(e.getMessage(), e);
		}
	}
	
	private void checkTransactionNeeded() {
		if (disallowOutOfTransactionUpdateOperations && !isTransactionInProgress() ) {
			throw new TransactionRequiredException( "no transaction is in progress" );
		}
	}
}
