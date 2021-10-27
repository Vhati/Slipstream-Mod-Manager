package net.vhati.modmanager.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.net.URI;
import javax.swing.AbstractAction;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.event.MouseInputAdapter;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModInfoArea extends JScrollPane {

	private static final Logger log = LoggerFactory.getLogger(ModInfoArea.class);

	private static final String STYLE_REGULAR = "regular";
	private static final String STYLE_HYPERLINK = "hyperlink";
	private static final String STYLE_TITLE = "title";
	private static final String ATTR_HYPERLINK_TARGET = "hyperlink-target";

	public static Color COLOR_HYPER = Color.BLUE;
	public static final StyleContext DEFAULT_STYLES = ModInfoArea.getDefaultStyleContext();

	private JPopupMenu linkPopup = new JPopupMenu();

	private Statusbar statusbar = null;
	private String lastClickedLinkTarget = null;

	private JTextPane textPane;
	private StyledDocument doc;
	private boolean browseWorks;

	public ModInfoArea() {
		this(DEFAULT_STYLES);
	}

	public ModInfoArea(StyleContext styleContext) {
		super();

		textPane = new JTextPane();
		textPane.setEditable(false);

		doc = new DefaultStyledDocument(styleContext);
		textPane.setStyledDocument(doc);

		Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
		if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
			browseWorks = true;
		}

		linkPopup.add(new AbstractAction("Copy link address") {
			@Override
			public void actionPerformed(ActionEvent ae) {
				if (lastClickedLinkTarget != null) {
					Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(lastClickedLinkTarget),
							null);
				}
			}
		});

		MouseInputAdapter hyperlinkListener = new MouseInputAdapter() {
			private Cursor defaultCursor = new Cursor(Cursor.DEFAULT_CURSOR);
			private Cursor linkCursor = new Cursor(Cursor.HAND_CURSOR);
			private boolean wasOverLink = false;

			@Override
			public void mousePressed(MouseEvent e) {
				if (e.isConsumed())
					return;
				if (e.isPopupTrigger())
					showMenu(e);
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				if (e.isConsumed())
					return;
				if (e.isPopupTrigger())
					showMenu(e);
			}

			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.isConsumed())
					return;
				if (!SwingUtilities.isLeftMouseButton(e))
					return;

				AttributeSet tmpAttr = doc.getCharacterElement(textPane.viewToModel(e.getPoint())).getAttributes();
				Object targetObj = tmpAttr.getAttribute(ATTR_HYPERLINK_TARGET);
				if (targetObj != null) {
					Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
					if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
						try {
							desktop.browse(new URI(targetObj.toString()));
						} catch (Exception f) {
							log.error("Error browsing clicked url: " + targetObj.toString(), f);
						}
					}
					e.consume();
				}
			}

			@Override
			public void mouseMoved(MouseEvent e) {
				AttributeSet tmpAttr = doc.getCharacterElement(textPane.viewToModel(e.getPoint())).getAttributes();
				Object targetObj = tmpAttr.getAttribute(ATTR_HYPERLINK_TARGET);
				if (targetObj != null) {
					textPane.setCursor(linkCursor);
					if (statusbar != null)
						statusbar.setStatusText(targetObj.toString());
					wasOverLink = true;
				} else {
					if (wasOverLink) {
						textPane.setCursor(defaultCursor);
						if (statusbar != null)
							statusbar.setStatusText("");
					}
					wasOverLink = false;
				}
			}

			private void showMenu(MouseEvent e) {
				AttributeSet tmpAttr = doc.getCharacterElement(textPane.viewToModel(e.getPoint())).getAttributes();
				Object targetObj = tmpAttr.getAttribute(ATTR_HYPERLINK_TARGET);
				if (targetObj != null) { // Link menu.
					textPane.requestFocus();

					lastClickedLinkTarget = targetObj.toString();

					int nx = e.getX();
					if (nx > 500)
						nx = nx - linkPopup.getSize().width;

					linkPopup.show(e.getComponent(), nx, e.getY() - linkPopup.getSize().height);

					e.consume();
				}
			}
		};
		textPane.addMouseListener(hyperlinkListener);
		textPane.addMouseMotionListener(hyperlinkListener);

		textPane.addMouseListener(new ClipboardMenuMouseListener());

		this.setViewportView(textPane);
	}

	public void setDescription(String title, String body) {
		setDescription(title, null, null, null, body);
	}

	public void setDescription(String title, String author, String version, String url, String body) {
		Style regularStyle = doc.getStyle(STYLE_REGULAR);
		try {
			doc.remove(0, doc.getLength());
			doc.insertString(doc.getLength(), title + "\n", doc.getStyle(STYLE_TITLE));

			boolean first = true;
			if (author != null) {
				doc.insertString(doc.getLength(), String.format("%sby %s", (first ? "" : " "), author), regularStyle);
				first = false;
			}
			if (version != null) {
				doc.insertString(doc.getLength(), String.format("%s(version %s)", (first ? "" : " "), version), regularStyle);
				first = false;
			}
			if (!first) {
				doc.insertString(doc.getLength(), "\n", regularStyle);
			}

			if (url != null) {
				doc.insertString(doc.getLength(), "Website: ", regularStyle);

				if (browseWorks && url.matches("^(?:https?|ftp)://.*")) {
					SimpleAttributeSet tmpAttr = new SimpleAttributeSet(doc.getStyle(STYLE_HYPERLINK));
					tmpAttr.addAttribute(ATTR_HYPERLINK_TARGET, url);
					doc.insertString(doc.getLength(), "Link", tmpAttr);
				} else {
					doc.insertString(doc.getLength(), url, regularStyle);
				}

				doc.insertString(doc.getLength(), "\n", regularStyle);
			}

			doc.insertString(doc.getLength(), "\n", regularStyle);

			if (body != null) {
				doc.insertString(doc.getLength(), body, regularStyle);
			}
		} catch (BadLocationException e) {
			log.error("Error filling info text area", e);
		}

		textPane.setCaretPosition(0);
	}

	public void setCaretPosition(int n) {
		textPane.setCaretPosition(n);
	}

	public void clear() {
		try {
			doc.remove(0, doc.getLength());
		} catch (BadLocationException e) {
			log.error("Error clearing info text area", e);
		}
	}

	public void appendTitleText(String s) throws BadLocationException {
		doc.insertString(doc.getLength(), s, doc.getStyle(STYLE_TITLE));
	}

	public void appendRegularText(String s) throws BadLocationException {
		doc.insertString(doc.getLength(), s, doc.getStyle(STYLE_REGULAR));
	}

	public void appendLinkText(String linkURL, String linkTitle) throws BadLocationException {
		if (browseWorks && linkURL.matches("^(?:https?|ftp)://.*")) {
			SimpleAttributeSet tmpAttr = new SimpleAttributeSet(doc.getStyle(STYLE_HYPERLINK));
			tmpAttr.addAttribute(ATTR_HYPERLINK_TARGET, linkURL);
			doc.insertString(doc.getLength(), linkTitle, tmpAttr);
		} else {
			doc.insertString(doc.getLength(), linkURL, doc.getStyle(STYLE_REGULAR));
		}
	}

	/**
	 * Sets a component with a statusbar to be set during mouse events.
	 */
	public void setStatusbar(Statusbar comp) {
		this.statusbar = comp;
	}

	private static StyleContext getDefaultStyleContext() {
		StyleContext result = new StyleContext();
		Style defaultStyle = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
		Style baseStyle = result.addStyle("base", defaultStyle);

		Style regularStyle = result.addStyle(STYLE_REGULAR, baseStyle);
		StyleConstants.setFontFamily(regularStyle, Font.MONOSPACED);
		StyleConstants.setFontSize(regularStyle, 12);

		Style hyperStyle = result.addStyle(STYLE_HYPERLINK, regularStyle);
		StyleConstants.setForeground(hyperStyle, COLOR_HYPER);
		StyleConstants.setUnderline(hyperStyle, true);

		Style titleStyle = result.addStyle(STYLE_TITLE, baseStyle);
		StyleConstants.setFontFamily(titleStyle, Font.SANS_SERIF);
		StyleConstants.setFontSize(titleStyle, 24);
		StyleConstants.setBold(titleStyle, true);

		return result;
	}
}
