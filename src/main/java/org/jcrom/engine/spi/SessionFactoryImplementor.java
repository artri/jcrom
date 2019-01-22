package org.jcrom.engine.spi;

import org.jcrom.SessionFactory;

public interface SessionFactoryImplementor extends SessionFactory {

	/**
	 * Obtain the identifier associated with this session.
	 * 
	 * @return The identifier associated with this session, or {@code null}
	 */
	String getUUID();
	
	/**
	 * Access to the name (if one) assigned to the SessionFactory
	 *
	 * @return The name for the SessionFactory
	 */
	String getName();
	
	@Override
	SessionBuilderImplementor withOptions();
}
