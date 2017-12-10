package net.vhati.modmanager.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import net.vhati.modmanager.core.AutoUpdateInfo;
import net.vhati.modmanager.core.ComparableVersion;
import net.vhati.modmanager.core.FTLUtilities;
import net.vhati.modmanager.core.ModDB;
import net.vhati.modmanager.core.ModFileInfo;
import net.vhati.modmanager.core.ModInfo;
import net.vhati.modmanager.core.ModPatchThread;
import net.vhati.modmanager.core.ModsScanObserver;
import net.vhati.modmanager.core.ModsScanThread;
import net.vhati.modmanager.core.ModUtilities;
import net.vhati.modmanager.core.Report;
import net.vhati.modmanager.core.Report.ReportFormatter;
import net.vhati.modmanager.core.SlipstreamConfig;
import net.vhati.modmanager.json.JacksonCatalogWriter;
import net.vhati.modmanager.json.URLFetcher;
import net.vhati.modmanager.ui.InertPanel;
import net.vhati.modmanager.ui.ManagerInitThread;
import net.vhati.modmanager.ui.ModInfoArea;
import net.vhati.modmanager.ui.ModPatchDialog;
import net.vhati.modmanager.ui.ModXMLSandbox;
import net.vhati.modmanager.ui.SlipstreamConfigDialog;
import net.vhati.modmanager.ui.Statusbar;
import net.vhati.modmanager.ui.StatusbarMouseListener;
import net.vhati.modmanager.ui.table.ChecklistTablePanel;
import net.vhati.modmanager.ui.table.ListState;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class ManagerFrame extends JFrame implements ActionListener, ModsScanObserver, Nerfable, Statusbar, Thread.UncaughtExceptionHandler {

	private static final Logger log = LogManager.getLogger( ManagerFrame.class );

	public static final String CATALOG_URL = "https://raw.github.com/Vhati/Slipstream-Mod-Manager/master/skel_common/backup/current_catalog.json";
	public static final String APP_UPDATE_URL = "https://raw.github.com/Vhati/Slipstream-Mod-Manager/master/skel_common/backup/auto_update.json";

	private File backupDir = new File( "./backup/" );
	private File modsDir = new File( "./mods/" );

	private File modsTableStateFile = new File( modsDir, "modorder.txt" );

	private File metadataFile = new File( backupDir, "cached_metadata.json" );

	private File catalogFile = new File( backupDir, "current_catalog.json" );
	private File catalogETagFile = new File( backupDir, "current_catalog_etag.txt" );

	private File appUpdateFile = new File( backupDir, "auto_update.json" );
	private File appUpdateETagFile = new File( backupDir, "auto_update_etag.txt" );

	private final Lock managerLock = new ReentrantLock();
	private final Condition scanEndedCond = managerLock.newCondition();
	private boolean scanning = false;

	private SlipstreamConfig appConfig;
	private String appName;
	private ComparableVersion appVersion;
	private String appURL;
	private String appAuthor;

	private HashMap<File,String> modFileHashes = new HashMap<File,String>();
	private HashMap<String,Date> modFileDates = new HashMap<String,Date>();
	private ModDB catalogModDB = new ModDB();
	private ModDB localModDB = new ModDB();

	private AutoUpdateInfo appUpdateInfo = null;
	private Color updateBtnDisabledColor = UIManager.getColor( "Button.foreground" );
	private Color updateBtnEnabledColor = new Color( 0, 124, 0 );

	private NerfListener nerfListener = new NerfListener( this );

	private ChecklistTablePanel<ModFileInfo> modsTablePanel;

	private JMenuBar menubar;
	private JMenu fileMenu;
	private JMenuItem rescanMenuItem;
	private JMenuItem extractDatsMenuItem;
	private JMenuItem sandboxMenuItem;
	private JMenuItem configMenuItem;
	private JMenuItem exitMenuItem;
	private JMenu helpMenu;
	private JMenuItem deleteBackupsMenuItem;
	private JMenuItem steamVerifyCacheMenuItem;
	private JMenuItem aboutMenuItem;

	private JButton patchBtn;
	private JButton toggleAllBtn;
	private JButton validateBtn;
	private JButton modsFolderBtn;
	private JButton updateBtn;
	private JSplitPane splitPane;
	private ModInfoArea infoArea;

	private JLabel statusLbl;


	public ManagerFrame( SlipstreamConfig appConfig, String appName, ComparableVersion appVersion, String appURL, String appAuthor ) {
		super();
		this.appConfig = appConfig;
		this.appName = appName;
		this.appVersion = appVersion;
		this.appURL = appURL;
		this.appAuthor = appAuthor;

		this.setTitle( String.format( "%s v%s", appName, appVersion ) );
		this.setDefaultCloseOperation( JFrame.DO_NOTHING_ON_CLOSE );

		JPanel contentPane = new JPanel( new BorderLayout() );

		JPanel mainPane = new JPanel( new BorderLayout() );
		contentPane.add( mainPane, BorderLayout.CENTER );

		JPanel topPanel = new JPanel( new BorderLayout() );

		modsTablePanel = new ChecklistTablePanel<ModFileInfo>();
		topPanel.add( modsTablePanel, BorderLayout.CENTER );

		JPanel modActionsPanel = new JPanel();
		modActionsPanel.setLayout( new BoxLayout( modActionsPanel, BoxLayout.Y_AXIS ) );
		modActionsPanel.setBorder( BorderFactory.createEmptyBorder( 5,5,5,5 ) );
		Insets actionInsets = new Insets( 5,10,5,10 );

		patchBtn = new JButton( "Patch" );
		patchBtn.setMargin( actionInsets );
		patchBtn.addMouseListener( new StatusbarMouseListener( this, "Incorporate all selected mods into the game. Or revert to vanilla, if none are." ) );
		patchBtn.addActionListener( this );
		modActionsPanel.add( patchBtn );

		toggleAllBtn = new JButton( "Toggle All" );
		toggleAllBtn.setMargin( actionInsets );
		toggleAllBtn.addMouseListener( new StatusbarMouseListener( this, "Select all mods, or none." ) );
		toggleAllBtn.addActionListener( this );
		modActionsPanel.add( toggleAllBtn );

		validateBtn = new JButton( "Validate" );
		validateBtn.setMargin( actionInsets );
		validateBtn.addMouseListener( new StatusbarMouseListener( this, "Check selected mods for problems." ) );
		validateBtn.addActionListener( this );
		modActionsPanel.add( validateBtn );

		modsFolderBtn = new JButton( "Open mods/" );
		modsFolderBtn.setMargin( actionInsets );
		modsFolderBtn.addMouseListener( new StatusbarMouseListener( this, String.format( "Open the %s/ folder.", modsDir.getName() ) ) );
		modsFolderBtn.addActionListener( this );
		modsFolderBtn.setEnabled( Desktop.isDesktopSupported() );
		modActionsPanel.add( modsFolderBtn );

		updateBtn = new JButton( "Update" );
		updateBtn.setMargin( actionInsets );
		updateBtn.addMouseListener( new StatusbarMouseListener( this, String.format( "Show info about the latest version of %s.", appName ) ) );
		updateBtn.addActionListener( this );
		updateBtn.setForeground( updateBtnDisabledColor );
		updateBtn.setEnabled( false );
		modActionsPanel.add( updateBtn );

		topPanel.add( modActionsPanel, BorderLayout.EAST );

		JButton[] actionBtns = new JButton[] {patchBtn, toggleAllBtn, validateBtn, modsFolderBtn, updateBtn };
		int actionBtnWidth = Integer.MIN_VALUE;
		int actionBtnHeight = Integer.MIN_VALUE;
		for ( JButton btn : actionBtns ) {
			actionBtnWidth = Math.max( actionBtnWidth, btn.getPreferredSize().width );
			actionBtnHeight = Math.max( actionBtnHeight, btn.getPreferredSize().height );
		}
		for ( JButton btn : actionBtns ) {
			Dimension size = new Dimension( actionBtnWidth, actionBtnHeight );
			btn.setPreferredSize( size );
			btn.setMinimumSize( size );
			btn.setMaximumSize( size );
		}

		infoArea = new ModInfoArea();
		infoArea.setPreferredSize( new Dimension( 504, 220 ) );
		infoArea.setStatusbar( this );

		splitPane = new JSplitPane( JSplitPane.VERTICAL_SPLIT );
		splitPane.setTopComponent( topPanel );
		splitPane.setBottomComponent( infoArea );
		mainPane.add( splitPane, BorderLayout.CENTER );

		JPanel statusPanel = new JPanel();
		statusPanel.setLayout( new BoxLayout( statusPanel, BoxLayout.Y_AXIS ) );
		statusPanel.setBorder( BorderFactory.createLoweredBevelBorder() );
		statusLbl = new JLabel(" ");
		statusLbl.setBorder( BorderFactory.createEmptyBorder( 2, 4, 2, 4 ) );
		statusLbl.setAlignmentX( Component.LEFT_ALIGNMENT );
		statusPanel.add( statusLbl );
		contentPane.add( statusPanel, BorderLayout.SOUTH );


		this.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing( WindowEvent e ) {
				// The close button was clicked.

				// This is where an "Are you sure?" popup could go.
				ManagerFrame.this.setVisible( false );
				ManagerFrame.this.dispose();

				// The following would also trigger this callback.
				//Window w = ...;
				//w.getToolkit().getSystemEventQueue().postEvent( new WindowEvent(w, WindowEvent.WINDOW_CLOSING) );
			}

			@Override
			public void windowClosed( WindowEvent e ) {
				// dispose() was called.
				ListState<ModFileInfo> tableState = getCurrentModsTableState();
				saveModsTableState( tableState );

				SlipstreamConfig appConfig = ManagerFrame.this.appConfig;

				if ( appConfig.getProperty( SlipstreamConfig.REMEMBER_GEOMETRY ).equals( "true" ) ) {
					if ( ManagerFrame.this.getExtendedState() == JFrame.NORMAL ) {
						Rectangle managerBounds = ManagerFrame.this.getBounds();
						int dividerLoc = splitPane.getDividerLocation();
						String geometry = String.format( "x,%d;y,%d;w,%d;h,%d;divider,%d", managerBounds.x, managerBounds.y, managerBounds.width, managerBounds.height, dividerLoc );
						appConfig.setProperty( SlipstreamConfig.MANAGER_GEOMETRY, geometry );
					}
				}

				try {
					appConfig.writeConfig();
				}
				catch ( IOException f ) {
					log.error( String.format( "Error writing config to \"%s\"", appConfig.getConfigFile().getName() ), f );
				}

				try {
					JacksonCatalogWriter.write( localModDB.getCollatedModInfo(), metadataFile );
				}
				catch ( IOException f ) {
					log.error( String.format( "Error writing metadata from local mods to \"%s\"", metadataFile.getName() ), f );
				}

				System.gc();
				//System.exit( 0 );  // Don't do this (InterruptedException). Let EDT end gracefully.
			}
		});

		// Highlighted row shows mod info.
		modsTablePanel.getTable().getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged( ListSelectionEvent e ) {
				if ( e.getValueIsAdjusting() ) return;

				int row = modsTablePanel.getTable().getSelectedRow();
				if ( row == -1 ) return;

				ModFileInfo modFileInfo = modsTablePanel.getTableModel().getItem( row );
				showLocalModInfo( modFileInfo );
			}
		});

		menubar = new JMenuBar();
		fileMenu = new JMenu( "File" );
		fileMenu.setMnemonic( KeyEvent.VK_F );
		rescanMenuItem = new JMenuItem( "Re-Scan mods/" );
		rescanMenuItem.addMouseListener( new StatusbarMouseListener( this, "Check the mods/ folder for new files." ) );
		rescanMenuItem.addActionListener( this );
		fileMenu.add( rescanMenuItem );
		extractDatsMenuItem = new JMenuItem( "Extract Dats..." );
		extractDatsMenuItem.addMouseListener( new StatusbarMouseListener( this, "Extract FTL resources into a folder." ) );
		extractDatsMenuItem.addActionListener( this );
		fileMenu.add( extractDatsMenuItem );
		sandboxMenuItem = new JMenuItem( "XML Sandbox..." );
		sandboxMenuItem.addMouseListener( new StatusbarMouseListener( this, "Experiment with advanced mod syntax." ) );
		sandboxMenuItem.addActionListener( this );
		fileMenu.add( sandboxMenuItem );
		configMenuItem = new JMenuItem( "Preferences..." );
		configMenuItem.addMouseListener( new StatusbarMouseListener( this, "Edit preferences." ) );
		configMenuItem.addActionListener( this );
		fileMenu.add( configMenuItem );
		fileMenu.add( new JSeparator() );
		exitMenuItem = new JMenuItem( "Exit" );
		exitMenuItem.addMouseListener( new StatusbarMouseListener( this, "Exit this application." ) );
		exitMenuItem.addActionListener( this );
		fileMenu.add( exitMenuItem );
		menubar.add( fileMenu );
		helpMenu = new JMenu( "Help" );
		helpMenu.setMnemonic( KeyEvent.VK_H );
		deleteBackupsMenuItem = new JMenuItem( "Delete Backups" );
		deleteBackupsMenuItem.addMouseListener( new StatusbarMouseListener( this, "Delete backed up resources." ) );
		deleteBackupsMenuItem.addActionListener( this );
		helpMenu.add( deleteBackupsMenuItem );
		steamVerifyCacheMenuItem = new JMenuItem( "Steam: Verify Game Cache" );
		steamVerifyCacheMenuItem.addMouseListener( new StatusbarMouseListener( this, "Tell Steam to 'Verify Game Cache'." ) );
		steamVerifyCacheMenuItem.addActionListener( this );
		helpMenu.add( steamVerifyCacheMenuItem );
		helpMenu.add( new JSeparator() );
		aboutMenuItem = new JMenuItem( "About" );
		aboutMenuItem.addMouseListener( new StatusbarMouseListener( this, "Show info about this application." ) );
		aboutMenuItem.addActionListener( this );
		helpMenu.add( aboutMenuItem );
		menubar.add( helpMenu );
		this.setJMenuBar( menubar );

		this.setGlassPane( new InertPanel() );

		this.setContentPane( contentPane );
		this.pack();
		this.setMinimumSize( new Dimension( 300, modActionsPanel.getPreferredSize().height+90 ) );
		this.setLocationRelativeTo( null );

		if ( appConfig.getProperty( SlipstreamConfig.REMEMBER_GEOMETRY ).equals( "true" ) )
			setGeometryFromConfig();

		showAboutInfo();
  }

	private void setGeometryFromConfig() {
		String geometry = appConfig.getProperty( SlipstreamConfig.MANAGER_GEOMETRY );
		if ( geometry != null ) {
			int[] xywh = new int[4];
			int dividerLoc = -1;
			Matcher m = Pattern.compile( "([^;,]+),(\\d+)" ).matcher( geometry );
			while ( m.find() ) {
				if ( m.group( 1 ).equals( "x" ) )
					xywh[0] = Integer.parseInt( m.group( 2 ) );
				else if ( m.group( 1 ).equals( "y" ) )
					xywh[1] = Integer.parseInt( m.group( 2 ) );
				else if ( m.group( 1 ).equals( "w" ) )
					xywh[2] = Integer.parseInt( m.group( 2 ) );
				else if ( m.group( 1 ).equals( "h" ) )
					xywh[3] = Integer.parseInt( m.group( 2 ) );
				else if ( m.group( 1 ).equals( "divider" ) )
					dividerLoc = Integer.parseInt( m.group( 2 ) );
			}
			boolean badGeometry = false;
			for ( int n : xywh ) {
				if ( n <= 0 ) {
					badGeometry = true;
					break;
				}
			}
			if ( !badGeometry && dividerLoc > 0 ) {
				Rectangle newBounds = new Rectangle( xywh[0], xywh[1], xywh[2], xywh[3] );
				ManagerFrame.this.setBounds( newBounds );
				splitPane.setDividerLocation( dividerLoc );
			}
		}
	}

	/**
	 * Extra initialization that must be called after the constructor.
	 * This must be called on the Swing event thread (use invokeLater()).
	 */
	public void init() {

		ManagerInitThread initThread = new ManagerInitThread( this,
		                                                      new SlipstreamConfig( appConfig ),
		                                                      modsDir,
		                                                      modsTableStateFile,
		                                                      metadataFile,
		                                                      catalogFile,
		                                                      catalogETagFile,
		                                                      appUpdateFile,
		                                                      appUpdateETagFile
		                                                    );
		initThread.setDaemon( true );
		initThread.setPriority( Thread.MIN_PRIORITY );
		initThread.setDefaultUncaughtExceptionHandler( this );
		initThread.start();
	}


	/**
	 * Returns a ListState describing content in the mods table.
	 */
	public ListState<ModFileInfo> getCurrentModsTableState() {
		ListState<ModFileInfo> tableState = new ListState<ModFileInfo>();

		for ( ModFileInfo modFileInfo : modsTablePanel.getAllItems() ) {
			tableState.addItem( modFileInfo );
		}

		return tableState;
	}

	/**
	 * Synchronizes a mods table state with a pool of available items.
	 *
	 * Items in the table that also are in the pool are unchanged.
	 * Items in the table that aren't in the pool are pruned.
	 * Items in the pool that weren't in the table are appended in ascending
	 * order.
	 *
	 * @param tableState  an existing state to amend
	 * @param unsortedMods  the pool of currently available local mods
	 */
	public void amendModsTableState( ListState<ModFileInfo> tableState, List<ModFileInfo> unsortedMods ) {
		List<ModFileInfo> availableMods = new ArrayList<ModFileInfo>( unsortedMods );
		Collections.sort( availableMods );

		for ( ModFileInfo modFileInfo : availableMods ) {
			if ( !tableState.containsItem( modFileInfo ) ) {
				tableState.addItem( modFileInfo );
			}
		}
		for ( ModFileInfo modFileInfo : tableState.getItems() ) {
			if ( !availableMods.contains( modFileInfo ) ) {
				tableState.removeItem( modFileInfo );
			}
		}
	}


	private void saveModsTableState( ListState<ModFileInfo> tableState ) {
		BufferedWriter bw = null;
		try {
			FileOutputStream os = new FileOutputStream( modsTableStateFile );
			bw = new BufferedWriter(new OutputStreamWriter( os, Charset.forName( "UTF-8" ) ));

			for ( ModFileInfo modFileInfo : tableState.getItems() ) {
				bw.write( modFileInfo.getFile().getName() );
				bw.write( "\r\n" );
			}
			bw.flush();
		}
		catch ( IOException e ) {
			log.error( String.format( "Error writing \"%s\"", modsTableStateFile.getName() ), e );
		}
		finally {
			try {if ( bw != null ) bw.close();}
			catch (Exception e) {}
		}
	}


	/**
	 * Clears and syncs the mods list with mods/ dir, then starts a new hash
	 * thread.
	 */
	public void rescanMods( ListState<ModFileInfo> tableState ) {
		managerLock.lock();
		try {
			if ( scanning ) return;
			scanning = true;
			rescanMenuItem.setEnabled( !scanning );
		}
		finally {
			managerLock.unlock();
		}

		modFileHashes.clear();
		modsTablePanel.clear();

		boolean allowZip = appConfig.getProperty( SlipstreamConfig.ALLOW_ZIP, "false" ).equals( "true" );
		File[] modFiles = modsDir.listFiles( new ModFileFilter( allowZip ) );

		List<ModFileInfo> unsortedMods = new ArrayList<ModFileInfo>();
		for ( File f : modFiles ) {
			ModFileInfo modFileInfo = new ModFileInfo( f );
			unsortedMods.add( modFileInfo );
		}
		amendModsTableState( tableState, unsortedMods );

		for ( ModFileInfo modFileInfo : tableState.getItems() ) {
			modsTablePanel.getTableModel().addItem( modFileInfo );
		}

		ModsScanThread scanThread = new ModsScanThread( modFiles, localModDB, this );
		scanThread.setDaemon( true );
		scanThread.setPriority( Thread.MIN_PRIORITY );
		scanThread.setDefaultUncaughtExceptionHandler( this );
		scanThread.start();
	}


	public void showAboutInfo() {
		String body = "";
		body += "- Drag to reorder mods.\n";
		body += "- Click the checkboxes to select.\n";
		body += "- Click 'Patch' to apply mods ( select none for vanilla ).\n";
		body += "\n";
		body += "Thanks for using this mod manager.\n";
		body += "Make sure to visit the forum for updates!";

		infoArea.setDescription( appName, appAuthor, appVersion.toString(), appURL, body );
	}

	public void showAppUpdateInfo() {
		StringBuilder buf = new StringBuilder();

		try {
			infoArea.clear();
			infoArea.appendTitleText( "What's New\n" );

			// Links.
			infoArea.appendRegularText( String.format( "Version %s: ", appUpdateInfo.getLatestVersion().toString() ) );
			boolean first = true;
			for ( Map.Entry<String,String> entry : appUpdateInfo.getLatestURLs().entrySet() ) {
				if ( !first ) infoArea.appendRegularText( " " );
				infoArea.appendRegularText( "[" );
				infoArea.appendLinkText( entry.getValue(), entry.getKey() );
				infoArea.appendRegularText( "]" );
				first = false;
			}
			infoArea.appendRegularText( "\n" );
			infoArea.appendRegularText( "\n" );

			// Notice.
			if ( appUpdateInfo.getNotice() != null && appUpdateInfo.getNotice().length() > 0 ) {
				infoArea.appendRegularText( appUpdateInfo.getNotice() );
				infoArea.appendRegularText( "\n" );
				infoArea.appendRegularText( "\n" );
			}

			// Changelog.
			for ( Map.Entry<ComparableVersion,List<String>> entry : appUpdateInfo.getChangelog().entrySet() ) {
				if ( appVersion.compareTo( entry.getKey() ) >= 0 ) break;

				if ( buf.length() > 0 ) buf.append( "\n" );
				buf.append( entry.getKey() ).append( ":\n" );

				for ( String change : entry.getValue() ) {
					buf.append( "  - " ).append( change ).append( "\n" );
				}
			}
			infoArea.appendRegularText( buf.toString() );

			infoArea.setCaretPosition( 0 );
		}
		catch ( Exception e ) {
			log.error( "Error filling info text area", e );
		}
	}

	/**
	 * Shows info about a local mod in the text area.
	 *
	 * Priority is given to embedded metadata.xml, but when that's absent,
	 * the gatalog's info is used. If the catalog doesn't have the info,
	 * an 'info missing' notice is shown instead.
	 */
	public void showLocalModInfo( ModFileInfo modFileInfo ) {
		String modHash = modFileHashes.get( modFileInfo.getFile() );

		ModInfo modInfo = localModDB.getModInfo( modHash );
		if ( modInfo == null || modInfo.isBlank() ) {
			modInfo = catalogModDB.getModInfo( modHash );
		}

		if ( modInfo != null ) {
			infoArea.setDescription( modInfo.getTitle(), modInfo.getAuthor(), modInfo.getVersion(), modInfo.getURL(), modInfo.getDescription() );
		}
		else {
			boolean notYetReady = isScanning();

			if ( notYetReady ) {
				String body = "";
				body += "No info is currently available for the selected mod.\n\n";
				body += "But Slipstream has not yet finished scanning the mods/ folder. ";
				body += "Try clicking this mod again after waiting a few seconds.";

				infoArea.setDescription( modFileInfo.getName(), body );
			}
			else {
				Date modDate = modFileDates.get( modHash );
				if ( modDate == null ) {
					long epochTime = -1;
					try {
						epochTime = ModUtilities.getModFileTime( modFileInfo.getFile() );
					}
					catch ( IOException e ) {
						log.error( String.format( "Error while getting modified time of mod file contents for \"%s\"", modFileInfo.getFile() ), e );
					}
					if ( epochTime != -1 ) {
						modDate = new Date( epochTime );
						modFileDates.put( modHash, modDate );
					}
				}

				String body = "";
				body += "No info is available for the selected mod.\n\n";

				if ( modDate != null ) {
					SimpleDateFormat dateFormat = new SimpleDateFormat( "yyyy-MM-dd" );
					String dateString = dateFormat.format( modDate );
					body += "It was released some time after "+ dateString +".\n\n";
				} else {
					body += "The date of its release could not be determined.\n\n";
				}

				body += "If it is stable and has been out for over a month,\n";
				body += "please let the Slipstream devs know where you ";
				body += "found it.\n\n";
				body += "Include the mod's version, and this hash.\n";
				body += "MD5: "+ modHash +"\n";
				infoArea.setDescription( modFileInfo.getName(), body );
			}
		}
	}

	public void exitApp() {
		this.setVisible( false );
		this.dispose();
	}


	@Override
	public void setStatusText( String text ) {
		if ( text.length() > 0 )
			statusLbl.setText( text );
		else
			statusLbl.setText( " " );
	}


	@Override
	public void actionPerformed( ActionEvent e ) {
		Object source = e.getSource();

		if ( source == patchBtn ) {
			List<File> modFiles = new ArrayList<File>();

			for ( ModFileInfo modFileInfo : modsTablePanel.getSelectedItems() ) {
				modFiles.add( modFileInfo.getFile() );
			}

			File datsDir = new File( appConfig.getProperty( SlipstreamConfig.FTL_DATS_PATH ) );

			ModPatchDialog patchDlg = new ModPatchDialog( this, true );

			// Offer to run FTL.
			if ( !"true".equals( appConfig.getProperty( SlipstreamConfig.NEVER_RUN_FTL, "false" ) ) ) {
				File exeFile = null;
				String[] exeArgs = null;

				// Try to run via Steam.
				if ( "true".equals( appConfig.getProperty( SlipstreamConfig.RUN_STEAM_FTL, "false" ) ) ) {

					String steamPath = appConfig.getProperty( SlipstreamConfig.STEAM_EXE_PATH );
					if ( steamPath.length() > 0 ) {
						exeFile = new File( steamPath );

						if ( exeFile.exists() ) {
							exeArgs = new String[] {"-applaunch", FTLUtilities.STEAM_APPID_FTL};
						}
						else {
							log.warn( String.format( "%s does not exist: %s", SlipstreamConfig.STEAM_EXE_PATH, exeFile.getAbsolutePath() ) );
							exeFile = null;
						}
					}

					if ( exeFile == null ) {
						log.warn( "Steam executable could not be found, so FTL will be launched directly" );
					}
				}
				// Try to run directly.
				if ( exeFile == null ) {
					exeFile = FTLUtilities.findGameExe( datsDir );

					if ( exeFile != null ) {
						exeArgs = new String[0];
					} else {
						log.warn( "FTL executable could not be found" );
					}
				}

				if ( exeFile != null ) {
					patchDlg.setSuccessTask( new SpawnGameTask( exeFile, exeArgs ) );
				}
			}

			log.info( "" );
			log.info( "Patching..." );
			log.info( "" );
			ModPatchThread patchThread = new ModPatchThread( modFiles, datsDir, backupDir, false, patchDlg );
			patchThread.setDefaultUncaughtExceptionHandler( this );
			patchThread.start();

			patchDlg.setVisible( true );
		}
		else if ( source == toggleAllBtn ) {
			modsTablePanel.toggleAllItemSelection();
		}
		else if ( source == validateBtn ) {
			StringBuilder resultBuf = new StringBuilder();
			boolean anyInvalid = false;

			for ( ModFileInfo modFileInfo : modsTablePanel.getSelectedItems() ) {
				Report validateReport = ModUtilities.validateModFile( modFileInfo.getFile() );

				ReportFormatter formatter = new ReportFormatter();
				formatter.format( validateReport.messages, resultBuf, 0 );
				resultBuf.append( "\n" );

				if ( validateReport.outcome == false ) anyInvalid = true;
			}

			if ( resultBuf.length() == 0 ) {
				resultBuf.append( "No mods were checked." );
			}
			else if ( anyInvalid ) {
				resultBuf.append( "\n" );
				resultBuf.append( "FTL itself can tolerate lots of XML typos and still run. " );
				resultBuf.append( "But malformed XML may break tools that do proper parsing, " );
				resultBuf.append( "and it hinders the development of new tools.\n" );
				resultBuf.append( "\n" );
				resultBuf.append( "Since v1.2, Slipstream will try to parse XML while patching: " );
				resultBuf.append( "first strictly, then failing over to a sloppy parser. " );
				resultBuf.append( "The sloppy parser will tolerate similar errors, at the risk " );
				resultBuf.append( "of unforseen behavior, so satisfying the strict parser " );
				resultBuf.append( "is advised.\n" );
			}
			infoArea.setDescription( "Results", resultBuf.toString() );
		}
		else if ( source == modsFolderBtn ) {
			try {
				if ( Desktop.isDesktopSupported() ) {
					Desktop.getDesktop().open( modsDir.getCanonicalFile() );
				} else {
					log.error( String.format( "Java cannot open the %s/ folder for you on this OS", modsDir.getName() ) );
				}
			}
			catch ( IOException f ) {
				log.error( "Error opening mods/ folder", f );
			}
		}
		else if ( source == updateBtn ) {
			showAppUpdateInfo();
		}
		else if ( source == rescanMenuItem ) {
			setStatusText( "" );
			if ( rescanMenuItem.isEnabled() == false ) return;

			ListState<ModFileInfo> tableState = getCurrentModsTableState();
			rescanMods( tableState );
		}
		else if ( source == extractDatsMenuItem ) {
			setStatusText( "" );
			JFileChooser extractChooser = new JFileChooser();
			extractChooser.setDialogTitle( "Choose a dir to extract into" );
			extractChooser.setFileHidingEnabled( false );
			extractChooser.setFileSelectionMode( JFileChooser.DIRECTORIES_ONLY );
			extractChooser.setMultiSelectionEnabled( false );

			if ( extractChooser.showSaveDialog( this ) != JFileChooser.APPROVE_OPTION )
				return;

			File extractDir = extractChooser.getSelectedFile();

			File datsDir = new File( appConfig.getProperty( SlipstreamConfig.FTL_DATS_PATH ) );

			DatExtractDialog extractDlg = new DatExtractDialog( this, extractDir, datsDir );
			extractDlg.getWorkerThread().setDefaultUncaughtExceptionHandler( this );
			extractDlg.extract();
			extractDlg.setVisible( true );
		}
		else if ( source == sandboxMenuItem ) {
			setStatusText( "" );
			File datsDir = new File( appConfig.getProperty( SlipstreamConfig.FTL_DATS_PATH ) );

			ModXMLSandbox sandboxFrame = new ModXMLSandbox( datsDir );
			sandboxFrame.addWindowListener( nerfListener );
			sandboxFrame.setSize( 800, 600 );
			sandboxFrame.setLocationRelativeTo( null );
			sandboxFrame.setVisible( true );
		}
		else if ( source == configMenuItem ) {
			setStatusText( "" );

			SlipstreamConfigDialog configFrame = new SlipstreamConfigDialog( appConfig );
			configFrame.addWindowListener( nerfListener );
			//configFrame.setSize( 300, 400 );
			configFrame.setLocationRelativeTo( null );
			configFrame.setVisible( true );
		}
		else if ( source == exitMenuItem ) {
			setStatusText( "" );
			exitApp();
		}
		else if ( source == deleteBackupsMenuItem ) {
			StringBuilder deleteBuf = new StringBuilder();
			deleteBuf.append( "Slipstream uses backups to revert FTL to a state without mods.\n" );
			deleteBuf.append( "You are about to delete them.\n" );
			deleteBuf.append( "\n" );
			deleteBuf.append( "The next time you click 'patch', Slipstream will create fresh backups.\n" );
			deleteBuf.append( "\n" );
			deleteBuf.append( "FTL *must be* in a working unmodded state *before* you click 'patch'.\n" );
			deleteBuf.append( "\n" );
			deleteBuf.append( "To get FTL into a working unmodded state, you may need to reinstall FTL\n" );
			deleteBuf.append( "or use Steam's \"Verify Game Cache\" feature.\n" );
			deleteBuf.append( "\n" );
			deleteBuf.append( "Whenever FTL is updated, you will need to delete stale backups or the\n" );
			deleteBuf.append( "game will break.\n" );
			deleteBuf.append( "\n" );
			deleteBuf.append( "Are you sure you want to continue?" );

			int response = JOptionPane.showConfirmDialog( ManagerFrame.this, deleteBuf.toString(), "Continue?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE );
			if ( response == JOptionPane.YES_OPTION ) {

				List<String> failures = new ArrayList<String>( 2 );
				boolean backupsExist = false;
				for ( String datName : new String[] {"ftl.dat", "data.dat", "resource.dat"} ) {
					File bakFile = new File( backupDir, datName +".bak" );
					if ( bakFile.exists() ) {
						backupsExist = true;

						if ( !bakFile.delete() ) {
							log.error( "Unable to delete backup: "+ bakFile.getName() );
							failures.add( bakFile.getName() );
						}
					}
				}
				if ( !backupsExist ) {
					JOptionPane.showMessageDialog( ManagerFrame.this, "There were no backups to delete.", "Nothing to do", JOptionPane.INFORMATION_MESSAGE );
				}
				else if ( failures.isEmpty() ) {
					JOptionPane.showMessageDialog( ManagerFrame.this, "Backups were deleted successfully.", "Success", JOptionPane.INFORMATION_MESSAGE );
				}
				else {
					StringBuilder failBuf = new StringBuilder( "The following files couldn't be deleted:" );
					for ( String s : failures ) {
						failBuf.append( "- \"" ).append( s ).append( "\"\n" );
					}
					failBuf.append( "\nTry going in the \"SMM/backup/\" folder and deleting them manually?" );
					JOptionPane.showMessageDialog( ManagerFrame.this, failBuf.toString(), "Error", JOptionPane.ERROR_MESSAGE );

					try {
						if ( Desktop.isDesktopSupported() ) {
							Desktop.getDesktop().open( backupDir.getCanonicalFile() );
						} else {
							log.error( String.format( "Java cannot open the %s/ folder for you on this OS", backupDir.getName() ) );
						}
					}
					catch ( IOException f ) {
						log.error( "Error opening mods/ folder", f );
					}
				}
			}
		}
		else if ( source == steamVerifyCacheMenuItem ) {
			StringBuilder verifyBuf = new StringBuilder();
			verifyBuf.append( "Slipstream is about to tell Steam to re-download FTL's resources. This will get\n" );
			verifyBuf.append( "the game back to a working unmodded state, but it could take a while.\n" );
			verifyBuf.append( "\n" );
			verifyBuf.append( "You can do it manually like this...\n" );
			verifyBuf.append( "- Go to Steam's Library.\n" );
			verifyBuf.append( "- Right-click FTL, choose \"Properties\".\n" );
			verifyBuf.append( "- Click the \"Verify integrity of game files...\" button.\n" );
			verifyBuf.append( "\n" );
			verifyBuf.append( "If you do not have Steam, you will need to reinstall FTL instead.\n" );
			verifyBuf.append( "\n" );
			verifyBuf.append( "Either way, you should delete Slipstream's backups as well.\n" );
			verifyBuf.append( "\n" );
			verifyBuf.append( "Are you sure you want to continue?" );

			int response = JOptionPane.showConfirmDialog( ManagerFrame.this, verifyBuf.toString(), "Continue?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE );
			if ( response == JOptionPane.YES_OPTION ) {
				try {
					String exePath = appConfig.getProperty( SlipstreamConfig.STEAM_EXE_PATH, "" );
					File exeFile = null;
					if ( exePath.length() > 0 && (exeFile=new File( exePath )).exists() ) {
						FTLUtilities.verifySteamGameCache( exeFile, FTLUtilities.STEAM_APPID_FTL );
					} else {
						log.warn( "Steam executable could not be found" );
					}
				}
				catch ( IOException f ) {
					log.error( "Couldn't tell Steam to 'verify game cache'", f );
				}
			}

		}
		else if ( source == aboutMenuItem ) {
			setStatusText( "" );
			showAboutInfo();
		}
	}


	@Override
	public void hashCalculated( final File f, final String hash ) {
		Runnable r = new Runnable() {
			@Override
			public void run() { modFileHashes.put( f, hash ); }
		};
		if ( SwingUtilities.isEventDispatchThread() ) r.run();
		else SwingUtilities.invokeLater( r );
	}

	@Override
	public void localModDBUpdated( ModDB newDB ) {
		setLocalModDB( newDB );
	}

	@Override
	public void modsScanEnded() {
		Runnable r = new Runnable() {
			@Override
			public void run() {
				managerLock.lock();
				try {
					scanning = false;
					rescanMenuItem.setEnabled( !scanning );
					scanEndedCond.signalAll();
				}
				finally {
					managerLock.unlock();
				}
			}
		};
		if ( SwingUtilities.isEventDispatchThread() ) r.run();
		else SwingUtilities.invokeLater( r );
	}


	/**
	 * Returns a lock for synchronizing thread operations.
	 */
	public Lock getLock() {
		return managerLock;
	}

	/**
	 * Returns a condition that will signal when the "mods/" dir has been scanned.
	 *
	 * Call getLock().lock() first.
	 * Loop while isScanning() is true, calling this condition's await().
	 * Finally, call getLock().unlock().
	 */
	public Condition getScanEndedCondition() {
		return scanEndedCond;
	}

	/**
	 * Returns true if the "mods/" folder is currently being scanned. (thread-safe)
	 */
	public boolean isScanning() {
		managerLock.lock();
		try {
			return scanning;
		}
		finally {
			managerLock.unlock();
		}
	}


	@Override
	public void setNerfed( boolean b ) {
		Component glassPane = this.getGlassPane();
		if (b) {
			glassPane.setVisible( true );
			glassPane.requestFocusInWindow();
		} else {
			glassPane.setVisible( false );
		}
	}


	/**
	 * Sets the ModDB for local metadata. (thread-safe)
	 */
	public void setLocalModDB( final ModDB newDB ) {
		Runnable r = new Runnable() {
			@Override
			public void run() { localModDB = newDB; }
		};
		if ( SwingUtilities.isEventDispatchThread() ) r.run();
		else SwingUtilities.invokeLater( r );
	}

	/**
	 * Sets the ModDB for the catalog. (thread-safe)
	 */
	public void setCatalogModDB( final ModDB newDB ) {
		Runnable r = new Runnable() {
			@Override
			public void run() { catalogModDB = newDB; }
		};
		if ( SwingUtilities.isEventDispatchThread() ) r.run();
		else SwingUtilities.invokeLater( r );
	}

	/**
	 * Sets info about available app updates. (thread-safe)
	 */
	public void setAppUpdateInfo( final AutoUpdateInfo aui ) {
		Runnable r = new Runnable() {
			@Override
			public void run() {
				appUpdateInfo = aui;
				boolean isUpdateAvailable = ( appVersion.compareTo(appUpdateInfo.getLatestVersion()) < 0 );
				updateBtn.setForeground( isUpdateAvailable ? updateBtnEnabledColor : updateBtnDisabledColor );
				updateBtn.setEnabled( isUpdateAvailable );
			}
		};
		if ( SwingUtilities.isEventDispatchThread() ) r.run();
		else SwingUtilities.invokeLater( r );
	}


	@Override
	public void uncaughtException( Thread t, Throwable e ) {
		log.error( "Uncaught exception in thread: "+ t.toString(), e );

		final String threadString = t.toString();
		final String errString = e.toString();
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				String message = "An unexpected error has occurred.\n";
				message += "\n";
				message += "Thread: "+ threadString +"\n";
				message += "Error: "+ errString +"\n";
				message += "\n";
				message += "See the log for details.\n";
				message += "\n";
				message += "If this interrupted patching, FTL's resources were probably corrupted.\n";
				message += "Restart Slipstream and patch without mods to restore vanilla backups.";

				JOptionPane.showMessageDialog( ManagerFrame.this, message, "Error", JOptionPane.ERROR_MESSAGE );
			}
		});
	}



	private class SpawnGameTask implements Runnable {
		private final File exeFile;
		private final String[] exeArgs;

		public SpawnGameTask( File exeFile, String... exeArgs ) {
			if ( exeArgs == null ) exeArgs = new String[0];
			this.exeFile = exeFile;
			this.exeArgs = new String[exeArgs.length];
			System.arraycopy( exeArgs, 0, this.exeArgs, 0, exeArgs.length );
		}

		@Override
		public void run() {
			if ( exeFile != null ) {
				int response = JOptionPane.showConfirmDialog( ManagerFrame.this, "Do you want to run the game now?", "Ready to Play", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE );
				if ( response == JOptionPane.YES_OPTION ) {
					log.info( "Running FTL..." );
					try {
						FTLUtilities.launchExe( exeFile, exeArgs );
					}
					catch ( Exception e ) {
						log.error( "Error launching FTL", e );
					}
					exitApp();
				}
			}
		}
	}



	/**
	 * Toggles a main window's nerfed state as popups are opened/disposed.
	 *
	 * Requires: setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE ).
	 */
	private static class NerfListener extends WindowAdapter {
		private Nerfable nerfObj;

		public NerfListener( Nerfable nerfObj ) {
			this.nerfObj = nerfObj;
		}

		@Override
		public void windowOpened( WindowEvent e ) {
			nerfObj.setNerfed( true );
		}
		@Override
		public void windowClosed( WindowEvent e ) {
			nerfObj.setNerfed( false );
		}
	}



	private static class ModFileFilter implements FileFilter {
		boolean allowZip;

		public ModFileFilter( boolean allowZip ) {
			this.allowZip = allowZip;
		}

		@Override
		public boolean accept( File f ) {
			if ( f.isFile() ) {
				if ( f.getName().endsWith( ".ftl" ) ) return true;

				if ( allowZip ) {
					if ( f.getName().endsWith( ".zip" ) ) return true;
				}
			}
			return false;
		}
	}
}
