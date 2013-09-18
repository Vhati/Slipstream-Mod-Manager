package net.vhati.modmanager.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.HashMap;
import java.util.Map;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import net.vhati.modmanager.ui.RegexDocument;


public class FieldEditorPanel extends JPanel {
	public enum ContentType { WRAPPED_LABEL, LABEL, STRING, INTEGER, BOOLEAN, SLIDER, COMBO, CHOOSER };

	private Map<String, JTextArea> wrappedLabelMap = new HashMap<String, JTextArea>();
	private Map<String, JLabel> labelMap = new HashMap<String, JLabel>();
	private Map<String, JTextField> stringMap = new HashMap<String, JTextField>();
	private Map<String, JTextField> intMap = new HashMap<String, JTextField>();
	private Map<String, JCheckBox> boolMap = new HashMap<String, JCheckBox>();
	private Map<String, JSlider> sliderMap = new HashMap<String, JSlider>();
	private Map<String, JComboBox> comboMap = new HashMap<String, JComboBox>();
	private Map<String, Chooser> chooserMap = new HashMap<String, Chooser>();
	private Map<String, JLabel> reminderMap = new HashMap<String, JLabel>();

	private GridBagConstraints gridC = new GridBagConstraints();

	private Component nameStrut = Box.createHorizontalStrut(1);
	private Component valueStrut = Box.createHorizontalStrut(120);
	private Component reminderStrut = Box.createHorizontalStrut(90);

	private boolean remindersVisible;


	public FieldEditorPanel( boolean remindersVisible ) {
		super( new GridBagLayout() );
		this.remindersVisible = remindersVisible;

		gridC.anchor = GridBagConstraints.WEST;
		gridC.fill = GridBagConstraints.HORIZONTAL;
		gridC.weightx = 0.0;
		gridC.weighty = 0.0;
		gridC.gridwidth = 1;
		gridC.gridx = 0;
		gridC.gridy = 0;

		// No default width for col 0.
		gridC.gridx = 0;
		this.add( nameStrut, gridC );
		gridC.gridx++;
		this.add( valueStrut, gridC );
		gridC.gridx++;
		if ( remindersVisible ) {
			this.add( reminderStrut, gridC );
			gridC.gridy++;
		}

		gridC.insets = new Insets(2, 4, 2, 4);
	}


	public void setNameWidth( int width ) {
		nameStrut.setMinimumSize( new Dimension(width, 0) );
		nameStrut.setPreferredSize( new Dimension(width, 0) );
	}

	public void setValueWidth( int width ) {
		valueStrut.setMinimumSize( new Dimension(width, 0) );
		valueStrut.setPreferredSize( new Dimension(width, 0) );
	}

	public void setReminderWidth( int width ) {
		reminderStrut.setMinimumSize( new Dimension(width, 0) );
		reminderStrut.setPreferredSize( new Dimension(width, 0) );
	}


