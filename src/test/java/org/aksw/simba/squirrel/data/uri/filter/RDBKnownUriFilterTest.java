package org.aksw.simba.squirrel.data.uri.filter;

import com.rethinkdb.RethinkDB;
import org.aksw.simba.squirrel.RethinkDBMockTest;
import org.aksw.simba.squirrel.model.RDBConnector;


public class RDBKnownUriFilterTest {
    private RethinkDB r;
    private RDBConnector connector;
    private RDBKnownUriFilter filter;

    /**
     * For functionality regarding the starting of rethinkdb container
     * TODO references - MERGE :\
     */
    private RethinkDBMockTest rethinkDBMockTest;

    /*
    @Before
    public void setUp() throws IOException, InterruptedException {
        r = RethinkDB.r;
        connector = new RDBConnector(RethinkDBMockTest.DB_HOST_NAME, RethinkDBMockTest.DB_PORT);
        filter = new RDBKnownUriFilter(connector, r, false);

        // to get rethinkdb container running
        rethinkDBMockTest = new RethinkDBMockTest();
        rethinkDBMockTest.setUp();
    }

    @Test
    public void testGetOutdatedUris() throws URISyntaxException, UnknownHostException {
        this.connector.open();

        r.dbCreate(RDBKnownUriFilter.DATABASE_NAME).run(this.connector.connection);
        r.db(RDBKnownUriFilter.DATABASE_NAME).tableCreate(RDBKnownUriFilter.TABLE_NAME).run(this.connector.connection);
        r.db(RDBKnownUriFilter.DATABASE_NAME).table(RDBKnownUriFilter.TABLE_NAME).indexCreate(RDBKnownUriFilter.COLUMN_URI).run(this.connector.connection);
        r.db(RDBKnownUriFilter.DATABASE_NAME).table(RDBKnownUriFilter.TABLE_NAME).indexWait(RDBKnownUriFilter.COLUMN_URI).run(this.connector.connection);

        CrawleableUri uri1 = new CrawleableUri(new URI("http://www.google.de"), InetAddress.getByName("192.168.100.1"));
        CrawleableUri uri2 = new CrawleableUri(new URI("http://www.upb.de"), InetAddress.getByName("192.168.100.1"));
        filter.add(uri1, System.currentTimeMillis() - 10);
        filter.add(uri2, System.currentTimeMillis() + 50000);

        // filter must return uri1 as it is outdated
        List<CrawleableUri> uris = filter.getOutdatedUris();
        Assert.assertEquals(1, uris.size());
        Assert.assertEquals(uri1, uris.get(0));

        // set crawlingInProcess to true for uri1
        Cursor<Boolean> cursor = r.db(RDBKnownUriFilter.DATABASE_NAME).table(RDBKnownUriFilter.TABLE_NAME).
            filter(doc -> doc.getField(RDBKnownUriFilter.COLUMN_URI).eq(uri1.getUri().toString())).
            getField(RDBKnownUriFilter.COLUMN_CRAWLING_IN_PROCESS).run(connector.connection);

        // check if flag is true for uri1
        Assert.assertTrue(cursor.next());

        cursor = r.db(RDBKnownUriFilter.DATABASE_NAME).table(RDBKnownUriFilter.TABLE_NAME).
            filter(doc -> doc.getField(RDBKnownUriFilter.COLUMN_URI).eq(uri2.getUri().toString())).
            getField(RDBKnownUriFilter.COLUMN_CRAWLING_IN_PROCESS).run(connector.connection);

        // check if flag is still false for uri2
        Assert.assertFalse(cursor.next());


        // filter must return nothing now
        uris = filter.getOutdatedUris();
        Assert.assertTrue(uris.isEmpty());

        // manipulate lastCrawlTimestamp so that uri will be returned by filter
        r.db(RDBKnownUriFilter.DATABASE_NAME).table(RDBKnownUriFilter.TABLE_NAME).
            filter(doc -> doc.getField(RDBKnownUriFilter.COLUMN_URI).eq(uri1.getUri().toString())).
            update(r.hashMap(RDBKnownUriFilter.COLUMN_TIMESTAMP_LAST_CRAWL,
                System.currentTimeMillis() - 10 * FrontierImpl.DEFAULT_GENERAL_RECRAWL_TIME)).run(connector.connection);


        // filter must return uri1 now again
        uris = filter.getOutdatedUris();
        Assert.assertEquals(1, uris.size());
        Assert.assertEquals(uri1, uris.get(0));

        cursor.close();
    }

    @After
    public void tearDown() throws IOException {
        rethinkDBMockTest.tearDown();
    }
    */
}
