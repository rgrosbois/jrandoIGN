package fr.rg.java.jrandoIGN;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.HashMap;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JInternalFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.text.JTextComponent;

/**
 * Fenêtre interne contenant une carte IGN et une barre de menu.
 *
 * @see IGNMap
 */
public class IGNFrame extends JInternalFrame implements ActionListener {

  // Version de la classe
  private static final long serialVersionUID = 1L;
  // Clés pour la gestion des menus
  private final static String actionAddress = "ACTION_ADDRESS",
    actionOrthoMap = "ACTION_ORTHOMAP",
    actionPrint = "ACTION_PRINT";
  private IGNMap map = null; // Image de la carte

  /**
   * Contruire la fenêtre sans carte.
   */
  public IGNFrame() {
    super("Carte IGN",
      true, // resizable
      true, // closeable
      true, // maximizable
      true); // iconifiable

    // Positionnement de la fenêtre
    setSize(700, 500);
    setLocation(30, 30);

    ResourceBundle resB
      = ResourceBundle.getBundle("i18n/strings", Locale.getDefault());

    // +----------------+
    // | Barre de menus |
    // +----------------+
    JMenuBar myMenuBar = new JMenuBar();

    // Menu Fichier
    JMenu menuFichier = new JMenu(resB.getString("file_menu"));
    // Imprimer
    JMenuItem mItemPrint = new JMenuItem(resB.getString("print_menu_itm"));
    mItemPrint.addActionListener(IGNFrame.this);
    mItemPrint.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P,
      ActionEvent.ALT_MASK));
    mItemPrint.setActionCommand(actionPrint);
    menuFichier.add(mItemPrint);
    // Rechercher
    JMenuItem mItmAddress = new JMenuItem(resB.getString("address_menu_itm"));
    mItmAddress.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A,
      ActionEvent.ALT_MASK));
    mItmAddress.setActionCommand(actionAddress);
    mItmAddress.addActionListener(IGNFrame.this);
    menuFichier.add(mItmAddress); // Item rechercher depuis une adresse

    myMenuBar.add(menuFichier);

    // Menu Afficher
    JMenu menuAfficher = new JMenu(resB.getString("display_menu"));
    JCheckBoxMenuItem mItmAirMap
      = new JCheckBoxMenuItem(resB.getString("satellite_menu_itm"));
    mItmAirMap.addActionListener(IGNFrame.this);
    mItmAirMap.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_U,
      ActionEvent.ALT_MASK));
    mItmAirMap.setActionCommand(actionOrthoMap);
    menuAfficher.add(mItmAirMap);
    myMenuBar.add(menuAfficher);

    setJMenuBar(myMenuBar);
  }

  /**
   * Contruire une fenêtre interne avec barre de menu et carte centrée autour
   * d'une géolocalisation.
   *
   * @param center
   */
  public IGNFrame(GeoLocation center) {
    this();

    // Carte IGN
    map = new IGNMap(center);
    add(map);
  }

  /**
   * Contruire une fenêtre interne avec barre de menu et carte contenant une
   * trace KML.
   *
   * @param b bundle contenant la trace
   */
  public IGNFrame(HashMap<String, Object> b) {
    this();

    // Carte IGN
    map = new IGNMap(b);
    add(map);
  }

  /**
   * Récupérer le composant affichant la carte IGN.
   *
   * @return
   */
  public IGNMap getMap() {
    return map;
  }

  /**
   * Gestion des menus de la fenêtre.
   *
   * @param e
   */
  @Override
  public void actionPerformed(ActionEvent e) {
    switch (e.getActionCommand()) {
      case actionAddress:
      // +---------------------------------------+
        // | Centrer la carte autour d'une adresse |
        // +---------------------------------------+

        // Combobox avec le focus pour les suggestions d'emplacements
        JComboBox<String> adresseCB = new JComboBox<>();
        adresseCB.setEditable(true); // éditable
        // Sélection de l'item avec souris ou clavier validé par ENTER (?)
        adresseCB.putClientProperty("JComboBox.isTableCellEditor", Boolean.TRUE);
        // Gestion avancée du champ d'édition
        JTextComponent editor = (JTextComponent) adresseCB.getEditor().getEditorComponent();
        editor.setDocument(new IGNLocationSuggest(adresseCB));
        // Pour prendre le focus une fois la boîte de dialogue créée
        adresseCB.addAncestorListener(new AncestorListener() {
          @Override
          public void ancestorAdded(AncestorEvent event) {
            JComponent component = event.getComponent();
            component.requestFocusInWindow();
          }

          @Override
          public void ancestorRemoved(AncestorEvent event) {
          }

          @Override
          public void ancestorMoved(AncestorEvent event) {
          }
        });

        // Boîte de dialogue
        JOptionPane.showMessageDialog(this,
          adresseCB, "Rechercher une adresse",
          JOptionPane.QUESTION_MESSAGE);

        // Récupération de l'adresse
        String address = (String) adresseCB.getSelectedItem();
        if (address != null) { // Adresse confirmée
          // Remplacer par la 1ère suggestion OpenLS
          GeoLocation[] g = IGNLocationSuggest.geolocaliser(address, 1);
          if (g != null && g[0] != null) {
            // Mettre à jour la carte IGN
            map.setMapCenter(g[0]);
            // Sauvegarder la position pour une prochaine utilisation
            DesktopFrame.saveCenterLocation(g[0]);
          }
        }
        break;
      case actionOrthoMap:
        map.setOrthoMode(((JCheckBoxMenuItem) e.getSource()).isSelected());
        break;
      case actionPrint:
        PrinterJob pj = PrinterJob.getPrinterJob();
        pj.setPrintable(getMap());
        if (pj.printDialog()) {
          try {
            pj.print();
          } catch (PrinterException ex) {
            Logger.getLogger(IGNFrame.class.getName()).log(Level.SEVERE, null, ex);
          }
        }
        break;
    }
  }
}
