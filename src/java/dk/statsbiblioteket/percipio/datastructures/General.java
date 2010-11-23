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
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class General {

    @XmlElement(name = "FileNum")
    private int numberOfFiles;

    @XmlElement(name = "Date")
    private String date;

    @XmlElement(name = "Name")
    private String name = "Please provide a name for this signature";

    public General() {
    }

    public General(int numberOfFiles, String date) {
        this.numberOfFiles = numberOfFiles;
        this.date = date;
    }

    public int getNumberOfFiles() {
        return numberOfFiles;
    }

    public void setNumberOfFiles(int numberOfFiles) {
        this.numberOfFiles = numberOfFiles;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
