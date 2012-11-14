/**
 * Copyright (C) 2012 Andrew Jackson <Andrew.Jackson@bl.uk>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opf_labs.fmts.fidget;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.SortedSet;

import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.mime.MimeTypesFactory;
import org.opf_labs.fmts.corpora.govdocs.GovDocs;
import org.opf_labs.fmts.corpora.govdocs.GovDocsCorpora;

/**
 * Class that wraps the Apache Tika MimeTypes Repository for purposes of
 * developing and testing Tika signatures. The main feature is one of loading
 * order & signature overloading. To be clear, the order of loading matters and
 * signatures loaded later overload those loaded before.
 * 
 * The class allows the caller to construct a TikaSigTester with files and
 * streams of their choosing, while mixing and matching with the resource files
 * with the aim of making developing and testing new signatures simple.
 * 
 * The loading of any particular category of definition is optional but to help
 * prevent unexpected bugs the order of loading is strictly enforced as follows:
 * <ol>
 * <li>THE tika-mimetypes.xml from the org.apache.tika.mime resource folder,
 * comes with the MimeTypes class.</li>
 * <li>ALL other file called custom-mimetypes.xml provided in the
 * orga.apache.tika.mime resource folder(s?).</li>
 * <li>Any MIME definitions provided by the caller, in the order provided by the
 * caller.</li>
 * </ol>
 * 
 * @author <a href="mailto:carl@openplanetsfoundation.org">Carl Wilson</a>.</p>
 *         <a href="https://github.com/carlwilson">carlwilson AT github</a>.</p>
 * @version 0.1
 * 
 *          Created 1 Nov 2012:22:09:39
 */
public final class TikaSigTester {
	private final static char TAB = '\t';
	/**
	 * The minimum data legnth required by Tika ID methods, BUT also the MAX
	 * number of bytes read from a stream during identification. This was
	 * generated by a default Tika MimeTypes..
	 */
	public final static int EMPTY_MIN_LENGTH = MimeTypesFactory.create()
			.getMinLength();
	private final MimeTypes mimeRepository;

	private TikaSigTester() {
		throw new AssertionError("NO THROUGH ROAD, use the static methods.");
	}

	private TikaSigTester(MimeTypes mimeRepo) {
		assert (mimeRepo != null);
		this.mimeRepository = mimeRepo;
	}

	/**
	 * Creates a TikaSigTester with only the tika-mimetypes.xml loaded from the
	 * Tika MimeTypes classloader.
	 * 
	 * @return a Tika MimeType definition only instance of the tester
	 */
	public final static TikaSigTester justTika() {
		try {
			final MimeTypes repo = MimeTypesFactory
					.create(new URL[] { TikaResourceHelper.getCoreUrl() });
			return new TikaSigTester(repo);
		} catch (Exception excep) {
			throw new IllegalStateException(excep);
		}
	}

	/**
	 * Creates a TikaSigTester with only the tika-mimetypes.xml loaded from the
	 * Tika MimeTypes classloader.
	 * 
	 * @return a Tika MimeType definition only instance of the tester
	 */
	public final static TikaSigTester justCustom() {
		try {
			List<URL> custUrls = TikaResourceHelper.getCustomUrls();
			final MimeTypes repo = MimeTypesFactory.create(custUrls
					.toArray(new URL[custUrls.size()]));
			return new TikaSigTester(repo);
		} catch (Exception excep) {
			throw new IllegalStateException(
					"Missing or corrupt mime type definitions.");
		}
	}

	/**
	 * Creates a "vanilla" set up TikaSigTester with Tika and custom MIME
	 * definitions loaded. The custom defs override the initial defs
	 * 
	 * @return the vanilla instance of the tester
	 * @throws IllegalStateException
	 *             If the internal sig files aren't available, given this is
	 *             probably catastrophic we throw a runtime.
	 */
	public final static TikaSigTester vanilla() {
		try {
			final MimeTypes repo = MimeTypesFactory.create(
					TikaResourceHelper.TIKA_MIMETYPES,
					TikaResourceHelper.CUSTOM_MIMETYPES);
			return new TikaSigTester(repo);
		} catch (Exception excep) {
			throw new IllegalStateException(
					"Missing or corrupt mime type definitions.");
		}
	}

