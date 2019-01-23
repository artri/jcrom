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
import java.io.Serializable;
import java.util.UUID;

import org.jcrom.FlushMode;
import org.jcrom.JcrRuntimeException;
import org.jcrom.Session;
import org.jcrom.SessionEventListener;
import org.jcrom.SessionFactory;
import org.jcrom.Transaction;
import org.jcrom.engine.spi.SessionFactoryImplementor;
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
	
	private boolean autoClear;
	private boolean autoClose;
	private transient boolean disallowOutOfTransactionUpdateOperations;
	private transient boolean discardOnClose;
	
	public SessionImpl(SessionFactoryImpl sessionFactory, SessionCreationOptions options) {
		this.sessionFactory = sessionFactory;
		this.flushMode = FlushMode.AUTO;
		this.autoClear = options.shouldAutoClear();
		this.autoClose = options.shouldAutoClose();
		this.disallowOutOfTransactionUpdateOperations = !sessionFactory.getSessionFactoryOptions().isAllowOutOfTransactionUpdateOperations();
		this.discardOnClose = sessionFactory.getSessionFactoryOptions().isReleaseResourcesOnCloseEnabled();
		
		// getTransactionCoordinator().pulse();
	}

	//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	//~~~~~~~~~~ SharedSessionContract
	
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.Session#JcrRuntimeException()
	 */	
	@Override
	public void close() throws JcrRuntimeException {
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
	
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.Session#isOpened()
	 */		
	@Override
	public boolean isOpened() {
		checkSessionFactoryOpen();
		pulseTransactionCoordinator();
		try {
			return !isClosed();
		} catch(JcrRuntimeException e) {
			throw e;
		}
	}

	protected void checkSessionFactoryOpen() {
		if (!getSessionFactory().isOpened()) {
			LOGGER.debug("Forcing Session closed as SessionFactory has been closed");
			setClosed();
		}
	}
	
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.Session#isClosed()
	 */
	@Override
	public boolean isClosed() {
		return closed || sessionFactory.isClosed();
	}
	
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.Session#beginTransaction()
	 */	
	@Override
	public Transaction beginTransaction() {
		checkOpen();
		
		Transaction transaction = getTransaction();
		transaction.begin();
		return transaction;
	}
	
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.Session#getTransaction()
	 */	
	@Override
	public Transaction getTransaction() {
		return accessTransaction();
	}
	
	//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	//~~~~~~~~~~ Session 	
	
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.Session#getSessionFactory()
	 */	
	@Override
	public SessionFactoryImplementor getSessionFactory() {
		return this.sessionFactory;
	}

	@Override
	public void flush() throws JcrRuntimeException {
		checkOpen();
		doFlush();
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
	
	@Override
	public void setFlushMode(FlushMode flushMode) {
		checkOpen();
		this.flushMode = flushMode;
	}

	@Override
	public FlushMode getFlushMode() {
		return flushMode;
	}
		
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.Session#isDirty()
	 */
	@Override
	public boolean isDirty() throws JcrRuntimeException {
		checkOpen();
		LOGGER.debug("Checking session dirtiness");
		if (actionQueue.areInsertionsOrDeletionsQueued() ) {
			log.debug( "Session dirty (scheduled updates and insertions)" );
			return true;
		}
		DirtyCheckEvent event = new DirtyCheckEvent( this );
		for (DirtyCheckEventListener listener : listeners(EventType.DIRTY_CHECK)) {
			listener.onDirtyCheck(event);
		}
		return event.isDirty();
	}

	@Override
	public boolean isDefaultReadOnly() {
		return persistenceContext.isDefaultReadOnly();
	}

	@Override
	public void setDefaultReadOnly(boolean defaultReadOnly) {
		persistenceContext.setDefaultReadOnly( defaultReadOnly );
	}
	
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.Session#addEventListeners()
	 */		
	@Override
	public void addEventListeners(SessionEventListener... listeners) {
		getEventListenerManager().addListener( listeners );
	}	
	
	// not for internal use:
	@Override
	public Serializable getIdentifier(Object object) throws JcrRuntimeException {
		checkOpen();
		checkTransactionSynchStatus();
		if (object instanceof HibernateProxy) {
			LazyInitializer li = ( (HibernateProxy) object ).getHibernateLazyInitializer();
			if ( li.getSession() != this ) {
				throw new TransientObjectException( "The proxy was not associated with this session" );
			}
			return li.getIdentifier();
		}
		else {
			EntityEntry entry = persistenceContext.getEntry( object );
			if ( entry == null ) {
				throw new TransientObjectException( "The instance was not associated with this session" );
			}
			return entry.getId();
		}
	}	
	
	@Override
	public boolean isReadOnly(Object entityOrProxy) {
		checkOpen();
//		return persistenceContext.isReadOnly(entityOrProxy);
		LOGGER.warn("persistenceContext.isReadOnly not implemented yet");
		return false;
	}

	@Override
	public void setReadOnly(Object entity, boolean readOnly) {
		checkOpen();
		//persistenceContext.setReadOnly(entity, readOnly);
		LOGGER.warn("persistenceContext.setReadOnly not implemented yet");
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
	public boolean isOpenOrWaitingForAutoClose() {
		return !isClosed() || waitingForAutoClose;
	}
	
	@Override
	public void checkOpen(boolean markForRollbackIfClosed) {
		if (isClosed()) {
			if (markForRollbackIfClosed /*&& transactionCoordinator.isTransactionActive()*/ ) {
				markForRollbackOnly();
			}
			throw new IllegalStateException("Session is closed");
		}
	}
	
	protected void setClosed() {
		closed = true;
		waitingForAutoClose = false;
		cleanupOnClose();
	}
	
	protected void cleanupOnClose() {
		// nothing to do in base impl, here for SessionImpl hook
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
	public void markForRollbackOnly() {
		try {
			accessTransaction().markRollbackOnly();
		} catch (Exception ignore) { }
	}
	
	@Override
	public long getTransactionStartTimestamp() {
		//return getCacheTransactionSynchronization().getCurrentTransactionStartTimestamp();
		throw new UnsupportedOperationException("Not implemented yet");
	}
	
	@Override
	public boolean isTransactionInProgress() {
		if (waitingForAutoClose) {
			return getSessionFactory().isOpened() /*&& transactionCoordinator.isTransactionActive()*/;
		}
		return !isClosed() /*&& transactionCoordinator.isTransactionActive()*/;
	}
	
	@Override
	public Transaction accessTransaction() {	
		if (null == this.currentJcrTransaction) {
			this.currentJcrTransaction = new TransactionImpl(this);
		}
		
		if (!isClosed() || (waitingForAutoClose && getSessionFactory().isOpened())) {
			LOGGER.warn("transactionCoordinator.pulse() not implemented yet");
			//transactionCoordinator.pulse();
		}
		return this.currentJcrTransaction;
	}

	protected Transaction getCurrentTransaction() {
		return this.currentJcrTransaction;
	}
	
	protected void pulseTransactionCoordinator() {
		if (!isClosed()) {
			LOGGER.warn("transactionCoordinator.pulse() not implemented yet");
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
