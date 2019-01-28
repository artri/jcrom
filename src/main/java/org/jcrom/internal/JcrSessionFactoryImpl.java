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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.jcr.Credentials;
import javax.jcr.Node;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Workspace;
import javax.jcr.observation.ObservationManager;

import org.jcrom.AnnotationReader;
import org.jcrom.Connection;
import org.jcrom.EventListenerDefinition;
import org.jcrom.FlushMode;
import org.jcrom.JcrMappingException;
import org.jcrom.JcrRuntimeException;
import org.jcrom.ReflectionAnnotationReader;
import org.jcrom.JcrSession;
import org.jcrom.SessionBuilder;
import org.jcrom.JcrSessionEventListener;
import org.jcrom.JcrSessionFactory;
import org.jcrom.Validator;
import org.jcrom.engine.spi.SessionBuilderImplementor;
import org.jcrom.engine.spi.JcrSessionFactoryImplementor;
import org.jcrom.engine.spi.SessionFactoryOptions;
import org.jcrom.mapping.Mapper;
import org.jcrom.type.DefaultTypeHandler;
import org.jcrom.type.JavaFXTypeHandler;
import org.jcrom.type.TypeHandler;
import org.jcrom.util.ReflectionUtils;
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
public class JcrSessionFactoryImpl implements JcrSessionFactoryImplementor {
	private static final long serialVersionUID = -6974581983167212739L;
	private static final Logger LOGGER = LoggerFactory.getLogger(JcrSessionFactoryImpl.class);
	private static final String JAVAFX_OBJECT_PROPERTY_CLASS = "javafx.beans.property.ObjectProperty";
	
	private String name;
	private String uuid;
	
	private volatile boolean isClosed;
	
	private final SessionFactoryOptions options;
	private final CurrentSessionContext currentSessionContext;
	
    private Credentials credentials;
    private String workspaceName;
    private Repository repository;

    private TypeHandler typeHandler;
    private Mapper mapper;
    private Validator validator;
    private AnnotationReader annotationReader;
    
    /**
     * Default Constructor
     * Use this constructor if you can set repository, credentials by injection
     **/
    public JcrSessionFactoryImpl(SessionFactoryOptions sessionFactoryOptions) {
    	this(sessionFactoryOptions, null);
    }

    public JcrSessionFactoryImpl(SessionFactoryOptions sessionFactoryOptions, Repository repository) {
        this(sessionFactoryOptions, repository, null);
    }

    public JcrSessionFactoryImpl(SessionFactoryOptions sessionFactoryOptions, Repository repository, Credentials credentials) {
        this(sessionFactoryOptions, repository, credentials, null);
    }

    public JcrSessionFactoryImpl(SessionFactoryOptions options, Repository repository, Credentials credentials, String workspaceName) {
    	this.options = options;
    	this.name = options.getSessionFactoryName();
    	this.uuid = options.getUUID();
    	
        this.repository = repository;
        this.credentials = credentials;
        this.workspaceName = workspaceName;
        
        this.currentSessionContext = buildCurrentSessionContext();
        
        this.typeHandler = getDefaultTypeHandler();
        this.mapper = new Mapper(this);
        this.validator = new Validator(this);
        this.annotationReader = new ReflectionAnnotationReader();
    }

    private static TypeHandler getDefaultTypeHandler() {
        Class<?> clazz = null;
        try {
            // Try to find a Java FX class. If found, uses the JavaFXTypeHandler class
            clazz = Class.forName(JAVAFX_OBJECT_PROPERTY_CLASS);
        } catch (Exception e) {
        	LOGGER.error(e.getMessage(), e);
        }
        return clazz == null ? new DefaultTypeHandler() : new JavaFXTypeHandler();
    }
    
	//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	//~~~~~ SessionFactory 
    
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.JcrSessionFactory#getOptions()
	 */
	public SessionFactoryOptions getOptions() {
		return options;
	}

	public TypeHandler getTypeHandler() {
		return this.typeHandler;
	}
	
	public void setTypeHandler(TypeHandler typeHandler) {
		this.typeHandler = typeHandler;
	}
		
    /**
	 * @return the mapper
	 */
	public Mapper getMapper() {
		return mapper;
	}

	/**
	 * @param mapper the mapper to set
	 */
	public void setMapper(Mapper mapper) {
		this.mapper = mapper;
	}

	public AnnotationReader getAnnotationReader() {
        return annotationReader;
    }
    
    public void setAnnotationReader(AnnotationReader annotationReader) {
        this.annotationReader = annotationReader;
    }
	
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.JcrSessionFactory#withOptions()
	 */
	@Override
	public SessionBuilderImplementor withOptions() {
		return new SessionBuilderImpl(this);
	}
	
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.JcrSessionFactory#openSession()
	 */
	@Override
	public JcrSession openSession() throws JcrRuntimeException {
		return withOptions().openSession();	
	}
	
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.JcrSessionFactory#getCurrentSession()
	 */
	@Override
	public JcrSession getCurrentSession() throws JcrRuntimeException {
		if (null == currentSessionContext) {
			throw new JcrRuntimeException("No CurrentSessionContext configured!");
		}
		return currentSessionContext.getCurrentSession();
	}
	
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.JcrSessionFactory#isClosed()
	 */
	@Override
	public boolean isClosed() {
		return this.isClosed;
	}

	/**
	 * (non-Javadoc)
	 * @see org.jcrom.JcrSessionFactory#isOpened()
	 */
	@Override
	public boolean isOpened() {
		return !isClosed;
	}
	
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.JcrSessionFactory#close()
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

