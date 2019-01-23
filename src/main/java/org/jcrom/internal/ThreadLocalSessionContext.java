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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.jcrom.JcrRuntimeException;
import org.jcrom.Session;
import org.jcrom.SessionFactory;
import org.jcrom.Transaction;
import org.jcrom.TransactionStatus;
import org.jcrom.engine.spi.SessionFactoryImplementor;
import org.jcrom.engine.spi.SessionImplementor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThreadLocalSessionContext implements CurrentSessionContext {
	private static final long serialVersionUID = 5784230901302535831L;
	private static final Logger LOGGER = LoggerFactory.getLogger(ThreadLocalSessionContext.class);
	
	private static final Class[] SESSION_PROXY_INTERFACES = new Class[] {
		Session.class,
		SessionImplementor.class,
//		EventSource.class,
//		LobCreationContext.class
	};
	private final SessionFactoryImplementor sessionFactory;
	
	/**
	 * A ThreadLocal maintaining current sessions for the given execution thread.
	 * The actual ThreadLocal variable is a java.util.Map to account for
	 * the possibility for multiple SessionFactory instances being used during execution
	 * of the given thread.
	 */
	private static final ThreadLocal<Map<SessionFactory, Session>> CONTEXT_TL = ThreadLocal.withInitial(HashMap::new);
	
	/**
	 * Constructs a ThreadLocalSessionContext
	 *
	 * @param sessionFactory The factory this context will service
	 */	
	public ThreadLocalSessionContext(SessionFactoryImplementor sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	/**
	 * Access to the SessionFactory
	 *
	 * @return The SessionFactory being serviced by this context
	 */
	public SessionFactoryImplementor getSessionFactory() {
		return sessionFactory;
	}

	/** 
	 * (non-Javadoc)
	 * @see org.jcrom.internal.CurrentSessionContext#currentSession()
	 */
	@Override
	public Session getCurrentSession() throws JcrRuntimeException {
		Session current = existingSession(getSessionFactory());
		if (null == current) {
			current = buildOrObtainSession();
			// register a cleanup sync
			current.getTransaction().registerSynchronization(buildCleanupSynch());
			// wrap the session in the transaction-protection proxy
			if (needsWrapping(current) ) {
				current = wrap(current);
			}
			
			doBind(current, getSessionFactory());
		} else {
			validateExistingSession(current);
		}
		return current;
	}
	

	private boolean needsWrapping(Session session) {
		// try to make sure we don't wrap and already wrapped session
		if (Proxy.isProxyClass(session.getClass())) {
			final InvocationHandler invocationHandler = Proxy.getInvocationHandler(session);
			if (invocationHandler != null && TransactionProtectionWrapper.class.isInstance(invocationHandler)) {
				return false;
			}
		}
		return true;
	}
	
	protected Session wrap(Session session) {
		final TransactionProtectionWrapper wrapper = new TransactionProtectionWrapper( session );
		final Session wrapped = (Session) Proxy.newProxyInstance(
				Session.class.getClassLoader(),
				SESSION_PROXY_INTERFACES,
				wrapper
		);
		// yick!  need this for proper serialization/deserialization handling...
		wrapper.setWrapped( wrapped );
		return wrapped;
	}
	
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

	/**
	 * Associates the given session with the current thread of execution.
	 *
	 * @param session The session to bind.
	 */
	public static void bind(Session session) {
		final SessionFactory factory = session.getSessionFactory();
		doBind(session, factory);
	}
	
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
		if (orphan == null) {
			return;
		}
		
		LOGGER.debug("Terminatinng orphaned session");
		try {
			final Transaction orphanTransaction = orphan.getTransaction();
			if (orphanTransaction != null && orphanTransaction.getStatus() == TransactionStatus.ACTIVE ) {
				try {
					orphanTransaction.rollback();
				}
				catch(Throwable t) {
					LOGGER.debug( "Unable to rollback transaction for orphaned session", t );
				}
			}
		} finally {
			try {
				// close the orphaned session
				orphan.close();
			} catch(Throwable t) {
				LOGGER.debug("Unable to close orphaned session", t);
			}
		}
	}
	

	/**
	 * Transaction sync used for cleanup of the internal session map.
	 */
	protected static class CleanupSync implements Serializable, javax.transaction.Synchronization {
		protected final SessionFactory factory;

		public CleanupSync(SessionFactory factory) {
			this.factory = factory;
		}

		//@Override
		public void beforeCompletion() {
		}

		//@Override
		public void afterCompletion(int i) {
			unbind(factory);
		}
	}
	
	private class TransactionProtectionWrapper implements InvocationHandler, Serializable {
		private final Session realSession;
		private Session wrappedSession;

		public TransactionProtectionWrapper(Session realSession) {
			this.realSession = realSession;
		}

		@Override
		@SuppressWarnings("SimplifiableIfStatement")
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			final String methodName = method.getName();

			// first check methods calls that we handle completely locally:
			if ("equals".equals(methodName) && method.getParameterCount() == 1) {
				if (args[0] == null || !Proxy.isProxyClass(args[0].getClass())) {
					return false;
				}
				return this.equals(Proxy.getInvocationHandler(args[0]));
			}
			else if ("hashCode".equals(methodName) && method.getParameterCount() == 0) {
				return this.hashCode();
			}
			else if ("toString".equals(methodName) && method.getParameterCount() == 0) {
				return String.format(Locale.ROOT, "ThreadLocalSessionContext.TransactionProtectionWrapper[%s]", realSession);
			}

			// then check method calls that we need to delegate to the real Session
			try {
				// If close() is called, guarantee unbind()
				if ("close".equals(methodName)) {
					unbind(realSession.getSessionFactory());
				}
				else if ("isOpened".equals(methodName)
						|| "getListeners".equals(methodName)) {
					// allow these to go through the the real session no matter what
					LOGGER.trace("Allowing invocation [{}] to proceed to real session", methodName);
				}
				else if (!realSession.isOpened()) {
					// essentially, if the real session is closed allow any
					// method call to pass through since the real session
					// will complain by throwing an appropriate exception;
					// NOTE that allowing close() above has the same basic effect,
					//   but we capture that there simply to doAfterTransactionCompletion the unbind...
					LOGGER.trace("Allowing invocation [{}] to proceed to real (closed) session", methodName);
				}
				else if (realSession.getTransaction().getStatus() != TransactionStatus.ACTIVE) {
					// limit the methods available if no transaction is active
					if ("beginTransaction".equals(methodName)
							|| "getTransaction".equals(methodName)
							|| "isTransactionInProgress".equals(methodName)
							|| "setFlushMode".equals(methodName)
							|| "getSessionFactory".equals(methodName)) {
						LOGGER.trace( "Allowing invocation [{}] to proceed to real (non-transacted) session", methodName);
					}
					else {
						throw new JcrRuntimeException("Calling method '" + methodName + "' is not valid without an active transaction (Current status: "
								+ realSession.getTransaction().getStatus() + ")" );
					}
				}
				LOGGER.trace("Allowing proxy invocation [{}] to proceed to real session", methodName);
				return method.invoke(realSession, args);
			}
			catch (InvocationTargetException e) {
				if (e.getTargetException() instanceof RuntimeException) {
					throw (RuntimeException) e.getTargetException();
				}
				throw e;
			}
		}

		/**
		 * Setter for property 'wrapped'.
		 *
		 * @param wrapped Value to set for property 'wrapped'.
		 */
		public void setWrapped(Session wrapped) {
			this.wrappedSession = wrapped;
		}
	
		//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		//~~~~~ serialization
		
		private void writeObject(ObjectOutputStream oos) throws IOException {
			// if a ThreadLocalSessionContext-bound session happens to get
			// serialized, to be completely correct, we need to make sure
			// that unbinding of that session occurs.
			oos.defaultWriteObject();
			SessionFactoryImplementor sfi = getSessionFactory();
			if (existingSession(sfi) == wrappedSession) {
				unbind(sfi);
			}
		}
	
		private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
			// on the inverse, it makes sense that if a ThreadLocalSessionContext-
			// bound session then gets deserialized to go ahead and re-bind it to
			// the ThreadLocalSessionContext session map.
			ois.defaultReadObject();
			realSession.getTransaction().registerSynchronization( buildCleanupSynch() );
			doBind(wrappedSession, getSessionFactory());
		}
	}
}	

