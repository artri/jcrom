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
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jcr.NamespaceRegistry;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

import org.jcrom.AnnotationReader;
import org.jcrom.JcrFile;
import org.jcrom.annotations.JcrFileNode;
import org.jcrom.annotations.JcrReference;
import org.jcrom.engine.spi.JcrSessionImplementor;
import org.jcrom.loader.ProxyFactory;
import org.jcrom.type.TypeHandler;
import org.jcrom.util.NodeFilter;
import org.jcrom.util.PathUtils;
import org.jcrom.util.ReflectionUtils;

/**
 * This class handles mappings of type @JcrReference
 * 
 * @author Olafur Gauti Gudmundsson
 * @author Nicolas Dos Santos
 */
class ReferenceMapper {

    private JcrSessionImplementor session;

    public ReferenceMapper(JcrSessionImplementor session) {
    	this.session = session;
    }

    private MapperImplementor getMapper() {
    	return session.getMapper();
    }
    
	private TypeHandler getTypeHandler() {
        return session.getTypeHandler();
    }
    
    private AnnotationReader getAnnotationReader() {
        return session.getAnnotationReader();
    }
    
    private String getPropertyName(Field field) {
        JcrReference jcrReference = getAnnotationReader().getAnnotation(field, JcrReference.class);
        String name = field.getName();
        if (!jcrReference.name().equals(Mapper.DEFAULT_FIELDNAME)) {
            name = jcrReference.name();
        }
        return name;
    }

    private List<Value> getReferenceValues(List<?> references, JcrReference jcrReference) throws IllegalAccessException, RepositoryException {
        List<Value> refValues = new ArrayList<Value>();
        for (Object reference : references) {
            if (jcrReference.byPath()) {
                String referencePath = getMapper().getNodePath(reference);
                if (referencePath != null && !referencePath.equals("")) {
                    if (session.hasNode(referencePath)) {
                        refValues.add(session.createValue(referencePath));
                    }
                }
            } else {
                String referenceId = getMapper().getNodeId(reference);
                if (referenceId != null && !referenceId.equals("")) {
                    Node referencedNode = session.getNodeById(referenceId);
                    Value value;
                    if (jcrReference.weak()) {
                        value = session.createValue(referencedNode, true);
                    } else {
                        value = session.createValue(referencedNode);
                    }
                    refValues.add(value);
                }
            }
        }
        return refValues;
    }

    private void addSingleReferenceToNode(Field field, Object obj, String propertyName, Node node) throws IllegalAccessException, RepositoryException {
        // extract the Identifier from the object, load the node, and add a reference to it
        JcrReference jcrReference = getAnnotationReader().getAnnotation(field, JcrReference.class);
        Object referenceObject = getTypeHandler().getObject(field, obj);
        if (referenceObject != null) {
            referenceObject = getMapper().clearCglib(referenceObject);
        }

        if (referenceObject != null) {
            mapSingleReference(jcrReference, referenceObject, node, propertyName);
        } else {
            // remove the reference
            node.setProperty(propertyName, (Value) null);
        }
    }

    private void addMultipleReferencesToNode(Field field, Object obj, String propertyName, Node node) throws IllegalAccessException, RepositoryException {

        JcrReference jcrReference = getAnnotationReader().getAnnotation(field, JcrReference.class);
        List<?> references = (List<?>) field.get(obj);
        if (node.hasProperty(propertyName) && !node.getProperty(propertyName).getDefinition().isMultiple()) {
            node.setProperty(propertyName, (Value) null);
            //node.save();
            node.getSession().save();
        }
        if (references != null && !references.isEmpty()) {
            List<Value> refValues = getReferenceValues(references, jcrReference);
            if (!refValues.isEmpty()) {
                node.setProperty(propertyName, refValues.toArray(new Value[refValues.size()]));
            } else if (node.hasProperty(propertyName)) {
                node.setProperty(propertyName, (Value[]) null);
            }
        } else if (node.hasProperty(propertyName)) {
            node.setProperty(propertyName, (Value[]) null);
        }
    }

    private void mapSingleReference(JcrReference jcrReference, Object referenceObject, Node containerNode, String propertyName) throws IllegalAccessException, RepositoryException {

        if (jcrReference.byPath()) {
            String referencePath = getMapper().getNodePath(referenceObject);
            if (referencePath != null && !referencePath.equals("")) {
                if (containerNode.getSession().getRootNode().hasNode(PathUtils.relativePath(referencePath))) {
                    containerNode.setProperty(propertyName, containerNode.getSession().getValueFactory().createValue(referencePath));
                }
            }
        } else {
            String referenceId = getMapper().getNodeId(referenceObject);
            if (referenceId != null && !referenceId.equals("")) {
                Node referencedNode = session.getNodeById(referenceId);
                if (jcrReference.weak()) {
                    containerNode.setProperty(propertyName, containerNode.getSession().getValueFactory().createValue(referencedNode, true));
                } else {
                    containerNode.setProperty(propertyName, referencedNode);
                }
            } else {
                // remove the reference
                containerNode.setProperty(propertyName, (Value) null);
            }
        }
    }

