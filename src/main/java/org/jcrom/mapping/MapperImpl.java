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
package org.jcrom.mapping;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.nodetype.NodeType;
import javax.jcr.version.Version;
import javax.jcr.version.VersionManager;

import org.jcrom.AnnotationReader;
import org.jcrom.JcrFile;
import org.jcrom.JcrMappingException;
import org.jcrom.Session;
import org.jcrom.annotations.JcrBaseVersionCreated;
import org.jcrom.annotations.JcrBaseVersionName;
import org.jcrom.annotations.JcrCheckedout;
import org.jcrom.annotations.JcrChildNode;
import org.jcrom.annotations.JcrCreated;
import org.jcrom.annotations.JcrFileNode;
import org.jcrom.annotations.JcrIdentifier;
import org.jcrom.annotations.JcrName;
import org.jcrom.annotations.JcrNode;
import org.jcrom.annotations.JcrParentNode;
import org.jcrom.annotations.JcrPath;
import org.jcrom.annotations.JcrProperty;
import org.jcrom.annotations.JcrProtectedProperty;
import org.jcrom.annotations.JcrReference;
import org.jcrom.annotations.JcrSerializedProperty;
import org.jcrom.annotations.JcrVersionCreated;
import org.jcrom.annotations.JcrVersionName;
import org.jcrom.callback.DefaultJcromCallback;
import org.jcrom.callback.JcromCallback;
import org.jcrom.engine.spi.SessionFactoryImplementor;
import org.jcrom.type.TypeHandler;
import org.jcrom.util.JcrUtils;
import org.jcrom.util.NodeFilter;
import org.jcrom.util.ReflectionUtils;

import net.sf.cglib.proxy.LazyLoader;

/**
 * This class handles the heavy lifting of mapping a JCR node to a JCR entity object, and vice versa.
 *
 * @author Olafur Gauti Gudmundsson
 * @author Nicolas Dos Santos
 */
public class MapperImpl implements MapperImplementor {
	private static final String FN_CGLIB_CALLBACK = "CGLIB$CALLBACK_0";
    private static final String FN_CGLIB_LAZY_LOADER = "CGLIB$LAZY_LOADER_0";

    private SessionFactoryImplementor sessionFactory;
    
    /** Set of classes that have been validated for mapping by this mapper */
    private final CopyOnWriteArraySet<Class<?>> mappedClasses = new CopyOnWriteArraySet<Class<?>>();

    private final PropertyMapper propertyMapper;

    private final ReferenceMapper referenceMapper;

    private final FileNodeMapper fileNodeMapper;

    private final ChildNodeMapper childNodeMapper;

    private final ThreadLocal<Map<HistoryKey, Object>> history = new ThreadLocal<Map<HistoryKey, Object>>();

    /**
     * Create a Mapper for a specific class.
     * 
     * @param sessionFactory
     */    
    public MapperImpl(SessionFactoryImplementor sessionFactory) {
    	this.sessionFactory = sessionFactory;
        this.propertyMapper = new PropertyMapper(this);
        this.referenceMapper = new ReferenceMapper(this);
        this.fileNodeMapper = new FileNodeMapper(this);
        this.childNodeMapper = new ChildNodeMapper(this);
    }
    
    public SessionFactoryImplementor getSessionFactory() {
		return sessionFactory;
	}

	public void setSessionFactory(SessionFactoryImplementor sessionFactory) {
		this.sessionFactory = sessionFactory;
	}
    
	public void clearHistory() {
        history.remove();
    }

    public boolean isMapped(Class<?> c) {
        return mappedClasses.contains(c);
    }

    void addMappedClass(Class<?> c) {
        mappedClasses.add(c);
    }

    CopyOnWriteArraySet<Class<?>> getMappedClasses() {
        return mappedClasses;
    }

    public boolean isCleanNames() {
        return getSessionFactory().getOptions().isCleanNames();
    }

    public boolean isDynamicInstantiation() {
        return getSessionFactory().getOptions().isDynamicInstantiation();
    }

    private Class<?> getClassForName(String className) {
        return getClassForName(className, null);
    }

