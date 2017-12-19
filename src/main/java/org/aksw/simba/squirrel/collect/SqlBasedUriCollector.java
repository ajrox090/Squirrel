package org.aksw.simba.squirrel.collect;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.aksw.simba.squirrel.data.uri.CrawleableUri;
import org.aksw.simba.squirrel.data.uri.serialize.Serializer;
import org.aksw.simba.squirrel.iterators.SqlBasedIterator;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of the {@link UriCollector} interface that is backed by a
 * SQL database.
 * 
 * @author Geralod Souza Junior (gsjunior@mail.uni-paderborn.de)
 * @author Michael R&ouml;der (michael.roeder@uni-paderborn.de)
 *
 * @NotThreadSafe because the prepared statement objects used internally are not
 *                stateless.
 */
@NotThreadSafe
public class SqlBasedUriCollector implements UriCollector, Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqlBasedUriCollector.class);

    protected static final String COUNT_URIS_QUERY = "SELECT COUNT(*) FROM ?";
    protected static final String CREATE_TABLE_QUERY = "CREATE TABLE ? (uri VARCHAR(255), serial INT, data BLOB, PRIMARY KEY(uri,serial));";
    protected static final String DROP_TABLE_QUERY = "DROP TABLE ?";
    protected static final String INSERT_URI_QUERY_PART_1 = " INSERT INTO ";
    protected static final String INSERT_URI_QUERY_PART_2 = "(uri,serial,data) VALUES(?,?,?)";
    // protected static final String CLEAR_TABLE_QUERY = "DELETE FROM uris";
    private static final String SELECT_TABLE_QUERY = "SELECT * FROM ? OFFSET ? FETCH NEXT ? ROWS ONLY ";
    private static final String TABLE_NAME_KEY = "URI_COLLECTOR_TABLE_NAME";
    private static final int MAX_ALPHANUM_PART_OF_TABLE_NAME = 30;
    private static final int DEFAULT_BUFFER_SIZE = 30;
    private static final Pattern TABLE_NAME_GENERATE_REGEX = Pattern.compile("[^0-9a-zA-Z]*");

    public static SqlBasedUriCollector create(Serializer serializer) {
        return create(serializer, "foundUris");
    }

    public static SqlBasedUriCollector create(Serializer serializer, String dbPath) {
        SqlBasedUriCollector collector = null;
        try {
            Class.forName("org.hsqldb.jdbc.JDBCDriver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace(System.out);
        }
        Statement s = null;
        try {
            Connection dbConnection = DriverManager.getConnection("jdbc:hsqldb:" + dbPath, "SA", "");
            // PreparedStatement createTableStmt =
            // dbConnection.prepareStatement(CREATE_TABLE_QUERY);
            // PreparedStatement dropTableStmt =
            // dbConnection.prepareStatement(DROP_TABLE_QUERY);
            // PreparedStatement insertStmt =
            // dbConnection.prepareStatement(INSERT_URI_QUERY);
            collector = new SqlBasedUriCollector(dbConnection,
                    /* createTableStmt, dropTableStmt, insertStmt, */ serializer);
        } catch (Exception e) {
            LOGGER.error("Error while creating a local database for storing the extracted URIs. Returning null.", e);
        } finally {
            try {
                if (s != null) {
                    s.close();
                }
            } catch (SQLException e) {
            }
        }
        return collector;
    }

    protected Connection dbConnection;
    protected Serializer serializer;
    protected int bufferSize = DEFAULT_BUFFER_SIZE;
    protected Map<String, UriTableStatus> knownUris = new HashMap<>();

    public SqlBasedUriCollector(Connection dbConnection, Serializer serializer) {
        this.dbConnection = dbConnection;
        this.serializer = serializer;
    }

    @Override
    public void openSinkForUri(CrawleableUri uri) {
        String tableName = getTableName(uri);
        try {
            dbConnection.createStatement().executeUpdate(CREATE_TABLE_QUERY.replace("?", tableName));
            dbConnection.commit();
            UriTableStatus table = UriTableStatus.create(tableName, dbConnection, bufferSize);
//            PreparedStatement ps = dbConnection.prepareStatement(CREATE_TABLE_QUERY);
            knownUris.put(uri.getUri().toString(), table);
        } catch (Exception e) {
            LOGGER.info("Couldn't create table for URI \"" + uri.getUri() + "\". ", e);
        }
    }

    @Override
    public Iterator<byte[]> getUris(CrawleableUri uri) {
        try {
        	
        	String tableName = getTableName(uri);
            Statement s = dbConnection.createStatement();
            ResultSet trs = s.executeQuery(COUNT_URIS_QUERY.replaceAll("\\?", tableName));
            int total = 0;
            // gets the total lines
            while (trs.next()) {
                total = trs.getInt(1);
            }
            if(total != 0) {
            	PreparedStatement ps = dbConnection.prepareStatement(SELECT_TABLE_QUERY.replaceFirst("\\?", tableName));
                return new SqlBasedIterator(ps, total,SELECT_TABLE_QUERY.replaceFirst("\\?", tableName));	
            }
        } catch (SQLException e) {
            LOGGER.error("Exception while querying URIs from database. Returning null.");
        }
        return Collections.emptyIterator();
    }

    @Override
    public void addTriple(CrawleableUri uri, Triple triple) {
        addUri(uri, triple.getSubject());
        addUri(uri, triple.getPredicate());
        addUri(uri, triple.getObject());
    }

    protected void addUri(CrawleableUri uri, Node node) {
        if (node.isURI()) {
            try {
                addNewUri(uri, new CrawleableUri(new URI(node.getURI())));
            } catch (URISyntaxException e) {
                LOGGER.error("Couldn't process extracted URI. It will be ignored.", e);
            }
        }
    }

    @Override
    public void addNewUri(CrawleableUri uri, CrawleableUri newUri) {
        String uriString = uri.getUri().toString();
        if (knownUris.containsKey(uriString)) {
            UriTableStatus table = knownUris.get(uriString);
            synchronized (table) {
                try {
                    table.addUri(uriString,serializer.serialize(newUri));
                } catch (IOException e) {
                	LOGGER.error("Couldn't serialize URI \"" + newUri.getUri() + "\". It will be ignored.", e);
                }catch(Exception e) {
                	LOGGER.error("Couldn't add URI \"" + newUri.getUri() + "\". It will be ignored.", e);
                }
            }
        } else {
            LOGGER.error("Got an unknown URI \"{}\". It will be ignored.", uri.getUri().toString());
        }
    }

    @Override
    public void closeSinkForUri(CrawleableUri uri) {
        String uriString = uri.getUri().toString();
        if (knownUris.containsKey(uriString)) {
            UriTableStatus table = knownUris.remove(uriString);
            synchronized (table) {
                try {
                    dbConnection.createStatement().executeUpdate(DROP_TABLE_QUERY.replace("?", getTableName(uri)));
                    dbConnection.commit();
                } catch (SQLException e) {
                    LOGGER.warn("Couldn't drop table of URI \"" + uri + "\". It will be ignored.", e);
                }
            }
        } else {
            LOGGER.info("Should close \"{}\" but it is not known. It will be ignored.", uri.getUri().toString());
        }
    }

    @Override
    public void close() throws IOException {
        // It might be necessary to go through the list of known URIs and close all of
        // the remaining URIs
        try {
            dbConnection.close();
        } catch (SQLException e) {
        }
    }

    /**
     * Retrieves the URIs table name from its properties or generates a new table
     * name and adds it to the URI (using the {@value #TABLE_NAME_KEY} property).
     * 
     * @param uri
     *            the URI for which a table name is needed.
     * @return the table name of the URI
     */
    protected static String getTableName(CrawleableUri uri) {
        if (uri.getData().containsKey(TABLE_NAME_KEY)) {
            return (String) uri.getData().get(TABLE_NAME_KEY);
        } else {
            String tableName = generateTableName(uri.getUri().toString());
            uri.addData(TABLE_NAME_KEY, tableName);
            return tableName;
        }
    }

    /**
     * Generates a table name based on the given URI. Only alphanumeric characters
     * of the URI are kept. If the URI exceeds the length of
     * {@link #MAX_ALPHANUM_PART_OF_TABLE_NAME}={@value #MAX_ALPHANUM_PART_OF_TABLE_NAME}
     * the exceeding part is cut off. After that the hash value of the original URI
     * is appended.
     * 
     * @param uri
     *            the URI for which a table name has to be generated
     * @return the table name of the URI
     */
    protected static String generateTableName(String uri) {
        String[] parts = TABLE_NAME_GENERATE_REGEX.split(uri);
        int pos = 0;
        StringBuilder builder = new StringBuilder();
        // Collect the alphanumeric parts of the URI
        while ((pos < parts.length) && (builder.length() < MAX_ALPHANUM_PART_OF_TABLE_NAME)) {
            // If the first character that would be added to the builder is a digit
            if ((builder.length() == 0) && (parts[pos].length() > 0) && Character.isDigit(parts[pos].charAt(0))) {
                // add a character in front of it
                builder.append('A');
            }
            builder.append(parts[pos]);
            ++pos;
        }
        // If the given String did not contain any useful characters, add at least a
        // single character before adding the hash
        if (builder.length() == 0) {
            builder.append('A');
        }

        // If we exceeded the maximum length of the alphanumeric part, delete the last
        // characters
        if (builder.length() > MAX_ALPHANUM_PART_OF_TABLE_NAME) {
            builder.delete(MAX_ALPHANUM_PART_OF_TABLE_NAME, builder.length());
        }
        // Append the hash code of the original URI
        builder.append(uri.hashCode());
        return builder.toString();
    }

    protected static class UriTableStatus {
        private final PreparedStatement insertStmt;
        private final List<byte[]> buffer;
        private final int bufferSize;

        public static UriTableStatus create(String tableName, Connection dbConnection, int bufferSize)
                throws SQLException {
            StringBuilder builder = new StringBuilder();
            builder.append(INSERT_URI_QUERY_PART_1);
            builder.append(tableName);
            builder.append(INSERT_URI_QUERY_PART_2);
            return new UriTableStatus(dbConnection.prepareStatement(builder.toString()), bufferSize);
        }

        public UriTableStatus(PreparedStatement insertStmt, int bufferSize) {
            this.insertStmt = insertStmt;
            buffer = new ArrayList<byte[]>(bufferSize);
            this.bufferSize = bufferSize;

        }

        public void addUri(String newUri,byte[] uri) {
        	
            synchronized (buffer) {
                buffer.add(uri);
                if (buffer.size() >= bufferSize) {
                    execute_unsecured(newUri);
                }
            }
        }

        public void clearBuffer() {
            synchronized (buffer) {
                buffer.clear();
            }
        }

        private void execute_unsecured(String newUri) {
        	try {
        		insertStmt.setString(1, newUri);
	            for (byte[] data : buffer) {
	            		insertStmt.setInt(2, data.hashCode());
	                	insertStmt.setBytes(3, data);
	                	insertStmt.addBatch();
	            }
            } catch (Exception e) {
                LOGGER.error("Error while creating insert statement for URI. It will be ignored.", e);
            }
            try {
                insertStmt.executeBatch();
                insertStmt.getConnection().commit();
                clearBuffer();
            }catch(BatchUpdateException e) {
            	LOGGER.error("URI already exists in the table. It will be ignored.",e);
        	}catch (Exception e) {
                LOGGER.error("Error while inserting a batch of URIs. They will be ignored.", e);
            }
        }
    }

}