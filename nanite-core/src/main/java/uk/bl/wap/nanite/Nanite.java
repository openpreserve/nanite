/**
 * 
 */
package uk.bl.wap.nanite;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import javax.activation.MimeTypeParseException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

import uk.bl.wap.nanite.droid.DroidDetector;
import uk.gov.nationalarchives.droid.command.action.CommandExecutionException;
import uk.gov.nationalarchives.droid.core.SignatureParseException;
import uk.gov.nationalarchives.droid.core.interfaces.signature.SignatureFileException;
import uk.gov.nationalarchives.droid.core.interfaces.signature.SignatureManagerException;

/**
 * 
 
 */
public class Nanite {

	DroidDetector nan = null;
	
	public Nanite() throws IOException, SignatureFileException, SignatureParseException, ConfigurationException, CommandExecutionException {
		nan = new DroidDetector();
	}

	/**
	 * @param args
	 * @throws IOException 
	 * @throws SignatureManagerException 
	 * @throws ConfigurationException 
	 * @throws SignatureFileException 
	 * @throws MimeTypeParseException 
	 * @throws SignatureParseException 
	 * @throws CommandExecutionException 
	 */
	public static void main(String[] args) throws IOException, SignatureManagerException, ConfigurationException, SignatureFileException, MimeTypeParseException, SignatureParseException, CommandExecutionException {
		DroidDetector nan = new DroidDetector();
		for( String fname : args ) {
			File file = new File(fname);
			System.out.println("File: "+fname);
			System.out.println("Nanite using DROID binary signature file version "+nan.getBinarySignatureFileVersion());
			System.out.println("Result: " + nan.detect(file));
			System.out.println("Result: " + nan.detect(new FileInputStream(file),new Metadata()) );
			System.out.println("----");
		}
	}

	/**
	 * @param payload
	 * @return
	 * @throws IOException 
	 */
	public MediaType identify(byte[] payload) throws IOException {
		return nan.detect( new ByteArrayInputStream(payload), new Metadata());
	}	

}
