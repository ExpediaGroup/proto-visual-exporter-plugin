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
package com.expediagroup.grpc.plugin.neo4j;

import com.expediagroup.grpc.plugin.ProtoSchema;
import com.expediagroup.grpc.plugin.ProtoSchemaExporter;
import com.google.common.annotations.VisibleForTesting;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.util.StringUtils.hasText;

/**
 * Exports the ProtoSchema view model to Neo4j.
 */
public class Neo4JProtoSchemaExporter implements ProtoSchemaExporter {

    private Map<String, String> nodes = new ConcurrentHashMap<>();
    protected static final String LOG_FILE_NAME = "neo4j-query-log.txt";
    protected static final String FULL_NAME_ATTRIBUTE_KEY = "_full_name_";
    protected static final String DOMAIN_ATTRIBUTE_KEY = "_domain_";
    protected static final String RELATIONSHIP_FIELD_KEY = "field";

    private Neo4jClient neo4jClient;

    /**
     * Constructor used to test by injecting a mock ne4ojclient
     * @param neo4jClient
     */
    @VisibleForTesting
    public Neo4JProtoSchemaExporter(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    /**
     * Neo4j server parameters
     * @param url
     * @param username
     * @param password
     */
    public Neo4JProtoSchemaExporter(String url, String username, String password) {
        neo4jClient = new Neo4jClient(url, username, password);
    }

    /**
     * Implement the export method to convert ProtoSchema to Neo4j Cypher queries
     * @param schema
     */
    @Override
    public void export(ProtoSchema schema) {
        neo4jClient.clean();
        schema.getEntities().values().parallelStream()
                .forEach( entity -> exportEntity(entity) );
        schema.getRelationships().parallelStream()
                .forEach( relationShip -> exportRelationship(relationShip) );
    }

    /**
     * Retrieve the query log
     */
    @Override
    public String getLog() {
        return neo4jClient.getQueryLog();
    }

    /**
     * Retrieve the log name to instruct the plugin to write to the file
     */
    @Override
    public String getLogName() {
        return LOG_FILE_NAME;
    }

    /**
     * Export a ProtoSchema entity
     * @param entity
     */
    private void exportEntity(ProtoSchema.Entity entity) {
        final Map<String, String> attributes = new HashMap<>();
        attributes.put(FULL_NAME_ATTRIBUTE_KEY, entity.getName());
        attributes.put(DOMAIN_ATTRIBUTE_KEY, entity.getDomain());
        attributes.putAll(entity.getAttributes());
        String ref = neo4jClient.createNode(entity.getDomain().replace(".", "_"), attributes);
        nodes.put(entity.getName(), ref);
    }

    /**
     * Export a ProtoSchema relationship
     * @param rel
     */
    private void exportRelationship(ProtoSchema.RelationShip rel) {
        final String from = nodes.get(rel.getType());
        final String to = nodes.get(rel.getTypeUsed());
        if (hasText(from) && hasText(to)) {
            neo4jClient.createRelationShip(from, to, "uses", withAttributes(RELATIONSHIP_FIELD_KEY, rel.getFieldName()));
        }
    }

    private static Map<String, String> withAttributes(String... kv) {
        final Map<String, String> map = new HashMap<>();
        for (int i=0; i<kv.length; i+=2) {
            if (hasText(kv[i+1])) {
                map.put(kv[i], kv[i + 1]);
            }
        }
        return map;
    }

}
