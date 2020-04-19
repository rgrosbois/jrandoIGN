/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package fr.rg.java.jrandoIGN;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Pour conserver une position de géolocalisation avec une éventuelle adresse et
 * bounding box.
 */
public class GeoLocation {
  public static final double RAYON_TERRE = 6_370_000; // 6'370 km


  private static final DateFormat df = new SimpleDateFormat("dd/MM/yyyy à HH:mm:ss", Locale.FRANCE);
  // latitude du lieu
  public double latitude;
  // longitude du lieu
  public double longitude;
  // adresse du lieu (si disponible)
  public String address;
  // Zone de géolocalisation contenant le lieu
  public BoundingBox bb;
  // Altitude issue du fichier KML (i.e. capteur)
  public float kmlElevation;
  // Altitude corrigée par un modèle de terrain (IGN)
  public float modelElevation = -1;
  // Altitude à afficher dans la fenêtre d'information de trace
  public float dispElevation;
  // TimeStamp en secondes
  public long timeStampS;
  // Distance parcourue
  public int length;
  // Vitesse instantanée
  public float speed;
  // Position modifiée?
  public boolean isModified = false;
  // Latitude modifiée
  public double modifiedLatitude;
  // Longitude modifiée
  public double modifiedLongitude;
  
  public GeoLocation() {
    bb = new BoundingBox();
  }

  /**
   * Construction à partir d'une longiture et latitude. La bounding box est
   * alors de dimension nulle et centrée sur cette même position.
   *
   * @param longitude
   * @param latitude
   */
  public GeoLocation(double longitude, double latitude) {
    this.latitude = latitude;
    this.longitude = longitude;
    bb = new BoundingBox();
    bb.latMin = latitude;
    bb.latMax = latitude;
    bb.longMin = longitude;
    bb.longMax = longitude;
  }

  /**
   * Associer une adresse à la géolocalisation.
   *
   * @param adresse
   */
  public void setAdresse(String adresse) {
    this.address = adresse;
  }

  /**
   * Définir une bounding box pour cette géolocalisation.
   *
   * @param longmin
   * @param longmax
   * @param latmin
   * @param latmax
   */
  public void setBoundingBox(double longmin, double longmax, double latmin, double latmax) {
    bb.longMin = longmin;
    bb.longMax = longmax;
    bb.latMin = latmin;
    bb.latMax = latmax;
  }

  /**
   * Calculer la distance en mètre depuis une autre géolocalisation.
   * 
   * @param g
   * @return 
   */
  public double computeDistance(GeoLocation g) {    
    double la = latitude;
    double lb = g.latitude;
    double da = longitude;
    double db = g.longitude;
    double ha = RAYON_TERRE + dispElevation;
    double hb = RAYON_TERRE + g.dispElevation;
    double dist = ha * ha + hb * hb - 2 * ha * hb * (
            Math.cos(Math.toRadians(la)) * Math.cos(Math.toRadians(lb)) * 
            Math.cos(Math.toRadians(da - db)) + Math.sin(Math.toRadians(la)) 
                    * Math.sin(Math.toRadians(lb))
            );
    if (dist < 0) { // Cas où erreurs de calcul empêchent de trouver 0
      return 0;
    } else {
      return Math.sqrt(dist);
    }
  }
  
  @Override
  public String toString() {
    Date date = new Date();
    date.setTime(timeStampS * 1000);
    return ((address == null) ? "" : address)
      + " " + latitude + "," + longitude + "," + kmlElevation + "/" + modelElevation
      + " (" + bb.latMin + "," + bb.longMin + "," + bb.latMax + "," + bb.longMax
      + ") " + df.format(date) + ",v=" + speed + ",d=" + length;
  }

  /**
   * Latitudes et longitudes extrêmes contenant le lieu
   */
  class BoundingBox {

    double latMin, latMax, longMin, longMax;
  }
}
