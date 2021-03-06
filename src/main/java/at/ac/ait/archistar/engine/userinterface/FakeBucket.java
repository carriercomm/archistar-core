package at.ac.ait.archistar.engine.userinterface;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.xml.parsers.ParserConfigurationException;

import org.jboss.resteasy.util.Hex;

import at.ac.ait.archistar.engine.Engine;
import at.ac.ait.archistar.engine.dataobjects.FSObject;
import at.ac.ait.archistar.engine.dataobjects.SimpleFile;
import at.archistar.crypto.secretsharing.ReconstructionException;

/**
 * this is a fake bucket implementation that forwards all incoming S3 commands
 * to the internal Archistar engine (which in turn forwards those commands to
 * the replicas)
 * 
 * @author andy
 */
public class FakeBucket {

    private final Engine engine;

    private final XmlDocumentBuilder builder;

    public FakeBucket(Engine engine) throws ParserConfigurationException {
        this.engine = engine;
        this.builder = new XmlDocumentBuilder();
    }

    /* list all elements within bucket */
    public String getAll(String bucketName, String delim, String prefix, int maxKeysInt) throws ReconstructionException {

        if (prefix != null && (prefix.equals("/") || prefix.equals(""))) {
            prefix = null;
        }

        HashSet<SimpleFile> results = new HashSet<>();
        for (String key : this.engine.listObjects(prefix)) {
            FSObject obj = engine.getObject(key);

            if (obj instanceof SimpleFile) {
                results.add((SimpleFile) obj);
            }
        }

        return builder.stringFromDoc(builder.listElements(prefix, bucketName, maxKeysInt, results));
    }

    public Response getById(String id) throws NoSuchAlgorithmException, ReconstructionException {

        FSObject obj = engine.getObject(id);
        if (obj != null && obj instanceof SimpleFile) {
            byte[] result = ((SimpleFile) obj).getData();

            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] thedigest = md.digest(result);
            return Response.accepted().entity(result).header("ETag", Hex.encodeHex(thedigest)).build();
        } else {
            return null;
        }
    }

    public Response writeById(String id, String gid, String uid, String mode, String serverSideEncryption, byte[] input) throws NoSuchAlgorithmException, ReconstructionException {

        SimpleFile obj = new SimpleFile(id, input, new HashMap<String, String>());

        obj.setMetaData("gid", gid);
        obj.setMetaData("uid", uid);
        obj.setMetaData("mode", mode);

        engine.putObject(obj);

        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] thedigest = md.digest(input);

        engine.getObject(id);

        return Response.accepted().header("ETag", Hex.encodeHex(thedigest)).build();
    }

    public Response deleteById(String id) throws ReconstructionException {

        FSObject obj = engine.getObject(id);
        engine.deleteObject(obj);

        return Response.accepted().status(204).build();
    }

    public Response getStatById(String id) throws ReconstructionException, NoSuchAlgorithmException {

        Map<String, String> result = engine.statObject(id);

        if (result != null) {
            FSObject obj = engine.getObject(id);

            if (obj == null) {
                System.err.println("returning 404");
                ResponseBuilder resp = Response.accepted().status(404);
                return resp.build();
            }

            SimpleFile file = null;
            if (obj instanceof SimpleFile) {
                file = (SimpleFile) obj;
            } else {
                assert(false);
            }

            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] thedigest = md.digest(file.getData());
            String etag = Hex.encodeHex(thedigest);

            ResponseBuilder resp = Response.accepted().status(200);

            System.err.println("Content-Length is " + file.getData().length);

            resp.header("Content-Length", "" + file.getData().length);
            resp.header("x-foobar", "" + file.getData().length);
            resp.header("ETag", etag);

            Map<String, String> md1 = file.getMetadata();

            for (Map.Entry<String, String> i : md1.entrySet()) {
                System.err.println("metadata: " + i.getKey() + " -> " + i.getValue());
            }

            if (md1.get("uid") != null) {
                resp.header("x-amz-meta-uid", md1.get("uid").replace("\r\n", ""));
            }

            if (md1.get("gid") != null) {
                resp.header("x-amz-meta-gid", md1.get("gid").replace("\r\n", ""));
            }

            if (md1.get("mode") != null) {
                resp.header("x-amz-meta-mode", md1.get("mode").replace("\r\n", ""));
            }

            Response r = resp.build();

            System.err.println("r->length: " + r.getLength());
            System.err.println("returning 200");
            return r;
        } else {
            System.err.println("returning 404");
            return Response.accepted().status(404).build();
        }
    }
}
