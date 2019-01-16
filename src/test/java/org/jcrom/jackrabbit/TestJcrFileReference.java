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

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.Calendar;

import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;

import org.jcrom.JcrDataProviderImpl;
import org.jcrom.JcrMappingException;
import org.jcrom.Jcrom;
import org.jcrom.entities.JcrFileReferenceByPathParentNode;
import org.jcrom.entities.JcrFileReferenceParentNode;
import org.jcrom.entities.ReferenceableJCRFile;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author Robin Wyles
 * @author Olafur Gauti Gudmundsson
 * @author Nicolas Dos Santos
 */
public class TestJcrFileReference extends TestAbstract {
	private static final Logger LOGGER = LoggerFactory.getLogger(TestJcrFileReference.class);
	
	@Before
	public void setUpRepository() throws Exception {
		super.setUpRepository();
	}

	@After
	public void tearDownRepository() throws Exception {
		super.tearDownRepository();
	}
	
    @Test
    public void testFileReference() throws JcrMappingException, RepositoryException {
        LOGGER.info("JcrFileNode with reference");

        // initialise jcrom
        jcrom.map(JcrFileReferenceParentNode.class);
        jcrom.map(ReferenceableJCRFile.class);

        // initialise mappable objects
        ReferenceableJCRFile fileRef = new ReferenceableJCRFile();
        fileRef.setId("12345");
        fileRef.setName("fileRef");
        fileRef.setMimeType("image/jpeg");
        fileRef.setEncoding("UTF-8");

        File imageFile = new File("src/test/resources/ogg.jpg");

        Calendar lastModified = Calendar.getInstance();
        lastModified.setTimeInMillis(imageFile.lastModified());
        fileRef.setLastModified(lastModified);

        fileRef.setDataProvider(new JcrDataProviderImpl(imageFile));

        JcrFileReferenceParentNode parent = new JcrFileReferenceParentNode();
        parent.setName("parent");
        parent.setbRef(fileRef);

        jcrom.addNode(getRootNode(), fileRef, new String[] { "mix:referenceable" });
        String instanceBID = getRootNode().getNode("fileRef").getIdentifier();
        jcrom.addNode(getRootNode(), parent);

        assertEquals(getRootNode().getNode("parent").getProperty("bRef").getType(), PropertyType.REFERENCE);
        assertEquals(getRootNode().getNode("parent").getProperty("bRef").getString(), instanceBID);

        JcrFileReferenceParentNode entityFromJcr = jcrom.fromNode(JcrFileReferenceParentNode.class, getRootNode().getNode("parent"));
        assertEquals("image/jpeg", entityFromJcr.getbRef().getMimeType());
        assertEquals(imageFile.length(), entityFromJcr.getbRef().getDataProvider().getContentLength());
        ///session.save();
    }

    @Test
    public void testFileReferenceByPath() throws JcrMappingException, RepositoryException {
        LOGGER.info("JcrFileNode referenced by path");

        // initialise jcrom
        jcrom.map(JcrFileReferenceByPathParentNode.class);
        jcrom.map(ReferenceableJCRFile.class);

        // initialise mappable objects
        ReferenceableJCRFile fileRef = new ReferenceableJCRFile();
        fileRef.setId("12345");
        fileRef.setName("fileRef");
        fileRef.setMimeType("image/jpeg");
        fileRef.setEncoding("UTF-8");

        File imageFile = new File("src/test/resources/ogg.jpg");

        Calendar lastModified = Calendar.getInstance();
        lastModified.setTimeInMillis(imageFile.lastModified());
        fileRef.setLastModified(lastModified);

        fileRef.setDataProvider(new JcrDataProviderImpl(imageFile));

        JcrFileReferenceByPathParentNode parent = new JcrFileReferenceByPathParentNode();
        parent.setName("parent");
        parent.setbRef(fileRef);

        jcrom.addNode(getRootNode(), fileRef, new String[] { "mix:referenceable" });
        String instancePath = getRootNode().getNode("fileRef").getPath();
        jcrom.addNode(getRootNode(), parent);

        assertEquals(getRootNode().getNode("parent").getProperty("bRef").getType(), PropertyType.STRING);
        assertEquals(getRootNode().getNode("parent").getProperty("bRef").getString(), instancePath);

        JcrFileReferenceByPathParentNode entityFromJcr = jcrom.fromNode(JcrFileReferenceByPathParentNode.class, getRootNode().getNode("parent"));
        assertEquals("image/jpeg", entityFromJcr.getbRef().getMimeType());
        assertEquals(imageFile.length(), entityFromJcr.getbRef().getDataProvider().getContentLength());
        ///session.save();
    }

}
