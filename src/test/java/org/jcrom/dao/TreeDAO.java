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
package org.jcrom.dao;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.jcrom.Jcrom;
import org.jcrom.dao.AbstractJcrDAO;
import org.jcrom.entities.Tree;

/**
 * 
 * @author Olafur Gauti Gudmundsson
 * @author Nicolas Dos Santos
 */
public class TreeDAO extends AbstractJcrDAO<Tree> {

    public TreeDAO(Jcrom jcrom) throws RepositoryException {
        super(jcrom);
    }

    @Override
    public Tree create(Tree entity) {
        Tree result = super.create(entity);
        return result;
    }

    @Override
    public Tree loadById(String id) {
        Tree result = super.loadById(id);
        return result;
    }

    @Override
    public Tree update(Tree entity) {
        Tree result = super.update(entity);
        return result;
    }
}
