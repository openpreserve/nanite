package dk.statsbiblioteket.percipio.datastructures;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Created by IntelliJ IDEA.
 * User: abr
 * Date: Nov 16, 2010
 * Time: 12:00:21 PM
 * To change this template use File | Settings | File Templates.
 */
@XmlRootElement(name="TrID")
@XmlAccessorType(XmlAccessType.FIELD)
public class Signature {

    @XmlElement(name = "Info")
    private Info info = new Info();

    @XmlElement(name = "General")
    private General general = new General();

    @XmlElement(name = "FrontBlock")
    private Block frontBlock = new Block();

    @XmlElement(name = "EndBlock")
    private Block endBlock = new Block();

    public Signature() {
    }

    public Block getFrontBlock() {
        return frontBlock;
    }

    public Block getEndBlock() {
        return endBlock;
    }
    
    /**
	 * @return the info
	 */
	public Info getInfo() {
		return info;
	}

	public General getGeneral() {
        return general;
    }
}
