package fr.rg.java.jrandoIGN;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.JComboBox;
import javax.swing.SwingWorker;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Gestion de la liste déroulante servant à suggérer une adresse de position en
 * fonction de ce que tape l'utilisateur. Les suggestions proviennent de
 * requêtes OpenLS auprès de l'IGN.
 */
public class IGNLocationSuggest extends PlainDocument {

  // Nombre maximum de suggestion pour une requête
  private static final int MAX_SUGGESTIONS = 5;
  // ComboBox utilisée pour la saisie et les suggestions d'adresses
  private final JComboBox comboBox;
  // Indique si la liste de suggestion est en cours de modification (=verrou)
  boolean listUpdating = false;

  /**
   * Construire une nouvelle instance associée à une JComboBox
   *
   * @param cb
   */
  public IGNLocationSuggest(JComboBox cb) {
    comboBox = cb;
  }

  /**
   * Automatiquement appelée, entre autre, lorsqu'un item de liste est
   * sélectionné. Cette méthode est court-circuitée lors du peuplement de la
   * liste déroulante.
   *
   * @param offs
   * @param len
   * @throws BadLocationException
   */
  @Override
  public void remove(int offs, int len) throws BadLocationException {
    if (listUpdating) {
      return;
    }
    super.remove(offs, len);
  }

  /**
   * Cette méthode est court-circuitée lorsque la liste est en court de
   * peuplement (sélection automatique ?) ou sinon sert de déclenchement pour
   * une requête OpenLS (uniquement lorsque l'utilisateur a tapé plus de 3
   * caractères).
   *
   * @param offs
   * @param str
   * @param a
   * @throws BadLocationException
   */
  @Override
  public void insertString(int offs, String str, AttributeSet a)
    throws BadLocationException {
    if (listUpdating) {
      return;
    }
    super.insertString(offs, str, a); // Mettre à jour le champ de saisie

    final String adresse = getText(0, getLength());
    if (getLength() >= 3) {
      new OpenLSRequest(adresse).execute();
    }
  }

  /**
   * Appel de la méthode de gestion de la requête OpenLS dans un Thread distinct
   * de l'UI.
   */
  private class OpenLSRequest extends SwingWorker<GeoLocation[], Void> {

    private final String partialAddress;

    public OpenLSRequest(String adresse) {
      this.partialAddress = adresse;
    }

    @Override
    protected GeoLocation[] doInBackground() throws Exception {
      return geolocaliser(partialAddress, MAX_SUGGESTIONS);
    }

    /**
     * Récupérer le résultat de la requête et peupler la liste déroulante
     */
    @Override
    protected void done() {
      listUpdating = true; // verrou

      comboBox.removeAllItems();  // vider la liste
      comboBox.addItem(partialAddress); // placer l'adresse partielle en tête
      try {
        GeoLocation[] g;
        g = (GeoLocation[]) get();
        for (GeoLocation g1 : g) {
          if (g1 != null) {
            if (!g1.address.equals(partialAddress)) {
              comboBox.addItem(g1.address);
            }
          }
        }
        // Dérouler la liste si plus de 1 élément
        comboBox.setPopupVisible(comboBox.getItemCount() > 1);
      } catch (InterruptedException | ExecutionException ex) {
        Logger.getLogger(IGNLocationSuggest.class.getName()).
          log(Level.SEVERE, null, ex);
      }
      listUpdating = false; // enlever le verrou
    }
  }

