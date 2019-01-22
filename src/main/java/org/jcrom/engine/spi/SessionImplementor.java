package org.jcrom.engine.spi;

import org.jcrom.Session;
import org.jcrom.Transaction;

public interface SessionImplementor extends Session {
	//===== SharedSessionContractImplementor =====
	/**
	 * Obtain the identifier associated with this session.
	 * 
	 * @return The identifier associated with this session, or {@code null}
	 */
	String getUUID();

	/**
	 * Checks whether the session is closed.  Provided separately from
	 * {@link #isOpen()} as this method does not attempt any JTA synchronization
	 * registration, where as {@link #isOpen()} does; which makes this one
	 * nicer to use for most internal purposes.
	 *
	 * @return {@code true} if the session is closed; {@code false} otherwise.
	 */
	boolean isClosed();
	
	/**
	 * Checks whether the session is open or is waiting for auto-close
	 *
	 * @return {@code true} if the session is closed or if it's waiting for auto-close; {@code false} otherwise.
	 */
	default boolean isOpenOrWaitingForAutoClose() {
		return !isClosed();
	}
	
	/**
	 * Performs a check whether the Session is open, and if not:<ul>
	 *     <li>marks current transaction (if one) for rollback only</li>
	 *     <li>throws an IllegalStateException (JPA defines the exception type)</li>
	 * </ul>
	 */
	default void checkOpen() {
		checkOpen(true);
	}
	
	/**
	 * Performs a check whether the Session is open, and if not:<ul>
	 *     <li>if {@code markForRollbackIfClosed} is true, marks current transaction (if one) for rollback only</li>
	 *     <li>throws an IllegalStateException (JPA defines the exception type)</li>
	 * </ul>
	 */
	void checkOpen(boolean markForRollbackIfClosed);
	
	/**
	 * Marks current transaction (if one) for rollback only
	 */
	void markForRollbackOnly();
	
	/**
	 * A "timestamp" at or before the start of the current transaction.
	 *
	 * @apiNote This "timestamp" need not be related to timestamp in the Java Date/millisecond
	 * sense.  It just needs to be an incrementing value.  See
	 * {@link CacheTransactionSynchronization#getCurrentTransactionStartTimestamp()}
	 */
	long getTransactionStartTimestamp();
	
	/**
	 * Does this <tt>Session</tt> have an active transaction
	 * or is there a JTA transaction in progress?
	 */
	boolean isTransactionInProgress();

	/**
	 * Provides access to the underlying transaction or creates a new transaction if
	 * one does not already exist or is active.  This is primarily for internal or
	 * integrator use.
	 *
	 * @return the transaction
     */
	Transaction accessTransaction();
	
	//===== SessionImplementor =====
	@Override
	SessionFactoryImplementor getSessionFactory();
	
}
