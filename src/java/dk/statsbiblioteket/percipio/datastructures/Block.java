package dk.statsbiblioteket.percipio.datastructures;

import javax.xml.bind.annotation.XmlElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: abr
 * Date: Nov 16, 2010
 * Time: 2:25:59 PM
 * To change this template use File | Settings | File Templates.
 */

public class Block {

    @XmlElement(name = "Pattern")
    public List<BytePattern> pattern = new ArrayList<BytePattern>();
}
