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
import static org.junit.Assert.assertNotNull;

import javax.jcr.Node;

import org.jcrom.Jcrom;
import org.jcrom.entities.Child;
import org.jcrom.entities.DynamicObject;
import org.jcrom.entities.ReferencedEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Thanks to Vincent Gigure for providing this test case.
 *
 * @author Vincent Gigure
 * @author Olafur Gauti Gudmundsson
 * @author Nicolas Dos Santos
 */
public class TestInstantiation extends TestAbstract {
	@Before
	public void setUpRepository() throws Exception {
		super.setUpRepository();
	}

	@After
	public void tearDownRepository() throws Exception {
		super.tearDownRepository();
	}
	
    @Test
    public void test_dynamic_map_instantiation() throws Exception {
        jcrom.map(DynamicObject.class).map(ReferencedEntity.class);

        Node rootNode = getRootNode().addNode("test");

        DynamicObject dyna = new DynamicObject();
        dyna.setName("Dynamic");

        ReferencedEntity child = new ReferencedEntity();
        child.setName("Child");

        dyna.putSingleReference("childOne", jcrom.fromNode(ReferencedEntity.class, jcrom.addNode(rootNode, child, new String[] { "mix:referenceable" })));
        dyna.putSingleReference("childTwo", jcrom.fromNode(ReferencedEntity.class, jcrom.addNode(rootNode, child, new String[] { "mix:referenceable" })));

        assertEquals(2, dyna.getSingleReferences().size());
        assertNotNull(dyna.getSingleReferences().get("childOne"));
        assertNotNull(dyna.getSingleReferences().get("childTwo"));

        DynamicObject loaded = jcrom.fromNode(DynamicObject.class, jcrom.addNode(rootNode, dyna));

        assertEquals(2, loaded.getSingleReferences().size());
        assertNotNull(loaded.getSingleReferences().get("childOne"));
        assertNotNull(loaded.getSingleReferences().get("childTwo"));

    }

    @Test
    public void test_dynamic_maps_stored_as_child_nodes_can_be_retrieved_by_key() throws Exception {
        jcrom.map(DynamicObject.class).map(Child.class);

        Node rootNode = getRootNode().addNode("test");

        DynamicObject dyna = new DynamicObject();
        dyna.setName("Dynamic");

        dyna.putSingleValueChild("childOne", createChildWithName("childName1"));
        dyna.putSingleValueChild("childTwo", createChildWithName("childName2"));
        dyna.putSingleValueChild("childThree", createChildWithName("childName3"));

        assertEquals(3, dyna.getSingleValueChildren().size());

        DynamicObject loaded = jcrom.fromNode(DynamicObject.class, jcrom.addNode(rootNode, dyna));

        assertEquals(3, loaded.getSingleValueChildren().size());
        assertNotNull(loaded.getSingleValueChildren().get("childOne"));
        assertNotNull(loaded.getSingleValueChildren().get("childTwo"));
        assertNotNull(loaded.getSingleValueChildren().get("childThree"));

    }

    private Child createChildWithName(String name) {
        Child child = new Child();
        child.setName(name);
        return child;
    }
}
