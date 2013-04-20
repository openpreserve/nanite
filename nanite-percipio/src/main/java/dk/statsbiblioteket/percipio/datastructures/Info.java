package dk.statsbiblioteket.percipio.datastructures;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

/**
 * Created by IntelliJ IDEA.
 * User: abr
 * Date: Nov 17, 2010
 * Time: 9:53:22 AM
 * To change this template use File | Settings | File Templates.
 * 
 * Example of fields:
		<Info>
                <FileType>SAPCAR CAR archive</FileType>
                <Ext>CAR</Ext>
                <ExtraInfo>
                        <Rem>ZIP like archiver for SAP.</Rem>
                        <RefURL>http://www.easymarketplace.de/SAPCAR.php</RefURL>
                </ExtraInfo>
                <User>Marco Pontello</User>
                <E-Mail>marcopon@gmail.com</E-Mail>
                <Home>http://mark0.net</Home>
        </Info>

 */
@XmlAccessorType(XmlAccessType.FIELD)
public class Info {

    @XmlElement(name = "FileType")
    private String fileType = "Please provide a name for this file type";

    public Info() {
    }

	/**
	 * @return the fileType
	 */
	public String getFileType() {
		return fileType;
	}

	/**
	 * @param fileType the fileType to set
	 */
	public void setFileType(String fileType) {
		this.fileType = fileType;
	}

}
