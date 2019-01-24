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
package org.jcrom;

import java.io.Closeable;
import java.io.Serializable;

import org.jcrom.engine.spi.SessionFactoryOptions;
import org.jcrom.type.TypeHandler;

/**
 * Session Factory interface. This interface describes a simplfied contract for retrieving a session.
 * 
 * @author Nicolas Dos Santos
 */
public interface JcrSessionFactory extends Serializable, Closeable {
	/**
	 * Get the special options used to build the factory.
	 *
	 * @return The special options used to build the factory.
	 */
	SessionFactoryOptions getOptions();
	
	/**
	 * Obtain a {@link JcrSession} builder.
	 *
	 * @return The session builder
	 */
	SessionBuilder withOptions();

    /**
     * Opens a Session using the credentials and workspace on this SessionFactory implementation. The session
     * factory doesn't allow specification of a different workspace name because:
     * <p>
     *" Each Session object is associated one-to-one with a Workspace object. The Workspace object represents
     * a `view` of an actual repository workspace entity as seen through the authorization settings of its
     * associated Session." (quote from javax.jcr.Session javadoc).
     * </p>
     * @return the session.
	 * 
	 * @return The created session
	 * @throws JcrRuntimeException Indicates a problem opening the session; pretty rare here.
	 */
	JcrSession openSession() throws JcrRuntimeException;
	
	/**
	 * Obtains the current {@link JcrSession}.
	 * 
	 * @return The current session
	 * @throws JcrRuntimeException Indicates an issue locating a suitable current session.
	 */
	JcrSession getCurrentSession() throws JcrRuntimeException;
	
	/**
	 * Check if the SessionFactory is already closed.
	 *
	 * @return True if this factory is already closed; false otherwise.
	 */
	boolean isClosed();
	
	/**
	 * Check if the SessionFactory is still opened.
	 *
	 * @return boolean
	 */	
	boolean isOpened();
	
	/**
	 * Destroy this <tt>SessionFactory</tt> and release all resources (caches, connection pools, etc).
	 * 
	 * It is the responsibility of the application to ensure that there are no
	 * open {@link JcrSession sessions} before calling this method as the impact
	 * on those {@link JcrSession sessions} is indeterminate.
	 * 
	 * @throws JcrRuntimeException Indicates an issue closing the factory.
	 */
	void close() throws JcrRuntimeException;	
	
	/**
	 * Retrieve this SessionFactory's {@link TypeHandler}.
	 *
	 * @return The SessionFactory's {@link TypeHandler}
	 */
	TypeHandler getTypeHandler();
}