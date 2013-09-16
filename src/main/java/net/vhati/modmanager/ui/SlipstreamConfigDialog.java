package net.vhati.modmanager.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

import net.vhati.modmanager.core.FTLUtilities;
import net.vhati.modmanager.core.SlipstreamConfig;
import net.vhati.modmanager.ui.FieldEditorPanel;
import net.vhati.modmanager.ui.FieldEditorPanel.ContentType;


public class SlipstreamConfigDialog extends JFrame implements ActionListener {
	protected static final String ALLOW_ZIP = "allow_zip";
	protected static final String NEVER_RUN_FTL = "never_run_ftl";
	protected static final String USE_DEFAULT_UI = "use_default_ui";
	protected static final String REMEMBER_GEOMETRY = "remember_geometry";
	protected static final String UPDATE_CATALOG = "update_catalog";
	protected static final String UPDATE_APP = "update_app";
	protected static final String FTL_DATS_PATH = "ftl_dats_path";

	protected SlipstreamConfig appConfig;

	protected FieldEditorPanel editorPanel;
	protected JButton applyBtn;


	public SlipstreamConfigDialog( SlipstreamConfig appConfig ) {
		super( "Preferences..." );
		this.setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE );

		this.appConfig = appConfig;

		editorPanel = new FieldEditorPanel( false );
		editorPanel.setBorder( BorderFactory.createEmptyBorder( 10, 10, 0, 10 ) );
		editorPanel.setNameWidth( 250 );
		editorPanel.addRow( ALLOW_ZIP, ContentType.BOOLEAN );
		editorPanel.addTextRow( "Treat .zip files as .ftl files." );
		editorPanel.addSeparatorRow();
		editorPanel.addRow( NEVER_RUN_FTL, ContentType.BOOLEAN );
		editorPanel.addTextRow( "Don't offer to run FTL after patching." );
		editorPanel.addSeparatorRow();
		editorPanel.addRow( USE_DEFAULT_UI, ContentType.BOOLEAN );
		editorPanel.addTextRow( "Don't attempt to resemble a native GUI." );
		editorPanel.addSeparatorRow();
		editorPanel.addRow( REMEMBER_GEOMETRY, ContentType.BOOLEAN );
		editorPanel.addTextRow( "Save window geometry on exit." );
		editorPanel.addSeparatorRow();
		editorPanel.addRow( UPDATE_CATALOG, ContentType.INTEGER );
		editorPanel.addTextRow( "Check for new mod descriptions every N days. (0 to disable)" );
		editorPanel.addSeparatorRow();
		editorPanel.addRow( UPDATE_APP, ContentType.INTEGER );
		editorPanel.addTextRow( "Check for newer app versions every N days. (0 to disable)" );
		editorPanel.addSeparatorRow();
		editorPanel.addRow( FTL_DATS_PATH, ContentType.CHOOSER );
		editorPanel.addTextRow( "Path to FTL's resources folder." );
		editorPanel.addSeparatorRow();
		editorPanel.addBlankRow();
		editorPanel.addTextRow( "Note: Some changes may have no immediate effect." );
		editorPanel.addBlankRow();
		editorPanel.addFillRow();

		editorPanel.getBoolean( ALLOW_ZIP ).setSelected( appConfig.getProperty( ALLOW_ZIP, "false" ).equals( "true" ) );
		editorPanel.getBoolean( NEVER_RUN_FTL ).setSelected( appConfig.getProperty( NEVER_RUN_FTL, "false" ).equals( "true" ) );
		editorPanel.getBoolean( USE_DEFAULT_UI ).setSelected( appConfig.getProperty( USE_DEFAULT_UI, "false" ).equals( "true" ) );
		editorPanel.getBoolean( REMEMBER_GEOMETRY ).setSelected( appConfig.getProperty( REMEMBER_GEOMETRY, "true" ).equals( "true" ) );
		editorPanel.getInt( UPDATE_CATALOG ).setText( Integer.toString(appConfig.getPropertyAsInt( UPDATE_CATALOG, 0 )) );
		editorPanel.getInt( UPDATE_APP ).setText( Integer.toString(appConfig.getPropertyAsInt( UPDATE_APP, 0 )) );

