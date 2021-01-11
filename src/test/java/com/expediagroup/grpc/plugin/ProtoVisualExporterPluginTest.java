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

import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.google.protobuf.compiler.PluginProtos;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.salesforce.jprotoc.Generator;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ProtoVisualExporterPluginTest {

    private static Generator generator;
    private String schemaReturnString;

    private TestExporter testExporter = new TestExporter();

    /**
     * TestExporter that implements the Export interface to output JSON representation of the schema
     */
    class TestExporter implements ProtoSchemaExporter {

        @Override
        public void export(ProtoSchema schema) {
            schemaReturnString = schema.toString();
        }

        @Override
        public String getLog() {
            return schemaReturnString;
        }

        @Override
        public String getLogName() {
            return "testexporter.txt";
        }
    }

    /** !!!!!! PLEASE READ !!!!!!
     * If you touch any of the proto files in test/java/proto you need to run this test once successfully in order to
     * get the latest descriptor_dump and then you need to copy it from target/generated-test-sources/protobuf/java
     * over to test/resources for this test to consume it.
     */
    @Test
    public void readSimpleProto() throws Exception {
        generator = new ProtoVisualExporterPlugin(testExporter);

        URL testproto = this.getClass().getResource("/descriptor_dump");

        byte[] generatorRequestBytes = ByteStreams.toByteArray(new FileInputStream(new File(testproto.getPath())));
        PluginProtos.CodeGeneratorRequest request = PluginProtos.CodeGeneratorRequest.parseFrom(
                generatorRequestBytes);

        List<PluginProtos.CodeGeneratorResponse.File> files = generator.generateFiles(request);
        assertThat(files).isNotNull().isNotEmpty();
        assertThat(files.size()).isEqualTo(1);
        DocumentContext jsonContext = JsonPath.parse(files.get(0).getContent());

        // Verify entity's name
        assertThat((String)jsonContext.read("$['entities']['hello.Greeting']['name']")).isEqualTo("hello.Greeting");

        // Verify a entity attribute
        assertThat((Map<String, String>)jsonContext.read("$['entities']['hello.Greeting']['attributes']"))
                .hasEntrySatisfying("name", value -> assertThat(value).isEqualTo("TYPE_STRING"));

        // Verify enum entity
        assertThat((String)jsonContext.read("$['entities']['hello.OrderType']['name']")).isEqualTo("hello.OrderType");

        // Verify one_of entity
        assertThat((String)jsonContext.read("$['entities']['hello.response_oneof']['name']")).isEqualTo("hello.response_oneof");
        assertThat((Map<String, String>)jsonContext.read("$['entities']['hello.response_oneof']['attributes']"))
                .hasEntrySatisfying("error", value -> assertThat(value).isEqualTo("TYPE_STRING"));

        // Relationship Verifications
        List<Map<String, String>> relationShips = jsonContext.read("$['relationships']");

        // Verify a entity->entity relationship: hello.Request has an order of type hello.Order
        assertThat(relationShips).contains(ImmutableMap.of("type","hello.Request","fieldName","order","typeUsed","hello.Order"));

        // Verify a entity->enum relationship: hello.Order has an enum field orderType of enum type hello.OrderType
        assertThat(relationShips).contains(ImmutableMap.of("type","hello.Order","fieldName","orderType","typeUsed","hello.OrderType"));

        // Verify a entity->oneof relationship: hello.Response has a one_of field response_oneof of type hello.response_oneof
        assertThat(relationShips).contains(ImmutableMap.of("type","hello.Response","fieldName","response_oneof","typeUsed","hello.response_oneof"));

        // Verify a one_of->entity relationship: hello.response_oneof has a field greeting of type hello.Greeting
        assertThat(relationShips).contains(ImmutableMap.of("type","hello.response_oneof","fieldName","greeting","typeUsed","hello.Greeting"));

    }

}
