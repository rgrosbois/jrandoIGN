package fr.rg.java.jrandoIGN;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.util.ArrayList;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamException;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.stream.events.XMLEvent;

/**
 * Méthodes pour extraires des géolocalisation d'une trace afin de créer et
 * remplir une HashMap constituée de:
 * <ul>
 * <li>LOCATIONS_KEY: une liste (ArrayList) des géolocalisations.</li>
 * <li>NUM_LOC_KEY: taille de la liste.</li>
 * <li>CUMUL_DIST_KEY: distance totale parcourue</li>
 * <li>PATHNAME_KEY: le nom de fichier KML associé (au format
 * yyyyMMdd-hhmm-hitrack.kml)</li>
 * <li>ALT_MIN_KEY: altitude minimale de la liste</li>
 * <li>ALT_MAX_KEY: altitude maximale de la liste</li>
 * <li>LAT_MIN_KEY: latitude minimale de la liste</li>
 * <li>LAT_MAX_KEY: latitude maximale de la liste</li>
 * <li>LONG_MIN_KEY: longitude minimale de la liste</li>
 * <li>LONG_MAX_KEY: longitude maximale de la liste</li>
 * <li>SPEED_MIN_KEY: vitesse minimale de la liste</li>
 * <li>SPEED_MAX_KEY: vitesse maximale de la liste</li>
 * <li></li>
 * </ul>
 */
public class TrackReader {

  // Clés d'enregistrement dans la HashMap
  public static final String ALT_MIN_KEY = "alt_min";
  public static final String ALT_MAX_KEY = "alt_max";
  public static final String PATHNAME_KEY = "title_key"; // Nom de fichier
  public static final String CUMUL_DIST_KEY = "cumul_dist_key"; // distance parcourue
  public static final String LAT_MIN_KEY = "lat_min_key"; // Latitude minimale
  public static final String LAT_MAX_KEY = "lat_max_key"; // Latitude maximale
  public static final String LONG_MIN_KEY = "long_min_key"; // Longitude minimale
  public static final String LONG_MAX_KEY = "long_max_key"; // Longitude maximale
  public static final String LOCATIONS_KEY = "loc_list_key"; // liste de positions
  public static final String NUM_LOC_KEY = "num_loc_key"; // nombre de positions
  public static final String SPEED_MIN_KEY = "vitesse_min_key"; // vitesse minimale
  public static final String SPEED_MAX_KEY = "vitesse_max_key"; // vitesse minimale

  // Tags de fichier KML
  private static final String KML_PLACEMARK = "Placemark";
  private static final String KML_WHEN = "when";
  private static final String KML_POINT = "Point";
  private static final String KML_COORDINATES = "coordinates";
  private static final String KML_LINESTRING = "LineString";