	/**
	 * Constructs JComponents for a given type of value.
	 * A row consists of a static label, some JComponent,
	 * and a reminder label.
	 *
	 * The component and reminder will be accessable later
	 * via getter methods.
	 */
	public void addRow( String valueName, ContentType contentType ) {
		gridC.fill = GridBagConstraints.HORIZONTAL;
		gridC.gridwidth = 1;
		gridC.weighty = 0.0;
		gridC.gridx = 0;
		this.add( new JLabel( valueName +":" ), gridC );

		gridC.gridx++;
		if ( contentType == ContentType.WRAPPED_LABEL ) {
			gridC.anchor = GridBagConstraints.WEST;
			JTextArea valueArea = new JTextArea();
			valueArea.setBackground(null);
			valueArea.setEditable( false );
			valueArea.setBorder(null);
			valueArea.setLineWrap( true );
			valueArea.setWrapStyleWord( true );
			valueArea.setFocusable( false );
			valueArea.setFont( UIManager.getFont("Label.font") );

			wrappedLabelMap.put( valueName, valueArea );
			this.add( valueArea, gridC );
		}
		else if ( contentType == ContentType.LABEL ) {
			gridC.anchor = GridBagConstraints.WEST;
			JLabel valueLbl = new JLabel();
			valueLbl.setHorizontalAlignment( SwingConstants.CENTER );
			labelMap.put( valueName, valueLbl );
			this.add( valueLbl, gridC );
		}
		else if ( contentType == ContentType.STRING ) {
			gridC.anchor = GridBagConstraints.WEST;
			JTextField valueField = new JTextField();
			stringMap.put( valueName, valueField );
			this.add( valueField, gridC );
		}
		else if ( contentType == ContentType.INTEGER ) {
			gridC.anchor = GridBagConstraints.WEST;
			JTextField valueField = new JTextField();
			valueField.setHorizontalAlignment( JTextField.RIGHT );
			valueField.setDocument( new RegexDocument("[0-9]*") );
			intMap.put( valueName, valueField );
			this.add( valueField, gridC );
		}
		else if ( contentType == ContentType.BOOLEAN ) {
			gridC.anchor = GridBagConstraints.CENTER;
			JCheckBox valueCheck = new JCheckBox();
			valueCheck.setHorizontalAlignment( SwingConstants.CENTER );
			boolMap.put( valueName, valueCheck );
			this.add( valueCheck, gridC );
		}
		else if ( contentType == ContentType.SLIDER ) {
			gridC.anchor = GridBagConstraints.CENTER;
			JPanel panel = new JPanel();
			panel.setLayout( new BoxLayout(panel, BoxLayout.X_AXIS) );
			final JSlider valueSlider = new JSlider( JSlider.HORIZONTAL );
			valueSlider.setPreferredSize( new Dimension(50, valueSlider.getPreferredSize().height) );
			sliderMap.put( valueName, valueSlider );
			panel.add(valueSlider);
			final JTextField valueField = new JTextField(3);
			valueField.setMaximumSize( valueField.getPreferredSize() );
			valueField.setHorizontalAlignment( JTextField.RIGHT );
			valueField.setEditable( false );
			panel.add(valueField);
			this.add( panel, gridC );

			valueSlider.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent e) {
					valueField.setText( ""+valueSlider.getValue() );
				}
			});
		}
		else if ( contentType == ContentType.COMBO ) {
			gridC.anchor = GridBagConstraints.CENTER;
			JComboBox valueCombo = new JComboBox();
			valueCombo.setEditable(false);
			comboMap.put( valueName, valueCombo );
			this.add( valueCombo, gridC );
		}
		else if ( contentType == ContentType.CHOOSER ) {
			gridC.anchor = GridBagConstraints.WEST;
			JPanel panel = new JPanel();
			panel.setLayout( new BoxLayout(panel, BoxLayout.X_AXIS) );

			JTextField chooserField = new JTextField();
			panel.add( chooserField );
			panel.add( Box.createHorizontalStrut( 5 ) );
			JButton chooserBtn = new JButton( "..." );
			chooserBtn.setMargin( new Insets(1,2,1,2) );
			panel.add( chooserBtn );
			Chooser valueChooser = new Chooser( chooserField, chooserBtn );
			chooserMap.put( valueName, valueChooser );

			this.add( panel, gridC );
		}
		gridC.gridx++;

		if ( remindersVisible ) {
			gridC.anchor = GridBagConstraints.WEST;
			JLabel valueReminder = new JLabel();
			reminderMap.put( valueName, valueReminder );
			this.add( valueReminder, gridC );
		}

		gridC.gridy++;
	}

	public void addTextRow( String text ) {
		gridC.fill = GridBagConstraints.HORIZONTAL;
		gridC.weighty = 0.0;
		gridC.gridwidth = GridBagConstraints.REMAINDER;
		gridC.gridx = 0;

		gridC.anchor = GridBagConstraints.WEST;
		JTextArea textArea = new JTextArea( text );
		textArea.setBackground(null);
		textArea.setEditable( false );
		textArea.setBorder(null);
		textArea.setLineWrap( true );
		textArea.setWrapStyleWord( true );
		textArea.setFocusable( false );
		textArea.setFont( UIManager.getFont("Label.font") );

		this.add( textArea, gridC );
		gridC.gridy++;
	}

	public void addSeparatorRow() {
		gridC.fill = GridBagConstraints.HORIZONTAL;
		gridC.weighty = 0.0;
		gridC.gridwidth = GridBagConstraints.REMAINDER;
		gridC.gridx = 0;

		JPanel panel = new JPanel();
		panel.setLayout( new BoxLayout( panel, BoxLayout.Y_AXIS ) );
		panel.add( Box.createVerticalStrut(8) );
		JSeparator sep = new JSeparator();
		sep.setPreferredSize( new Dimension(1, sep.getPreferredSize().height) );
		panel.add( sep );
		panel.add( Box.createVerticalStrut(8) );

		this.add( panel, gridC );
		gridC.gridy++;
	}

	public void addBlankRow() {
		gridC.fill = GridBagConstraints.NONE;
		gridC.weighty = 0.0;
		gridC.gridwidth = GridBagConstraints.REMAINDER;
		gridC.gridx = 0;

		this.add( Box.createVerticalStrut(12), gridC );
		gridC.gridy++;
	}

	public void addFillRow() {
		gridC.fill = GridBagConstraints.VERTICAL;
		gridC.weighty = 1.0;
		gridC.gridwidth = GridBagConstraints.REMAINDER;
		gridC.gridx = 0;

		this.add( Box.createVerticalGlue(), gridC );
		gridC.gridy++;
	}


	public void setStringAndReminder( String valueName, String s ) {
		JTextField valueField = stringMap.get( valueName );
		if ( valueField != null ) valueField.setText(s);
		if ( remindersVisible ) setReminder( valueName, s );
	}

	public void setIntAndReminder( String valueName, int n ) {
		setIntAndReminder( valueName, n, ""+n );
	}
	public void setIntAndReminder( String valueName, int n, String s ) {
		JTextField valueField = intMap.get( valueName );
		if ( valueField != null ) valueField.setText( ""+n );
		if ( remindersVisible ) setReminder( valueName, s );
	}

	public void setBoolAndReminder( String valueName, boolean b ) {
		setBoolAndReminder( valueName, b, ""+b );
	}
	public void setBoolAndReminder( String valueName, boolean b, String s ) {
		JCheckBox valueCheck = boolMap.get( valueName );
		if ( valueCheck != null ) valueCheck.setSelected(b);
		if ( remindersVisible ) setReminder( valueName, s );
	}

	public void setSliderAndReminder( String valueName, int n ) {
		setSliderAndReminder( valueName, n, ""+n );
	}
	public void setSliderAndReminder( String valueName, int n, String s ) {
		JSlider valueSlider = sliderMap.get( valueName );
		if ( valueSlider != null ) valueSlider.setValue(n);
		if ( remindersVisible ) setReminder( valueName, s );
	}

	public void setComboAndReminder( String valueName, Object o ) {
		setComboAndReminder( valueName, o, o.toString() );
	}
	public void setComboAndReminder( String valueName, Object o, String s ) {
		JComboBox valueCombo = comboMap.get( valueName );
		if ( valueCombo != null ) valueCombo.setSelectedItem(o);
		if ( remindersVisible ) setReminder( valueName, s );
	}

	public void setChooserAndReminder( String valueName, String s ) {
		Chooser valueChooser = chooserMap.get( valueName );
		if ( valueChooser != null ) valueChooser.getTextField().setText(s);
		if ( remindersVisible ) setReminder( valueName, s );
	}

	public void setReminder( String valueName, String s ) {
		JLabel valueReminder = reminderMap.get( valueName );
		if ( valueReminder != null ) valueReminder.setText( "( "+ s +" )" );
	}

	public JTextArea getWrappedLabel( String valueName ) {
		return wrappedLabelMap.get( valueName );
	}

	public JLabel getLabel( String valueName ) {
		return labelMap.get( valueName );
	}

	public JTextField getString( String valueName ) {
		return stringMap.get( valueName );
	}

	public JTextField getInt( String valueName ) {
		return intMap.get( valueName );
	}

	public JCheckBox getBoolean( String valueName ) {
		return boolMap.get( valueName );
	}

	public JSlider getSlider( String valueName ) {
		return sliderMap.get( valueName );
	}

	public JComboBox getCombo( String valueName ) {
		return comboMap.get( valueName );
	}

	public Chooser getChooser( String valueName ) {
		return chooserMap.get( valueName );
	}


	public void reset() {
		for ( JTextArea valueArea : wrappedLabelMap.values() )
			valueArea.setText("");

		for ( JLabel valueLbl : labelMap.values() )
			valueLbl.setText("");

		for ( JTextField valueField : stringMap.values() )
			valueField.setText("");

		for ( JTextField valueField : intMap.values() )
			valueField.setText("");

		for ( JCheckBox valueCheck : boolMap.values() )
			valueCheck.setSelected(false);

		for ( JSlider valueSlider : sliderMap.values() )
			valueSlider.setValue(0);

		for ( JComboBox valueCombo : comboMap.values() )
			valueCombo.removeAllItems();

		for ( Chooser valueChooser : chooserMap.values() )
			valueChooser.getTextField().setText("");

		for ( JLabel valueReminder : reminderMap.values() )
			valueReminder.setText("");
	}

	@Override
	public void removeAll() {
		labelMap.clear();
		stringMap.clear();
		intMap.clear();
		boolMap.clear();
		sliderMap.clear();
		comboMap.clear();
		reminderMap.clear();
		super.removeAll();
		gridC = new GridBagConstraints();

		gridC.anchor = GridBagConstraints.WEST;
		gridC.fill = GridBagConstraints.HORIZONTAL;
		gridC.weightx = 0.0;
		gridC.weighty = 0.0;
		gridC.gridwidth = 1;
		gridC.gridx = 0;
		gridC.gridy = 0;

		// No default width for col 0.
		gridC.gridx = 0;
		this.add( Box.createVerticalStrut(1), gridC );
		gridC.gridx++;
		this.add( valueStrut, gridC );
		gridC.gridx++;
		if ( remindersVisible ) {
			this.add( reminderStrut, gridC );
			gridC.gridy++;
		}

		gridC.insets = new Insets(2, 4, 2, 4);
	}



	public static class Chooser {
		private JTextField textField;
		private JButton button;

		public Chooser( JTextField textField, JButton button ) {
			this.textField = textField;
			this.button = button;
		}

		public JTextField getTextField() { return textField; }
		public JButton getButton() { return button; }
	}
}
