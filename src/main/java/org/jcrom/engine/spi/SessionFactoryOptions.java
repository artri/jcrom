package org.jcrom.engine.spi;

import org.jcrom.cfg.BaselineSessionEventsListenerBuilder;

public interface SessionFactoryOptions {
	
	/**
	 * specifies whether to clean names of new nodes, that is, 
	 * replace illegal characters and spaces automatically
	 * @return boolean
	 * @default true
	 */
	boolean isCleanNames();
	
	/**
	 * dynamicInstantiation if set to true, then Jcrom will try to retrieve the name of the class 
	 * to instantiate from a node property (see @JcrNode(classNameProperty)).
	 * @return boolean
	 * @default false
	 */
	boolean isDynamicInstantiation();
	
	/**
	 * Get the UUID unique to this SessionFactoryOptions.  Will be the
	 * same value available as {@link SessionFactoryImplementor#getUuid()}.
	 *
	 * @apiNote The value is generated as a {@link java.util.UUID}, but kept
	 * as a String.
	 *
	 * @return The UUID for this SessionFactory.
	 *
	 * @see org.hibernate.internal.SessionFactoryRegistry#getSessionFactory
	 * @see SessionFactoryImplementor#getUuid
	 */
	String getUUID();
	
	/**
	 * The name to be used for the SessionFactory.  This is use both in:<ul>
	 *     <li>in-VM serialization</li>
	 *     <li>JNDI binding, depending on {@link #isSessionFactoryNameAlsoJndiName}</li>
	 * </ul>
	 *
	 * @return The SessionFactory name
	 */
	String getSessionFactoryName();
	
	boolean isFlushBeforeCompletionEnabled();

	boolean isAutoCloseSessionEnabled();
	
	boolean isAllowOutOfTransactionUpdateOperations();
	
	boolean isReleaseResourcesOnCloseEnabled();
	
	BaselineSessionEventsListenerBuilder getBaselineSessionEventsListenerBuilder();
}
