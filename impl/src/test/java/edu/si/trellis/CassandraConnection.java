package edu.si.trellis;

import static com.datastax.oss.driver.api.core.CqlSession.builder;
import static com.datastax.oss.driver.api.core.DefaultConsistencyLevel.ONE;
import static edu.si.trellis.DatasetCodec.DATASET_CODEC;
import static edu.si.trellis.IRICodec.IRI_CODEC;
import static edu.si.trellis.InputStreamCodec.INPUTSTREAM_CODEC;
import static java.net.InetSocketAddress.createUnresolved;
import static org.slf4j.LoggerFactory.getLogger;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;

import edu.si.trellis.query.rdf.GetFirstMemento;
import edu.si.trellis.query.rdf.GetMemento;
import edu.si.trellis.query.rdf.Mementoize;
import edu.si.trellis.query.rdf.Mementos;

import java.net.InetSocketAddress;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.trellisldp.api.IdentifierService;

class CassandraConnection implements AfterAllCallback, BeforeAllCallback {

    private static final String[] CLEANOUT_QUERIES = new String[] { "TRUNCATE metadata ; ", "TRUNCATE mutabledata ; ",
            "TRUNCATE immutabledata ;", "TRUNCATE binarydata ;", "TRUNCATE mementodata ;" };

    private static final DefaultConsistencyLevel testConsistency = ONE;

    private static final Logger log = getLogger(CassandraConnection.class);

    private static final String keyspace = "trellis";

    private CqlSession session;

    CassandraResourceService resourceService;

    CassandraBinaryService binaryService;

    CassandraMementoService mementoService;

    private static final String contactAddress = System.getProperty("cassandra.contactAddress", "localhost");

    private static final Integer contactPort = Integer.getInteger("cassandra.nativeTransportPort", 9042);

    private static final boolean cleanBefore = Boolean.getBoolean("cleanBeforeTests");

    private static final boolean cleanAfter = Boolean.getBoolean("cleanAfterTests");

    @Override
    public void beforeAll(ExtensionContext context) {
        log.debug("Trying Cassandra connection at: {}:{}", contactAddress, contactPort);
        final InetSocketAddress socketAddress = createUnresolved(contactAddress, contactPort);
        this.session = builder()
                        .withLocalDatacenter("datacenter1")
                        .addTypeCodecs(INPUTSTREAM_CODEC, IRI_CODEC, DATASET_CODEC)
                        .withKeyspace("trellis")
                        .addContactPoint(socketAddress).build();
        this.resourceService = new CassandraResourceService(new edu.si.trellis.query.rdf.Delete(session, ONE),
                        new edu.si.trellis.query.rdf.Get(session, ONE),
                        new edu.si.trellis.query.rdf.ImmutableInsert(session, testConsistency),
                        new edu.si.trellis.query.rdf.MutableInsert(session, testConsistency),
                        new edu.si.trellis.query.rdf.Touch(session, testConsistency),
                        new edu.si.trellis.query.rdf.ImmutableRetrieve(session, testConsistency),
                        new edu.si.trellis.query.rdf.BasicContainment(session, testConsistency));
        resourceService.initializeRoot();
        this.mementoService = new CassandraMementoService(new Mementos(session, testConsistency),
                        new Mementoize(session, testConsistency), new GetMemento(session, testConsistency),
                        new GetFirstMemento(session, testConsistency));
        this.binaryService = new CassandraBinaryService((IdentifierService) null, 1024 * 1024,
                        new edu.si.trellis.query.binary.GetChunkSize(session, testConsistency),
                        new edu.si.trellis.query.binary.Insert(session, testConsistency),
                        new edu.si.trellis.query.binary.Delete(session, testConsistency),
                        new edu.si.trellis.query.binary.Read(session, testConsistency),
                        new edu.si.trellis.query.binary.ReadRange(session, testConsistency));
        if (cleanBefore) cleanOut();
    }

    private void cleanOut() {
        log.info("Cleaning out test keyspace {}", keyspace);
        for (String q : CLEANOUT_QUERIES)
            session.execute(q);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (cleanAfter) cleanOut();
        session.close();
    }
}
