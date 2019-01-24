package org.jcrom.mapping;

import java.io.IOException;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.jcrom.AnnotationReader;
import org.jcrom.callback.JcromCallback;
import org.jcrom.engine.spi.JcrSessionImplementor;
import org.jcrom.type.TypeHandler;
import org.jcrom.util.NodeFilter;

public interface MapperImplementor extends Mapper {

	@Override
	JcrSessionImplementor getSession();
	
	TypeHandler getTypeHandler();
	
	AnnotationReader getAnnotationReader();
	
	Object createInstanceForNode(Class<?> objClass, Node node) throws RepositoryException, IllegalAccessException, ClassNotFoundException, InstantiationException;
	Object findEntityByPath(List<?> entities, String path) throws IllegalAccessException;
	
	String getNodeId(Object object) throws IllegalAccessException;
	void setId(Object object, String id) throws IllegalAccessException;
	
	String getNodePath(Object object) throws IllegalAccessException;
	void setNodePath(Object object, String path) throws IllegalAccessException;
	
	String getNodeName(Object object) throws IllegalAccessException;	
	void setNodeName(Object object, String name) throws IllegalAccessException;
	
	Class<?> findClassFromNode(Class<?> defaultClass, Node node) 
			throws RepositoryException, IllegalAccessException, ClassNotFoundException, InstantiationException;
	Object mapNodeToClass(Object obj, Node node, NodeFilter nodeFilter, Object parentObject, int depth) 
			throws ClassNotFoundException, InstantiationException, RepositoryException, IllegalAccessException, IOException;
	
	Node addNode(Node parentNode, Object entity, String[] mixinTypes, JcromCallback action) throws IllegalAccessException, RepositoryException, IOException;
	Node addNode(Node parentNode, Object entity, String[] mixinTypes, boolean createNode, JcromCallback action) throws IllegalAccessException, RepositoryException, IOException;
	Node updateNode(Node node, Object entity, NodeFilter nodeFilter, JcromCallback action) throws RepositoryException, IllegalAccessException, IOException;
	Node updateNode(Node node, Object entity, Class<?> entityClass, NodeFilter nodeFilter, int depth, JcromCallback action) throws RepositoryException, IllegalAccessException, IOException;
	
	Node checkIfVersionedChild(Node node) throws RepositoryException;	
	
	Object clearCglib(Object obj) throws IllegalAccessException;
}
