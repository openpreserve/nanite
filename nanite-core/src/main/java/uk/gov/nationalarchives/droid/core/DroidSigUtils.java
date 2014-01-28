/**
 * 
 */
package uk.gov.nationalarchives.droid.core;

import uk.gov.nationalarchives.droid.core.signature.droid6.FFSignatureFile;

/**
 * @author Andrew Jackson <Andrew.Jackson@bl.uk>
 *
 */
public class DroidSigUtils {

	/**
	 * Allow external code to access the signature file directly.
	 * 
	 * @param bid
	 * @return
	 */
	public static FFSignatureFile getSigFile( BinarySignatureIdentifier bid ) {
		return bid.getSigFile();
	}
	
}
