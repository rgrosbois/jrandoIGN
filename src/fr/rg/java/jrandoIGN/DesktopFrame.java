package fr.rg.java.jrandoIGN;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GraphicsConfiguration;
import java.awt.MouseInfo;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyVetoException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.ButtonGroup;
import javax.swing.JDesktopPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Fenêtre principale contenant les fenêtres internes de carte et d'information.
 */
public class DesktopFrame extends JFrame implements ActionListener,
  TrackInfoFrame.GeoLocSelectionListener, TrackModificationListener {

  // Version
  private static final long serialVersionUID = 11;
  // Auteur
  private static final String AUTHOR_NAME = "R. Grosbois";
  // Latitude par défaut (Echirolles)
  private static final float DEFAULT_LATITUDE = 45f;
  // Longitude par défaut (Echirolles)
  private static final float DEFAULT_LONGITUDE = 0f;
  // Sauvegarde de la latitude
  private static final String SAVED_LATITUDE = "saved_latitude";
  // Sauvegarde de la longitude
  private static final String SAVED_LONGITUDE = "saved_longitude";
  // Sauvegarde du dernier répertoire de fichier KML consulté
  private static final String LAST_USED_DIR_KEY = "last_used_dir";
  // Sauvegarde du nom d'hôte du proxy
  public static final String PROXY_HOSTNAME_KEY = "proxy_hostname";
  // Sauvegarde du numéro de port du proxy
  public static final String PROXY_PORT_NUMBER_KEY = "proxy_port_number";
  // Menus de l'IHM
  private JMenuItem mItmOpenKml, mItmOpenHiTrack, mItmCloseKml, mItmSaveKml;
  private JRadioButtonMenuItem mItmEditKml;
  private JMenuItem mItmAbout, mItmIGNKey;
  private JRadioButtonMenuItem mItmMapWin, mItmInfoWin;
  private JMenuItem mItmCasc, mItmTileHor, mItmTileVer;
  private JMenuItem mItmConfigProxy;

  // Conteneur pour les données de la trace
  private HashMap<String, Object> trackBundle;

  /**
   * Créer la fenêtre principale dans l'écran spécifié.
   *
   * @param gc
   */
  public DesktopFrame(GraphicsConfiguration gc) {
    super(gc);

    // Chaînes de caractères internationalisées
    ResourceBundle resB = ResourceBundle.getBundle("i18n/strings",
      Locale.getDefault());

    // Titre de l'application
    super.setTitle(resB.getString("app_name"));

    // La fenêtre (sans décoration) occupe 80% de l'écran principal
    Rectangle screenSize = gc.getBounds();
    super.setSize(screenSize.width * 8 / 10, screenSize.height * 8 / 10);

    // Respect du mode de placement du gestionnaire de fenêtres natif
    super.setLocationByPlatform(true);

    // Définir le gestionnaire de fenêtres internes
    super.setContentPane(new JDesktopPane());

    // +-------+
    // | Menus |
    // +-------+
    JMenuBar myMenuBar = new JMenuBar();
    super.setJMenuBar(myMenuBar);
    myMenuBar.add(getTrackMenu(resB)); // Trace
    myMenuBar.add(getWindowMenu(resB)); // Fenêtre
    myMenuBar.add(getConfigMenu(resB)); // Configuration
    myMenuBar.add(getHelpMenu(resB)); // Aide

    // +------------------------------+
    // | Fenêtre interne de Carte IGN |
    // +------------------------------+
    IGNFrame igf = getIGNFrame(null, true);
    try {
      // Sélectionner la fenêtre
      igf.setSelected(true);
    } catch (PropertyVetoException ex) {
      Logger.getLogger(DesktopFrame.class.getName()).log(Level.SEVERE, null, ex);
    }
  }

  /**
   * Création du menu de gestion de trace.
   *
   * @param resB Ressource contenant les chaînes de caractères
   * internationalisées
   * @return
   */
  private JMenu getTrackMenu(ResourceBundle resB) {
    // Menu
    JMenu trackMenu = new JMenu(resB.getString("path_menu"));

    // Ouvrir une trace KML
    mItmOpenKml = new JMenuItem(resB.getString("open_kml_path_menu_itm"));
    mItmOpenKml.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O,
      ActionEvent.ALT_MASK));
    mItmOpenKml.addActionListener(DesktopFrame.this);
    trackMenu.add(mItmOpenKml); // Item Ouvrir un fichier KML
    
    // Ouvrir une trace HiTrack (Huawei)
    mItmOpenHiTrack = new JMenuItem(resB.getString("open_hitrack_path_menu_itm"));
    mItmOpenHiTrack.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_H,
      ActionEvent.ALT_MASK));
    mItmOpenHiTrack.addActionListener(DesktopFrame.this);
    trackMenu.add(mItmOpenHiTrack); // Item Ouvrir un fichier HiTrack

    // Editer la trace -> inactif tant qu'aucune trace n'est ouverte
    mItmEditKml = new JRadioButtonMenuItem(resB.getString("edit_path_menu_itm"));
    mItmEditKml.addActionListener(DesktopFrame.this);
    mItmEditKml.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E,
      ActionEvent.ALT_MASK));
    mItmEditKml.setSelected(false);
    mItmEditKml.setEnabled(false);
    trackMenu.add(mItmEditKml);

    // Fermer la trace en cours -> inactif tant qu'aucune trace n'est ouverte
    mItmCloseKml = new JMenuItem(resB.getString("remove_path_menu_itm"));
    mItmCloseKml.addActionListener(DesktopFrame.this);
    mItmCloseKml.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D,
      ActionEvent.ALT_MASK));
    mItmCloseKml.setEnabled(false);
    trackMenu.add(mItmCloseKml);

    // Enregistrer la trace en cours -> inactif tant qu'aucune trace n'est ouverte
    mItmSaveKml = new JMenuItem(resB.getString("save_path_menu_itm"));
    mItmSaveKml.addActionListener(DesktopFrame.this);
    mItmSaveKml.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S,
      ActionEvent.ALT_MASK));
    mItmSaveKml.setEnabled(false);
    trackMenu.add(mItmSaveKml);

    return trackMenu;
  }

  /**
   * Création du menu de gestion des fenêtres internes.
   *
   * @param resB Ressource contenant les chaînes de caractères
   * internationalisées
   * @return
   */
  private JMenu getWindowMenu(ResourceBundle resB) {
    // Menu
    JMenu winMenu = new JMenu(resB.getString("window_menu"));

    mItmMapWin = new JRadioButtonMenuItem(resB.getString("mapwin_menu_itm"), true);
    mItmMapWin.addActionListener(DesktopFrame.this);
    mItmMapWin.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M,
      ActionEvent.ALT_MASK));
    winMenu.add(mItmMapWin);
    mItmInfoWin = new JRadioButtonMenuItem(resB.getString("infowin_menu_itm"), false);
    mItmInfoWin.addActionListener(DesktopFrame.this);
    mItmInfoWin.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I,
      ActionEvent.ALT_MASK));
    winMenu.add(mItmInfoWin);
    ButtonGroup group = new ButtonGroup();
    group.add(mItmMapWin);
    group.add(mItmInfoWin);
    winMenu.addSeparator();
    mItmCasc = new JMenuItem(resB.getString("cascade_menu_itm"));
    mItmCasc.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C,
      ActionEvent.ALT_MASK));
    mItmCasc.addActionListener(DesktopFrame.this);
    winMenu.add(mItmCasc);
    mItmTileHor = new JMenuItem(resB.getString("horiz_tile_menu_itm"));
    mItmTileHor.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_H,
      ActionEvent.ALT_MASK));
    mItmTileHor.addActionListener(DesktopFrame.this);
    winMenu.add(mItmTileHor);
    mItmTileVer = new JMenuItem(resB.getString("vert_tile_menu_itm"));
    mItmTileVer.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V,
      ActionEvent.ALT_MASK));
    mItmTileVer.addActionListener(DesktopFrame.this);
    winMenu.add(mItmTileVer);

    return winMenu;
  }

  /**
   * Création du menu de configuration de l'application.
   *
   * @param resB Ressource contenant les chaînes de caractères
   * internationalisées
   * @return
   */
  private JMenu getConfigMenu(ResourceBundle resB) {
    // Menu
    JMenu configMenu = new JMenu(resB.getString("config_menu"));

    mItmConfigProxy = new JMenuItem(resB.getString("proxy") + "...");
    mItmConfigProxy.addActionListener(DesktopFrame.this);
    configMenu.add(mItmConfigProxy);

    mItmIGNKey = new JMenuItem(resB.getString("ignkey_menu_itm"));
    mItmIGNKey.addActionListener(DesktopFrame.this);
    configMenu.add(mItmIGNKey);

    return configMenu;
  }

  /**
   * Création du menu d'aide.
   *
   * @param resB Ressource contenant les chaînes de caractères
   * internationalisées
   * @return
   */
  private JMenu getHelpMenu(ResourceBundle resB) {
    // Menu
    JMenu helpMenu = new JMenu(resB.getString("help_menu"));

    mItmAbout = new JMenuItem(resB.getString("about_menu_itm"));
    mItmAbout.addActionListener(DesktopFrame.this);
    helpMenu.add(mItmAbout);

    return helpMenu;
  }

  /**
   * Récupérer la dernière position sauvegardée dans les préférences (ou
   * Echirolles, sinon).
   *
   * @return
   */
  private GeoLocation getSavedCenterLocation() {
    Preferences settings = Preferences.userNodeForPackage(this.getClass());
    double latitude = settings.getFloat(SAVED_LATITUDE, DEFAULT_LATITUDE);
    double longitude = settings.getFloat(SAVED_LONGITUDE, DEFAULT_LONGITUDE);
    return new GeoLocation(longitude, latitude);
  }

  /**
   * Récupérer la référence vers la fenêtre interne affichant la carte IGN.
   *
   * @param b bundle contenant une trace à dessiner sur la carte
   * @param force Créer la fenêtre si elle n'existe pas
   * @return Reférence vers la fenêtre interne ou null si inexistante
   */
  private IGNFrame getIGNFrame(HashMap<String, Object> b, boolean force) {
    IGNFrame ignFrame = null;

    // Récupérer la fenêtre interne si elle existe
    JDesktopPane desktopPane = (JDesktopPane) getContentPane();
    JInternalFrame[] frames = desktopPane.getAllFrames();
    for (JInternalFrame jif : frames) {
      if (jif instanceof IGNFrame) {
        ignFrame = (IGNFrame) jif;
        if (b != null) {
          ignFrame.getMap().addTrack(b);
        }
        return ignFrame; // La fenêtre existe
      }
    }

    // La fenêtre n'existe pas
    if (force) { // Créer la fenêtre
      if (b == null) {
        ignFrame = new IGNFrame(getSavedCenterLocation());
      } else {
        ignFrame = new IGNFrame(b);
      }
      // Écouter les modification de trace
      ignFrame.getMap().addTrackModificationListener(this);
      // Rendre visible la fenêtre
      ignFrame.setVisible(true);
      desktopPane.add(ignFrame);
    }

    return ignFrame;
  }

  /**
   * Récupérer la fenêtre interne contenant les statistiques et y afficher
   * éventuellement une trace.
   *
   * @param b bundle contenant la trace à afficher
   * @param force la créer si nécessaire
   * @return Reférence vers la fenêtre interne ou null si inexistante
   */
  private TrackInfoFrame getTrackInfoFrame(HashMap<String, Object> b, boolean force) {
    TrackInfoFrame tif = null;

    // Récupérer la fenêtre interne si elle existe
    JDesktopPane desktopPane = (JDesktopPane) getContentPane();
    JInternalFrame[] frames = desktopPane.getAllFrames();
    for (JInternalFrame jif : frames) {
      if (jif instanceof TrackInfoFrame) {
        tif = (TrackInfoFrame) jif;
        tif.addTrack(b);
        return tif;
      }
    }

    // La créer si nécessaire
    if (force) {
      tif = new TrackInfoFrame(b);
      tif.addGeoLocSelectionListener(DesktopFrame.this);
      tif.setVisible(true);
      desktopPane.add(tif);
    }

    return tif;
  }

  /**
   * Enregistrer la position pour centrer la carte lors d'un prochain
   * redémarrage.
   *
   * @param center Géolocalisation pour recentrer la carte
   */
  public static void saveCenterLocation(GeoLocation center) {
    Preferences settings = Preferences.userNodeForPackage(DesktopFrame.class);
    settings.putDouble(SAVED_LATITUDE, center.latitude);
    settings.putDouble(SAVED_LONGITUDE, center.longitude);
    try {
      settings.flush();
    } catch (BackingStoreException ex) {
      Logger.getLogger(DesktopFrame.class.getName()).log(Level.SEVERE, null, ex);
    }
  }

  /**
   * Activer ou désactiver les actions sur une trace.
   *
   * @param b
   */
  private void enableTrackManipulation(boolean b) {
    mItmCloseKml.setEnabled(b);
    mItmEditKml.setEnabled(b);
    mItmSaveKml.setEnabled(b);
  }

  /**
   * Choisir un fichier KML puis enregistrer la trace courante.
   */
  private void saveKmlFile() {
    // Sélecteur de fichiers de type KML affichant initialement
    // le dernier répértoire utilisé
    Preferences prefs = Preferences.userNodeForPackage(this.getClass());
    JFileChooser chooser = new JFileChooser(prefs.get(LAST_USED_DIR_KEY, null));
    
    ResourceBundle resB = ResourceBundle.getBundle("i18n/strings",
      Locale.getDefault());
    chooser.setApproveButtonText(resB.getString("save_button_text"));

    // Filtre 
    FileNameExtensionFilter filter = new FileNameExtensionFilter(
      "Fichier KML", "kml", "KML");
    chooser.setFileFilter(filter);
    
    // Préremplir le nom de fichier
    File file = new File(trackBundle.get(TrackReader.PATHNAME_KEY).toString());
    chooser.setSelectedFile(file);

    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      // Un fichier a été sélectionné
      File choosenFile = chooser.getSelectedFile();

      // Enregistrer le répertoire courant
      prefs.put(LAST_USED_DIR_KEY, choosenFile.getParent());
      try {
        prefs.flush();
      } catch (BackingStoreException ex) {
        Logger.getLogger(DesktopFrame.class.getName()).log(Level.SEVERE, null, ex);
      }

      ArrayList<GeoLocation> list = (ArrayList<GeoLocation>) trackBundle.get(TrackReader.LOCATIONS_KEY);
      FileOutputStream fos = null;
      boolean useModel = false;
      TrackInfoFrame tif = getTrackInfoFrame(trackBundle, false);
      if (tif != null) {
        useModel = tif.isCorrectedElevationSelected();
      }
      try {
        fos = new FileOutputStream(choosenFile);
        KMLWriter.writeKMLFile(list, fos, useModel);
      } catch (IOException e) {
        System.err.println("Erreur d'ouverture du fichier "
          + chooser.getSelectedFile());
      }

      // Modifier le titre de l'application
      setTitle(resB.getString("app_name") + " - " + choosenFile.getName()
        + " (" + trackBundle.get(TrackReader.NUM_LOC_KEY) + ")");
    }
  }
  
  /**
   * Choisir puis ouvrir un fichier HiTrack contenant une trace
   */
  private void openHiTrack() {
    // Sélecteur de fichiers affichage initialement le
    // dernier répertoire utilisé
    Preferences prefs = Preferences.userNodeForPackage(this.getClass());
    JFileChooser chooser = new JFileChooser(prefs.get(LAST_USED_DIR_KEY, null));
    
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      // Un fichier a été sélectionné
      File choosenFile = chooser.getSelectedFile();
      
      // Enregistrer le répertoire courant
      prefs.put(LAST_USED_DIR_KEY, choosenFile.getParent());
      try {
        prefs.flush();
      } catch (BackingStoreException ex) {
        Logger.getLogger(DesktopFrame.class.getName()).log(Level.SEVERE, null, ex);
      }
      
      // Extraire les géolocalisations
      trackBundle
        = (new TrackReader()).extractFromHiTrack(choosenFile.getAbsolutePath());
      
      handleTrackBundle();
    }
    
  }

  /**
   * Choisir puis ouvrir un fichier KML contenant une trace.
   */
  private void openKmlFile() {
    // Sélecteur de fichiers de type KML affichant initialement
    // le dernier répértoire utilisé
    Preferences prefs = Preferences.userNodeForPackage(this.getClass());
    FileNameExtensionFilter filter = new FileNameExtensionFilter(
      "Fichier KML", "kml", "KML");

    JFileChooser chooser = new JFileChooser(prefs.get(LAST_USED_DIR_KEY, null));
    chooser.setFileFilter(filter);

    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      // Un fichier a été sélectionné
      File choosenFile = chooser.getSelectedFile();

      // Enregistrer le répertoire courant
      prefs.put(LAST_USED_DIR_KEY, choosenFile.getParent());
      try {
        prefs.flush();
      } catch (BackingStoreException ex) {
        Logger.getLogger(DesktopFrame.class.getName()).log(Level.SEVERE, null, ex);
      }

      // Extraire les géolocalisations
      trackBundle
        = (new TrackReader()).extractFromKML(choosenFile.getAbsolutePath());

      handleTrackBundle();
    }
  }

  /**
   * Extraire les informations du Bundle pour mettre à jour le titre de 
   * l'application et afficher la trace sur la carte.
   */
  private void handleTrackBundle() {
    // Modifier le titre de l'application
    ResourceBundle resB
            = ResourceBundle.getBundle("i18n/strings", Locale.getDefault());
    setTitle(resB.getString("app_name") + " - "
            + trackBundle.get(TrackReader.PATHNAME_KEY)
            + " (" + trackBundle.get(TrackReader.NUM_LOC_KEY) + ")");
    
    ArrayList<GeoLocation> list
            = (ArrayList<GeoLocation>) trackBundle.get(TrackReader.LOCATIONS_KEY);
    if (list != null && !list.isEmpty()) {
      // Sauvegarder la position centrale
      saveCenterLocation(list.get(0));

      // Transférer les informations aux fenêtres internes (les créer si 
      // nécessaire)
      getTrackInfoFrame(trackBundle, true);
      IGNFrame igf = getIGNFrame(trackBundle, true);

      // Donner le focus à la fenêtre de carte
      JDesktopPane desktopPane = (JDesktopPane) getContentPane();
      desktopPane.setSelectedFrame(igf);

      // Activer la manipulation de trace
      enableTrackManipulation(true);
    }
  }
  
  /**
   * Enlever la trace KML actuellement affichée.
   */
  private void removeKmlPath() {
    // +----------------------+
    // | Enlever la trace KML |
    // +----------------------+
    IGNFrame igf = getIGNFrame(null, false);
    if (igf != null) {
      igf.getMap().removeKmlPath();
    }
    TrackInfoFrame pif = getTrackInfoFrame(null, false);
    if (pif != null) {
      pif.clear();
    }
    trackBundle = null;

    // Modifier le titre de l'application
    ResourceBundle resB = ResourceBundle.
      getBundle("i18n/strings", Locale.getDefault());
    setTitle(resB.getString("app_name"));
    enableTrackManipulation(false);
  }

  private void cascadedInternalFrames() {
    JDesktopPane desk = (JDesktopPane) getContentPane();
    Dimension size = desk.getSize();
    JInternalFrame[] allframes = desk.getAllFrames(); // Nombre de fenêtres
    int decalage = 25;
    int w = size.width - allframes.length * 2 * decalage;
    int h = size.height - allframes.length * 2 * decalage;
    int x = 0, y = 0;
    for (JInternalFrame f : allframes) {
      if (!f.isClosed() && f.isIcon()) {
        try {
          f.setIcon(false);
        } catch (PropertyVetoException ex) {
          Logger.getLogger(DesktopFrame.class.getName()).log(Level.SEVERE, null, ex);
        }
      }
      desk.getDesktopManager().resizeFrame(f, x, y, w, h);
      try {
        f.setSelected(true);
      } catch (PropertyVetoException ex) {
      }
      x = x + decalage;
      y = y + decalage;
    }
  }

  private void horizontallyTiledInternalFrames() {
    JDesktopPane desk = (JDesktopPane) getContentPane();
    Dimension size = desk.getSize();
    JInternalFrame[] allframes = desk.getAllFrames(); // Nombre de fenêtres
    int w = size.width;
    int h = size.height / allframes.length;
    int x = 0, y = 0;
    for (JInternalFrame f : allframes) {
      if (!f.isClosed() && f.isIcon()) {
        try {
          f.setIcon(false);
        } catch (PropertyVetoException ex) {
          Logger.getLogger(DesktopFrame.class.getName()).log(Level.SEVERE, null, ex);
        }
      }
      desk.getDesktopManager().resizeFrame(f, x, y, w, h);
      try {
        f.setSelected(true);
      } catch (PropertyVetoException ex) {
      }
      y = y + h;
    }
  }

  private void verticallyTiledInternalFrames() {
    JDesktopPane desk = (JDesktopPane) getContentPane();
    Dimension size = desk.getSize();
    JInternalFrame[] allframes = desk.getAllFrames(); // Nombre de fenêtres
    int w = size.width / allframes.length;
    int h = size.height;
    int x = 0, y = 0;
    for (JInternalFrame f : allframes) {
      if (!f.isClosed() && f.isIcon()) {
        try {
          f.setIcon(false);
        } catch (PropertyVetoException ex) {
          Logger.getLogger(DesktopFrame.class.getName()).log(Level.SEVERE, null, ex);
        }
      }
      desk.getDesktopManager().resizeFrame(f, x, y, w, h);
      try {
        f.setSelected(true);
      } catch (PropertyVetoException ex) {
      }
      x = x + w;
    }
  }

  /**
   * Spécifier un serveur mandataire pour les connexions réseau. Le serveur est
   * identifier par son nom d'hôte (ou adresse IP) et son port.
   */
  private void setupProxy() {
    ResourceBundle resB
      = ResourceBundle.getBundle("i18n/strings", Locale.getDefault());
    Preferences prefs = Preferences.userNodeForPackage(this.getClass());

    JTextField hostname = new JTextField(prefs.get(PROXY_HOSTNAME_KEY, ""));
    JTextField port = new JTextField(prefs.get(PROXY_PORT_NUMBER_KEY, ""));
    Object[] message = {
      resB.getString("hostname") + " :", hostname,
      resB.getString("port") + " :", port
    };

    int option = JOptionPane.showConfirmDialog(DesktopFrame.this, message,
      resB.getString("proxy"), JOptionPane.OK_CANCEL_OPTION);
    if (option == JOptionPane.OK_OPTION) {
      try { // Sauvegarder dans les préférences
        prefs.put(PROXY_HOSTNAME_KEY, hostname.getText());
        prefs.put(PROXY_PORT_NUMBER_KEY, port.getText());
        prefs.flush();
      } catch (BackingStoreException ex) {
        Logger.getLogger(DesktopFrame.class.getName()).log(Level.SEVERE, null, ex);
      }
    }
  }

  private void aboutMessage() {
    ResourceBundle resB = ResourceBundle.getBundle("i18n/strings", Locale.getDefault());
    JOptionPane.showMessageDialog(DesktopFrame.this,
      resB.getString("app_name")
      + "\nVersion: " + serialVersionUID
      + "\n" + AUTHOR_NAME,
      resB.getString("about_menu_itm"),
      JOptionPane.INFORMATION_MESSAGE);
  }

  private void setupIGNDevelopmentKey() {
    // Récupérer la clé IGN
    Preferences prefs = Preferences.userNodeForPackage(IGNMap.class);
    String cleIGN = prefs.get(IGNMap.KEY_CLE_IGN, IGNMap.CLE_IGN_DEFAULT);
    String newKey;
    newKey = JOptionPane.
      showInputDialog("Entrer une clef de développement IGN valide", cleIGN);
    if (newKey != null && !"".equals(newKey)) {
      try {
        prefs.put(IGNMap.KEY_CLE_IGN, newKey);
        prefs.flush();
      } catch (BackingStoreException ex) {
        Logger.getLogger(DesktopFrame.class.getName()).log(Level.SEVERE, null, ex);
      }
    }
  }

  /**
   * Gestion des menus.
   *
   * @param e
   */
  @Override
  public void actionPerformed(ActionEvent e) {
    Object src = e.getSource();
    if (mItmOpenKml.equals(src)) {
      openKmlFile();
    } else if (mItmOpenHiTrack.equals(src)) {
      openHiTrack();
    } else if (mItmCloseKml.equals(src)) {
      removeKmlPath();
    } else if (mItmEditKml.equals(src)) {
      // Warning si le mode de correction d'altitude n'est pas activé
      TrackInfoFrame tif = getTrackInfoFrame(trackBundle, false);
      if (mItmEditKml.isSelected()
        && (tif == null || !tif.isCorrectedElevationSelected())) {
        JOptionPane.showMessageDialog(DesktopFrame.this,
          "Les positions modifiées risque d'être associées à des altitudes "
          + "erronnées.\nIl est préférable d'activer le mode de correction "
          + "d'altitude depuis la fenêtre d'informations sur la trace",
          "Altitudes non ramenées au sol",
          JOptionPane.WARNING_MESSAGE);
      }

      getIGNFrame(trackBundle, true).getMap().setEditionMode(mItmEditKml.isSelected());
    } else if (mItmSaveKml.equals(src)) {
      saveKmlFile();
    } else if (mItmMapWin.equals(src)) { // Sélectionner la fen. carte
      try {
        getIGNFrame(trackBundle, true).setSelected(true);
      } catch (PropertyVetoException ex) {
      }
    } else if (mItmInfoWin.equals(src)) { // Sélectionner la fen. statistiques
      try {
        getTrackInfoFrame(trackBundle, true).setSelected(true);
      } catch (PropertyVetoException ex) {
      }
    } else if (mItmCasc.equals(src)) { // Cascader les fenêtres
      cascadedInternalFrames();
    } else if (mItmTileHor.equals(src)) { // Mosaïque horizontale
      horizontallyTiledInternalFrames();
    } else if (mItmTileVer.equals(src)) { // Mosaïque verticale
      verticallyTiledInternalFrames();
    } else if (mItmConfigProxy.equals(src)) { // serveur mandataire
      setupProxy();
    } else if (mItmAbout.equals(src)) { // A propos
      aboutMessage();
    } else if (mItmIGNKey.equals(src)) { // Entrer/modifier la clé IGN
      setupIGNDevelopmentKey();
    }
  }

  /**
   * Une géolocalisation a été sélectionnée dans la fenêtre de statistiques.
   *
   * @param geoIdx
   */
  @Override
  public void onGeoLocIntervalSeleted(int geoIdx1, int geoIdx2) {
    IGNFrame igf = getIGNFrame(null, false);
    if (igf != null) {
      igf.getMap().setSelectedGeoLocationInterval(geoIdx1, geoIdx2);
    }
  }

  /**
   * Démarrage de l'application.
   *
   * @param args
   */
  public static void main(String[] args) {
    // Look and Feel
    try {
      UIManager.setLookAndFeel(
        UIManager.getCrossPlatformLookAndFeelClassName());
    } catch (UnsupportedLookAndFeelException | ClassNotFoundException |
      InstantiationException | IllegalAccessException e) {
    }
    UIManager.put("swing.boldMetal", Boolean.FALSE);

    EventQueue.invokeLater(new Runnable() {
      @Override
      public void run() {
        JFrame.setDefaultLookAndFeelDecorated(true);

        // Utiliser l'écran où se trouve la souris
        DesktopFrame frame = new DesktopFrame(MouseInfo.getPointerInfo().
          getDevice().getDefaultConfiguration());

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
      }
    });
  }

  /**
   * La trace a été modifiée sur l'affichage de carte IGN, prévenir la fenêtre
   * d'affichage des statistiques, si elle existe.
   */
  @Override
  public void onTrackModified() {
    TrackInfoFrame tif = getTrackInfoFrame(trackBundle, false);
    if (tif != null) {
      tif.onTrackModified();
    }
  }

}
