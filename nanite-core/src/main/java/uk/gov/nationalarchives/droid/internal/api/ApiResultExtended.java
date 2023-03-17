package uk.gov.nationalarchives.droid.internal.api;

import uk.gov.nationalarchives.droid.core.interfaces.IdentificationMethod;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationResult;
import uk.gov.nationalarchives.droid.core.signature.droid6.FFSignatureFile;

public class ApiResultExtended {
    private final String extension;
    private final IdentificationMethod method;
    private final String puid;

    private final String name;
    
    private final String mimeType;
    
    private final String version;

    public ApiResultExtended(String extension, IdentificationMethod method, String puid, String name, String mimeType, String version) {
        this.extension = extension;
        this.method = method;
        this.puid = puid;
        this.name = name;
        this.mimeType = mimeType;
        this.version = version;
    }
    
    public static ApiResultExtended fromIdentificationResult(String extension, IdentificationResult res, FFSignatureFile signatureFile) {
    	String mimeType = res.getMimeType();
    	// If this is a container match, patch in the MIME type for the PUID:
    	if( res.getMethod() == IdentificationMethod.CONTAINER) {
    		mimeType = signatureFile.getFileFormat(res.getPuid()).getMimeType();
    	}
    	ApiResultExtended r = new ApiResultExtended(
    			extension, res.getMethod(), res.getPuid(), res.getName(),
    			mimeType, res.getVersion());
    	return r;
    }

    public String getName() {
        return name;
    }

    public String getPuid() {
        return puid;
    }

    public IdentificationMethod getMethod() {
        return method;
    }

    public String getExtension() {
        return extension;
    }

	public String getMimeType() {
		return mimeType;
	}

	public String getVersion() {
		return version;
	}
}
