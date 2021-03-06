package at.ac.ait.archistar.integration;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.fest.assertions.api.Assertions.fail;

import io.netty.channel.nio.NioEventLoopGroup;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import at.ac.ait.archistar.backendserver.storageinterface.FilesystemStorage;
import at.ac.ait.archistar.backendserver.storageinterface.StorageServer;
import at.ac.ait.archistar.engine.TestEngine;
import at.ac.ait.archistar.engine.dataobjects.FSObject;
import at.ac.ait.archistar.engine.dataobjects.SimpleFile;
import at.ac.ait.archistar.engine.distributor.BFTDistributor;
import at.ac.ait.archistar.engine.distributor.Distributor;
import at.ac.ait.archistar.engine.distributor.TestServerConfiguration;
import at.ac.ait.archistar.engine.metadata.MetadataService;
import at.ac.ait.archistar.engine.metadata.SimpleMetadataService;
import at.archistar.crypto.CryptoEngine;
import at.archistar.crypto.RabinBenOrEngine;
import at.archistar.crypto.secretsharing.ReconstructionException;
import at.archistar.crypto.secretsharing.WeakSecurityException;
import at.archistar.crypto.random.FakeRandomSource;
import at.archistar.crypto.random.RandomSource;
import java.security.NoSuchAlgorithmException;

public class EncryptedFileSystemTest extends AbstractIntegrationTest {

    private static Set<StorageServer> createNewServers() {
        File baseDir = new File("/tmp/test-encrypted-filesystem/" + UUID.randomUUID() + "/");
        baseDir.mkdirs();

        File dir1 = new File(baseDir, "1");
        dir1.mkdir();
        File dir2 = new File(baseDir, "2");
        dir2.mkdir();
        File dir3 = new File(baseDir, "3");
        dir3.mkdir();
        File dir4 = new File(baseDir, "4");
        dir4.mkdir();

        HashSet<StorageServer> servers = new HashSet<>();
        servers.add(new FilesystemStorage(0, dir1));
        servers.add(new FilesystemStorage(1, dir2));
        servers.add(new FilesystemStorage(2, dir3));
        servers.add(new FilesystemStorage(3, dir4));
        return servers;
    }

    @Test
    public void testPersistedStoreAndRetrieveOperation() throws ReconstructionException {
        SimpleFile testObject = new SimpleFile(randomTestFilename(), testData, new HashMap<String, String>());
        String path = testObject.getPath();

        // WHEN I connect engine and store a fragment 
        engine.connect();
        engine.putObject(testObject);

        // AND I disconnect and reconnect
        engine.disconnect();

        assertThat(engine.connect()).isEqualTo(engine.getNumberOfServers());

        // THEN the data should still be available
        FSObject retrObject = engine.getObject(path);
        assertThat(retrObject).isNotNull().isInstanceOf(SimpleFile.class);
        assertThat(path).isEqualTo(retrObject.getPath());
        assertThat(((SimpleFile) retrObject).getData()).isEqualTo(testData);
    }

    @BeforeClass
    public static void prepareServer() throws WeakSecurityException, NoSuchAlgorithmException {
        serverConfig = new TestServerConfiguration(createNewServers());
        serverConfig.setupTestServer(1);

        RandomSource rng = new FakeRandomSource();
        CryptoEngine crypto = new RabinBenOrEngine(4, 3, rng);
        Distributor distributor = new BFTDistributor(serverConfig, new NioEventLoopGroup());
        MetadataService metadata = new SimpleMetadataService(serverConfig, distributor, crypto);
        engine = new TestEngine(serverConfig, metadata, distributor, crypto);
    }

    @AfterClass
    public static void shutdownServers() {
        serverConfig.teardownTestServer();
    }
}