    private Class<?> getClassForName(String className, Class<?> defaultClass) {
        for (Class<?> c : mappedClasses) {
            if (className.equals(c.getCanonicalName())) {
                return c;
            }
        }
        try {
            return Class.forName(className, true, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException ex) {
            return defaultClass;
        }
    }

    private Field findAnnotatedField(Object obj, Class<? extends Annotation> annotationClass) {
        for (Field field : ReflectionUtils.getDeclaredAndInheritedFields(obj.getClass(), false)) {
            if (getAnnotationReader().isAnnotationPresent(field, annotationClass)) {
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }

    Field findPathField(Object obj) {
        return findAnnotatedField(obj, JcrPath.class);
    }

    Field findParentField(Object obj) {
        return findAnnotatedField(obj, JcrParentNode.class);
    }

    Field findNameField(Object obj) {
        return findAnnotatedField(obj, JcrName.class);
    }

    Field findIdField(Object obj) {
        return findAnnotatedField(obj, JcrIdentifier.class);
    }
    
    Object getParentObject(Object childObject) throws IllegalAccessException {
        Field parentField = findParentField(childObject);
        return parentField != null ? getTypeHandler().getObject(parentField, childObject) : null;
    }

    String getChildContainerNodePath(Object childObject, Object parentObject, Node parentNode) throws IllegalAccessException, RepositoryException {
        return childNodeMapper.getChildContainerNodePath(childObject, parentObject, parentNode);
    }

    static boolean hasMixinType(Node node, String mixinType) throws RepositoryException {
        for (NodeType nodeType : node.getMixinNodeTypes()) {
            if (nodeType.getName().equals(mixinType)) {
                return true;
            }
        }
        return false;
    }

    void setBaseVersionInfo(Object object, String name, Calendar created) throws IllegalAccessException {
        Field baseName = findAnnotatedField(object, JcrBaseVersionName.class);
        if (baseName != null) {
            baseName.set(object, name);
        }
        Field baseCreated = findAnnotatedField(object, JcrBaseVersionCreated.class);
        if (baseCreated != null) {
            if (baseCreated.getType() == Date.class) {
                baseCreated.set(object, created.getTime());
            } else if (baseCreated.getType() == Timestamp.class) {
                baseCreated.set(object, new Timestamp(created.getTimeInMillis()));
            } else if (baseCreated.getType() == Calendar.class) {
                baseCreated.set(object, created);
            }
        }
    }

    Object findParentObjectFromNode(Node node) throws RepositoryException, IllegalAccessException, ClassNotFoundException, InstantiationException, IOException {
        Object parentObj = null;
        Node parentNode = node.getParent();
        while (parentNode != null) {
            Class<?> parentClass = findClassFromNode(Object.class, parentNode);
            if (parentClass != null && !parentClass.equals(Object.class)) {
                // Gets parent object without children
                parentObj = fromNode(parentClass, parentNode, new NodeFilter(NodeFilter.INCLUDE_ALL, 0));
                break;
            }
            try {
                parentNode = parentNode.getParent();
            } catch (Exception ignore) {
                parentNode = null;
            }
        }
        return parentObj;
    }

    /**
     * Transforms the node supplied to an instance of the entity class that this Mapper was created for.
     *
     * @param node
     *            the JCR node from which to create the object
     * @param nodeFilter
     *            the NodeFilter to be applied
     * @param action
     *            callback object that specifies the Jcr action
     * @return an instance of the JCR entity class, mapped from the node
     * @throws java.lang.Exception
     */
    public Object fromNodeWithParent(Class<?> entityClass, Node node, NodeFilter nodeFilter) throws ClassNotFoundException, InstantiationException, RepositoryException, IllegalAccessException, IOException {
        history.set(new HashMap<HistoryKey, Object>());
        Object obj = createInstanceForNode(entityClass, node);

        Object parentObj = findParentObjectFromNode(node);

        if (nodeFilter == null) {
            nodeFilter = new NodeFilter(NodeFilter.INCLUDE_ALL, NodeFilter.DEPTH_INFINITE);
        }

        if (JcrFile.class.isAssignableFrom(obj.getClass())) {
            // special handling of JcrFile objects
            fileNodeMapper.mapSingleFile((JcrFile) obj, node, parentObj, 0, nodeFilter);
        }
        mapNodeToClass(obj, node, nodeFilter, parentObj, 0);
        history.remove();
        return obj;
    }

    /**
     * Transforms the node supplied to an instance of the entity class that this Mapper was created for.
     *
     * @param node
     *            the JCR node from which to create the object
     * @param nodeFilter
     *            the NodeFilter to be applied
     * @return an instance of the JCR entity class, mapped from the node
     * @throws java.lang.Exception
     */
    Object fromNode(Class<?> entityClass, Node node, NodeFilter nodeFilter) throws ClassNotFoundException, InstantiationException, RepositoryException, IllegalAccessException, IOException {
        history.set(new HashMap<HistoryKey, Object>());
        Object obj = createInstanceForNode(entityClass, node);

        if (nodeFilter == null) {
            nodeFilter = new NodeFilter(NodeFilter.INCLUDE_ALL, NodeFilter.DEPTH_INFINITE);
        }

        if (JcrFile.class.isAssignableFrom(obj.getClass())) {
            // special handling of JcrFile objects
            fileNodeMapper.mapSingleFile((JcrFile) obj, node, null, 0, nodeFilter);
        }
        mapNodeToClass(obj, node, nodeFilter, null, 0);
        history.remove();
        return obj;
    }

    private boolean isVersionable(Node node) throws RepositoryException {
        for (NodeType mixinType : node.getMixinNodeTypes()) {
            if (mixinType.getName().equals("mix:versionable") || mixinType.getName().equals(NodeType.MIX_VERSIONABLE)) {
                return true;
            }
        }
        return false;
    }

    static VersionManager getVersionManager(Node node) throws RepositoryException {
        VersionManager versionMgr = node.getSession().getWorkspace().getVersionManager();
        return versionMgr;
    }

    static Node getNodeById(Node node, String id) throws RepositoryException {
        return node.getSession().getNodeByIdentifier(id);
    }

    PropertyMapper getPropertyMapper() {
        return propertyMapper;
    }

    ReferenceMapper getReferenceMapper() {
        return referenceMapper;
    }

    FileNodeMapper getFileNodeMapper() {
        return fileNodeMapper;
    }

    ChildNodeMapper getChildNodeMapper() {
        return childNodeMapper;
    }
    
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    //~~~~~~ Mapper 
    
    public Session getSession() {
    	return getSessionFactory().getCurrentSession();
    }
    
    @Override
	public List<?> getChildrenList(Class<?> childObjClass, Node childrenContainer, Object parentObj, int depth, NodeFilter nodeFilter, JcrChildNode jcrChildNode)
					throws ClassNotFoundException, InstantiationException, RepositoryException, IllegalAccessException, IOException {
		return childNodeMapper.getChildrenList(childObjClass, childrenContainer, parentObj, depth, nodeFilter, jcrChildNode);
	}
	
    @Override
    public Object getSingleChild(Class<?> childObjClass, Node childNode, Object obj, int depth, NodeFilter nodeFilter) 
    		throws ClassNotFoundException, InstantiationException, RepositoryException, IllegalAccessException, IOException {
    	return childNodeMapper.getSingleChild(childObjClass, childNode, obj, depth, nodeFilter);
    }
	
    @Override
    public JcrFile getSingleFile(Class<?> childObjClass, Node fileContainer, Object obj, JcrFileNode jcrFileNode, int depth, NodeFilter nodeFilter) 
    		throws ClassNotFoundException, InstantiationException, RepositoryException, IllegalAccessException, IOException {
    	return fileNodeMapper.getSingleFile(childObjClass, fileContainer, obj, jcrFileNode, depth, nodeFilter);
    }
    
    @Override
    public List<JcrFile> getFileList(Class<?> childObjClass, Node fileContainer, Object obj, JcrFileNode jcrFileNode, int depth, NodeFilter nodeFilter) 
    		throws ClassNotFoundException, InstantiationException, RepositoryException, IllegalAccessException, IOException {
    	return fileNodeMapper.getFileList(childObjClass, fileContainer, obj, jcrFileNode, depth, nodeFilter);
    }
    
    @Override
    public List<?> getReferenceList(Field field, String propertyName, Class<?> referenceObjClass, Node node, Object obj, int depth, NodeFilter nodeFilter) 
    		throws ClassNotFoundException, InstantiationException, RepositoryException, IllegalAccessException, IOException {
    	return referenceMapper.getReferenceList(field, propertyName, referenceObjClass, node, obj, depth, nodeFilter);
    }

    @Override
    public Object createReferencedObject(Field field, Value value, Object obj, Session session, Class<?> referenceObjClass, int depth, NodeFilter nodeFilter) 
    		throws ClassNotFoundException, InstantiationException, RepositoryException, IllegalAccessException, IOException {
    	return referenceMapper.createReferencedObject(field, value, obj, session, referenceObjClass, depth, nodeFilter);
    }

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    //~~~~~~ MapperImplementor 
    
    @Override
    public TypeHandler getTypeHandler() {
        return getSessionFactory().getTypeHandler();
    }
    
    @Override
    public AnnotationReader getAnnotationReader() {
        return getSessionFactory().getAnnotationReader();
    }
    
    @Override
    public String getCleanName(String name) {
        if (name == null) {
            throw new JcrMappingException("Node name is null");
        }
        
        //TODO: reuse from SessionImpl.createValidName
        return isCleanNames() ? PathUtils.createValidName(name) : name;
    }
    
    @Override
    public Object createInstanceForNode(Class<?> objClass, Node node) throws RepositoryException, IllegalAccessException, ClassNotFoundException, InstantiationException {
        return findClassFromNode(objClass, node).newInstance();
    }
    
    @Override
    public Object findEntityByPath(List<?> entities, String path) throws IllegalAccessException {
        for (Object entity : entities) {
            if (path.equals(getNodePath(entity))) {
                return entity;
            }
        }
        return null;
    }
    
    @Override
    public String getNodeId(Object object) throws IllegalAccessException {
        Field idField = findIdField(object);
        return idField != null ? (String) getTypeHandler().getObject(idField, object) : null;
    }
    
    @Override
    public void setId(Object object, String id) throws IllegalAccessException {
        Field idField = findIdField(object);
        if (idField != null) {
        	getTypeHandler().setObject(idField, object, id);
        }
    }
    
    @Override
    public String getNodePath(Object object) throws IllegalAccessException {
        Field field = findPathField(object);
        return (String) getTypeHandler().getObject(field, object);
    }
    
    @Override
    public void setNodePath(Object object, String path) throws IllegalAccessException {
        Field field = findPathField(object);
        getTypeHandler().setObject(field, object, path);
    }
    
    @Override
    public String getNodeName(Object object) throws IllegalAccessException {
        Field field = findNameField(object);
        return (String) getTypeHandler().getObject(field, object);
    }
    
    @Override
    public void setNodeName(Object object, String name) throws IllegalAccessException {
        Field field = findNameField(object);
        getTypeHandler().setObject(field, object, name);
    }  
    
    @Override
    public Class<?> findClassFromNode(Class<?> defaultClass, Node node) throws RepositoryException, IllegalAccessException, ClassNotFoundException, InstantiationException {
    	if (!isDynamicInstantiation()) {
            // use default class
            return defaultClass;    		
    	}
    	
        // first we try to locate the class name from node property
        String classNameProperty = "className";
        JcrNode jcrNode = ReflectionUtils.getJcrNodeAnnotation(defaultClass);
        if (jcrNode != null && !jcrNode.classNameProperty().equals("none")) {
            classNameProperty = jcrNode.classNameProperty();
        }

        if (node.hasProperty(classNameProperty)) {
            String className = node.getProperty(classNameProperty).getString();
            Class<?> c = getClassForName(className, defaultClass);
            if (isMapped(c)) {
                return c;
            } else {
                throw new JcrMappingException("Trying to instantiate unmapped class: " + c.getName());
            }
        } else {
            // use default class
            return defaultClass;
        }

    }
    
    @Override
    public Object mapNodeToClass(Object obj, Node node, NodeFilter nodeFilter, Object parentObject, int depth) throws ClassNotFoundException, InstantiationException, RepositoryException, IllegalAccessException, IOException {

        if (!JcrFile.class.isAssignableFrom(obj.getClass())) {
            // this does not apply for JcrFile extensions
            setNodeName(obj, node.getName());
        }

        // construct history key
        HistoryKey key = new HistoryKey();
        key.path = node.getPath();
        if (nodeFilter.getMaxDepth() == NodeFilter.DEPTH_INFINITE) {
            // then use infinite depth as key depth
            key.depth = NodeFilter.DEPTH_INFINITE;
        } else {
            // calculate key depth from max depth - current depth
            key.depth = nodeFilter.getMaxDepth() - depth;
        }

        // now check the history key
        if (history.get() == null) {
            history.set(new HashMap<HistoryKey, Object>());
        }
        if (history.get().containsKey(key)) {
            return history.get().get(key);
        } else {
            history.get().put(key, obj);
        }

        for (Field field : ReflectionUtils.getDeclaredAndInheritedFields(obj.getClass(), false)) {
            field.setAccessible(true);

            if (getAnnotationReader().isAnnotationPresent(field, JcrProperty.class) && nodeFilter.isDepthPropertyIncluded(depth)) {
                propertyMapper.mapPropertyToField(obj, field, node, depth, nodeFilter);
            } else if (getAnnotationReader().isAnnotationPresent(field, JcrSerializedProperty.class) && nodeFilter.isDepthPropertyIncluded(depth)) {
                propertyMapper.mapSerializedPropertyToField(obj, field, node, depth, nodeFilter);
            } else if (getAnnotationReader().isAnnotationPresent(field, JcrProtectedProperty.class)) {
                propertyMapper.mapProtectedPropertyToField(obj, field, node);
            } else if (getAnnotationReader().isAnnotationPresent(field, JcrIdentifier.class)) {
                getTypeHandler().setObject(field, obj, node.getIdentifier());
            } else if (getAnnotationReader().isAnnotationPresent(field, JcrBaseVersionName.class)) {
                if (isVersionable(node)) {
                    // Version baseVersion = node.getBaseVersion();
                    Version baseVersion = getVersionManager(node).getBaseVersion(node.getPath());
                    getTypeHandler().setObject(field, obj, baseVersion.getName());
                }
            } else if (getAnnotationReader().isAnnotationPresent(field, JcrBaseVersionCreated.class)) {
                if (isVersionable(node)) {
                    // Version baseVersion = node.getBaseVersion();
                    Version baseVersion = getVersionManager(node).getBaseVersion(node.getPath());
                    getTypeHandler().setObject(field, obj, getTypeHandler().getValue(field.getType(), null, getTypeHandler().createValue(Calendar.class, baseVersion.getCreated(), node.getSession().getValueFactory()), null));
                }
            } else if (getAnnotationReader().isAnnotationPresent(field, JcrVersionName.class)) {
                if (node.getParent() != null && node.getParent().isNodeType(NodeType.NT_VERSION)) {
                	getTypeHandler().setObject(field, obj, node.getParent().getName());
                } else if (isVersionable(node)) {
                    // if we're not browsing version history, then this must be the base version
                    //Version baseVersion = node.getBaseVersion();
                    Version baseVersion = getVersionManager(node).getBaseVersion(node.getPath());
                    getTypeHandler().setObject(field, obj, baseVersion.getName());
                }
            } else if (getAnnotationReader().isAnnotationPresent(field, JcrVersionCreated.class)) {
                if (node.getParent() != null && node.getParent().isNodeType(NodeType.NT_VERSION)) {
                    Version version = (Version) node.getParent();
                    getTypeHandler().setObject(field, obj, getTypeHandler().getValue(field.getType(), null, getTypeHandler().createValue(Calendar.class, version.getCreated(), node.getSession().getValueFactory()), null));
                } else if (isVersionable(node)) {
                    // if we're not browsing version history, then this must be the base version
                    //Version baseVersion = node.getBaseVersion();
                    Version baseVersion = getVersionManager(node).getBaseVersion(node.getPath());
                    getTypeHandler().setObject(field, obj, getTypeHandler().getValue(field.getType(), null, getTypeHandler().createValue(Calendar.class, baseVersion.getCreated(), node.getSession().getValueFactory()), null));
                }
            } else if (getAnnotationReader().isAnnotationPresent(field, JcrCheckedout.class)) {
            	getTypeHandler().setObject(field, obj, node.isCheckedOut());
            } else if (getAnnotationReader().isAnnotationPresent(field, JcrCreated.class)) {
                if (node.hasProperty(Property.JCR_CREATED)) {
                	getTypeHandler().setObject(field, obj, getTypeHandler().getValue(field.getType(), null, node.getProperty(Property.JCR_CREATED).getValue(), null));
                }
            } else if (getAnnotationReader().isAnnotationPresent(field, JcrParentNode.class)) {
                if (parentObject != null && getTypeHandler().getType(field.getType(), field.getGenericType(), obj).isInstance(parentObject)) {
                	getTypeHandler().setObject(field, obj, parentObject);
                }
            } else if (getAnnotationReader().isAnnotationPresent(field, JcrChildNode.class) && nodeFilter.isDepthIncluded(depth)) {
                childNodeMapper.getChildrenFromNode(field, node, obj, depth, nodeFilter);
            } else if (getAnnotationReader().isAnnotationPresent(field, JcrReference.class)) {
                referenceMapper.getReferencesFromNode(field, node, obj, depth, nodeFilter);
            } else if (getAnnotationReader().isAnnotationPresent(field, JcrFileNode.class) && nodeFilter.isDepthIncluded(depth)) {
                fileNodeMapper.getFilesFromNode(field, node, obj, depth, nodeFilter);
            } else if (getAnnotationReader().isAnnotationPresent(field, JcrPath.class)) {
            	getTypeHandler().setObject(field, obj, node.getPath());
            }
        }
        return obj;
    }
    
    /**
     * Transforms the entity supplied to a JCR node, and adds that node as a child to the parent node supplied.
     *
     * @param parentNode
     *            the parent node to which the entity node will be added
     * @param entity
     *            the entity to be mapped to the JCR node
     * @param mixinTypes
     *            an array of mixin type that will be added to the new node
     * @param action
     *            callback object that specifies the Jcrom actions:
     *            <ul>
     *              <li>{@link JcromCallback#doAddNode(Node, String, JcrNode, Object)},</li>
     *              <li>{@link JcromCallback#doAddMixinTypes(Node, String[], JcrNode, Object)},</li>
     *              <li>{@link JcromCallback#doComplete(Object, Node)},</li>
     *            </ul>
     * @return the newly created JCR node
     * @throws java.lang.Exception
     */
    @Override
    public Node addNode(Node parentNode, Object entity, String[] mixinTypes, JcromCallback action) throws IllegalAccessException, RepositoryException, IOException {
        return addNode(parentNode, entity, mixinTypes, true, action);
    }

    @Override
    public Node addNode(Node parentNode, Object entity, String[] mixinTypes, boolean createNode, JcromCallback action) throws IllegalAccessException, RepositoryException, IOException {
        entity = getTypeHandler().resolveAddEntity(entity);
        entity = clearCglib(entity);

        if (action == null) {
            action = new DefaultJcromCallback();
        }

        // create the child node
        Node node;
        JcrNode jcrNode = getTypeHandler().getJcrNodeAnnotation(entity.getClass(), entity.getClass().getGenericSuperclass(), entity);
        if (createNode) {
            // add node
            String nodeName = getCleanName(getNodeName(entity));
            node = action.doAddNode(parentNode, nodeName, jcrNode, entity);

            // add mixin types
            action.doAddMixinTypes(node, mixinTypes, jcrNode, entity);

            // update the object id, name and path
            setId(entity, node.getIdentifier());
            setNodeName(entity, node.getName());
            setNodePath(entity, node.getPath());
        } else {
            node = parentNode;
        }

        // add class name to property
        if (jcrNode != null && !jcrNode.classNameProperty().equals("none")) {
            action.doAddClassNameToProperty(node, jcrNode, entity);
        }

        // special handling of JcrFile objects
        if (JcrFile.class.isAssignableFrom(entity.getClass())) {
            fileNodeMapper.addFileNode(node, (JcrFile) entity);
        }

        for (Field field : ReflectionUtils.getDeclaredAndInheritedFields(entity.getClass(), true)) {
            field.setAccessible(true);

            if (getAnnotationReader().isAnnotationPresent(field, JcrProperty.class)) {
                propertyMapper.addProperty(field, entity, node);

            } else if (getAnnotationReader().isAnnotationPresent(field, JcrSerializedProperty.class)) {
                propertyMapper.addSerializedProperty(field, entity, node);

            } else if (getAnnotationReader().isAnnotationPresent(field, JcrChildNode.class)) {
                childNodeMapper.addChildren(field, entity, node);

            } else if (getAnnotationReader().isAnnotationPresent(field, JcrReference.class)) {
                referenceMapper.addReferences(field, entity, node);

            } else if (getAnnotationReader().isAnnotationPresent(field, JcrFileNode.class)) {
                fileNodeMapper.addFiles(field, entity, node);
            }
        }

        // complete the addition of the new node
        action.doComplete(entity, node);

        return node;
    }
    
    /**
     * Update an existing JCR node with the entity supplied.
     *
     * @param node
     *            the JCR node to be updated
     * @param entity
     *            the entity that will be mapped to the existing node
     * @param nodeFilter
     *            the NodeFilter to apply when updating child nodes and references
     * @param action
     *            callback object that specifies the Jcrom actions
     * @return the updated node
     * @throws java.lang.Exception
     */
    @Override
    public Node updateNode(Node node, Object entity, NodeFilter nodeFilter, JcromCallback action) throws RepositoryException, IllegalAccessException, IOException {
        return updateNode(node, entity, entity.getClass(), nodeFilter, 0, action);
    }

    @Override
    public Node updateNode(Node node, Object entity, Class<?> entityClass, NodeFilter nodeFilter, int depth, JcromCallback action) throws RepositoryException, IllegalAccessException, IOException {

        entity = clearCglib(entity);

        if (nodeFilter == null) {
            nodeFilter = new NodeFilter(NodeFilter.INCLUDE_ALL, NodeFilter.DEPTH_INFINITE);
        }

        if (action == null) {
            action = new DefaultJcromCallback();
        }

        // map the class name to a property
        JcrNode jcrNode = ReflectionUtils.getJcrNodeAnnotation(entityClass);
        if (jcrNode != null && !jcrNode.classNameProperty().equals("none")) {
            // check if the class of the object has changed
            if (node.hasProperty(jcrNode.classNameProperty())) {
                String oldClassName = node.getProperty(jcrNode.classNameProperty()).getString();
                if (!oldClassName.equals(entity.getClass().getCanonicalName())) {
                    // different class, so we should remove the properties of the old class
                    Class<?> oldClass = getClassForName(oldClassName);
                    if (oldClass != null) {
                        Class<?> newClass = entity.getClass();

                        Set<Field> oldFields = new HashSet<Field>();
                        oldFields.addAll(Arrays.asList(ReflectionUtils.getDeclaredAndInheritedFields(oldClass, true)));
                        oldFields.removeAll(Arrays.asList(ReflectionUtils.getDeclaredAndInheritedFields(newClass, true)));

                        // remove the old fields
                        for (Field field : oldFields) {
                            if (node.hasProperty(field.getName())) {
                                node.getProperty(field.getName()).remove();
                            }
                        }
                    }
                }
            }
            action.doUpdateClassNameToProperty(node, jcrNode, entity);
        }

        // special handling of JcrFile objects
        if (JcrFile.class.isAssignableFrom(entity.getClass()) && depth == 0) {
            fileNodeMapper.addFileNode(node, (JcrFile) entity);
        }

        for (Field field : ReflectionUtils.getDeclaredAndInheritedFields(entityClass, true)) {
            field.setAccessible(true);

            if (getAnnotationReader().isAnnotationPresent(field, JcrProperty.class) && nodeFilter.isDepthPropertyIncluded(depth)) {
                propertyMapper.updateProperty(field, entity, node, depth, nodeFilter);

            } else if (getAnnotationReader().isAnnotationPresent(field, JcrSerializedProperty.class) && nodeFilter.isDepthPropertyIncluded(depth)) {
                propertyMapper.updateSerializedProperty(field, entity, node, depth, nodeFilter);

            } else if (getAnnotationReader().isAnnotationPresent(field, JcrChildNode.class) && nodeFilter.isDepthIncluded(depth)) {
                // child nodes
                childNodeMapper.updateChildren(field, entity, node, depth, nodeFilter);

            } else if (getAnnotationReader().isAnnotationPresent(field, JcrReference.class)) {
                // references
                referenceMapper.updateReferences(field, entity, node, nodeFilter);

            } else if (getAnnotationReader().isAnnotationPresent(field, JcrFileNode.class) && nodeFilter.isDepthIncluded(depth)) {
                // file nodes
                fileNodeMapper.updateFiles(field, entity, node, depth, nodeFilter);
            }
        }

        // if name is different, then we move the node
        if (!node.getName().equals(getCleanName(getNodeName(entity)))) {
            boolean isVersionable = JcrUtils.hasMixinType(node, "mix:versionable") || JcrUtils.hasMixinType(node, NodeType.MIX_VERSIONABLE);
            Node parentNode = node.getParent();

            if (isVersionable) {
                if (JcrUtils.hasMixinType(parentNode, "mix:versionable") || JcrUtils.hasMixinType(parentNode, NodeType.MIX_VERSIONABLE)) {
                    JcrUtils.checkout(parentNode);
                }
            }

            // move node
            String nodeName = getCleanName(getNodeName(entity));
            action.doMoveNode(parentNode, node, nodeName, jcrNode, entity);

            if (isVersionable) {
                if ((JcrUtils.hasMixinType(parentNode, "mix:versionable") || JcrUtils.hasMixinType(parentNode, NodeType.MIX_VERSIONABLE)) && parentNode.isCheckedOut()) {
                    // Save session changes before checking-in the parent node
                    node.getSession().save();
                    JcrUtils.checkin(parentNode);
                }
            }

            // update the object name and path
            setNodeName(entity, node.getName());
            setNodePath(entity, node.getPath());
        }

        // complete the update of the node
        action.doComplete(entity, node);

        return node;
    }
    
    /**
     * Check if this node has a child version history reference. If so, then return the referenced node, else return the
     * node supplied.
     *
     * @param node
     * @return
     * @throws javax.jcr.RepositoryException
     */
    @Override
    public Node checkIfVersionedChild(Node node) throws RepositoryException {
        if (node.hasProperty(Property.JCR_CHILD_VERSION_HISTORY)) {
            //Node versionNode = node.getSession().getNodeByUUID(node.getProperty("jcr:childVersionHistory").getString());
            Node versionNode = getNodeById(node, node.getProperty(Property.JCR_CHILD_VERSION_HISTORY).getString());
            NodeIterator it = versionNode.getNodes();
            while (it.hasNext()) {
                Node n = it.nextNode();
                if ((!n.getName().equals("jcr:rootVersion") && !n.getName().equals(Node.JCR_ROOT_VERSION)) && n.isNodeType(NodeType.NT_VERSION) && n.hasNode(Node.JCR_FROZEN_NODE) && node.getPath().indexOf("/" + n.getName() + "/") != -1) {
                    return n.getNode(Node.JCR_FROZEN_NODE);
                }
            }
            return node;
        } else {
            return node;
        }
    }
    
    /**
     * This is a temporary solution to enable lazy loading of single child nodes and single references. The problem is
     * that Jcrom uses direct field modification, but CGLIB fails to cascade field changes between the enhanced class
     * and the lazy object.
     *
     * @param obj
     * @return
     * @throws java.lang.IllegalAccessException
     */
    @Override
    public Object clearCglib(Object obj) throws IllegalAccessException {
        for (Field field : ReflectionUtils.getDeclaredAndInheritedFields(obj.getClass(), true)) {
            field.setAccessible(true);
            if (field.getName().equals(FN_CGLIB_LAZY_LOADER)) {
                Object object = getTypeHandler().getObject(field, obj);
                if (object != null) {
                    return object;
                } else {
                    // lazy loading has not been triggered yet, so
                    // we do it manually
                    return triggerLazyLoading(obj);
                }
            }
        }
        return obj;
    }
  
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    
    private Object triggerLazyLoading(Object obj) throws IllegalAccessException {
        for (Field field : ReflectionUtils.getDeclaredAndInheritedFields(obj.getClass(), false)) {
            field.setAccessible(true);
            if (field.getName().equals(FN_CGLIB_CALLBACK)) {
                try {
                    return ((LazyLoader) getTypeHandler().getObject(field, obj)).loadObject();
                } catch (Exception e) {
                    throw new JcrMappingException("Could not trigger lazy loading", e);
                }
            }
        }
        return obj;
    }
    
    /**
     * Class for the history key. Contains the node path and the depth.
     * Thanks to Leander for supplying this fix.
     */
    private static class HistoryKey {

        private String path;
        private int depth;

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + depth;
            result = prime * result + ((path == null) ? 0 : path.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            HistoryKey other = (HistoryKey) obj;
            if (depth != other.depth) {
                return false;
            }
            if (path == null) {
                if (other.path != null) {
                    return false;
                }
            } else if (!path.equals(other.path)) {
                return false;
            }
            return true;
        }
    }
}