		JTextField ftlDatsPathField = editorPanel.getChooser( FTL_DATS_PATH ).getTextField();
		ftlDatsPathField.setText( appConfig.getProperty( FTL_DATS_PATH, "" ) );
		ftlDatsPathField.setPreferredSize( new Dimension(150, ftlDatsPathField.getPreferredSize().height) );
		editorPanel.getChooser( FTL_DATS_PATH ).getButton().addActionListener(this);

		JPanel ctrlPanel = new JPanel();
		ctrlPanel.setLayout( new BoxLayout( ctrlPanel, BoxLayout.X_AXIS ) );
		ctrlPanel.setBorder( BorderFactory.createEmptyBorder( 10, 0, 10, 0 ) );
		ctrlPanel.add( Box.createHorizontalGlue() );
		applyBtn = new JButton( "Apply" );
		applyBtn.addActionListener(this);
		ctrlPanel.add( applyBtn );
		ctrlPanel.add( Box.createHorizontalGlue() );

		final JScrollPane editorScroll = new JScrollPane( editorPanel );
		editorScroll.setVerticalScrollBarPolicy( ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS );
		editorScroll.getVerticalScrollBar().setUnitIncrement( 10 );
		int vbarWidth = editorScroll.getVerticalScrollBar().getPreferredSize().width;
		editorScroll.setPreferredSize( new Dimension(editorPanel.getPreferredSize().width+vbarWidth+5, 400) );

		JPanel contentPane = new JPanel( new BorderLayout() );
		contentPane.add( editorScroll, BorderLayout.CENTER );
		contentPane.add( ctrlPanel, BorderLayout.SOUTH );
		this.setContentPane( contentPane );
		this.pack();
		this.setMinimumSize( new Dimension(250, 250) );


		editorScroll.addAncestorListener(new AncestorListener() {
			@Override
			public void ancestorAdded( AncestorEvent e ) {
				editorScroll.getViewport().setViewPosition( new Point(0, 0) );
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
			String tmp;
			appConfig.setProperty( ALLOW_ZIP, editorPanel.getBoolean( ALLOW_ZIP ).isSelected() ? "true" : "false" );
			appConfig.setProperty( NEVER_RUN_FTL, editorPanel.getBoolean( NEVER_RUN_FTL ).isSelected() ? "true" : "false" );
			appConfig.setProperty( USE_DEFAULT_UI, editorPanel.getBoolean( USE_DEFAULT_UI ).isSelected() ? "true" : "false" );
			appConfig.setProperty( REMEMBER_GEOMETRY, editorPanel.getBoolean( REMEMBER_GEOMETRY ).isSelected() ? "true" : "false" );

			tmp = editorPanel.getInt( UPDATE_CATALOG ).getText();
			try {
				int n = Integer.parseInt( tmp );
				n = Math.max( 0, n );
				appConfig.setProperty( UPDATE_CATALOG, Integer.toString( n ) );
			}
			catch ( NumberFormatException f ) {}

			tmp = editorPanel.getInt( UPDATE_APP ).getText();
			try {
				int n = Integer.parseInt( tmp );
				n = Math.max( 0, n );
				appConfig.setProperty( UPDATE_APP, Integer.toString( n ) );
			}
			catch ( NumberFormatException f ) {}

			tmp = editorPanel.getChooser( FTL_DATS_PATH ).getTextField().getText();
			if ( tmp.length() > 0 && FTLUtilities.isDatsDirValid( new File(tmp) ) ) {
				appConfig.setProperty( FTL_DATS_PATH, tmp );
			}

			this.setVisible( false );
			this.dispose();
		}
		else if ( source == editorPanel.getChooser( FTL_DATS_PATH ).getButton() ) {
			File datsDir = FTLUtilities.promptForDatsDir( this );
			if ( datsDir != null ) {
				editorPanel.getChooser( FTL_DATS_PATH ).getTextField().setText( datsDir.getAbsolutePath() );
			}
		}
	}
}
