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

import com.expediagroup.grpc.plugin.neo4j.Neo4JProtoSchemaExporter;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.compiler.PluginProtos;
import com.salesforce.jprotoc.GeneratorException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.Collections;
import java.util.List;

/**
 * Plugin for exporting protocol buffers to a visual representation
 */
public class ProtoVisualExporterPlugin extends com.salesforce.jprotoc.Generator {

    private ProtoSchemaExporter exporter;

    private final static Logger LOGGER = LogManager.getLogger(ProtoVisualExporterPlugin.class);

    /**
     * Takes in the visual exporter
     * @param exporter
     */
    public ProtoVisualExporterPlugin(ProtoSchemaExporter exporter) {
        super();
        this.exporter = exporter;
    }

    /**
     * Register a protocol buffer enum
     * @param descriptor - The Enum descriptor
     * @param descriptorPackage - Indicates the package the enum belongs to
     * @param schema - The View Model we are populating
     */
    private void registerEnum(final DescriptorProtos.EnumDescriptorProto descriptor, final String descriptorPackage, ProtoSchema schema) {
        String messageFullName = descriptorPackage + "." + descriptor.getName();
        schema.registerEntity(messageFullName, descriptorPackage);
    }

    /**
     * Register a protocol buffer message
     * @param descriptor - The Message descriptor
     * @param descriptorPackage - Indicates the package the message belongs to
     * @param schema - The View Model we are populating
     */
    private void registerMessage(final DescriptorProtos.DescriptorProto descriptor, final String descriptorPackage, ProtoSchema schema) {
        String messageFullName = descriptorPackage + "." + descriptor.getName();
        schema.registerEntity(messageFullName, descriptorPackage);

        if (descriptor.getOneofDeclCount() > 0) {
            // register one of's as an 'entity'
            descriptor.getOneofDeclList().stream()
                .forEach( oneOf -> {
                    String oneOfFullName = descriptorPackage + "." + oneOf.getName();
                    schema.registerEntity(oneOfFullName, descriptorPackage);
                });
        }

        descriptor.getFieldList()
                .stream()
                .forEach( field -> registerField(descriptor, field, descriptorPackage, schema));
    }

    /**
     * Register a protocol buffer field with one_of supports.
     *
     * For one_ofs, we create a relationship between the parent message and the one_of and list all the attributes of the one_of
     * in there instead of the actual message.
     *
     * Note that we construct a full name of the field name including the package to disambiguate.
     *
     * @param message - The Message descriptor that this field belongs to
     * @param field - The Field descriptor itself
     * @param descriptorPackage - Indicates the package the message belongs to
     * @param schema - The View Model we are populating
     */
    private void registerField(final DescriptorProtos.DescriptorProto message, DescriptorProtos.FieldDescriptorProto field, final String descriptorPackage, ProtoSchema schema) {

        if (field.hasOneofIndex()) {
            // Handle this as a one of relationship
            DescriptorProtos.OneofDescriptorProto oneofDescriptorProto = message.getOneofDecl(field.getOneofIndex());
            if (oneofDescriptorProto == null) {
                throw new RuntimeException("couldn't find the oneOfDescriptor [fieldName={" + field.getName() + "}, oneOfIndex={" + field.getOneofIndex() + "}]!! Something is wrong.");
            }

            // Relationship is a SET so dont worry about registering it multiple times for each field in a 'oneof'
            String messageName = descriptorPackage + "." + message.getName();
            String oneofDescriptorFullName = descriptorPackage + "." + oneofDescriptorProto.getName();
            // Super hacky
            schema.registerRelationship(messageName, oneofDescriptorProto.getName(), oneofDescriptorFullName);

            registerField(oneofDescriptorProto.getName(), field.getName(), field.getType(), field.getTypeName(), descriptorPackage, schema);
        } else {
            registerField(message.getName(), field.getName(), field.getType(), field.getTypeName(), descriptorPackage, schema);
        }
    }

    /**
     * Helper function to register a field as a relationship or entity field.
     *
     * If the type is 'Message' then we will use the fieldTypeName  but if it's primitive then we will just use the fieldType.
     * Note that we construct a full name of the field name including the package to disambiguate.
     *
     * @param messageName - Original message name that this field belongs to
     * @param fieldName - What the field's name is
     * @param fieldType - What the field type is (used if it's basic primitive type like STRING)
     * @param fieldTypeName - What the field type name is (if it's a message for example)
     * @param descriptorPackage -
     * @param schema
     */
    private void registerField(String messageName, String fieldName, DescriptorProtos.FieldDescriptorProto.Type fieldType, String fieldTypeName, final String descriptorPackage, ProtoSchema schema) {
        if (fieldType == DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE ||
                fieldType == DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM) {
            String messageFullName = descriptorPackage + "." + messageName;
            // This is hacky but it's the way we get it. Strip off the first 'period' to include the package. Ex. '.hello.Request' -> 'hello.Request'
            String fieldTypeNameFormatted = fieldTypeName.substring(1);
            schema.registerRelationship(messageFullName, fieldName, fieldTypeNameFormatted);
        } else {
            String messageFullName = descriptorPackage + "." + messageName;
            schema.registerEntityAttribute(messageFullName, fieldName, fieldType.name());
        }
    }

    /**
     * Handle each protocol file
     * @param fileDesc - File descriptor
     * @param schema - View model we are populating
     * @return File representation of the view
     */
    private void handleProtoFile(DescriptorProtos.FileDescriptorProto fileDesc, ProtoSchema schema) {

        for (DescriptorProtos.EnumDescriptorProto enumDescriptorProto : fileDesc.getEnumTypeList()) {
            registerEnum(enumDescriptorProto, fileDesc.getPackage(), schema);
        }

        for (DescriptorProtos.DescriptorProto descriptorProto : fileDesc.getMessageTypeList()) {
            registerMessage(descriptorProto, fileDesc.getPackage(), schema);
        }
    }

    @Override
    public List<PluginProtos.CodeGeneratorResponse.File> generateFiles(PluginProtos.CodeGeneratorRequest request) throws GeneratorException {

        ProtoSchema schema = new ProtoSchema();

        // Build the view model
         request.getProtoFileList().forEach(
                 file -> this.handleProtoFile(file, schema)
         );

         exporter.export(schema);
         return Collections.singletonList(PluginProtos.CodeGeneratorResponse.File
                 .newBuilder()
                .setName(exporter.getLogName())
                .setContent(exporter.getLog())
                .build());
    }

    public static void main(String[] args) {

        ProtoSchemaExporter exporter = null;

        if (args.length > 0 && args[0].equalsIgnoreCase("neo4j")) {
            String username = "";
            String password = "";
            String url = "http://localhost:7474";

            switch (args.length) {
                case 1:
                    // use default values
                    break;
                case 2:
                    url = args[1];
                    break;
                case 3:
                    url = args[1];
                    username = args[2];
                    break;
                case 4:
                    url = args[1];
                    username = args[2];
                    password = args[3];
                    break;
                default:
                    LOGGER.error("Export mode of neo4j doesn't have the correct args: 'neo4j <url> <username> <password>");
                    return;
            }

            exporter = new Neo4JProtoSchemaExporter(url, username, password);
        } else {
            LOGGER.error("Unrecognized export mode.");
            return;
        }

        com.salesforce.jprotoc.ProtocPlugin.generate(new ProtoVisualExporterPlugin(exporter));
    }

}
