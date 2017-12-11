package net.vhati.modmanager.xml;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import org.jdom2.CDATA;
import org.jdom2.Comment;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Text;
import org.jdom2.output.Format;
import org.jdom2.output.LineSeparator;
import org.jdom2.output.XMLOutputter;

import net.vhati.modmanager.core.EOLWriter;


public class JDOMModMetadataWriter {

	/**
	 * Writes boilerplate metadata for a mod.
	 *
	 * @param outFile "{modDir}/mod-appendix/metadata.xml"
	 * @param modTitle
	 * @param modURL
	 * @param modAuthor
	 * @param modVersion
	 * @param modDesc
	 */
	public static void writeMetadata( File outFile, String modTitle, String modURL, String modAuthor, String modVersion, String modDesc ) throws IOException {
		StringBuilder buf = new StringBuilder();

		Element rootNode = new Element( "metadata" );
		Document doc = new Document( rootNode );

		rootNode.addContent( new Text( "\n" ) );

		rootNode.addContent( new Text( "\t" ) );
		buf.setLength( 0 );
		buf.append( "\n" );
		buf.append( "\t\tCDATA tags mean no need to escape special characters.\n" );
		buf.append( "\t\tDon't worry about spaces at the start/end. That gets trimmed.\n" );
		buf.append( "\t" );
		rootNode.addContent( new Comment( buf.toString() ) );
		rootNode.addContent( new Text( "\n\n\n" ) );

		// title.
		rootNode.addContent( new Text( "\t" ) );
		rootNode.addContent( new Comment( String.format( " %s ", "The title of this mod." ) ) );
		rootNode.addContent( new Text( "\n" ) );

		rootNode.addContent( new Text( "\t" ) );
		Element titleNode = new Element( "title" );
		titleNode.setContent( new CDATA( String.format( " %s ", modTitle ) ) );
		rootNode.addContent( titleNode );
		rootNode.addContent( new Text( "\n\n\n" ) );

		// threadUrl.
		rootNode.addContent( new Text( "\t" ) );
		buf.setLength( 0 );
		buf.append( "\n" );
		buf.append( "\t\tThis mod's thread on subsetgames.com.\n" );
		buf.append( "\t\tIf there's no thread yet, create one to announce your upcoming mod in the\n" );
		buf.append( "\t\tforum. Then paste the url here.\n" );
		buf.append( "\t" );
		rootNode.addContent( new Comment( buf.toString() ) );
		rootNode.addContent( new Text( "\n" ) );

		rootNode.addContent( new Text( "\t" ) );
		Element urlNode = new Element( "threadUrl" );
		urlNode.setContent( new CDATA( String.format( " %s ", modURL ) ) );
		rootNode.addContent( urlNode );
		rootNode.addContent( new Text( "\n\n\n" ) );

		// author.
		rootNode.addContent( new Text( "\t" ) );
		rootNode.addContent( new Comment( String.format( " %s ", "Your forum user name." ) ) );
		rootNode.addContent( new Text( "\n" ) );

		rootNode.addContent( new Text( "\t" ) );
		Element authorNode = new Element( "author" );
		authorNode.setContent( new CDATA( String.format( " %s ", modAuthor ) ) );
		rootNode.addContent( authorNode );
		rootNode.addContent( new Text( "\n\n\n" ) );

		// version.
		rootNode.addContent( new Text( "\t" ) );
		buf.setLength( 0 );
		buf.append( "\n" );
		buf.append( "\t\tThe revision/variant of this release, preferably at least a number.\n" );
		buf.append( "\t\tExamples:\n" );
		buf.append( "\t\t\t0.3\n" );
		buf.append( "\t\t\t2.1c Ships Only WIP\n" );
		buf.append( "\t\t\t2.4.1 Hi-res Bkgs\n" );
		buf.append( "\t\t\t1.0 for FTL 1.03.1\n" );
		buf.append( "\t" );
		rootNode.addContent( new Comment( buf.toString() ) );
		rootNode.addContent( new Text( "\n" ) );

		rootNode.addContent( new Text( "\t" ) );
		Element versionNode = new Element( "version" );
		versionNode.setContent( new CDATA( String.format( " %s ", modVersion ) ) );
		rootNode.addContent( versionNode );
		rootNode.addContent( new Text( "\n\n\n" ) );

		// description.
		rootNode.addContent( new Text( "\t" ) );
		Element descNode = new Element( "description" );
		descNode.addContent( new Text( "\n" ) );
		descNode.addContent( new CDATA( String.format( "\n%s\n", modDesc ) ) );
		descNode.addContent( new Text( "\n\t" ) );
		rootNode.addContent( descNode );

		rootNode.addContent( new Text( "\n\n" ) );

		buf.setLength( 0 );
		buf.append( "\n" );
		buf.append( "\tSuggestions for the description...\n" );
		buf.append( "\n" );
		buf.append( "\tWrite a short paragraph about the mod's effect first (what style ship, how\n" );
		buf.append( "\tdoes it affect gameplay). No need to introduce yourself.\n" );
		buf.append( "\n" );
		buf.append( "\tOptionally add a paragraph of background flavor.\n" );
		buf.append( "\n" );
		buf.append( "\tOptionally list important features.\n" );
		buf.append( "\n" );
		buf.append( "\tList any concerns about mod compatibility, preferred order, or requirements.\n" );
		buf.append( "\n" );
		buf.append( "\tMention \"Replaces the XYZ ship.\" if relevant.\n" );
		buf.append( "\t\tKestrel-A, Stealth-A, Mantis-A,\n" );
		buf.append( "\t\tEngi-A,    Fed-A,     Slug-A,\n" );
		buf.append( "\t\tRock-A,    Zoltan-A,  Crystal-A\n" );
		buf.append( "\n" );
		buf.append( "\tAbove all, keep the description general, so you won't have to edit\n" );
		buf.append( "\tthat again for each new version.\n" );
		buf.append( "\t" );
		rootNode.addContent( new Comment( buf.toString() ) );
		rootNode.addContent( new Text( "\n" ) );

		Format format = Format.getPrettyFormat();
		format.setTextMode( Format.TextMode.PRESERVE );
		format.setExpandEmptyElements( false );
		format.setOmitDeclaration( false );
		format.setIndent( "\t" );
		format.setLineSeparator( LineSeparator.CRNL );

		Writer writer = null;
		try {
			writer = new EOLWriter( new FileWriter( outFile ), "\r\n" );

			XMLOutputter xmlOutput = new XMLOutputter();
			xmlOutput.setFormat( format );
			xmlOutput.output( doc, writer );
		}
		finally {
			try {if ( writer != null ) writer.close();}
			catch ( IOException e ) {}
		}
	}
}
