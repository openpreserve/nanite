package uk.bl.wap.nanite.tika;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.mime.MimeTypesFactory;

import uk.bl.wap.nanite.ExtendedMimeType;
import uk.bl.wap.nanite.Identification;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.activation.MimeTypeParseException;

public class TikaClient extends Identification {

	private MimeTypes mimeTypes;

    public TikaClient() throws MimeTypeException, IOException {
    	mimeTypes = TikaClient.create(
                "tika-mimetypes.xml", "custom-mimetypes.xml");
    }
    
    public MimeTypes getMimeTypes() {
    	return this.mimeTypes;
    }
    
	@Override
	public ExtendedMimeType identify(InputStream input) {
        Metadata metadata = new Metadata();
        MediaType mediaType;
		try {
	        TikaInputStream stream = TikaInputStream.get(input);
			mediaType = mimeTypes.detect(stream, metadata);
	        stream.close();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
        String mime = mediaType.toString().intern();
        try {
			return new ExtendedMimeType(mime);
		} catch (MimeTypeParseException e) {
			e.printStackTrace();
			return null;
		}
	}

    public static MimeTypes create(String coreFilePath, String extensionFilePath)
            throws IOException, MimeTypeException {
        // This allows us to replicate class.getResource() when using
        //  the classloader directly
        String classPrefix = MimeTypes.class.getPackage().getName().replace('.', '/') + "/";
        ClassLoader cl = MimeTypes.class.getClassLoader();
       
        // Get the core URL, and all the extensions URLs
        URL coreURL = cl.getResource(classPrefix+coreFilePath);
        List<URL> extensionURLs = Collections.list(
              cl.getResources(classPrefix+extensionFilePath));

        // Swap that into an Array, and process
        List<URL> urls = new ArrayList<URL>();
        urls.add(coreURL);
        urls.addAll(extensionURLs);
        
        return MimeTypesFactory.create( urls.toArray(new URL[urls.size()]) );
    }

    public Identity detect(File input) throws IOException {
        long before = System.currentTimeMillis();
        String mime = this.identify(input).toString();
        long duration = System.currentTimeMillis() - before;
        return new Identity(input,mime,duration);

    }


    public  List<Identity> identify(String govDocsData) throws Exception {

        List<File> datafiles = getFiles(new File(govDocsData));
        return identify(datafiles);

    }


    public List<Identity> identify(List<File> datafiles) throws Exception {

        List<Identity> identities = new ArrayList<Identity>();

        TikaClient sw = new TikaClient();

        for (File file : datafiles) {
            if (!file.isFile()){
                continue;
            }
            Identity detection = sw.detect(file);
            identities.add(detection);
            System.out.println(file.getAbsolutePath()+":"+detection.getMime());
        }
        return identities;


    }


    private static List<File> getFiles(File dir){
        List<File> result = new ArrayList<File>();
        if (!dir.isDirectory()){
            result.add(dir);
            return result;
        }
        File[] files = dir.listFiles();
        for (File file : files) {
            result.addAll(getFiles(file));
        }
        Collections.sort(result);
        return result;
    }

    public class Identity {

        private String mime;

        private long time;
        private File file;

        public Identity(File file, String mime, long time) {
            this.file = file;
            this.mime = mime;
            this.time = time;
        }

        public String getMime() {
            return mime;
        }

        public long getTime() {
            return time;
        }

        public File getFile() {
            return file;
        }
    }

    public static void main(String... args) throws Exception {
        String govDocsData = null;
        if (args.length == 1) {
            govDocsData = args[0];
        }
        TikaClient sw = new TikaClient();
        sw.identify(govDocsData);

    }


}