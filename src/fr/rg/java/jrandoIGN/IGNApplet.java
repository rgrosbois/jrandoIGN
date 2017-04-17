package fr.rg.java.jrandoIGN;

import java.lang.reflect.InvocationTargetException;

import javax.swing.JApplet;
import javax.swing.SwingUtilities;

public class IGNApplet extends JApplet {

  private static final long serialVersionUID = 1L;

  @Override
  public void init() {
    super.init();

    try {
      SwingUtilities.invokeAndWait(new Runnable() {
        @Override
        public void run() {
          createGUI();
        }
      });
    } catch (InvocationTargetException | InterruptedException e) {
    }
  }

  private void createGUI() {
    // Centre de la carte initiale
    double latitude = 45.145;
    double longitude = 5.72f;
    GeoLocation center = new GeoLocation(longitude, latitude);

    IGNMap map = new IGNMap(center);
    map.setOpaque(true);
    setContentPane(map);
  }
}
