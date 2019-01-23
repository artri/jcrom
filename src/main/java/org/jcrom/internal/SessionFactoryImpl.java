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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.jcr.Credentials;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Workspace;
import javax.jcr.observation.ObservationManager;

import org.jcrom.Connection;
import org.jcrom.EventListenerDefinition;
import org.jcrom.FlushMode;
import org.jcrom.JcrMappingException;
import org.jcrom.JcrRuntimeException;
import org.jcrom.Session;
import org.jcrom.SessionBuilder;
import org.jcrom.SessionEventListener;
import org.jcrom.SessionFactory;
import org.jcrom.engine.spi.SessionBuilderImplementor;
import org.jcrom.engine.spi.SessionFactoryImplementor;
import org.jcrom.engine.spi.SessionFactoryOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JCR Session Factory. This class is just a simple wrapper around the repository which facilitates session retrieval through a central point.
 * 
 * <p/>
 * The session factory is able to add event listener definitions for each session and some utility methods.
 * 
 * @author Nicolas Dos Santos
 */
public class SessionFactoryImpl implements SessionFactoryImplementor {
	private static final Logger LOGGER = LoggerFactory.getLogger(SessionFactoryImpl.class);
	
	private String name;
	private String uuid;
	
	private transient volatile boolean isClosed;
	private final transient SessionFactoryOptions sessionFactoryOptions;
	private final transient CurrentSessionContext currentSessionContext;
	
    private Credentials credentials;
    private String workspaceName;
    private Repository repository;

//    private final transient TypeHelper typeHelper;
    
    /**
     * Default Constructor
     * Use this constructor if you can set repository, credentials by injection
     **/
    public SessionFactoryImpl(SessionFactoryOptions sessionFactoryOptions) {
    	this(sessionFactoryOptions, null);
    }

    public SessionFactoryImpl(SessionFactoryOptions sessionFactoryOptions, Repository repository) {
        this(sessionFactoryOptions, repository, null);
    }

    public SessionFactoryImpl(SessionFactoryOptions sessionFactoryOptions, Repository repository, Credentials credentials) {
        this(sessionFactoryOptions, repository, credentials, null);
    }

    public SessionFactoryImpl(SessionFactoryOptions sessionFactoryOptions, Repository repository, Credentials credentials, String workspaceName) {
    	this.sessionFactoryOptions = sessionFactoryOptions;
    	this.name = sessionFactoryOptions.getSessionFactoryName();
    	this.uuid = sessionFactoryOptions.getUUID();
    	
        this.repository = repository;
        this.credentials = credentials;
        this.workspaceName = workspaceName;
        
        this.currentSessionContext = buildCurrentSessionContext();
    }

	//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	//~~~~~ SessionFactory 
    
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.SessionFactory#getSessionFactoryOptions()
	 */
	public SessionFactoryOptions getSessionFactoryOptions() {
		return sessionFactoryOptions;
	}

