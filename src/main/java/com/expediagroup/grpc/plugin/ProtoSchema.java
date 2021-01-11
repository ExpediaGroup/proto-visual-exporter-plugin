/**
 * Copyright (C) 2019 Expedia, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.expediagroup.grpc.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ProtoSchema is the internal view model of the protocol buffers.
 */
public class ProtoSchema {

    private final static Logger LOGGER = LogManager.getLogger(ProtoSchema.class);

    private Map<String, Entity> entities = new ConcurrentHashMap<>();
    private Set<RelationShip> relationships = new HashSet<>();

    public void registerEntity(String entityName, String descriptorPackage) {
        if (!entities.containsKey(entityName)) {
            final String domain = StringUtils.isNotBlank(descriptorPackage)
                    ? descriptorPackage
                    : "unknown";
            final Entity entity = new Entity();
            entity.name = entityName;
            entity.domain = domain;
            entities.put(entity.name, entity);
        }
    }

    public void registerEntityAttribute(String entityName, String attributeName, String attributeType) {
        if (entities.containsKey(entityName)) {
            final Entity entity = entities.get(entityName);
            entity.attributes.put(attributeName, attributeType);
        }
    }

    public void registerRelationship(String type, String fieldName, String typeUsed) {
        final RelationShip rel = new RelationShip();
        rel.type = type;
        rel.fieldName = fieldName;
        rel.typeUsed = typeUsed;
        relationships.add(rel);
    }

    public Map<String, Entity> getEntities() {
        return entities;
    }


    public Set<RelationShip> getRelationships() {
        return relationships;
    }

    /**
     * Print out a JSON representation of the ProtoSchema
     * @return
     */
    public String toString() {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(this);
        } catch(Exception ex) {
            throw new RuntimeException(ex);
        }
    }


    public static class Entity {
        private String name;
        private String domain;
        private Map<String, String> attributes = new ConcurrentHashMap<>();

        public String getName() {
            return name;
        }

        public String getDomain() {
            return domain;
        }

        public Map<String, String> getAttributes() {
            return attributes;
        }

    }

    public static class RelationShip {
        private String type;
        private String fieldName;
        private String typeUsed;

        public String getType() {
            return type;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getTypeUsed() {
            return typeUsed;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RelationShip that = (RelationShip) o;
            return type.equals(that.type) &&
                    fieldName.equals(that.fieldName) &&
                    typeUsed.equals(that.typeUsed);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, fieldName, typeUsed);
        }
    }
}
