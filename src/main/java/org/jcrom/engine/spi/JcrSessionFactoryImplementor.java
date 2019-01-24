package org.jcrom.engine.spi;

import org.jcrom.AnnotationReader;
import org.jcrom.JcrSessionFactory;
import org.jcrom.mapping.Mapper;

public interface JcrSessionFactoryImplementor extends JcrSessionFactory {

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
	
	/**
	 * Retrieve this SessionFactory's {@link AnnotationReader}.
	 *
	 * @return The SessionFactory's {@link AnnotationReader}
	 */	
	AnnotationReader getAnnotationReader();
	
	Mapper getMapper();	
}