  // Pour interpréter les dates de positions
  private static final DateFormat df1 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
  /**
   * Lecture d'un fichier KML contenant un trajet (LineString) et, la plupart du
   * temps, des positions (avec leurs dates).
   *
   * <p>
   * L'opération pouvant s'avérer longue, il est préférable de la mettre en
   * oeuvre dans le cadre d'un 'SwingWorker'.
   *
   * <p>
   * Format du fichier KML:
   * <pre>
   * Document
   * |--name
   * |--description
   * |--Style id="track_n" // trace normale
   * | |-- IconStyle
   * | | |-- Icon
   * | | | |-- href
   * |--Style id="track_h" // trace surlignée
   * | |-- IconStyle
   * | | |-- scale
   * | | |-- Icon
   * | | | |-- href
   * |--StyleMap id="track"
   * | |-- Pair
   * | | |-- key
   * | | |-- styleUrl
   * | |-- Pair
   * | | |-- key
   * | | |-- styleUrl
   * |--Style id="lineStyle"
   * | |-- LineStyle
   * | | |-- color
   * | | |-- width
   * ///////// Points individuels (1 placemark par point) //////
   * |--Folder
   * | |--name
   * | |--Placemark
   * | | |-- Timestamp
   * | | | |-- when
   * | | |-- StyleUrl
   * | | |-- Point
   * | | | |-- coordinates
   * | | |-- description
   * | |--Placemark
   * | | |-- Timestamp
   * | | | |-- when
   * | | |-- StyleUrl
   * | | |-- Point
   * | | | |-- coordinates
   * | | |-- description
   * . . . . . .
   * | |--Placemark
   * | | |-- Timestamp
   * | | | |-- when
   * | | |-- StyleUrl
   * | | |-- Point
   * | | | |-- coordinates
   * | | |-- description
   * /////////// Points de la trace //////////////
   * |-- Placemark
   * | |-- name
   * | |-- styleUrl
   * | |-- LineString
   * | | |-- tessellate
   * | | |-- altitudeMode
   * | | |-- coordinates
   * </pre>
   * 
   * Cette méthode fait les hypothèses suivantes:
   * <ul>
   * <li>Les points de la trace apparaissent auparavant en tant que positions
   * isolées.</li>
   * <li>Il n'y a qu'une seule trace.</li>
   * </ul>
   *
   * <p>
   * Le déroulement de l'analyse s'effectue en une seule passe mais en 2 étapes:
   * <ol>
   * <li>Récupération de chaque position individuelle dans un noeud 'Placemark'
   * sous 'Folder':
   * <ol>
   * <li>information de géolocalisation dans le sous-noeud 'coordinates',</li>
   * <li>information de date dans le sous-noeud 'when'. Chaque nouvelle position
   * est alors sauvegardée temporairement dans une HashMap où la clé est le
   * triplet (latitude,longitude,altitude).</li>
   * </ol>
   * </li>
   *
   * <li>Récupération de la trace dans le noeud 'LineString' (sous-noeud
   * 'Placemark'). A chaque nouveau point, on tente de récupérer la position
   * correspondante dans la HashMap précédente, on calcule alors les
   * informations de vitesse et distance et on l'ajoute à la fin de la
   * liste.</li>
   * </ol>
   *
   * @param fileName
   * @return
   */
  public HashMap<String, Object> extractFromKML(String fileName) {
    HashMap<String, Object> bundle = new HashMap<>();

    ArrayList<GeoLocation> list = new ArrayList<>(); // Stockage du trajet
    HashMap<String, GeoLocation> points = new HashMap<>(); // Stockage des positions
    String key = null; // Clé de stockage des positions

    XMLInputFactory xmlif;
    XMLStreamReader xmlsr;
    int eventType;
    boolean inPoint = false;
    boolean inLineString = false;
    boolean parseIndividualLocation = false;
    boolean parseTime = false;
    boolean parseTrackLocations = false;

    double longMin = 180;
    double longMax = -180;
    double latMin = 90;
    double latMax = -90;
    float altMin = 100000;
    float altMax = 0;
    float cumulDist = 0.0f;
    float vitMin = 10000;
    float vitMax = 0;
    GeoLocation g = null;
    Date date;
    String coord[];
    int nbCollision = 0, nbCollisionMax = 0;

    // Initialisation du Bundle vide
    bundle.put(PATHNAME_KEY, fileName); // Nom du fichier
    bundle.put(NUM_LOC_KEY, (int) -1); // Signaler une erreur
    bundle.put(CUMUL_DIST_KEY, 0); // Aucune géolocalisation récupérée

    try {
      xmlif = XMLInputFactory.newInstance();
      xmlsr = xmlif.createXMLStreamReader(new FileReader(fileName));

      while (xmlsr.hasNext()) { // Jusqu'à la fin du document
        eventType = xmlsr.next(); // Demander l'évênement suivant

        switch (eventType) {
          // +-----------------+
          // | Balise ouvrante |
          // +-----------------+
          case XMLEvent.START_ELEMENT:
            switch (xmlsr.getLocalName()) {
              case KML_PLACEMARK:
                g = new GeoLocation();
                nbCollision = 0;
                break;
              case KML_POINT:
                inPoint = true;
                break;
              case KML_COORDINATES:
                if (inPoint) {
                  parseIndividualLocation = true;
                } else if (inLineString) {
                  parseTrackLocations = true;
                }
                break;
              case KML_WHEN:
                if (g != null) {
                  parseTime = true;
                }
                break;
              case KML_LINESTRING:
                inLineString = true;
                break;
            }
            break;
          // +---------------------+
          // | Texte inter-balises |
          // +---------------------+
          case XMLEvent.CHARACTERS:
            if (xmlsr.isWhiteSpace()) {
              break;
            }
            if (parseIndividualLocation) { // Analyse le doublet (ou triplet) de position
              if (g == null) {
                System.err.println("Coordonnées d'une position trouvée "
                  + "en dehors d'une balise Placemark");
              } else {
                key = xmlsr.getText();
                coord = key.split(",");
                g.longitude = Double.parseDouble(coord[0]);
                g.latitude = Double.parseDouble(coord[1]);
                if (coord.length >= 3) { // altitude
                  g.kmlElevation = Float.parseFloat(coord[2]);
                }
              }

              parseIndividualLocation = false;
            } else if (parseTime) { // Analyse l'instant associé à la position
              try {
                if (g == null) {
                  System.err.println("Instant d'une position trouvée "
                    + "en dehors d'une balise Placemark");
                } else {
                  date = df1.parse(xmlsr.getText());
                  g.timeStampS = date.getTime() / 1000; // en secondes
                }
              } catch (ParseException ex) {
                Logger.getLogger(TrackReader.class.getName()).log(Level.SEVERE, null, ex);
              }

              parseTime = false;
            } else if (parseTrackLocations) { // Analyse de la trace: listes de positions
              // Reconstituer le contenu
              StringBuilder sb = new StringBuilder();
              do {
                if (xmlsr.isWhiteSpace()) {
                  continue;
                }
                sb.append(xmlsr.getText());
              } while (xmlsr.next() == XMLEvent.CHARACTERS);
              
              // Remplir la liste
              StringTokenizer st = new StringTokenizer(sb.toString());
              while (st.hasMoreTokens()) {
                key = st.nextToken();
//                System.out.println("key="+key);

                // Gestion des collisions (points avec mêmes géolocalisations)
                nbCollision = 0;
                while (nbCollision <= nbCollisionMax && points.get(key) == null) {
                  key += "-";
                  nbCollision++;
                }
                if (points.get(key) != null) { // Un point existe déjà
                  // Reprendre la géolocalisation de ce point
                  g = points.get(key);
                  points.remove(key); // Le supprimer de la liste

                  // Calculs de la distance cumulative et la vitesse instantanée
                  if (list.size() > 1) {
                    GeoLocation lastLocation = list.get(list.size() - 1);

                    // Distance cumulative version perso
                    cumulDist += g.computeDistance(lastLocation);
                    g.length = (int) cumulDist;

                    // Vitesse v=dx/dt
                    if (g.timeStampS - lastLocation.timeStampS > 0) {
                      g.speed = (g.length - lastLocation.length) * 3.6f
                        / (g.timeStampS - lastLocation.timeStampS);
                    } else { // cas où dt=0
                      g.speed = 0;
                    }
                    if (g.speed < vitMin) {
                      vitMin = g.speed;
                    }
                    if (g.speed > vitMax) {
                      vitMax = g.speed;
                    }
                  } else {
                    g.length = 0;
                    g.speed = 0;
                  }
                } else { // Le point n'existe pas -> il n'aura pas de timeStamp
                  System.err.println("Point de la trace non défini individuellement");
                  // Supprimer les éventuels '-' à la fin de la clé
                  while(key.charAt(key.length()-1)=='-') {
                    key = key.substring(0, key.length()-1);
                  }
                  // Séparer les informations
                  coord = key.split(",");
                  g = new GeoLocation();
                  g.longitude = Double.parseDouble(coord[0]);
                  g.latitude = Double.parseDouble(coord[1]);
                  if (coord.length >= 3) { // altitude
                    g.kmlElevation = Float.parseFloat(coord[2]);
                  }
                }
                // Mise à jour des statistiques
                if (g.latitude < latMin) {
                  latMin = g.latitude;
                }
                if (g.latitude > latMax) {
                  latMax = g.latitude;
                }
                if (g.longitude < longMin) {
                  longMin = g.longitude;
                }
                if (g.longitude > longMax) {
                  longMax = g.longitude;
                }
                if (g.kmlElevation < altMin) {
                  altMin = g.kmlElevation;
                }
                if (g.kmlElevation > altMax) {
                  altMax = g.kmlElevation;
                }
                list.add(g);
//                System.out.println("Geo: " + g);
              }
              g = null;

              parseTrackLocations = false;
            }
            break;
          // +-----------------+
          // | Balise fermante |
          // +-----------------+
          case XMLEvent.END_ELEMENT:
            switch (xmlsr.getLocalName()) {
              case KML_PLACEMARK:
                if (inPoint) {
                  if (g != null) {
                    if (key != null) {
                      // Vérifier une éventuelle collision
                      while (points.get(key) != null) {
                        key += "-";
                        nbCollision++;
                      }
//                      System.out.println("key="+key);
                      points.put(key, g);
                      if (nbCollision > nbCollisionMax) {
                        nbCollisionMax = nbCollision;
                      }
                    }
                    g = null;
                  }
                  inPoint = false;
                }
                break;
              case KML_LINESTRING:
                inLineString = false;
                break;
            }
            break;
        }
      }

      xmlsr.close(); // Fermer le flux
    } catch (FileNotFoundException | XMLStreamException e) {
    }

    if (list.size() > 0) {
      bundle.put(CUMUL_DIST_KEY, (int) cumulDist); // Distance cumulative
      bundle.put(ALT_MAX_KEY, (int) altMax);
      bundle.put(ALT_MIN_KEY, (int) altMin);
      bundle.put(LAT_MIN_KEY, latMin);
      bundle.put(LAT_MAX_KEY, latMax);
      bundle.put(LONG_MIN_KEY, longMin);
      bundle.put(LONG_MAX_KEY, longMax);
      bundle.put(SPEED_MIN_KEY, vitMin);
      bundle.put(SPEED_MAX_KEY, vitMax);
      bundle.put(NUM_LOC_KEY, list.size()); // Nombre de positions
      bundle.put(LOCATIONS_KEY, list); // liste de positions
    }

    return bundle;
  }