	/**
	 * @see org.apache.tika.mime.MimeTypesFactory#create(InputStream...)
	 * 
	 * @param streams
	 *            the mime type definition streams to parse
	 * @return a new TikaSigTester initialised from the streams
	 * @throws IOException
	 *             if the stream can not be read
	 * @throws MimeTypeException
	 *             if the type configuration is invalid
	 */
	public static final TikaSigTester streamsOnly(InputStream... streams)
			throws MimeTypeException, IOException {
		final MimeTypes repo = MimeTypesFactory.create(streams);
		return new TikaSigTester(repo);
	}

	/**
	 * Loads the Tika MIME defs first followed by the streams so they override
	 * the Tika defs
	 * 
	 * @param streams
	 *            the mime type definition streams to parse
	 * @return a new TikaSigTester initialised from the streams
	 * @throws IOException
	 *             if the stream can not be read
	 * @throws MimeTypeException
	 *             if the type configuration is invalid
	 */
	public static final TikaSigTester tikaAndStreams(InputStream... streams)
			throws MimeTypeException, IOException {
		InputStream[] coreStr = new InputStream[] { TikaResourceHelper
				.getCoreUrl().openStream() };
		return new TikaSigTester(TikaResourceHelper.fromStreamArrays(coreStr,
				streams));
	}

	/**
	 * Loads the "Vanilla" MIME defs first followed by the streams so they
	 * override the other defs, i.e. Tika <- Custom <- streams
	 * 
	 * @param streams
	 *            the mime type definition streams to parse
	 * @return a new TikaSigTester initialised from the streams
	 * @throws IOException
	 *             if the stream can not be read
	 * @throws MimeTypeException
	 *             if the type configuration is invalid
	 */
	public static final TikaSigTester vanillaAndStreams(InputStream... streams)
			throws MimeTypeException, IOException {
		InputStream[] vanStrs = TikaResourceHelper
				.streamsFromUrls(TikaResourceHelper.getVanillaUrls());
		return new TikaSigTester(TikaResourceHelper.fromStreamArrays(vanStrs,
				streams));
	}

	/**
	 * Loads the Custom MIME defs first followed by the streams so they override
	 * the Custom defs
	 * 
	 * @param streams
	 *            the mime type definition streams to parse
	 * @return a new TikaSigTester initialised from the custom defs & streams
	 * @throws IOException
	 *             if the stream can not be read
	 * @throws MimeTypeException
	 *             if the type configuration is invalid
	 */
	public static final TikaSigTester customAndStreams(InputStream... streams)
			throws MimeTypeException, IOException {
		InputStream[] custStrs = TikaResourceHelper
				.streamsFromUrls(TikaResourceHelper.getCustomUrls());
		return new TikaSigTester(TikaResourceHelper.fromStreamArrays(custStrs,
				streams));
	}

	/**
	 * @see TikaSigTester#streamsOnly(InputStream...)
	 * @param files
	 *            the mime type definition files to parse
	 * @return a new TikaSigTester initialised from the files
	 * @throws IOException
	 *             if the files can not be read
	 * @throws MimeTypeException
	 *             if the type configuration is invalid
	 */
	public static final TikaSigTester filesOnly(File... files)
			throws MimeTypeException, IOException {
		return streamsOnly(TikaResourceHelper.streamsFromFiles(files));
	}

	/**
	 * @see TikaSigTester#tikaAndStreams(InputStream...)
	 * @param files
	 *            the mime type definition files to parse
	 * @return a new TikaSigTester initialised from the files
	 * @throws IOException
	 *             if the files can not be read
	 * @throws MimeTypeException
	 *             if the type configuration is invalid
	 */
	public static final TikaSigTester tikaAndFiles(File... files)
			throws MimeTypeException, IOException {
		return tikaAndStreams(TikaResourceHelper.streamsFromFiles(files));
	}

	/**
	 * @see TikaSigTester#vanillaAndStreams(InputStream...)
	 * @param files
	 *            the mime type definition files to parse
	 * @return a new TikaSigTester initialised from the files
	 * @throws IOException
	 *             if the files can not be read
	 * @throws MimeTypeException
	 *             if the type configuration is invalid
	 */
	public static final TikaSigTester vanillaAndFiles(File... files)
			throws MimeTypeException, IOException {
		return tikaAndStreams(TikaResourceHelper.streamsFromFiles(files));
	}

