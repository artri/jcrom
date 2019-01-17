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
package org.jcrom.context.internal;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.Session;

import org.jcrom.JcrRuntimeException;
import org.jcrom.SessionFactory;
import org.jcrom.context.CurrentSessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThreadLocalSessionContext implements CurrentSessionContext {
	private static final long serialVersionUID = 5784230901302535831L;
	private static final Logger LOGGER = LoggerFactory.getLogger(ThreadLocalSessionContext.class);
	
	private final SessionFactory sessionFactory;
	
	/**
	 * A ThreadLocal maintaining current sessions for the given execution thread.
	 * The actual ThreadLocal variable is a java.util.Map to account for
	 * the possibility for multiple SessionFactory instances being used during execution
	 * of the given thread.
	 */
	private static final ThreadLocal<Map<SessionFactory, Session>> CONTEXT_TL = ThreadLocal.withInitial( HashMap::new );
	

	/**
	 * Constructs a ThreadLocalSessionContext
	 *
	 * @param sessionFactory The factory this context will service
	 */	
	public ThreadLocalSessionContext(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	/**
	 * Access to the SessionFactory
	 *
	 * @return The SessionFactory being serviced by this context
	 */
	public SessionFactory getSessionFactory() {
		return sessionFactory;
	}

	/** 
	 * (non-Javadoc)
	 * @see org.jcrom.context.CurrentSessionContext#currentSession()
	 */
	@Override
	public Session getCurrentSession() throws JcrRuntimeException {
		Session current = existingSession(getSessionFactory());
		if (null == current) {
			current = buildOrObtainSession();
			// register a cleanup sync
			//current.getTransaction().registerSynchronization(buildCleanupSynch());
			// wrap the session in the transaction-protection proxy
//			if (needsWrapping(current) ) {
//				current = wrap(current);
//			}
			
			doBind(current, getSessionFactory());
		} else {
			validateExistingSession(current);
		}
		return current;
	}
	

//	private boolean needsWrapping(Session session) {
//		// try to make sure we don't wrap and already wrapped session
//		if (Proxy.isProxyClass(session.getClass())) {
//			final InvocationHandler invocationHandler = Proxy.getInvocationHandler(session);
//			if (invocationHandler != null && TransactionProtectionWrapper.class.isInstance(invocationHandler)) {
//				return false;
//			}
//		}
//		return true;
//	}
//	
//	protected Session wrap(Session session) {
//		final TransactionProtectionWrapper wrapper = new TransactionProtectionWrapper( session );
//		final Session wrapped = (Session) Proxy.newProxyInstance(
//				Session.class.getClassLoader(),
//				SESSION_PROXY_INTERFACES,
//				wrapper
//		);
//		// yick!  need this for proper serialization/deserialization handling...
//		wrapper.setWrapped( wrapped );
//		return wrapped;
//	}
	
	/**
	 * Strictly provided for sub-classing purposes; specifically to allow long-session support.
	 * 
	 * This implementation always just opens a new session.
	 *
	 * @return the built or (re)obtained session.
	 */	
	protected Session buildOrObtainSession() {
		return getSessionFactory().openSession();
	}
	
	protected CleanupSync buildCleanupSynch() {
		return new CleanupSync(getSessionFactory());
	}
	
	protected void validateExistingSession(Session session) {
		return;
	}
	
	private static Session existingSession(SessionFactory factory) {
		return sessionMap().get(factory);
	}

	protected static Map<SessionFactory, Session> sessionMap() {
		return CONTEXT_TL.get();
	}

	/*
	 * Associates the given session with the current thread of execution.
	 *
	 * @param session The session to bind.
	 */
//	public static void bind(Session session) {
//		final SessionFactory factory = session.getSessionFactory();
//		doBind(session, factory);
//	}
	
	/**
	 * Disassociates a previously bound session from the current thread of execution.
	 *
	 * @param factory The factory for which the session should be unbound.
	 * @return The session which was unbound.
	 */
	public static void unbind(SessionFactory sessionFactory) {
		doUnbind(sessionFactory, true);
	}
	
	@SuppressWarnings({"unchecked"})
	private static void doBind(Session session, SessionFactory sessionFactory) {
		Session orphanedPreviousSession = sessionMap().put(sessionFactory, session);
		terminateOrphanedSession(orphanedPreviousSession, sessionFactory);
	}
		
	private static void doUnbind(SessionFactory sessionFactory, boolean releaseMapIfEmpty) {
		final Map<SessionFactory, Session> sessionMap = sessionMap();
		final Session session = sessionMap.remove(sessionFactory);
		if (releaseMapIfEmpty && sessionMap.isEmpty()) {
			//Do not use set(null) as it would prevent the initialValue to be invoked again in case of need.
			CONTEXT_TL.remove();
		}
		terminateOrphanedSession(session, sessionFactory);
	}
	
	private static void terminateOrphanedSession(Session orphan, SessionFactory sessionFactory) {
		if (orphan != null ) {
			LOGGER.debug("Terminatinng orphaned session");
			try {
				//TODO: perform internal stuff with orphaned session
				
//				final Transaction orphanTransaction = orphan.getTransaction();
//				if (orphanTransaction != null && orphanTransaction.getStatus() == TransactionStatus.ACTIVE ) {
//					try {
//						orphanTransaction.rollback();
//					}
//					catch( Throwable t ) {
//						LOG.debug( "Unable to rollback transaction for orphaned session", t );
//					}
//				}
			} finally {
				try {
					// close the orphaned session
					sessionFactory.releaseSession(orphan);
				} catch( Throwable t ) {
					LOGGER.debug( "Unable to close orphaned session", t );
				}
			}
		}
	}
	

	/**
	 * Transaction sync used for cleanup of the internal session map.
	 */
	protected static class CleanupSync implements Serializable /*, javax.transaction.Synchronization*/ {
		protected final SessionFactory factory;

		public CleanupSync(SessionFactory factory) {
			this.factory = factory;
		}

		//@Override
		public void beforeCompletion() {
		}

		//@Override
		public void afterCompletion(int i) {
			unbind( factory );
		}
	}	
}	

