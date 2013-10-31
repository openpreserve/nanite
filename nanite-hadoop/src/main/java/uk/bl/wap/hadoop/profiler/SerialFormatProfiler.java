/**
 * 
 */
package uk.bl.wap.hadoop.profiler;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import org.apache.hadoop.io.Text;
import org.archive.io.ArchiveReader;
import org.archive.io.ArchiveReaderFactory;
import org.archive.io.ArchiveRecord;

import uk.bl.wa.hadoop.WritableArchiveRecord;

/**
 * @author Andrew Jackson <Andrew.Jackson@bl.uk>
 *
 */
public class SerialFormatProfiler {
	
	private static FormatProfilerMapper fpm = new FormatProfilerMapper();
	
	static {
		fpm.configure(null);
	}

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		// Input arc/warc
		String inFilename = "../DOTUK-HISTORICAL-1996-2010-GROUP-AN-XAABJQ-20110428000000-00000.arc.gz";
		if( args.length > 0 ) {
			inFilename = args[0];
		}
		
		// 
		File in = new File(inFilename);		
		ArchiveReader reader = ArchiveReaderFactory.get(in);
		Iterator<ArchiveRecord> ir = reader.iterator();
		int recordCount = 0;
		
		// Iterate though each record in the WARC file
		while( ir.hasNext() ) {
			ArchiveRecord rec = ir.next();
			recordCount++;
			System.out.println("Processing "+recordCount+"@"+rec.getHeader().getOffset()+
					"+"+rec.available()+","+rec.getHeader().getLength()+
					": "+rec.getHeader().getUrl());
			WritableArchiveRecord war = new WritableArchiveRecord();
			war.setRecord(rec);
			fpm.map(new Text(rec.getHeader().getUrl()), war, null, null);
		}

	}

}