    /**
     * Maps a Map<String,Object> or Map<String,List<Object>> to a JCR Node.
     */
    private void addMapOfReferencesToNode(Field field, Object obj, String containerName, Node node) throws IllegalAccessException, RepositoryException {

        JcrReference jcrReference = getAnnotationReader().getAnnotation(field, JcrReference.class);

        // remove previous references if they exist
        if (node.hasNode(containerName)) {
            node.getNode(containerName).remove();
        }

        // create a reference container
        Node referenceContainer = node.addNode(containerName);

        // map the references as properties on the container node
        Map<?, ?> referenceMap = (Map<?, ?>) field.get(obj);
        if (referenceMap != null && !referenceMap.isEmpty()) {
            Class<?> paramClass = ReflectionUtils.getParameterizedClass(field.getGenericType(), 1);
            for (Map.Entry<?, ?> entry : referenceMap.entrySet()) {
                String key = (String) entry.getKey();
                if (getTypeHandler().isList(paramClass)) {
                    List<?> references = (List<?>) entry.getValue();
                    List<Value> refValues = getReferenceValues(references, jcrReference);
                    if (refValues != null && !refValues.isEmpty()) {
                        referenceContainer.setProperty(key, refValues.toArray(new Value[refValues.size()]));
                    }

                } else {
                    Object referenceObject = entry.getValue();
                    if (referenceObject != null) {
                    	mapSingleReference(jcrReference, referenceObject, referenceContainer, key);
                    }
                }
            }
        }
    }

    private void setReferenceProperties(Field field, Object obj, Node node, NodeFilter nodeFilter) throws IllegalAccessException, RepositoryException {

        String propertyName = getPropertyName(field);

        // make sure that the reference should be updated
        if (nodeFilter == null || nodeFilter.isNameIncluded(field.getName())) {
            if (getTypeHandler().isList(field.getType())) {
                // multiple references in a List
                addMultipleReferencesToNode(field, obj, propertyName, node);
            } else if (getTypeHandler().isMap(field.getType())) {
                // multiple references in a Map
                addMapOfReferencesToNode(field, obj, propertyName, node);
            } else {
                // single reference object
                addSingleReferenceToNode(field, obj, propertyName, node);
            }
        }
    }

    void addReferences(Field field, Object obj, Node node) throws IllegalAccessException, RepositoryException {
        setReferenceProperties(field, obj, node, null);
    }

    void updateReferences(Field field, Object obj, Node node, NodeFilter nodeFilter) throws IllegalAccessException, RepositoryException {
        setReferenceProperties(field, obj, node, nodeFilter);
    }

    private Node getSingleReferencedNode(JcrReference jcrReference, Value value) throws RepositoryException {
        if (jcrReference.byPath()) {
            if (session.hasNode(value.getString())) {
                return session.getNode(value.getString());
            }
        } else {
            return session.getNodeById(value.getString());
        }
        return null;
    }

    Object createReferencedObject(Field field, Value value, Object obj, Class<?> referenceObjClass, int depth, NodeFilter nodeFilter) throws ClassNotFoundException, InstantiationException, RepositoryException, IllegalAccessException, IOException {

        JcrReference jcrReference = getAnnotationReader().getAnnotation(field, JcrReference.class);
        Node referencedNode = null;

        if (jcrReference.byPath()) {
            if (session.hasNode(value.getString())) {
                referencedNode = session.getNode(value.getString());
            }
        } else {
            referencedNode = session.getNodeById(value.getString());
        }

        if (referencedNode != null) {
            Object referencedObject = getMapper().createInstanceForNode(referenceObjClass, referencedNode);
            if (nodeFilter.isIncluded(field.getName(), depth)) {
                // load and map the object, we don't send the current object as parent
                referencedObject = getMapper().mapNodeToClass(referencedObject, referencedNode, nodeFilter, null, depth + 1);
            } else {
                if (jcrReference.byPath()) {
                    // just store the path
                	getMapper().setNodePath(referencedObject, value.getString());
                } else {
                    // just store the Identifier
                	getMapper().setId(referencedObject, value.getString());
                }
            }
            // support JcrFileNode annotation for references
            if (field.isAnnotationPresent(JcrFileNode.class) && referencedObject instanceof JcrFile) {
                JcrFileNode fileNode = field.getAnnotation(JcrFileNode.class);
                FileNodeMapper.addFileProperties((JcrFile) referencedObject, referencedNode, fileNode, depth, nodeFilter);
            }
            return referencedObject;
        } else {
            return null;
        }
    }

