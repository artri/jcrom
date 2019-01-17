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

import java.util.UUID;

import javax.jcr.Credentials;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.observation.ObservationManager;

import org.jcrom.EventListenerDefinition;
import org.jcrom.JcrMappingException;
import org.jcrom.JcrRuntimeException;
import org.jcrom.SessionFactory;
import org.jcrom.context.CurrentSessionContext;
import org.jcrom.context.internal.ThreadLocalSessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Jcr Session Factory. This class is just a simple wrapper around the repository which facilitates session retrieval through a central point.
 * 
 * <p/>
 * The session factory is able to add event listener definitions for each session and some utility methods.
 * 
 * @author Nicolas Dos Santos
 */
public class SessionFactoryImpl implements SessionFactory {
	private static final Logger LOGGER = LoggerFactory.getLogger(SessionFactoryImpl.class);
	private static final String SESSION_FACTORY_NAME = "SessionFactory";
	
	private final String name = SESSION_FACTORY_NAME;
	private String uuid;
	
	private transient volatile boolean isClosed;
	private final transient CurrentSessionContext currentSessionContext;
	
    private Credentials credentials;
    private String workspaceName;
    private Repository repository;

    private EventListenerDefinition eventListeners[] = new EventListenerDefinition[] {};
    
    /**
     * Default Constructor
     * Use this constructor if you can set repository, credentials by injection
     **/
    public SessionFactoryImpl() {
    	this(null);
    }

    public SessionFactoryImpl(Repository repository) {
        this(repository, null, null);
    }

    public SessionFactoryImpl(Repository repository, Credentials credentials) {
        this(repository, credentials, null);
    }

    public SessionFactoryImpl(Repository repository, Credentials credentials, String workspaceName) {
        this.repository = repository;
        this.credentials = credentials;
        this.workspaceName = workspaceName;
        
        this.currentSessionContext = buildCurrentSessionContext();
    }

    /**
	 * @return the name
	 */
	public String getName() {
		return name;
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

	/**
	 * (non-Javadoc)
	 * @see org.jcrom.SessionFactory#openSession()
	 */
	@Override
	public Session openSession() throws JcrRuntimeException {
		LOGGER.trace("Open JCR session");
		
        try {
            Session session = repository.login(credentials, workspaceName);
            return addListeners(session);
        } catch (RepositoryException ex) {
        	LOGGER.error(ex.getMessage(), ex);
            throw new JcrMappingException("Could not open Jcr Session", ex);
        }		
	}

    /**
     * Close the current JCR Session
     * @param session the Session to close
     */
	@Override
    public void releaseSession(Session session) throws JcrRuntimeException {
	    if (null != session) {
	    	LOGGER.debug("Releasing the session");
	    	session.logout();
	    }
    }
    
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.SessionFactory#getCurrentSession()
	 */
	@Override
	public Session getCurrentSession() throws JcrRuntimeException {
		if (null == currentSessionContext) {
			throw new JcrRuntimeException("No CurrentSessionContext configured!");
		}
		return currentSessionContext.getCurrentSession();
	}

	
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.SessionFactory#invalidate()
	 */
	@Override
	public void invalidate() throws JcrRuntimeException {
		if (null == currentSessionContext) {
			throw new JcrRuntimeException("No CurrentSessionContext configured!");
		}
		((ThreadLocalSessionContext) currentSessionContext).unbind(this);
	}

	/**
	 * (non-Javadoc)
	 * @see org.jcrom.SessionFactory#isOpened()
	 */
	@Override
	public boolean isOpened() {
		return !isClosed();
	}
	
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.SessionFactory#isClosed()
	 */
	@Override
	public boolean isClosed() {
		return this.isClosed;
	}

	/**
	 * (non-Javadoc)
	 * @see org.jcrom.SessionFactory#close()
	 */
	@Override
	public void close() throws JcrRuntimeException {
		synchronized(this) {
			if (isClosed) {
				LOGGER.trace("Already closed");
				return;
			}
			
			isClosed = true;
		}
		
		LOGGER.trace("Closing SessionFactory");
		invalidate();
	}

	private CurrentSessionContext buildCurrentSessionContext() {
		return new ThreadLocalSessionContext(this);
	}
    
    /**
     * Hook for adding listeners to the newly returned session. We have to treat exceptions manually and can't
     * reply on the template.
     * @param session JCR session
     * @return the listened session
     * @throws javax.jcr.RepositoryException
     */
    private Session addListeners(Session session) throws RepositoryException {
        if (getRepository() == null) {
            throw new IllegalArgumentException("repository is required");
        }

        if (eventListeners != null && eventListeners.length > 0) {
            if (!supportsObservation(getRepository())) {
                throw new IllegalStateException("repository " + getRepositoryInfo() + " does NOT support Observation; remove Listener definitions");
            }
            Workspace ws = session.getWorkspace();
            ObservationManager manager = ws.getObservationManager();

            for (EventListenerDefinition eventListener : eventListeners) {
                manager.addEventListener(eventListener.getListener(), eventListener.getEventTypes(), eventListener.getAbsPath(), eventListener.isDeep(), eventListener.getUuid(), eventListener.getNodeTypeName(), eventListener.isNoLocal());
            }
        }
        return session;
    }

    /**
     * @return Returns the repository.
     */
    public Repository getRepository() {
        return repository;
    }

    /**
     * @param repository The repository to set.
     */
    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    /**
     * @return Returns the workspaceName.
     */
    public String getWorkspaceName() {
        return workspaceName;
    }

    /**
     * @param workspaceName The workspaceName to set.
     */
    public void setWorkspaceName(String workspaceName) {
        this.workspaceName = workspaceName;
    }

    /**
     * @return Returns the credentials.
     */
    public Credentials getCredentials() {
        return credentials;
    }

    /**
     * @param credentials The credentials to set.
     */
    public void setCredentials(Credentials credentials) {
        this.credentials = credentials;
    }

    /**
     * @return Returns the eventListenerDefinitions.
     */
    public EventListenerDefinition[] getEventListeners() {
        return eventListeners;
    }

    /**
     * @param eventListenerDefinitions The eventListenerDefinitions to set.
     */
    public void setEventListeners(EventListenerDefinition[] eventListenerDefinitions) {
        this.eventListeners = eventListenerDefinitions;
    }

    /**
     * A toString representation of the Repository.
     * @return
     */
    private String getRepositoryInfo() {
        if (null == getRepository()) {
            return "<N/A>";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(getRepository().getDescriptor(Repository.REP_NAME_DESC));
        sb.append(" ");
        sb.append(getRepository().getDescriptor(Repository.REP_VERSION_DESC));
        return sb.toString();
    }

    private static boolean supportsObservation(Repository repository) {
        return "true".equals(repository.getDescriptor(Repository.OPTION_OBSERVATION_SUPPORTED));
    }
}
