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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.util.*;


/**
 * Builds the Neo4j queries to create the nodes and relationships.
 */
public class Neo4jClient {

    private static final Logger LOGGER = LogManager.getLogger(Neo4jClient.class);

    private RestTemplate restTemplate;
    private String url;
    private StringBuffer queryTracker;

    /**
     * Sets up the Neo4j connection
     * @param url - Neo4j server
     * @param username - Neo4j server username
     * @param password - Neo4j server password
     */
    public Neo4jClient(String url, String username, String password) {
        queryTracker = new StringBuffer();
        restTemplate = null;
        final MappingJackson2HttpMessageConverter jackson2Converter = new MappingJackson2HttpMessageConverter();
        final ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        jackson2Converter.setObjectMapper(mapper);
        List<HttpMessageConverter<?>> converters = new ArrayList<>();
        converters.add(new StringHttpMessageConverter());
        converters.add(jackson2Converter);
        restTemplate = new RestTemplate();
        restTemplate.setMessageConverters(converters);

        final PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(10);
        cm.setDefaultMaxPerRoute(10);
        final BasicCredentialsProvider bcp = new BasicCredentialsProvider();

        bcp.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
        final HttpClient httpClient = HttpClientBuilder.create()
                .setConnectionManager(cm)
                .setDefaultCredentialsProvider(bcp)
                .setDefaultAuthSchemeRegistry(
                        RegistryBuilder.<AuthSchemeProvider>create()
                                .register(AuthSchemes.BASIC, new BasicSchemeFactory())
                                .build()
                )
                .build();
        final HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        restTemplate.setRequestFactory(factory);

        this.url = url;
    }

    public void clean() {
        try {
            execute("MATCH (n) OPTIONAL MATCH (n)-[r]-() DELETE n, r");
            queryTracker = new StringBuffer();
        }catch(Exception ex) {
            LOGGER.error("Failed to clean neo4j", ex);
        }
    }

    /**
     * Create the Neo4j node
     * @param node - name for the node
     * @param attributes - any fields the node may have
     * @return Node reference
     */
    public String createNode(String node, Map<String, String> attributes) {

        final StringBuilder cql = new StringBuilder()
                .append("CREATE (n:")
                .append(node)
                .append(" {");
        attributes.forEach( (k,v) -> {
            cql.append(" ").append(k).append(" : {").append(k).append("},");
        });
        cql.replace(cql.length()-1, cql.length(), " }")
                .append(") RETURN n");

        DocumentContext ctx = execute(cql.toString(), attributes);
        String nodeId = ctx.read("$.data[0][0].self");

        synchronized (this) {
            queryTracker.append("==========Node (").append(nodeId).append(")==========\n").append(cql);
            if (!attributes.isEmpty()) {
                queryTracker.append("\ndata:\n");
                attributes.forEach((k, v) -> queryTracker.append(" ").append(k).append(" : {").append(v).append("},"));
            }
            queryTracker.append("\n");
        }

        return nodeId;
    }

    /**
     * Create a relationship with no fields
     * @param from - Node reference of the 'from' part
     * @param to - Node reference of the 'from' part
     * @param type - Type of the relationship
     * @return
     */
    public String createRelationShip(String from, String to, String type) {
        return createRelationShip(from, to, type, null);
    }

    /**
     * Create a relationship with fields
     * @param from - Node reference of the 'from' part
     * @param to - Node reference of the 'from' part
     * @param type - Type of the relationship
     * @param attributes - Extra fields for the relationship
     * @return
     */
    public String createRelationShip(String from, String to, String type, Map<String, String> attributes) {
        final RelationshipRequest request = new RelationshipRequest();
        request.to = to;
        request.type = type;
        request.data = attributes;

        synchronized (this) {
            queryTracker.append("==========Relationship==========\n")
                    .append("from: ")
                    .append(from)
                    .append("\nto: ")
                    .append(to);

            if (!attributes.isEmpty()) {
                queryTracker.append("\ndata:\n");
                attributes.forEach( (k,v) -> queryTracker.append(" ").append(k).append(" : {").append(v).append("},"));
            }

            queryTracker.append("\n");
        }

        DocumentContext ctx = execute(from + "/relationships", request);
        return ctx.read("$.self");
    }

    /**
     * Returns the query tracker
     * @return
     */
    public String getQueryLog() {
        return queryTracker.toString();
    }

    /**
     * Execute the neo4j request with no parameters
     * @param cql - The cypher query
     * @return
     */
    private DocumentContext execute(String cql) {
        return execute(cql, null);
    }

    /**
     * Execute the neo4j request with Map representation of parameters
     * @param cql - The Cypher query
     * @param params - Map representation of the Cypher query params to fill in the placeholders in the query
     * @return
     */
    private DocumentContext execute(String cql, Map<String, String> params) {
        final CypherRequest request = new CypherRequest();
        request.query = cql;
        request.params = params;
        return execute(url + "/db/data/cypher", request);
    }

    /**
     * Execute the query with the Cypher Request Object
     * @param path - Neo4j url
     * @param request - Cypher Request object
     * @return
     */
    private DocumentContext execute(String path, Object request) {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));

        final HttpEntity entity = new HttpEntity<>(request, headers);
        ResponseEntity<String> responseEntity = null;
        try {
            responseEntity = restTemplate.postForEntity(path, entity, String.class);
        } catch(Exception ex) {
            LOGGER.error("Unable to execute neo4j query.", ex);
            throw new RuntimeException(ex);
        }
        return JsonPath.parse(responseEntity.getBody());
    }

    /**
     * Cypher Request object
     */
    private static class CypherRequest {
        public String query;
        public Map<String, String> params;
    }

    /**
     * Relationship request object
     */
    private static class RelationshipRequest {
        public String to;
        public String type;
        public Map<String, String> data;
    }
}