    List<?> getReferenceList(Field field, String propertyName, Class<?> referenceObjClass, Node node, Object obj, int depth, NodeFilter nodeFilter) throws ClassNotFoundException, InstantiationException, RepositoryException, IllegalAccessException, IOException {

        List<Object> references = new ArrayList<Object>();
        if (node.hasProperty(propertyName)) {

            Value[] refValues;

            if (node.getProperty(propertyName).getDefinition().isMultiple()) {
                refValues = node.getProperty(propertyName).getValues();
            } else {
                refValues = new Value[] { node.getProperty(propertyName).getValue() };
            }

            for (Value value : refValues) {
                Object referencedObject = createReferencedObject(field, value, obj, referenceObjClass, depth, nodeFilter);
                references.add(referencedObject);
            }

        }

        return references;

    }

    Map<String, Object> getReferenceMap(Field field, String containerName, Class<?> mapParamClass, Node node, Object obj, int depth, NodeFilter nodeFilter, JcrReference jcrReference) throws ClassNotFoundException, InstantiationException, RepositoryException, IllegalAccessException, IOException {

        Map<String, Object> references = new HashMap<String, Object>();
        if (node.hasNode(containerName)) {
            Node containerNode = node.getNode(containerName);
            PropertyIterator propertyIterator = containerNode.getProperties();
            while (propertyIterator.hasNext()) {
                Property p = propertyIterator.nextProperty();
                if (!p.getName().startsWith("jcr:") && !p.getName().startsWith(NamespaceRegistry.NAMESPACE_JCR)) {
                    if (getTypeHandler().isList(mapParamClass)) {
                        if (jcrReference.lazy()) {
                        	// lazy loading
                        	references.put(p.getName(), ProxyFactory.createReferenceListProxy(session, mapParamClass, obj, containerNode.getPath(), p.getName(), depth, nodeFilter, field));
                        } else {
                        	// eager loading
                            references.put(p.getName(), getReferenceList(field, p.getName(), mapParamClass, containerNode, obj, depth, nodeFilter));
                        }
                    } else {
                        if (jcrReference.lazy()) {
                        	// lazy loading
                            Node referencedNode = getSingleReferencedNode(jcrReference, p.getValue());
                            references.put(p.getName(), ProxyFactory.createReferenceProxy(session, getMapper().findClassFromNode(mapParamClass, referencedNode), obj, containerNode.getPath(), p.getName(), depth, nodeFilter, field));
                        } else {
                        	// eager loading
                        	references.put(p.getName(), createReferencedObject(field, p.getValue(), obj, mapParamClass, depth, nodeFilter));
                        }
                    }
                }
            }
        }
        return references;
    }

    void getReferencesFromNode(Field field, Node node, Object obj, int depth, NodeFilter nodeFilter) throws ClassNotFoundException, InstantiationException, RepositoryException, IllegalAccessException, IOException {

        String propertyName = getPropertyName(field);
        JcrReference jcrReference = getAnnotationReader().getAnnotation(field, JcrReference.class);

        if (getTypeHandler().isList(field.getType())) {
            // multiple references in a List
            Class<?> referenceObjClass = ReflectionUtils.getParameterizedClass(field.getGenericType());
            List value = null;
            if (jcrReference.lazy()) {
                // lazy loading
                value = ProxyFactory.createReferenceListProxy(session, referenceObjClass, obj, node.getPath(), propertyName, depth, nodeFilter, field);
            } else {
                // eager loading
                value = getReferenceList(field, propertyName, referenceObjClass, node, obj, depth, nodeFilter);
            }
            getTypeHandler().setObject(field, obj, value);
        } else if (getTypeHandler().isMap(field.getType())) {
            // multiple references in a Map
            // lazy loading is applied to each value in the Map
            Class<?> mapParamClass = ReflectionUtils.getParameterizedClass(field.getGenericType(), 1);
            Map<String, Object> value = getReferenceMap(field, propertyName, mapParamClass, node, obj, depth, nodeFilter, jcrReference);
            getTypeHandler().setObject(field, obj, value);
        } else {
            // single reference
            if (node.hasProperty(propertyName)) {
                Class<?> referenceObjClass = getTypeHandler().getType(field.getType(), field.getGenericType(), obj);
                Object value = null;
                if (jcrReference.lazy()) {
                    value = ProxyFactory.createReferenceProxy(session, referenceObjClass, obj, node.getPath(), propertyName, depth, nodeFilter, field);
                } else {
                    value = createReferencedObject(field, node.getProperty(propertyName).getValue(), obj, referenceObjClass, depth, nodeFilter);
                }
                getTypeHandler().setObject(field, obj, value);
            }
        }
    }
}
