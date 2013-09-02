package net.vhati.modmanager.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import java.util.Properties;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;

import net.vhati.ftldat.FTLDat;
import net.vhati.modmanager.core.ComparableVersion;
import net.vhati.modmanager.core.FTLUtilities;
import net.vhati.modmanager.core.HashObserver;
import net.vhati.modmanager.core.HashThread;
import net.vhati.modmanager.core.ModDB;
import net.vhati.modmanager.core.ModFileInfo;
import net.vhati.modmanager.core.ModInfo;
import net.vhati.modmanager.core.ModPatchThread;
import net.vhati.modmanager.core.ModPatchThread.BackedUpDat;
import net.vhati.modmanager.core.ModUtilities;
import net.vhati.modmanager.core.Report;
import net.vhati.modmanager.core.Report.ReportFormatter;
import net.vhati.modmanager.json.GrognakCatalogFetcher;
import net.vhati.modmanager.json.JacksonGrognakCatalogReader;
import net.vhati.modmanager.ui.ChecklistTableModel;
import net.vhati.modmanager.ui.ModInfoArea;
import net.vhati.modmanager.ui.ModPatchDialog;
import net.vhati.modmanager.ui.ModXMLSandbox;
import net.vhati.modmanager.ui.Statusbar;
import net.vhati.modmanager.ui.StatusbarMouseListener;
import net.vhati.modmanager.ui.TableRowTransferHandler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class ManagerFrame extends JFrame implements ActionListener, HashObserver, Statusbar {

	private static final Logger log = LogManager.getLogger(ManagerFrame.class);

	private File backupDir = new File( "./backup/" );
	private File modsDir = new File( "./mods/" );

	private int catalogFetchInterval = 7;  // Days.
	private File catalogFile = new File( backupDir, "current_catalog.json" );
	private File catalogETagFile = new File( backupDir, "current_catalog_etag.txt" );

	private Properties config;
	private String appName;
	private ComparableVersion appVersion;
	private String appURL;
	private String appAuthor;

	private HashMap<File,String> modFileHashes = new HashMap<File,String>();
	private ModDB modDB = new ModDB();

	private ChecklistTableModel<ModFileInfo> localModsTableModel;
	private JTable localModsTable;

	private JMenuBar menubar;
	private JMenu fileMenu;
	private JMenuItem rescanMenuItem;
	private JMenuItem extractDatsMenuItem;
	private JMenuItem sandboxMenuItem;
	private JMenuItem exitMenuItem;
	private JMenu helpMenu;
	private JMenuItem aboutMenuItem;

	private JButton patchBtn;
	private JButton toggleAllBtn;
	private JButton validateBtn;
	private JButton modsFolderBtn;

	private ModInfoArea infoArea;

	private JLabel statusLbl;


	public ManagerFrame( Properties config, String appName, ComparableVersion appVersion, String appURL, String appAuthor ) {
		super();
		this.config = config;
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

		localModsTableModel = new ChecklistTableModel<ModFileInfo>();

		localModsTable = new JTable( localModsTableModel );
		localModsTable.setFillsViewportHeight( true );
		localModsTable.setSelectionMode( ListSelectionModel.SINGLE_SELECTION );
		localModsTable.setTableHeader( null );
		localModsTable.getColumnModel().getColumn(0).setMinWidth(30);
		localModsTable.getColumnModel().getColumn(0).setMaxWidth(30);
		localModsTable.getColumnModel().getColumn(0).setPreferredWidth(30);

		JScrollPane localModsScroll = new JScrollPane( null, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER );
		localModsScroll.setViewportView( localModsTable );
		//localModsScroll.setColumnHeaderView( null );  // Counterpart to setTableHeader().
		localModsScroll.setPreferredSize( new Dimension(Integer.MIN_VALUE, Integer.MIN_VALUE) );
		topPanel.add( localModsScroll, BorderLayout.CENTER );

		JPanel modActionsPanel = new JPanel();
		modActionsPanel.setLayout( new BoxLayout(modActionsPanel, BoxLayout.Y_AXIS) );
		modActionsPanel.setBorder( BorderFactory.createEmptyBorder(5,5,5,5) );
		Insets actionInsets = new Insets(5,10,5,10);

		patchBtn = new JButton("Patch");
		patchBtn.setMargin( actionInsets );
		patchBtn.addMouseListener( new StatusbarMouseListener( this, "Incorporate all selected mods into the game." ) );
		patchBtn.addActionListener(this);
		modActionsPanel.add( patchBtn );

		toggleAllBtn = new JButton("Toggle All");
		toggleAllBtn.setMargin( actionInsets );
		toggleAllBtn.addMouseListener( new StatusbarMouseListener( this, "Select all mods, or none." ) );
		toggleAllBtn.addActionListener(this);
		modActionsPanel.add( toggleAllBtn );

		validateBtn = new JButton("Validate");
		validateBtn.setMargin( actionInsets );
		validateBtn.addMouseListener( new StatusbarMouseListener( this, "Check selected mods for problems." ) );
		validateBtn.addActionListener(this);
		modActionsPanel.add( validateBtn );

		modsFolderBtn = new JButton("Open mods/");
		modsFolderBtn.setMargin( actionInsets );
		modsFolderBtn.addMouseListener( new StatusbarMouseListener( this, "Open the mods/ folder." ) );
		modsFolderBtn.addActionListener(this);
		modsFolderBtn.setEnabled( Desktop.isDesktopSupported() );
		modActionsPanel.add( modsFolderBtn );

		topPanel.add( modActionsPanel, BorderLayout.EAST );

		JButton[] actionBtns = new JButton[] {patchBtn, toggleAllBtn, validateBtn, modsFolderBtn };
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
		infoArea.setPreferredSize( new Dimension(504, 220) );

		JSplitPane splitPane = new JSplitPane( JSplitPane.VERTICAL_SPLIT );
		splitPane.setTopComponent( topPanel );
		splitPane.setBottomComponent( infoArea );
		mainPane.add( splitPane, BorderLayout.CENTER );

		JPanel statusPanel = new JPanel();
		statusPanel.setLayout( new BoxLayout(statusPanel, BoxLayout.Y_AXIS) );
		statusPanel.setBorder( BorderFactory.createLoweredBevelBorder() );
		statusLbl = new JLabel(" ");
		statusLbl.setBorder( BorderFactory.createEmptyBorder(2, 4, 2, 4) );
		statusLbl.setAlignmentX( Component.LEFT_ALIGNMENT );
		statusPanel.add( statusLbl );
		contentPane.add( statusPanel, BorderLayout.SOUTH );


		this.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing( WindowEvent e ) {
				List<ModFileInfo> sortedMods = new ArrayList<ModFileInfo>();

				for ( int i=0; i < localModsTableModel.getRowCount(); i++ ) {
					sortedMods.add( localModsTableModel.getItem(i) );
				}
				saveModOrder( sortedMods );

				System.exit( 0 );
			}
		});

		// Double-click toggles checkboxes.
		localModsTable.addMouseListener(new MouseAdapter() {
			int prevRow = -1;
			int streak = 0;

			@Override
			public void mouseClicked( MouseEvent e ) {
				if ( e.getSource() != localModsTable ) return;
				int thisRow = localModsTable.rowAtPoint( e.getPoint() );

				// Reset on first click and when no longer on that row.
				if ( e.getClickCount() == 1 ) prevRow = -1;
				if ( thisRow != prevRow ) {
					streak = 1;
					prevRow = thisRow;
					return;
				} else {
					streak++;
				}
				if ( streak % 2 != 0 ) return;  // Respond to click pairs.

				// Don't further toggle a multi-clicked checkbox.
				int viewCol = localModsTable.columnAtPoint( e.getPoint() );
				int modelCol = localModsTable.getColumnModel().getColumn(viewCol).getModelIndex();
				if ( modelCol == 0 ) return;

				int row = localModsTable.getSelectedRow();
				if ( row != -1 ) {
					boolean selected = localModsTableModel.isSelected( row );
					localModsTableModel.setSelected( row, !selected );
				}
			}
		});

		// Highlighted row shows mod info.
		localModsTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged( ListSelectionEvent e ) {
				if ( e.getValueIsAdjusting() ) return;

				int row = localModsTable.getSelectedRow();
				if ( row == -1 ) return;

				ModFileInfo modFileInfo = localModsTableModel.getItem( row );
				showLocalModInfo( modFileInfo );
			}
		});

		localModsTable.setTransferHandler( new TableRowTransferHandler( localModsTable ) );
		localModsTable.setDropMode( DropMode.INSERT );  // Drop between rows, not on them.
		localModsTable.setDragEnabled( true );

		menubar = new JMenuBar();
		fileMenu = new JMenu( "File" );
		fileMenu.setMnemonic( KeyEvent.VK_F );
		rescanMenuItem = new JMenuItem( "Re-Scan mods/" );
		rescanMenuItem.addMouseListener( new StatusbarMouseListener( this, "Check the mods/ folder for new files." ) );
		rescanMenuItem.addActionListener(this);
		fileMenu.add( rescanMenuItem );
		extractDatsMenuItem = new JMenuItem( "Extract Dats..." );
		extractDatsMenuItem.addMouseListener( new StatusbarMouseListener( this, "Extract FTL resources into a folder." ) );
		extractDatsMenuItem.addActionListener(this);
		fileMenu.add( extractDatsMenuItem );
		sandboxMenuItem = new JMenuItem( "XML Sandbox..." );
		sandboxMenuItem.addMouseListener( new StatusbarMouseListener( this, "Experiment with advanced mod syntax." ) );
		sandboxMenuItem.addActionListener(this);
		fileMenu.add( sandboxMenuItem );
		fileMenu.add( new JSeparator() );
		exitMenuItem = new JMenuItem( "Exit" );
		exitMenuItem.addMouseListener( new StatusbarMouseListener( this, "Exit this application." ) );
		exitMenuItem.addActionListener(this);
		fileMenu.add( exitMenuItem );
		menubar.add( fileMenu );
		helpMenu = new JMenu( "Help" );
		helpMenu.setMnemonic( KeyEvent.VK_H );
		aboutMenuItem = new JMenuItem( "About" );
		aboutMenuItem.addMouseListener( new StatusbarMouseListener( this, "Show info about this application." ) );
		aboutMenuItem.addActionListener(this);
		helpMenu.add( aboutMenuItem );
		menubar.add( helpMenu );
		this.setJMenuBar( menubar );

		this.setContentPane( contentPane );
		this.pack();
		this.setMinimumSize( new Dimension( 300, modActionsPanel.getPreferredSize().height+90 ) );
		this.setLocationRelativeTo(null);

		showAboutInfo();
  }

	/**
	 * Extra initialization that must be called after the constructor.
	 * This must be called on the Swing event thread (use invokeLater()).
	 */
	public void init() {

		List<String> preferredOrder = loadModOrder();
		rescanMods( preferredOrder );

		boolean needNewCatalog = false;

		if ( catalogFile.exists() ) {
			// Load the catalog first, before updating.
			ModDB currentDB = JacksonGrognakCatalogReader.parse( catalogFile );
			if ( currentDB != null ) modDB = currentDB;

			// Check if the downloaded catalog is stale.
			Date catalogDate = new Date( catalogFile.lastModified() );
			Calendar cal = Calendar.getInstance();
			cal.add( Calendar.DATE, catalogFetchInterval * -1 );
			if ( catalogDate.before( cal.getTime() ) ) {
				log.debug( String.format( "Catalog is older than %d days.", catalogFetchInterval ) );
				needNewCatalog = true;
			} else {
				log.debug( "Catalog isn't stale yet." );
			}
		}
		else {
			// Catalog file doesn't exist.
			needNewCatalog = true;
		}

		// Don't update if the user doesn't want to.
		String updatesAllowed = config.getProperty( "update_catalog", "false" );
		if ( !updatesAllowed.equals("true") ) needNewCatalog = false;

		if ( needNewCatalog ) {
			Runnable fetchTask = new Runnable() {
				@Override
				public void run() {
					String catalogURL = GrognakCatalogFetcher.CATALOG_URL;
					boolean fetched = GrognakCatalogFetcher.fetchCatalog( catalogURL, catalogFile, catalogETagFile );

					if ( fetched ) reloadCatalog();
				}
			};
			Thread fetchThread = new Thread( fetchTask );
			fetchThread.start();
		}
	}


	/**
	 * Reparses and replace the downloaded ModDB catalog. (thread-safe)
	 */
	public void reloadCatalog() {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				if ( catalogFile.exists() ) {
					ModDB currentDB = JacksonGrognakCatalogReader.parse( catalogFile );
					if ( currentDB != null ) modDB = currentDB;
				}
			}
		});
	}


	/**
	 * Returns a mod list with names sorted in a preferred order.
	 *
	 * Mods not mentioned in the name list appear at the end, alphabetically.
	 */
	private List<ModFileInfo> reorderMods( List<ModFileInfo> unsortedMods, List<String> preferredOrder ) {
		List<ModFileInfo> sortedMods = new ArrayList<ModFileInfo>();
		List<ModFileInfo> availableMods = new ArrayList<ModFileInfo>( unsortedMods );
		Collections.sort( availableMods );

		if ( preferredOrder != null ) {
			for ( String name : preferredOrder ) {
				Iterator<ModFileInfo> it = availableMods.iterator();
				while ( it.hasNext() ) {
					ModFileInfo modFileInfo = it.next();
					if ( modFileInfo.getName().equals( name ) ) {
						it.remove();
						sortedMods.add( modFileInfo );
						break;
					}
				}
			}
		}
		sortedMods.addAll( availableMods );

		return sortedMods;
	}

	/**
	 * Reads modorder.txt and returns a list of mod names in preferred order.
	 */
	private List<String> loadModOrder() {
		List<String> result = new ArrayList<String>();

		FileInputStream is = null;
		try {
			is = new FileInputStream( new File( modsDir, "modorder.txt" ) );
			BufferedReader br = new BufferedReader(new InputStreamReader( is, Charset.forName("UTF-8") ));
			String line;
			while ( (line = br.readLine()) != null ) {
				result.add( line );
			}
		}
		catch ( FileNotFoundException e ) {
		}
		catch ( IOException e ) {
			log.error( "Error reading modorder.txt.", e );
		}
		finally {
			try {if (is != null) is.close();}
			catch (Exception e) {}
		}

		return result;
	}

	private void saveModOrder( List<ModFileInfo> sortedMods ) {
		FileOutputStream os = null;
		try {
			os = new FileOutputStream( new File( modsDir, "modorder.txt" ) );
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter( os, Charset.forName("UTF-8") ));

			for ( ModFileInfo modFileInfo : sortedMods ) {
				bw.write( modFileInfo.getName() );
				bw.write( "\r\n" );
			}
			bw.flush();
		}
		catch ( IOException e ) {
			log.error( "Error writing modorder.txt.", e );
		}
		finally {
			try {if (os != null) os.close();}
			catch (Exception e) {}
		}
	}

	/**
	 * Clears and syncs the mods list with mods/ dir, then starts a new hash thread.
	 */
	private void rescanMods( List<String> preferredOrder ) {
		if ( rescanMenuItem.isEnabled() == false ) return;
		rescanMenuItem.setEnabled( false );

		modFileHashes.clear();
		localModsTableModel.removeAllItems();

		boolean allowZip = config.getProperty( "allow_zip", "false" ).equals( "true" );
		File[] modFiles = modsDir.listFiles( new ModFileFilter( allowZip ) );

		List<ModFileInfo> unsortedMods = new ArrayList<ModFileInfo>();
		for ( File f : modFiles ) {
			ModFileInfo modFileInfo = new ModFileInfo( f );
			unsortedMods.add( modFileInfo );
		}

		List<ModFileInfo> sortedMods = reorderMods( unsortedMods, preferredOrder );
		for ( ModFileInfo modFileInfo : sortedMods ) {
			localModsTableModel.addItem( modFileInfo );
		}

		HashThread hashThread = new HashThread( modFiles, this );
		hashThread.setDaemon( true );
		hashThread.start();
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

	/**
	 * Shows info about a local mod in the text area.
	 */
	public void showLocalModInfo( ModFileInfo modFileInfo ) {
		String modHash = modFileHashes.get( modFileInfo.getFile() );

		ModInfo modInfo = modDB.getModInfo( modHash );
		if ( modInfo != null ) {
			infoArea.setDescription( modInfo.getTitle(), modInfo.getAuthor(), modInfo.getVersion(), modInfo.getURL(), modInfo.getDescription() );
		}
		else {
			long epochTime = -1;
			try {
				epochTime = ModUtilities.getModFileTime( modFileInfo.getFile() );
			} catch ( IOException e ) {
				log.error( String.format( "Error while getting modified time of mod file contents for \"%s\".", modFileInfo.getFile() ), e );
			}

			String body = "";
			body += "No info is available for the selected mod.\n\n";

			if ( epochTime != -1 ) {
				SimpleDateFormat dateFormat = new SimpleDateFormat( "yyyy/MM/dd" );
				String dateString = dateFormat.format( new Date( epochTime ) );
				body += "It was released sometime after "+ dateString +".\n\n";
			} else {
				body += "The date of its release could not be determined.\n\n";
			}

			body += "If it is stable and has been out for over a month,\n";
			body += "please let the Slipstream devs know where you ";
			body += "found it.\n";
			body += "Include the mod's version, and this hash.\n";
			body += "MD5: "+ modHash +"\n";
			infoArea.setDescription( modFileInfo.getName(), body );
		}
	}

	public void exitApp() {
		this.setVisible( false );
		this.dispose();
	}


	@Override
	public void setStatusText( String text ) {
		if (text.length() > 0)
			statusLbl.setText(text);
		else
			statusLbl.setText(" ");
	}


	@Override
	public void actionPerformed( ActionEvent e ) {
		Object source = e.getSource();

		if ( source == patchBtn ) {
			List<File> modFiles = new ArrayList<File>();

			for ( int i=0; i < localModsTableModel.getRowCount(); i++ ) {
				if ( localModsTableModel.isSelected(i) ) {
					modFiles.add( localModsTableModel.getItem(i).getFile() );
				}
			}

			File datsDir = new File( config.getProperty( "ftl_dats_path" ) );

			BackedUpDat dataDat = new BackedUpDat();
			dataDat.datFile = new File( datsDir, "data.dat" );
			dataDat.bakFile = new File( backupDir, "data.dat.bak" );
			BackedUpDat resDat = new BackedUpDat();
			resDat.datFile = new File( datsDir, "resource.dat" );
			resDat.bakFile = new File( backupDir, "resource.dat.bak" );

			ModPatchDialog patchDlg = new ModPatchDialog( this, true );
			patchDlg.setSuccessTask( new SpawnGameTask() );

			log.info( "" );
			log.info( "Patching..." );
			log.info( "" );
			ModPatchThread patchThread = new ModPatchThread( modFiles, dataDat, resDat, patchDlg );
			patchThread.start();

			patchDlg.setVisible( true );
		}
		else if ( source == toggleAllBtn ) {
			int selectedCount = 0;
			for ( int i = localModsTableModel.getRowCount()-1; i >= 0; i-- ) {
				if ( localModsTableModel.isSelected(i) ) selectedCount++;
			}
			boolean b = ( selectedCount != localModsTableModel.getRowCount() );

			for ( int i = localModsTableModel.getRowCount()-1; i >= 0; i-- ) {
				localModsTableModel.setSelected( i, b );
			}
		}
		else if ( source == validateBtn ) {
			StringBuilder resultBuf = new StringBuilder();
			boolean anyInvalid = false;

			for ( int i=0; i < localModsTableModel.getRowCount(); i++ ) {
				if ( !localModsTableModel.isSelected(i) ) continue;

				ModFileInfo modFileInfo = localModsTableModel.getItem( i );
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
				resultBuf.append( "FTL itself can tolerate lots of XML typos and still run. " );
				resultBuf.append( "But malformed XML may break tools that do proper parsing, " );
				resultBuf.append( "and it hinders the development of new tools.\n" );
				resultBuf.append( "\n" );
				resultBuf.append( "In future releases, Slipstream will try to parse XML while " );
				resultBuf.append( "patching: first strictly, then failing over to a sloppy " );
				resultBuf.append( "parser. The sloppy parser will tolerate similar errors, " );
				resultBuf.append( "at the risk of unforseen behavior, so satisfying the " );
				resultBuf.append( "strict parser is advised.\n" );
			}
			infoArea.setDescription( "Results", resultBuf.toString() );
		}
		else if ( source == modsFolderBtn ) {
			try {
				if ( Desktop.isDesktopSupported() )
					Desktop.getDesktop().open( modsDir.getCanonicalFile() );
				else
					log.error( "Opening the mods/ folder is not possible on your OS." );
			}
			catch ( IOException f ) {
				log.error( "Error opening mods/ folder.", f );
			}
		}
		else if ( source == rescanMenuItem ) {
			setStatusText( "" );
			if ( rescanMenuItem.isEnabled() == false ) return;

			List<String> preferredOrder = new ArrayList<String>();

			for ( int i=0; i < localModsTableModel.getRowCount(); i++ ) {
				preferredOrder.add( localModsTableModel.getItem(i).getName() );
			}
			rescanMods( preferredOrder );
		}
		else if ( source == extractDatsMenuItem ) {
			setStatusText( "" );
			JFileChooser extractChooser = new JFileChooser();
			extractChooser.setDialogTitle("Choose a dir to extract into");
			extractChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			extractChooser.setMultiSelectionEnabled(false);

			if ( extractChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION )
				return;

			File extractDir = extractChooser.getSelectedFile();

			File datsDir = new File( config.getProperty( "ftl_dats_path" ) );
			File dataDatFile = new File( datsDir, "data.dat" );
			File resDatFile = new File( datsDir, "resource.dat" );
			File[] datFiles = new File[] {dataDatFile, resDatFile};

			DatExtractDialog extractDlg = new DatExtractDialog( this, extractDir, datFiles );
			extractDlg.extract();
			extractDlg.setVisible( true );
		}
		else if ( source == sandboxMenuItem ) {
			File datsDir = new File( config.getProperty( "ftl_dats_path" ) );
			File dataDatFile = new File( datsDir, "data.dat" );

			ModXMLSandbox sandboxDlg = new ModXMLSandbox( this, dataDatFile );
			sandboxDlg.setVisible( true );
		}
		else if ( source == exitMenuItem ) {
			setStatusText( "" );
			exitApp();
		}
		else if ( source == aboutMenuItem ) {
			setStatusText( "" );
			showAboutInfo();
		}
	}


	@Override
	public void hashCalculated( final File f, final String hash ) {
		SwingUtilities.invokeLater( new Runnable() {
			@Override
			public void run() {
				modFileHashes.put( f, hash );
			}
		});
	}

	@Override
	public void hashingEnded() {
		SwingUtilities.invokeLater( new Runnable() {
			@Override
			public void run() {
				rescanMenuItem.setEnabled( true );
			}
		});
	}



	private class SpawnGameTask implements Runnable {
		@Override
		public void run() {
			String neverRunFtl = config.getProperty( "never_run_ftl", "false" );
			if ( !neverRunFtl.equals("true") ) {
				File datsDir = new File( config.getProperty( "ftl_dats_path" ) );

				File exeFile = FTLUtilities.findGameExe( datsDir );
				if ( exeFile != null ) {
					int response = JOptionPane.showConfirmDialog( ManagerFrame.this, "Do you want to run the game now?", "Ready to Play", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE );
					if ( response == JOptionPane.YES_OPTION ) {
						log.info( "Running FTL..." );
						try {
							FTLUtilities.launchGame( exeFile );
						} catch ( Exception e ) {
							log.error( "Error launching FTL.", e );
						}
						exitApp();
					}
				}
			}
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
				if ( f.getName().endsWith(".ftl") ) return true;

				if ( allowZip ) {
					if ( f.getName().endsWith(".zip") ) return true;
				}
			}
			return false;
		}
	}
}
