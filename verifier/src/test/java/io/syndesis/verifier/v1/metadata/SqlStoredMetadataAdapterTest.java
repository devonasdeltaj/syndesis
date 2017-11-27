/*
 * Copyright (C) 2017 Red Hat, Inc.
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

package io.syndesis.verifier.v1.metadata;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;


import io.syndesis.connector.sql.DatabaseProduct;
import io.syndesis.connector.sql.stored.SampleStoredProcedures;
import io.syndesis.connector.sql.stored.SqlStoredConnectorMetaDataExtension;
import org.apache.camel.component.extension.MetaDataExtension.MetaData;
import org.apache.commons.io.IOUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.assertj.core.api.Assertions.fail;
import static org.junit.Assert.assertEquals;

public class SqlStoredMetadataAdapterTest {

    private static Connection connection;
    private static Properties properties = new Properties();
    private static String DERBY_DEMO_ADD2_SQL =
            "CREATE PROCEDURE DEMO_ADD2( IN A INTEGER, IN B INTEGER, OUT C INTEGER ) " +
            "PARAMETER STYLE JAVA " +
            "LANGUAGE JAVA " +
            "EXTERNAL NAME 'io.syndesis.connector.SampleStoredProcedures.demo_add'";

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        try (InputStream is = SqlStoredMetadataAdapterTest.class.getClassLoader().getResourceAsStream("application.properties")) {
            properties.load(is);
            String user     = String.valueOf(properties.get("sql-stored-connector.user"));
            String password = String.valueOf(properties.get("sql-stored-connector.password"));
            String url      = String.valueOf(properties.get("sql-stored-connector.url"));

            connection = DriverManager.getConnection(url,user,password);
            String databaseProductName = connection.getMetaData().getDatabaseProductName();
            Map<String,Object> parameters = new HashMap<>();
            for (final String name: properties.stringPropertyNames()) {
                parameters.put(name.substring(name.indexOf(".")+1), properties.getProperty(name));
            }
            if (databaseProductName.equalsIgnoreCase(DatabaseProduct.APACHE_DERBY.nameWithSpaces())) {
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute(SampleStoredProcedures.DERBY_DEMO_ADD_SQL);
                    stmt.execute(DERBY_DEMO_ADD2_SQL);
                } catch (Exception e) {
                    fail("Exception during Stored Procedure Creation.", e);
                }
            }
        } catch (Exception ex) {
            fail("Exception", ex);
        }
    }

    @AfterClass
    public static void afterClass() throws SQLException {
        if (connection!=null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    public void adaptTest() throws IOException {

        SqlStoredConnectorMetaDataExtension ext = new SqlStoredConnectorMetaDataExtension();
        Map<String,Object> parameters = new HashMap<>();
        for (final String name: properties.stringPropertyNames()) {
            parameters.put(name.substring(name.indexOf(".")+1), properties.getProperty(name));
        }
        Optional<MetaData> metadata = ext.meta(parameters);

        SqlStoredMetadataAdapter adapter = new SqlStoredMetadataAdapter();
        SyndesisMetadata<JsonSchema> syndesisMetaData = adapter.adapt(parameters, metadata.get());

        ObjectMapper mapper = new ObjectMapper();
        String expectedListOfProcedures = IOUtils.toString(this.getClass().getResource("/sql/stored_procedure_list.json"), StandardCharsets.UTF_8);
        String actualListOfProcedures = (mapper.writerWithDefaultPrettyPrinter().writeValueAsString(syndesisMetaData));
        assertEquals(expectedListOfProcedures, actualListOfProcedures);

        parameters.put("procedureName", "DEMO_ADD");
        SyndesisMetadata<JsonSchema> syndesisMetaData2 = adapter.adapt(parameters, metadata.get());
        String expectedMetadata = IOUtils.toString(this.getClass().getResource("/sql/demo_add_metadata.json"), StandardCharsets.UTF_8);
        String actualMetadata = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(syndesisMetaData2);
        assertEquals(expectedMetadata, actualMetadata);
    }
}
