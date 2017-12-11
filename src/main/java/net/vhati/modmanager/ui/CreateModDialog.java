package net.vhati.modmanager.ui;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.vhati.modmanager.ui.FieldEditorPanel;
import net.vhati.modmanager.ui.FieldEditorPanel.ContentType;
import net.vhati.modmanager.ui.RegexDocument;
import net.vhati.modmanager.xml.JDOMModMetadataWriter;


public class CreateModDialog extends JDialog implements ActionListener {

	private static final Logger log = LogManager.getLogger( CreateModDialog.class );

	protected static final String DIR_NAME = "Directory Name";
	protected static final String TITLE = "Title";
	protected static final String URL = "Thread URL";
	protected static final String AUTHOR = "Author";
	protected static final String VERSION = "Version";
	protected static final String DESC = "Description";

	protected FieldEditorPanel editorPanel;
	protected JButton applyBtn;

	protected File modsDir;


	public CreateModDialog( Frame owner, File modsDir ) {
		super( owner, "New Mod" );
		this.setDefaultCloseOperation( JDialog.DISPOSE_ON_CLOSE );

		this.modsDir = modsDir;

		editorPanel = new FieldEditorPanel( false );
		editorPanel.setBorder( BorderFactory.createEmptyBorder( 10, 10, 0, 10 ) );
		editorPanel.setNameWidth( 100 );
		editorPanel.setValueWidth( 350 );

		editorPanel.addRow( DIR_NAME, ContentType.STRING );
		editorPanel.getString( DIR_NAME ).setDocument( new RegexDocument( "[^\\/:;*?<>|^\"]*" ) );
		editorPanel.addTextRow( String.format( "The name of a directory to create in the %s/ folder.", modsDir.getName() ) );
		editorPanel.addSeparatorRow();
		editorPanel.addRow( TITLE, ContentType.STRING );
		editorPanel.addTextRow( "The title of this mod." );
		editorPanel.addSeparatorRow();
		editorPanel.addRow( URL, ContentType.STRING );
		editorPanel.addTextRow( "This mod's thread on the forum." );
		editorPanel.addSeparatorRow();
		editorPanel.addRow( AUTHOR, ContentType.STRING );
		editorPanel.addTextRow( "Your forum user name." );
		editorPanel.addSeparatorRow();
		editorPanel.addRow( VERSION, ContentType.STRING );
		editorPanel.addTextRow( "The revision/variant of this release, preferably at least a number." );
		editorPanel.addSeparatorRow();
		editorPanel.addRow( DESC, ContentType.TEXT_AREA );
		editorPanel.getTextArea( DESC ).setRows( 15 );
		editorPanel.addTextRow( "Summary of gameplay effects; flavor; features; concerns about compatibility, preferred order, requirements; replaced ship slot; etc." );

		JPanel ctrlPanel = new JPanel();
		ctrlPanel.setLayout( new BoxLayout( ctrlPanel, BoxLayout.X_AXIS ) );
		ctrlPanel.setBorder( BorderFactory.createEmptyBorder( 10, 0, 10, 0 ) );
		ctrlPanel.add( Box.createHorizontalGlue() );
		applyBtn = new JButton( "Generate Mod" );
		applyBtn.addActionListener( this );
		ctrlPanel.add( applyBtn );
		ctrlPanel.add( Box.createHorizontalGlue() );

		final JScrollPane editorScroll = new JScrollPane( editorPanel );
		editorScroll.setVerticalScrollBarPolicy( ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS );
		editorScroll.getVerticalScrollBar().setUnitIncrement( 10 );
		int vbarWidth = editorScroll.getVerticalScrollBar().getPreferredSize().width;
		editorScroll.setPreferredSize( new Dimension( editorPanel.getPreferredSize().width+vbarWidth+5, 400 ) );

		JPanel contentPane = new JPanel( new BorderLayout() );
		contentPane.add( editorScroll, BorderLayout.CENTER );
		contentPane.add( ctrlPanel, BorderLayout.SOUTH );
		this.setContentPane( contentPane );
		this.pack();
		this.setMinimumSize( new Dimension( 250, 250 ) );


		editorScroll.addAncestorListener(new AncestorListener() {
			@Override
			public void ancestorAdded( AncestorEvent e ) {
				editorScroll.getViewport().setViewPosition( new Point( 0, 0 ) );
			}
			@Override
			public void ancestorMoved( AncestorEvent e ) {
			}
			@Override
			public void ancestorRemoved( AncestorEvent e ) {
			}
		});
	}

	@Override
	public void actionPerformed( ActionEvent e ) {
		Object source = e.getSource();

		if ( source == applyBtn ) {
			String dirName = editorPanel.getString( DIR_NAME ).getText().trim();
			String modTitle = editorPanel.getString( TITLE ).getText().trim();
			String modURL = editorPanel.getString( URL ).getText().trim();
			String modAuthor = editorPanel.getString( AUTHOR ).getText().trim();
			String modVersion = editorPanel.getString( VERSION ).getText().trim();
			String modDesc = editorPanel.getTextArea( DESC ).getText().trim();

			if ( dirName.length() == 0 ) {
				JOptionPane.showMessageDialog( CreateModDialog.this, "No directory name was given.", "Nothing to do", JOptionPane.WARNING_MESSAGE );
				return;
			}

			File genDir = new File( modsDir, dirName );
			if ( !genDir.exists() ) {
				try {
					// Generate the mod.
					if ( genDir.mkdir() ) {
						File appendixDir = new File ( genDir, "mod-appendix" );
						if ( appendixDir.mkdir() ) {
							File metadataFile = new File( appendixDir, "metadata.xml" );

							JDOMModMetadataWriter.writeMetadata( metadataFile, modTitle, modURL, modAuthor, modVersion, modDesc );
						}
						else {
							throw new IOException( String.format( "Failed to create directory: %s", appendixDir.getName() ) );
						}
					}
					else {
						throw new IOException( String.format( "Failed to create directory: %s", genDir.getName() ) );
					}

					// Show the folder.
					try {
						if ( Desktop.isDesktopSupported() ) {
							Desktop.getDesktop().open( genDir.getCanonicalFile() );
						} else {
							log.error( String.format( "Java cannot open the %s/ folder for you on this OS", genDir.getName() ) );
						}
					}
					catch ( IOException f ) {
						log.error( String.format( "Error opening %s/ folder", genDir.getName() ), f );
					}

					// All done.
					CreateModDialog.this.dispose();
				}
				catch ( IOException f ) {
					log.error( String.format( "Failed to generate new mod: %s", genDir.getName() ), f );

					JOptionPane.showMessageDialog( CreateModDialog.this, String.format( "Failed to generate new mod: %s\n%s", genDir.getName(), f.getMessage() ), "Error", JOptionPane.ERROR_MESSAGE );
				}
			}
			else {
				JOptionPane.showMessageDialog( CreateModDialog.this, String.format( "A directory named \"%s\" already exists.", genDir.getName() ), "Nothing to do", JOptionPane.WARNING_MESSAGE );
			}
		}
	}
}
