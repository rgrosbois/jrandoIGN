// TODO:
// - Améliorer le nombre de tuiles téléchargées pour répondre au besoin du zoom
package fr.rg.java.jrandoIGN;

import java.awt.AWTEvent;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.io.File;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.prefs.Preferences;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

/**
 * JComponent utilisé pour afficher une image de carte IGN constituée de tuiles
 * téléchargées sur le site de l'IGN.
 *
 * <p>
 * Le composant contient:
 * <ul>
 * <li>une image de carte IGN.</li>
 * <li>Une barre d'état indiquant:
 * <ul>
 * <li>le niveau de zoom sur la carte.</li>
 * <li>l'avancement des téléchargements et actions en cours.</li>
 * <li>les coordonnées de la géolocalisation se trouvant sous la souris.</li>
 * </ul></li>
 * </ul>
 *
 * <p>
 * La souris permet différentes interactions:<ul>
 * <li>Clic gauche + glisser:
 * <ul>
 * <li>en mode édition de trace: sélectionner et déplacer un point de la
 * trace.</li>
 * <li>sinon: translater la carte.</li>
 * </ul>
 * </li>
 * <li>Molette: modification du niveau de zoom.</li>
 * </ul>
 */
public class IGNMap extends JComponent implements Printable,
        TrackModificationBroadcaster {

    // +------------+
    // | Constantes |
    // +------------+
    // Clé de développement IGN valable jusqu'au ?
    public static final String CLE_IGN_DEFAULT = "ry9bshqmzmv1gao9srw610oq";
    public static final String KEY_CLE_IGN = "key_cle_IGN";

    private static final int TILE_PIXEL_DIM = 256; // Dimension d'une tuile
    private static final int DEFAULT_MAP_WIDTH = 3 * TILE_PIXEL_DIM;
    private static final int DEFAULT_MAP_HEIGHT = 3 * TILE_PIXEL_DIM;
    // Type d'interpolation pour le zoom sur la carte
    private final static RenderingHints rhZoom = new RenderingHints(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC); // interpolation
    // Pour dessiner la trace
    private static final int STROKE_WIDTH = 5;
    private static final BasicStroke stroke
            = new BasicStroke(STROKE_WIDTH, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_BEVEL);
    private static final Color TRANSPARENT_BLUE = new Color(0f, 0f, 1.0f, 0.8f);
    private static final Color TRANSPARENT_RED = new Color(1.0f, 0.1f, 0.2f, 0.5f);
    private static final Color TRANSPARENT_GREEN = new Color(0f, 1.0f, 0f, 0.5f);
    private static final float PT_WIDTH = 7.5f;

    // Échelle de distance
    private final static Font FONT = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    private final static int LEGEND_VAL = 200;
    private final static String LEGEND = LEGEND_VAL + " m";

    // +---------+
    // | Membres |
    // +---------+
    private boolean dispOneKm;
    private boolean dispOrthoImg; // type de tuiles (aérienne ou non)
    private int ignScale = 15; // Niveau de zoom IGN
    // Indices des tuiles extrêmes de la carte actuelle (visibles ou non)
    private int tileRowMin, tileRowMax, tileColMin, tileColMax;
    // SwingWorker pour récupérer les tuiles 
    private TileLoader loader = null;
    // Cache mémoire pour les tuiles de la carte
    private final HashMap<String, ImageIcon> tuiles;
    // Utilisation d'un cache disque pour les tuiles téléchargées et plus 
    // nécessairement affichées (non autorisé par l'IGN)
    private final boolean useLocalTileCache = true;
    private final File localTileCacheDir; // répertoire de stockage

    // Géolocalisation au centre de l'écran (à maintenir à jour
    // après chaque translation de carte)
    private GeoLocation centerGeoLoc;
    private Point2D.Double mapTranslation;
    private Point mouseDragStart;

    // Zoom sur la carte
    private float scale = 1;
    private final Point2D.Double mouseWheelPos = new Point2D.Double(0, 0);

    // Barre de statut
    private final JProgressBar progress; // barre de progression
    private final JLabel zoomLbl; // Affichage du niveau de zoom
    private final String progressTitle = "Carte IGN 1:25000";

    // Trace issue du fichier KML
    private ArrayList<GeoLocation> kmlList; // données
    private final Path2D kmlPath = new Path2D.Double(); // courbe entière
    private final Path2D kmlSubpath = new Path2D.Double(); // portion de courbe
    private Rectangle2D trackPoints[] = null; // Points de la courbe
    private Point2D kmlStart, kmlEnd; // début et fin de courbe
    private final ImageIcon imgDepKml, imgArrKml, intermKml; // Images

    // Mode édition de trace
    private boolean trackEditingMode = false; // Mode édition de la trace
    private int selectedPointIdx = -1; // Point sélectionné (-1 sinon)
    private final JPopupMenu popup; // menu contextuel
    private boolean contextMenuActivated = false; // menu contextuel activé
    private JMenuItem itmDelPoint, itmAddPtAfter, itmAddPtBefore, itmCancel;

    // Échelle de distance sur la carte
    private Rectangle2D bounds;

    // Écouteurs de modification de trace
    private final List<WeakReference<TrackModificationListener>> mListeners;

    /**
     * Construire le composant avec une carte centrée autour d'une
     * géolocalisation donnée.
     *
     * @param gL centre de la carte
     */
    public IGNMap(GeoLocation gL) {
        // Initialisation
        mapTranslation = new Point2D.Double(0, 0);
        mouseDragStart = new Point(0, 0);

        // Mémoriser la position centrale
        centerGeoLoc = new GeoLocation(gL.longitude, gL.latitude);

        // Répertoire cache pour les cartes (le créer si nécessaire)
        localTileCacheDir = new File(System.getProperty("user.home") + File.separator
                + ".jrandoIGN" + File.separator + "cache" + File.separator);
        if (!localTileCacheDir.exists()) {
            localTileCacheDir.mkdirs();
        }

        // Cache mémoire: les tuiles seront chargées
        // à la mise en place du composant avec la gestion
        // de l'évênement AWTEvent.COMPONENT_RESIZED
        tuiles = new HashMap<>();

        // +--------------+
        // | Barre d'état |
        // +--------------+
        BorderLayout layout = new BorderLayout();
        super.setLayout(layout);
        JPanel panStatus = new JPanel();
        panStatus.setAlignmentX(LEFT_ALIGNMENT);
        panStatus.setLayout(new BorderLayout());
        super.add(panStatus, BorderLayout.PAGE_END);

        // Niveaux de zoom (échelle IGN/zoom dans la carte)
        zoomLbl = new JLabel(ignScale + "/100%");
        zoomLbl.setFont(new Font(Font.DIALOG, Font.PLAIN, 8));
        zoomLbl.setBorder(new EmptyBorder(0, 5, 0, 5));
        panStatus.add(zoomLbl, BorderLayout.WEST);

        // Progression des téléchargements et géolocalisation sous la souris
        progress = new JProgressBar(0, 100);
        //progress.setAlignmentX(LEFT_ALIGNMENT);
        progress.setStringPainted(true);
        progress.setFont(new Font(Font.DIALOG, Font.PLAIN, 10));
        progress.setString(progressTitle);
        panStatus.add(progress, BorderLayout.CENTER);

        // Activer la gestion des évêments graphiques
        enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK
                | AWTEvent.MOUSE_WHEEL_EVENT_MASK | AWTEvent.COMPONENT_EVENT_MASK);

        // Charger les images utilisée par la trace
        imgDepKml = new ImageIcon(IGNMap.class.getResource("/img/depart_kml.png"));
        imgArrKml = new ImageIcon(IGNMap.class.getResource("/img/arrivee_kml.png"));
        intermKml = new ImageIcon(IGNMap.class.getResource("/img/intermediaire_kml.png"));

        // Liste des écouteurs de modification de trace
        mListeners = new ArrayList<>();

        popup = createPopupMenu();
    }

    /**
     * Construire le composant et afficher une trace KML sur la carte centrée
     * sur la première géolocalisation.
     *
     * @param b
     */
    public IGNMap(HashMap<String, Object> b) {
        // Créer la carte centrée sur la 1ère géolocalisation
        this(((ArrayList<GeoLocation>) b.get(TrackReader.LOCATIONS_KEY)).get(0));

        // Ajouter la trace
        addTrack(b);
    }

    /**
     * Ajouter un point intermédiaire dans la trace.
     *
     * @param gbeforeIdx
     * @param gAfterIdx
     */
    private void insertPointInTrack(int gbeforeIdx, int gAfterIdx) {
        // Nouvelle géolocalisation par interpolation
        GeoLocation gNew = new GeoLocation();
        GeoLocation gBefore = kmlList.get(gbeforeIdx);
        GeoLocation gAfter = kmlList.get(gAfterIdx);
        gNew.latitude = (gBefore.latitude + gAfter.latitude) / 2;
        gNew.longitude = (gBefore.longitude + gAfter.longitude) / 2;
        gNew.timeStampS = (gBefore.timeStampS + gAfter.timeStampS) / 2;
        gNew.length = (gBefore.length + gAfter.length) / 2;
        gNew.kmlElevation = (gBefore.kmlElevation + gAfter.kmlElevation) / 2;
        gNew.dispElevation = (gBefore.dispElevation + gAfter.dispElevation) / 2;
        gNew.modelElevation = -1;
        gNew.speed = (gBefore.speed + gAfter.speed) / 2;
        // Insertion de la nouvelle Geolocalisation    
        kmlList.add(gAfterIdx, gNew);
        // Mettre à jour l'affichage
        selectedPointIdx = -1;
        generateTrackComponents();
        repaint();
        // avertir les écouteurs que la trace a été modifiée
        for (WeakReference<TrackModificationListener> wml : mListeners) {
            TrackModificationListener ml = wml.get();
            if (ml != null) {
                ml.onTrackModified();
            }
        }
    }

    /**
     * Création du menu contextuel utilisé pour éditer les points d'une trace.
     *
     * @return
     */
    private JPopupMenu createPopupMenu() {
        JPopupMenu pup = new JPopupMenu();
        itmDelPoint = new JMenuItem("Supprimer ce point");
        itmDelPoint.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                if (selectedPointIdx != -1) {
                    kmlList.remove(selectedPointIdx);
                    selectedPointIdx = -1;
                    generateTrackComponents();
                    repaint();
                    // avertir les écouteurs que la trace a été modifiée
                    for (WeakReference<TrackModificationListener> wml : mListeners) {
                        TrackModificationListener ml = wml.get();
                        if (ml != null) {
                            ml.onTrackModified();
                        }
                    }
                }
            }
        });
        pup.add(itmDelPoint);
        itmAddPtAfter = new JMenuItem("Ajouter un point après");
        itmAddPtAfter.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                if (selectedPointIdx != -1) {
                    insertPointInTrack(selectedPointIdx, selectedPointIdx + 1);
                }
            }
        });
        pup.add(itmAddPtAfter);
        itmAddPtBefore = new JMenuItem("Ajouter un point avant");
        itmAddPtBefore.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                if (selectedPointIdx != -1) {
                    insertPointInTrack(selectedPointIdx - 1, selectedPointIdx);
                }
            }
        });
        pup.add(itmAddPtBefore);
        pup.addSeparator();
        itmCancel = new JMenuItem("Annuler");
        itmCancel.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                selectedPointIdx = -1;
            }
        });
        pup.add(itmCancel);

        return pup;
    }

    /**
     * Ajouter une trace (issue d'un fichier KML) sur la carte.
     *
     * <p>
     * La trace est stockée dans une instance de Path2D.
     *
     * @see java.awt.geom.Path2D
     * @param b Container des données de la trace
     */
    public final void addTrack(HashMap<String, Object> b) {
        if (b == null) { // Effacer la trace actuelle
            removeKmlPath();
            return;
        }

        // Récupérer et conserver la liste des géolocalisations
        kmlList = (ArrayList<GeoLocation>) b.get(TrackReader.LOCATIONS_KEY);
        if (kmlList == null || kmlList.isEmpty()) {
            return;
        }

        // Centrer la carte sur la première position
        setMapCenter(kmlList.get(0));

        // Créer les éléments graphiques correspondant à la trace
        generateTrackComponents();
    }

    /**
     * Créer le chemin et la liste des points permettant de visualiser et éditer
     * la trace. Mémoriser les coordonnées de début et fin de trace afin de
     * dessiner les images correspondantes en même temps que la trace.
     *
     * <p>
     * Afin de minimiser les créations/suppressions d'élément graphique, toute
     * nouvelle trace réutilise le chemin et la liste des points de la
     * précédente (après réinitialisation).
     *
     * @see Path2D
     * @see Rectangle2D
     */
    private void generateTrackComponents() {
        if (kmlList == null) {
            return;
        }

        // Réinitialisations
        kmlPath.reset();
        if (trackPoints == null || trackPoints.length < kmlList.size()) {
            trackPoints = new Rectangle2D[kmlList.size()];
        }
        if (kmlStart == null) {
            kmlStart = new Point2D.Float();
        }
        if (kmlEnd == null) {
            kmlEnd = new Point2D.Float();
        }

        GeoLocation g;
        double tileDim = WMTS.getTileDim(ignScale);
        double mapOrigWmtsX = tileColMin * TILE_PIXEL_DIM;
        double mapOrigWmtsY = tileRowMin * TILE_PIXEL_DIM;
        float x = 0, y = 0;
        double latitude, longitude;
        for (int i = 0; i < kmlList.size(); i++) { // Parcours de la liste
            g = kmlList.get(i);
            if (g.isModified) {
                latitude = g.modifiedLatitude;
                longitude = g.modifiedLongitude;
            } else {
                latitude = g.latitude;
                longitude = g.longitude;
            }
            x = (float) (WMTS.longToWmtsX(longitude)
                    / tileDim * TILE_PIXEL_DIM - mapOrigWmtsX);
            y = (float) (WMTS.latToWmtsY(latitude)
                    / tileDim * TILE_PIXEL_DIM - mapOrigWmtsY);
            if (i == 0) { // Premier point
                kmlPath.moveTo(x, y);
                kmlStart.setLocation(x, y);
            } else { // points suivants
                kmlPath.lineTo(x, y);
            }
            trackPoints[i] = new Rectangle2D.Float(x - PT_WIDTH / 2,
                    y - PT_WIDTH / 2, PT_WIDTH, PT_WIDTH);
        } // Fin de parcours de la liste
        kmlEnd.setLocation(x, y);
    }

    /**
     * Activer/désactiver le mode d'édition de la trace.
     *
     * @param b
     */
    public void setEditionMode(boolean b) {
        trackEditingMode = b;
        repaint();
    }

    /**
     * Définir le type de vue (aérienne ou carte IGN).
     *
     * @param ortho Vue aérienne si vrai
     */
    public void setOrthoMode(boolean ortho) {
        if (this.dispOrthoImg != ortho) { // Changement de mode.
            this.dispOrthoImg = ortho;
            tileRowMin = 0; // Pour forcer le changement des tuiles
            identifyAndLoadMapTiles();
        }
    }

    /**
     * Afficher ou non la zone des 1 km
     *
     * @param oneKm Afficher ou non la zone
     */
    public void setOneKmMode(boolean oneKm) {
        if (this.dispOneKm != oneKm) { // Changement de mode.
            this.dispOneKm = oneKm;
            tileRowMin = 0; // Pour forcer le changement des tuiles
            identifyAndLoadMapTiles();
        }
    }

    private int sGeoIdx1 = -1;
    private int sGeoIdx2 = -1;

    /**
     * Surligner une partie de la trace.
     *
     * @param gIdx Indice de la géolocalisation dans la trace
     */
    public void setSelectedGeoLocationInterval(int gIdx, int gIdx2) {
        sGeoIdx1 = gIdx;
        sGeoIdx2 = gIdx2;
        if (gIdx2 != -1 && gIdx2 != gIdx) {
            generateKmlSubpath(gIdx, gIdx2);
        }
        repaint();
    }

    /**
     * Modifier la position centrale de la carte.
     *
     * @param gL
     */
    public void setMapCenter(GeoLocation gL) {
        if (centerGeoLoc == null || centerGeoLoc.latitude != gL.latitude
                || centerGeoLoc.longitude != gL.longitude) {
            centerGeoLoc = new GeoLocation(gL.longitude, gL.latitude);
            identifyAndLoadMapTiles();
        }
    }

    /**
     * <ol>
     * <li>Identifier les tuiles englobant la géolocalisation centrale.</li>
     * <li>Calculer le décalage à appliquer pour placer la centerGeoLoc au
     * centre de la carte.</li>
     * </ol>
     *
     * @param gL
     */
    private void identifyAndLoadMapTiles() {
        // Indices de tuiles actuels (nuls à la création de la carte)
        int oldTileRowMin = tileRowMin;
        int oldTileRowMax = tileRowMax;
        int oldTileColMin = tileColMin;
        int oldTileColMax = tileColMax;

        // Identifier la tuile centrale (i.e. contenant la géolocalisation)
        int tileRowCenter = WMTS.latToTileRow(centerGeoLoc.latitude, ignScale);
        int tileColCenter = WMTS.longToTileCol(centerGeoLoc.longitude, ignScale);

        // Taille actuelle du composant
        Dimension dim = getSize();
        if (dim.width * dim.height == 0) { // Composant non encore créé
            dim = getPreferredSize();
        }

        // Quantité de tuiles à récupérer pour couvrir 2 fois l'écran : rajouter
        // EXTRA_TILE rangées de tuiles sur chaque bord et rajouter une tuile
        // lorsque le nombre est impair
        int EXTRA_TILE = 1;
        int nTileX
                = (dim.width + dim.width + TILE_PIXEL_DIM - 1) / TILE_PIXEL_DIM + 2 * EXTRA_TILE;
        if (nTileX % 2 == 0) {
            nTileX++;
        }
        int nTileY
                = (dim.height + dim.height + TILE_PIXEL_DIM - 1) / TILE_PIXEL_DIM + 2 * EXTRA_TILE;
        if (nTileY % 2 == 0) {
            nTileY++;
        }

        // Indices des nouvelles tuiles limites: la tuile contenant la 
        // géolocalisation centrale se trouve au centre
        tileRowMin = tileRowCenter - nTileY / 2;
        tileRowMax = tileRowCenter + nTileY / 2;
        tileColMin = tileColCenter - nTileX / 2;
        tileColMax = tileColCenter + nTileX / 2;

        // Décalage pour positionner la géolocalisation au centre
        double tileDim = WMTS.getTileDim(ignScale);
        double xGL = (WMTS.longToWmtsX(centerGeoLoc.longitude)
                - tileColMin * tileDim) / tileDim * TILE_PIXEL_DIM;
        double yGL = (WMTS.latToWmtsY(centerGeoLoc.latitude)
                - tileRowMin * tileDim) / tileDim * TILE_PIXEL_DIM;
        mapTranslation.x = dim.width / 2 - xGL;
        mapTranslation.y = dim.height / 2 - yGL;

        // Si nécessaire, charger les nouvelles tuiles identifiées
        // et décaler la trace KML
        if (tileRowMin != oldTileRowMin || tileRowMax != oldTileRowMax
                || tileColMin != oldTileColMin || tileColMax != oldTileColMax) {
            if (loader != null) { // Annuler un éventuel téléchargement en cours
                loader.cancel(true);
                progress.setValue(0);
            }
            loader = new TileLoader(tileRowMin, tileRowMax, tileColMin, tileColMax,
                    dispOrthoImg, ignScale);
            loader.execute(); // Charger les tuiles

            // (Ré-)générer la trace KML
            generateTrackComponents();
        }
        repaint(); // redessiner la carte
    }

    /**
     * Afficher un nombre entier de tuiles par défaut.
     *
     * @return
     */
    @Override
    public Dimension getPreferredSize() {
        return new Dimension(DEFAULT_MAP_WIDTH, DEFAULT_MAP_HEIGHT);
    }

    /**
     * Dessiner la carte (tuiles mémorisées dans la table de hachage) et la
     * trace.
     *
     * @param gInit
     */
    @Override
    protected void paintComponent(Graphics gInit) {
        super.paintComponent(gInit);
        Graphics2D g = (Graphics2D) gInit.create();

        // +-----------------------+
        // | Position de la caméra |
        // +-----------------------+
        // Zoom autour de la position de la souris
        g.setRenderingHints(rhZoom);
        g.translate(mouseWheelPos.x, mouseWheelPos.y);
        g.scale(scale, scale);
        g.translate(-mouseWheelPos.x, -mouseWheelPos.y);
        // Recentrer la carte
        g.translate(mapTranslation.x, mapTranslation.y);
        // +------------------------+
        // | Tuiles de la carte IGN |
        // +------------------------+
        String key;
        ImageIcon img;
        for (int r = tileRowMin; r <= tileRowMax; r++) { // Lignes de tuiles
            for (int c = tileColMin; c <= tileColMax; c++) { // Colonnes de tuiles 
                key = (dispOrthoImg ? "ortho-" : "")
                        + "z" + ignScale + "-r" + r + "-c" + c;
                if (tuiles.containsKey(key)) {
                    img = tuiles.get(key);
                    img.paintIcon(IGNMap.this, g,
                            (c - tileColMin) * TILE_PIXEL_DIM,
                            (r - tileRowMin) * TILE_PIXEL_DIM);
                }
            }
        }

        // +--------------+
        // | Échelle 200m |
        // +--------------+
        Graphics2D g2 = (Graphics2D) gInit;
        double x0, x1, y0, y1;
        int w = getWidth();
        int h = getHeight();
        x0 = Math.max(w * 0.02, 6);
        y0 = Math.min(h * 0.98, getHeight() - 6 - progress.getHeight());
        x1 = x0 + LEGEND_VAL * scale * TILE_PIXEL_DIM / WMTS.getTileDim(ignScale);
        y1 = y0;
        g2.setFont(FONT);
        bounds = g2.getFontMetrics().getStringBounds(LEGEND, g2);
        // Fond semi-transparant
        g2.setPaint(new Color(.9f, .9f, 1, .9f)); // Blanc transparant        
        g2.fill(new RoundRectangle2D.Double(x0 - 5, y0 - bounds.getHeight() - 5,
                (x1 - x0) + bounds.getWidth() + 5 + 5 + 2,
                bounds.getHeight() + 5 + 5, 10, 10));
        // Graduation
        g2.setPaint(Color.BLACK);
        g2.setStroke(new BasicStroke(2));
        g2.draw(new Line2D.Double(x0, y0, x1, y1));
        g2.draw(new Line2D.Double(x0, y0, x0, y0 - 4));
        g2.draw(new Line2D.Double((x0 + x1) / 2.0, y0, (x0 + x1) / 2.0, y0 - 2));
        g2.draw(new Line2D.Double(x1, y1, x1, y1 - 4));
        // Texte
        g2.drawString(LEGEND, (float) (x1 + 2), (float) y1);

        
        // Cercle de 1 km autour de la position de départ
        if(dispOneKm) {
            double radiusEquator = TILE_PIXEL_DIM / WMTS.getTileDim(ignScale) * 1000; // 1km
            double lat, x, y;
            if (kmlStart!=null) { // Centrer sur la position de départ
              x = kmlStart.getX();
              y = kmlStart.getY();
              lat = kmlList.get(0).latitude;
            } else { // Centre au milieu de la fenêtre
              Dimension dim = getSize();
              x = -mapTranslation.x + dim.width/2;
              y = -mapTranslation.y + dim.height/2;
              lat = centerGeoLoc.latitude;

              g.setPaint(Color.magenta);
              g.fill(new Ellipse2D.Double(x-5,y-5,10,10));
            }
            double radius = radiusEquator/Math.cos(Math.toRadians(lat));
            
            g.setPaint(TRANSPARENT_GREEN);
            g.fill(new Ellipse2D.Double(x-radius,y-radius,2*radius,2*radius));
        }
            

        // +-----------+
        // | Trace KML |
        // +-----------+
        if (kmlList != null && kmlList.size() > 0) {
            // Définir le pinceau
            g.setStroke(stroke);
            g.setPaint(TRANSPARENT_BLUE);
            // Dessiner la trace
            g.draw(kmlPath);

            // Points de la trace (mode édition)
            if (trackEditingMode) {
                // Remplissage
                g.setPaint(Color.WHITE);
                for (int i = 0; i < kmlList.size(); i++) {
                    g.fill(trackPoints[i]);
                }
                // Contour
                g.setPaint(Color.BLUE);
                g.setStroke(new BasicStroke(2));
                for (int i = 0; i < kmlList.size(); i++) {
                    if (kmlList.get(i).isModified) {
                        g.setPaint(Color.RED);
                        g.draw(trackPoints[i]);
                        g.setPaint(Color.BLUE);
                    } else {
                        g.draw(trackPoints[i]);
                    }
                }
            }

            // Dessiner les marqueurs de début et fin
            imgDepKml.paintIcon(IGNMap.this, g,
                    (int) (kmlStart.getX() - imgDepKml.getIconWidth() / 2),
                    (int) (kmlStart.getY() - imgDepKml.getIconHeight()));
            imgArrKml.paintIcon(IGNMap.this, g,
                    (int) (kmlEnd.getX() - imgArrKml.getIconWidth() / 2),
                    (int) (kmlEnd.getY() - imgArrKml.getIconHeight()));

            // Sous-trace et position particulière
            if (sGeoIdx1 != -1) {
                // Sous-trace
                if (sGeoIdx2 != -1 && sGeoIdx2 != sGeoIdx1) {
                    g.setStroke(new BasicStroke(stroke.getLineWidth() * 1.5f));
                    g.setPaint(TRANSPARENT_RED);
                    g.draw(kmlSubpath);
                }

                // Position particulière
                GeoLocation gL = kmlList.get(sGeoIdx1);
                double tileDim = WMTS.getTileDim(ignScale);
                double mapOrigWmtsX = tileColMin * TILE_PIXEL_DIM;
                double mapOrigWmtsY = tileRowMin * TILE_PIXEL_DIM;
                float x = (float) (WMTS.longToWmtsX(gL.longitude) / tileDim * TILE_PIXEL_DIM - mapOrigWmtsX);
                float y = (float) (WMTS.latToWmtsY(gL.latitude) / tileDim * TILE_PIXEL_DIM - mapOrigWmtsY);
                intermKml.paintIcon(IGNMap.this, g, (int) (x - intermKml.getIconWidth() / 2),
                        (int) (y - intermKml.getIconHeight()));
            }

            g.dispose();
        }
    }

    /**
     * Détermine si la souris se trouve sur un point de la trace et renvoit son
     * indice le cas échéant. L'indice vaut -1 si aucun point n'est trouvé ou si
     * le mode édition de trace n'est pas activé.
     *
     * @param e
     * @return
     */
    private int identifySelectedPoint(MouseEvent e) {
        if (!trackEditingMode) {
            return -1;
        }
        double x = (e.getX() - mouseWheelPos.x) / scale
                + mouseWheelPos.x - mapTranslation.x;
        double y = (e.getY() - mouseWheelPos.y) / scale
                + mouseWheelPos.y - mapTranslation.y;
        for (int i = kmlList.size() - 1; i >= 0; i--) {
            if (trackPoints[i].contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Gestion des évênements statiques de la souris:
     * <ul>
     * <li>bouton appuyé: début de translation d'un point de la trace (mode
     * édition) ou la carte (sinon).</li>
     * <li>bouton relâché: mémoriser la nouvelle position du point (mode
     * édition).</li>
     * <li>La souris sortie du composant: fin d'affichage de la géolocalisation
     * sous la souris.</li>
     * </ul>
     *
     * @param e
     */
    @Override
    protected void processMouseEvent(MouseEvent e) {
        super.processMouseEvent(e);
        switch (e.getID()) {
            case MouseEvent.MOUSE_PRESSED: // Sauvegarder la position d'origine
                mouseDragStart = e.getPoint();
                selectedPointIdx = identifySelectedPoint(e);
                if (selectedPointIdx != -1) {
                    // Point sélectionné
                    if (e.isPopupTrigger()) { // clic droit: menu contextuel
                        // Adapter le menu 
                        if (selectedPointIdx == 0) { // 1er point est sélectionné
                            itmAddPtBefore.setEnabled(false);
                        } else {
                            itmAddPtBefore.setEnabled(true);
                        }
                        if (selectedPointIdx == kmlList.size() - 1) { // selection du dernier point
                            itmAddPtBefore.setEnabled(false);
                        } else {
                            itmAddPtBefore.setEnabled(true);
                        }
                        // Afficher le menu
                        popup.show(e.getComponent(), e.getX(), e.getY());
                        contextMenuActivated = true;
                    } else {
                        contextMenuActivated = false;
                    }

                    int iWidth = (int) (2 * PT_WIDTH);
                    repaint(mouseDragStart.x - iWidth, mouseDragStart.y - iWidth,
                            2 * iWidth, 2 * iWidth);
                }
                break;
            case MouseEvent.MOUSE_RELEASED:
                if (selectedPointIdx != -1 && !contextMenuActivated) { // Point en cours de déplacement
                    GeoLocation g = kmlList.get(selectedPointIdx);
                    // Provoquer un nouveau calcul de l'altitude du point
                    g.modelElevation = -1;
                    // avertir les écouteurs que le point a été modifié
                    for (WeakReference<TrackModificationListener> wml : mListeners) {
                        TrackModificationListener ml = wml.get();
                        if (ml != null) {
                            ml.onTrackModified();
                        }
                    }
                    selectedPointIdx = -1;
                    int iWidth = (int) (2 * PT_WIDTH);
                    repaint(mouseDragStart.x - iWidth, mouseDragStart.y - iWidth,
                            2 * iWidth, 2 * iWidth);
                }
                break;
            case MouseEvent.MOUSE_EXITED: // Enlever l'affichage de la géolocalisation
                progress.setString(progressTitle);
                break;
        }
    }

    /**
     * Gestion des évênements dynamiques de la souris pour le déplacement de la
     * carte.
     *
     * @param e
     */
    @Override
    protected void processMouseMotionEvent(MouseEvent e) {
        super.processMouseMotionEvent(e);
        switch (e.getID()) {
            case MouseEvent.MOUSE_DRAGGED:
                if (contextMenuActivated) {
                    // Ne rien déplacer
                } else if (trackEditingMode && selectedPointIdx != -1) {
                    // +-------------------+
                    // | Déplacer le point |
                    // +-------------------+
                    GeoLocation g = kmlList.get(selectedPointIdx);
                    double latitude = mouseY2Latitude(e);
                    double longitude = mouseX2Longitude(e);
                    if (g.latitude * g.longitude != latitude * longitude) {
                        if (g.latitude != latitude) {
                            g.modifiedLatitude = latitude;
                        }
                        if (g.modifiedLongitude != longitude) {
                            g.modifiedLongitude = longitude;
                        }
                        g.isModified = true;
                    } else {
                        g.isModified = false;
                    }
                    updateMouseGeolocationDisplay(e);

                    // Régénérer la trace
                    generateTrackComponents();
                    repaint();
                } else {
                    // +-------------------+
                    // | Déplacer la carte |
                    // +-------------------+
                    // Calculer la distance de déplacement
                    double dx = (e.getX() - mouseDragStart.x) / scale;
                    double dy = (e.getY() - mouseDragStart.y) / scale;
                    // Sauvegarder la nouvelle position d'origine
                    mouseDragStart = e.getPoint();

                    // Identifier la tuile centrale actuelle (i.e. contenant la géolocalisation)
                    int tileRowCenter = WMTS.latToTileRow(centerGeoLoc.latitude, ignScale);
                    int tileColCenter = WMTS.longToTileCol(centerGeoLoc.longitude, ignScale);

                    // Calculer les coordonnées de la nouvelle géolocalisation centrale
                    double wmtsX = WMTS.longToWmtsX(centerGeoLoc.longitude)
                            - dx / TILE_PIXEL_DIM * WMTS.getTileDim(ignScale);
                    double wmtsY = WMTS.latToWmtsY(centerGeoLoc.latitude)
                            - dy / TILE_PIXEL_DIM * WMTS.getTileDim(ignScale);
                    centerGeoLoc.longitude = WMTS.wmtsXToLongitude(wmtsX);
                    centerGeoLoc.latitude = WMTS.wmtsYToLatitude(wmtsY);

                    // Identifier si la tuile centrale a été modifiée
                    if (WMTS.latToTileRow(centerGeoLoc.latitude, ignScale) != tileRowCenter
                            || WMTS.longToTileCol(centerGeoLoc.longitude, ignScale) != tileColCenter) {
                        // Récupérer des nouvelles tuiles 
                        identifyAndLoadMapTiles();
                    } else {
                        mapTranslation.x += dx;
                        mapTranslation.y += dy;
                        repaint();
                    }
                }
                break;
            case MouseEvent.MOUSE_MOVED: // Afficher la géolocalisation sous la souris
                updateMouseGeolocationDisplay(e);
                break;
        }
    }

    private void updateMouseGeolocationDisplay(MouseEvent e) {
        progress.setString(String.format("lat=%.6f,long=%.6f",
                mouseY2Latitude(e), mouseX2Longitude(e)));
    }

    /**
     * Identifier la latitude correspondant à l'ordonnée de la souris.
     *
     * @param e
     * @return
     */
    private double mouseY2Latitude(MouseEvent e) {
        return WMTS.wmtsYToLatitude(
                ((e.getY() - mouseWheelPos.y) / scale + mouseWheelPos.y - mapTranslation.y
                + tileRowMin * TILE_PIXEL_DIM)
                * WMTS.getTileDim(ignScale) / TILE_PIXEL_DIM);
    }

    /**
     * Identifier la longitude correspondant à l'abscisse de la souris.
     *
     * @param e
     * @return
     */
    private double mouseX2Longitude(MouseEvent e) {
        return WMTS.wmtsXToLongitude(
                ((e.getX() - mouseWheelPos.x) / scale + mouseWheelPos.x - mapTranslation.x
                + tileColMin * TILE_PIXEL_DIM)
                * WMTS.getTileDim(ignScale) / TILE_PIXEL_DIM);
    }

    /**
     * Gestion de la molette de la souris pour le zoom sur la carte.
     *
     * @param e
     */
    @Override
    protected void processMouseWheelEvent(MouseWheelEvent e) {
        super.processMouseWheelEvent(e);

        // Centre du zoom
        mouseWheelPos.x = e.getX();
        mouseWheelPos.y = e.getY();

        // Calcul du nouveau niveau de zoom
        scale -= e.getWheelRotation() / 10.0;
        scale = (float) Math.max(.5, Math.min(scale, 2));

        // Calculer la nouvelle géolocalisation au centre de l'écran
        Dimension dim = getSize();
        double newCenterX = mouseWheelPos.x + (dim.width / 2.0 - mouseWheelPos.x) / scale;
        double newCenterY = mouseWheelPos.y + (dim.height / 2.0 - mouseWheelPos.y) / scale;
        centerGeoLoc.latitude = WMTS.wmtsYToLatitude(
                (newCenterY - mapTranslation.y + tileRowMin * TILE_PIXEL_DIM)
                * WMTS.getTileDim(ignScale) / TILE_PIXEL_DIM);
        centerGeoLoc.longitude = WMTS.wmtsXToLongitude(
                (newCenterX - mapTranslation.x + tileColMin * TILE_PIXEL_DIM)
                * WMTS.getTileDim(ignScale) / TILE_PIXEL_DIM);
        repaint();

        if (scale >= 2 && ignScale < 16) {
            // Passer au niveau d'échelle IGN supérieur lorsque
            // le zoom vaut 2 (jusqu'à concurrence du niveau 16)
            ignScale++;
            scale = 1;
            identifyAndLoadMapTiles();
        } else if (scale <= 0.5 && ignScale > 2) {
            // Passer au niveau d'échelle IGN inférieur lorsque
            // le zoom vaut 0.5 (jusqu'à concurrence du niveau 2)
            ignScale--;
            scale = 1;
            identifyAndLoadMapTiles();
        }

        // Afficher le niveau de zoom et l'échelle IGN
        zoomLbl.setText(ignScale + "/" + (int) (scale * 100) + "%");
    }

    /**
     * Identifier et afficher les nouvelles tuiles lorsque le composant est
     * redimensionné.
     *
     * @param e
     */
    @Override
    protected void processComponentEvent(ComponentEvent e) {
        super.processComponentEvent(e);
        switch (e.getID()) {
            case ComponentEvent.COMPONENT_MOVED:
                break;
            case ComponentEvent.COMPONENT_RESIZED:
                identifyAndLoadMapTiles();
                break;
            case ComponentEvent.COMPONENT_SHOWN:
                break;
            case ComponentEvent.COMPONENT_HIDDEN:
                break;
        }
    }

    /**
     * Pour imprimer la carte.
     *
     * @param graphics
     * @param pageFormat
     * @param pageIndex
     * @return
     * @throws PrinterException
     */
    @Override
    public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
        if (pageIndex > 0) {
            return NO_SUCH_PAGE;
        }

        Graphics2D g2d = (Graphics2D) graphics;
        g2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
        IGNMap.this.printAll(graphics);

        return PAGE_EXISTS;
    }

    private void generateKmlSubpath(int idx1, int idx2) {
        if (kmlList == null) {
            return;
        }
        if (idx2 < idx1) { // Replacer dans l'ordre
            int tmp = idx2;
            idx2 = idx1;
            idx1 = tmp;
        }

        // +----------------------------------------+
        // | Repeupler le Path2D pour le chemin KML |
        // +----------------------------------------+
        GeoLocation g;
        double tileDim = WMTS.getTileDim(ignScale);
        double mapOrigWmtsX = tileColMin * TILE_PIXEL_DIM;
        double mapOrigWmtsY = tileRowMin * TILE_PIXEL_DIM;
        kmlSubpath.reset();
        float x, y;
        for (int i = idx1; i <= idx2; i++) {
            g = kmlList.get(i);
            x = (float) (WMTS.longToWmtsX(g.longitude)
                    / tileDim * TILE_PIXEL_DIM - mapOrigWmtsX);
            y = (float) (WMTS.latToWmtsY(g.latitude)
                    / tileDim * TILE_PIXEL_DIM - mapOrigWmtsY);
            if (i == idx1) {
                kmlSubpath.moveTo(x, y);
            } else {
                kmlSubpath.lineTo(x, y);
            }
        }
    }

    /**
     * Effacer la trace KML de la carte.
     */
    public void removeKmlPath() {
        kmlList = null;
        kmlPath.reset();
        trackEditingMode = false;
        selectedPointIdx = -1;
        repaint();
    }

    @Override
    public void addTrackModificationListener(TrackModificationListener gsl) {
        WeakReference<TrackModificationListener> g = new WeakReference<>(gsl);
        if (!mListeners.contains(g)) {
            mListeners.add(g);
        }
    }

    @Override
    public void removeTrackModificationListener(TrackModificationListener gsl) {
        WeakReference<TrackModificationListener> g = new WeakReference<>(gsl);
        if (mListeners.contains(g)) {
            mListeners.remove(g);
        }
    }

    /**
     * SwingWorker permettant de charger les tuiles de l'image sans bloquer
     * l'Event Dispatch Thread.
     *
     * <p>
     * La méthode doInBackground() se charge de la récupération des tuiles
     * depuis le thread du SwingWorker. Elle vérifie, au préalable, si la tuile
     * courante n'est présente déjà dans la table de hachage avant de la
     * télécharger sur le site de l'IGN (ou de la récupérer dans le cache de
     * l'application).
     *
     * <p>
     * La méthode process() qui s'exécute depuis l'Event Dispatch Thread se
     * charge de dessiner chaque image et de la mémoriser, si nécessaire, dans
     * la mémoire cache.
     */
    class TileLoader extends SwingWorker<Void, ImageIcon> {

        private final int tRMin, tRMax, tCMin, tCMax;
        private final boolean ortho;
        private final int ignScale;

        int nTileReady = 0;

        public TileLoader(int tRMin, int tRMax, int tCMin, int tCMax, boolean ortho,
                int ignScale) {
            this.tRMin = tRMin;
            this.tRMax = tRMax;
            this.tCMin = tCMin;
            this.tCMax = tCMax;
            this.ortho = ortho;
            this.ignScale = ignScale;
        }

        /**
         * Télécharger toutes les tuiles de l'image
         *
         * @return
         * @throws Exception
         */
        @Override
        protected Void doInBackground() throws Exception {
            BufferedImage img;
            URL url;
            URLConnection connection;
            String key;
            String[] desc;
            int ligne, colonne, z;
            nTileReady = 0;

            // Récupérer la clé IGN
            Preferences prefs = Preferences.userNodeForPackage(IGNMap.class);
            final String cleIGN = prefs.get(IGNMap.KEY_CLE_IGN, IGNMap.CLE_IGN_DEFAULT);

            // Supprimer les tuiles inutiles dans la table de hachage
            for (Iterator<String> it = tuiles.keySet().iterator();
                    it.hasNext();) {
                key = it.next();
                desc = key.split("-");
                if (desc.length == 4) { // Image ortho (vue aérienne)
                    z = Integer.parseInt(desc[1].substring(1));
                    ligne = Integer.parseInt(desc[2].substring(1));
                    colonne = Integer.parseInt(desc[3].substring(1));
                } else { // Carte de randonnée
                    z = Integer.parseInt(desc[0].substring(1));
                    ligne = Integer.parseInt(desc[1].substring(1));
                    colonne = Integer.parseInt(desc[2].substring(1));
                }
                if (z != ignScale || ligne < tRMin || ligne > tRMax
                        || colonne < tCMin || colonne > tCMax) {
                    it.remove();
                }
            }

            // ---- Récupérer les nouvelles utiles ----
            // indice de la tuile centrale
            int tileRowCenter = WMTS.latToTileRow(centerGeoLoc.latitude, ignScale);
            int tileColCenter = WMTS.longToTileCol(centerGeoLoc.longitude, ignScale);

            // Calculer les distances de chaque tuile par rapport au centre
            int dist;
            HashMap<Integer, ArrayList<String>> classement = new HashMap<>();
            ArrayList<String> a;
            int maxDist = 0;
            int r, c;
            for (r = tRMin; r <= tRMax; r++) { // Lignes de tuiles
                for (c = tCMin; c <= tCMax; c++) { // Colonnes de tuiles 
                    dist = (int) Math.sqrt(Math.pow(r - tileRowCenter, 2)
                            + Math.pow(c - tileColCenter, 2));
                    key = (ortho ? "ortho-" : "")
                            + "z" + ignScale + "-r" + r + "-c" + c;

                    // Créer l'Arraylist si nécessaire
                    a = classement.get(dist);
                    if (a == null) {
                        classement.put(dist, new ArrayList<String>());
                        a = classement.get(dist);
                    }
                    if (dist > maxDist) {
                        maxDist = dist;
                    }

                    // Insérer l'élément
                    a.add(key);
                }
            }

            // Télécharger les tuiles 
            File cacheFile;
            Iterator<String> it;
            String[] tInfo;
            for (int i = 0; i <= maxDist; i++) {
                a = classement.get(i);
                if (a == null) {
                    continue;
                }
                it = a.iterator();
                while (it.hasNext()) { // Boucle sur les tuiles à la même distance
                    key = it.next();
                    cacheFile = new File(localTileCacheDir, key + ".jpg");
                    if (!tuiles.containsKey(key)) {
                        if (useLocalTileCache && cacheFile.exists()) { // Récupérer depuis le cache
                            img = ImageIO.read(cacheFile);
                        } else { // Télécharger

                            // Récupérer les indices de colonne et ligne
                            tInfo = key.split("-");
                            r = 0;
                            c = 0;
                            if (tInfo.length == 3) {
                                r = Integer.parseInt(tInfo[1].substring(1));
                                c = Integer.parseInt(tInfo[2].substring(1));
                            } else if (tInfo.length == 4) {
                                r = Integer.parseInt(tInfo[2].substring(1));
                                c = Integer.parseInt(tInfo[3].substring(1));
                            }

                            url = new URL("https://gpp3-wxs.ign.fr/" + cleIGN + "/wmts/?"
                                    + "SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0"
                                    + (ortho ? "&LAYER=ORTHOIMAGERY.ORTHOPHOTOS"
                                            : "&LAYER=GEOGRAPHICALGRIDSYSTEMS.MAPS")
                                    + "&STYLE=normal"
                                    + "&TILEMATRIXSET=PM&TILEMATRIX=" + ignScale
                                    + "&TILEROW=" + r + "&TILECOL=" + c
                                    + "&FORMAT=image/jpeg");
                            String proxyHostname = prefs.get(DesktopFrame.PROXY_HOSTNAME_KEY, "");
                            if (!"".equalsIgnoreCase(proxyHostname)) { // utiliser un proxy
                                int proxyPortNum = Integer.parseInt(
                                        prefs.get(DesktopFrame.PROXY_PORT_NUMBER_KEY, "0"));
                                connection
                                        = (HttpURLConnection) url.openConnection(new Proxy(Proxy.Type.HTTP,
                                                new InetSocketAddress(proxyHostname, proxyPortNum)));
                            } else { // Pas de proxy
                                connection = (HttpURLConnection) url.openConnection();
                            }
                            connection.setRequestProperty("Referer", "http://localhost/IGN/");
                            img = ImageIO.read(connection.getInputStream());
                            if (useLocalTileCache) { // Sauvegarder l'image dans le cache
                                ImageIO.write(img, "jpg", cacheFile);
                            }
                        }
                        publish(new ImageIcon(img, key));
                    } else {
                        publish(tuiles.get(key));
                    }

                }
            }

            return null;
        }

        /**
         * Afficher la tuile issue du téléchargement.
         *
         * @param chunks
         */
        @Override
        protected void process(List<ImageIcon> chunks) {
            super.process(chunks);
            int ligne, colonne;
            String key;
            String[] desc;
            Graphics2D g = (Graphics2D) IGNMap.this.getGraphics();

            g.translate(mapTranslation.x, mapTranslation.y);
            for (ImageIcon i : chunks) {
                key = i.getDescription();
                desc = key.split("-");
                if (desc.length == 4) { // Image ortho (vue aérienne)
                    ligne = Integer.parseInt(desc[2].substring(1));
                    colonne = Integer.parseInt(desc[3].substring(1));
                } else { // Carte de randonnée
                    ligne = Integer.parseInt(desc[1].substring(1));
                    colonne = Integer.parseInt(desc[2].substring(1));
                }
                if (!tuiles.containsKey(key)) {
                    tuiles.put(key, i);
                }
                repaint();
                nTileReady++;
                progress.setValue(nTileReady * 100 / (tileRowMax - tileRowMin + 1)
                        / (tileColMax - tileColMin + 1));
            }
            g.dispose();
        }

        @Override
        protected void done() {
            progress.setValue(0);
            repaint();
        }

    }

}