  /**
   * Extraire uniquement les géolocalisations (tp=lbs) d'un fichier HiTrack 
   * de Huawei (testé avec la Huawei Mi Band 3)
   * 
   * À faire, extraire et utiliser les autres informations:
   * <ul>
   * <li>tp=p-m: pace-minutes</li>
   * <li>tp=b-p-m: beat-per-minutes</li>
   * <li>tp=h-r: heart-rate</li>
   * <li>tp=s-r: stride-rate</li>
   * <li>tp=rs: speed-per-second</li>
   * <li>tp=alti: altitude</li>
   * </ul>
   *
   * @param fileName
   * @return
   */
  public HashMap<String, Object> extractFromHiTrack(String fileName) {
    HashMap<String, Object> bundle = new HashMap<>();

    ArrayList<GeoLocation> list = new ArrayList<>(); // Stockage du trajet
    HashMap<String, GeoLocation> points = new HashMap<>(); // Stockage des positions
    String key = null; // Clé de stockage des positions

    double longMin = 180;
    double longMax = -180;
    double latMin = 90;
    double latMax = -90;
    float altMin = 100000;
    float altMax = 0;
    float cumulDist = 0.0f;
    float vitMin = 10000;
    float vitMax = 0;

    // Initialisation du Bundle vide
    bundle.put(PATHNAME_KEY, fileName); // Nom du fichier
    bundle.put(NUM_LOC_KEY, (int) -1); // Signaler une erreur
    bundle.put(CUMUL_DIST_KEY, 0); // Aucune géolocalisation récupérée

    System.out.println(fileName);

    // Extraire l'horodatage contenu dans le nom de fichier
    String[] info = fileName.split("_");
    if (info.length == 2) {
      // Instants de début et de fin
      long start = Long.parseLong(info[1].substring(0, 13));
      Instant instantStart = Instant.ofEpochSecond(start / 1000);
      DateTimeFormatter fmtPath = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
              .withZone(ZoneId.systemDefault());
      bundle.put(PATHNAME_KEY, fmtPath.format(instantStart) + "-hitrack.kml"); // Nom de fichier KML

//      long end = Long.parseLong(info[1].substring(13, 26));
//      Instant instantEnd = Instant.ofEpochSecond(end / 1000);
//      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
//              .withZone(ZoneId.systemDefault());
//      System.out.println(formatter.format(instantStart) + " -> " + formatter.format(instantEnd));
      
      try {
        BufferedReader in = new BufferedReader(new FileReader(fileName));

        String ligne;
        GeoLocation g;
        int compteur = 0;
        while( (ligne=in.readLine()) != null) { // Fin de fichier
          String[] val = ligne.split(";");
          
          if (val[0].equals("tp=lbs")) { // Location per second
            g = new GeoLocation();
            int k = Integer.parseInt(val[1].split("=")[1]);
            g.latitude = Double.parseDouble(val[2].split("=")[1]);
            g.longitude = Double.parseDouble(val[3].split("=")[1]);
            g.kmlElevation = Float.parseFloat(val[4].split("=")[1]);
            g.timeStampS = (long) Double.parseDouble(val[5].split("=")[1]);
            
            // Supprimer les géolocalisations fausses
            if (g.latitude==90) {
              continue;
            }
            
            // Ne conserver qu'une géolocalisation toutes les 20s ?
            compteur++;
            if (compteur==20) {
              compteur=0;
            } else {
              continue;
            }

            // Calculs de la distance cumulative et de la vitesse instantanée
            if (list.size() > 1) {
              GeoLocation lastLocation = list.get(list.size() - 1);

              // Distance cumulative version perso
              cumulDist += g.computeDistance(lastLocation);
              g.length = (int) cumulDist;

              // Vitesse v=dx/dt
              if (g.timeStampS - lastLocation.timeStampS > 0) {
                g.speed = (g.length - lastLocation.length) * 3.6f
                        / (g.timeStampS - lastLocation.timeStampS);
              } else { // cas où dt=0
                g.speed = 0;
              }
              if (g.speed < vitMin) {
                vitMin = g.speed;
              }
              if (g.speed > vitMax) {
                vitMax = g.speed;
              }
            } else {
              g.length = 0;
              g.speed = 0;
            }

            // Mise à jour des statistiques
            if (g.latitude < latMin) {
              latMin = g.latitude;
            }
            if (g.latitude > latMax) {
              latMax = g.latitude;
            }
            if (g.longitude < longMin) {
              longMin = g.longitude;
            }
            if (g.longitude > longMax) {
              longMax = g.longitude;
            }
            if (g.kmlElevation < altMin) {
              altMin = g.kmlElevation;
            }
            if (g.kmlElevation > altMax) {
              altMax = g.kmlElevation;
            }

            list.add(g);
          } else { // Format non-supporté
            
          }
        }
      } catch (IOException e) {
        System.err.println("Erreur de lecture de fichier HiTrack");
      }
    }

    if (list.size() > 0) {
      bundle.put(CUMUL_DIST_KEY, (int) cumulDist); // Distance cumulative
      bundle.put(ALT_MAX_KEY, (int) altMax);
      bundle.put(ALT_MIN_KEY, (int) altMin);
      bundle.put(LAT_MIN_KEY, latMin);
      bundle.put(LAT_MAX_KEY, latMax);
      bundle.put(LONG_MIN_KEY, longMin);
      bundle.put(LONG_MAX_KEY, longMax);
      bundle.put(SPEED_MIN_KEY, vitMin);
      bundle.put(SPEED_MAX_KEY, vitMax);
      bundle.put(NUM_LOC_KEY, list.size()); // Nombre de positions
      bundle.put(LOCATIONS_KEY, list); // liste de positions
    }

    return bundle;
  }

  
}
