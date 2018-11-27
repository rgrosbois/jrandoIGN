package fr.rg.java.jrandoIGN;

import java.awt.AWTEvent;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.StringTokenizer;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.border.TitledBorder;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;

/**
 * Fenêtre interne permettant d'afficher les statistiques d'une trace ainsi
 * qu'un graphique double représentant:
 * <ul>
 * <li>la vitesse en fonction de la distance parcourue,</li>
 * <li>l'altitude en fonction de la distance parcourue.</li>
 * </ul>
 *
 * <p>
 * La fonctionnalité de correction d'altitude consiste à remplacer les altitudes
 * de la trace par celles issues d'un modèle d'altitude IGN.
 *
 * @author grosbois
 */
public class TrackInfoFrame extends JInternalFrame
		implements InternalFrameListener, PropertyChangeListener, ActionListener, TrackModificationListener {

	private static final long serialVersionUID = 1L;
	/**
	 * Polices utilisées.
	 */
	private static final Font fontSmall = new Font("Dialog", Font.PLAIN, 9);
	private static final Font fontNormal = new Font("Dialog", Font.PLAIN, 10);

	/**
	 * Couleurs utilisées.
	 */
	private static final Color transparentBlue = new Color(0f, 0f, 1.0f, 0.5f),
			transparentWhite = new Color(1f, 1f, 1.0f, 0.5f), transparentRed = new Color(1f, 0f, 0f, 0.5f),
			speedColor = new Color(100, 100, 255), // Bleu clair
			elevColor = new Color(255, 100, 100), // rouge clair,
			intervColor = new Color(1, .1f, .2f, .5f);

	// Liste des écouteurs de sélection de géolocalisation
	private final List<WeakReference<GeoLocSelectionListener>> gListeners;

	/**
	 * Distance cumulative (en m). Suite au traitement des données, cette valeur
	 * peut différer de celle calculée à la lecture du fichier KML.
	 */
	private int distTot;
	/**
	 * Durée totale (avec les pause) de parcours de la trace.
	 */
	private long dureeTot;
	/**
	 * Altitude maximale du graphe (en m).
	 */
	private int graphElevMax;
	/**
	 * Altitude minimale du graphe (en m).
	 */
	private int graphElevMin;
	/**
	 * Liste des géolocalisations
	 */
	private ArrayList<GeoLocation> locList;
	/**
	 * Zone de dessin pour les courbes
	 */
	private final JComponent graph;
	/**
	 * Vitesse minimale du graphe (en km/h).
	 */
	private float graphSpeedMin;
	/**
	 * Vitesse maximale du graphe (en km/h).
	 */
	private float graphSpeedMax;

	/**
	 * Case à cocher pour activer/désactiver la fonctionnalité de correction
	 * d'altitude.
	 */
	private final JCheckBoxMenuItem correctedElevations;
	/**
	 * Barre de progression durant la correction d'altitude
	 */
	private final JProgressBar progressBar;
	/**
	 * Correcteur d'altitude à partir de modèles d'élévations
	 */
	private ElevationCorrection corr = null;
	/**
	 * Sauvegarde des altitudes mesurées en cas d'utilisation de celles issues
	 * d'un modèle Internet.
	 */
	private float[] bkpElevations;

	/**
	 * Pour l'affichage des statistiques de parcours
	 */
	private final JLabel distanceLbl, denivLbl, elevLbl, durationLbl, speedLbl;

	/**
	 * Constructeur.
	 *
	 * @param bundle
	 *            contient les données du trajet.
	 */
	public TrackInfoFrame(HashMap<String, Object> bundle) {
		// Propriétes de la fenêtre
		super("", true, // resizable
				true, // closeable
				true, // maximizable
				true); // iconifiable
		ResourceBundle resB = ResourceBundle.getBundle("i18n/strings", Locale.getDefault());
		setTitle(resB.getString("pathinfoframe_name"));
		setOpaque(true);
		setBackground(Color.BLACK);
		setSize(700, 500);

		// Liste d'écouteurs de sélection de géolocalisation
		gListeners = new ArrayList<>();

		// +----------------+
		// | Barre de menus |
		// +----------------+
		JMenuBar menuBar = new JMenuBar();
		setJMenuBar(menuBar);

		JMenu menuOptions = new JMenu(resB.getString("option_menu"));
		menuBar.add(menuOptions);

		correctedElevations = new JCheckBoxMenuItem(resB.getString("altitude_correction"));
		correctedElevations.setEnabled(false);
		correctedElevations.setSelected(false);
		correctedElevations.addActionListener(TrackInfoFrame.this);
		menuOptions.add(correctedElevations);

		// +--------------+
		// | Statistiques |
		// +--------------+
		JPanel statPan = new JPanel(new GridLayout(1, 5));
		statPan.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		statPan.setBackground(Color.BLACK);
		add(statPan, BorderLayout.PAGE_START);

		String emptyTxt = resB.getString("empty_text");
		TitledBorder bDist = BorderFactory.createTitledBorder(resB.getString("distance_title"));
		bDist.setTitleFont(fontSmall);
		bDist.setTitleColor(Color.WHITE);
		distanceLbl = new JLabel("<html>" + emptyTxt + "</html>");
		distanceLbl.setFont(fontNormal);
		distanceLbl.setHorizontalAlignment(SwingConstants.CENTER);
		distanceLbl.setBorder(bDist);
		distanceLbl.setForeground(Color.WHITE);
		statPan.add(distanceLbl);

		TitledBorder bElev = BorderFactory.createTitledBorder(resB.getString("elevation_title"));
		bElev.setTitleFont(fontSmall);
		bElev.setTitleColor(Color.WHITE);
		elevLbl = new JLabel("<html><i><small>" + emptyTxt + "<br>" + emptyTxt + "</small></i></html>");
		elevLbl.setFont(fontNormal);
		elevLbl.setHorizontalAlignment(SwingConstants.CENTER);
		elevLbl.setBorder(bElev);
		elevLbl.setForeground(Color.WHITE);
		statPan.add(elevLbl);

		TitledBorder bDeniv = BorderFactory.createTitledBorder(resB.getString("slope_title"));
		bDeniv.setTitleFont(fontSmall);
		bDeniv.setTitleColor(Color.WHITE);
		denivLbl = new JLabel("<html><i><small>" + emptyTxt + "<br>" + emptyTxt + "</small></i></html>");
		denivLbl.setFont(fontNormal);
		denivLbl.setBorder(bDeniv);
		denivLbl.setForeground(Color.WHITE);
		denivLbl.setHorizontalAlignment(SwingConstants.CENTER);
		statPan.add(denivLbl);

		TitledBorder bDuree = BorderFactory.createTitledBorder(resB.getString("duration_title"));
		bDuree.setTitleFont(fontSmall);
		bDuree.setTitleColor(Color.WHITE);
		durationLbl = new JLabel("<html><i><small>" + emptyTxt + "<br>" + emptyTxt + "</small></i></html>");
		durationLbl.setFont(fontNormal);
		durationLbl.setHorizontalAlignment(SwingConstants.CENTER);
		durationLbl.setBorder(bDuree);
		durationLbl.setForeground(Color.WHITE);
		statPan.add(durationLbl);

		TitledBorder bSpeed = BorderFactory.createTitledBorder(resB.getString("speed_title"));
		bSpeed.setTitleFont(fontSmall);
		bSpeed.setTitleColor(Color.WHITE);
		speedLbl = new JLabel("<html><i><small>" + emptyTxt + "<br>" + emptyTxt + "</small></i></html>");
		speedLbl.setFont(fontNormal);
		speedLbl.setHorizontalAlignment(SwingConstants.CENTER);
		speedLbl.setBorder(bSpeed);
		speedLbl.setForeground(Color.WHITE);
		statPan.add(speedLbl);

		// +--------------------+
		// | Graphe du parcours |
		// +--------------------+
		graph = new MixedGraph();
		add(graph, BorderLayout.CENTER);
		// Eventuelle trace KML
		if (bundle != null) {
			TrackInfoFrame.this.addTrack(bundle);
		}

		// +----------------------+
		// | Barre de progression |
		// +----------------------+
		progressBar = new JProgressBar(0, 100);
		progressBar.setStringPainted(true);
		progressBar.setFont(fontNormal);
		progressBar.setAlignmentX(LEFT_ALIGNMENT);
		add(progressBar, BorderLayout.PAGE_END);

		addInternalFrameListener(TrackInfoFrame.this);
	}

	/**
	 * Spécifier la trace à analyser.
	 *
	 * @param bundle
	 *            Données de la trace ou null pour effacer une trace existante.
	 *
	 */
	public void addTrack(HashMap<String, Object> bundle) {
		if (bundle == null) { // Effacement d'une ancienne trace
			clear();
			correctedElevations.setEnabled(false); // désactiver le correcteur
			return;
		}
		// autoriser la fonction correction d'altitude
		correctedElevations.setEnabled(true);

		locList = (ArrayList<GeoLocation>) bundle.get(KMLReader.LOCATIONS_KEY);
		updateTrack();
	}

	/**
	 * Mettre à jour l'affichage de la trace, lors de son ajout ou après une
	 * modification.
	 */
	private void updateTrack() {
		// Calcul de la durée totale
		if (locList != null && !locList.isEmpty()) {
			dureeTot = locList.get(locList.size() - 1).timeStampS - locList.get(0).timeStampS;
		}

		// Lancer la tâche de correction d'altitude
		if (correctedElevations.isSelected()) {
			corr = new ElevationCorrection();
			corr.addPropertyChangeListener(this);
			corr.execute();
		} else {
			filterTrack(); // traiter les données
			parseAndDisplaySubTrack(-1, -1); // analyser et afficher la trace
												// entière
		}
	}

	/**
	 * Filtrer les géolocalisation de la trace et identifier les extrema du
	 * graphe.
	 *
	 * <p>
	 * Le filtrage consiste à:
	 * <ul>
	 * <li>Supprimer les variations d'altitudes inférieures à un seuil de
	 * référence (i.e. suppression du bruit d'acquisition du capteur)</li>
	 * <li>Calculer les distances cumulatives et vitesses instantanées avec les
	 * nouvelles altitudes.</li>
	 * </ul>
	 */
	private void filterTrack() {
		GeoLocation g, lastLocation = null;
		final float minDeltaAlt = 10; // Seuil de lissage d'altitude
		float cumulDist = 0;

		// Vecteur pour le filtrage de la vitesse
		// final int NVAL = 6;
		// float v[] = new float[NVAL];
		// for (int j = 0; j < NVAL; j++) {
		// v[j] = loc.get(j).speed;
		// }
		float pathSpeedMin = 1000;
		float pathSpeedMax = 0;
		int pathElevMin = 10_000;
		int pathElevMax = 0;

		for (int i = 0; i < locList.size(); i++) {
			g = locList.get(i);

			// Récupérer la bonne altitude
			if (correctedElevations.isSelected()) { // Altitude du modèle
				g.dispElevation = g.modelElevation;
			} else { // Altitude du fichier KML
				g.dispElevation = g.kmlElevation;
			}
			System.out.println("g.dispElevation=" + g.dispElevation);

			if (lastLocation != null) {
				// Lisser l'altitude
				if (Math.abs(g.dispElevation - lastLocation.dispElevation) < minDeltaAlt) {
					g.dispElevation = lastLocation.dispElevation;
				}

				// Recalculer la distance cumulative
				cumulDist += KMLReader.computeDistance(g, lastLocation);
				g.length = (int) cumulDist;

				// Recalculer la vitesse instantanée
				if (g.timeStampS - lastLocation.timeStampS > 0) {
					g.speed = (g.length - lastLocation.length) * 3.6f / (g.timeStampS - lastLocation.timeStampS);
				} else {
					g.speed = 0;
				}

				// // Attribuer une vitesse non nulle à la première position
				// if (i == 1) {
				// lastLocation.speed = g.speed;
				// }
				// // +------------------------+
				// // | Filtrage de la vitesse |
				// // +------------------------+
				// // Détection des extrema locaux de vitesse
				// v[i % NVAL] = g.speed;
				// // Recherche des extrema
				// int imin = 0, imax = 0;
				// for (int j = 1; j < NVAL; j++) {
				// if (v[j] < v[imin]) {
				// imin = j;
				// }
				// if (v[j] > v[imax]) {
				// imax = j;
				// }
				// }
				// // Moyenne glissante de la vitesse avec suppression des
				// extrema
				// float moyenne = 0;
				// for (int j = 0; j < NVAL; j++) {
				// if (j != imin && j != imax) {
				// moyenne += v[j];
				// }
				// }
				// moyenne /= (NVAL - 2);
				// g.speed = moyenne; // remplacer la vitesse instantanée par la
				// moyenne
				// Mettre à jour les extrema
				if (g.speed < pathSpeedMin) {
					pathSpeedMin = g.speed;
				}
				if (g.speed > pathSpeedMax) {
					pathSpeedMax = g.speed;
				}
				if (g.dispElevation < pathElevMin) {
					pathElevMin = (int) g.dispElevation;
				}
				if (g.dispElevation > pathElevMax) {
					pathElevMax = (int) g.dispElevation;
				}

			}
			lastLocation = g;
		}
		// Modifier les variables globales
		distTot = (int) cumulDist;
		graphElevMax = pathElevMax + 100;
		graphElevMin = (pathElevMin / 1000) * 1000;
		graphSpeedMax = pathSpeedMax + 10;
		graphSpeedMin = 0;

	}

	/**
	 * Calculer les statistiques de la (sous-)trace puis mettre à jour les
	 * affichages.
	 *
	 * <p>
	 * Les statistiques consistent en:
	 * <ul>
	 * <li>L'identification des pauses (vitesses inférieures à un seuil fixé) et
	 * le calcul de la durée sans pause de la trace.</li>
	 * <li>Les dénivelés cumulés positifs et négatifs.</li>
	 * </ul>
	 */
	private void parseAndDisplaySubTrack(int start, int end) {
		if (locList == null || locList.isEmpty()) {
			return;
		}

		// Extrema
		float subpathCumulDist = 0;
		int subpathElevMin = 10_000;
		int subpathElevMax = 0;
		float denivCumulPos = 0;
		float denivCumulNeg = 0;

		// Comptabilisation des pauses
		final float seuilVitesse = 1.5f; // en km/h (calcul des pauses)
		boolean pauseDetectee = false;
		long debutPause = 0; // Instant de départ de la pause
		long dureePause = 0; // Durée de la pause
		long dureeTotale, dureeSansPause;

		// Indices des géolocalisation de départ et de fin
		int lstart = start, lend = end;
		if (start == -1) {
			lstart = 0;
		}
		if (end == -1) {
			lend = locList.size() - 1;
		}
		if (lend < lstart) {
			int tmp = lend;
			lend = lstart;
			lstart = tmp;
		}

		GeoLocation lastLocation = null, g;
		for (int i = lstart; i <= lend; i++) { // Boucle sur les
												// géolocalisations
			g = locList.get(i);

			if (lastLocation != null) {
				// Dénivelés cumulatifs
				if (g.dispElevation > lastLocation.dispElevation) { // Dénivelé
																	// positif
					denivCumulPos += (g.dispElevation - lastLocation.dispElevation);
				} else { // Dénivelé négatif
					denivCumulNeg += (lastLocation.dispElevation - g.dispElevation);
				}
				System.out.println(g.dispElevation + " -> " + lastLocation.dispElevation + ": " + "+" + denivCumulPos
						+ ", -" + denivCumulNeg);

				// Distance cumulative
				subpathCumulDist += KMLReader.computeDistance(g, lastLocation);
			}

			// Détection des pauses
			if (g.speed < seuilVitesse) { // Pas de mouvement
				if (!pauseDetectee) { // Début de pause
					debutPause = g.timeStampS; // sauvegarder l'instant de
												// départ
					pauseDetectee = true;
				}
			} else { // Mouvement détecté
				if (pauseDetectee) { // Fin de pause
					dureePause += (g.timeStampS - debutPause); // Calcul de la
																// durée
					pauseDetectee = false;
				}
			}

			// Extrema
			if (g.dispElevation < subpathElevMin) {
				subpathElevMin = (int) g.dispElevation;
			}
			if (g.dispElevation > subpathElevMax) {
				subpathElevMax = (int) g.dispElevation;
			}

			lastLocation = g;
		} // Fin de boucle sur les géolocalisations

		// Durées de parcours
		dureeTotale = locList.get(lend).timeStampS - locList.get(lstart).timeStampS;
		dureeSansPause = dureeTotale - dureePause;

		// Afficher les statistiques
		if (start == -1 && end == -1) { // Parcours complet -> couleur blanche
			distanceLbl.setForeground(Color.WHITE);
			elevLbl.setForeground(Color.WHITE);
			denivLbl.setForeground(Color.WHITE);
			durationLbl.setForeground(Color.WHITE);
			speedLbl.setForeground(Color.WHITE);
		} else { // Sous-parcours -> couleur rougeatre
			distanceLbl.setForeground(intervColor);
			elevLbl.setForeground(intervColor);
			denivLbl.setForeground(intervColor);
			durationLbl.setForeground(intervColor);
			speedLbl.setForeground(intervColor);
		}
		distanceLbl.setText("<html>" + dist2String(subpathCumulDist) + "</html>");
		elevLbl.setText("<html><i><small>" + elev2HTML(subpathElevMin, subpathElevMax) + "</small></i></html>");
		denivLbl.setText("<html><i><small>" + deniv2HTML(denivCumulPos, denivCumulNeg) + "<small></i></html>");
		durationLbl.setText("<html><i><small>" + time2String(dureeTotale, true) + "<br>("
				+ time2String(dureeSansPause, true) + ")" + "</small></i></html>");
		speedLbl.setText("<html><i><small>" + meanSpeeds2HTML(subpathCumulDist, dureeTotale, dureeSansPause)
				+ "</small></i></html>");

		// Redessiner le graphe
		graph.invalidate();
	}

	/**
	 * Effacer les courbes sur le graphe.
	 */
	public void clear() {
		locList = null;
		if (graph != null) { // Redessiner le graphe
			graph.invalidate();
		}

		// Réinitialiser les affichages
		ResourceBundle resB = ResourceBundle.getBundle("i18n/strings", Locale.getDefault());
		String emptyTxt = resB.getString("empty_text");
		distanceLbl.setText("<html>" + emptyTxt + "</html>");
		denivLbl.setText("<html><i><small>" + emptyTxt + "<br>" + emptyTxt + "</small></i></html>");
		elevLbl.setText("<html><i><small>" + emptyTxt + "<br>" + emptyTxt + "</small></i></html>");
		durationLbl.setText("<html><i><small>" + emptyTxt + "<br>" + emptyTxt + "</small></i></html>");
		speedLbl.setText("<html><i><small>" + emptyTxt + "<br>" + emptyTxt + "</small></i></html>");
	}

	/**
	 * Surveiller l'état de la propriéte 'progress' utilisée par la tâche de
	 * correction d'altitude afin de mettre à jour la barre de progrès.
	 *
	 * @param evt
	 */
	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if ("progress".equals(evt.getPropertyName())) {
			int progress = (Integer) evt.getNewValue();
			progressBar.setValue(progress);
			progressBar.setString("Correction altitude " + progress + " %");
		}
	}

	/**
	 * Surveiller l'utilisation de la fonctionnalité de correction d'altitude.
	 *
	 * @param e
	 */
	@Override
	public void actionPerformed(ActionEvent e) {
		if (correctedElevations.isSelected()) { // Tâche de correction
												// d'altitude
			corr = new ElevationCorrection();
			corr.addPropertyChangeListener(this);
			corr.execute();
		} else { // Remettre en place les altitudes initiales
			resetElevations();
			filterTrack();
			parseAndDisplaySubTrack(-1, -1);

			progressBar.setString("Altitudes réinitialisées-");
		}
	}

	@Override
	public void onTrackModified() {
		updateTrack();
	}

	/**
	 * Recherche des altitudes ramenées au niveau du sol dans un Thread séparé
	 * car les requêtes IGN peuvent prendre du temps. Ces nouvelles altitudes
	 * sont récupérées par Internet auprès de l'IGN qui les calcule à partir
	 * d'un modèle de terrain.
	 */
	private class ElevationCorrection extends SwingWorker<Void, Void> {

		// Nombre de géolocalisations à traiter en même temps afin de minimiser
		// les accès Internet
		private final int nbLocInQuery = 50;

		/**
		 * Initialiser la barre de progression de la fenêtre.
		 */
		public ElevationCorrection() {
			if (progressBar != null) {
				progressBar.setValue(0);
			}
		}

		/**
		 * Récupérer les groupements d'altitudes corrigées auprès de l'IGN tout
		 * en mettant à jour la barre de progression de la fenêtre.
		 *
		 * @return
		 * @throws Exception
		 */
		@Override
		protected Void doInBackground() throws Exception {
			int[] glIndexes = new int[locList.size()];
			setProgress(0);

			// Identifier le nombre d'altitudes à corriger et repérer les
			// indices
			// de leurs géolocalisations
			int len = 0;
			GeoLocation g;
			for (int i = 0; i < locList.size(); i++) {
				g = locList.get(i);
				if (g.modelElevation == -1) {
					glIndexes[len++] = i;
				}
			}
			if (len == 0) { // Aucune altitude à corriger
				setProgress(100);
				return null;
			}

			int j;
			double[] latitude = new double[nbLocInQuery];
			double[] longitude = new double[nbLocInQuery];
			int[] elevation = new int[nbLocInQuery];

			for (int i = 0; i < len; i += nbLocInQuery) {
				// Créer un groupe de géolocalisation
				for (j = 0; j < nbLocInQuery && i + j < len; j++) {
					g = locList.get(glIndexes[i + j]);
					latitude[j] = g.latitude;
					longitude[j] = g.longitude;
				}
				// Récupérer les altitudes corrigées
				getQuickIGNElevations(latitude, longitude, elevation, j);

				// Recopier les altitudes corrigées
				for (j = 0; j < nbLocInQuery && i + j < len; j++) {
					g = locList.get(glIndexes[i + j]);
					g.modelElevation = elevation[j];
				}

				setProgress(i * 100 / len);
			}
			setProgress(100);

			return null;
		}

		/**
		 * Appeler les méthodes de mise à jour les champs d'altitude, de
		 * distance et de vitesse une fois les corrections terminées. La barre
		 * de progession affiche un message de fin.
		 */
		@Override
		protected void done() {
			filterTrack();
			parseAndDisplaySubTrack(-1, -1);
			graph.invalidate();

			progressBar.setString("Altitudes corrigées");

			// Supprimer la référence
			corr = null;
		}

		/**
		 * Les altitudes ne peuvent être récupérées que par groupe de 20
		 * maximum.
		 *
		 * @param latitude
		 * @param longitude
		 * @param elevation
		 * @param nbLoc
		 */
		private void quickGetGeoNamesAsterElevations(double[] latitude, double[] longitude, int[] elevation,
				int nbLoc) {
			String urlString;
			URL url;
			HttpURLConnection urlConnection;
			BufferedReader in;

			urlString = "http://api.geonames.org/astergdem?lats=" + latitude[0];
			for (int j = 1; j < nbLoc; j++) {
				urlString += "," + latitude[j];
			}
			urlString += "&lngs=" + longitude[0];
			for (int j = 1; j < nbLoc; j++) {
				urlString += "," + longitude[j];
			}
			urlString += "&username=rgrosbois";
			try {
				url = new URL(urlString);
				urlConnection = (HttpURLConnection) url.openConnection();

				in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
				for (int j = 0; j < nbLoc; j++) {
					elevation[j] = Integer.parseInt(in.readLine());
				}
				in.close();
			} catch (IOException ex) {
				System.err.println("Erreur de communication avec le serveur");
			}
		}

		/**
		 * Gestion des requêtes IGN pour l'obtention des altitudes corrigées.
		 *
		 * @param latitude
		 *            tableau de latitudes des géolocalisations à corriger
		 * @param longitude
		 *            tableau de longitudes des géolocalisations à corriger
		 * @param elevation
		 *            tableau pour stocker les altitudes corrigées
		 * @param nbLoc
		 *            taille utile des tableaux
		 */
		private void getQuickIGNElevations(double[] latitude, double[] longitude, int[] elevation, int nbLoc) {
			String urlString;
			URL url;
			HttpURLConnection urlConnection;
			BufferedReader in;

			// Récupérer la clé IGN
			Preferences prefs = Preferences.userNodeForPackage(IGNMap.class);
			String cleIGN = prefs.get(IGNMap.key_cleIGN, IGNMap.cleIGN_default);

			urlString = "http://gpp3-wxs.ign.fr/" + cleIGN + "/alti/rest/elevation.json?lat=" + latitude[0];
			for (int j = 1; j < nbLoc; j++) {
				urlString += "," + latitude[j];
			}
			urlString += "&lon=" + longitude[0];
			for (int j = 1; j < nbLoc; j++) {
				urlString += "," + longitude[j];
			}
			urlString += "&zonly=true&delimiter=,";
			// System.out.println("urlString="+urlString);
			try {
				url = new URL(urlString);
				urlConnection = (HttpURLConnection) url.openConnection();
				urlConnection.setRequestProperty("Referer", "http://localhost/IGN/");

				// Lecture de la réponse, ligne par ligne.
				String reponse = "", line;
				in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
				while ((line = in.readLine()) != null) {
					reponse += line;
				}
				in.close();
				// System.out.println("reponse="+reponse);

				int startAltIdx, endAltIdx;
				startAltIdx = reponse.indexOf("[");
				endAltIdx = reponse.indexOf("]", startAltIdx + 1);
				StringTokenizer st = new StringTokenizer(reponse.substring(startAltIdx + 1, endAltIdx), ",");
				int j = 0;
				while (st.hasMoreTokens()) {
					elevation[j++] = (int) Float.parseFloat(st.nextToken());
				}
			} catch (IOException ex) {
				System.err.println("Erreur de communication avec le serveur");
			}
		}

		/**
		 * Récupérer l'altitude réelle d'un lieu à l'aide de geonames.org.
		 *
		 * <p>
		 * Trois modèles d'élévation (DEM: Digital Elevation Model) possibles:
		 * <ul>
		 * <li>Gtopo30: précision de 30 secondes d'arc (environ 1km x 1km) ->
		 * insuffisant pour cette application.</li>
		 * <li>SRTM3: précision de 3 secondes d'arc de 90m x 90m -> un peu juste
		 * pour cette application.</li>
		 * <li>Aster Global: précision de 30m x 30m -> utilisé ici.</li>
		 * </ul>
		 * La
		 *
		 * @param latitude
		 * @param longitude
		 * @see http://www.geonames.org/export/web-services.html
		 * @return
		 */
		private float getAsterElevation(double latitude, double longitude) {
			URL url;
			HttpURLConnection urlConnexion;
			BufferedReader br;
			String line;
			String result = "";

			try {
				// url = new
				// URL("http://api.geonames.org/gtopo30?lat="+latitude+
				// "&lng="+longitude+"&username=rgrosbois"); // Gtopo30
				// url = new URL("http://api.geonames.org/srtm3?lat="+latitude+
				// "&lng="+longitude+"&username=rgrosbois"); // SRTM3
				url = new URL("http://api.geonames.org/astergdem?lat=" + latitude + "&lng=" + longitude
						+ "&username=rgrosbois"); // Aster Global
				urlConnexion = (HttpURLConnection) url.openConnection();

				br = new BufferedReader(new InputStreamReader(urlConnexion.getInputStream()));
				while ((line = br.readLine()) != null) {
					result += line;
				}
				br.close();

				return Float.parseFloat(result);
			} catch (IOException ex) {
				System.err.println("Erreur de communication avec le serveur");
			}

			return Float.NaN;
		}

	}

	/**
	 * Indique si le mode correction d'altitude est activé.
	 *
	 * @return
	 */
	public boolean isCorrectedElevationSelected() {
		return correctedElevations.isSelected();
	}

	/**
	 * Récupérer les altitudes initiales de géolocalisation.
	 */
	private void resetElevations() {
		int len = locList.size();

		if (bkpElevations == null || bkpElevations.length != len) {
			return;
		}

		GeoLocation g;
		for (int i = 0; i < len; i++) {
			g = locList.get(i);
			g.dispElevation = g.kmlElevation;
		}
	}

	/**
	 * Chaîne représentant une distance avec l'unité adéquate.
	 *
	 * @return Distance en mètres (si inférieure à 1km) ou kilomètre sinon.
	 */
	private String dist2String(float distance) {
		if (distance < 1000) { // Moins d'1km -> afficher en mètres
			return String.format(Locale.getDefault(), "%dm", (int) distance);
		} else { // Afficher en kilomètres
			return String.format(Locale.getDefault(), "%.1fkm", distance / 1000f);
		}
	}

	/**
	 * Chaîne représentant une durée au format __h__mn__s.
	 *
	 * @param duree
	 *            Durée en secondes.
	 * @param showSeconds
	 *            Afficher ou non les secondes
	 * @return
	 */
	private static String time2String(long duree, boolean showSeconds) {
		String s = "";

		if (duree > 3600) { // Quantité d'heures
			s += String.format(Locale.getDefault(), "%dh", duree / 60 / 60);
		}

		if (duree % 3600 > 60) { // Quantité de minutes
			s += String.format(Locale.getDefault(), "%dmn", (duree % 3600) / 60);
		}

		if (showSeconds && duree % 60 != 0) { // Quantité de secondes
			s += String.format(Locale.getDefault(), "%ds", duree % 60);
		}
		return s;
	}

	/**
	 * Chaîne contenant les résultats de calculs de vitesse moyennes avec ou
	 * sans pause en km/h.
	 *
	 * @param distanceTot
	 *            Distance totale de la trace en mètres.
	 * @param dureeTotale
	 *            Durée de la trace avec les pauses.
	 * @param dureeSansPause
	 *            Durée de la trace sans les pause.
	 * @return vitesses en km/h au format HTML
	 */
	private String meanSpeeds2HTML(float distanceTot, long dureeTotale, long dureeSansPause) {
		if (dureeTotale == 0) {
			ResourceBundle resB = ResourceBundle.getBundle("i18n/strings", Locale.getDefault());
			return resB.getString("empty_text");
		} else {
			double moyenne = distanceTot * 3.6 / dureeTotale;
			double moyenne2 = distanceTot * 3.6 / dureeSansPause;
			return String.format(Locale.getDefault(), "%.1fkm/h<br>(%.1fkm/h)", moyenne, moyenne2);
		}
	}

	/**
	 * Chaîne contenant les dénivelés.
	 *
	 * @param denivCumulPos
	 *            Dénivelé positif en mètres
	 * @param denivCumulNeg
	 *            Dénivelé négatif en mètres
	 *
	 * @return chaîne au format HTML
	 */
	private String deniv2HTML(float denivCumulPos, float denivCumulNeg) {
		return String.format(Locale.getDefault(), "+%dm<br>-%dm", (int) denivCumulPos, (int) denivCumulNeg);
	}

	/**
	 * Chaîne contenant les extrema d'altitude ainsi que leur différence.
	 *
	 * @param pathElevMin
	 *            Altitude minimale
	 * @param pathElevMax
	 *            Altitude maximale
	 * @return chaîne au format HTML
	 */
	private String elev2HTML(int pathElevMin, int pathElevMax) {
		return String.format(Locale.getDefault(), "%dm /%dm<br>(%dm)", pathElevMin, pathElevMax,
				pathElevMax - pathElevMin);
	}

	/**
	 * Supprimer la référence vers le correcteur d'altitude.
	 *
	 * @param e
	 */
	@Override
	public void internalFrameClosing(InternalFrameEvent e) {
		if (corr != null) {
			corr.cancel(true);
			corr = null;
		}
	}

	@Override
	public void internalFrameOpened(InternalFrameEvent e) {
	}

	@Override
	public void internalFrameClosed(InternalFrameEvent e) {
	}

	@Override
	public void internalFrameIconified(InternalFrameEvent e) {
	}

	@Override
	public void internalFrameDeiconified(InternalFrameEvent e) {
	}

	@Override
	public void internalFrameActivated(InternalFrameEvent e) {
	}

	@Override
	public void internalFrameDeactivated(InternalFrameEvent e) {
	}

	/**
	 * Composant pour dessiner le graphe avec les deux courbes.
	 */
	class MixedGraph extends JComponent {

		// Constantes
		private final int BASE_MARGIN = 15;
		private final float INNER_SEP = 5;
		private final int XSEP = 20;
		private final int YSEP = 20;

		/**
		 * Courbe d'altitude.
		 */
		private final Path2D pElev;
		/**
		 * Courbe de vitesse.
		 */
		private final Path2D pSpeed; // Courbe de vitesse

		// Variables temporaires
		private final Point2D.Float c;
		private Rectangle2D bounds;
		private GeoLocation g;
		private String tmpS;

		/**
		 * Dimensions de la zone de dessin.
		 */
		private int width, height;
		/**
		 * Coordonnées extrêmes de l'intérieur du graphe (en pixels)
		 */
		private int xMin, xMax, yMax, yMin;
		/**
		 * La souris se trouvait précédemment à l'intérieur du graphe ou non.
		 */
		private boolean wasInsideGraph = false;
		/**
		 * Indices de la géolocalisation sous la souris.
		 */
		private int firstGeoLocIdx = -1, secondGeoLocIdx = -1;

		/**
		 * Constructeur.
		 */
		public MixedGraph() {
			super();
			setOpaque(true);

			// Evênements souris
			enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);

			c = new Point2D.Float();
			pElev = new Path2D.Double();
			pSpeed = new Path2D.Double();
		}

		/**
		 * Gestion des évênements statiques de la souris.
		 *
		 * @param e
		 */
		@Override
		protected void processMouseEvent(MouseEvent e) {
			super.processMouseEvent(e);
			if (locList == null || locList.isEmpty()) {
				return;
			}
			if (MouseEvent.MOUSE_CLICKED == e.getID()) {
				if (isInsideGraph(e.getX(), e.getY())) {
					secondGeoLocIdx = -1;
				} else {
					removeMouseSelection();
				}
			}
		}

		/**
		 * Déterminer si la souris se trouve actuellement à l'intérieur du
		 * graphe ou non.
		 *
		 * @param mx
		 *            Abscisse informatique de la souris
		 * @param my
		 *            Ordonnée informatique de la souris
		 * @return
		 */
		private boolean isInsideGraph(int mx, int my) {
			return mx >= (xMin + XSEP) && mx <= (xMax - XSEP) && my >= (yMin + YSEP) && my <= (yMax - YSEP);
		}

		/**
		 * La souris vient de sortir du graphe. Remettre la barre de progression
		 * dans son état original et mettre à jour les drapeaux.
		 */
		private void removeMouseSelection() {
			wasInsideGraph = false;
			firstGeoLocIdx = -1;
			secondGeoLocIdx = -1;
			// Avertir les écouteurs
			for (WeakReference<GeoLocSelectionListener> wg : gListeners) {
				GeoLocSelectionListener gsl = wg.get();
				if (gsl != null) {
					gsl.onGeoLocIntervalSeleted(-1, -1);
				}
			}
			parseAndDisplaySubTrack(-1, -1);
			repaint(xMin, yMin, xMax - xMin, yMax - yMin);
		}

		/**
		 * Gestion des évênements de déplacement de la souris.
		 *
		 * @param e
		 */
		@Override
		protected void processMouseMotionEvent(MouseEvent e) {
			super.processMouseMotionEvent(e);
			if (locList == null) {
				return;
			}
			if (MouseEvent.MOUSE_DRAGGED == e.getID()) {
				if (isInsideGraph(e.getX(), e.getY())) {
					if (!wasInsideGraph) {
						wasInsideGraph = true;
					}
					// Identifier la géolocalisation la plus proche
					secondGeoLocIdx = getNearestGeoLocIdx(e.getX());
					parseAndDisplaySubTrack(firstGeoLocIdx, secondGeoLocIdx);
					repaint(xMin, yMin, xMax - xMin, yMax - yMin);
					// Avertir les écouteurs
					for (WeakReference<GeoLocSelectionListener> wg : gListeners) {
						GeoLocSelectionListener gsl = wg.get();
						if (gsl != null) {
							gsl.onGeoLocIntervalSeleted(firstGeoLocIdx, secondGeoLocIdx);
						}
					}
				}
			} else if (MouseEvent.MOUSE_MOVED == e.getID() && secondGeoLocIdx == -1) {
				if (isInsideGraph(e.getX(), e.getY())) {
					if (!wasInsideGraph) {
						wasInsideGraph = true;
					}
					// Identifier la géolocalisation la plus proche
					firstGeoLocIdx = getNearestGeoLocIdx(e.getX());
					repaint(xMin, yMin, xMax - xMin, yMax - yMin);
					// Avertir les écouteurs
					for (WeakReference<GeoLocSelectionListener> wg : gListeners) {
						GeoLocSelectionListener gsl = wg.get();
						if (gsl != null) {
							gsl.onGeoLocIntervalSeleted(firstGeoLocIdx, firstGeoLocIdx);
						}
					}

				}
			}
		}

		/**
		 * Déterminer la géolocalisation la plus proche de l'endroit où se situe
		 * la souris.
		 *
		 * @param e
		 * @return indice de la géolocalisation dans la trace
		 */
		private int getNearestGeoLocIdx(int x) {
			int gIdx;

			if (locList == null || locList.isEmpty()) {
				return -1;
			}

			int dist = x2dist(x);
			// Trouver la géolocalisation la plus proche par dichotomie
			int imin = 0, imax = locList.size();
			gIdx = 0;
			boolean again = true;
			while (again) {
				gIdx = (imax + imin) / 2;
				if (gIdx == imax || gIdx == imin) {
					again = false; // Géolocalisation trouvée
				} else if (locList.get(gIdx).length == dist) {
					again = false; // Géolocalisation trouvée
				} else if (locList.get(gIdx).length < dist) {
					// Chercher la géolocalisation dans la partie supérieure
					imin = gIdx;
				} else {
					// Chercher la géolocalisation dans la partie inférieure
					imax = gIdx;
				}
			}
			return gIdx;
		}

		/**
		 * Donner les coordonnées informatiques d'un point du graphe d'altitude.
		 * Le résultat est renvoyé dans le premier paramètre afin de permettre
		 * l'utilisation de la même variable Point2D à chaque appel.
		 *
		 * @param c
		 *            Coordonnées informatiques
		 * @param d
		 *            Distance en mètres
		 * @param alt
		 *            Altitude en mètres
		 */
		private void elevDist2xy(Point2D.Float c, int d, float alt) {
			c.x = dist2x(d);
			c.y = elev2y(alt);
		}

		/**
		 * Calculer l'abscisse informatique d'une distance.
		 *
		 * @param d
		 *            Distance en mètres
		 * @return Abscisse en pixels
		 */
		private float dist2x(int d) {
			return xMin + XSEP + d * 1.0f * (xMax - xMin - 2 * XSEP) / distTot;
		}

		/**
		 * Calculer la distance parcourue correspondant à une abscisse
		 * informatique.
		 *
		 * @param x
		 * @return
		 */
		private int x2dist(float x) {
			return (int) ((x - xMin - XSEP) * distTot / (xMax - xMin - 2 * XSEP));
		}

		/**
		 * Calculer l'ordonnée informatique d'une altitude.
		 *
		 * @param alt
		 *            Altitude en mètres
		 * @return Ordonnée en pixels
		 */
		private float elev2y(float alt) {
			return yMax - YSEP - (alt - graphElevMin) * (yMax - yMin - 2 * YSEP) / (graphElevMax - graphElevMin);
		}

		/**
		 * Calculer les coordonnées informatiques d'un point du graphe de
		 * vitesse
		 *
		 * @param c
		 *            Coordonnées
		 * @param d
		 *            Distance en mètres
		 * @param vit
		 *            Vitesse en km/h
		 */
		private void distSpeed2xy(Point2D.Float c, int d, float vit) {
			c.x = dist2x(d);
			c.y = speed2y(vit);
		}

		/**
		 * Calculer l'ordonnée informatique d'une vitesse.
		 *
		 * @param speed
		 *            Vitesse en km/h
		 * @return Ordonnée dans la vue en pixels
		 */
		private float speed2y(float speed) {
			return yMax - YSEP - (speed - graphSpeedMin) * (yMax - yMin - 2 * YSEP) / (graphSpeedMax - graphSpeedMin);
		}

		/**
		 * Dessiner les 2 graphes et afficher les statistiques de parcours dans
		 * la vue
		 */
		@Override
		protected void paintComponent(Graphics gp) {
			super.paintComponent(gp);

			if (locList == null || locList.isEmpty()) {
				return;
			}
			int numPoints = locList.size();

			Graphics2D g2 = (Graphics2D) gp; // récupérer l'outil graphique
												// Swing

			// Copie de l'outil graphique pour les légendes
			Graphics2D g2Legends;
			g2Legends = (Graphics2D) g2.create();
			final float dash1[] = { 2f };
			g2Legends.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 2f, dash1, 0.0f));
			g2Legends.setFont(fontNormal);
			g2Legends.setPaint(Color.WHITE);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

			// +-------------------+
			// | Calcul des marges |
			// +-------------------+
			width = getWidth(); // Largeur de la zone
			height = getHeight(); // Hauteur de la zone

			// Marge gauche (légende d'altitude)
			tmpS = String.format(Locale.getDefault(), "%dm", graphElevMax);
			bounds = g2Legends.getFontMetrics().getStringBounds(tmpS, g2Legends);
			xMin = (int) bounds.getWidth() + BASE_MARGIN;

			// Marge droite (légende de vitesse)
			tmpS = String.format(Locale.getDefault(), "%.0fkm/h", graphSpeedMax);
			bounds = g2Legends.getFontMetrics().getStringBounds(tmpS, g2Legends);
			xMax = width - ((int) bounds.getWidth() + BASE_MARGIN); // Marge
																	// droite

			// Marges basse (axe distance) et haute (axe temporel)
			tmpS = "0 m s";
			bounds = g2Legends.getFontMetrics().getStringBounds(tmpS, g2Legends);
			yMin = (int) bounds.getHeight() + BASE_MARGIN;
			yMax = height - yMin;

			// Colorier l'intérieur du graphe en blanc
			g2Legends.fill(new Rectangle2D.Float(xMin, yMin, xMax - xMin, yMax - yMin));

			// +-------------------+
			// | Courbe d'altitude |
			// +-------------------+
			// Créer la courbe tout en identifiant l'ordonnée de l'altitude
			// maximale
			// servant au dégradé
			double yElevMax = yMax;
			pElev.reset();
			for (int i = 0; i < numPoints; i++) {
				g = locList.get(i);
				elevDist2xy(c, g.length, g.dispElevation);
				if (i == 0) {
					pElev.moveTo(c.x, c.y);
				} else {
					pElev.lineTo(c.x, c.y);
				}
				if (c.y < yElevMax) { // Max d'altitude = min d'ordonnée
					yElevMax = c.y;
				}
			}
			// Dessiner la courbe (contour) en rouge
			Graphics2D g2ElevShape;
			g2ElevShape = (Graphics2D) g2.create();
			g2ElevShape.setPaint(Color.RED);
			g2ElevShape.setStroke(new BasicStroke(2));
			g2ElevShape.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2ElevShape.draw(pElev);

			// Fermer la courbe et dessiner le dégradé intérieur
			elevDist2xy(c, distTot, graphElevMin);
			pElev.lineTo(c.x, c.y);
			elevDist2xy(c, 0, graphElevMin);
			pElev.lineTo(c.x, c.y);
			pElev.closePath();
			Graphics2D g2ElevFill = (Graphics2D) g2.create();
			g2ElevFill.setPaint(new GradientPaint(0, (int) yElevMax, transparentWhite, 0, yMax, transparentRed));
			g2ElevFill.fill(pElev);

			// +-------------------+
			// | Courbe de vitesse |
			// +-------------------+
			if (graphSpeedMax > 10000) {
				return; // Calcul de vitesse erroné (vitesse infinie = pb
						// d'acquisition)
			}
			// Créer le chemin correspondant à la courbe de vitesse
			// tout en identifiant l'ordonnée de la vitesse maximale pour
			// le calcul du dégradé
			double ySpeedMax = yMax;
			pSpeed.reset();
			for (int i = 0; i < numPoints; i++) {
				g = locList.get(i);
				distSpeed2xy(c, g.length, g.speed);
				if (i == 0) {
					pSpeed.moveTo(c.x, c.y);
				} else {
					pSpeed.lineTo(c.x, c.y);
				}
				if (c.y < ySpeedMax) {
					ySpeedMax = c.y;
				}
			}
			// Dessiner la courbe (contour) en bleu
			Graphics2D g2SpeedShape;
			g2SpeedShape = (Graphics2D) g2ElevShape.create();
			g2SpeedShape.setColor(Color.BLUE);
			g2SpeedShape.draw(pSpeed);

			// Dégradé intérieur
			distSpeed2xy(c, distTot, graphSpeedMin);
			pSpeed.lineTo(c.x, c.y);
			distSpeed2xy(c, 0, graphSpeedMin);
			pSpeed.lineTo(c.x, c.y);
			pSpeed.closePath();
			Graphics2D g2SpeedFill = (Graphics2D) g2ElevFill.create();
			g2SpeedFill.setPaint(new GradientPaint(0, 0, transparentBlue, 0, height, transparentWhite));
			pSpeed.moveTo(xMin, yMax);
			g2SpeedFill.fill(pSpeed);

			// +-------------------------+
			// | Graduations en altitude |
			// +-------------------------+
			g2Legends.setPaint(elevColor);
			int deniv = graphElevMax - graphElevMin;
			int incr;
			if (deniv < 1000) {
				incr = 100; // si la plage est inférieure à 1000 m => graduer
							// tous les 100 m
			} else {
				incr = 200; // Sinon graduer tous les 200 m
			}
			for (int i = graphElevMin; i <= graphElevMax; i += incr) {
				elevDist2xy(c, 0, i);
				// Légende intermédiaire
				tmpS = String.format(Locale.getDefault(), "%dm", i);
				bounds = g2Legends.getFontMetrics().getStringBounds(tmpS, g2Legends);
				g2Legends.drawString(tmpS, (float) (xMin - INNER_SEP - bounds.getWidth()),
						(float) (c.y + bounds.getHeight() / 2));
				// Axe intermédiaire
				g2Legends.draw(new Line2D.Float(xMin, c.y, xMax - XSEP, c.y));
			}

			// +------------------------+
			// | Graduations en vitesse |
			// +------------------------+
			// Les graduations dépendent de la plage de vitesse
			g2Legends.setColor(speedColor);
			float deltaV = graphSpeedMax - graphSpeedMin;
			if (deltaV < 20) {
				incr = 2; // si la plage est inférieure à 20 km/h => graduer
							// tous les 2 km/h
			} else {
				incr = 5; // Sinon graduer tous les 5 km/h
			}
			for (int i = (int) graphSpeedMin; i <= graphSpeedMax; i += incr) {
				distSpeed2xy(c, 0, i);
				// Légende intermédiaire
				tmpS = String.format(Locale.getDefault(), "%d km/h", i);
				bounds = g2Legends.getFontMetrics().getStringBounds(tmpS, g2Legends);
				g2Legends.drawString(tmpS, xMax + INNER_SEP, (float) (c.y + bounds.getHeight() / 2));
				// Axe intermédiaire
				g2Legends.draw(new Line2D.Float(xMin + XSEP, c.y, xMax, c.y));
			}

			// +-------------------------+
			// | Graduations en distance |
			// +-------------------------+
			if (distTot < 2_000) {
				incr = 200; // Si moins de 2 km => graduer tous les 200 m
			} else if (distTot < 10_000) {
				incr = 1000; // Si moins de 10 km => graduer tous les 1 km
			} else if (distTot < 20_000) {
				incr = 2_000; // Si moins de 20 km => graduer tous les 2 km
			} else {
				incr = 5_000; // Sinon graduer tous les 5 km
			}
			g2Legends.setPaint(Color.LIGHT_GRAY);
			for (int d = 0; d <= distTot; d += incr) {
				// Graduation verticale
				elevDist2xy(c, d, graphElevMin);
				g2Legends.draw(new Line2D.Float(c.x, yMin + YSEP, c.x, yMax + INNER_SEP / 2));
				// Légende
				tmpS = String.format(Locale.getDefault(), "%dm", d);
				bounds = g2Legends.getFontMetrics().getStringBounds(tmpS, g2Legends);
				g2Legends.drawString(tmpS, c.x - (float) bounds.getWidth() / 2,
						yMax + INNER_SEP / 2 + (float) bounds.getHeight());
			}

			// +-------------------------+
			// | Graduations temporelles |
			// +-------------------------+
			if (dureeTot < 60 * 30) { // Si moins de 30mn => graduer toutes les
										// 5mn
				incr = 5 * 60;
			} else if (dureeTot < 60 * 60) {// Si moins d'1h => graduer toutes
											// les 10mn
				incr = 10 * 60;
			} else if (dureeTot < 60 * 60 * 2) { // Si moins de 2h => graduer
													// tous les 1/4h
				incr = 15 * 60;
			} else { // Sinon graduer toutes les 30mn
				incr = 30 * 60;
			}
			GeoLocation g0 = locList.get(0);
			int iLoc = 1;
			g = locList.get(iLoc); // Première position
			for (int t = incr; t < dureeTot; t += incr) {
				// Trouver la géolocalisation se situant juste après la
				// graduation
				while (Math.abs(g.timeStampS - g0.timeStampS) < t) {
					g = locList.get(++iLoc);
				}

				// Axe vertical
				elevDist2xy(c, g.length, graphElevMax);
				g2Legends.draw(new Line2D.Float(c.x, yMin - INNER_SEP / 2, c.x, yMax - YSEP));
				// Légende
				tmpS = time2String(t, false);
				bounds = g2Legends.getFontMetrics().getStringBounds(tmpS, g2Legends);
				g2Legends.drawString(tmpS, (float) (c.x - bounds.getWidth() / 2),
						yMin - (float) bounds.getHeight() / 2 - INNER_SEP / 2);
			}

			// +-----------------------+
			// | Position particulière |
			// +-----------------------+
			if (firstGeoLocIdx != -1) {
				g = locList.get(firstGeoLocIdx);
				elevDist2xy(c, g.length, g.dispElevation);
				if (secondGeoLocIdx != -1) { // Intervalle de géolocalisations
					GeoLocation gEnd = locList.get(secondGeoLocIdx);
					Point2D.Float cEnd = new Point2D.Float();
					elevDist2xy(cEnd, gEnd.length, gEnd.dispElevation);

					Graphics2D g2interv = (Graphics2D) g2Legends.create();
					g2interv.setPaint(intervColor);
					// Rectangle
					if (cEnd.x > c.x) {
						g2interv.fill(new Rectangle2D.Float(c.x, yMin, cEnd.x - c.x, yMax - yMin));
					} else {
						g2interv.fill(new Rectangle2D.Float(cEnd.x, yMin, c.x - cEnd.x, yMax - yMin));
					}
				}

				Graphics2D g2g = (Graphics2D) g2Legends.create();
				g2Legends.setPaint(Color.WHITE);
				g2g.setPaint(Color.MAGENTA);
				g2g.setStroke(new BasicStroke(2));
				// Ligne verticale
				g2g.draw(new Line2D.Float(c.x, yMin, c.x, yMax));
				// Distance courante
				g2g.setStroke(new BasicStroke(1));
				tmpS = String.format(Locale.getDefault(), "%dm", g.length);
				bounds = g2g.getFontMetrics().getStringBounds(tmpS, g2g);
				Rectangle2D rect = new Rectangle2D.Double(c.x - bounds.getWidth() / 2 - INNER_SEP,
						yMax - bounds.getHeight() - INNER_SEP, bounds.getWidth() + 2 * INNER_SEP,
						bounds.getHeight() + INNER_SEP);
				g2Legends.fill(rect);
				g2g.draw(rect);
				g2g.drawString(tmpS, (float) (c.x - bounds.getWidth() / 2), yMax - INNER_SEP);
				// Durée courante
				tmpS = time2String(g.timeStampS - g0.timeStampS, true);
				bounds = g2g.getFontMetrics().getStringBounds(tmpS, g2g);
				rect = new Rectangle2D.Double(c.x - bounds.getWidth() / 2 - INNER_SEP, yMin,
						bounds.getWidth() + 2 * INNER_SEP, bounds.getHeight() + INNER_SEP);
				g2Legends.fill(rect);
				g2g.draw(rect);
				g2g.drawString(tmpS, (float) (c.x - bounds.getWidth() / 2), (float) (yMin + bounds.getHeight()));
				// Altitude
				tmpS = ((int) g.dispElevation) + "m";
				bounds = g2ElevShape.getFontMetrics().getStringBounds(tmpS, g2ElevShape);
				rect = new Rectangle2D.Double(c.x - bounds.getWidth() / 2 - INNER_SEP, c.y,
						bounds.getWidth() + 2 * INNER_SEP, bounds.getHeight() + INNER_SEP);
				g2Legends.fill(rect);
				g2ElevShape.draw(rect);
				g2ElevShape.drawString(tmpS, (float) (c.x - bounds.getWidth() / 2), (float) (c.y + bounds.getHeight()));
				// Vitesse
				distSpeed2xy(c, g.length, g.speed);
				tmpS = String.format(Locale.getDefault(), "%.1fkm/h", g.speed);
				bounds = g2SpeedShape.getFontMetrics().getStringBounds(tmpS, g2SpeedShape);
				rect = new Rectangle2D.Double(c.x - bounds.getWidth() / 2 - INNER_SEP, c.y,
						bounds.getWidth() + 2 * INNER_SEP, bounds.getHeight() + INNER_SEP);
				g2Legends.fill(rect);
				g2SpeedShape.draw(rect);
				g2SpeedShape.drawString(tmpS, (float) (c.x - bounds.getWidth() / 2),
						(float) (c.y + bounds.getHeight()));

			}

		}
	}

	public interface GeoLocSelectionListener {

		/**
		 * Prévient les écouteurs qu'un sous-ensemble de la trace a été
		 * sélectionné.
		 *
		 * @param geoIdx1
		 *            Index de la géolocalisation de départ.
		 * @param geoIdx2
		 *            Index de la géolocalisation d'arrivée.
		 */
		public void onGeoLocIntervalSeleted(int geoIdx1, int geoIdx2);
	}

	public void addGeoLocSelectionListener(GeoLocSelectionListener gsl) {
		WeakReference<GeoLocSelectionListener> g = new WeakReference<>(gsl);
		if (!gListeners.contains(g)) {
			gListeners.add(g);
		}
	}

	public void removeGeoLocSelectionListener(GeoLocSelectionListener gsl) {
		WeakReference<GeoLocSelectionListener> g = new WeakReference<>(gsl);
		if (gListeners.contains(g)) {
			gListeners.remove(g);
		}
	}

}
