# proto-visual-exporter-plugin
![License](https://img.shields.io/hexpm/l/plug.svg)

A protoc plugin that takes in the protocol buffers schemas and outputs to your chosen visual exporter. Having a visual way of representing the schema is useful in seeing how types are being used. 

Protocol Buffers schema can be exported to:
* [Neo4J](https://neo4j.com/), a popular graph database to visualize relationships between the different entites of the model

## Launch Neo4J locally
There is an included [docker-compose.yml](docker-compose.yml) file that will launch a local instance of Neo4J. Simple run the following command in a docker environment:
```
docker-compose up
```

## Usage
### In a JVM project that contains protocol buffers
Add this to your maven project's pom.xml in the build->plugins section:
```
<plugin>
    <groupId>org.xolstice.maven.plugins</groupId>
    <artifactId>protobuf-maven-plugin</artifactId>
    <version>0.6.1</version>
    <configuration>
        <protocArtifact>com.google.protobuf:protoc:3.7.1:exe:${os.detected.classifier}</protocArtifact>
        <pluginId>grpc-java</pluginId>
    </configuration>
    <executions>
        <execution>
            <id>protobuf-plugin</id>
            <goals>
                <goal>compile</goal>
            </goals>
            <phase>generate-sources</phase>
            <configuration>
                <protocArtifact>com.google.protobuf:protoc:3.6.1:exe:${os.detected.classifier}</protocArtifact>
                <useArgumentFile>true</useArgumentFile>
                <protocPlugins>
                    <protocPlugin>
                        <id>proto-visual-exporter-plugin</id>
                        <groupId>com.expediagroup.grpc</groupId>
                        <artifactId>proto-visual-exporter-plugin</artifactId>
                        <version>1.0-SNAPSHOT</version>
                        <mainClass>com.expediagroup.grpc.plugin.ProtoVisualExporterPlugin</mainClass>
                        <args>
                            <arg>neo4j</arg>
                            <arg>http://localhost:7474</arg> <!-- optional -->
                            <arg>username</arg> <!-- optional -->
                            <arg>password</arg> <!-- optional -->
                        </args>
                    </protocPlugin>
                </protocPlugins>
            </configuration>
        </execution>
    </executions>
</plugin>
```
### Manually execute the plugin

This requires you to get a *descriptor_dump* first using the [Salesforce Generator](https://github.com/salesforce/grpc-java-contrib/tree/master/jprotoc/jprotoc). You can see an example of this in this project's test.

Once you have a descriptor_dump file you can send it into the executable jar like this:
`./target/proto-visual-exporter-plugin-1.0-SNAPSHOT-osx-x86_64.exe < src/test/resources/descriptor_dump neo4j http://localhost:7474 username password`

### Args
### Neo4j
If none of the args for Neo4j are specified it will default to 'localhost:7474'.

```neo4j <url> <username> <password>```

After the plugin is run, a file called 'neo4j-query-log.txt' is spit out that shows the neo4j queries it ran.

Note that we made some decisions around how to represent the protocol buffers in the Neo4j graph as follows:
* We treat *one_of* as an entity and put all the attributes in it's own node.
* We treat enums as their own entity.
* Primitive types (ie. strings, ints, etc.) will be shown on the node itself as data.
* Entity relationships in Neo4j will show up with the arrow containing the field name.

## Contributing
Please see the [Contributing Guide](CONTRIBUTING.md) to see how you can contribute.

## Legal
This project is available under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).

Copyright 2019 Expedia, Inc.
