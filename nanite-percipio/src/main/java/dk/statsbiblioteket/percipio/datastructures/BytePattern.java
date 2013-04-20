package dk.statsbiblioteket.percipio.datastructures;

import dk.statsbiblioteket.percipio.utilities.Bytes;

import javax.xml.bind.annotation.*;
import java.nio.charset.Charset;

/**
 * Created by IntelliJ IDEA.
 * User: abr
 * Date: Nov 16, 2010
 * Time: 12:30:50 PM
 * To change this template use File | Settings | File Templates.
 */
@XmlAccessorType(XmlAccessType.NONE)
@XmlType(propOrder = {"bytes","offset","ascii"})
public class BytePattern {


    private byte[] pattern;


    private int offset;



    public BytePattern(int offset, byte[] pattern) {
        this.offset = offset;
        this.pattern = pattern;
    }

    public BytePattern() {
    }
    
    @XmlElement(name = "Pos")
    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }


    @XmlElement(name = "Bytes")
    public String getBytes(){
        return Bytes.toHex(pattern).toUpperCase();
    }

    public void setBytes(String bytes){
        pattern = Bytes.hexStringToByteArray(bytes);
    }
    
    @XmlElement(name = "ASCII")
    public String getAscii(){
        return Bytes.stripNonValidXMLCharacters(new String(pattern, Charset.forName("US-ASCII")));
        
    }

    public void setAscii(String ascii){
        //do nothing
    }

    @XmlTransient
    public byte[] getPattern(){
        return pattern;
    }

    public void setPattern(byte[] pattern) {
        this.pattern = pattern;
    }
    
}