    /**
     * Add a class that this instance can map to/from JCR nodes. This method will validate the class, and all mapped
     * JcrEntity implementations referenced from this class.
     * 
     * @param entityClass the class that will be mapped
     * @return the Jcrom instance
     */
    public synchronized void map(Class<?> entityClass) {
        if (!mapper.isMapped(entityClass)) {
            Set<Class<?>> validClasses = validator.validate(entityClass, mapper.isDynamicInstantiation());
            for (Class<?> c : validClasses) {
                mapper.addMappedClass(c);
            }
        }
    }

    public synchronized void map(Collection<Class<?>> classesToMap) {
        for (Class<?> c : classesToMap) {
            map(c);
        }    	
    }
    
    /**
     * Tries to map all classes in the package specified. Fails if one of the classes is not valid for mapping.
     * 
     * @param packageName the name of the package to process
     * @return the Jcrom instance
     */
    public synchronized void mapPackage(String packageName) {
        mapPackage(packageName, false);
    }

    /**
     * Tries to map all classes in the package specified.
     * 
     * @param packageName the name of the package to process
     * @param ignoreInvalidClasses specifies whether to ignore classes in the package that cannot be mapped
     * @return the Jcrom instance
     */
    public synchronized void mapPackage(String packageName, boolean ignoreInvalidClasses) {
        try {
            for (Class<?> c : ReflectionUtils.getClasses(packageName)) {
                try {
                    // Ignore Enum because these are not entities
                    // Can be useful if there is an inner Enum
                    if (!c.isEnum()) {
                        map(c);
                    }
                } catch (JcrMappingException ex) {
                    if (!ignoreInvalidClasses) {
                        throw ex;
                    }
                }
            }
        } catch (IOException ioex) {
            throw new JcrMappingException("Could not get map classes from package " + packageName, ioex);
        } catch (ClassNotFoundException cnfex) {
            throw new JcrMappingException("Could not get map classes from package " + packageName, cnfex);
        }
    }

    /**
     * Get a set of all classes that are mapped by this instance.
     * 
     * @return all classes that are mapped by this instance
     */
    public Set<Class<?>> getMappedClasses() {
        return Collections.unmodifiableSet(mapper.getMappedClasses());
    }

    /**
     * Check whether a specific class is mapped by this instance.
     * 
     * @param entityClass the class we want to check
     * @return true if the class is mapped, else false
     */
    public boolean isMapped(Class<?> entityClass) {
        return mapper.isMapped(entityClass);
    }

    public String getName(Object object) throws JcrMappingException {
        try {
            return mapper.getNodeName(object);
        } catch (IllegalAccessException e) {
            throw new JcrMappingException("Could not get node name from object", e);
        }
    }

    public String getPath(Object object) throws JcrMappingException {
        try {
            return mapper.getNodePath(object);
        } catch (IllegalAccessException e) {
            throw new JcrMappingException("Could not get node path from object", e);
        }
    }

    public Object getParentObject(Object childObject) throws JcrMappingException {
        try {
            return mapper.getParentObject(childObject);
        } catch (IllegalAccessException e) {
            throw new JcrMappingException("Could not get parent object with Annotation JcrParentNode from child object", e);
        }
    }

    public String getChildContainerPath(Object childObject, Object parentObject, Node parentNode) {
        try {
            return mapper.getChildContainerNodePath(childObject, parentObject, parentNode);
        } catch (IllegalAccessException e) {
            throw new JcrMappingException("Could not get child object with Annotation @JcrChildNode and with the type '" + childObject.getClass() + "' from parent object", e);
        } catch (RepositoryException e) {
            throw new JcrMappingException("Could not get child object with Annotation @JcrChildNode and with the type '" + childObject.getClass() + "' from parent object", e);
        }
    }

    public void setBaseVersionInfo(Object object, String name, Calendar created) throws JcrMappingException {
        try {
            mapper.setBaseVersionInfo(object, name, created);
        } catch (IllegalAccessException e) {
            throw new JcrMappingException("Could not set base version info on object", e);
        }
    }
    
    private static boolean supportsObservation(Repository repository) {
        return "true".equals(repository.getDescriptor(Repository.OPTION_OBSERVATION_SUPPORTED));
    }
    
    static class SessionBuilderImpl implements SessionBuilderImplementor, SessionCreationOptions {
    	private static final Logger LOGGER = LoggerFactory.getLogger(SessionBuilderImpl.class);
    	
    	private final JcrSessionFactoryImpl sessionFactory;
    	private Connection connection;
    	private boolean autoJoinTransactions = true;
		private FlushMode flushMode;
		private boolean autoClose;
		private boolean autoClear;
		
		@Deprecated
		private List<JcrSessionEventListener> listeners;
		private List<EventListenerDefinition> eventListeners;
		
		public SessionBuilderImpl(JcrSessionFactoryImpl sessionFactory) {
			this.sessionFactory = sessionFactory;
			// initialize with default values
			this.autoClose = sessionFactory.getOptions().isAutoCloseSessionEnabled();
			this.flushMode = sessionFactory.getOptions().isFlushBeforeCompletionEnabled() ? FlushMode.AUTO : FlushMode.MANUAL;
			
			listeners = sessionFactory.getOptions().getBaselineSessionEventsListenerBuilder().buildBaselineList();
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
		public JcrSession openSession() {
			LOGGER.trace("Opening JCR Session.");
			final JcrSessionImpl session = new JcrSessionImpl(sessionFactory, this);

			for (JcrSessionEventListener listener : listeners) {
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
	    private JcrSession addListeners(JcrSession session) throws RepositoryException {
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
		public SessionBuilder eventListeners(JcrSessionEventListener... listeners) {
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
