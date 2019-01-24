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
package org.jcrom.jackrabbit;

import java.io.File;

import javax.jcr.Node;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;

import org.apache.jackrabbit.core.TransientRepository;
import org.jcrom.Jcrom;
import org.jcrom.internal.JcrSessionFactoryImpl;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author Olafur Gauti Gudmundsson
 * @author Nicolas Dos Santos
 */
public class TestAbstract {
	private static final Logger LOGGER = LoggerFactory.getLogger(TestAbstract.class);
	
    protected static Repository repo;
    protected static String userID = "admin";
    protected static char[] password = "admin".toCharArray();

    protected static Jcrom jcrom;
    
    @BeforeClass
    public static void beforeClass() {
    	LOGGER.info("Setting up repository");
    	
        deleteDir(new File("repository"));
        new File("repository.xml").delete();
        new File("derby.log").delete();

        repo = new TransientRepository();
        
        jcrom = new Jcrom(true, true);
        jcrom.setSessionFactory(new JcrSessionFactoryImpl(repo, new SimpleCredentials(userID, password)));    	
    }
    
    @AfterClass
    public static void afterClass() {
    	LOGGER.info("Shutting down repository");
    	
    	jcrom.getSessionFactory().close();
    	
        deleteDir(new File("repository"));
        new File("repository.xml").delete();
        new File("derby.log").delete();    	
    }
    
    @Before
    public void setUpRepository() throws Exception {
    	jcrom.getSessionFactory().invalidate();
    }

    @After
    public void tearDownRepository() throws Exception {
    	jcrom.getSessionFactory().invalidate();
    }

    protected Session getSession() {
    	return jcrom.getSessionFactory().getCurrentSession();
    }
    
    protected Node getRootNode() throws RepositoryException {
    	return getSession().getRootNode();
    }
    
    protected void save() throws RepositoryException {
    	getSession().save();
    }
    
    public static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (String element : children) {
                boolean success = deleteDir(new File(dir, element));
                if (!success) {
                    return false;
                }
            }
        }

        // The directory is now empty so delete it
        return dir.delete();
    }
}
