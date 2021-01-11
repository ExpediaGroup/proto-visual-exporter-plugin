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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Neo4JProtoSchemaExporterTest {

    @Test
    public void testGetLogName() {
        Neo4jClient neo4jClient = mock(Neo4jClient.class);
        Neo4JProtoSchemaExporter neo4JProtoSchemaExporter = new Neo4JProtoSchemaExporter(neo4jClient);
        assertThat(neo4JProtoSchemaExporter.getLogName().equals(Neo4JProtoSchemaExporter.LOG_FILE_NAME));
    }

    @Test
    public void testGetLog() {
        Neo4jClient neo4jClient = mock(Neo4jClient.class);
        Neo4JProtoSchemaExporter neo4JProtoSchemaExporter = new Neo4JProtoSchemaExporter(neo4jClient);

        String expectedTestLog = "testlog";

        when(neo4jClient.getQueryLog()).thenReturn(expectedTestLog);

        assertThat(neo4JProtoSchemaExporter.getLog().equals(expectedTestLog));
    }

    @Test
    public void testExportWithNoEntitiesAndNoRelationships() {
        Neo4jClient neo4jClient = mock(Neo4jClient.class);
        Neo4JProtoSchemaExporter neo4JProtoSchemaExporter = new Neo4JProtoSchemaExporter(neo4jClient);

        ProtoSchema protoSchema = new ProtoSchema();
        neo4JProtoSchemaExporter.export(protoSchema);
        verify(neo4jClient, atLeast(1)).clean();
        verify(neo4jClient, times(0)).createRelationShip(any(), any(), any());
        verify(neo4jClient, times(0)).createNode(any(), any());
    }



    @Test
    public void testExportWithTwoEntitiesAndARelationship() {
        Neo4jClient neo4jClient = mock(Neo4jClient.class);
        Neo4JProtoSchemaExporter neo4JProtoSchemaExporter = new Neo4JProtoSchemaExporter(neo4jClient);

        // Setup
        ProtoSchema protoSchema = new ProtoSchema();
        protoSchema.registerEntity("node1", "expediagroup.package");
        protoSchema.registerEntityAttribute("node1", "id", "String");
        protoSchema.registerEntity("node2", "expediagroup.package");
        protoSchema.registerEntityAttribute("node2", "name", "String");
        protoSchema.registerRelationship("node1", "pointerToNode2", "node2");

        Map<String, String> node1ExpectedAttributes = new HashMap<>();
        node1ExpectedAttributes.put("id", "String");
        node1ExpectedAttributes.put(Neo4JProtoSchemaExporter.FULL_NAME_ATTRIBUTE_KEY, "node1");
        node1ExpectedAttributes.put(Neo4JProtoSchemaExporter.DOMAIN_ATTRIBUTE_KEY, "expediagroup.package");

        Map<String, String> node2ExpectedAttributes = new HashMap<>();
        node2ExpectedAttributes.put("name", "String");
        node2ExpectedAttributes.put(Neo4JProtoSchemaExporter.FULL_NAME_ATTRIBUTE_KEY, "node2");
        node2ExpectedAttributes.put(Neo4JProtoSchemaExporter.DOMAIN_ATTRIBUTE_KEY, "expediagroup.package");

        String node1ref = "node1ref";
        String node2ref = "node2ref";
        when(neo4jClient.createNode(eq("expediagroup_package"), eq(node1ExpectedAttributes))).thenReturn(node1ref);
        when(neo4jClient.createNode(eq("expediagroup_package"), eq(node2ExpectedAttributes))).thenReturn(node2ref);

        neo4JProtoSchemaExporter.export(protoSchema);

        Map<String, String> relationshipExpectedField = new HashMap<>();
        relationshipExpectedField.put(Neo4JProtoSchemaExporter.RELATIONSHIP_FIELD_KEY, "pointerToNode2");

        verify(neo4jClient, times(1)).createRelationShip(eq(node1ref), eq(node2ref), eq("uses"), eq(relationshipExpectedField));
    }
}