	/**
	 * @see TikaSigTester#customAndStreams(InputStream...)
	 * @param files
	 *            the mime type definition files to parse
	 * @return a new TikaSigTester initialised from the files
	 * @throws IOException
	 *             if the files can not be read
	 * @throws MimeTypeException
	 *             if the type configuration is invalid
	 */
	public static final TikaSigTester customAndFiles(File... files)
			throws MimeTypeException, IOException {
		return customAndStreams(TikaResourceHelper.streamsFromFiles(files));
	}

	/**
	 * @param file
	 *            the file to identify
	 * @return the IdentificationResult from the file
	 * @throws FileNotFoundException
	 *             if the file's not there
	 */
	public IdentificationResult identify(File file)
			throws FileNotFoundException {
		return TikaIdentifier.fromFile(this.mimeRepository, file);
	}
	
	/**
	 * @param stream
	 *            the stream to identify
	 * @return the IdentificationResult from the file
	 * @throws FileNotFoundException
	 *             if the file's not there
	 */
	public IdentificationResult identify(InputStream stream)
			throws FileNotFoundException {
		return TikaIdentifier.fromStream(this.mimeRepository, stream);
	}
	

	@SuppressWarnings("resource")
	List<IdentificationResult> identify(String govDocsData) {
		File govDocsRoot = new File(govDocsData);
		System.out.println("Assessing Corpora:" + new Date());
		GovDocsCorpora govDocs = GovDocs.newInstance(govDocsRoot);
		System.out.println(govDocs);
		List<IdentificationResult> results = new ArrayList<IdentificationResult>();
		System.out.println("Start:" + new Date());
		for (int foldNum = 0; foldNum < 1000; foldNum++) {
			System.out.println("Folder:" + foldNum + " " + new Date());
			for (int fileNum = 0; fileNum < 1000; fileNum++) {
				InputStream str = null;
				URI ident;
				try {
					str = govDocs.getItem(foldNum, fileNum);
					ident = URI.create("govdoc:item:" + govDocs.getItemName(foldNum, fileNum));
					IdentificationResult result = TikaIdentifier.fromStream(this.mimeRepository, str, ident);
					results.add(result);
				} catch (FileNotFoundException excep) {
					System.err.println("Missing file number " + fileNum);
					System.err.println(excep);
					//excep.printStackTrace();
					// Just miss for now
				}
				finally {
					try {
						if (str != null) str.close();
					} catch (IOException excep) {
						// TODO Auto-generated catch block
						excep.printStackTrace();
					}
				}
			}
		}
		return results;
	}

	/**
	 * @return the sorted set of media types contained in the MIME CorpraType Repo
	 */
	public final SortedSet<MediaType> getTypes() {
		return Collections.unmodifiableSortedSet(this.mimeRepository
				.getMediaTypeRegistry().getTypes());
	}

	/**
	 * A little test main to identify GovDocsDirectories from passed param
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String... args) throws Exception {
		if (args.length > 0) {
			String govDocsData = args[0];
			TikaSigTester sw = TikaSigTester.vanilla();
			List<IdentificationResult> results = sw.identify(govDocsData);
			FileWriter writer = new FileWriter("d:/tika.txt");
			BufferedWriter out = new BufferedWriter(writer);
			for (IdentificationResult result : results) {
				System.out.println(result);
				out.write(result.toString());
			}
			out.close();
		} else {
			System.err.println("Expected a GovDocsDirectories dir.");
		}

	}

	/**
	 * Print out a summary of all known types:
	 * 
	 * @param tester
	 *            the TikaSigTester to print the known types from
	 * 
	 * @throws MimeTypeException
	 */
	public static void printTypes(TikaSigTester tester)
			throws MimeTypeException {
		for (MediaType md : tester.getTypes()) {
			MimeType mt = tester.mimeRepository.forName(md.toString());
			StringBuilder str = new StringBuilder(mt.getType().toString())
					.append(TAB).append(mt.getExtension()).append(TAB)
					.append(mt.getName()).append(TAB)
					.append(mt.getDescription());
			System.out.println(str);
		}
	}
}