  /**
   * Récupérer une liste de suggestion d'adresses auprès de l'IGN grâce à une
   * requête OpenLS.
   *
   * @param address
   * @param maxReponses
   * @return
   */
  public static GeoLocation[] geolocaliser(String address, int maxReponses) {
    URL url;
    HttpURLConnection urlConnection;
    Writer output;
    GeoLocation loc[] = new GeoLocation[maxReponses];
    Document dom;

    // Récupérer la clé IGN
    Preferences prefs = Preferences.userNodeForPackage(IGNMap.class);
    String cleIGN = prefs.get(IGNMap.key_cleIGN, IGNMap.cleIGN_default);

    String content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
      + "<XLS\n"
      + "xmlns:xls=\"http://www.opengis.net/xls\"\n"
      + "xmlns:gml=\"http://www.opengis.net/gml\"\n"
      + "xmlns=\"http://www.opengis.net/xls\"\n"
      + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
      + "version=\"1.2\"\n"
      + "xsi:schemaLocation=\"http://www.opengis.net/xls "
      + "http://schemas.opengis.net/ols/1.2/olsAll.xsd\">\n"
      + "<RequestHeader/>\n"
      + "<Request requestID=\"1\" version=\"1.2\" "
      + "methodName=\"LocationUtilityService\" "
      + "maximumResponses=\"" + maxReponses + "\">\n"
      + "<GeocodeRequest returnFreeForm=\"false\">\n"
      + //"<Address countryCode=\"PositionOfInterest\">\n"+
      "<Address countryCode=\"StreetAddress\">\n"
      + "<freeFormAddress>" + address + "</freeFormAddress>\n"
      + "</Address>\n"
      + "</GeocodeRequest>\n"
      + "</Request>\n"
      + "</XLS>\n";
    try {
      // Envoyer la requête
      url = new URL("http://gpp3-wxs.ign.fr/" + cleIGN
        + "/geoportail/ols");
      String proxyHostname = prefs.get(DesktopFrame.PROXY_HOSTNAME_KEY, "");
      if (!"".equalsIgnoreCase(proxyHostname)) { // utiliser un proxy
        int proxyPortNum = Integer.parseInt(
          prefs.get(DesktopFrame.PROXY_PORT_NUMBER_KEY, "0"));
        urlConnection
          = (HttpURLConnection) url.openConnection(new Proxy(Proxy.Type.HTTP,
              new InetSocketAddress(proxyHostname, proxyPortNum)));
      } else { // Pas de proxy
        urlConnection = (HttpURLConnection) url.openConnection();
      }
      urlConnection.setDoOutput(true); // pour poster
      urlConnection.setDoInput(true); // pour lire
      urlConnection.setUseCaches(false);
      urlConnection.setRequestProperty("Referer", "http://localhost/IGN/");
      urlConnection.setRequestMethod("POST");
      urlConnection.setRequestProperty("Content-Type", "text/xml");
      urlConnection.connect();
      output = new OutputStreamWriter(urlConnection.getOutputStream());
      output.append(content);
      output.flush();
      output.close();

      // Analyser la réponse
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      DocumentBuilder db;
      Element document, adresse, gmlpos;
      NodeList nl, place, streetAddress, building, street, postalCode;
      NamedNodeMap place2;
      String codePostal = null, ville = null, rue = null, numeroRue = null;

      // Récupérer le modèle du document
      db = dbf.newDocumentBuilder();
      dom = db.parse(urlConnection.getInputStream());

      // Extraire les informations pertinentes
      document = dom.getDocumentElement();
      nl = document.getElementsByTagName("GeocodedAddress");

      for (int i = 0; i < nl.getLength(); i++) { // Uniquement la première réponse
        adresse = (Element) nl.item(i);
        gmlpos = (Element) adresse.getElementsByTagName("gml:pos").item(0);
        String[] geo = gmlpos.getTextContent().split(" ");
        loc[i] = new GeoLocation(Double.parseDouble(geo[1]), // longitude
          Double.parseDouble(geo[0])); // latitude

        // Compléments d'information
        place = adresse.getElementsByTagName("Place");
        for (int j = 0, n = place.getLength(); j < n; j++) { // éléments Place
          place2 = place.item(j).getAttributes();
          for (int k = 0, n2 = place2.getLength(); k < n2; k++) { // attributs de Place

            if (place2.item(k).getNodeValue().equalsIgnoreCase("Bbox")) { // Bounding Box
              String bbox = place.item(j).getTextContent();
              String[] geobox = bbox.split(";");
              loc[i].setBoundingBox(Double.parseDouble(geobox[0]),// longmin
                Double.parseDouble(geobox[2]), // longmax
                Double.parseDouble(geobox[1]), // latmin
                Double.parseDouble(geobox[3])); // latmax
            } else if (place2.item(k).getNodeValue().equalsIgnoreCase("Commune")) {
              ville = place.item(j).getTextContent();
              if (ville.isEmpty()) {
                ville = null;
              }
            }

          } // Boucle sur attributs de Place
        } // Boucle sur éléments Place

        streetAddress = adresse.getElementsByTagName("StreetAddress");
        if (streetAddress != null) {
          // Numéro de rue
          building = ((Element) streetAddress.item(0)).getElementsByTagName("Building");
          if (building != null && building.getLength() >= 1) {
            numeroRue = building.item(0).getAttributes().item(0).getTextContent();
            if (numeroRue.isEmpty()) {
              numeroRue = null;
            }
          }

          // Rue
          street = ((Element) streetAddress.item(0)).getElementsByTagName("Street");
          if (street != null && street.getLength() >= 1) {
            rue = street.item(0).getTextContent();
            if (rue.isEmpty()) {
              rue = null;
            }
          }

        }
        // Code postal
        postalCode = adresse.getElementsByTagName("PostalCode");
        if (postalCode != null && postalCode.getLength() >= 1) {
          codePostal = postalCode.item(0).getTextContent();
          if (codePostal.isEmpty()) {
            codePostal = null;
          }
        }
        // Ajouter une éventuelle adresse
        loc[i].setAdresse(
          ((numeroRue != null) ? numeroRue + " " : "")
          + ((rue != null) ? rue + ", " : "")
          + ((codePostal != null) ? codePostal + " " : "")
          + ((ville != null) ? ville : ""));
      } // Boucle sur les adresses candidates
    } catch (ParserConfigurationException | SAXException | IOException e1) {
    }

    return loc;
  }
}
