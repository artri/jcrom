package org.jcrom;

public interface SessionBuilder {

	/**
	 * Opens a session with the specified options.
	 *
	 * @return The session
	 */
	JcrSession openSession();
	
	/**
	 * Adds a specific connection to the session options.
	 *
	 * @param connection The connection to use.
	 *
	 * @return {@code this}, for method chaining
	 */
	SessionBuilder connection(Connection connection);

	/**
	 * Should the session built automatically join in any ongoing JTA transactions.
	 *
	 * @param autoJoinTransactions Should JTA transactions be automatically joined
	 *
	 * @return {@code this}, for method chaining
	 *
	 * @see javax.persistence.SynchronizationType#SYNCHRONIZED
	 */
	SessionBuilder autoJoinTransactions(boolean autoJoinTransactions);

	/**
	 * Should the session be automatically cleared on a failed transaction?
	 *
	 * @param autoClear Whether the Session should be automatically cleared
	 *
	 * @return {@code this}, for method chaining
	 */
	SessionBuilder autoClear(boolean autoClear);

	/**
	 * Specify the initial FlushMode to use for the opened Session
	 *
	 * @param flushMode The initial FlushMode to use for the opened Session
	 *
	 * @return {@code this}, for method chaining
	 *
	 * @see javax.persistence.PersistenceContextType
	 */
	SessionBuilder flushMode(FlushMode flushMode);
	
	/**
	 * Apply one or more SessionEventListener instances to the listeners for the Session to be built.
	 *
	 * @param listeners The listeners to incorporate into the built Session
	 *
	 * @return {@code this}, for method chaining
	 */
	SessionBuilder eventListeners(JcrSessionEventListener... listeners);
	
	/**
	 * Remove all listeners intended for the built Session currently held here, including any auto-apply ones; in other
	 * words, start with a clean slate.
	 *
	 * {@code this}, for method chaining
	 */
	SessionBuilder clearEventListeners();
	
}