	/**
	 * (non-Javadoc)
	 * @see org.jcrom.SessionFactory#withOptions()
	 */
	@Override
	public SessionBuilderImplementor withOptions() {
		return new SessionBuilderImpl(this);
	}
	
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.SessionFactory#openSession()
	 */
	@Override
	public Session openSession() throws JcrRuntimeException {
		return withOptions().openSession();	
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
	 * @see org.jcrom.SessionFactory#isClosed()
	 */
	@Override
	public boolean isClosed() {
		return this.isClosed;
	}

	/**
	 * (non-Javadoc)
	 * @see org.jcrom.SessionFactory#isOpened()
	 */
	@Override
	public boolean isOpened() {
		return !isClosed;
	}
	
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.SessionFactory#close()
	 */
	@Override
	public void close() throws JcrRuntimeException {
		LOGGER.trace("Closing SessionFactory");		
		synchronized(this) {
			if (isClosed) {
				LOGGER.trace("Already closed");
				return;
			}
			
			isClosed = true;
		}
		
		if (null == currentSessionContext) {
			throw new JcrRuntimeException("No CurrentSessionContext configured!");
		}
		((ThreadLocalSessionContext) currentSessionContext).unbind(this);		
	}
	
	//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	//~~~~~ SessionFactoryImplementor 
	
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
	
    /*
     * Close the current JCR Session
     * @param session the Session to close
     */
//	@Override
//    public void releaseSession(Session session) throws JcrRuntimeException {
//	    if (null != session) {
//	    	LOGGER.debug("Releasing the session");
//	    	session.logout();
//	    }
//    }
	
	/*
	 * (non-Javadoc)
	 * @see org.jcrom.SessionFactory#invalidate()
	 */
//	@Override
//	public void invalidate() throws JcrRuntimeException {
//		if (null == currentSessionContext) {
//			throw new JcrRuntimeException("No CurrentSessionContext configured!");
//		}
//		((ThreadLocalSessionContext) currentSessionContext).unbind(this);
//	}


	private CurrentSessionContext buildCurrentSessionContext() {
		return new ThreadLocalSessionContext(this);
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
    
    static class SessionBuilderImpl implements SessionBuilderImplementor, SessionCreationOptions {
    	private static final Logger LOGGER = LoggerFactory.getLogger(SessionBuilderImpl.class);
    	
    	private final SessionFactoryImpl sessionFactory;
    	private Connection connection;
    	private boolean autoJoinTransactions = true;
		private FlushMode flushMode;
		private boolean autoClose;
		private boolean autoClear;
		
		@Deprecated
		private List<SessionEventListener> listeners;
		private List<EventListenerDefinition> eventListeners;
		
		public SessionBuilderImpl(SessionFactoryImpl sessionFactory) {
			this.sessionFactory = sessionFactory;
			// initialize with default values
			this.autoClose = sessionFactory.getSessionFactoryOptions().isAutoCloseSessionEnabled();
			this.flushMode = sessionFactory.getSessionFactoryOptions().isFlushBeforeCompletionEnabled() ? FlushMode.AUTO : FlushMode.MANUAL;
			
			listeners = sessionFactory.getSessionFactoryOptions().getBaselineSessionEventsListenerBuilder().buildBaselineList();
			this.listeners = new ArrayList<>();
			List<EventListenerDefinition> eventListeners = new ArrayList<EventListenerDefinition>();
		}
    	
		@Override
		public boolean shouldAutoJoinTransactions() {
			return autoJoinTransactions;
		}

		@Override
		public FlushMode getInitialSessionFlushMode() {
			return flushMode;
		}

		@Override
		public boolean shouldAutoClose() {
			return autoClose;
		}

		@Override
		public boolean shouldAutoClear() {
			return autoClear;
		}

		@Override
		public Connection getConnection() {
			return connection;
		}
		
		// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		// SessionBuilder

		@Override
		public Session openSession() {
			LOGGER.trace("Opening JCR Session.");
			final SessionImpl session = new SessionImpl(sessionFactory, this);

			for (SessionEventListener listener : listeners) {
				LOGGER.warn("Adding listener to a session: skipped");
				//TODO: needs to be implemented
//				session.getEventListenerManager().addListener(listener);
			}

			return session;
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
	    
		/* (non-Javadoc)
		 * @see org.jcrom.SessionBuilder#connection(org.jcrom.Connection)
		 */
		@Override
		public SessionBuilder connection(Connection connection) {
			this.connection = connection;
			return this;
		}

		/* (non-Javadoc)
		 * @see org.jcrom.SessionBuilder#autoJoinTransactions(boolean)
		 */
		@Override
		public SessionBuilder autoJoinTransactions(boolean autoJoinTransactions) {
			this.autoJoinTransactions = autoJoinTransactions;
			return this;
		}

		/* (non-Javadoc)
		 * @see org.jcrom.SessionBuilder#autoClear(boolean)
		 */
		@Override
		public SessionBuilder autoClear(boolean autoClear) {
			this.autoClear = autoClear;
			return this;
		}

		/* (non-Javadoc)
		 * @see org.jcrom.SessionBuilder#flushMode(org.jcrom.FlushMode)
		 */
		@Override
		public SessionBuilder flushMode(FlushMode flushMode) {
			this.flushMode = flushMode;
			return this;
		}

	    /**
	     * @param eventListenerDefinitions The eventListenerDefinitions to set.
	     */
	    public SessionBuilder eventListeners(EventListenerDefinition... eventListenerDefinitions) {
			Collections.addAll(this.eventListeners, eventListenerDefinitions);
			return this;
	    }
	    
		/* (non-Javadoc)
		 * @see org.jcrom.SessionBuilder#eventListeners(org.jcrom.SessionEventListener[])
		 */
		@Override
		public SessionBuilder eventListeners(SessionEventListener... listeners) {
			Collections.addAll(this.listeners, listeners);
			return this;
		}

		/* (non-Javadoc)
		 * @see org.jcrom.SessionBuilder#clearEventListeners()
		 */
		@Override
		public SessionBuilder clearEventListeners() {
			listeners.clear();
			eventListeners.clear();
			return this;
		}
		
    }
}
