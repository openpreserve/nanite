package dk.statsbiblioteket.percipio;

import dk.statsbiblioteket.percipio.Brain;
import dk.statsbiblioteket.percipio.datastructures.Score;
import dk.statsbiblioteket.percipio.datastructures.Signature;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * Created by IntelliJ IDEA.
 * User: abr
 * Date: Nov 17, 2010
 * Time: 10:35:15 AM
 * To change this template use File | Settings | File Templates.
 */
public class BrainTest {

    Marshaller marshaller;
    Unmarshaller unmarshaller;

    @Before
    public void setUp() throws Exception {

        JAXBContext context = JAXBContext.newInstance(Signature.class);

        marshaller = context.createMarshaller();
        marshaller.setProperty( Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE );

        unmarshaller = context.createUnmarshaller();

    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testLearn() throws Exception {

        Brain brain = new Brain();
        File[] pdffiles = new File("src/test/resources/pdf").listFiles();
        Signature signature = brain.learn(pdffiles);
        System.out.println("Learned signature: "+signature.getFrontBlock().pattern.get(0).getAscii());
    }

    @Test
    public void testScore() throws Exception {
        List<File> pdffiles = Arrays.asList(new File("src/test/resources/pdf").listFiles());


        ArrayList<File> arraylist = new ArrayList<File>();
        arraylist.addAll(pdffiles);
        File firstPdf = arraylist.remove(3);

        Brain brain = new Brain();

        Signature signature = brain.learn(arraylist);
        Score score = brain.score(Arrays.asList(new Signature[]{signature}), firstPdf);
        assertTrue("Score too low, this should really be a pdf file", score.getScoreboard().first().getA() > 10);

        System.out.println("score for pdf file '"+firstPdf.getName()+"' is "+score.getScoreboard().first().getA());
        /*marshaller.marshal(signature,System.out);*/
    }

    @Test
    public void testRelearn() throws Exception {
        List<File> pdffiles = Arrays.asList(new File("src/test/resources/pdf").listFiles());


        ArrayList<File> arraylist = new ArrayList<File>();
        arraylist.addAll(pdffiles);
        File thirdPdf = arraylist.remove(3);
        File secondPdf = arraylist.remove(2);
        File firstPdf = arraylist.remove(1);
 
        Brain brain = new Brain();

        // FIXME Scoring and re-learning appears to be broken.
        
        Signature signature = brain.learn(arraylist);
        Score score = brain.score(Arrays.asList(new Signature[]{signature}), firstPdf);
        /*System.out.println("score for pdf file '"+firstPdf.getName()+"' is "+score.getScoreboard().first().getA());*/
        assertTrue("Initial score of "+score.getScoreboard().first().getA()+" too low, this signature should be improved", score.getScoreboard().first().getA() > 10);

        /*marshaller.marshal(signature,System.out);
*/
        Signature relearnedSig = brain.relearn(signature, secondPdf, thirdPdf);
        score = brain.score(Arrays.asList(new Signature[]{relearnedSig}), firstPdf);
        assertTrue("Re-learned score of "+score.getScoreboard().first().getA()+" too low, this should really be a pdf file", score.getScoreboard().first().getA() > 10);

        /*System.out.println("score for pdf file '"+firstPdf.getName()+"' is "+score.getScoreboard().first().getA());
        marshaller.marshal(signature,System.out);
*/

    }

}
